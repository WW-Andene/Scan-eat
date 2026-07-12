package fr.scanneat.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanneat_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    companion object {
        val KEY_API_KEY              = stringPreferencesKey("groq_api_key")
        val KEY_GROQ_MODEL           = stringPreferencesKey("groq_model")
        val KEY_API_MODE             = stringPreferencesKey("api_mode")
        val KEY_SERVER_URL           = stringPreferencesKey("server_url")
        val KEY_LANGUAGE             = stringPreferencesKey("language")
        val KEY_THEME                = stringPreferencesKey("theme")
        val KEY_ONBOARDING_COMPLETE  = booleanPreferencesKey("onboarding_complete")
        val KEY_DYSLEXIC_FONT        = booleanPreferencesKey("dyslexic_font")
        val KEY_COLORBLIND_MODE      = stringPreferencesKey("colorblind_mode")
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

    // The stored value is Keystore-encrypted (see ApiKeyCipher) going forward.
    // A value stored before this existed is still plaintext — decryptOrNull
    // returns null for it (not valid Base64(iv+ciphertext), or the GCM tag
    // fails to verify), so it's re-encrypted in place on first read after the
    // app updates, transparent to every caller of this flow.
    // distinctUntilChanged on every flow below - this whole file lives in one
    // Preferences blob, so store.edit{} for ANY key (e.g. saving weight) makes
    // DataStore re-emit ALL of these flows, not just the one that changed.
    // Without dedup, every unrelated write re-fires every collector here with
    // an unchanged value.
    val groqApiKey: Flow<String> = storeData.map { prefs ->
        val stored = prefs[KEY_API_KEY] ?: return@map ""
        ApiKeyCipher.decryptOrNull(stored) ?: stored.also { plaintext ->
            store.edit { it[KEY_API_KEY] = ApiKeyCipher.encrypt(plaintext) }
        }
    }.distinctUntilChanged()
    /** Empty string means "use the built-in default" (see OcrParser.DEFAULT_MODEL). */
    val groqModel: Flow<String>   = storeData.map { it[KEY_GROQ_MODEL] ?: "" }.distinctUntilChanged()
    val apiMode: Flow<ApiMode>    = storeData.map { ApiMode.fromKey(it[KEY_API_MODE] ?: "direct") }.distinctUntilChanged()
    val serverUrl: Flow<String>   = storeData.map { it[KEY_SERVER_URL] ?: "" }.distinctUntilChanged()
    val language: Flow<String>    = storeData.map { it[KEY_LANGUAGE]   ?: "fr" }.distinctUntilChanged()
    val theme: Flow<String>       = storeData.map { it[KEY_THEME]      ?: "oled" }.distinctUntilChanged()
    val onboardingComplete: Flow<Boolean> = storeData.map { it[KEY_ONBOARDING_COMPLETE] ?: false }.distinctUntilChanged()
    val dyslexicFont: Flow<Boolean>       = storeData.map { it[KEY_DYSLEXIC_FONT] ?: false }.distinctUntilChanged()
    /** "none" | "deuteranopia" | "protanopia" | "tritanopia" */
    val colorblindMode: Flow<String>      = storeData.map { it[KEY_COLORBLIND_MODE] ?: "none" }.distinctUntilChanged()

    suspend fun setGroqApiKey(key: String)  = store.edit { it[KEY_API_KEY]    = ApiKeyCipher.encrypt(key) }
    suspend fun setGroqModel(model: String) = store.edit { it[KEY_GROQ_MODEL] = model }
    suspend fun setApiMode(mode: ApiMode)   = store.edit { it[KEY_API_MODE]   = mode.key }
    suspend fun setServerUrl(url: String)   = store.edit { it[KEY_SERVER_URL] = url }
    suspend fun setLanguage(lang: String)   = store.edit { it[KEY_LANGUAGE]   = lang }
    suspend fun setTheme(theme: String)     = store.edit { it[KEY_THEME]      = theme }
    suspend fun setOnboardingComplete(v: Boolean) = store.edit { it[KEY_ONBOARDING_COMPLETE] = v }
    suspend fun setDyslexicFont(v: Boolean)       = store.edit { it[KEY_DYSLEXIC_FONT] = v }
    suspend fun setColorblindMode(mode: String)   = store.edit { it[KEY_COLORBLIND_MODE] = mode }

    // ---- Profile ----

    val profile: Flow<Profile> = storeData.map { p ->
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
    }.distinctUntilChanged()

    suspend fun saveProfile(profile: Profile) = store.edit { p ->
        p[KEY_ACTIVE_PROFILE]       = profile.id
        p[KEY_PROFILE_NAME]         = profile.name
        p[KEY_PROFILE_SEX]          = profile.sex.name
        // Clearing a field must actually remove the stored value — leaving the old
        // one behind made a blanked-out age/weight/height/goal silently reappear
        // the next time Profile was opened.
        profile.ageYears?.let      { p[KEY_PROFILE_AGE]         = it } ?: p.remove(KEY_PROFILE_AGE)
        profile.weightKg?.let      { p[KEY_PROFILE_WEIGHT]      = it.toFloat() } ?: p.remove(KEY_PROFILE_WEIGHT)
        profile.heightCm?.let      { p[KEY_PROFILE_HEIGHT]      = it.toFloat() } ?: p.remove(KEY_PROFILE_HEIGHT)
        profile.goalWeightKg?.let  { p[KEY_PROFILE_GOAL_WEIGHT] = it.toFloat() } ?: p.remove(KEY_PROFILE_GOAL_WEIGHT)
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
