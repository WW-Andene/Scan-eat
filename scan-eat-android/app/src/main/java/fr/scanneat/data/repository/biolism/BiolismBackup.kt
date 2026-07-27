package fr.scanneat.data.repository.biolism

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
internal class BackupStore(
    private val store: DataStore<Preferences>,
    private val storeData: Flow<Preferences>,
    private val timerStateStore: TimerStateStore,
    private val sessionHistoryStore: SessionHistoryStore,
) {
    suspend fun exportForBackup(): BiolismRepository.BiolismBackupData {
        val p = storeData.first()
        return BiolismRepository.BiolismBackupData(
            onboarded          = p[BiolismRepository.K_ONBOARDED] ?: false,
            hasProfileOverride = p[BiolismRepository.K_SEX] != null,
            sex         = p[BiolismRepository.K_SEX],
            ageYears    = p[BiolismRepository.K_AGE],
            heightCm    = p[BiolismRepository.K_HEIGHT],
            weightKg    = p[BiolismRepository.K_WEIGHT],
            activityId  = p[BiolismRepository.K_ACTIVITY],
            ethnicityId = p[BiolismRepository.K_ETHNICITY],
            waistCm     = p[BiolismRepository.K_WAIST],
            hipCm       = p[BiolismRepository.K_HIP],
            neckCm      = p[BiolismRepository.K_NECK],
            cycleDay    = p[BiolismRepository.K_CYCLE_DAY],
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
            p.remove(BiolismRepository.K_SEX); p.remove(BiolismRepository.K_AGE); p.remove(BiolismRepository.K_HEIGHT); p.remove(BiolismRepository.K_WEIGHT); p.remove(BiolismRepository.K_ACTIVITY)
            if (data.hasProfileOverride) {
                data.sex?.let         { p[BiolismRepository.K_SEX] = it }
                data.ageYears?.let    { p[BiolismRepository.K_AGE] = it }
                data.heightCm?.let    { p[BiolismRepository.K_HEIGHT] = it }
                data.weightKg?.let    { p[BiolismRepository.K_WEIGHT] = it }
                data.activityId?.let  { p[BiolismRepository.K_ACTIVITY] = it }
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
            p.remove(BiolismRepository.K_ETHNICITY); p.remove(BiolismRepository.K_WAIST); p.remove(BiolismRepository.K_HIP); p.remove(BiolismRepository.K_NECK); p.remove(BiolismRepository.K_CYCLE_DAY)
            data.ethnicityId?.let { p[BiolismRepository.K_ETHNICITY] = it }
            data.waistCm?.let     { p[BiolismRepository.K_WAIST] = it }
            data.hipCm?.let       { p[BiolismRepository.K_HIP] = it }
            data.neckCm?.let      { p[BiolismRepository.K_NECK] = it }
            data.cycleDay?.let    { p[BiolismRepository.K_CYCLE_DAY] = it }
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
