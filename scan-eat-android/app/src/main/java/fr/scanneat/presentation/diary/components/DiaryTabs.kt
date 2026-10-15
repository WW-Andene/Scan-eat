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

/**
 * Restructuration step 2: the Journal's own tabs. Weight, Activity and Sleep
 * share the "Corps" slot (WEIGHT); Treatment and Expenses moved to the Santé
 * and Cuisine hubs. All nine stay DiaryTab entries: they are also the A4
 * modules a user can follow or hide.
 */
internal val JOURNAL_TABS = listOf(DiaryTab.MEALS, DiaryTab.WATER, DiaryTab.WEIGHT, DiaryTab.FASTING, DiaryTab.MOOD)
internal val BODY_PARTS = listOf(DiaryTab.WEIGHT, DiaryTab.ACTIVITY, DiaryTab.SLEEP)

/** The Journal tab a tracker lives in, null when it lives outside the Journal. */
internal fun journalSlotOf(tab: DiaryTab): DiaryTab? = when (tab) {
    in BODY_PARTS -> DiaryTab.WEIGHT
    DiaryTab.TREATMENT, DiaryTab.EXPENSES -> null
    else -> tab
}

/** Journal tabs hidden by the module choice - Corps only once all three body trackers are hidden. */
internal fun hiddenJournalTabs(hiddenModules: Set<DiaryTab>): Set<DiaryTab> =
    JOURNAL_TABS.filter { t -> if (t == DiaryTab.WEIGHT) BODY_PARTS.all { it in hiddenModules } else t in hiddenModules }.toSet()

internal fun journalLabelRes(tab: DiaryTab): Int = if (tab == DiaryTab.WEIGHT) R.string.diary_tab_body else tab.labelRes

/** Parses UserPreferences.diaryPrimaryTabsOrder's stored CSV of [DiaryTab] names
 *  back into an ordered list — falls back to [DEFAULT_PRIMARY_DIARY_TABS] for a
 *  blank/first-run value or anything that doesn't cleanly resolve to exactly
 *  three distinct tabs (a stale value from a future app version with a
 *  different primary-tab count, corruption, etc). */
internal fun parsePrimaryDiaryTabs(csv: String): List<DiaryTab> {
    if (csv.isBlank()) return DEFAULT_PRIMARY_DIARY_TABS
    val parsed = csv.split(",").mapNotNull { name -> DiaryTab.entries.firstOrNull { it.name == name.trim() }?.let(::journalSlotOf) }.distinct()
    return if (parsed.size == DEFAULT_PRIMARY_DIARY_TABS.size) parsed else DEFAULT_PRIMARY_DIARY_TABS
}

internal fun serializePrimaryDiaryTabs(tabs: List<DiaryTab>): String = tabs.joinToString(",") { it.name }

/** Bundle doesn't natively round-trip an enum - process death (a low-memory
 *  background kill, the most common reason Android recreates an Activity)
 *  otherwise silently reset whichever Journal sub-tab (Weight/Water/Activity/
 *  Fasting/Treatment) the user was on back to Meals with no indication anything moved. */
internal val DiaryTabSaver = Saver<DiaryTab, String>(save = { it.name }, restore = { DiaryTab.valueOf(it) })

/** Roadmap A4: Journal trackers the user can stop following (Meals always stays). */
internal val HIDEABLE_DIARY_TABS = DiaryTab.entries - DiaryTab.MEALS

internal fun parseHiddenModules(csv: String): Set<DiaryTab> =
    csv.split(",").mapNotNull { n -> HIDEABLE_DIARY_TABS.firstOrNull { it.name == n.trim() } }.toSet()

internal fun serializeHiddenModules(hidden: Set<DiaryTab>): String = hidden.filter { it in HIDEABLE_DIARY_TABS }.joinToString(",") { it.name }

/**
 * Header tabs once hidden modules are removed: each hidden primary slot is
 * filled by the next followed tab not already shown, overflow keeps the rest.
 */
internal fun visibleDiaryTabs(primary: List<DiaryTab>, hidden: Set<DiaryTab>): Pair<List<DiaryTab>, List<DiaryTab>> {
    val followed = JOURNAL_TABS.filter { it !in hidden }
    val kept = primary.filter { it !in hidden }.toMutableList()
    followed.filter { it !in kept }.forEach { if (kept.size < primary.size) kept += it }
    return kept to followed.filter { it !in kept }
}
