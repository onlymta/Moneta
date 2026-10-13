package com.moneta.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.ui.accounts.AccountsScreen
import com.moneta.app.ui.accounts.AddEditAccountScreen
import com.moneta.app.ui.backup.BackupScreen
import com.moneta.app.ui.budgets.AddEditBudgetScreen
import com.moneta.app.ui.budgets.BudgetsScreen
import com.moneta.app.ui.categories.AddEditCategoryScreen
import com.moneta.app.ui.categories.CategoriesScreen
import com.moneta.app.ui.bills.AddBillScreen
import com.moneta.app.ui.bills.BillsScreen
import com.moneta.app.ui.dashboard.HomeScreen
import com.moneta.app.ui.goals.AddGoalScreen
import com.moneta.app.ui.goals.ContributeToGoalScreen
import com.moneta.app.ui.goals.GoalsScreen
import com.moneta.app.ui.importexport.ExportScreen
import com.moneta.app.ui.importexport.ImportScreen
import com.moneta.app.ui.insights.InsightsScreen
import com.moneta.app.data.settings.OnboardingStatus
import com.moneta.app.ui.more.MoreScreen
import com.moneta.app.ui.onboarding.OnboardingExit
import com.moneta.app.ui.onboarding.OnboardingScreen
import com.moneta.app.ui.recurring.AddRecurringScreen
import com.moneta.app.ui.recurring.RecurringScreen
import com.moneta.app.ui.quickadd.QuickAddScreen
import com.moneta.app.ui.reports.ReportsScreen
import com.moneta.app.ui.settings.SettingsScreen
import com.moneta.app.ui.settings.SettingsViewModel
import com.moneta.app.ui.transactiondetail.TransactionDetailScreen
import com.moneta.app.ui.transactions.TransactionsScreen

/**
 * The app's single top-level scaffold: bottom bar with 5 destinations
 * (Section 4) plus a center FAB that opens Quick Add as a full-screen modal
 * route, rather than burying "add a transaction" a tap deeper inside a tab
 * (Section 7's speed requirement). Reads live settings once here so the
 * Budgets tab and the Goals/Bills rows under More can honestly reflect the
 * Section 26 module toggles without every downstream screen needing to know
 * about Settings.
 */
@Composable
fun MonetaApp(startupViewModel: StartupViewModel = hiltViewModel()) {
    val startRoute by startupViewModel.startRoute.collectAsState()
    val route = startRoute
    if (route == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        MonetaAppContent(startRoute = route)
    }
}

