package fr.scanneat.data.repository.report

import fr.scanneat.data.local.db.report.MisclassificationReportDao
import fr.scanneat.data.local.db.report.MisclassificationReportEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MisclassificationReport(
    val id: String,
    val barcode: String?,
    val productName: String,
    val brand: String,
    val currentClassification: String,
    val correctedClassification: String,
    val note: String,
    val reportedAt: Long,
)

/** See MisclassificationReportEntity's own doc comment on why this is a
 *  local log, not a real submitted-to-a-server "community" correction. */
@Singleton
class MisclassificationReportRepository @Inject constructor(
    private val dao: MisclassificationReportDao,
) {
    fun observeAll(profileId: String = "default"): Flow<List<MisclassificationReportEntity>> =
        dao.observeAll(profileId)

    suspend fun report(
        barcode: String?,
        productName: String,
        brand: String,
        currentClassification: String,
        correctedClassification: String,
        note: String,
        profileId: String = "default",
    ) {
        dao.insert(
            MisclassificationReportEntity(
                id = UUID.randomUUID().toString(),
                barcode = barcode,
                productName = productName,
                brand = brand,
                currentClassification = currentClassification,
                correctedClassification = correctedClassification.trim(),
                note = note.trim(),
                reportedAt = System.currentTimeMillis(),
                profileId = profileId,
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    // ---- Backup export/import ----
    suspend fun exportAll(profileId: String = "default"): List<MisclassificationReportEntity> = dao.getAllForBackup(profileId)
    suspend fun importAll(entities: List<MisclassificationReportEntity>) {
        if (entities.isEmpty()) return
        dao.insertAll(entities)
    }
}
