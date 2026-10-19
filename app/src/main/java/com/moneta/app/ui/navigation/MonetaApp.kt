package com.moneta.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.DrawerState
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.moneta.app.ui.accounts.AccountHistoryScreen
import com.moneta.app.ui.accounts.AddEditAccountScreen
import com.moneta.app.ui.backup.BackupScreen
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
import com.moneta.app.data.settings.OnboardingStatus
import com.moneta.app.ui.notifications.NotificationsScreen
import com.moneta.app.ui.notifications.NotificationsViewModel
import com.moneta.app.ui.search.SearchScreen
import com.moneta.app.ui.onboarding.OnboardingExit
import com.moneta.app.ui.onboarding.OnboardingScreen
import com.moneta.app.ui.recurring.AddRecurringScreen
import com.moneta.app.ui.recurring.RecurringScreen
import com.moneta.app.ui.quickadd.QuickAddScreen
import com.moneta.app.ui.reports.ReportsScreen
import com.moneta.app.ui.settings.SettingsScreen
import com.moneta.app.ui.settings.SettingsViewModel
import com.moneta.app.ui.transactiondetail.TransactionDetailPopup
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
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    notificationsViewModel: NotificationsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val prefs by settingsViewModel.preferences.collectAsState()
    SideEffect {
        com.moneta.app.core.money.DisplayCurrency.code = prefs.displayCurrency
        com.moneta.app.core.money.DisplayCurrency.showSymbol = prefs.showCurrencySymbol
    }
    val reminderContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(prefs.reminderEnabled, prefs.reminderHour, prefs.reminderMinute) {
        if (prefs.reminderEnabled) {
            com.moneta.app.reminder.ReminderScheduler.schedule(reminderContext, prefs.reminderHour, prefs.reminderMinute)
        } else {
            com.moneta.app.reminder.ReminderScheduler.cancel(reminderContext)
        }
    }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    // The header only appears on the five main tabs; every other screen already has
    // its own title bar (or is a full-screen form).
    val alertCount by notificationsViewModel.unseenCount.collectAsState()
    val visibleTabs = prefs.tabOrder.mapNotNull { key ->
        val destination = destinationForKey(key)
        val shown = when {
            destination == null -> false
            key == "home" -> true
            key == "budgets" -> prefs.budgetsEnabled && key !in prefs.hiddenTabs
            else -> key !in prefs.hiddenTabs
        }
        if (shown) destination else null
    }
    val accountsInBar = MonetaDestination.Accounts in visibleTabs
    // Accounts opened from the side menu (when it is not a bottom-bar tab) is a normal side page
    // with its own header and back arrow, like the rest of the side-menu pages.
    val isMainTab = MonetaDestination.bottomBarItems.any { it.route == currentRoute } &&
        (currentRoute != ACCOUNTS_ROUTE || accountsInBar)
    val hiddenTabDestinations = prefs.tabOrder.mapNotNull { key ->
        val destination = destinationForKey(key)
        if (destination != null && destination !in visibleTabs && (key != "budgets" || prefs.budgetsEnabled)) destination else null
    }
    val detailId = remember { mutableStateOf<Long?>(null) }
    val goBack: () -> Unit = { navController.popBackStack() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    // Welcome flow and Quick Add are focused full-screen pages: no bottom bar, and they
    // handle the system-bar insets themselves.
    val fullScreen = currentRoute == null ||
        currentRoute == ONBOARDING_ROUTE ||
        currentRoute == SEARCH_ROUTE ||
        currentRoute.startsWith(QUICK_ADD_ROUTE)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isMainTab,
        drawerContent = {
            MonetaDrawerContent(
                goalsEnabled = prefs.goalsEnabled,
                billsEnabled = prefs.billsEnabled,
                alertCount = alertCount,
                extraTabs = hiddenTabDestinations,
                onItem = { route ->
                    drawerScope.launch { drawerState.close() }
                    navController.openRoute(route)
                }
            )
        }
    ) {
    Scaffold(
        contentWindowInsets = if (fullScreen) WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (isMainTab) {
                MonetaTopBar(
                    navController = navController,
                    alertCount = alertCount,
                    onMenu = { drawerScope.launch { drawerState.open() } }
                )
            }
        },
        bottomBar = {
            if (!fullScreen) {
                MonetaBottomBar(
                    navController,
                    tabs = visibleTabs
                )
            }
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
                QuickAddFab(navController, currentRoute)
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
                HomeScreen(navController, userName = prefs.userName, onTransactionClick = { detailId.value = it })
            }

            composable(MonetaDestination.Transactions.route) {
                TransactionsScreen(
                    onTransactionClick = { id -> detailId.value = id }
                )
            }

            composable(MonetaDestination.Budgets.route) {
                BudgetsScreen(onOpenRecords = { navController.navigateToTab(MonetaDestination.Transactions.route) })
            }
            composable(MonetaDestination.Reports.route) {
                ReportsScreen(onOpenBudgets = { navController.navigateToTab(MonetaDestination.Budgets.route) })
            }

            composable(SEARCH_ROUTE) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route -> navController.openRoute(route) },
                    onOpenTransaction = { id -> detailId.value = id }
                )
            }
            composable(NOTIFICATIONS_ROUTE) {
                NotificationsScreen(onBack = goBack, onOpen = { route -> navController.openRoute(route) })
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

            // --- Accounts ---
            composable(ACCOUNTS_ROUTE) {
                AccountsScreen(
                    onBack = if (accountsInBar) null else goBack,
                    onAddAccount = { navController.navigate(ACCOUNT_FORM_ADD_ROUTE) },
                    onEditAccount = { id -> navController.navigate(accountEditRoute(id)) },
                    onOpenAccount = { id -> navController.navigate(accountHistoryRoute(id)) }
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

            composable(
                ACCOUNT_HISTORY_ROUTE_PATTERN,
                arguments = listOf(navArgument(ARG_HISTORY_ACCOUNT_ID) { type = NavType.LongType })
            ) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getLong(ARG_HISTORY_ACCOUNT_ID) ?: return@composable
                AccountHistoryScreen(
                    accountId = accountId,
                    onBack = { navController.popBackStack() },
                    onTransactionClick = { id -> detailId.value = id },
                    onEdit = { navController.navigate(accountEditRoute(accountId)) }
                )
            }

            // --- Categories ---
            composable(CATEGORIES_ROUTE) {
                CategoriesScreen(
                    onBack = { navController.popBackStack() },
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
                    onBack = { navController.popBackStack() },
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
                BillsScreen(onBack = { navController.popBackStack() }, onAddBill = { navController.navigate(ADD_BILL_ROUTE) })
            }
            composable(ADD_BILL_ROUTE) {
                AddBillScreen(onDone = { navController.popBackStack() })
            }

            // --- Export ---
            composable(EXPORT_ROUTE) {
                ExportScreen(onBack = goBack)
            }

            // --- Import ---
            composable(IMPORT_ROUTE) {
                ImportScreen(onBack = goBack)
            }

            // --- Backup ---
            composable(BACKUP_ROUTE) {
                BackupScreen(onBack = goBack)
            }

            // --- Recurring ---
            composable(RECURRING_ROUTE) {
                RecurringScreen(onBack = { navController.popBackStack() }, onAdd = { navController.navigate(ADD_RECURRING_ROUTE) })
            }
            composable(ADD_RECURRING_ROUTE) {
                AddRecurringScreen(onDone = { navController.popBackStack() })
            }

            composable(ABOUT_ROUTE) {
                com.moneta.app.ui.settings.AboutScreen(onBack = goBack)
            }

            // --- Settings ---
            composable(SETTINGS_ROUTE) {
                SettingsScreen(onBack = goBack, onOpenRoute = { route -> navController.navigate(route) })
            }
        }
      }
    }
    }

    detailId.value?.let { id ->
        TransactionDetailPopup(
            transactionId = id,
            onDismiss = { detailId.value = null },
            onEdit = { txId ->
                detailId.value = null
                navController.navigate(quickAddEditRoute(txId))
            }
        )
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
private fun MonetaBottomBar(navController: NavHostController, tabs: List<MonetaDestination>) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val items = tabs

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
 * The header shown on the main tabs: menu button, logo + app name, then search
 * (whole app), notifications (with a count badge) and settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonetaTopBar(navController: NavHostController, alertCount: Int, onMenu: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.moneta.app.R.drawable.ic_moneta_mark),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(com.moneta.app.R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        actions = {
            IconButton(onClick = { navController.navigate(SEARCH_ROUTE) }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search the whole app")
            }
            IconButton(onClick = { navController.navigate(NOTIFICATIONS_ROUTE) }) {
                BadgedBox(
                    badge = {
                        if (alertCount > 0) {
                            Badge { Text(if (alertCount > 9) "9+" else alertCount.toString()) }
                        }
                    }
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                }
            }
        }
    )
}

