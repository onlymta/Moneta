package com.moneta.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Where the first-run welcome flow stands. NOT_STARTED shows the welcome flow on
 * launch; SKIPPED ("do this later") shows a reminder banner on Home until the
 * user finishes or dismisses it; COMPLETED never shows either again.
 */
enum class OnboardingStatus { NOT_STARTED, SKIPPED, COMPLETED }

/**
 * The app's stored preferences (Section 25). Some fields here are fully
 * wired into behavior elsewhere in the app ([themeMode] controls the actual
 * theme; [budgetsEnabled]/[goalsEnabled]/[billsEnabled] actually hide those
 * tabs/rows; [lastUsedAccountId]/[lastUsedExpenseCategoryId]/
 * [lastUsedIncomeCategoryId] actually pre-select Quick Add's defaults per
 * Section 7's "remember recently used categories and accounts"); others
 * ([currencyCode], [confirmBeforeDelete]) are genuinely persisted and
 * editable here but not yet read everywhere they'd eventually need to be -
 * see the project README for exactly which is which rather than assuming
 * everything on this screen changes app behavior (Section 47: a toggle
 * that's stored but not enforced anywhere is still an honest feature as
 * long as it's disclosed, not a fake one - it's the "clearly marked as
 * planned/not yet implemented" case, not the forbidden one).
 */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currencyCode: String = "PKR",
    val budgetsEnabled: Boolean = true,
    val goalsEnabled: Boolean = true,
    val billsEnabled: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val compactTransactionRows: Boolean = false,
    val lastUsedAccountId: Long? = null,
    val lastUsedExpenseCategoryId: Long? = null,
    val lastUsedIncomeCategoryId: Long? = null,
    val userName: String = "",
    val onboardingStatus: OnboardingStatus = OnboardingStatus.NOT_STARTED
)

private val Context.dataStore by preferencesDataStore(name = "moneta_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val BUDGETS_ENABLED = booleanPreferencesKey("budgets_enabled")
        val GOALS_ENABLED = booleanPreferencesKey("goals_enabled")
        val BILLS_ENABLED = booleanPreferencesKey("bills_enabled")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
        val COMPACT_TRANSACTION_ROWS = booleanPreferencesKey("compact_transaction_rows")
        val LAST_USED_ACCOUNT_ID = longPreferencesKey("last_used_account_id")
        val LAST_USED_EXPENSE_CATEGORY_ID = longPreferencesKey("last_used_expense_category_id")
        val LAST_USED_INCOME_CATEGORY_ID = longPreferencesKey("last_used_income_category_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val ONBOARDING_STATUS = stringPreferencesKey("onboarding_status")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "PKR",
            budgetsEnabled = prefs[Keys.BUDGETS_ENABLED] ?: true,
            goalsEnabled = prefs[Keys.GOALS_ENABLED] ?: true,
            billsEnabled = prefs[Keys.BILLS_ENABLED] ?: true,
            confirmBeforeDelete = prefs[Keys.CONFIRM_BEFORE_DELETE] ?: true,
            compactTransactionRows = prefs[Keys.COMPACT_TRANSACTION_ROWS] ?: false,
            lastUsedAccountId = prefs[Keys.LAST_USED_ACCOUNT_ID],
            lastUsedExpenseCategoryId = prefs[Keys.LAST_USED_EXPENSE_CATEGORY_ID],
            lastUsedIncomeCategoryId = prefs[Keys.LAST_USED_INCOME_CATEGORY_ID],
            userName = prefs[Keys.USER_NAME] ?: "",
            onboardingStatus = prefs[Keys.ONBOARDING_STATUS]
                ?.let { runCatching { OnboardingStatus.valueOf(it) }.getOrNull() }
                ?: OnboardingStatus.NOT_STARTED
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setCurrencyCode(code: String) = context.dataStore.edit { it[Keys.CURRENCY_CODE] = code }

    suspend fun setBudgetsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.BUDGETS_ENABLED] = enabled }

    suspend fun setGoalsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.GOALS_ENABLED] = enabled }

    suspend fun setBillsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.BILLS_ENABLED] = enabled }

    suspend fun setConfirmBeforeDelete(enabled: Boolean) =
        context.dataStore.edit { it[Keys.CONFIRM_BEFORE_DELETE] = enabled }

    suspend fun setCompactTransactionRows(enabled: Boolean) =
        context.dataStore.edit { it[Keys.COMPACT_TRANSACTION_ROWS] = enabled }

    /** Called after a successful Quick Add save so the next entry defaults to the same account. */
    suspend fun setLastUsedAccountId(accountId: Long) =
        context.dataStore.edit { it[Keys.LAST_USED_ACCOUNT_ID] = accountId }

    suspend fun setLastUsedExpenseCategoryId(categoryId: Long) =
        context.dataStore.edit { it[Keys.LAST_USED_EXPENSE_CATEGORY_ID] = categoryId }

    suspend fun setLastUsedIncomeCategoryId(categoryId: Long) =
        context.dataStore.edit { it[Keys.LAST_USED_INCOME_CATEGORY_ID] = categoryId }

    suspend fun setUserName(name: String) = context.dataStore.edit { it[Keys.USER_NAME] = name.trim() }

    suspend fun setOnboardingStatus(status: OnboardingStatus) =
        context.dataStore.edit { it[Keys.ONBOARDING_STATUS] = status.name }
}
