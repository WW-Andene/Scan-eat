package fr.scanneat.data.repository.backup

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.customfood.CustomFoodDao
import fr.scanneat.data.local.db.medication.MedicationDao
import fr.scanneat.data.local.db.medication.MedicationLogDao
import fr.scanneat.data.local.db.price.PriceDao
import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.template.MealTemplateDao
import fr.scanneat.data.local.prefs.SecureFieldCipher
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.data.repository.planning.TemplateItem
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
    private val priceDao: PriceDao,
    private val biolismRepo: BiolismRepository,
    private val customFoodDao: CustomFoodDao,
    private val mealTemplateDao: MealTemplateDao,
    private val recipeDao: RecipeDao,
    private val scanHistoryDao: ScanHistoryDao,
    private val medicationDao: MedicationDao,
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
        // notes is Keystore-encrypted at rest (WeightRepository) - decrypt before
        // writing to a human-readable CSV, same as exportMedicationCsv's name field.
        val lines = rows.sortedBy { it.date }.map { e ->
            val notes = SecureFieldCipher.decryptOrNull(e.notes) ?: e.notes
            "${e.date},${e.weightKg},${csvField(notes)}"
        }
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
        // medicationName is Keystore-encrypted at rest (MedicationRepository) -
        // decrypt before writing to a human-readable CSV, or every row would
        // show unreadable ciphertext instead of the medication's name.
        val lines = rows.sortedBy { it.date }.map { e ->
            val name = SecureFieldCipher.decryptOrNull(e.medicationName) ?: e.medicationName
            "${e.date},${csvField(name)},${e.takenAt}"
        }
        return buildCsv("date,medication,takenAt", lines)
    }

    /** Exports completed fasts as CSV - see [exportWeightCsv]'s own doc comment. */
    suspend fun exportFastingCsv(): String {
        val rows = fastingRepo.history.first()
        val lines = rows.sortedBy { it.date }.map { c -> "${c.date},${c.targetHours},${c.achievedHours},${c.reached}" }
        return buildCsv("date,targetHours,achievedHours,reached", lines)
    }

    /**
     * Exports logged prices (Dépenses) as CSV - see [exportWeightCsv]'s own doc
     * comment. Every other domain this session added a logging feature for
     * (Weight/Activity/Hydration/Medication/Fasting) already got a CSV export
     * alongside its JSON-backup coverage; price_log had neither until now.
     */
    suspend fun exportPricesCsv(): String {
        val rows = priceDao.getAllForBackup()
        val lines = rows.sortedBy { it.date }.map { e ->
            "${e.date},${csvField(e.productName)},${e.category},${e.priceEuros},${e.weightG ?: ""},${e.pricePerKg ?: ""}"
        }
        return buildCsv("date,product,category,priceEuros,weightG,pricePerKg", lines)
    }

    /**
     * Exports "Mes Aliments" (custom/saved foods) as CSV - JSON backup already
     * covers this table (BackupRepository.exportToJson()) but, unlike Diary/
     * Weight/Activity/etc., it had no lightweight spreadsheet export either.
     */
    suspend fun exportCustomFoodsCsv(): String {
        val rows = customFoodDao.getAllForBackup()
        val nutritionAdapter = moshi.adapter(fr.scanneat.domain.model.NutritionPer100g::class.java)
        val lines = rows.sortedBy { it.name }.map { e ->
            val n = runCatching { nutritionAdapter.fromJson(e.nutritionJson) }.getOrNull()
            "${csvField(e.name)},${e.category},${(n?.energyKcal ?: 0.0).toInt()},${e.barcode ?: ""}"
        }
        return buildCsv("name,category,kcalPer100g,barcode", lines)
    }

    /**
     * Exports saved meal templates as CSV - see [exportCustomFoodsCsv]'s own doc
     * comment. Items are summarized (name + total kcal), not exploded one row
     * per ingredient, matching this export family's "one row per record" shape.
     */
    suspend fun exportMealTemplatesCsv(): String {
        val rows = mealTemplateDao.getAllForBackup()
        val itemsAdapter = moshi.adapter<List<TemplateItem>>(Types.newParameterizedType(List::class.java, TemplateItem::class.java))
        val lines = rows.sortedBy { it.name }.map { e ->
            val items = runCatching { itemsAdapter.fromJson(e.itemsJson) }.getOrNull().orEmpty()
            val totalKcal = items.sumOf { it.kcal }
            "${csvField(e.name)},${e.meal},${items.size},${totalKcal.toInt()},${e.favorite}"
        }
        return buildCsv("name,meal,itemCount,totalKcal,favorite", lines)
    }

    /** Exports saved recipes as CSV - see [exportCustomFoodsCsv]'s own doc comment. */
    suspend fun exportRecipesCsv(): String {
        val rows = recipeDao.getAllForBackup()
        val componentsAdapter = moshi.adapter<List<RecipeComponent>>(Types.newParameterizedType(List::class.java, RecipeComponent::class.java))
        val lines = rows.sortedBy { it.name }.map { e ->
            val components = runCatching { componentsAdapter.fromJson(e.componentsJson) }.getOrNull().orEmpty()
            val totalKcal = components.sumOf { it.kcal }
            val ingredients = components.joinToString("; ") { it.productName }
            "${csvField(e.name)},${e.servings},${totalKcal.toInt()},${csvField(ingredients)},${e.favorite}"
        }
        return buildCsv("name,servings,totalKcal,ingredients,favorite", lines)
    }

    /**
     * Exports scan history (every scanned/identified product, not just what was
     * logged to the diary - see [exportDiaryCsv] for that) as CSV. See
     * [exportCustomFoodsCsv]'s own doc comment for why this was missing.
     */
    suspend fun exportScanHistoryCsv(): String {
        val rows = scanHistoryDao.getAllForBackup()
        val lines = rows.sortedBy { it.scannedAt }.map { e ->
            "${e.scannedAt},${csvField(e.productName)},${e.barcode ?: ""},${e.score},${e.grade},${e.category},${e.favorite}"
        }
        return buildCsv("scannedAt,product,barcode,score,grade,category,favorite", lines)
    }

    /**
     * Exports medication *definitions* (name/dosage/schedule) as CSV - distinct
     * from [exportMedicationCsv]'s adherence log above. name/dosage/scheduleNote
     * are Keystore-encrypted at rest (see the encryption added earlier this
     * session) and must be decrypted before writing a human-readable CSV, same
     * as exportMedicationCsv's own name field.
     */
    suspend fun exportMedicationsCsv(): String {
        val rows = medicationDao.getAllForBackup()
        val lines = rows.sortedBy { it.name }.map { e ->
            val name         = SecureFieldCipher.decryptOrNull(e.name) ?: e.name
            val dosage       = SecureFieldCipher.decryptOrNull(e.dosage) ?: e.dosage
            val scheduleNote = SecureFieldCipher.decryptOrNull(e.scheduleNote) ?: e.scheduleNote
            "${csvField(name)},${csvField(dosage)},${csvField(scheduleNote)},${e.active}"
        }
        return buildCsv("name,dosage,scheduleNote,active", lines)
    }
}
