package fr.scanneat.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.domain.engine.DietKey
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanneat_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    companion object {
        val KEY_API_KEY              = stringPreferencesKey("groq_api_key")
        val KEY_API_MODE             = stringPreferencesKey("api_mode")
        val KEY_SERVER_URL           = stringPreferencesKey("server_url")
        val KEY_LANGUAGE             = stringPreferencesKey("language")
        val KEY_THEME                = stringPreferencesKey("theme")
        val KEY_ACTIVE_PROFILE       = stringPreferencesKey("active_profile")
        // Profile — flat keys
        val KEY_PROFILE_NAME         = stringPreferencesKey("profile_name")
        val KEY_PROFILE_SEX          = stringPreferencesKey("profile_sex")
        val KEY_PROFILE_AGE          = intPreferencesKey("profile_age")
        val KEY_PROFILE_WEIGHT       = floatPreferencesKey("profile_weight_kg")
        val KEY_PROFILE_HEIGHT       = floatPreferencesKey("profile_height_cm")
        val KEY_PROFILE_GOAL_WEIGHT  = floatPreferencesKey("profile_goal_weight_kg")
        val KEY_PROFILE_DIET         = stringPreferencesKey("profile_diet")
        val KEY_PROFILE_ACTIVITY     = stringPreferencesKey("profile_activity")
        val KEY_PROFILE_GOAL         = stringPreferencesKey("profile_goal")
        val KEY_PROFILE_MENSTRUATING = booleanPreferencesKey("profile_menstruating")
        val KEY_PROFILE_ALLERGENS    = stringPreferencesKey("profile_allergens") // comma-separated
    }

    // ---- API / app settings ----

    val groqApiKey: Flow<String>  = store.data.map { it[KEY_API_KEY]    ?: "" }
    val apiMode: Flow<ApiMode>    = store.data.map { ApiMode.fromKey(it[KEY_API_MODE] ?: "direct") }
    val serverUrl: Flow<String>   = store.data.map { it[KEY_SERVER_URL] ?: "" }
    val language: Flow<String>    = store.data.map { it[KEY_LANGUAGE]   ?: "fr" }
    val theme: Flow<String>       = store.data.map { it[KEY_THEME]      ?: "oled" }

    suspend fun setGroqApiKey(key: String)  = store.edit { it[KEY_API_KEY]    = key }
    suspend fun setApiMode(mode: ApiMode)   = store.edit { it[KEY_API_MODE]   = mode.key }
    suspend fun setServerUrl(url: String)   = store.edit { it[KEY_SERVER_URL] = url }
    suspend fun setLanguage(lang: String)   = store.edit { it[KEY_LANGUAGE]   = lang }
    suspend fun setTheme(theme: String)     = store.edit { it[KEY_THEME]      = theme }

    // ---- Profile ----

    val profile: Flow<Profile> = store.data.map { p ->
        Profile(
            id             = p[KEY_ACTIVE_PROFILE] ?: "default",
            name           = p[KEY_PROFILE_NAME]   ?: "",
            sex            = Sex.values().firstOrNull { it.name == p[KEY_PROFILE_SEX] } ?: Sex.NOT_SPECIFIED,
            ageYears       = p[KEY_PROFILE_AGE],
            weightKg       = p[KEY_PROFILE_WEIGHT]?.toDouble(),
            heightCm       = p[KEY_PROFILE_HEIGHT]?.toDouble(),
            goalWeightKg   = p[KEY_PROFILE_GOAL_WEIGHT]?.toDouble(),
            diet           = DietKey.entries.firstOrNull { it.key == p[KEY_PROFILE_DIET] } ?: DietKey.NONE,
            activityLevel  = ActivityLevel.values().firstOrNull { it.name == p[KEY_PROFILE_ACTIVITY] } ?: ActivityLevel.MODERATELY_ACTIVE,
            goal           = Goal.values().firstOrNull { it.name == p[KEY_PROFILE_GOAL] } ?: Goal.MAINTAIN,
            isMenstruating = p[KEY_PROFILE_MENSTRUATING] ?: false,
            allergens      = p[KEY_PROFILE_ALLERGENS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet(),
        )
    }

    suspend fun saveProfile(profile: Profile) = store.edit { p ->
        p[KEY_ACTIVE_PROFILE]       = profile.id
        p[KEY_PROFILE_NAME]         = profile.name
        p[KEY_PROFILE_SEX]          = profile.sex.name
        profile.ageYears?.let      { p[KEY_PROFILE_AGE]         = it }
        profile.weightKg?.let      { p[KEY_PROFILE_WEIGHT]      = it.toFloat() }
        profile.heightCm?.let      { p[KEY_PROFILE_HEIGHT]      = it.toFloat() }
        profile.goalWeightKg?.let  { p[KEY_PROFILE_GOAL_WEIGHT] = it.toFloat() }
        p[KEY_PROFILE_DIET]         = profile.diet.key
        p[KEY_PROFILE_ACTIVITY]     = profile.activityLevel.name
        p[KEY_PROFILE_GOAL]         = profile.goal.name
        p[KEY_PROFILE_MENSTRUATING] = profile.isMenstruating
        p[KEY_PROFILE_ALLERGENS]    = profile.allergens.joinToString(",")
    }

    /** Convenience — update only weight (used by WeightRepository after logging). */
    suspend fun updateWeight(kg: Double) = store.edit { it[KEY_PROFILE_WEIGHT] = kg.toFloat() }
}

enum class ApiMode(val key: String) {
    DIRECT("direct"),
    SERVER("server");

    companion object {
        fun fromKey(k: String): ApiMode = values().firstOrNull { it.key == k } ?: DIRECT
    }
}
