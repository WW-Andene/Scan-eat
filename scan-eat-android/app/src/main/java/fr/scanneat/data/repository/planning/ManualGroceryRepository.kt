package fr.scanneat.data.repository.planning

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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

data class ManualGroceryItem(val id: String, val name: String, val grams: Double)

private val Context.manualGroceryDataStore by preferencesDataStore(name = "manual_grocery")
private val KEY_ITEMS = stringPreferencesKey("manual_grocery_items")

@Singleton
class ManualGroceryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.manualGroceryDataStore

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

    private fun parse(raw: String?): List<ManualGroceryItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split("|").mapNotNull { entry ->
            val p = entry.split(",")
            if (p.size != 3) return@mapNotNull null
            runCatching { ManualGroceryItem(p[0], p[1], p[2].toDouble()) }.getOrNull()
        }
    }

    private fun serialize(list: List<ManualGroceryItem>): String =
        list.joinToString("|") { "${it.id},${it.name},${it.grams}" }
}
