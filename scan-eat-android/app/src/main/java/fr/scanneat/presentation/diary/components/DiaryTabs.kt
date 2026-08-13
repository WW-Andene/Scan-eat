package fr.scanneat.presentation.diary.components

import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.Droplet
import compose.icons.tablericons.Pill
import compose.icons.tablericons.FileInvoice
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Moon
import compose.icons.tablericons.Heart
import compose.icons.TablerIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.dp
import fr.scanneat.R

internal enum class DiaryTab(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MEALS(R.string.diary_tab_meals, TablerIcons.ClipboardList),
    WEIGHT(R.string.diary_tab_weight, Icons.Rounded.Scale),
    WATER(R.string.diary_tab_water, TablerIcons.Droplet),
    ACTIVITY(R.string.diary_tab_activity, Icons.Rounded.FitnessCenter),
    FASTING(R.string.diary_tab_fasting, TablerIcons.Clock),
    TREATMENT(R.string.diary_tab_treatment, TablerIcons.Pill),
    EXPENSES(R.string.diary_tab_expenses, TablerIcons.FileInvoice),
    SLEEP(R.string.diary_tab_sleep, TablerIcons.Moon),
    MOOD(R.string.diary_tab_mood, TablerIcons.Heart),
}

internal val DEFAULT_PRIMARY_DIARY_TABS = listOf(DiaryTab.MEALS, DiaryTab.WEIGHT, DiaryTab.WATER)

/** Parses UserPreferences.diaryPrimaryTabsOrder's stored CSV of [DiaryTab] names
 *  back into an ordered list — falls back to [DEFAULT_PRIMARY_DIARY_TABS] for a
 *  blank/first-run value or anything that doesn't cleanly resolve to exactly
 *  three distinct tabs (a stale value from a future app version with a
 *  different primary-tab count, corruption, etc). */
internal fun parsePrimaryDiaryTabs(csv: String): List<DiaryTab> {
    if (csv.isBlank()) return DEFAULT_PRIMARY_DIARY_TABS
    val parsed = csv.split(",").mapNotNull { name -> DiaryTab.entries.firstOrNull { it.name == name.trim() } }.distinct()
    return if (parsed.size == DEFAULT_PRIMARY_DIARY_TABS.size) parsed else DEFAULT_PRIMARY_DIARY_TABS
}

internal fun serializePrimaryDiaryTabs(tabs: List<DiaryTab>): String = tabs.joinToString(",") { it.name }

/** Bundle doesn't natively round-trip an enum - process death (a low-memory
 *  background kill, the most common reason Android recreates an Activity)
 *  otherwise silently reset whichever Journal sub-tab (Weight/Water/Activity/
 *  Fasting/Treatment) the user was on back to Meals with no indication anything moved. */
internal val DiaryTabSaver = Saver<DiaryTab, String>(save = { it.name }, restore = { DiaryTab.valueOf(it) })

// Taller than FloatingTopBarHeight (title row + tab row, not just a single
// title row) - not including the device's own status-bar inset, which is
// added separately via windowInsetsPadding below, same as FloatingTopBar/
// BiolismScreen's own equivalent constant.
//
// User-reported: bumped +52dp after DiaryHeader's own outer margin was fixed
// to match FloatingTopBar's 1(sides):2(top/bottom) ratio (FloatingChromeMargin,
// vertical=32dp each edge) instead of its previous ad-hoc Spacing.S(6dp) -
// this hardcoded approximation of the header's real measured height needed
// the same +26dp top / +26dp bottom the margin change actually added, or
// content below would start sliding up under the now-taller header.
// User-requested: all sizes must sit on a base-2 scale
// (2/4/6/8/12/16/24/32/48/64/96/128) - 124dp/52dp aren't members, snapped to 128dp/48dp.
internal val DiaryHeaderHeight = 128.dp + 48.dp
