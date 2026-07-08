package fr.scanneat.presentation.grocery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.RecipeRepository
import fr.scanneat.domain.engine.*
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class GroceryViewModel @Inject constructor(repo: RecipeRepository) : ViewModel() {

    val groceryItems: StateFlow<List<GroceryItem>> = repo.observeAll()
        .map { recipes ->
            aggregateGroceryList(recipes.map { r ->
                GroceryRecipeInput(
                    name       = r.name,
                    components = r.components.map { c -> GroceryComponent(c.productName, c.grams) },
                )
            })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun GroceryScreen(
    viewModel: GroceryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val items     = viewModel.groceryItems.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var snack by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liste de courses", color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = OnBackground) } },
                actions = {
                    if (items.value.isNotEmpty()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(formatGroceryList(items.value)))
                            snack = true
                        }) { Icon(Icons.Default.ContentCopy, "Copier", tint = AccentGreen) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        if (items.value.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛒", style = MaterialTheme.typography.displaySmall)
                    Text("Créez des recettes pour générer la liste de courses automatiquement.",
                        color = OnBackground.copy(0.5f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Text("${items.value.size} articles · agrégé depuis vos recettes",
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
                items(items.value) { item ->
                    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                if (item.sources.isNotEmpty()) {
                                    Text(item.sources.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                }
                            }
                            if (item.grams > 0) {
                                Text("${item.grams} g", style = MaterialTheme.typography.labelLarge, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // Key on snack value so the effect restarts correctly on each copy action
    LaunchedEffect(snack) {
        if (snack) {
            kotlinx.coroutines.delay(2000)
            snack = false
        }
    }

}
