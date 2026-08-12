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
import kotlinx.coroutines.flow.combine
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
        // User-requested: OLED/Dark/Light/Contrast (brightness/contrast) and a
        // color accent (Matcha/Lavande/Sunflower/Lazulite) are two different
        // axes - previously colorAccent was baked into `theme` itself as four
        // extra values, meaning OLED's true-black background and a color
        // accent were mutually exclusive. Independent so any accent can be
        // layered on top of any brightness mode.
        val KEY_COLOR_ACCENT         = stringPreferencesKey("color_accent")
        val KEY_ONBOARDING_COMPLETE  = booleanPreferencesKey("onboarding_complete")
        // app-audit §F: Pantry/Symptoms/Pregnancy tracking shipped this session
        // with zero notice to existing users - no onboarding mention, no "what's
        // new". Bumped whenever a batch of features warrants a one-time spotlight;
        // WhatsNewCard compares this against WHATS_NEW_CURRENT_VERSION.
        val KEY_WHATS_NEW_SEEN_VERSION = intPreferencesKey("whats_new_seen_version")
        val KEY_DYSLEXIC_FONT        = booleanPreferencesKey("dyslexic_font")
        val KEY_COLORBLIND_MODE      = stringPreferencesKey("colorblind_mode")
        val KEY_USE_IMPERIAL_WEIGHT  = booleanPreferencesKey("use_imperial_weight")
        val KEY_CURRENCY_SYMBOL      = stringPreferencesKey("currency_symbol")
        val KEY_BIOLISM_ADVANCED     = booleanPreferencesKey("biolism_advanced_view")
        val KEY_ANIMATED_BACKGROUND  = booleanPreferencesKey("animated_background")
        // User-reported: instant scan mode reset to off every time the Scan tab
        // was left and reopened - it was a plain in-ViewModel MutableStateFlow
        // with no backing store, unlike every other toggle in the app.
        val KEY_SCAN_INSTANT_MODE    = booleanPreferencesKey("scan_instant_mode")
        val KEY_ACTIVITY_BEST_STREAK = intPreferencesKey("activity_best_streak_days")
        val KEY_ACTIVITY_WEEKLY_GOAL_MIN = intPreferencesKey("activity_weekly_goal_minutes")
        // User-requested: a real sleep tracker with a configurable nightly-hours
        // goal - same "null means use the default" pattern as
        // activityWeeklyGoalMinutes above. 8.0h is the commonly-cited adult
        // recommendation (CDC/NIH) used as the fallback.
        val KEY_SLEEP_GOAL_HOURS = floatPreferencesKey("sleep_goal_hours")
        val KEY_ACTIVE_PROFILE       = stringPreferencesKey("active_profile")
        // R&D audit finding: profileId was threaded through every tracker
        // repository (Diary/Weight/Activity/...) but nothing in the app ever
        // created or switched a second one - pure dead scaffolding. CSV of
        // every profile id besides "default", which always exists implicitly
        // (it's this app's original single-profile identity, never explicitly
        // registered) - see profileIds' own doc comment.
        val KEY_PROFILE_IDS          = stringPreferencesKey("profile_ids")
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
        // User-requested: long-press-drag-to-reorder for Journal's header tabs and
        // the bottom nav — both stored as opaque comma-separated identifiers (enum
        // name / route) here, parsed against the current tab set at the call site,
        // so this data-layer file doesn't need to depend on presentation-layer
        // enums. Empty string means "no custom order saved yet, use the default".
        val KEY_DIARY_PRIMARY_TABS   = stringPreferencesKey("diary_primary_tabs")
        val KEY_NAV_TAB_ORDER        = stringPreferencesKey("nav_tab_order")
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
    val theme: Flow<String>       = storeData.map { it[KEY_THEME]      ?: "dark" }.distinctUntilChanged()
    val colorAccent: Flow<String> = storeData.map { it[KEY_COLOR_ACCENT] ?: "none" }.distinctUntilChanged()
    val onboardingComplete: Flow<Boolean> = storeData.map { it[KEY_ONBOARDING_COMPLETE] ?: false }.distinctUntilChanged()
    val whatsNewSeenVersion: Flow<Int> = storeData.map { it[KEY_WHATS_NEW_SEEN_VERSION] ?: 0 }.distinctUntilChanged()
    suspend fun setWhatsNewSeenVersion(v: Int) = store.edit { it[KEY_WHATS_NEW_SEEN_VERSION] = v }
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

    val scanInstantMode: Flow<Boolean> = storeData.map { it[KEY_SCAN_INSTANT_MODE] ?: false }.distinctUntilChanged()

    // Expenses previously hardcoded "€" at every display site - unusable outside
    // the eurozone. Defaults to "€" so existing users see no change.
    val currencySymbol: Flow<String> = storeData.map { it[KEY_CURRENCY_SYMBOL] ?: "€" }.distinctUntilChanged()

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

    /** Custom drag-and-drop order for Journal's always-visible header tabs — see [KEY_DIARY_PRIMARY_TABS]. */
    val diaryPrimaryTabsOrder: Flow<String> = storeData.map { it[KEY_DIARY_PRIMARY_TABS] ?: "" }.distinctUntilChanged()
    suspend fun setDiaryPrimaryTabsOrder(csv: String) = store.edit { it[KEY_DIARY_PRIMARY_TABS] = csv }

    /** Custom drag-and-drop order for the bottom nav's tabs — see [KEY_NAV_TAB_ORDER]. */
    val navTabOrder: Flow<String> = storeData.map { it[KEY_NAV_TAB_ORDER] ?: "" }.distinctUntilChanged()
    suspend fun setNavTabOrder(csv: String) = store.edit { it[KEY_NAV_TAB_ORDER] = csv }

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

    /** User-set override for the WHO 150min/week active-minutes goal, same
     *  "null means use the default" pattern as HydrationRepository.customGoalMl -
     *  Activity previously had no configurable goal at all, just the flat WHO
     *  figure hardcoded into ActivityWeeklyMinutesCard. */
    val activityWeeklyGoalMinutes: Flow<Int?> = storeData.map { it[KEY_ACTIVITY_WEEKLY_GOAL_MIN] }.distinctUntilChanged()
    suspend fun setActivityWeeklyGoalMinutes(minutes: Int?) = store.edit { prefs ->
        if (minutes == null) prefs.remove(KEY_ACTIVITY_WEEKLY_GOAL_MIN) else prefs[KEY_ACTIVITY_WEEKLY_GOAL_MIN] = minutes.coerceAtLeast(1)
    }

    val sleepGoalHours: Flow<Double> = storeData.map { (it[KEY_SLEEP_GOAL_HOURS] ?: 8.0f).toDouble() }.distinctUntilChanged()
    suspend fun setSleepGoalHours(hours: Double) = store.edit { prefs ->
        prefs[KEY_SLEEP_GOAL_HOURS] = hours.coerceIn(1.0, 16.0).toFloat()
    }

    suspend fun setGroqApiKey(key: String)  = store.edit { it[KEY_API_KEY]    = SecureFieldCipher.encrypt(key) }
    suspend fun setCerebrasApiKey(key: String) = store.edit { it[KEY_CEREBRAS_API_KEY] = SecureFieldCipher.encrypt(key) }
    suspend fun setGroqModel(model: String) = store.edit { it[KEY_GROQ_MODEL] = model }
    suspend fun setApiMode(mode: ApiMode)   = store.edit { it[KEY_API_MODE]   = mode.key }
    suspend fun setServerUrl(url: String)   = store.edit { it[KEY_SERVER_URL] = url }
    suspend fun setLanguage(lang: String)   = store.edit { it[KEY_LANGUAGE]   = lang }
    suspend fun setTheme(theme: String)     = store.edit { it[KEY_THEME]      = theme }
    suspend fun setColorAccent(accent: String) = store.edit { it[KEY_COLOR_ACCENT] = accent }
    suspend fun setOnboardingComplete(v: Boolean) = store.edit { it[KEY_ONBOARDING_COMPLETE] = v }
    suspend fun setDyslexicFont(v: Boolean)       = store.edit { it[KEY_DYSLEXIC_FONT] = v }
    suspend fun setColorblindMode(mode: String)   = store.edit { it[KEY_COLORBLIND_MODE] = mode }
    suspend fun setUseImperialWeight(v: Boolean)  = store.edit { it[KEY_USE_IMPERIAL_WEIGHT] = v }

    suspend fun setScanInstantMode(v: Boolean) = store.edit { it[KEY_SCAN_INSTANT_MODE] = v }
    suspend fun setCurrencySymbol(v: String)      = store.edit { it[KEY_CURRENCY_SYMBOL] = v.ifBlank { "€" } }
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
    //
    // R&D audit finding, now real: profileId was pervasive scaffolding (every
    // tracker repository accepted one) but the app only ever had ONE flat set
    // of profile_* keys, so nothing could actually switch. Each profile's
    // data now lives under its own id-namespaced keys (profileKey/
    // profileIntKey/profileFloatKey/profileBoolKey below); "default" - the
    // app's original single-profile identity - transparently falls back to
    // reading the legacy flat KEY_PROFILE_* keys until it's ever explicitly
    // saved under its own namespaced keys (see readProfile), so an existing
    // single-profile installation loses nothing and needs no migration step.

    private fun profileKey(id: String, field: String) = stringPreferencesKey("profile_${id}_$field")
    private fun profileIntKey(id: String, field: String) = intPreferencesKey("profile_${id}_$field")
    private fun profileFloatKey(id: String, field: String) = floatPreferencesKey("profile_${id}_$field")
    private fun profileBoolKey(id: String, field: String) = booleanPreferencesKey("profile_${id}_$field")

    private fun readProfile(p: Preferences, id: String): Profile {
        val hasOwnData = p[profileKey(id, "sex")] != null
        return when {
            hasOwnData -> Profile(
                id             = id,
                name           = p[profileKey(id, "name")] ?: "",
                sex            = Sex.values().firstOrNull { it.name == p[profileKey(id, "sex")] } ?: Sex.NOT_SPECIFIED,
                ageYears       = p[profileIntKey(id, "age")],
                weightKg       = p[profileFloatKey(id, "weight")]?.toDouble(),
                heightCm       = p[profileFloatKey(id, "height")]?.toDouble(),
                goalWeightKg   = p[profileFloatKey(id, "goal_weight")]?.toDouble(),
                diet           = DietKey.entries.firstOrNull { it.key == p[profileKey(id, "diet")] } ?: DietKey.NONE,
                activityLevel  = ActivityLevel.values().firstOrNull { it.name == p[profileKey(id, "activity")] } ?: ActivityLevel.MODERATELY_ACTIVE,
                goal           = Goal.values().firstOrNull { it.name == p[profileKey(id, "goal")] } ?: Goal.MAINTAIN,
                isMenstruating = p[profileBoolKey(id, "menstruating")] ?: false,
                allergens      = decryptCsvSet(p[profileKey(id, "allergens")]),
                healthConditions = decryptCsvSet(p[profileKey(id, "conditions")]),
                pregnancyStartDate = p[profileKey(id, "pregnancy_start")]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                fatLevel       = BodyCompositionLevel.entries.firstOrNull { it.name == p[profileKey(id, "fat_level")] },
                muscleLevel    = BodyCompositionLevel.entries.firstOrNull { it.name == p[profileKey(id, "muscle_level")] },
            )
            // Legacy fallback — the only profile storage that existed before
            // multi-profile support, read as-is until "default" is ever saved
            // under its own namespaced keys (saveProfile below always writes
            // the new-style keys, so this branch stops being reached the
            // first time the user opens and saves Profile).
            id == "default" -> Profile(
                id             = "default",
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
            else -> Profile(id = id) // freshly created, not yet saved
        }
    }

    private fun storedProfileIds(p: Preferences): List<String> =
        p[KEY_PROFILE_IDS]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    val profile: Flow<Profile> = storeData.map { p -> readProfile(p, p[KEY_ACTIVE_PROFILE] ?: "default") }.distinctUntilChanged()

    val activeProfileId: Flow<String> = storeData.map { it[KEY_ACTIVE_PROFILE] ?: "default" }.distinctUntilChanged()

    /** Every profile id that exists, "default" always first — the id every
     *  tracker repository's own `profileId: String = "default"` default
     *  parameter already assumes. */
    val profileIds: Flow<List<String>> = storeData.map { p -> (listOf("default") + storedProfileIds(p)).distinct() }.distinctUntilChanged()

    /** Full Profile for every registered id, for the profile-switcher UI. */
    val profileList: Flow<List<Profile>> = combine(profileIds, storeData) { ids, p -> ids.map { id -> readProfile(p, id) } }.distinctUntilChanged()

    suspend fun setActiveProfileId(id: String) = store.edit { it[KEY_ACTIVE_PROFILE] = id }

    /** Creates a new, empty profile and registers it (does not switch to it -
     *  the caller decides, same as every other explicit-confirm write in this
     *  app). Returns the generated id. */
    suspend fun createProfile(name: String): String {
        val id = java.util.UUID.randomUUID().toString()
        store.edit { p ->
            p[KEY_PROFILE_IDS] = (storedProfileIds(p) + id).joinToString(",")
            p[profileKey(id, "name")] = name
            // Marks this id as having "own data" (see readProfile's hasOwnData
            // check) so it never falls into the id=="default" legacy branch,
            // which only applies to the literal "default" id anyway - written
            // for consistency and so an immediate profileList read shows a
            // real Sex value rather than one only implied by the field's absence.
            p[profileKey(id, "sex")] = Sex.NOT_SPECIFIED.name
        }
        return id
    }

    /** Renames an existing profile (including "default", whose display name
     *  is otherwise blank/legacy-derived - see readProfile). */
    suspend fun renameProfile(id: String, name: String) = store.edit { p ->
        p[profileKey(id, "name")] = name
    }

    /** Removes [id] from the switcher and its own stored data. Never touches
     *  tracker rows (Diary/Weight/Activity/... still tagged with this
     *  profileId) - same conservative-deletion stance the rest of this app
     *  takes elsewhere (e.g. a removed health-condition key stays stored,
     *  just unread) rather than risking silently destroying logged history.
     *  "default" can't be deleted - it always exists implicitly. Switches the
     *  active profile back to "default" first if [id] was the active one. */
    suspend fun deleteProfile(id: String) {
        if (id == "default") return
        store.edit { p ->
            p[KEY_PROFILE_IDS] = storedProfileIds(p).filter { it != id }.joinToString(",")
            listOf("name", "sex", "diet", "activity", "goal", "allergens", "conditions", "fat_level", "muscle_level").forEach { p.remove(profileKey(id, it)) }
            p.remove(profileIntKey(id, "age"))
            p.remove(profileFloatKey(id, "weight"))
            p.remove(profileFloatKey(id, "height"))
            p.remove(profileFloatKey(id, "goal_weight"))
            p.remove(profileBoolKey(id, "menstruating"))
            if (p[KEY_ACTIVE_PROFILE] == id) p[KEY_ACTIVE_PROFILE] = "default"
        }
    }

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
        val id = profile.id
        p[KEY_ACTIVE_PROFILE] = id
        if (id != "default" && id !in storedProfileIds(p)) p[KEY_PROFILE_IDS] = (storedProfileIds(p) + id).joinToString(",")
        p[profileKey(id, "name")] = profile.name
        p[profileKey(id, "sex")]  = profile.sex.name
        // Clearing a field must actually remove the stored value — leaving the old
        // one behind made a blanked-out age/weight/height/goal silently reappear
        // the next time Profile was opened.
        profile.ageYears?.let      { p[profileIntKey(id, "age")]           = it } ?: p.remove(profileIntKey(id, "age"))
        profile.weightKg?.let      { p[profileFloatKey(id, "weight")]      = it.toFloat() } ?: p.remove(profileFloatKey(id, "weight"))
        profile.heightCm?.let      { p[profileFloatKey(id, "height")]      = it.toFloat() } ?: p.remove(profileFloatKey(id, "height"))
        profile.goalWeightKg?.let  { p[profileFloatKey(id, "goal_weight")] = it.toFloat() } ?: p.remove(profileFloatKey(id, "goal_weight"))
        p[profileKey(id, "diet")]         = profile.diet.key
        p[profileKey(id, "activity")]     = profile.activityLevel.name
        p[profileKey(id, "goal")]         = profile.goal.name
        p[profileBoolKey(id, "menstruating")] = profile.isMenstruating
        p[profileKey(id, "allergens")]    = SecureFieldCipher.encrypt(profile.allergens.joinToString(","))
        p[profileKey(id, "conditions")]   = SecureFieldCipher.encrypt(profile.healthConditions.joinToString(","))
        profile.pregnancyStartDate?.let { p[profileKey(id, "pregnancy_start")] = it.toString() } ?: p.remove(profileKey(id, "pregnancy_start"))
        profile.fatLevel?.let    { p[profileKey(id, "fat_level")]    = it.name } ?: p.remove(profileKey(id, "fat_level"))
        profile.muscleLevel?.let { p[profileKey(id, "muscle_level")] = it.name } ?: p.remove(profileKey(id, "muscle_level"))
    }

    /** Convenience — update only weight (used by WeightRepository after logging
     *  for the currently active profile). */
    suspend fun updateWeight(kg: Double) = store.edit { p ->
        val id = p[KEY_ACTIVE_PROFILE] ?: "default"
        p[profileFloatKey(id, "weight")] = kg.toFloat()
    }

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
