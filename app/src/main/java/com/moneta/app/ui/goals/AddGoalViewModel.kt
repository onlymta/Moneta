package com.moneta.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"
const val DEFAULT_GOAL_ICON = "flag"

data class AddGoalUiState(
    val name: String = "",
    val targetAmountInput: String = "",
    val targetDate: LocalDate? = null,
    val nameError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class AddGoalViewModel @Inject constructor(
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddGoalUiState())
    val uiState: StateFlow<AddGoalUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, nameError = null) }

    fun onAmountChange(input: String) = _uiState.update { it.copy(targetAmountInput = input, amountError = null) }

    fun onTargetDateChange(date: LocalDate?) = _uiState.update { it.copy(targetDate = date) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Give this goal a name.") }
            return
        }
        val amount = Money.fromDecimalInput(state.targetAmountInput, DEFAULT_CURRENCY)
        if (amount == null || amount.isZero) {
            _uiState.update { it.copy(amountError = "Enter a valid target amount.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            goalRepository.createGoal(
                name = state.name.trim(),
                icon = DEFAULT_GOAL_ICON,
                targetAmount = amount,
                targetDate = state.targetDate
            )
            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}

data class ContributeUiState(
    val goalName: String = "",
    val amountInput: String = "",
    val amountError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class ContributeToGoalViewModel @Inject constructor(
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContributeUiState())
    val uiState: StateFlow<ContributeUiState> = _uiState.asStateFlow()

    private var goalId: Long? = null

    fun load(id: Long) {
        if (goalId == id) return
        goalId = id
        viewModelScope.launch {
            val goal = goalRepository.getById(id) ?: return@launch
            _uiState.update { it.copy(goalName = goal.name) }
        }
    }

    fun onAmountChange(input: String) = _uiState.update { it.copy(amountInput = input, amountError = null) }

    fun contribute(onDone: () -> Unit) {
        val id = goalId ?: return
        val amount = Money.fromDecimalInput(_uiState.value.amountInput, DEFAULT_CURRENCY)
        if (amount == null || amount.isZero) {
            _uiState.update { it.copy(amountError = "Enter a valid amount.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            goalRepository.contribute(id, amount)
            _uiState.update { it.copy(isSaving = false) }
            onDone()
        }
    }
}
