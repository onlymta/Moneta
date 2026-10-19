package com.moneta.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AddEditAccountScreen(
    accountId: Long?,
    onDone: () -> Unit,
    viewModel: AddEditAccountViewModel = hiltViewModel()
) {
    LaunchedEffect(accountId) {
        if (accountId != null) viewModel.loadForEdit(accountId)
    }
    val state by viewModel.uiState.collectAsState()

    com.moneta.app.ui.common.FormScreenScaffold(
        title = if (state.isEditing) "Edit account" else "Add account",
        onClose = onDone
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Account name") },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Type", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            items(AccountType.entries) { type ->
                FilterChip(
                    selected = state.type == type,
                    onClick = { viewModel.onTypeChange(type) },
                    label = { Text(if (type == AccountType.SAVINGS) "Savings account" else if (type == AccountType.BANK) "Bank (current)" else type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Text("Color", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            items(accountColorPalette) { colorHex ->
                val color = Color(android.graphics.Color.parseColor(colorHex))
                Column(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { viewModel.onColorChange(colorHex) }
                ) {
                    if (state.colorHex == colorHex) {
                        Text(
                            "\u2713",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.openingBalanceInput,
            onValueChange = viewModel::onOpeningBalanceChange,
            label = { Text("Opening balance") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.amountError != null,
            supportingText = (
                state.amountError
                    ?: if (state.isEditing) "Changing this shifts the current balance by the same amount; your transactions are kept."
                    else "Leave blank to start at zero."
                ).let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Include in net worth", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Turn off for accounts you don't want counted in your total balance.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.includeInNetWorth, onCheckedChange = viewModel::onIncludeInNetWorthChange)
        }

        Spacer(Modifier.height(0.dp))

        Button(onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}
