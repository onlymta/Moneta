package com.moneta.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How often the app saves an automatic backup into its own storage. */
enum class BackupFrequency { OFF, DAILY, WEEKLY }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How Quick Add shows its account/category pickers: all chips at once, or compact dropdown menus. */
enum class PickerStyle { CHIPS, DROPDOWN }

/** Cards on the Home dashboard that can be switched on or off in Settings. */
enum class HomeCard(val label: String) {
    OPENING("Opening balance"),
    INCOME("Income"),
    EXPENSES("Expenses"),
    EXPECTED_CLOSING("Expected closing balance"),
    BUDGETED("Budgeted amount"),
    YET_TO_SPEND("Yet to spend (budgeted, not spent)")
}

/** Which accounts the headline balance adds up. */
enum class BalanceScope { ALL, CURRENT, SAVINGS }

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
    val onboardingStatus: OnboardingStatus = OnboardingStatus.NOT_STARTED,
    /** When on, a month's opening balance includes everything left over from earlier months. */
    val carryForwardBalance: Boolean = true,
    /** When on, unspent budget from last month is added to this month's budget. */
    val rolloverBudgets: Boolean = false,
    val showAccountsInBottomBar: Boolean = false,
    val pickerStyle: PickerStyle = PickerStyle.DROPDOWN,
    /** Notification ids the user has already looked at (so the bell badge only counts new ones). */
    val seenAlertIds: Set<String> = emptySet(),
    /** Left-to-right order of the main tabs, as keys: home, transactions, budgets, reports, accounts. */
    val tabOrder: List<String> = DEFAULT_TAB_ORDER,
    /** Tabs switched off in the bottom bar (they stay reachable from the side menu). */
    val hiddenTabs: Set<String> = emptySet(),
    /** Currency shown on screen. (The stored amounts do not change; this only changes how they are labelled.) */
    val displayCurrency: String = "PKR",
    val showCurrencySymbol: Boolean = true,
    // --- Record form customization ---
    val formSaveAtTop: Boolean = true,
    val formPickersInRow: Boolean = true,
    val formPickersAboveAmount: Boolean = true,
    val formCustomKeypad: Boolean = false,
    val formNotesAboveAmount: Boolean = true,
    val formDateBelowKeypad: Boolean = false,
    val balanceScope: BalanceScope = BalanceScope.ALL,
    /** How many decimals amounts show: 0, 1 or 2. */
    val decimals: Int = 2,
    /** The Home balance figure is hidden (shown as dots). Remembered between launches. */
    val hideBalance: Boolean = false,
    /** Home dashboard cards the user switched off (keys from HomeCard). */
    val hiddenHomeCards: Set<String> = emptySet(),
    /** Order of the Home dashboard cards (keys from HomeCard). */
    val homeCardOrder: List<String> = HomeCard.entries.map { it.name },
    /** Home shows three wide cards (month total, budget, expected closing) instead of six tiles. */
    val compactHome: Boolean = false,
    /** When on, sub-categories can be created and each has its own budget and analysis. */
    val subcategoriesEnabled: Boolean = false,
    // --- Reminders and backups ---
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val autoBackupFrequency: BackupFrequency = BackupFrequency.OFF,
    val lastAutoBackupAt: Long = 0L,
    /** A folder the user chose (as a content URI) that automatic backups are also copied into; empty when unset. */
    val autoBackupFolder: String = "",
    val dismissedAlertIds: Set<String> = emptySet()
)

