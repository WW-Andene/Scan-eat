package fr.scanneat.data.repository.health

import fr.scanneat.data.local.db.medication.MedicationDao
import fr.scanneat.data.local.db.medication.MedicationEntity
import fr.scanneat.data.local.db.medication.MedicationLogDao
import fr.scanneat.data.local.db.medication.MedicationLogEntity
import fr.scanneat.data.local.db.toIsoString
import fr.scanneat.data.local.db.toLocalDate
import fr.scanneat.data.local.prefs.SecureFieldCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// MEDICATION REPOSITORY — backs the "Traitement" tab. Barcode/name-scan lookup
// against the real BDPM database now exists (see domain.engine.medication.
// MedicationLookupDb, wired into ScanViewModel.saveDetectedMedication()); this
// repository is the persistence layer both that flow and manual entry share.
// ============================================================================

data class Medication(
    val id: String,
    val name: String,
    val dosage: String = "",
    val scheduleNote: String = "",
    val barcode: String? = null,
    val active: Boolean = true,
    // Fasting/Hydration/Weight all fire a reminder via ReminderWorker — a
    // medication's "schedule" was only ever a display-only free-text note
    // with no way to actually be reminded to take it.
    val reminderOn: Boolean = false,
    val reminderTime: String = "08:00",
    // Exists on the entity but was previously dropped in toDomain() - see
    // MedicationViewModel.adherenceStreak, which needs to know a medication's
    // own start date to avoid punishing the streak for the ordinary act of
    // adding a new active medication.
    val createdAt: Long = 0,
)

/** One "I took this" event on a given day - see MedicationLogEntity for why this needed its own table. */
data class MedicationLogEntry(
    val id: String,
    val medicationId: String,
    val medicationName: String,
    val date: LocalDate,
    val takenAt: Long,
)

