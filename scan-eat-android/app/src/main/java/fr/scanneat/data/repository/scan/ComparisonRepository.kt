package fr.scanneat.data.repository.scan

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.domain.model.ScoreAudit
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ScanSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// COMPARISON REPOSITORY — port of public/features/comparison.js
//
// A/B diff between two consecutive scans:
//   1. User scans Product A → arms comparison (24 h TTL)
//   2. User scans Product B → comparison fires, shows diff
//
// Storage: DataStore (three keys, same intent as the three localStorage keys).
// TTL enforced on read to prevent stale diffs from surprising the user.
// ============================================================================

private val Context.comparisonDataStore by preferencesDataStore(name = "comparison")

private val KEY_ARMED      = booleanPreferencesKey("compare_armed")
private val KEY_ARMED_AT   = longPreferencesKey("compare_armed_at")
private val KEY_PREV_JSON  = stringPreferencesKey("compare_prev_json")

private const val COMPARE_ARM_TTL_MS = 24L * 60 * 60 * 1000   // 24 h

data class ScoreSnapshot(
    val name: String,
    val score: Int,
    val grade: String,
    val category: String,
    val redFlags: List<String>,
    val greenFlags: List<String>,
    val barcode: String?,
)

data class ComparisonResult(
    val prev: ScoreSnapshot,
    val next: ScoreSnapshot,
    val scoreDelta: Int,           // positive = next is better
    val addedRedFlags: List<String>,
    val removedRedFlags: List<String>,
    val addedGreenFlags: List<String>,
    val removedGreenFlags: List<String>,
)

@Singleton
class ComparisonRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val store = context.comparisonDataStore

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private val snapshotAdapter = moshi.adapter(ScoreSnapshot::class.java)

    /** True when a comparison is armed AND within the 24 h TTL. */
    val isArmed: Flow<Boolean> = storeData.map { prefs ->
        val armed   = prefs[KEY_ARMED] ?: false
        val armedAt = prefs[KEY_ARMED_AT] ?: 0L
        armed && (System.currentTimeMillis() - armedAt) < COMPARE_ARM_TTL_MS
    }

    /** Arm: snapshot the current scan result as Product A. */
    suspend fun arm(result: ScanResult) {
        val snapshot = result.toSnapshot()
        store.edit { prefs ->
            prefs[KEY_ARMED]     = true
            prefs[KEY_ARMED_AT]  = System.currentTimeMillis()
            prefs[KEY_PREV_JSON] = snapshotAdapter.toJson(snapshot)
        }
    }

    /**
     * Compare Product B (next) against the armed Product A (prev).
     * Returns null if no valid armed snapshot. Clears the armed state.
     */
    suspend fun compare(next: ScanResult): ComparisonResult? {
        val prefs = storeData.first()

        val armed   = prefs[KEY_ARMED] ?: false
        val armedAt = prefs[KEY_ARMED_AT] ?: 0L
        if (!armed || (System.currentTimeMillis() - armedAt) >= COMPARE_ARM_TTL_MS) {
            disarm()
            return null
        }

        val prevJson = prefs[KEY_PREV_JSON] ?: return null
        val prev = runCatching { snapshotAdapter.fromJson(prevJson) }.getOrNull() ?: return null

        disarm()

        val nextSnapshot = next.toSnapshot()
        return ComparisonResult(
            prev              = prev,
            next              = nextSnapshot,
            scoreDelta        = nextSnapshot.score - prev.score,
            addedRedFlags     = nextSnapshot.redFlags - prev.redFlags.toSet(),
            removedRedFlags   = prev.redFlags - nextSnapshot.redFlags.toSet(),
            addedGreenFlags   = nextSnapshot.greenFlags - prev.greenFlags.toSet(),
            removedGreenFlags = prev.greenFlags - nextSnapshot.greenFlags.toSet(),
        )
    }

    /** Disarm without comparing (user navigates away, TTL expired, etc.). */
    suspend fun disarm() {
        store.edit { prefs ->
            prefs.remove(KEY_ARMED)
            prefs.remove(KEY_ARMED_AT)
            prefs.remove(KEY_PREV_JSON)
        }
    }

    private fun ScanResult.toSnapshot() = ScoreSnapshot(
        name       = product.name,
        score      = audit.score,
        grade      = audit.grade.label,
        category   = product.category.key,
        redFlags   = audit.redFlags,
        greenFlags = audit.greenFlags,
        barcode    = barcode,
    )
}
