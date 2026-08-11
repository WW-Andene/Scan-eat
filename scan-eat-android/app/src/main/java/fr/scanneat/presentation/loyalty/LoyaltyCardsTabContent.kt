package fr.scanneat.presentation.loyalty

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * Embedded content for Courses' "Fidélité" tab - see GroceryScreen.kt's own
 * GroceryTab. [onScanCard] pushes AppRoutes.LOYALTY_CARD_SCAN (the camera
 * capture flow, LoyaltyCardScanScreen.kt); manual entry (store name + typed
 * code) stays available as a fallback for a card the camera can't read.
 */
@Composable
fun LoyaltyCardsTabContent(onScanCard: () -> Unit, viewModel: LoyaltyCardsViewModel = hiltViewModel()) {
    val cards = viewModel.cards.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var storeText by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Button(onClick = onScanCard, colors = ButtonDefaults.buttonColors(containerColor = AccentCoral)) {
                Icon(Icons.Rounded.QrCodeScanner, null, modifier = Modifier.padding(end = Spacing.XS))
                Text(stringResource(R.string.loyalty_scan))
            }
            OutlinedButton(onClick = { storeText = ""; codeText = ""; showAddDialog = true }) {
                Text(stringResource(R.string.loyalty_add))
            }
        }
        if (cards.value.isEmpty()) {
            EmptyListState(
                icon = Icons.Rounded.CreditCard,
                message = stringResource(R.string.loyalty_empty_body),
                ctaLabel = stringResource(R.string.loyalty_scan),
                onCta = onScanCard,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                items(cards.value, key = { it.id }) { card ->
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius.CONTROL),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(Spacing.L),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(card.storeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnBackground)
                                Spacer(Modifier.height(Spacing.XS))
                                // Grouped in 4s (same convention most card/loyalty numbers are
                                // printed in) purely for on-screen readability at checkout.
                                Text(
                                    card.code.chunked(4).joinToString(" "),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = OnBackground.copy(0.85f),
                                )
                            }
                            IconButton(onClick = { viewModel.removeCard(card.id) }) {
                                Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = OnBackground.copy(0.6f))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.XXL)) }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.loyalty_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    OutlinedTextField(
                        value = storeText,
                        onValueChange = { storeText = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.loyalty_store_placeholder)) },
                    )
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.loyalty_code_placeholder)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = storeText.isNotBlank() && codeText.isNotBlank(),
                    onClick = { viewModel.addCard(storeText, codeText); showAddDialog = false },
                ) { Text(stringResource(R.string.common_add)) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
