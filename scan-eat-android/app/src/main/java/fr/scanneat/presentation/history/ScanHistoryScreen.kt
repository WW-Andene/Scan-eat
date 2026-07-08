package fr.scanneat.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject



@Composable
fun ScanHistoryScreen(
    viewModel: ScanHistoryViewModel = hiltViewModel(),
    onOpenResult: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val scans = viewModel.filtered.collectAsStateWithLifecycle()
    val query = viewModel.query.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique", color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = OnBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = query.value,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher…", color = OnBackground.copy(0.4f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnBackground.copy(0.5f)) },
                trailingIcon = {
                    if (query.value.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Default.Close, null, tint = OnBackground.copy(0.5f))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f),
                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                ),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(scans.value) { scan ->
                    val gradeColor = gradeColor(scan.audit.grade)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariant)
                            .clickable { if (scan.dbId > 0) onOpenResult(scan.dbId) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), color = gradeColor.copy(0.2f)) {
                            Text(
                                scan.audit.grade.label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = gradeColor, fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("${scan.audit.score}/100 · ${scan.product.category.key.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = OnSurface.copy(0.3f), modifier = Modifier.size(20.dp))
                    }
                }
                if (scans.value.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (query.value.isBlank()) "Aucun scan enregistré." else "Aucun résultat pour \"${query.value}\".", color = OnBackground.copy(0.4f))
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

}

private fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.A_PLUS -> Color(0xFF4CAF50); Grade.A -> Color(0xFF8BC34A); Grade.B -> Color(0xFFCDDC39)
    Grade.C -> Color(0xFFFF9800); Grade.D -> Color(0xFFFF5722); Grade.F -> Color(0xFFF44336)
}
