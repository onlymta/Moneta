package com.moneta.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.settings.OnboardingStatus
import com.moneta.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides, once per app launch, which screen to start on: the welcome flow for a
 * brand-new install, otherwise Home. Existing users (anyone who already has an
 * account) never see it, even right after updating from an older version.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute

    init {
        viewModelScope.launch {
            val route = try {
                val prefs = settingsRepository.preferences.first()
                val hasAccounts = accountRepository.observeAllAccounts().first().isNotEmpty()
                if (prefs.onboardingStatus == OnboardingStatus.NOT_STARTED && !hasAccounts) {
                    ONBOARDING_ROUTE
                } else {
                    MonetaDestination.Home.route
                }
            } catch (e: Exception) {
                MonetaDestination.Home.route
            }
            _startRoute.value = route
        }
    }
}
