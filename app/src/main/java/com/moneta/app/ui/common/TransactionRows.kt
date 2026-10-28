package com.moneta.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.Transaction
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.TransferGray
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A transaction plus everything a list row needs to show, already looked up (names, icon, accounts hit). */
data class TransactionListItem(
    val transaction: Transaction,
    val title: String,
    val subtitle: String,
    val detail: String?,
    val iconName: String,
    val colorHex: String
)

fun buildTransactionItems(
    transactions: List<Transaction>,
    accounts: List<Account>,
    categories: List<Category>
): List<TransactionListItem> {
    val accountById = accounts.associateBy { it.id }
    val categoryById = categories.associateBy { it.id }
    return transactions.map { t ->
        val accountName = accountById[t.accountId]?.name ?: "Unknown account"
        if (t.type == TransactionType.TRANSFER) {
            val toName = t.toAccountId?.let { accountById[it]?.name } ?: "Unknown account"
            TransactionListItem(
                transaction = t,
                title = "Transfer",
                subtitle = "$accountName → $toName",
                detail = listOfNotNull(t.payee, t.notes).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                iconName = "account_balance_wallet",
                colorHex = "#3B82F6"
            )
        } else {
            val category = t.categoryId?.let { categoryById[it] }
            val parent = category?.parentCategoryId?.let { categoryById[it] }
            val categoryName = when {
                category == null -> "Uncategorized"
                parent != null -> parent.name + " › " + category.name
                else -> category.name
            }
            TransactionListItem(
                transaction = t,
                title = categoryName,
                subtitle = accountName,
                detail = listOfNotNull(t.payee, t.notes).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                iconName = category?.icon ?: "category",
                colorHex = category?.colorHex ?: "#5B6B76"
            )
        }
    }
}

fun formatMinutes(minutesSinceMidnight: Int): String =
    LocalTime.of((minutesSinceMidnight / 60).coerceIn(0, 23), (minutesSinceMidnight % 60).coerceIn(0, 59))
        .format(DateTimeFormatter.ofPattern("h:mm a"))

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TransactionRowCard(
    item: TransactionListItem,
    showDate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val t = item.transaction
    val color = when (t.type) {
        TransactionType.INCOME -> IncomeGreen
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.TRANSFER -> TransferGray
    }
    val shown: Money = if (t.type == TransactionType.EXPENSE) -t.amount else t.amount
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonetaSpacing.md, vertical = MonetaSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(iconName = item.iconName, colorHex = item.colorHex)
            Spacer(Modifier.width(MonetaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                // The account sits in a little tag so it never reads like the description below it.
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                item.detail?.let { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Spacer(Modifier.width(MonetaSpacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(shown, color = color, modifier = Modifier)
                if (showDate) {
                    Text(
                        t.date.format(DateTimeFormatter.ofPattern("d MMM")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "Tue, 13 Oct" header above each day's rows, with that day's net total on the right. */
@Composable
fun DayHeader(date: LocalDate, net: Money, modifier: Modifier = Modifier) {
    val tint = if (net.isNegative) ExpenseRed else IncomeGreen
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MonetaSpacing.md, bottom = MonetaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // The day's total is drawn as a tinted pill so it can't be mistaken for a transaction amount.
        Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.14f)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Day ", style = MaterialTheme.typography.labelSmall, color = tint)
                MoneyText(net, color = tint)
            }
        }
    }
}
