package fr.scanneat.presentation.diary.components

import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.Droplet
import compose.icons.tablericons.Pill
import compose.icons.tablericons.FileInvoice
import compose.icons.tablericons.Clock
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
}

/** Bundle doesn't natively round-trip an enum - process death (a low-memory
 *  background kill, the most common reason Android recreates an Activity)
 *  otherwise silently reset whichever Journal sub-tab (Weight/Water/Activity/
 *  Fasting/Treatment) the user was on back to Meals with no indication anything moved. */
internal val DiaryTabSaver = Saver<DiaryTab, String>(save = { it.name }, restore = { DiaryTab.valueOf(it) })

// Taller than FloatingTopBarHeight (title row + tab row, not just a single
// title row) - not including the device's own status-bar inset, which is
// added separately via windowInsetsPadding below, same as FloatingTopBar/
// BiolismScreen's own equivalent constant.
internal val DiaryHeaderHeight = 124.dp
