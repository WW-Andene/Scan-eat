package fr.scanneat.data.repository.backup

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import fr.scanneat.data.local.db.toLocalDate
import fr.scanneat.data.local.db.weight.WeightDao
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.domain.engine.dashboard.monthlyRollup
import fr.scanneat.domain.engine.dashboard.weeklyRollup
import fr.scanneat.domain.engine.scoring.dailyTargets
import fr.scanneat.domain.engine.scoring.hasMinimalProfile
import fr.scanneat.domain.model.Profile
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Generates a multi-page A4 "evolution report" PDF entirely with
 * android.graphics.pdf.PdfDocument + Canvas — no third-party PDF library
 * dependency, and no Context needed (PdfDocument is a plain framework
 * class), so this stays a pure repository like every other export path
 * here (CsvExportRepository, BackupRepository).
 *
 * Deliberately NOT a medical document despite the "scientific-style"
 * framing the feature request used — it's a formatted summary of the
 * user's OWN logged data (weight, intake, activity, hydration, fasting,
 * spend), not a diagnosis or clinical interpretation. Every page carries
 * the same disclaimer for that reason.
 */
@Singleton
class PdfReportRepository @Inject constructor(
    private val weightDao: WeightDao,
    private val consumptionRepo: ConsumptionRepository,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
    private val priceRepo: PriceRepository,
    private val prefs: UserPreferences,
) {
    private companion object {
        const val PAGE_W = 595   // A4 @ 72dpi
        const val PAGE_H = 842
        const val MARGIN = 48
    }

    suspend fun generate(language: String): PdfDocument {
        val profile = prefs.profile.first()
        val today = LocalDate.now()
        val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale(language))
        val isFr = language == "fr"

        val weights = weightDao.getAllForBackup().sortedBy { it.date }
        val diaryLast30 = consumptionRepo.observeRange(today.minusDays(29), today).first()
        val diaryLast7  = diaryLast30.filter { !it.date.isBefore(today.minusDays(6)) }
        val weekRollup  = weeklyRollup(diaryLast7, today)
        val monthRollup = monthlyRollup(diaryLast30, today)
        val targets = if (hasMinimalProfile(profile)) dailyTargets(profile) else null
        val hydration = hydrationRepo.exportAll().filter { (d, _) -> !d.isBefore(today.minusDays(29)) }
        val fasts = fastingRepo.history.first().filter { c ->
            runCatching { LocalDate.parse(c.date) }.getOrNull()?.let { !it.isBefore(today.minusDays(29)) } == true
        }
        val priceEntries = priceRepo.observeAll().first().filter { !it.date.isBefore(today.minusDays(29)) }

        val doc = PdfDocument()
        val body = ReportPageBuilder(doc, isFr, dateFmt)

        body.titlePage(profile, today)
        body.weightPage(weights, profile)
        body.nutritionPage(weekRollup, monthRollup, targets)
        body.wellnessPage(hydration, fasts, priceEntries)

        return doc
    }
}

