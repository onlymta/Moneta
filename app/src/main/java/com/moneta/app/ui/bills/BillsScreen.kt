@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.repository.BillUrgency
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.WarningAmber
import java.time.format.DateTimeFormatter

@Composable
fun BillsScreen(
    onAddBill: () -> Unit,
    viewModel: BillsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.lastMarkPaidCreatedTransaction) {
        val result = state.lastMarkPaidCreatedTransaction ?: return@LaunchedEffect
        val message = if (result) {
            "Bill marked paid and recorded as an expense"
        } else {
            "Bill marked paid (no account/category set, so no transaction was recorded)"
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMarkPaidResult()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bills") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBill) {
                Icon(Icons.Filled.Add, contentDescription = "Add bill")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (!state.isLoading && state.bills.isEmpty()) {
            BillsEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = MonetaSpacing.screenHorizontal, end = MonetaSpacing.screenHorizontal, top = MonetaSpacing.screenHorizontal, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                items(state.bills, key = { it.bill.id }) { row ->
                    BillRowCard(
                        row = row,
                        onMarkPaid = { viewModel.markPaid(row.bill.id) },
                        onDelete = { viewModel.deleteBill(row.bill.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BillsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No bills tracked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text(
            "Add a recurring or one-off bill to keep track of due dates.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun BillRowCard(row: BillRow, onMarkPaid: () -> Unit, onDelete: () -> Unit) {
    val (label, color) = when (row.urgency) {
        BillUrgency.OVERDUE -> "Overdue" to ExpenseRed
        BillUrgency.DUE_TODAY -> "Due today" to ExpenseRed
        BillUrgency.DUE_SOON -> "Due soon" to WarningAmber
        BillUrgency.UPCOMING -> "Upcoming" to MaterialTheme.colorScheme.onSurfaceVariant
        BillUrgency.PAID -> "Paid" to IncomeGreen
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(row.bill.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${row.bill.dueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} · $label",
                        style = MaterialTheme.typography.labelMedium,
                        color = color
                    )
                }
                MoneyText(row.bill.amount)
            }
            if (row.urgency != BillUrgency.PAID) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete bill")
                    }
                    TextButton(onClick = onMarkPaid) { Text("Mark paid") }
                }
            }
        }
    }
}
