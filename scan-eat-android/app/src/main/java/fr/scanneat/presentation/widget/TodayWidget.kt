package fr.scanneat.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import fr.scanneat.di.widgetEntryPoint
import fr.scanneat.domain.engine.dashboard.logStreakDays
import fr.scanneat.domain.engine.scoring.dailyTargets
import fr.scanneat.presentation.MainActivity
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.util.localizedQuantityString
import fr.scanneat.util.localizedString
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

// ============================================================================
// TODAY WIDGET — home-screen glance at today's kcal progress + logging streak,
// the app's first presence outside its own UI. Read-only: tapping it opens
// the app rather than trying to squeeze logging into a widget's constrained
// interaction surface. Refreshes on the platform's own updatePeriodMillis
// cadence (see today_widget_info.xml) plus once whenever it's added/resized
// (GlanceAppWidgetReceiver's default onUpdate behavior) - no separate
// WorkManager job needed, unlike ReminderWorker's notification checks, which
// run on their own schedule independent of any widget host.
// ============================================================================

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val prefs = entryPoint.userPreferences()
        val consumptionRepo = entryPoint.consumptionRepository()

        val lang = prefs.language.first()
        val profile = prefs.profile.first()
        val today = LocalDate.now()
        val summary = consumptionRepo.observeDay(today).first()
        val targets = dailyTargets(profile)
        // 30 days is enough to establish "today extends yesterday's streak" without
        // an unbounded range query every time a widget host asks for a refresh.
        val recentEntries = consumptionRepo.observeRange(today.minusDays(30), today).first()
        val streak = logStreakDays(recentEntries, today)

        val kcal = summary.totals.energyKcal.roundToInt()
        val targetKcal = targets?.kcal?.roundToInt()
        val progress = if (targetKcal != null && targetKcal > 0) (kcal.toFloat() / targetKcal).coerceIn(0f, 1f) else 0f

        val kcalLabel = localizedString(context, lang, R.string.widget_today_kcal_label)
        val streakLabel = localizedQuantityString(context, lang, R.plurals.widget_today_streak, streak, streak)
        val noTargetLabel = localizedString(context, lang, R.string.widget_today_no_target)

        provideContent {
            GlanceTheme {
                TodayWidgetContent(
                    kcal = kcal,
                    targetKcal = targetKcal,
                    progress = progress,
                    kcalLabel = kcalLabel,
                    streakLabel = streakLabel,
                    noTargetLabel = noTargetLabel,
                )
            }
        }
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
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(streakLabel, style = TextStyle(color = ColorProvider(AccentCoral), fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
