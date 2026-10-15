package fr.scanneat.presentation.expenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/** Dépenses as its own screen (Cuisine hub) - ExpensesScreen itself only knows how to be embedded. */
@Composable
fun ExpensesRoute(onBack: () -> Unit, onOpenCalendar: () -> Unit, onScanReceipt: () -> Unit) {
    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.diary_tab_expenses), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
            ExpensesScreen(
                embeddedTopPadding = padding.calculateTopPadding(),
                embeddedBottomPadding = padding.calculateBottomPadding(),
                onOpenCalendar = onOpenCalendar,
                onScanReceipt = onScanReceipt,
            )
        }
    }
}
