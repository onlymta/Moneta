package com.moneta.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AddGoalScreen(
    onDone: () -> Unit,
    viewModel: AddGoalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    com.moneta.app.ui.common.FormScreenScaffold(title = "Add goal", onClose = onDone) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Goal name") },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.targetAmountInput,
            onValueChange = viewModel::onAmountChange,
            label = { Text("Target amount") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.amountError != null,
            supportingText = state.amountError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        var pickingDate by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.material3.OutlinedButton(
            onClick = { pickingDate = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                state.targetDate?.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
                    ?.let { "Target date: $it" } ?: "Set a target date (optional)"
            )
        }
        if (pickingDate) {
            GoalDatePickerDialog(
                initial = state.targetDate,
                onPick = { date ->
                    viewModel.onTargetDateChange(date)
                    pickingDate = false
                },
                onDismiss = { pickingDate = false }
            )
        }

        Spacer(Modifier.height(0.dp))

        Button(
            onClick = { viewModel.save(onDone) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isSaving) "Saving…" else "Save goal")
        }
    }
}
