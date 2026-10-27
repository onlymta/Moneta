package com.moneta.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.settings.AppPreferences
import com.moneta.app.data.settings.OnboardingStatus
import com.moneta.app.data.settings.PickerStyle
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = settingsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setCurrencyCode(code: String) = viewModelScope.launch { settingsRepository.setCurrencyCode(code) }

    fun setBudgetsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setBudgetsEnabled(enabled) }

    fun setGoalsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setGoalsEnabled(enabled) }

    fun setBillsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setBillsEnabled(enabled) }

    fun setConfirmBeforeDelete(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setConfirmBeforeDelete(enabled) }

    fun setCompactTransactionRows(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setCompactTransactionRows(enabled) }

    /** The Home "finish setting up" reminder's Dismiss button: stop reminding for good. */
    fun dismissOnboardingReminder() =
        viewModelScope.launch { settingsRepository.setOnboardingStatus(OnboardingStatus.COMPLETED) }

    fun setUserName(name: String) = viewModelScope.launch { settingsRepository.setUserName(name) }

    fun setCarryForwardBalance(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setCarryForwardBalance(enabled) }

    fun setShowGreeting(on: Boolean) =
        viewModelScope.launch { settingsRepository.setShowGreeting(on) }

    fun setCarryOverspend(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setCarryOverspend(enabled) }

    fun setRolloverBudgets(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setRolloverBudgets(enabled) }

    fun setShowAccountsInBottomBar(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setShowAccountsInBottomBar(enabled) }

    fun setPickerStyle(style: PickerStyle) = viewModelScope.launch { settingsRepository.setPickerStyle(style) }

    fun setTabOrder(order: List<String>) = viewModelScope.launch { settingsRepository.setTabOrder(order) }

    fun setHiddenTabs(hidden: Set<String>) = viewModelScope.launch { settingsRepository.setHiddenTabs(hidden) }

    fun setDecimals(value: Int) = viewModelScope.launch { settingsRepository.setDecimals(value) }

    fun setHideBalance(hidden: Boolean) = viewModelScope.launch { settingsRepository.setHideBalance(hidden) }

    fun setCompactHome(on: Boolean) = viewModelScope.launch { settingsRepository.setCompactHome(on) }

    fun setHomeCardOrder(order: List<String>) = viewModelScope.launch { settingsRepository.setHomeCardOrder(order) }

    fun setHiddenHomeCards(keys: Set<String>) = viewModelScope.launch { settingsRepository.setHiddenHomeCards(keys) }

    fun setSubcategoriesEnabled(on: Boolean) = viewModelScope.launch { settingsRepository.setSubcategoriesEnabled(on) }

    fun setDisplayCurrency(code: String) = viewModelScope.launch { settingsRepository.setDisplayCurrency(code) }

    fun setShowCurrencySymbol(show: Boolean) = viewModelScope.launch { settingsRepository.setShowCurrencySymbol(show) }

    fun setFormSaveAtTop(on: Boolean) = viewModelScope.launch { settingsRepository.setFormSaveAtTop(on) }

    fun setFormPickersInRow(on: Boolean) = viewModelScope.launch { settingsRepository.setFormPickersInRow(on) }

    fun setFormPickersAboveAmount(on: Boolean) = viewModelScope.launch { settingsRepository.setFormPickersAboveAmount(on) }

    fun setFormCustomKeypad(on: Boolean) = viewModelScope.launch { settingsRepository.setFormCustomKeypad(on) }

    fun setFormNotesAboveAmount(on: Boolean) = viewModelScope.launch { settingsRepository.setFormNotesAboveAmount(on) }

    fun setAutoBackupFrequency(frequency: com.moneta.app.data.settings.BackupFrequency) =
        viewModelScope.launch { settingsRepository.setAutoBackupFrequency(frequency) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) =
        viewModelScope.launch { settingsRepository.setReminder(enabled, hour, minute) }

    fun setFormDateBelowKeypad(on: Boolean) = viewModelScope.launch { settingsRepository.setFormDateBelowKeypad(on) }

    fun setBalanceScope(scope: com.moneta.app.data.settings.BalanceScope) =
        viewModelScope.launch { settingsRepository.setBalanceScope(scope) }
}