/** All page-drawing logic — kept out of the repository body above so [PdfReportRepository] stays a thin data-gathering orchestrator. */
private class ReportPageBuilder(
    private val doc: PdfDocument,
    private val isFr: Boolean,
    private val dateFmt: DateTimeFormatter,
) {
    private val pageW = 595
    private val pageH = 842
    private val margin = 48f

    private val titlePaint = Paint().apply { color = Color.BLACK; textSize = 22f; isFakeBoldText = true; isAntiAlias = true }
    private val headerPaint = Paint().apply { color = Color.rgb(0xE0, 0x5A, 0x47); textSize = 15f; isFakeBoldText = true; isAntiAlias = true }
    private val bodyPaint = Paint().apply { color = Color.DKGRAY; textSize = 11f; isAntiAlias = true }
    private val labelPaint = Paint().apply { color = Color.GRAY; textSize = 9f; isAntiAlias = true }
    private val linePaint = Paint().apply { color = Color.rgb(0xE0, 0x5A, 0x47); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val gridPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

    private fun newPage(): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(pageW, pageH, doc.pages.size + 1).create()
        val page = doc.startPage(info)
        return page to page.canvas
    }

    private fun footer(canvas: Canvas) {
        val disclaimer = if (isFr)
            "Rapport généré automatiquement à partir des données saisies dans Scan'eat. Ne constitue pas un avis médical."
        else
            "Automatically generated from data logged in Scan'eat. Not a medical opinion or diagnosis."
        canvas.drawText(disclaimer, margin, pageH - 24f, labelPaint)
    }

    fun titlePage(profile: Profile, today: LocalDate) {
        val (page, canvas) = newPage()
        var y = 120f
        canvas.drawText(if (isFr) "Rapport d'évolution" else "Evolution Report", margin, y, titlePaint.apply { textSize = 26f })
        y += 30f
        canvas.drawText("Scan'eat — ${today.format(dateFmt)}", margin, y, bodyPaint)
        y += 50f
        canvas.drawLine(margin, y, pageW - margin, y, gridPaint)
        y += 40f
        canvas.drawText(if (isFr) "Profil" else "Profile", margin, y, headerPaint)
        y += 24f
        val rows = buildList {
            if (profile.name.isNotBlank()) add((if (isFr) "Nom" else "Name") to profile.name)
            profile.ageYears?.let { add((if (isFr) "Âge" else "Age") to "$it") }
            profile.weightKg?.let { add((if (isFr) "Poids actuel" else "Current weight") to "%.1f kg".format(Locale.US, it)) }
            profile.heightCm?.let { add((if (isFr) "Taille" else "Height") to "${it.roundToInt()} cm") }
            profile.goalWeightKg?.let { add((if (isFr) "Objectif de poids" else "Goal weight") to "%.1f kg".format(Locale.US, it)) }
            add((if (isFr) "Régime" else "Diet") to profile.diet.key.replace('_', ' '))
            add((if (isFr) "Objectif" else "Goal") to profile.goal.name.lowercase().replace('_', ' '))
        }
        rows.forEach { (label, value) ->
            canvas.drawText(label, margin, y, labelPaint)
            canvas.drawText(value, margin + 160f, y, bodyPaint)
            y += 20f
        }
        footer(canvas)
        doc.finishPage(page)
    }

    fun weightPage(weights: List<fr.scanneat.data.local.db.weight.WeightEntity>, profile: Profile) {
        val (page, canvas) = newPage()
        var y = 60f
        canvas.drawText(if (isFr) "Évolution du poids" else "Weight evolution", margin, y, headerPaint.apply { textSize = 18f })
        y += 32f
        if (weights.isEmpty()) {
            canvas.drawText(if (isFr) "Aucune donnée de poids enregistrée." else "No weight data logged yet.", margin, y, bodyPaint)
        } else {
            val first = weights.first()
            val last = weights.last()
            val delta = last.weightKg - first.weightKg
            val summaryLines = listOf(
                (if (isFr) "Première pesée" else "First entry") to "${first.date.toLocalDate().format(dateFmt)} — %.1f kg".format(Locale.US, first.weightKg),
                (if (isFr) "Dernière pesée" else "Latest entry") to "${last.date.toLocalDate().format(dateFmt)} — %.1f kg".format(Locale.US, last.weightKg),
                (if (isFr) "Variation totale" else "Total change") to "%+.1f kg".format(Locale.US, delta),
                (if (isFr) "Nombre de pesées" else "Total entries") to "${weights.size}",
            )
            summaryLines.forEach { (label, value) ->
                canvas.drawText(label, margin, y, labelPaint)
                canvas.drawText(value, margin + 160f, y, bodyPaint)
                y += 20f
            }
            y += 20f
            // Simple line chart of the last up-to-90 points
            val chartPoints = weights.takeLast(90)
            val chartRect = RectF(margin, y, pageW - margin, y + 220f)
            drawLineChart(canvas, chartRect, chartPoints.map { it.weightKg })
            y = chartRect.bottom + 30f
            canvas.drawText(
                if (isFr) "Dernières ${chartPoints.size} pesées" else "Last ${chartPoints.size} entries",
                margin, y, labelPaint,
            )
        }
        footer(canvas)
        doc.finishPage(page)
    }

    fun nutritionPage(
        weekRollup: fr.scanneat.domain.engine.dashboard.RollupResult,
        monthRollup: fr.scanneat.domain.engine.dashboard.RollupResult,
        targets: fr.scanneat.domain.engine.scoring.DailyTargets?,
    ) {
        val (page, canvas) = newPage()
        var y = 60f
        canvas.drawText(if (isFr) "Nutrition" else "Nutrition", margin, y, headerPaint.apply { textSize = 18f })
        y += 32f
        canvas.drawText(if (isFr) "Moyenne sur 7 jours" else "7-day average", margin, y, headerPaint.apply { textSize = 13f })
        y += 22f
        y = drawMacroRows(canvas, y, weekRollup.avg.kcal, weekRollup.avg.proteinG, weekRollup.avg.carbsG, weekRollup.avg.fatG, targets)
        y += 20f
        canvas.drawText(if (isFr) "Moyenne sur 30 jours" else "30-day average", margin, y, headerPaint.apply { textSize = 13f })
        y += 22f
        y = drawMacroRows(canvas, y, monthRollup.avg.kcal, monthRollup.avg.proteinG, monthRollup.avg.carbsG, monthRollup.avg.fatG, targets)
        y += 10f
        canvas.drawText(
            (if (isFr) "Jours enregistrés (30j) : " else "Days logged (30d): ") + "${monthRollup.daysLogged}/30",
            margin, y, bodyPaint,
        )
        footer(canvas)
        doc.finishPage(page)
    }

    private fun drawMacroRows(canvas: Canvas, startY: Float, kcal: Double, protein: Double, carbs: Double, fat: Double, targets: fr.scanneat.domain.engine.scoring.DailyTargets?): Float {
        var y = startY
        val rows = listOf(
            (if (isFr) "Calories" else "Calories") to "${kcal.roundToInt()} kcal" + (targets?.let { " / ${it.kcal.roundToInt()} kcal" } ?: ""),
            (if (isFr) "Protéines" else "Protein") to "${protein.roundToInt()} g",
            (if (isFr) "Glucides" else "Carbs") to "${carbs.roundToInt()} g",
            (if (isFr) "Lipides" else "Fat") to "${fat.roundToInt()} g",
        )
        rows.forEach { (label, value) ->
            canvas.drawText(label, margin + 12f, y, labelPaint)
            canvas.drawText(value, margin + 160f, y, bodyPaint)
            y += 20f
        }
        return y
    }

    fun wellnessPage(
        hydration: List<Pair<LocalDate, Int>>,
        fasts: List<fr.scanneat.data.repository.health.FastCompletion>,
        priceEntries: List<fr.scanneat.data.repository.expense.PriceEntry>,
    ) {
        val (page, canvas) = newPage()
        var y = 60f
        canvas.drawText(if (isFr) "Hydratation, jeûne et dépenses (30j)" else "Hydration, fasting & expenses (30d)", margin, y, headerPaint.apply { textSize = 18f })
        y += 32f

        canvas.drawText(if (isFr) "Hydratation" else "Hydration", margin, y, headerPaint.apply { textSize = 13f })
        y += 22f
        if (hydration.isEmpty()) {
            canvas.drawText(if (isFr) "Aucune donnée." else "No data.", margin + 12f, y, bodyPaint); y += 20f
        } else {
            val avgMl = hydration.sumOf { it.second } / hydration.size
            canvas.drawText((if (isFr) "Moyenne quotidienne : " else "Daily average: ") + "$avgMl mL", margin + 12f, y, bodyPaint)
            y += 20f
        }
        y += 16f

        canvas.drawText(if (isFr) "Jeûne intermittent" else "Intermittent fasting", margin, y, headerPaint.apply { textSize = 13f })
        y += 22f
        if (fasts.isEmpty()) {
            canvas.drawText(if (isFr) "Aucune donnée." else "No data.", margin + 12f, y, bodyPaint); y += 20f
        } else {
            val reached = fasts.count { it.reached }
            canvas.drawText(
                (if (isFr) "Jeûnes réussis : " else "Fasts completed: ") + "$reached/${fasts.size}",
                margin + 12f, y, bodyPaint,
            )
            y += 20f
        }
        y += 16f

        canvas.drawText(if (isFr) "Dépenses" else "Expenses", margin, y, headerPaint.apply { textSize = 13f })
        y += 22f
        if (priceEntries.isEmpty()) {
            canvas.drawText(if (isFr) "Aucune donnée." else "No data.", margin + 12f, y, bodyPaint); y += 20f
        } else {
            val total = priceEntries.sumOf { it.priceEuros }
            canvas.drawText(
                (if (isFr) "Total sur 30 jours : " else "30-day total: ") + "%.2f €".format(Locale.US, total),
                margin + 12f, y, bodyPaint,
            )
            y += 20f
        }

        footer(canvas)
        doc.finishPage(page)
    }

    /** Minimal line chart — axis + polyline, no library, matching this whole report's plain-Canvas approach. */
    private fun drawLineChart(canvas: Canvas, rect: RectF, values: List<Double>) {
        canvas.drawRect(rect, gridPaint.apply { style = Paint.Style.STROKE })
        if (values.size < 2) return
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0.01 } ?: 1.0
        val stepX = rect.width() / (values.size - 1)
        var prevX = rect.left
        var prevY = rect.bottom - ((values[0] - min) / range).toFloat() * rect.height()
        for (i in 1 until values.size) {
            val x = rect.left + stepX * i
            val yy = rect.bottom - ((values[i] - min) / range).toFloat() * rect.height()
            canvas.drawLine(prevX, prevY, x, yy, linePaint)
            prevX = x; prevY = yy
        }
    }
}
