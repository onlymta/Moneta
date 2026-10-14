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

    fun setRolloverBudgets(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setRolloverBudgets(enabled) }

    fun setShowAccountsInBottomBar(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setShowAccountsInBottomBar(enabled) }

    fun setPickerStyle(style: PickerStyle) = viewModelScope.launch { settingsRepository.setPickerStyle(style) }
}
