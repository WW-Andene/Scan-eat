package fr.scanneat.presentation.grocery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.presentation.ui.theme.*

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
                title = { Text(stringResource(R.string.grocery_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = {
                    if (items.value.isNotEmpty()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(formatGroceryList(items.value)))
                            snack = true
                        }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.common_copy), tint = AccentGreen) }
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
                    Icon(Icons.Default.ShoppingCart, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.grocery_empty_body),
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
                    Text(stringResource(R.string.grocery_item_count, items.value.size),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
                items(items.value, key = { it.name }) { item ->
                    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(10.dp))) {
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
                                    Text(stringResource(R.string.grocery_grams, item.grams), style = MaterialTheme.typography.labelLarge, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                                }
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
