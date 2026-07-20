package fr.scanneat.presentation.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * MealRemindersCard was only ever embedded inline in Diary and Medication — there
 * was no way to reach reminder settings from anywhere else, including Settings
 * itself, despite the reminder system (ReminderWorker) covering meals, hydration,
 * weigh-ins, activity, fasting targets, and custom reminders app-wide. This is a
 * thin dedicated screen so Settings can link to one place that manages all of them.
 */
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            FloatingTopBar(
                title = { Text(stringResource(R.string.reminders_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Teal)
                .padding(horizontal = Spacing.L).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Spacer(Modifier.height(Spacing.XS))
            MealRemindersCard()
            Spacer(Modifier.height(Spacing.XL))
        }
    }
}
