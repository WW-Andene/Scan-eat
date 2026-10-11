package fr.scanneat.data.repository.biolism

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Backup / restore — Biolism previously had no export/import path at all:
 * its own profile override (waist/hip/neck/cycle-day, its own sex/age/height/
 * weight when the user explicitly diverges from the app-wide profile),
 * onboarding flag, session timer state, manual HR override, and the last-20
 * workout session history all lived only in this device's private
 * "biolism_prefs" DataStore, silently lost on a BackupRepository restore to
 * a new device. Mirrors the exportForBackup/importForBackup shape already
 * used by FastingRepository.
 *
 * Delegate used by [BiolismRepository], which forwards its own
 * `exportForBackup`/`importForBackup` members to this class so external
 * callers see no change in shape.
 */
private const val DEFAULT_PROFILE_ID = "default"

internal class BackupStore(
    private val store: DataStore<Preferences>,
    private val storeData: Flow<Preferences>,
    private val timerStateStore: TimerStateStore,
    private val sessionHistoryStore: SessionHistoryStore,
) {
    suspend fun exportForBackup(): BiolismRepository.BiolismBackupData {
        val p = storeData.first()
        // Profile-override fields are now namespaced per profile id (see
        // BiolismRepository.kSex/kAge/etc.'s own doc comment on why) - backup/
        // restore, like every other repository's getAllForBackup()/importForBackup()
        // this session's earlier backup audit found, only ever operates on the
        // "default" profile (BackupRepository never threads a real active-profile
        // id through), so this stays consistent with that existing, documented
        // app-wide scoping rather than introducing a new inconsistency here.
        return BiolismRepository.BiolismBackupData(
            onboarded          = p[BiolismRepository.K_ONBOARDED] ?: false,
            hasProfileOverride = p[BiolismRepository.kSex(DEFAULT_PROFILE_ID)] != null,
            sex         = p[BiolismRepository.kSex(DEFAULT_PROFILE_ID)],
            ageYears    = p[BiolismRepository.kAge(DEFAULT_PROFILE_ID)],
            heightCm    = p[BiolismRepository.kHeight(DEFAULT_PROFILE_ID)],
            weightKg    = p[BiolismRepository.kWeight(DEFAULT_PROFILE_ID)],
            activityId  = p[BiolismRepository.kActivity(DEFAULT_PROFILE_ID)],
            ethnicityId = p[BiolismRepository.kEthnicity(DEFAULT_PROFILE_ID)],
            waistCm     = p[BiolismRepository.kWaist(DEFAULT_PROFILE_ID)],
            hipCm       = p[BiolismRepository.kHip(DEFAULT_PROFILE_ID)],
            neckCm      = p[BiolismRepository.kNeck(DEFAULT_PROFILE_ID)],
            cycleDay    = p[BiolismRepository.kCycleDay(DEFAULT_PROFILE_ID)],
            timerState  = timerStateStore.timerState.first(),
            manualHR    = p[BiolismRepository.K_MANUAL_HR],
            sessions    = sessionHistoryStore.sessions.first(),
        )
    }

    suspend fun importForBackup(data: BiolismRepository.BiolismBackupData) {
        store.edit { p ->
            p[BiolismRepository.K_ONBOARDED] = data.onboarded
            // Previously only reapplied K_SEX/AGE/HEIGHT/WEIGHT/ACTIVITY when the
            // backup itself had an override, but never cleared an EXISTING local
            // override when the backup didn't - restoring a backup with no
            // override (or an old pre-v4 backup with none at all) left this
            // device's prior override in place, now permanently diverged from the
            // main profile the restore just overwrote. Always clear first, same
            // key set as clearProfileOverride(), then conditionally reapply.
            p.remove(BiolismRepository.kSex(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kAge(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kHeight(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kWeight(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kActivity(DEFAULT_PROFILE_ID))
            // Every field below previously landed straight in DataStore from the parsed
            // backup with no bound check - BiolismProfileScreen/BiolismOnboardingScreen
            // both clamp these same fields to the same ranges before ever constructing a
            // profile (age 1-120, height 50-250cm, weight 20-400kg, waist/hip 0-250cm,
            // neck 0-100cm), the same class of live-save-vs-import validation gap already
            // found and fixed this session for BackupRepository's weight_log/custom_foods
            // import path. A hand-edited or corrupted Biolism backup could otherwise feed
            // an implausible measurement straight into MetabolicsCalculator's BF%/TDEE math.
            if (data.hasProfileOverride) {
                data.sex?.let         { p[BiolismRepository.kSex(DEFAULT_PROFILE_ID)] = it }
                data.ageYears?.let    { p[BiolismRepository.kAge(DEFAULT_PROFILE_ID)] = it.coerceIn(1, 120) }
                data.heightCm?.let    { p[BiolismRepository.kHeight(DEFAULT_PROFILE_ID)] = it.coerceIn(50f, 250f) }
                data.weightKg?.let    { p[BiolismRepository.kWeight(DEFAULT_PROFILE_ID)] = it.coerceIn(20f, 400f) }
                data.activityId?.let  { p[BiolismRepository.kActivity(DEFAULT_PROFILE_ID)] = it }
            }
            // Biolism-exclusive body-composition fields (waist/hip/neck/ethnicity/
            // cycleDay) are independent of hasProfileOverride, which only tracks the
            // sex/age/height/weight main-profile override (see saveBodyMeasurements()'s
            // own doc comment - it never touches K_SEX) - gating them behind it too
            // meant a user who set ONLY these via the Biolism profile screen, never
            // diverging sex/age/height/weight from the main profile, had them silently
            // vanish on restore: exportForBackup() captures them unconditionally, but
            // importForBackup only ever reapplied them inside the hasProfileOverride
            // branch. Always clear first, same as the profile-override fields above.
            p.remove(BiolismRepository.kEthnicity(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kWaist(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kHip(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kNeck(DEFAULT_PROFILE_ID)); p.remove(BiolismRepository.kCycleDay(DEFAULT_PROFILE_ID))
            data.ethnicityId?.let { p[BiolismRepository.kEthnicity(DEFAULT_PROFILE_ID)] = it }
            data.waistCm?.let     { p[BiolismRepository.kWaist(DEFAULT_PROFILE_ID)] = it.coerceIn(0f, 250f) }
            data.hipCm?.let       { p[BiolismRepository.kHip(DEFAULT_PROFILE_ID)] = it.coerceIn(0f, 250f) }
            data.neckCm?.let      { p[BiolismRepository.kNeck(DEFAULT_PROFILE_ID)] = it.coerceIn(0f, 100f) }
            data.cycleDay?.let    { p[BiolismRepository.kCycleDay(DEFAULT_PROFILE_ID)] = it }
            if (data.manualHR != null) p[BiolismRepository.K_MANUAL_HR] = data.manualHR else p.remove(BiolismRepository.K_MANUAL_HR)
            // Previously only written `if (data.sessions.isNotEmpty())`, same "forgot to
            // clear on the empty case" bug this function's own doc comment above already
            // describes fixing for the profile-override fields - restoring a backup with
            // no (or fewer) sessions left this device's existing session history in place
            // instead of reverting to the backup's actual state.
            p[BiolismRepository.K_SESSIONS] = Json.encodeToString(data.sessions.map { SerializableSession.fromDomain(it) }.takeLast(20))
        }
        timerStateStore.saveTimerState(data.timerState)
    }
}
