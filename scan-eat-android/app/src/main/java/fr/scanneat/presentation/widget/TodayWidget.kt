package fr.scanneat.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.scanneat.R
import fr.scanneat.data.repository.health.HYD_GLASS_ML
import fr.scanneat.di.widgetEntryPoint
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.computeMetabolics
import fr.scanneat.domain.engine.dashboard.logStreakDays
import fr.scanneat.domain.engine.scoring.dailyTargets
import fr.scanneat.domain.engine.scoring.hasMinimalProfile
import fr.scanneat.domain.engine.scoring.withKcalOverride
import fr.scanneat.presentation.MainActivity
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.LocalColorblindMode
import fr.scanneat.presentation.ui.theme.semanticBlue
import fr.scanneat.util.localizedQuantityString
import fr.scanneat.util.localizedString
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

// ============================================================================
// TODAY WIDGET — home-screen glance at today's kcal progress + logging streak,
// the app's first presence outside its own UI. Mostly read-only (tapping the
// widget body opens the app) except the hydration row's "+250 mL" chip, which
// runs an ActionCallback to log a glass without opening the app at all - the
// one piece of today's data that's genuinely a single undifferentiated tap
// (see HydrationRepository.addGlass), unlike kcal logging which always needs
// picking a product/portion/meal slot and can't be meaningfully collapsed
// into one widget tap. Refreshes on the platform's own updatePeriodMillis
// cadence (see today_widget_info.xml) plus once whenever it's added/resized
// (GlanceAppWidgetReceiver's default onUpdate behavior) or a glass is logged
// (explicit update() call in AddGlassAction) - no separate WorkManager job
// needed, unlike ReminderWorker's notification checks, which run on their
// own schedule independent of any widget host.
// ============================================================================

class TodayWidget : GlanceAppWidget() {
    // Widget host resizeMode allows shrinking below the full layout's natural height
    // (see today_widget_info.xml) - without SizeMode.Responsive, a user shrinking the
    // widget just clips whatever doesn't fit (the hydration row first) rather than
    // getting a deliberately reflowed compact layout. The two sizes bracket the
    // resizeMode range: COMPACT_SIZE matches the manifest's minWidth/minHeight floor,
    // FULL_SIZE matches the current fixed layout's natural size.
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, FULL_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val prefs = entryPoint.userPreferences()
        val consumptionRepo = entryPoint.consumptionRepository()
        val hydrationRepo = entryPoint.hydrationRepository()
        val biolismRepo = entryPoint.biolismRepository()

