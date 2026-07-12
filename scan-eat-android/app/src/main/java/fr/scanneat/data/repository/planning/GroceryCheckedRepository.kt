package fr.scanneat.data.repository.planning

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// GROCERY CHECKED REPOSITORY — persisted check-off state for the grocery
// list. GroceryScreen previously had zero check-off UI at all (not even a
// dead checkbox) — a grocery list a user can't tick items off while shopping
// isn't functionally complete.
//
// Grocery items are derived (aggregated from recipe components, no stable DB
// id — see GroceryList.kt's aggregateGroceryList), so checked state is keyed
// on the item's normalized name, same key aggregateGroceryList itself already
// uses for de-duplication.
// ============================================================================

private val Context.groceryCheckedDataStore by preferencesDataStore(name = "grocery_checked")
private val KEY_CHECKED = stringSetPreferencesKey("checked_item_keys")

@Singleton
class GroceryCheckedRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.groceryCheckedDataStore

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val checkedKeys: Flow<Set<String>> = storeData.map { it[KEY_CHECKED] ?: emptySet() }

    suspend fun setChecked(key: String, checked: Boolean) {
        store.edit { prefs ->
            val current = prefs[KEY_CHECKED] ?: emptySet()
            prefs[KEY_CHECKED] = if (checked) current + key else current - key
        }
    }

    /** Clears every checked mark — e.g. once a shopping trip is done. */
    suspend fun clearAll() {
        store.edit { prefs -> prefs.remove(KEY_CHECKED) }
    }

    /**
     * Overwrites the checked set wholesale — used by backup restore. A backup
     * predating this field (or one from an empty grocery list) has nothing to
     * restore, so this is a no-op rather than wiping today's checked items -
     * same convention HydrationRepository.importAll uses.
     */
    suspend fun restoreAll(keys: Set<String>) {
        if (keys.isEmpty()) return
        store.edit { prefs -> prefs[KEY_CHECKED] = keys }
    }
}