@Composable
private fun MonetaAppContent(
    startRoute: String,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val prefs by settingsViewModel.preferences.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    // The header only appears on the five main tabs; every other screen already has
    // its own title bar (or is a full-screen form).
    val isMainTab = MonetaDestination.bottomBarItems.any { it.route == currentRoute }
    // Welcome flow and Quick Add are focused full-screen pages: no bottom bar, and they
    // handle the system-bar insets themselves.
    val fullScreen = currentRoute == null ||
        currentRoute == ONBOARDING_ROUTE ||
        currentRoute.startsWith(QUICK_ADD_ROUTE)

    Scaffold(
        contentWindowInsets = if (fullScreen) WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) else ScaffoldDefaults.contentWindowInsets,
        topBar = { if (isMainTab) MonetaTopBar(navController, currentRoute) },
        bottomBar = {
            if (!fullScreen) MonetaBottomBar(navController, budgetsEnabled = prefs.budgetsEnabled)
        },
        floatingActionButton = {
            // Only shown on the 3 bottom-nav tabs that have no "add" action of their
            // own (Home/Records/Reports) - Budgets and every sub-screen reached from
            // More (Accounts, Categories, Goals, Bills, add/edit forms, etc.) either
            // have their own dedicated FAB or aren't a place to jump straight into
            // recording a transaction, so showing this one there produced two
            // overlapping plus buttons in the same corner (bug fix).
            val showQuickAddFab = currentRoute == MonetaDestination.Home.route ||
                currentRoute == MonetaDestination.Transactions.route ||
                currentRoute == MonetaDestination.Reports.route
            if (showQuickAddFab) {
                QuickAddFab(navController)
            }
        }
    ) { innerPadding ->
      Column(modifier = Modifier.padding(innerPadding)) {
        if (prefs.onboardingStatus == OnboardingStatus.SKIPPED && currentRoute == MonetaDestination.Home.route) {
            SetupReminderBanner(
                onFinish = { navController.navigate(ONBOARDING_ROUTE) },
                onDismiss = { settingsViewModel.dismissOnboardingReminder() }
            )
        }
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.weight(1f)
        ) {
            composable(ONBOARDING_ROUTE) {
                OnboardingScreen(
                    onExit = { exit ->
                        navController.navigate(MonetaDestination.Home.route) {
                            popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                        when (exit) {
                            OnboardingExit.HOME -> Unit
                            OnboardingExit.ADD_TRANSACTION -> navController.navigate(QUICK_ADD_ROUTE)
                            OnboardingExit.IMPORT -> navController.navigate(IMPORT_ROUTE)
                        }
                    }
                )
            }

            composable(MonetaDestination.Home.route) {
                HomeScreen(navController, userName = prefs.userName)
            }

            composable(MonetaDestination.Transactions.route) {
                TransactionsScreen(
                    onTransactionClick = { id -> navController.navigate(transactionDetailRoute(id)) }
                )
            }

            composable(MonetaDestination.Budgets.route) {
                BudgetsScreen(onAddBudget = { navController.navigate(ADD_BUDGET_ROUTE) })
            }
            composable(ADD_BUDGET_ROUTE) {
                AddEditBudgetScreen(onDone = { navController.popBackStack() })
            }
            composable(MonetaDestination.Reports.route) { ReportsScreen() }

            composable(MonetaDestination.More.route) {
                MoreScreen(
                    goalsEnabled = prefs.goalsEnabled,
                    billsEnabled = prefs.billsEnabled,
                    onAccountsClick = { navController.navigate(ACCOUNTS_ROUTE) },
                    onCategoriesClick = { navController.navigate(CATEGORIES_ROUTE) },
                    onGoalsClick = { navController.navigate(GOALS_ROUTE) },
                    onBillsClick = { navController.navigate(BILLS_ROUTE) },
                    onInsightsClick = { navController.navigate(INSIGHTS_ROUTE) },
                    onImportClick = { navController.navigate(IMPORT_ROUTE) },
                    onExportClick = { navController.navigate(EXPORT_ROUTE) },
                    onBackupClick = { navController.navigate(BACKUP_ROUTE) },
                    onRecurringClick = { navController.navigate(RECURRING_ROUTE) },
                    onSettingsClick = { navController.navigate(SETTINGS_ROUTE) }
                )
            }

            // --- Quick Add (add new, or edit an existing transaction) ---
            composable(
                QUICK_ADD_ROUTE_PATTERN,
                arguments = listOf(navArgument(ARG_TRANSACTION_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getLong(ARG_TRANSACTION_ID)?.takeIf { it >= 0 }
                QuickAddScreen(
                    transactionId = transactionId,
                    onDone = { navController.popBackStack() }
                )
            }

            // --- Transaction detail ---
            composable(
                TRANSACTION_DETAIL_ROUTE_PATTERN,
                arguments = listOf(navArgument(ARG_TRANSACTION_ID) { type = NavType.LongType })
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getLong(ARG_TRANSACTION_ID) ?: return@composable
                TransactionDetailScreen(
                    transactionId = transactionId,
                    onEdit = { id -> navController.navigate(quickAddEditRoute(id)) },
                    onBack = { navController.popBackStack() }
                )
            }

            // --- Accounts ---
            composable(ACCOUNTS_ROUTE) {
                AccountsScreen(
                    onAddAccount = { navController.navigate(ACCOUNT_FORM_ADD_ROUTE) },
                    onEditAccount = { id -> navController.navigate(accountEditRoute(id)) }
                )
            }
            composable(
                ACCOUNT_FORM_ROUTE_PATTERN,
                arguments = listOf(navArgument(ARG_ACCOUNT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getLong(ARG_ACCOUNT_ID)?.takeIf { it >= 0 }
                AddEditAccountScreen(accountId = accountId, onDone = { navController.popBackStack() })
            }

            // --- Categories ---
            composable(CATEGORIES_ROUTE) {
                CategoriesScreen(
                    onAddCategory = { type -> navController.navigate(categoryAddRoute(type)) },
                    onEditCategory = { id ->
                        // The type isn't known at this call site until the category loads;
                        // EXPENSE is a safe route default since AddEditCategoryViewModel.start
                        // re-derives the real type from the loaded category immediately.
                        navController.navigate(categoryEditRoute(CategoryType.EXPENSE, id))
                    },
                    onAddSubcategory = { type, parentId ->
                        navController.navigate(categoryAddSubcategoryRoute(type, parentId))
                    }
                )
            }
            composable(
                CATEGORY_FORM_ROUTE_PATTERN,
                arguments = listOf(
                    navArgument(ARG_CATEGORY_TYPE) { type = NavType.StringType },
                    navArgument(ARG_CATEGORY_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(ARG_CATEGORY_PARENT_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val typeArg = backStackEntry.arguments?.getString(ARG_CATEGORY_TYPE) ?: CategoryType.EXPENSE.name
                val categoryId = backStackEntry.arguments?.getLong(ARG_CATEGORY_ID)?.takeIf { it >= 0 }
                val parentId = backStackEntry.arguments?.getLong(ARG_CATEGORY_PARENT_ID)?.takeIf { it >= 0 }
                AddEditCategoryScreen(
                    type = CategoryType.valueOf(typeArg),
                    categoryId = categoryId,
                    forcedParentId = parentId,
                    onDone = { navController.popBackStack() }
                )
            }

            // --- Goals ---
            composable(GOALS_ROUTE) {
                GoalsScreen(
                    onAddGoal = { navController.navigate(ADD_GOAL_ROUTE) },
                    onContribute = { id -> navController.navigate(contributeGoalRoute(id)) }
                )
            }
            composable(ADD_GOAL_ROUTE) {
                AddGoalScreen(onDone = { navController.popBackStack() })
            }
            composable(
                CONTRIBUTE_GOAL_ROUTE_PATTERN,
                arguments = listOf(navArgument(ARG_GOAL_ID) { type = NavType.LongType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getLong(ARG_GOAL_ID) ?: return@composable
                ContributeToGoalScreen(goalId = goalId, onDone = { navController.popBackStack() })
            }

            // --- Bills ---
            composable(BILLS_ROUTE) {
                BillsScreen(onAddBill = { navController.navigate(ADD_BILL_ROUTE) })
            }
            composable(ADD_BILL_ROUTE) {
                AddBillScreen(onDone = { navController.popBackStack() })
            }

            // --- Insights ---
            composable(INSIGHTS_ROUTE) {
                InsightsScreen()
            }

            // --- Export ---
            composable(EXPORT_ROUTE) {
                ExportScreen()
            }

            // --- Import ---
            composable(IMPORT_ROUTE) {
                ImportScreen()
            }

            // --- Backup ---
            composable(BACKUP_ROUTE) {
                BackupScreen()
            }

            // --- Recurring ---
            composable(RECURRING_ROUTE) {
                RecurringScreen(onAdd = { navController.navigate(ADD_RECURRING_ROUTE) })
            }
            composable(ADD_RECURRING_ROUTE) {
                AddRecurringScreen(onDone = { navController.popBackStack() })
            }

            // --- Settings ---
            composable(SETTINGS_ROUTE) {
                SettingsScreen()
            }
        }
      }
    }
}

/** Shown on Home after "Do this later" so the welcome setup isn't forgotten. */
@Composable
private fun SetupReminderBanner(onFinish: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Finish setting up Moneta",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onFinish) { Text("Continue") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun MonetaBottomBar(navController: NavHostController, budgetsEnabled: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val items = MonetaDestination.bottomBarItems.filter { destination ->
        destination != MonetaDestination.Budgets || budgetsEnabled
    }

    NavigationBar {
        items.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(destination.route) },
                icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                label = { Text(stringResource(destination.labelRes)) }
            )
        }
    }
}

/**
 * Switches to a bottom-bar tab. Tab-tap bug fix: the old code mixed
 * saveState/restoreState for most tabs with a different pop-inclusive rule for
 * More. Sub-screens (Accounts, Categories, Add Budget, ...) are plain top-level
 * routes, not nested graphs, so after switching tabs a few times the saved
 * stacks got restored under the wrong tab - taps then opened a different tab
 * or seemed to do nothing. One rule for every tab, with nothing saved or
 * restored: pop back to Home, then open the tab on top (a tap on the tab
 * you're already on is ignored). Every screen reloads its data from the
 * database, so nothing the user entered is lost by not saving tab state.
 */
private fun NavHostController.navigateToTab(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo(MonetaDestination.Home.route) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * The header shown on the five main tabs: logo + app name on the left, quick
 * actions on the right. Search jumps to Records (which has the search box);
 * Settings opens the settings screen. Sub-screens keep their own title bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonetaTopBar(navController: NavHostController, currentRoute: String?) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(com.moneta.app.R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        actions = {
            if (currentRoute != MonetaDestination.Transactions.route) {
                IconButton(onClick = { navController.navigateToTab(MonetaDestination.Transactions.route) }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search transactions")
                }
            }
            IconButton(onClick = { navController.navigate(SETTINGS_ROUTE) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
    )
}

@Composable
private fun QuickAddFab(navController: NavHostController) {
    FloatingActionButton(onClick = { navController.navigate(QUICK_ADD_ROUTE) }) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(com.moneta.app.R.string.nav_add))
    }
}