/** Opens a route from the drawer, search or a notification: tabs switch tab, everything else stacks on top. */
private fun NavHostController.openRoute(route: String) {
    val isTab = MonetaDestination.bottomBarItems.any { it.route == route }
    if (isTab) navigateToTab(route) else navigate(route)
}

private class DrawerEntry(val label: String, val icon: ImageVector, val route: String)

@Composable
private fun MonetaDrawerContent(
    goalsEnabled: Boolean,
    billsEnabled: Boolean,
    alertCount: Int,
    extraTabs: List<MonetaDestination>,
    onItem: (String) -> Unit
) {
    val entries = buildList {
        extraTabs.forEach { tab ->
            add(DrawerEntry(stringResource(tab.labelRes), tab.icon, tab.route))
        }
        add(DrawerEntry("Categories", Icons.Outlined.Category, CATEGORIES_ROUTE))
        if (billsEnabled) add(DrawerEntry("Bills", Icons.Outlined.Receipt, BILLS_ROUTE))
        add(DrawerEntry("Scheduled & recurring", Icons.Outlined.Repeat, RECURRING_ROUTE))
        if (goalsEnabled) add(DrawerEntry("Goals", Icons.Outlined.Flag, GOALS_ROUTE))
        add(
            DrawerEntry(
                if (alertCount > 0) "Notifications ($alertCount)" else "Notifications",
                Icons.Outlined.Notifications,
                NOTIFICATIONS_ROUTE
            )
        )
        add(DrawerEntry("Import", Icons.Outlined.FileUpload, IMPORT_ROUTE))
        add(DrawerEntry("Export", Icons.Outlined.FileDownload, EXPORT_ROUTE))
        add(DrawerEntry("Backup & restore", Icons.Outlined.Backup, BACKUP_ROUTE))
        add(DrawerEntry("Settings", Icons.Outlined.Settings, SETTINGS_ROUTE))
        add(DrawerEntry("About", Icons.Outlined.Info, ABOUT_ROUTE))
    }
    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(com.moneta.app.R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
            )
            entries.forEach { entry ->
                NavigationDrawerItem(
                    label = { Text(entry.label) },
                    icon = { Icon(entry.icon, contentDescription = null) },
                    selected = false,
                    onClick = { onItem(entry.route) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickAddFab(navController: NavHostController, resetKey: String?) {
    com.moneta.app.ui.common.MovableFab(
        onClick = { navController.navigate(QUICK_ADD_ROUTE) },
        contentDescription = stringResource(com.moneta.app.R.string.nav_add),
        style = com.moneta.app.ui.common.FabStyle.TRANSACTION,
        resetKey = resetKey
    )
}
