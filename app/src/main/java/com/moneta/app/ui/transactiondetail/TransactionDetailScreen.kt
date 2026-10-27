package com.moneta.app.ui.transactiondetail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.ui.common.formatMinutes
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.TransferGray
import java.time.format.DateTimeFormatter

/**
 * A compact pop-up with everything about one transaction (including the time of day,
 * which the list rows leave out) and Edit / Duplicate / Delete actions.
 */
@Composable
fun TransactionDetailPopup(
    transactionId: Long,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(key = "detail-$transactionId")
) {
    LaunchedEffect(transactionId) { viewModel.load(transactionId) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    LaunchedEffect(state.duplicatedId) {
        if (state.duplicatedId != null) {
            Toast.makeText(context, "Transaction duplicated", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    val transaction = state.transaction
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            if (state.isLoading || transaction == null) {
                Text("Loading…", modifier = Modifier.padding(MonetaSpacing.xl))
            } else {
                val accent = when (transaction.type) {
                    TransactionType.INCOME -> IncomeGreen
                    TransactionType.EXPENSE -> ExpenseRed
                    TransactionType.TRANSFER -> TransferGray
                }
                Column(
                    modifier = Modifier.padding(MonetaSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    Text(
                        transaction.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                    MoneyText(
                        transaction.amount,
                        color = accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    DetailRow("Category", state.categoryName ?: if (transaction.type == TransactionType.TRANSFER) "Transfer" else "Uncategorized")
                    DetailRow("Account", state.accountName)
                    state.toAccountName?.let { DetailRow("To account", it) }
                    DetailRow(
                        "When",
                        transaction.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) + " · " +
                            formatMinutes(transaction.timeMinutesSinceMidnight)
                    )
                    transaction.payee?.takeIf { it.isNotBlank() }?.let { DetailRow("Payee", it) }
                    transaction.notes?.takeIf { it.isNotBlank() }?.let { DetailRow("Notes", it) }
                    Spacer(Modifier.height(MonetaSpacing.xs))

                    if (confirmDelete) {
                        Text("Delete this transaction?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
                            TextButton(onClick = viewModel::delete) {
                                Text("Delete", color = ExpenseRed)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ActionButton("Edit", Icons.Outlined.Edit) { onEdit(transactionId) }
                            ActionButton("Duplicate", Icons.Outlined.ContentCopy) { onDuplicate(transactionId) }
                            ActionButton("Delete", Icons.Outlined.Delete, ExpenseRed) { confirmDelete = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.primary else tint
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (tint == androidx.compose.ui.graphics.Color.Unspecified) androidx.compose.ui.graphics.Color.Unspecified else tint)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
