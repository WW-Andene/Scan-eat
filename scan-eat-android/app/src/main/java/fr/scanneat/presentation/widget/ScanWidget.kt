package fr.scanneat.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import fr.scanneat.di.widgetEntryPoint
import fr.scanneat.domain.model.Grade
import fr.scanneat.presentation.MainActivity
import fr.scanneat.presentation.ui.theme.AccentCoralRaw
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.util.localizedString
import kotlinx.coroutines.flow.first

// ============================================================================
// SCAN WIDGET — home-screen "scan a product" button + a glance at the last
// scanned product's score, the app's second widget after TodayWidget (kcal).
// Same fixed-color-constants approach as TodayWidget (see that file's own F35
// comment on why this never reads GlanceTheme.colors). Entirely read-only
// except the "Scanner" chip, which opens straight into the Scan tab via the
// same fr.scanneat.action.SHORTCUT_SCAN action the static launcher shortcut
// (res/xml/shortcuts.xml) already uses - MainActivity.shortcutStartRoute
// already routes it, nothing new needed there. Tapping the rest of the
// widget (the last-scan summary) opens the app normally, same as
// TodayWidget's whole-widget click - deep-linking straight into that scan's
// own Result screen would need a new intent-filter/start-destination
// mechanism MainActivity doesn't have yet (only whole-tab routes exist).
// ============================================================================

private val WidgetBackground       = ColorProvider(Color(0xFF1B1611))
private val WidgetOnBackground     = ColorProvider(Color(0xFFEFEAE6))
private val WidgetOnSurfaceVariant = ColorProvider(Color(0xFFCFC7CC))

// Same 3-tier spread NormalGradeColors (Colors.kt) uses, duplicated here as a
// plain non-@Composable map rather than importing that private val - the
// widget already keeps its own fixed palette independent of in-app theming
// (see the header comment above), same precedent TodayWidget's Widget*
// constants set.
private fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.A_PLUS -> Color(0xFF2E7D32)
    Grade.A      -> Color(0xFF547928)
    Grade.B      -> Color(0xFF856C01)
    Grade.C      -> Color(0xFF8D6900)
    Grade.D      -> Color(0xFFA05F00)
    Grade.E      -> Color(0xFFCC380A)
    Grade.F      -> Color(0xFFD8201C)
}

class ScanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val prefs = entryPoint.userPreferences()
        val scanRepo = entryPoint.scanRepository()

        val lang = prefs.language.first()
        val profile = prefs.profile.first()
        val lastScan = scanRepo.observeHistory(limit = 1, profileId = profile.id).first().firstOrNull()

        val scanLabel = localizedString(context, lang, R.string.widget_scan_button)
        val emptyLabel = localizedString(context, lang, R.string.widget_scan_empty)
        val lastScanLabel = localizedString(context, lang, R.string.widget_scan_last_label)
        val scanIntent = Intent(context, MainActivity::class.java).apply {
            action = "fr.scanneat.action.SHORTCUT_SCAN"
        }

        provideContent {
            ScanWidgetContent(
                productName = lastScan?.product?.name,
                score = lastScan?.audit?.score,
                grade = lastScan?.audit?.grade,
                scanLabel = scanLabel,
                emptyLabel = emptyLabel,
                lastScanLabel = lastScanLabel,
                scanIntent = scanIntent,
            )
        }
    }
}

@Composable
private fun ScanWidgetContent(
    productName: String?,
    score: Int?,
    grade: Grade?,
    scanLabel: String,
    emptyLabel: String,
    lastScanLabel: String,
    scanIntent: Intent,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(Spacing.SM + Spacing.XS)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (productName != null && grade != null) {
            Text(lastScanLabel, style = TextStyle(color = WidgetOnSurfaceVariant, fontSize = 11.sp))
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                productName,
                maxLines = 1,
                style = TextStyle(color = WidgetOnBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    grade.label,
                    style = TextStyle(color = ColorProvider(gradeColor(grade)), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                )
                if (score != null) {
                    Text(" $score/100", style = TextStyle(color = WidgetOnSurfaceVariant, fontSize = 13.sp))
                }
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
        } else {
            Text(emptyLabel, style = TextStyle(color = WidgetOnSurfaceVariant, fontSize = 12.sp))
            Spacer(modifier = GlanceModifier.height(10.dp))
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            // Own clickable target inside the whole-widget-opens-app Column above -
            // this chip opens straight into the Scan tab (SHORTCUT_SCAN action,
            // same one the static launcher shortcut already uses) instead of
            // whichever tab the user last had open.
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(AccentCoralRaw))
                    .cornerRadius(12.dp)
                    .padding(horizontal = Spacing.SM, vertical = Spacing.XS)
                    .clickable(actionStartActivity(scanIntent)),
            ) {
                Text(scanLabel, style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

class ScanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScanWidget()
}
