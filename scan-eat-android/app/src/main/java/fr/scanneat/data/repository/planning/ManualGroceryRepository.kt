package fr.scanneat.data.repository.planning

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.domain.engine.planning.GroceryComponent
import fr.scanneat.domain.engine.planning.GroceryRecipeInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// MANUAL GROCERY REPOSITORY — ad-hoc grocery entries added directly (e.g. from
// a scanned product's "Save to..." popup), not derived from a recipe. Stored
// as one-component GroceryRecipeInput so GroceryViewModel can feed them into
// the same aggregateGroceryList() as regular recipe ingredients.
// ============================================================================

@JsonClass(generateAdapter = true)
data class ManualGroceryItem(val id: String, val name: String, val grams: Double)

private val Context.manualGroceryDataStore by preferencesDataStore(name = "manual_grocery")
private val KEY_ITEMS = stringPreferencesKey("manual_grocery_items")

@Singleton
class ManualGroceryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val store = context.manualGroceryDataStore
    private val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, ManualGroceryItem::class.java)
    private val adapter = moshi.adapter<List<ManualGroceryItem>>(listType)

    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val items: Flow<List<ManualGroceryItem>> = storeData.map { parse(it[KEY_ITEMS]) }.distinctUntilChanged()

    /** Feeds directly into aggregateGroceryList() alongside recipe-derived inputs. */
    val asRecipeInputs: Flow<List<GroceryRecipeInput>> = items.map { list ->
        list.map { GroceryRecipeInput(name = it.name, components = listOf(GroceryComponent(it.name, it.grams))) }
    }

    suspend fun add(name: String, grams: Double) {
        if (name.isBlank()) return
        store.edit { prefs ->
            val current = parse(prefs[KEY_ITEMS]).toMutableList()
            current.add(ManualGroceryItem(UUID.randomUUID().toString(), name.trim(), grams.coerceAtLeast(0.0)))
            prefs[KEY_ITEMS] = serialize(current)
        }
    }

    suspend fun remove(id: String) {
        store.edit { prefs ->
            val current = parse(prefs[KEY_ITEMS]).filterNot { it.id == id }
            prefs[KEY_ITEMS] = serialize(current)
        }
    }

    /**
     * Previously a hand-rolled "id,name,grams" joined with "|" — any product
     * name containing a comma (extremely common in real OFF names, e.g.
     * "Nutella, pâte à tartiner") produced more than 3 comma-split parts,
     * failed the size check, and silently vanished from the list on the next
     * DataStore reload. Real JSON (same pattern as every other repo in this
     * app) can't be corrupted by a name's own punctuation.
     *
     * A pre-existing pipe-joined value from before this fix is simply treated
     * as unparseable JSON and discarded (returns an empty list, not a crash) -
     * an acceptable one-time loss of an ad-hoc, easily re-added shopping list,
     * versus perpetuating a serialization format that silently drops entries.
     */
    private fun parse(raw: String?): List<ManualGroceryItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { adapter.fromJson(raw) }.getOrNull() ?: emptyList()
    }

    private fun serialize(list: List<ManualGroceryItem>): String = adapter.toJson(list)
}
