package fr.scanneat.data.repository.reaction

import fr.scanneat.data.local.db.reaction.AllergicReactionDao
import fr.scanneat.data.local.db.reaction.AllergicReactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AllergicReaction(
    val id: String,
    val date: LocalDate,
    val productName: String,
    val barcode: String?,
    val ingredients: List<String>,
    val symptoms: String,
    val severity: Int,
    val epinephrineUsed: Boolean,
    val notes: String,
)

/** Roadmap N10: allergic-reaction log. */
@Singleton
class AllergicReactionRepository @Inject constructor(private val dao: AllergicReactionDao) {
    fun observeAll(profileId: String): Flow<List<AllergicReaction>> = dao.observeAll(profileId).map { list ->
        list.mapNotNull { e ->
            val date = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
            AllergicReaction(e.id, date, e.productName, e.barcode,
                e.ingredientsText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                e.symptoms, e.severity, e.epinephrineUsed, e.notes)
        }
    }

    suspend fun add(
        date: LocalDate, productName: String, barcode: String?, ingredients: List<String>,
        symptoms: String, severity: Int, epinephrineUsed: Boolean, notes: String, profileId: String,
    ) = dao.insert(AllergicReactionEntity(
        id = UUID.randomUUID().toString(), date = date.toString(), productName = productName.trim(), barcode = barcode,
        ingredientsText = ingredients.joinToString(", "), symptoms = symptoms.trim(), severity = severity.coerceIn(1, 3),
        epinephrineUsed = epinephrineUsed, notes = notes.trim(), loggedAt = System.currentTimeMillis(), profileId = profileId,
    ))

    suspend fun delete(id: String) = dao.delete(id)
}
