package com.moneta.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.R
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun TransactionsScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search transactions") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.screenHorizontal)
        )

        if (!state.isLoading && state.transactions.isEmpty()) {
            TransactionsEmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = MonetaSpacing.screenHorizontal,
                    vertical = MonetaSpacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                items(state.transactions, key = { it.id }) { transaction ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTransactionClick(transaction.id) }
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MonetaSpacing.cardPadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    transaction.payee ?: transaction.notes ?: transaction.type.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1
                                )
                                Text(
                                    transaction.date.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val color = when (transaction.type) {
                                TransactionType.INCOME -> IncomeGreen
                                TransactionType.EXPENSE -> ExpenseRed
                                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            MoneyText(transaction.amount, color = color)
                        }
                    }
                }
            }
        }
    }
}

/** Section 30 empty state, with the exact copy specified in the original brief. */
@Composable
private fun TransactionsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.empty_transactions_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(MonetaSpacing.xs))
        Text(
            androidx.compose.ui.res.stringResource(R.string.empty_transactions_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
