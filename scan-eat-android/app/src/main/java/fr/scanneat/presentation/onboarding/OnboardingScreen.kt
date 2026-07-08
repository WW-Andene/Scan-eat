package fr.scanneat.presentation.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: UserPreferences) : ViewModel() {

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun setMode(mode: ApiMode) = viewModelScope.launch { prefs.setApiMode(mode) }
    fun setApiKey(key: String) = viewModelScope.launch { prefs.setGroqApiKey(key) }
    fun setServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url) }
    fun finish() { _done.value = true }
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onDone: () -> Unit,
    onGoToProfile: () -> Unit = {},
) {
    val done = viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done.value) { if (done.value) onDone() }

    var page by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf(ApiMode.DIRECT) }
    var apiKey by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(40.dp))

            when (page) {
                // ---- Page 0: Welcome ----
                0 -> {
                    Text("🥦", fontSize = 64.sp)
                    Text("Scan'eat", style = MaterialTheme.typography.headlineLarge, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Scannez un code-barres ou photographiez une étiquette pour obtenir un score nutritionnel 0–100 basé sur la composition réelle du produit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnBackground.copy(0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { page = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Commencer", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                }

                // ---- Page 1: API mode ----
                1 -> {
                    Text("Configuration", style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Scan'eat utilise un modèle de vision par IA pour lire les étiquettes. Choisissez comment l'appeler :",
                        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeCard(
                            selected  = selectedMode == ApiMode.DIRECT,
                            title     = "Mode Direct",
                            subtitle  = "Votre clé Groq (gratuite). Données traitées par Groq.",
                            onClick   = { selectedMode = ApiMode.DIRECT },
                        )
                        ModeCard(
                            selected  = selectedMode == ApiMode.SERVER,
                            title     = "Mode Serveur",
                            subtitle  = "Votre propre backend Ktor. Aucune donnée ne quitte votre infrastructure.",
                            onClick   = { selectedMode = ApiMode.SERVER },
                        )
                    }

                    if (selectedMode == ApiMode.DIRECT) {
                        OutlinedTextField(
                            value = apiKey, onValueChange = { apiKey = it },
                            label = { Text("Clé Groq (gsk_…)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Text("Clé gratuite sur console.groq.com", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                    } else {
                        OutlinedTextField(
                            value = serverUrl, onValueChange = { serverUrl = it },
                            label = { Text("URL du serveur") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            viewModel.setMode(selectedMode)
                            if (apiKey.isNotBlank()) viewModel.setApiKey(apiKey)
                            if (serverUrl.isNotBlank()) viewModel.setServerUrl(serverUrl)
                            page = 2
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (selectedMode == ApiMode.DIRECT && apiKey.isNotBlank()) ||
                                  (selectedMode == ApiMode.SERVER && serverUrl.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Continuer", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                }

                // ---- Page 2: Profile prompt ----
                2 -> {
                    Text("Personnalisation", style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "Complétez votre profil (sexe, âge, poids, régime, allergènes) pour obtenir un score personnel adapté à vos besoins nutritionnels. Vous pouvez le faire plus tard dans l'onglet Profil.",
                        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.finish(); onGoToProfile() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Configurer mon profil maintenant", color = Color.Black, fontWeight = FontWeight.SemiBold) }
                        TextButton(
                            onClick = { viewModel.finish() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Passer, configurer plus tard", color = OnBackground.copy(0.5f)) }
                    }
                }
            }
        }
    }

}

@Composable
private fun ModeCard(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(14.dp),
        color   = if (selected) AccentGreen.copy(0.15f) else SurfaceVariant,
        border  = if (selected) ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = AccentGreen))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            }
        }
    }
}
