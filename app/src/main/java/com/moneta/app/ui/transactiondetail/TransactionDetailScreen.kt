@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.transactiondetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import kotlinx.coroutines.launch

@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(transactionId) { viewModel.load(transactionId) }
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Once soft-deleted, offer an undo snackbar then leave the screen (Section 9/31).
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            val result = snackbarHostState.showSnackbar(
                message = "Transaction deleted",
                actionLabel = "Undo"
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                onBack()
            }
        }
    }

    LaunchedEffect(state.duplicatedId) {
        if (state.duplicatedId != null) {
            scope.launch { snackbarHostState.showSnackbar("Transaction duplicated") }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Transaction") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val transaction = state.transaction
        if (state.isLoading || transaction == null) {
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MonetaSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
        ) {
            val amountColor = when (transaction.type) {
                TransactionType.INCOME -> IncomeGreen
                TransactionType.EXPENSE -> ExpenseRed
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            MoneyText(transaction.amount, color = amountColor)

            DetailRow("Type", transaction.type.name.lowercase().replaceFirstChar { it.uppercase() })
            DetailRow("Account", state.accountName)
            state.toAccountName?.let { DetailRow("To account", it) }
            state.categoryName?.let { DetailRow("Category", it) }
            DetailRow("Date", transaction.date.toString())
            transaction.payee?.let { DetailRow("Payee", it) }
            transaction.notes?.let { DetailRow("Notes", it) }

            Spacer(Modifier.height(0.dp))

            Button(onClick = { onEdit(transactionId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Edit")
            }
            OutlinedButton(onClick = viewModel::duplicate, modifier = Modifier.fillMaxWidth()) {
                Text("Duplicate")
            }
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed)
            ) {
                Text("Delete")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this transaction?") },
            text = { Text("You can undo this right after deleting.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
