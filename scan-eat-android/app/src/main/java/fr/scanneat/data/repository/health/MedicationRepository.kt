package fr.scanneat.data.repository.health

import fr.scanneat.data.local.db.medication.MedicationDao
import fr.scanneat.data.local.db.medication.MedicationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
)

@Singleton
class MedicationRepository @Inject constructor(private val dao: MedicationDao) {

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
    ): Medication {
        val medication = Medication(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(), dosage = dosage.trim(), scheduleNote = scheduleNote.trim(),
            barcode = barcode, active = active,
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
    )

    private fun MedicationEntity.toDomain() = Medication(
        id = id, name = name, dosage = dosage, scheduleNote = scheduleNote, barcode = barcode, active = active,
    )
}
