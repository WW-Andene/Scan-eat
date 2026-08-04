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

    /** "fr" if the device's own locale is French, "en" otherwise — the two languages this app ships. */
    private fun defaultLanguage(): String =
        if (java.util.Locale.getDefault().language == "fr") "fr" else "en"

    companion object {
        val KEY_API_KEY              = stringPreferencesKey("groq_api_key")
        val KEY_CEREBRAS_API_KEY     = stringPreferencesKey("cerebras_api_key")
        val KEY_GROQ_MODEL           = stringPreferencesKey("groq_model")
        val KEY_API_MODE             = stringPreferencesKey("api_mode")
        val KEY_SERVER_URL           = stringPreferencesKey("server_url")
        val KEY_LANGUAGE             = stringPreferencesKey("language")
        val KEY_THEME                = stringPreferencesKey("theme")
        val KEY_ONBOARDING_COMPLETE  = booleanPreferencesKey("onboarding_complete")
        val KEY_DYSLEXIC_FONT        = booleanPreferencesKey("dyslexic_font")
        val KEY_COLORBLIND_MODE      = stringPreferencesKey("colorblind_mode")
        val KEY_USE_IMPERIAL_WEIGHT  = booleanPreferencesKey("use_imperial_weight")
        val KEY_BIOLISM_ADVANCED     = booleanPreferencesKey("biolism_advanced_view")
        val KEY_ANIMATED_BACKGROUND  = booleanPreferencesKey("animated_background")
        val KEY_ACTIVITY_BEST_STREAK = intPreferencesKey("activity_best_streak_days")
        val KEY_ACTIVE_PROFILE       = stringPreferencesKey("active_profile")
        val KEY_BUDGET_WEEKLY        = floatPreferencesKey("budget_weekly_euros")
        val KEY_BUDGET_PER_MEAL      = floatPreferencesKey("budget_per_meal_euros")
        val KEY_BUDGET_DAILY         = floatPreferencesKey("budget_daily_euros")
        val KEY_BUDGET_MONTHLY       = floatPreferencesKey("budget_monthly_euros")
        val KEY_IS_PREMIUM           = booleanPreferencesKey("is_premium")
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
        val KEY_PROFILE_CONDITIONS   = stringPreferencesKey("profile_conditions") // comma-separated
    }

    // ---- API / app settings ----

    // The stored value is Keystore-encrypted (see SecureFieldCipher) going forward.
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
        SecureFieldCipher.decryptOrNull(stored) ?: stored.also { plaintext ->
            store.edit { it[KEY_API_KEY] = SecureFieldCipher.encrypt(plaintext) }
        }
    }.distinctUntilChanged()
    /** Empty string means "use the built-in default" (see OcrParser.DEFAULT_MODEL). */
    val groqModel: Flow<String>   = storeData.map { it[KEY_GROQ_MODEL] ?: "" }.distinctUntilChanged()
    /**
     * Second provider, same purpose as groqApiKey — OcrParser tries Groq's model
     * list first and only falls through to Cerebras if every Groq attempt fails
     * (missing/invalid key, rate-limited, or the pinned models retired), so a
     * single vendor outage doesn't take scanning down entirely. Empty means
     * "not configured", which OcrParser treats as "skip this provider".
     */
    val cerebrasApiKey: Flow<String> = storeData.map { prefs ->
        val stored = prefs[KEY_CEREBRAS_API_KEY] ?: return@map ""
        SecureFieldCipher.decryptOrNull(stored) ?: stored.also { plaintext ->
            store.edit { it[KEY_CEREBRAS_API_KEY] = SecureFieldCipher.encrypt(plaintext) }
        }
    }.distinctUntilChanged()
    val apiMode: Flow<ApiMode>    = storeData.map { ApiMode.fromKey(it[KEY_API_MODE] ?: "direct") }.distinctUntilChanged()
    val serverUrl: Flow<String>   = storeData.map { it[KEY_SERVER_URL] ?: "" }.distinctUntilChanged()
    // Only fr/en are shipped (values/ and values-en/), so a device locale other than
    // French previously still got forced into French UI on first launch - the hardcoded
    // "fr" fallback ignored the device's own language entirely. Falls back to the
    // device's actual current locale (not a value captured once at process start),
    // same reasoning DateTimeConversions/formatDecimal already apply to Locale.
    val language: Flow<String>    = storeData.map { it[KEY_LANGUAGE] ?: defaultLanguage() }.distinctUntilChanged()
    val theme: Flow<String>       = storeData.map { it[KEY_THEME]      ?: "oled" }.distinctUntilChanged()
    val onboardingComplete: Flow<Boolean> = storeData.map { it[KEY_ONBOARDING_COMPLETE] ?: false }.distinctUntilChanged()
    val dyslexicFont: Flow<Boolean>       = storeData.map { it[KEY_DYSLEXIC_FONT] ?: false }.distinctUntilChanged()
    /** "none" | "deuteranopia" | "protanopia" | "tritanopia" */
    val colorblindMode: Flow<String>      = storeData.map { it[KEY_COLORBLIND_MODE] ?: "none" }.distinctUntilChanged()
    /**
     * WeightScreen's kg/lb display toggle was plain Compose `remember` state
     * with no backing store at all — every time the screen was left and
     * reopened (or the process was recreated), the unit silently reset to kg,
     * forcing anyone using lb to re-toggle it every visit.
     */
    val useImperialWeight: Flow<Boolean> = storeData.map { it[KEY_USE_IMPERIAL_WEIGHT] ?: false }.distinctUntilChanged()

    // R&D §X.0: Biolism's Data tab has 14 cards, several research-grade
    // (substrate flux/RQ, Fanger thermoregulation, ventilation physiology,
    // raw formula sheets) that can overwhelm a user who just wants BMR/body
    // composition/energy at a glance. Defaults to true (the existing full
    // view) so no current user sees anything change unless they opt out -
    // this is a progressive-disclosure option, not a removal.
    val biolismAdvancedView: Flow<Boolean> = storeData.map { it[KEY_BIOLISM_ADVANCED] ?: true }.distinctUntilChanged()

    /**
     * Settings > Appearance toggle for the screen's own ambient background wash
     * (see ambientGloom() in Glass.kt) drifting slowly instead of sitting fully
     * static. Defaults to false: it's a decorative, always-on-screen animation
     * (unlike e.g. biolismAdvancedView, a one-time layout choice), so it costs a
     * small continuous recomposition/redraw on every screen using ambientGloom
     * for as long as it stays enabled - opt-in rather than on-by-default.
     */
    val animatedBackground: Flow<Boolean> = storeData.map { it[KEY_ANIMATED_BACKGROUND] ?: false }.distinctUntilChanged()

    /**
     * Longest consecutive-day Activité streak ever reached - a persisted high-water
     * mark, unlike ActivityViewModel.streak (the *current* run, which resets to 0
     * the day after a missed workout). Needed to celebrate a new streak record the
     * moment it's set, the same one-time acknowledgment Fasting already has for
     * personalRecord - without a stored mark there's no way to tell "today's streak
     * is a new all-time best" from "today's streak merely continues an old one."
     */
    val activityBestStreak: Flow<Int> = storeData.map { it[KEY_ACTIVITY_BEST_STREAK] ?: 0 }.distinctUntilChanged()
    suspend fun setActivityBestStreak(days: Int) = store.edit { it[KEY_ACTIVITY_BEST_STREAK] = days }

    suspend fun setGroqApiKey(key: String)  = store.edit { it[KEY_API_KEY]    = SecureFieldCipher.encrypt(key) }
    suspend fun setCerebrasApiKey(key: String) = store.edit { it[KEY_CEREBRAS_API_KEY] = SecureFieldCipher.encrypt(key) }
    suspend fun setGroqModel(model: String) = store.edit { it[KEY_GROQ_MODEL] = model }
    suspend fun setApiMode(mode: ApiMode)   = store.edit { it[KEY_API_MODE]   = mode.key }
    suspend fun setServerUrl(url: String)   = store.edit { it[KEY_SERVER_URL] = url }
    suspend fun setLanguage(lang: String)   = store.edit { it[KEY_LANGUAGE]   = lang }
    suspend fun setTheme(theme: String)     = store.edit { it[KEY_THEME]      = theme }
    suspend fun setOnboardingComplete(v: Boolean) = store.edit { it[KEY_ONBOARDING_COMPLETE] = v }
    suspend fun setDyslexicFont(v: Boolean)       = store.edit { it[KEY_DYSLEXIC_FONT] = v }
    suspend fun setColorblindMode(mode: String)   = store.edit { it[KEY_COLORBLIND_MODE] = mode }
    suspend fun setUseImperialWeight(v: Boolean)  = store.edit { it[KEY_USE_IMPERIAL_WEIGHT] = v }
    suspend fun setBiolismAdvancedView(v: Boolean) = store.edit { it[KEY_BIOLISM_ADVANCED] = v }
    suspend fun setAnimatedBackground(v: Boolean)  = store.edit { it[KEY_ANIMATED_BACKGROUND] = v }

    /**
     * Freemium gate: Biolism (metabolism tracking) and AI-powered photo/label
     * scanning are the two paid-tier features - everything else stays free.
     * No real payment processor is wired up yet (Google Play Billing requires
     * Play Console product configuration first); this flag is the single
     * source of truth every gated screen reads, so wiring Billing later only
     * means replacing setIsPremium's caller, not touching any gated screen.
     */
    val isPremium: Flow<Boolean> = storeData.map { it[KEY_IS_PREMIUM] ?: false }.distinctUntilChanged()
    suspend fun setIsPremium(v: Boolean) = store.edit { it[KEY_IS_PREMIUM] = v }

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
            allergens      = decryptCsvSet(p[KEY_PROFILE_ALLERGENS]),
            healthConditions = decryptCsvSet(p[KEY_PROFILE_CONDITIONS]),
        )
    }.distinctUntilChanged()

    // Allergens and health conditions (diabetes, pregnancy, kidney disease,
    // allergies, ...) are real medical data, not incidental settings — stored
    // Keystore-encrypted like the API keys above, via the same
    // decrypt-or-fall-back-to-legacy-plaintext-and-re-encrypt pattern. A value
    // saved before this existed is still a plain comma-joined string;
    // decryptOrNull returns null for it and the raw value is used as-is (the
    // repair/re-encrypt happens on the next saveProfile, same as any other
    // profile edit — there's no dedicated migration path since, unlike the API
    // key flows, this one has no long-lived read-only collector to repair in place).
    private fun decryptCsvSet(stored: String?): Set<String> {
        val plaintext = stored?.let { SecureFieldCipher.decryptOrNull(it) ?: it } ?: return emptySet()
        return plaintext.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

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
        p[KEY_PROFILE_ALLERGENS]    = SecureFieldCipher.encrypt(profile.allergens.joinToString(","))
        p[KEY_PROFILE_CONDITIONS]   = SecureFieldCipher.encrypt(profile.healthConditions.joinToString(","))
    }

    /** Convenience — update only weight (used by WeightRepository after logging). */
    suspend fun updateWeight(kg: Double) = store.edit { it[KEY_PROFILE_WEIGHT] = kg.toFloat() }

    // ---- Expenses (Dépenses) budget targets ----
    // Both null means "no target set yet" - ExpensesScreen shows spend-only
    // (no over/under budget framing) until the user opts into one, same as
    // goalWeightKg's null-means-unset convention on Profile.
    val budgetWeeklyEuros: Flow<Double?> = storeData.map { it[KEY_BUDGET_WEEKLY]?.toDouble() }.distinctUntilChanged()
    val budgetPerMealEuros: Flow<Double?> = storeData.map { it[KEY_BUDGET_PER_MEAL]?.toDouble() }.distinctUntilChanged()
    // Day/Month targets, same null-means-unset convention as weekly/per-meal above -
    // added alongside the Jour/Semaine/Mois view toggle on ExpensesScreen, which
    // previously only had a budget to compare against in Week mode.
    val budgetDailyEuros: Flow<Double?> = storeData.map { it[KEY_BUDGET_DAILY]?.toDouble() }.distinctUntilChanged()
    val budgetMonthlyEuros: Flow<Double?> = storeData.map { it[KEY_BUDGET_MONTHLY]?.toDouble() }.distinctUntilChanged()
    suspend fun setBudgetWeeklyEuros(v: Double?) = store.edit { p -> v?.let { p[KEY_BUDGET_WEEKLY] = it.toFloat() } ?: p.remove(KEY_BUDGET_WEEKLY) }
    suspend fun setBudgetPerMealEuros(v: Double?) = store.edit { p -> v?.let { p[KEY_BUDGET_PER_MEAL] = it.toFloat() } ?: p.remove(KEY_BUDGET_PER_MEAL) }
    suspend fun setBudgetDailyEuros(v: Double?) = store.edit { p -> v?.let { p[KEY_BUDGET_DAILY] = it.toFloat() } ?: p.remove(KEY_BUDGET_DAILY) }
    suspend fun setBudgetMonthlyEuros(v: Double?) = store.edit { p -> v?.let { p[KEY_BUDGET_MONTHLY] = it.toFloat() } ?: p.remove(KEY_BUDGET_MONTHLY) }
}

enum class ApiMode(val key: String) {
    DIRECT("direct"),
    SERVER("server");

    companion object {
        fun fromKey(k: String): ApiMode = values().firstOrNull { it.key == k } ?: DIRECT
    }
}
