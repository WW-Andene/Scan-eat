package fr.scanneat.data.repository.symptom

import fr.scanneat.data.local.db.symptom.SymptomDao
import fr.scanneat.data.local.db.symptom.SymptomEntity
import fr.scanneat.data.local.db.toIsoString
import fr.scanneat.data.local.db.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Open-ended rather than a closed enum - the whole point of a symptom
 *  journal is capturing things this app's author never anticipated, so a
 *  handful of common presets are offered but "autre" free text is always
 *  the fallback path, not a limitation. */
enum class SymptomType(val key: String) {
    BLOATING("bloating"), FATIGUE("fatigue"), SLEEP("sleep"), HEADACHE("headache"),
    STOMACH_PAIN("stomach_pain"), SKIN("skin"), OTHER("other");
    companion object {
        fun fromKey(k: String): SymptomType = entries.firstOrNull { it.key == k } ?: OTHER
    }
}

data class SymptomEntry(
    val id: String,
    val date: LocalDate,
    val type: SymptomType,
    val customLabel: String,
    val severity: Int,
    val notes: String,
    val loggedAt: Long,
)

@Singleton
class SymptomRepository @Inject constructor(
    private val dao: SymptomDao,
) {
    fun observeAll(profileId: String = "default"): Flow<List<SymptomEntry>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

    suspend fun add(
        date: LocalDate, type: SymptomType, customLabel: String, severity: Int, notes: String,
        profileId: String = "default",
    ) {
        dao.insert(
            SymptomEntity(
                id = UUID.randomUUID().toString(),
                date = date.toIsoString(),
                // customLabel folded into `type`'s stored string for OTHER
                // entries only - keeps the schema to one column instead of a
                // second nullable one that's meaningless for the 6 presets.
                type = if (type == SymptomType.OTHER && customLabel.isNotBlank()) customLabel.trim() else type.key,
                severity = severity.coerceIn(1, 5),
                notes = notes.trim(),
                loggedAt = System.currentTimeMillis(),
                profileId = profileId,
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    // ---- Backup export/import ----
    suspend fun exportAll(profileId: String = "default"): List<SymptomEntity> = dao.getAllForBackup(profileId)
    suspend fun importAll(entities: List<SymptomEntity>) {
        if (entities.isEmpty()) return
        dao.insertAll(entities)
    }
}

private fun SymptomEntity.toDomain(): SymptomEntry {
    val known = SymptomType.entries.firstOrNull { it.key == type }
    return SymptomEntry(
        id = id,
        date = date.toLocalDate(),
        type = known ?: SymptomType.OTHER,
        customLabel = if (known == null) type else "",
        severity = severity,
        notes = notes,
        loggedAt = loggedAt,
    )
}
