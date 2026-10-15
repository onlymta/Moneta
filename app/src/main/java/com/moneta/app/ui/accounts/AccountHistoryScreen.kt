package com.moneta.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.common.DayHeader
import com.moneta.app.ui.common.IconBadge
import com.moneta.app.ui.common.TransactionRowCard
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AccountHistoryScreen(
    accountId: Long,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onEdit: () -> Unit,
    viewModel: AccountHistoryViewModel = hiltViewModel()
) {
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    val state by viewModel.uiState.collectAsState()
    val account = state.account

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonetaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back") }
            Text(
                account?.name ?: "Account",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit account") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MonetaSpacing.screenHorizontal,
                end = MonetaSpacing.screenHorizontal,
                top = MonetaSpacing.sm,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            if (account != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(iconName = account.icon, colorHex = account.colorHex)
                                Spacer(Modifier.width(MonetaSpacing.md))
                                Column {
                                    Text("Current balance", style = MaterialTheme.typography.labelMedium)
                                    MoneyText(account.currentBalance)
                                }
                            }
                            Spacer(Modifier.height(MonetaSpacing.sm))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("In", style = MaterialTheme.typography.labelMedium)
                                    MoneyText(state.income, color = IncomeGreen)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Out", style = MaterialTheme.typography.labelMedium)
                                    MoneyText(state.expense, color = ExpenseRed)
                                }
                            }
                            Text(
                                state.showingLabel + " · " + state.count + " transaction(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!state.isLoading && state.groups.isEmpty()) {
                item {
                    Text(
                        "No transactions in " + state.showingLabel + ".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MonetaSpacing.md)
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = "day-" + group.date.toEpochDay()) {
                    DayHeader(date = group.date, net = group.net)
                }
                items(group.items.size, key = { "tx-" + group.items[it].transaction.id }) { index ->
                    val item = group.items[index]
                    TransactionRowCard(
                        item = item,
                        showDate = false,
                        onClick = { onTransactionClick(item.transaction.id) }
                    )
                }
            }

            item {
                Button(
                    onClick = viewModel::showPreviousMonth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MonetaSpacing.sm)
                ) {
                    Text("View " + state.previousMonthLabel.ifBlank { "previous month" } + " too")
                }
            }
        }
    }
}
