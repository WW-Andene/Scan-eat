package fr.scanneat.data.repository.health

import fr.scanneat.data.local.db.medication.MedicationDao
import fr.scanneat.data.local.db.medication.MedicationEntity
import fr.scanneat.data.local.db.medication.MedicationLogDao
import fr.scanneat.data.local.db.medication.MedicationLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// MEDICATION REPOSITORY — backs the "Traitement" tab. Barcode-scan lookup and
// a curated medication database are a separate, larger effort (see roadmap
// items on the medicament database + scanner integration); this repository
// covers manual entry/tracking, which the tab needs regardless of where the
// data comes from.
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

    fun observeAll(profileId: String = "default"): Flow<List<Medication>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

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
        val medication = Medication(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(), dosage = dosage.trim(), scheduleNote = scheduleNote.trim(),
            barcode = barcode, active = active, reminderOn = reminderOn, reminderTime = reminderTime,
        )
        dao.upsert(medication.toEntity(System.currentTimeMillis(), profileId))
        return medication
    }

    suspend fun setActive(medication: Medication, active: Boolean, profileId: String = "default") {
        dao.upsert(medication.copy(active = active).toEntity(System.currentTimeMillis(), profileId))
    }

    suspend fun delete(id: String) = dao.delete(id)

    private fun Medication.toEntity(createdAt: Long, profileId: String) = MedicationEntity(
        id = id, name = name, dosage = dosage, scheduleNote = scheduleNote,
        barcode = barcode, active = active, createdAt = createdAt, profileId = profileId,
        reminderOn = reminderOn, reminderTime = reminderTime,
    )

    private fun MedicationEntity.toDomain() = Medication(
        id = id, name = name, dosage = dosage, scheduleNote = scheduleNote, barcode = barcode, active = active,
        reminderOn = reminderOn, reminderTime = reminderTime,
    )

    // ── Adherence log ("I took this") ────────────────────────────────────────

    fun observeLogByDate(date: LocalDate, profileId: String = "default"): Flow<List<MedicationLogEntry>> =
        logDao.observeByDate(date.toString(), profileId).map { list -> list.map { it.toLogDomain() } }

    suspend fun getLogRange(from: LocalDate, to: LocalDate, profileId: String = "default"): List<MedicationLogEntry> =
        logDao.getRange(from.toString(), to.toString(), profileId).map { it.toLogDomain() }

    suspend fun logTaken(medication: Medication, date: LocalDate = LocalDate.now(), profileId: String = "default") {
        logDao.insert(MedicationLogEntity(
            id             = UUID.randomUUID().toString(),
            medicationId   = medication.id,
            medicationName = medication.name,
            date           = date.toString(),
            takenAt        = System.currentTimeMillis(),
            profileId      = profileId,
        ))
    }

    suspend fun deleteLogEntry(id: String) = logDao.delete(id)

    private fun MedicationLogEntity.toLogDomain() = MedicationLogEntry(
        id = id, medicationId = medicationId, medicationName = medicationName,
        date = LocalDate.parse(date), takenAt = takenAt,
    )
}