val DEFAULT_TAB_ORDER = listOf("home", "transactions", "budgets", "accounts", "reports")

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
        val CARRY_FORWARD = booleanPreferencesKey("carry_forward_balance")
        val ROLLOVER_BUDGETS = booleanPreferencesKey("rollover_budgets")
        val SHOW_ACCOUNTS_IN_BAR = booleanPreferencesKey("show_accounts_in_bar")
        val PICKER_STYLE = stringPreferencesKey("picker_style")
        val SEEN_ALERTS = stringSetPreferencesKey("seen_alert_ids")
        val TAB_ORDER = stringPreferencesKey("tab_order_v2")
        val HIDDEN_TABS = stringPreferencesKey("hidden_tabs_v2")
        val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        val SHOW_CURRENCY_SYMBOL = booleanPreferencesKey("show_currency_symbol")
        val FORM_SAVE_TOP = booleanPreferencesKey("form_save_top")
        val FORM_PICKERS_ROW = booleanPreferencesKey("form_pickers_row")
        val FORM_PICKERS_ABOVE = booleanPreferencesKey("form_pickers_above")
        val FORM_KEYPAD = booleanPreferencesKey("form_keypad")
        val FORM_NOTES_ABOVE = booleanPreferencesKey("form_notes_above")
        val FORM_DATE_BELOW = booleanPreferencesKey("form_date_below")
        val BALANCE_SCOPE = stringPreferencesKey("balance_scope")
        val DECIMALS = intPreferencesKey("display_decimals")
        val HIDE_BALANCE = booleanPreferencesKey("hide_balance")
        val HIDDEN_HOME_CARDS = stringSetPreferencesKey("hidden_home_cards")
        val HOME_CARD_ORDER = stringPreferencesKey("home_card_order")
        val HOME_COMPACT = booleanPreferencesKey("home_compact")
        val SUBCATEGORIES = booleanPreferencesKey("subcategories_enabled")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup_at")
        val AUTO_BACKUP_FOLDER = stringPreferencesKey("auto_backup_folder")
        val DISMISSED_ALERTS = stringSetPreferencesKey("dismissed_alert_ids")
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
                ?: OnboardingStatus.NOT_STARTED,
            carryForwardBalance = prefs[Keys.CARRY_FORWARD] ?: true,
            rolloverBudgets = prefs[Keys.ROLLOVER_BUDGETS] ?: false,
            showAccountsInBottomBar = prefs[Keys.SHOW_ACCOUNTS_IN_BAR] ?: false,
            pickerStyle = prefs[Keys.PICKER_STYLE]
                ?.let { runCatching { PickerStyle.valueOf(it) }.getOrNull() }
                ?: PickerStyle.DROPDOWN,
            seenAlertIds = prefs[Keys.SEEN_ALERTS] ?: emptySet(),
            tabOrder = parseTabOrder(prefs[Keys.TAB_ORDER]),
            hiddenTabs = prefs[Keys.HIDDEN_TABS]
                ?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet(),
            displayCurrency = prefs[Keys.DISPLAY_CURRENCY] ?: "PKR",
            showCurrencySymbol = prefs[Keys.SHOW_CURRENCY_SYMBOL] ?: true,
            formSaveAtTop = prefs[Keys.FORM_SAVE_TOP] ?: true,
            formPickersInRow = prefs[Keys.FORM_PICKERS_ROW] ?: true,
            formPickersAboveAmount = prefs[Keys.FORM_PICKERS_ABOVE] ?: true,
            formCustomKeypad = prefs[Keys.FORM_KEYPAD] ?: false,
            formNotesAboveAmount = prefs[Keys.FORM_NOTES_ABOVE] ?: true,
            formDateBelowKeypad = prefs[Keys.FORM_DATE_BELOW] ?: false,
            balanceScope = prefs[Keys.BALANCE_SCOPE]
                ?.let { name -> BalanceScope.entries.firstOrNull { it.name == name } }
                ?: BalanceScope.ALL,
            decimals = (prefs[Keys.DECIMALS] ?: 2).coerceIn(0, 2),
            hideBalance = prefs[Keys.HIDE_BALANCE] ?: false,
            hiddenHomeCards = prefs[Keys.HIDDEN_HOME_CARDS] ?: emptySet(),
            homeCardOrder = parseHomeCardOrder(prefs[Keys.HOME_CARD_ORDER]),
            compactHome = prefs[Keys.HOME_COMPACT] ?: false,
            subcategoriesEnabled = prefs[Keys.SUBCATEGORIES] ?: false,
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 21,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
            autoBackupFrequency = prefs[Keys.AUTO_BACKUP_FREQUENCY]
                ?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() }
                ?: BackupFrequency.OFF,
            lastAutoBackupAt = prefs[Keys.LAST_AUTO_BACKUP] ?: 0L,
            autoBackupFolder = prefs[Keys.AUTO_BACKUP_FOLDER] ?: "",
            dismissedAlertIds = prefs[Keys.DISMISSED_ALERTS] ?: emptySet()
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

    suspend fun setCarryForwardBalance(enabled: Boolean) = context.dataStore.edit { it[Keys.CARRY_FORWARD] = enabled }

    suspend fun setRolloverBudgets(enabled: Boolean) = context.dataStore.edit { it[Keys.ROLLOVER_BUDGETS] = enabled }

    suspend fun setShowAccountsInBottomBar(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_ACCOUNTS_IN_BAR] = enabled }

    suspend fun setPickerStyle(style: PickerStyle) = context.dataStore.edit { it[Keys.PICKER_STYLE] = style.name }

    suspend fun markAlertsSeen(ids: Set<String>) = context.dataStore.edit { it[Keys.SEEN_ALERTS] = ids }

    suspend fun setTabOrder(order: List<String>) = context.dataStore.edit { it[Keys.TAB_ORDER] = order.joinToString(",") }

    suspend fun setHiddenTabs(hidden: Set<String>) = context.dataStore.edit { it[Keys.HIDDEN_TABS] = hidden.joinToString(",") }

    suspend fun setDisplayCurrency(code: String) = context.dataStore.edit { it[Keys.DISPLAY_CURRENCY] = code }

    suspend fun setShowCurrencySymbol(show: Boolean) = context.dataStore.edit { it[Keys.SHOW_CURRENCY_SYMBOL] = show }

    suspend fun setFormSaveAtTop(on: Boolean) = context.dataStore.edit { it[Keys.FORM_SAVE_TOP] = on }

    suspend fun setFormPickersInRow(on: Boolean) = context.dataStore.edit { it[Keys.FORM_PICKERS_ROW] = on }

    suspend fun setFormPickersAboveAmount(on: Boolean) = context.dataStore.edit { it[Keys.FORM_PICKERS_ABOVE] = on }

    suspend fun setFormCustomKeypad(on: Boolean) = context.dataStore.edit { it[Keys.FORM_KEYPAD] = on }

    suspend fun setFormDateBelowKeypad(on: Boolean) = context.dataStore.edit { it[Keys.FORM_DATE_BELOW] = on }

    suspend fun setDecimals(value: Int) = context.dataStore.edit { it[Keys.DECIMALS] = value.coerceIn(0, 2) }

    suspend fun setHideBalance(hidden: Boolean) = context.dataStore.edit { it[Keys.HIDE_BALANCE] = hidden }

    suspend fun setHiddenHomeCards(keys: Set<String>) = context.dataStore.edit { it[Keys.HIDDEN_HOME_CARDS] = keys }

    suspend fun setCompactHome(on: Boolean) = context.dataStore.edit { it[Keys.HOME_COMPACT] = on }

    suspend fun setHomeCardOrder(order: List<String>) = context.dataStore.edit { it[Keys.HOME_CARD_ORDER] = order.joinToString(",") }

    suspend fun setSubcategoriesEnabled(on: Boolean) = context.dataStore.edit { it[Keys.SUBCATEGORIES] = on }

    /**
     * Every stored setting as JSON (name, type, value), so a backup can carry the user's whole setup
     * (name, theme, currency, tab order, form options, dashboard cards...) without listing each key by hand.
     */
    suspend fun exportAllJson(): org.json.JSONObject {
        val out = org.json.JSONObject()
        context.dataStore.data.first().asMap().forEach { (key, value) ->
            val entry = org.json.JSONObject()
            when (value) {
                is String -> { entry.put("t", "s"); entry.put("v", value) }
                is Boolean -> { entry.put("t", "b"); entry.put("v", value) }
                is Int -> { entry.put("t", "i"); entry.put("v", value) }
                is Long -> { entry.put("t", "l"); entry.put("v", value) }
                is Set<*> -> {
                    entry.put("t", "set")
                    entry.put("v", org.json.JSONArray().apply { value.forEach { put(it.toString()) } })
                }
                else -> return@forEach
            }
            out.put(key.name, entry)
        }
        return out
    }

    /** Applies settings from [exportAllJson]; keys it doesn't know how to read are skipped. */
    suspend fun importAllJson(json: org.json.JSONObject) {
        context.dataStore.edit { prefs ->
            json.keys().forEach { name ->
                // Where the welcome flow stands is about this install, not about the data, so it is not restored.
                if (name == "onboarding_status" || name == "auto_backup_folder" || name == "last_auto_backup_at") return@forEach
                val entry = json.optJSONObject(name) ?: return@forEach
                when (entry.optString("t")) {
                    "s" -> prefs[stringPreferencesKey(name)] = entry.getString("v")
                    "b" -> prefs[booleanPreferencesKey(name)] = entry.getBoolean("v")
                    "i" -> prefs[intPreferencesKey(name)] = entry.getInt("v")
                    "l" -> prefs[longPreferencesKey(name)] = entry.getLong("v")
                    "set" -> {
                        val arr = entry.getJSONArray("v")
                        val set = HashSet<String>()
                        for (i in 0 until arr.length()) set.add(arr.getString(i))
                        prefs[stringSetPreferencesKey(name)] = set
                    }
                }
            }
        }
    }

    suspend fun setBalanceScope(scope: BalanceScope) = context.dataStore.edit { it[Keys.BALANCE_SCOPE] = scope.name }

    suspend fun setFormNotesAboveAmount(on: Boolean) = context.dataStore.edit { it[Keys.FORM_NOTES_ABOVE] = on }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.REMINDER_ENABLED] = enabled
        it[Keys.REMINDER_HOUR] = hour
        it[Keys.REMINDER_MINUTE] = minute
    }

    suspend fun setAutoBackupFrequency(frequency: BackupFrequency) =
        context.dataStore.edit { it[Keys.AUTO_BACKUP_FREQUENCY] = frequency.name }

    suspend fun setAutoBackupFolder(uri: String) = context.dataStore.edit { it[Keys.AUTO_BACKUP_FOLDER] = uri }

    suspend fun setLastAutoBackupAt(timeMillis: Long) = context.dataStore.edit { it[Keys.LAST_AUTO_BACKUP] = timeMillis }

    suspend fun setDismissedAlertIds(ids: Set<String>) = context.dataStore.edit { it[Keys.DISMISSED_ALERTS] = ids }

    private fun parseHomeCardOrder(raw: String?): List<String> {
        val all = HomeCard.entries.map { it.name }
        val saved = raw?.split(",")?.filter { it in all }?.distinct() ?: emptyList()
        return saved + all.filter { it !in saved }
    }

    private fun parseTabOrder(raw: String?): List<String> {
        val saved = raw?.split(",")?.filter { it in DEFAULT_TAB_ORDER }?.distinct() ?: emptyList()
        // Any tab missing from the saved order (e.g. after an update) is appended so it never disappears.
        return saved + DEFAULT_TAB_ORDER.filter { it !in saved }
    }
}
