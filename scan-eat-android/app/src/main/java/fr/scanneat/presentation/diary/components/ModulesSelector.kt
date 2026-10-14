package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/** Roadmap A4: which Journal trackers the user follows (shared by onboarding and Settings). */
@Composable
internal fun ModulesSelector(hidden: Set<DiaryTab>, onChange: (Set<DiaryTab>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.modules_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        HIDEABLE_DIARY_TABS.forEach { tab ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = tab !in hidden, onCheckedChange = { follow -> onChange(if (follow) hidden - tab else hidden + tab) })
                Icon(tab.icon, null, tint = OnBackground.copy(0.7f), modifier = Modifier.size(IconSize.Inline))
                Spacer(Modifier.width(Spacing.S))
                Text(stringResource(tab.labelRes), color = OnBackground)
            }
        }
    }
}
