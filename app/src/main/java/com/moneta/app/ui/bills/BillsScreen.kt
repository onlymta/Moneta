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
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moneta.app.ui.common.FabStyle
import com.moneta.app.ui.common.MovableFab
import com.moneta.app.ui.common.SubPageHeader
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
    onBack: (() -> Unit)?,
    onAddBill: () -> Unit,
    onEditBill: (Long) -> Unit = {},
    viewModel: BillsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var deleteCandidate by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { SubPageHeader("Bills", onBack) },
        floatingActionButton = {
            MovableFab(onClick = onAddBill, contentDescription = "Add bill", style = FabStyle.OTHER)
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
                        onEdit = { onEditBill(row.bill.id) },
                        onMarkPaid = { viewModel.markPaid(row.bill.id) },
                        onRecordPayment = { viewModel.recordPayment(row.bill.id) },
                        onMarkUnpaid = { viewModel.markUnpaid(row.bill.id) },
                        onDelete = { deleteCandidate = row.bill.id }
                    )
                }
            }
        }
    }

    deleteCandidate?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete this bill?") },
            text = { Text("The bill is removed from your list. Any payment you already recorded stays in your transactions.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBill(id)
                    deleteCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
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
private fun BillRowCard(
    row: BillRow,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onRecordPayment: () -> Unit,
    onMarkUnpaid: () -> Unit,
    onDelete: () -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.bill.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${row.bill.dueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} · $label",
                        style = MaterialTheme.typography.labelMedium,
                        color = color
                    )
                    if (row.bill.reminderDaysBefore >= 0 && row.urgency != BillUrgency.PAID) {
                        Text(
                            "Reminder on ${row.bill.dueDate.minusDays(row.bill.reminderDaysBefore.toLong()).format(DateTimeFormatter.ofPattern("MMM d"))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    row.bill.notes?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MoneyText(row.bill.amount)
            }
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit bill") }
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete bill") }
                }
                if (row.urgency != BillUrgency.PAID) {
                    Row {
                        TextButton(onClick = onMarkPaid) { Text("Mark paid") }
                        TextButton(onClick = onRecordPayment) { Text("Record payment") }
                    }
                } else {
                    TextButton(onClick = onMarkUnpaid) { Text("Mark unpaid") }
                }
            }
        }
    }
}
