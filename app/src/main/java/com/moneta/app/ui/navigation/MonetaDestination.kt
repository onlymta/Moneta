package com.moneta.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.moneta.app.R

/**
 * The main-tab destinations. Home, Records, Budgets and Reports are always in the
 * bottom bar; Accounts appears there only when switched on under
 * Settings > Advanced settings > Customization. Everything else lives in the
 * side drawer. [More] is no longer in the bar (its route still exists).
 */
sealed class MonetaDestination(val route: String, val icon: ImageVector, val labelRes: Int) {
    data object Home : MonetaDestination("home", Icons.Outlined.Home, R.string.nav_home)
    data object Transactions : MonetaDestination("transactions", Icons.Outlined.SwapHoriz, R.string.nav_transactions)
    data object Budgets : MonetaDestination("budgets", Icons.Outlined.AccountBalanceWallet, R.string.nav_budgets)
    data object Reports : MonetaDestination("reports", Icons.Outlined.BarChart, R.string.nav_reports)
    data object Accounts : MonetaDestination("accounts", Icons.Outlined.AccountBalance, R.string.nav_accounts)
    data object More : MonetaDestination("more", Icons.Outlined.MoreHoriz, R.string.nav_more)

    companion object {
        // Launch-crash fix: this MUST be a getter, not `val bottomBarItems = listOf(...)`.
        // A stored property here runs inside MonetaDestination's static initializer,
        // which is triggered while the first subobject (Home) is itself still being
        // created - at that moment Home.INSTANCE is still null, so the list got a
        // null first entry and the bottom bar crashed with a NullPointerException
        // the first time it drew (every launch). A getter builds the list on demand,
        // after all five objects fully exist.
        val bottomBarItems: List<MonetaDestination>
            get() = listOf(Home, Transactions, Budgets, Accounts, Reports)
    }
}

/** Maps a stored tab key (see AppPreferences.tabOrder) to its destination. */
fun destinationForKey(key: String): MonetaDestination? = when (key) {
    "home" -> MonetaDestination.Home
    "transactions" -> MonetaDestination.Transactions
    "budgets" -> MonetaDestination.Budgets
    "reports" -> MonetaDestination.Reports
    "accounts" -> MonetaDestination.Accounts
    else -> null
}

/** Quick Add sheet destination, opened from the center FAB - not part of the bottom bar. */
const val QUICK_ADD_ROUTE = "quick_add"
private const val TRANSACTION_ID_ARG = "transactionId"
const val QUICK_ADD_ROUTE_PATTERN = "$QUICK_ADD_ROUTE?$TRANSACTION_ID_ARG={$TRANSACTION_ID_ARG}"
fun quickAddEditRoute(transactionId: Long) = "$QUICK_ADD_ROUTE?$TRANSACTION_ID_ARG=$transactionId"

const val TRANSACTION_DETAIL_ROUTE_PATTERN = "transaction_detail/{$TRANSACTION_ID_ARG}"
fun transactionDetailRoute(transactionId: Long) = "transaction_detail/$transactionId"

const val ACCOUNTS_ROUTE = "accounts"
const val CATEGORY_ANALYSIS_ROUTE_PATTERN = "category_analysis/{categoryId}/{monthStart}"
fun categoryAnalysisRoute(categoryId: Long, monthStartEpochDay: Long) = "category_analysis/$categoryId/$monthStartEpochDay"
const val SEARCH_ROUTE = "search"
private const val HISTORY_ACCOUNT_ARG = "historyAccountId"
const val ACCOUNT_HISTORY_ROUTE_PATTERN = "account_history/{$HISTORY_ACCOUNT_ARG}"
fun accountHistoryRoute(accountId: Long) = "account_history/$accountId"
const val NOTIFICATIONS_ROUTE = "notifications"
private const val ACCOUNT_ID_ARG = "accountId"
const val ACCOUNT_FORM_ROUTE_PATTERN = "account_form?$ACCOUNT_ID_ARG={$ACCOUNT_ID_ARG}"
const val ACCOUNT_FORM_ADD_ROUTE = "account_form"
fun accountEditRoute(accountId: Long) = "account_form?$ACCOUNT_ID_ARG=$accountId"

const val CATEGORIES_ROUTE = "categories"
private const val CATEGORY_TYPE_ARG = "categoryType"
private const val CATEGORY_ID_ARG = "categoryId"
private const val CATEGORY_PARENT_ID_ARG = "parentId"
const val CATEGORY_FORM_ROUTE_PATTERN =
    "category_form/{$CATEGORY_TYPE_ARG}?$CATEGORY_ID_ARG={$CATEGORY_ID_ARG}&$CATEGORY_PARENT_ID_ARG={$CATEGORY_PARENT_ID_ARG}"
fun categoryAddRoute(type: com.moneta.app.data.local.entity.CategoryType) = "category_form/${type.name}"
fun categoryEditRoute(type: com.moneta.app.data.local.entity.CategoryType, categoryId: Long) =
    "category_form/${type.name}?$CATEGORY_ID_ARG=$categoryId"
/** Opens the Add Category form with a parent already chosen - Section 12's "add subcategory" shortcut. */
fun categoryAddSubcategoryRoute(type: com.moneta.app.data.local.entity.CategoryType, parentId: Long) =
    "category_form/${type.name}?$CATEGORY_PARENT_ID_ARG=$parentId"


const val GOALS_ROUTE = "goals"
const val ADD_GOAL_ROUTE = "add_goal"
private const val GOAL_ID_ARG = "goalId"
const val CONTRIBUTE_GOAL_ROUTE_PATTERN = "contribute_goal/{$GOAL_ID_ARG}"
fun contributeGoalRoute(goalId: Long) = "contribute_goal/$goalId"

const val BILLS_ROUTE = "bills"
const val ADD_BILL_ROUTE = "add_bill"

const val EXPORT_ROUTE = "export"
const val IMPORT_ROUTE = "import"
const val BACKUP_ROUTE = "backup"

const val RECURRING_ROUTE = "recurring"
const val ADD_RECURRING_ROUTE = "add_recurring"

const val SETTINGS_ROUTE = "settings"
const val ABOUT_ROUTE = "about"

/** First-run welcome/setup flow. */
const val ONBOARDING_ROUTE = "onboarding"

internal const val ARG_TRANSACTION_ID = TRANSACTION_ID_ARG
internal const val ARG_ACCOUNT_ID = ACCOUNT_ID_ARG
internal const val ARG_HISTORY_ACCOUNT_ID = HISTORY_ACCOUNT_ARG
internal const val ARG_CATEGORY_TYPE = CATEGORY_TYPE_ARG
internal const val ARG_CATEGORY_ID = CATEGORY_ID_ARG
internal const val ARG_GOAL_ID = GOAL_ID_ARG
internal const val ARG_CATEGORY_PARENT_ID = CATEGORY_PARENT_ID_ARG
