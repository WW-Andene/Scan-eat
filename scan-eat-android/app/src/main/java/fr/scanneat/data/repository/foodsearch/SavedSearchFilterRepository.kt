package fr.scanneat.data.repository.foodsearch

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-requested: "filtres sauvegardés dans Recherche (ex: 'sans sulfates'
 * en un tap)" - a named preset of query text + the two existing filter axes
 * (FoodSearchViewModel.filter/gradeFilter), reapplied in one tap instead of
 * re-typing/re-selecting the same combination every time. [filterName]/
 * [gradeName] are stored as plain strings (FoodSearchFilter.name / Grade.name)
 * rather than the enums themselves - kotlinx.serialization needs each enum
 * type explicitly @Serializable, and neither lives in this package; the
 * ViewModel converts both directions instead.
 */
@Serializable
data class SavedSearchFilter(
    val id: Int,
    val label: String,
    val query: String,
    val filterName: String,
    val gradeName: String?,
)

private val Context.savedSearchFiltersDataStore by preferencesDataStore(name = "saved_search_filters")

@Singleton
class SavedSearchFilterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.savedSearchFiltersDataStore

    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private companion object {
        val KEY_FILTERS = stringPreferencesKey("filters")
        val KEY_NEXT_ID = intPreferencesKey("next_id")
    }

    private fun decode(p: Preferences): List<SavedSearchFilter> =
        runCatching { Json.decodeFromString<List<SavedSearchFilter>>(p[KEY_FILTERS] ?: "[]") }.getOrElse { emptyList() }

    val filters: Flow<List<SavedSearchFilter>> = storeData.map(::decode).distinctUntilChanged()

    suspend fun add(label: String, query: String, filterName: String, gradeName: String?) = store.edit { p ->
        val list = decode(p)
        val nextId = p[KEY_NEXT_ID] ?: 1
        p[KEY_NEXT_ID] = nextId + 1
        p[KEY_FILTERS] = Json.encodeToString(list + SavedSearchFilter(nextId, label.trim(), query, filterName, gradeName))
    }

    suspend fun delete(id: Int) = store.edit { p ->
        p[KEY_FILTERS] = Json.encodeToString(decode(p).filter { it.id != id })
    }

    // ---- Backup export/import ----
    suspend fun exportAll(): List<SavedSearchFilter> = decode(storeData.first())

    /** Merged with (not replacing) whatever presets already exist locally,
     *  keyed by id - same non-destructive intent every other importAll() in
     *  this app already follows. */
    suspend fun importAll(entries: List<SavedSearchFilter>) {
        if (entries.isEmpty()) return
        store.edit { p ->
            val existing = decode(p)
            val merged = existing.filter { e -> entries.none { it.id == e.id } } + entries
            p[KEY_FILTERS] = Json.encodeToString(merged)
            val maxId = merged.maxOfOrNull { it.id } ?: 0
            if ((p[KEY_NEXT_ID] ?: 1) <= maxId) p[KEY_NEXT_ID] = maxId + 1
        }
    }
}
