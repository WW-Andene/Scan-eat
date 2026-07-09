package fr.scanneat.presentation.biolism

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.biolism.bioProfile.BiolismOnboardingScreen
import fr.scanneat.presentation.biolism.bioProfile.BiolismProfileScreen
import fr.scanneat.presentation.biolism.bioProfile.BiolismProfileViewModel
import fr.scanneat.presentation.biolism.data.DataScreen
import fr.scanneat.presentation.biolism.tracker.TrackerScreen
import fr.scanneat.presentation.ui.theme.*

private enum class BiolismTab(@androidx.annotation.StringRes val labelRes: Int) {
    TRACKER(R.string.biolism_tab_tracker), DATA(R.string.biolism_tab_data), PROFILE(R.string.biolism_tab_profile)
}

@Composable
fun BiolismScreen(gateViewModel: BiolismProfileViewModel = hiltViewModel()) {
    val profile   = gateViewModel.profile.collectAsStateWithLifecycle()
    val onboarded = gateViewModel.onboarded.collectAsStateWithLifecycle()

    if (!onboarded.value && !profile.value.isValid) {
        BiolismOnboardingScreen()
        return
    }

    var activeTab by remember { mutableStateOf(BiolismTab.TRACKER) }

    val bgColor = MaterialTheme.colorScheme.background
    val fgColor = MaterialTheme.colorScheme.onBackground
    Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // ── Internal 3-tab header ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Background)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tab_biolism), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp), color = LocalGoldAccent.current)
            }
            Text(stringResource(R.string.biolism_subtitle), style = MaterialTheme.typography.labelSmall, color = fgColor.copy(0.4f), letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            // Sub-tab row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BiolismTab.values().forEach { tab ->
                    val isActive = tab == activeTab
                    Surface(
                        onClick = { activeTab = tab },
                        modifier = Modifier.weight(1f).semantics { role = Role.Tab; selected = isActive },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) GoldHaze else OnBackground.copy(0.03f),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, GoldBorder) else null,
                    ) {
                        Text(
                            stringResource(tab.labelRes),
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) LocalGoldAccent.current else fgColor.copy(0.5f),
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = OnBackground.copy(0.06f))

        // ── Tab content ───────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                BiolismTab.TRACKER -> TrackerScreen()
                BiolismTab.DATA    -> DataScreen()
                BiolismTab.PROFILE -> BiolismProfileScreen()
            }
        }
    }
}
