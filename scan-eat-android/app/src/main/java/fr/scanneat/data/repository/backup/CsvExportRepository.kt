package fr.scanneat.data.repository.backup

import com.squareup.moshi.Moshi
import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.medication.MedicationLogDao
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.local.db.weight.WeightDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// CSV EXPORT REPOSITORY — split out of BackupRepository, which mixed the
// full-database JSON backup/restore responsibility with these per-domain
// spreadsheet-friendly exports. Distinct concern: JSON backup/restore is a
// complete, versioned, round-trippable snapshot; these are one-way,
// human-readable exports of a single table for opening in a spreadsheet.
// ============================================================================

@Singleton
class CsvExportRepository @Inject constructor(
    private val consumptionDao: ConsumptionDao,
    private val weightDao: WeightDao,
    private val activityDao: ActivityDao,
    private val medicationLogDao: MedicationLogDao,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
    private val biolismRepo: BiolismRepository,
    private val moshi: Moshi,
) {
    /**
     * RFC 4180 CSV field escaping with a formula-injection guard - Excel/Sheets
     * evaluate a quoted CSV cell's leading character regardless of the
     * surrounding quotes, so free text starting with =+-@ (which can originate
     * from the crowd-edited Open Food Facts database, not just something the
     * user themselves typed, e.g. a product name or Activité's custom sub-type
     * field) becomes a live formula the instant the exported file is opened in
     * a spreadsheet. Prefixing with a single quote is the standard mitigation
     * (OWASP's CSV-injection guidance) - spreadsheet apps treat a leading '
     * as "force this cell to plain text."
     */
    private fun csvField(value: String): String {
        val guarded = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
        return "\"${guarded.replace("\"", "\"\"")}\""
    }

    /** Shared RFC 4180 assembly (header + one line per already-formatted row) - every
     *  export*Csv() below differs only in its data source, columns, and row formatting. */
    private fun buildCsv(header: String, rows: List<String>): String = buildString {
        appendLine(header)
        rows.forEach { appendLine(it) }
    }

    /** Exports diary (consumption log) entries as RFC 4180 CSV — a spreadsheet-friendly
     * complement to the full JSON backup, covering date/meal/product/portion/kcal columns. */
    suspend fun exportDiaryCsv(): String {
        val rows = consumptionDao.getAllForBackup()
        val nutritionAdapter = moshi.adapter(fr.scanneat.domain.model.NutritionPer100g::class.java)
        val lines = rows.sortedWith(compareBy({ it.date }, { it.loggedAt })).map { e ->
            val nutrition = runCatching { nutritionAdapter.fromJson(e.nutritionJson) }.getOrNull()
            val kcalPer100 = nutrition?.energyKcal ?: 0.0
            val kcalTotal  = kcalPer100 * e.portionG / 100.0
            "${e.date},${e.mealSlot},${csvField(e.productName)},${e.portionG.toInt()},${kcalPer100.toInt()},${kcalTotal.toInt()}"
        }
        return buildCsv("date,meal,product,portionG,kcalPer100g,kcalTotal", lines)
    }

    /**
     * Exports Biolism workout sessions as CSV, same RFC 4180 spreadsheet-friendly
     * pattern as [exportDiaryCsv] - the JSON backup already carries this data via
     * BiolismRepository.exportForBackup(), but there was no lightweight
     * spreadsheet-friendly export for it the way the diary already has.
     */
    suspend fun exportBiolismSessionsCsv(): String {
        val rows = biolismRepo.sessions.first()
        val lines = rows.sortedBy { it.timestamp }.map { s ->
            "${s.timestamp},${s.elapsedSec.toInt()},${s.kcalBurned.toInt()},${"%.1f".format(java.util.Locale.US, s.kcalPerMin)}," +
                "${s.bmrDay.toInt()},${s.tdeeDay.toInt()},${csvField(s.activityLabel)},${s.ketosis},${s.startWeightKg},${s.endWeightKg}," +
                "${"%.3f".format(java.util.Locale.US, s.fatFrac)},${s.fatLostKg}"
        }
        return buildCsv("timestamp,elapsedSec,kcalBurned,kcalPerMin,bmrDay,tdeeDay,activity,ketosis,startWeightKg,endWeightKg,fatFrac,fatLostKg", lines)
    }

    /**
     * Exports weight log entries as CSV - Diary and Biolism sessions already had a
     * lightweight spreadsheet export, but Weight/Activity/Hydration/Medication/
     * Fasting had none despite each already exposing an equivalent JSON-backup
     * dataset (weightDao.getAllForBackup(), etc.) this can trivially wrap.
     */
    suspend fun exportWeightCsv(): String {
        val rows = weightDao.getAllForBackup()
        val lines = rows.sortedBy { it.date }.map { e -> "${e.date},${e.weightKg},${csvField(e.notes)}" }
        return buildCsv("date,weightKg,notes", lines)
    }

    /** Exports Activité log entries as CSV - see [exportWeightCsv]'s own doc comment. */
    suspend fun exportActivityCsv(): String {
        val rows = activityDao.getAllForBackup()
        // subType is genuine free text (Activité's custom sub-type field) - previously
        // written completely unquoted/unescaped, so a comma typed into that field
        // shifted every subsequent column for the row.
        val lines = rows.sortedBy { it.date }.map { e ->
            "${e.date},${e.type},${e.minutes},${e.kcalBurned}," +
                "${csvField(e.subType ?: "")},${e.sets ?: ""},${e.reps ?: ""},${e.distanceKm ?: ""},${e.weightUsedKg ?: ""}"
        }
        return buildCsv("date,type,minutes,kcalBurned,subType,sets,reps,distanceKm,weightUsedKg", lines)
    }

    /** Exports daily water intake as CSV - see [exportWeightCsv]'s own doc comment. */
    suspend fun exportHydrationCsv(): String {
        val rows = hydrationRepo.exportAll()
        val lines = rows.sortedBy { it.first }.map { (date, ml) -> "$date,$ml" }
        return buildCsv("date,ml", lines)
    }

    /** Exports the "dose taken" adherence log as CSV - see [exportWeightCsv]'s own doc comment. */
    suspend fun exportMedicationCsv(): String {
        val rows = medicationLogDao.getAllForBackup()
        val lines = rows.sortedBy { it.date }.map { e -> "${e.date},${csvField(e.medicationName)},${e.takenAt}" }
        return buildCsv("date,medication,takenAt", lines)
    }

    /** Exports completed fasts as CSV - see [exportWeightCsv]'s own doc comment. */
    suspend fun exportFastingCsv(): String {
        val rows = fastingRepo.history.first()
        val lines = rows.sortedBy { it.date }.map { c -> "${c.date},${c.targetHours},${c.achievedHours},${c.reached}" }
        return buildCsv("date,targetHours,achievedHours,reached", lines)
    }
}
