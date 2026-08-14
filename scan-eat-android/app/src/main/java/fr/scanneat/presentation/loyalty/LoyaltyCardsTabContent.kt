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
import fr.scanneat.data.repository.loyalty.LoyaltyCard
import fr.scanneat.presentation.ui.theme.*

/**
 * Embedded content for Courses' "Fidélité" tab - see GroceryScreen.kt's own
 * GroceryTab. Manual entry only (store name + typed code) - see
 * LoyaltyCardRepository's own doc comment on why camera-based card
 * recognition isn't offered here.
 */
@Composable
fun LoyaltyCardsTabContent(viewModel: LoyaltyCardsViewModel = hiltViewModel()) {
    val cards = viewModel.cards.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var storeText by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }
    // DeleteConfirmDialog's own doc comment lists Weight/Templates/Recipes/
    // Activity as its users - Loyalty removed a card on a single tap with no
    // confirmation at all, the one destructive action in the app skipped by
    // that shared pattern. A misclick here means retyping the code from the
    // physical card, a real enough cost to warrant the same one-tap-undo
    // safety net every other delete action already gets.
    var deleteTarget by remember { mutableStateOf<LoyaltyCard?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.S)) {
            OutlinedButton(onClick = { storeText = ""; codeText = ""; showAddDialog = true }) {
                Text(stringResource(R.string.loyalty_add))
            }
        }
        if (cards.value.isEmpty()) {
            EmptyListState(
                icon = Icons.Rounded.CreditCard,
                message = stringResource(R.string.loyalty_empty_body),
                ctaLabel = stringResource(R.string.loyalty_add),
                onCta = { storeText = ""; codeText = ""; showAddDialog = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                items(cards.value, key = { it.id }) { card ->
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius.CONTROL),
                        colors = CardDefaults.cardColors(containerColor = dialogContainerColor),
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
                            IconButton(onClick = { deleteTarget = card }) {
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
                // colors/text color explicit - previously fully un-themed
                // (no colors= param, no manual text color either), so both
                // the ripple and the label text fell back to Material3's
                // default colorScheme.primary styling instead of this
                // app's brand AccentCoral every other dialog button uses.
                TextButton(
                    enabled = storeText.isNotBlank() && codeText.isNotBlank(),
                    onClick = { viewModel.addCard(storeText, codeText); showAddDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentCoral),
                ) { Text(stringResource(R.string.common_add)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = OnBackground.copy(0.6f)),
                ) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            itemName = target.storeName,
            onConfirm = { viewModel.removeCard(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}