        val lang = prefs.language.first()
        val colorblindMode = prefs.colorblindMode.first()
        val profile = prefs.profile.first()
        val today = LocalDate.now()
        val summary = consumptionRepo.observeDay(today).first()
        // Same Biolism-override rule as Dashboard/Diary (see DiaryViewModel.targets) -
        // without this, the widget silently showed a different kcal target than the
        // in-app screens for the same day, for any user with a valid Biolism profile
        // (the common case, since it auto-populates from the main Profile).
        val bioProfile = biolismRepo.profile.first()
        val baseTargets = if (hasMinimalProfile(profile)) dailyTargets(profile) else null
        val bioTdee = if (bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay else null
        val targets = baseTargets?.let { if (bioTdee != null) it.withKcalOverride(bioTdee, profile.goal) else it }
        // getAllLoggedDates() is a cheap DISTINCT-date query (no row hydration, no
        // nutrition JSON parsing) - unlike a bounded observeRange(), it can't silently
        // cap a real streak longer than whatever window was queried (see Dashboard's
        // identical fix for the same bug).
        val loggedDates = consumptionRepo.getAllLoggedDates()
        val streak = logStreakDays(loggedDates, today)
        val hydrationMl = hydrationRepo.observe(today).first()

        val kcal = summary.totals.energyKcal.roundToInt()
        val targetKcal = targets?.kcal?.roundToInt()
        val progress = if (targetKcal != null && targetKcal > 0) (kcal.toFloat() / targetKcal).coerceIn(0f, 1f) else 0f

        val kcalLabel = localizedString(context, lang, R.string.widget_today_kcal_label)
        val streakLabel = localizedQuantityString(context, lang, R.plurals.widget_today_streak, streak, streak)
        val noTargetLabel = localizedString(context, lang, R.string.widget_today_no_target)
        val hydrationLabel = localizedString(context, lang, R.string.widget_today_hydration_ml, hydrationMl)
        val addGlassLabel = localizedString(context, lang, R.string.widget_today_add_glass, HYD_GLASS_ML)
        // Widget hasn't tracked Dashboard/Diary's macro breakdown at all since it was
        // built - reuse the same single-letter abbreviations (P/G/L fr, P/C/F en)
        // Templates/Recipes cards already use, rather than a widget-only string.
        val macroLabel = "${localizedString(context, lang, R.string.macro_protein_abbr)} ${summary.totals.proteinG.roundToInt()}g · " +
            "${localizedString(context, lang, R.string.macro_carbs_abbr)} ${summary.totals.carbsG.roundToInt()}g · " +
            "${localizedString(context, lang, R.string.macro_fat_abbr)} ${summary.totals.fatG.roundToInt()}g"

        provideContent {
            // semanticBlue() (used by the hydration row below) reads LocalColorblindMode,
            // which otherwise silently defaults to "none" here — the in-app screens all
            // pick it up via ScanEatTheme's own CompositionLocalProvider, but the widget's
            // GlanceTheme never provided it, so a colorblind-mode user got the widget's
            // hydration chip in the un-adjusted blue regardless of their in-app setting.
            CompositionLocalProvider(LocalColorblindMode provides colorblindMode) {
                GlanceTheme {
                    // LocalSize.current resolves to whichever of sizeMode's declared buckets
                    // best fits the space the host actually gave this instance.
                    val compact = LocalSize.current.height < FULL_SIZE.height
                    TodayWidgetContent(
                        kcal = kcal,
                        targetKcal = targetKcal,
                        progress = progress,
                        kcalLabel = kcalLabel,
                        streakLabel = streakLabel,
                        noTargetLabel = noTargetLabel,
                        hydrationLabel = hydrationLabel,
                        addGlassLabel = addGlassLabel,
                        macroLabel = macroLabel,
                        compact = compact,
                    )
                }
            }
        }
    }

    companion object {
        val COMPACT_SIZE = DpSize(180.dp, 90.dp)
        // Grew from 130dp when the macro (P/C/F) row was added below the streak line -
        // see today_widget_info.xml's matching minHeight bump.
        val FULL_SIZE = DpSize(180.dp, 148.dp)
    }
}

@Composable
private fun TodayWidgetContent(
    kcal: Int,
    targetKcal: Int?,
    progress: Float,
    kcalLabel: String,
    streakLabel: String,
    noTargetLabel: String,
    hydrationLabel: String,
    addGlassLabel: String,
    macroLabel: String,
    compact: Boolean,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(kcalLabel, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        Spacer(modifier = GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$kcal", style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            if (targetKcal != null) {
                Text(" / $targetKcal", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 16.sp))
            }
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (targetKcal != null) {
            LinearProgressIndicator(
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                progress = progress,
                color = ColorProvider(AccentCoral),
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        } else {
            Text(noTargetLabel, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        }
        // Compact bucket (widget shrunk to roughly its minimum size): kcal + progress
        // only. Streak and the hydration quick-add row are the first things to go -
        // both are secondary to "how am I doing on calories today", the widget's
        // one-glance purpose - rather than letting them get silently clipped.
        if (!compact) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(streakLabel, style = TextStyle(color = ColorProvider(AccentCoral), fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(macroLabel, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            Spacer(modifier = GlanceModifier.height(10.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    hydrationLabel,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(semanticBlue()), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Box(
                    modifier = GlanceModifier
                        .background(ColorProvider(semanticBlue()))
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        // Own clickable target inside the whole-widget-opens-app Column above -
                        // this specific chip logs a glass in place instead of launching the app.
                        .clickable(actionRunCallback<AddGlassAction>()),
                ) {
                    Text(addGlassLabel, style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class AddGlassAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        widgetEntryPoint(context).hydrationRepository().addGlass()
        // update() is a member fun on GlanceAppWidget itself (no top-level import
        // exists for it, unlike updateAll()) - refreshes just this widget instance.
        TodayWidget().update(context, glanceId)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