@Singleton
class MedicationRepository @Inject constructor(
    private val dao: MedicationDao,
    private val logDao: MedicationLogDao,
) {

    // name/dosage/scheduleNote are encrypted at rest (see toEntity/toDomain), so
    // MedicationDao.observeAll can no longer ORDER BY name in SQL - ciphertext
    // has no relation to the plaintext's alphabetical order (a fresh random IV
    // means even the same name re-encrypts to different bytes each time). The
    // DAO only orders by `active` now; the name-ascending tiebreak the UI
    // still expects is reproduced here, after decryption.
    fun observeAll(profileId: String = "default"): Flow<List<Medication>> =
        dao.observeAll(profileId).map { list ->
            list.map { it.toDomain() }.sortedWith(compareByDescending<Medication> { it.active }.thenBy { it.name })
        }

    suspend fun save(
        name: String,
        dosage: String = "",
        scheduleNote: String = "",
        barcode: String? = null,
        active: Boolean = true,
        id: String? = null,
        profileId: String = "default",
        reminderOn: Boolean = false,
        reminderTime: String = "08:00",
    ): Medication {
        // Barcode-first dedup (see MedicationDao.upsertMedication) - without an
        // explicit id, rescanning the same medication's barcode now updates the
        // existing row in place instead of creating a duplicate with its own
        // reminder schedule.
        val entity = dao.upsertMedication(explicitId = id, barcode = barcode, profileId = profileId) { resolvedId, createdAt ->
            MedicationEntity(
                id = resolvedId, name = SecureFieldCipher.encrypt(name.trim()), dosage = SecureFieldCipher.encrypt(dosage.trim()),
                scheduleNote = SecureFieldCipher.encrypt(scheduleNote.trim()),
                barcode = barcode, active = active, createdAt = createdAt, profileId = profileId,
                reminderOn = reminderOn, reminderTime = reminderTime,
            )
        }
        return entity.toDomain()
    }

    suspend fun setActive(medication: Medication, active: Boolean, profileId: String = "default") {
        val createdAt = dao.findById(medication.id)?.createdAt ?: System.currentTimeMillis()
        dao.upsert(medication.copy(active = active).toEntity(createdAt, profileId))
    }

    suspend fun delete(id: String) = dao.delete(id)

    // name/dosage/scheduleNote can reveal a medical condition (antidepressant,
    // HIV medication, etc.) more directly than almost any other field in this
    // app, so they're Keystore-encrypted at rest like the profile's allergens/
    // healthConditions (SecureFieldCipher, same decrypt-or-fall-back-to-legacy-
    // plaintext-and-re-encrypt pattern - a row saved before this existed is
    // still plaintext and gets encrypted the next time it's saved).
    // barcode is left as-is: it's a public product identifier, not personal
    // data, and DAO lookups (findByBarcode/upsertMedication) match on it directly.
    private fun Medication.toEntity(createdAt: Long, profileId: String) = MedicationEntity(
        id = id, name = SecureFieldCipher.encrypt(name), dosage = SecureFieldCipher.encrypt(dosage),
        scheduleNote = SecureFieldCipher.encrypt(scheduleNote),
        barcode = barcode, active = active, createdAt = createdAt, profileId = profileId,
        reminderOn = reminderOn, reminderTime = reminderTime,
    )

    private fun MedicationEntity.toDomain() = Medication(
        id = id, name = SecureFieldCipher.decryptOrNull(name) ?: name,
        dosage = SecureFieldCipher.decryptOrNull(dosage) ?: dosage,
        scheduleNote = SecureFieldCipher.decryptOrNull(scheduleNote) ?: scheduleNote,
        barcode = barcode, active = active,
        reminderOn = reminderOn, reminderTime = reminderTime, createdAt = createdAt,
    )

    // ── Adherence log ("I took this") ────────────────────────────────────────

    // Unlike observeAll above, no SQL ORDER BY touches medicationName, so no
    // sort needs to move to Kotlin here - takenAt/date ordering is untouched
    // by encrypting this one field.
    fun observeLogByDate(date: LocalDate, profileId: String = "default"): Flow<List<MedicationLogEntry>> =
        logDao.observeByDate(date.toIsoString(), profileId).map { list -> list.mapNotNull { it.toLogDomain() } }

    suspend fun getLogRange(from: LocalDate, to: LocalDate, profileId: String = "default"): List<MedicationLogEntry> =
        logDao.getRange(from.toIsoString(), to.toIsoString(), profileId).mapNotNull { it.toLogDomain() }

    /** Flow counterpart to [getLogRange] — see MedicationLogDao.observeRange's own doc comment. */
    fun observeLogRange(from: LocalDate, to: LocalDate, profileId: String = "default"): Flow<List<MedicationLogEntry>> =
        logDao.observeRange(from.toIsoString(), to.toIsoString(), profileId).map { list -> list.mapNotNull { it.toLogDomain() } }

    suspend fun logTaken(medication: Medication, date: LocalDate = LocalDate.now(), profileId: String = "default") {
        logDao.insertIfAbsent(medication.id, date.toIsoString(), profileId) {
            MedicationLogEntity(
                id             = UUID.randomUUID().toString(),
                medicationId   = medication.id,
                // medicationName is a denormalized snapshot (see MedicationLogEntity's
                // own doc comment) of the same sensitive field as Medication.name -
                // encrypted here for the same reason.
                medicationName = SecureFieldCipher.encrypt(medication.name),
                date           = date.toIsoString(),
                takenAt        = System.currentTimeMillis(),
                profileId      = profileId,
            )
        }
        logDao.trim(MAX_LOG_HISTORY_ROWS, profileId)
    }

    suspend fun deleteLogEntry(id: String) = logDao.delete(id)

    private fun MedicationLogEntity.toLogDomain(): MedicationLogEntry? = runCatching {
        MedicationLogEntry(
            id = id, medicationId = medicationId, medicationName = SecureFieldCipher.decryptOrNull(medicationName) ?: medicationName,
            date = date.toLocalDate(), takenAt = takenAt,
        )
    }.onFailure {
        // Same silent-drop gap already fixed in WeightRepository/ActivityRepository
        // toDomain() (cycle 2, commit 2717150) - `date.toLocalDate()` is
        // LocalDate.parse under the hood and throws on a malformed date column.
        // This was the last sibling repo whose per-row mapper wasn't guarded, so
        // one corrupted medication_log row previously crashed the whole
        // observeLogByDate()/getLogRange()/observeLogRange() flow - taking down
        // Dashboard's otherTrackers, CalendarViewModel's markers/dayDetail, and
        // MedicationViewModel's adherence log entirely instead of just dropping
        // that one row.
        android.util.Log.w("MedicationRepository", "Failed to parse medication log entry id=$id", it)
    }.getOrNull()

    companion object {
        /** Same retention rationale as ScanRepository.MAX_HISTORY_ROWS. */
        const val MAX_LOG_HISTORY_ROWS = 5000
    }
}
