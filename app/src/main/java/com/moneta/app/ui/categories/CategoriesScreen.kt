@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moneta.app.ui.common.FabStyle
import com.moneta.app.ui.common.MovableFab
import com.moneta.app.ui.common.SubPageHeader
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.Category
import com.moneta.app.ui.common.IconBadge
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun CategoriesScreen(
    onBack: (() -> Unit)?,
    onAddCategory: (CategoryType) -> Unit,
    onEditCategory: (Long) -> Unit,
    onAddSubcategory: (CategoryType, Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var reordering by remember { mutableStateOf(false) }
    val plusVm: com.moneta.app.ui.plus.PlusViewModel = hiltViewModel()
    val plusState by plusVm.state.collectAsState()
    val openPlus = com.moneta.app.ui.plus.LocalOpenPlus.current

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { SubPageHeader("Categories", onBack) },
        floatingActionButton = {
            MovableFab(
                onClick = { onAddCategory(state.selectedType) },
                contentDescription = "Add category",
                style = FabStyle.OTHER
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val tabIndex = if (state.selectedType == CategoryType.EXPENSE) 0 else 1
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { viewModel.onTypeSelected(CategoryType.EXPENSE) },
                    text = { Text("Expense") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { viewModel.onTypeSelected(CategoryType.INCOME) },
                    text = { Text("Income") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MonetaSpacing.screenHorizontal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sub-categories", style = MaterialTheme.typography.bodyMedium)
                    if (!plusState.hasPlus) {
                        Text(
                            "A Moneta Plus feature",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                androidx.compose.material3.Switch(
                    checked = state.subcategoriesEnabled,
                    onCheckedChange = { on ->
                        if (plusState.hasPlus) viewModel.setSubcategoriesEnabled(on) else openPlus()
                    }
                )
                TextButton(onClick = { reordering = true }) { Text("Reorder") }
            }

            if (!state.isLoading && state.topLevelCategories.isEmpty()) {
                CategoriesEmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = MonetaSpacing.screenHorizontal, end = MonetaSpacing.screenHorizontal, top = MonetaSpacing.screenHorizontal, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    items(state.tree, key = { it.category.id }) { node ->
                        CategoryRow(
                            category = node.category,
                            onClick = { onEditCategory(node.category.id) },
                            onDelete = { viewModel.requestDelete(node.category) },
                            onAddSub = if (state.subcategoriesEnabled) {
                                { onAddSubcategory(state.selectedType, node.category.id) }
                            } else null,
                            modifier = Modifier.padding(start = MonetaSpacing.xl * node.depth)
                        )
                    }
                }
            }
        }
    }

    if (reordering) {
        com.moneta.app.ui.common.ReorderDialog(
            title = "Reorder categories",
            items = state.tree.map { node -> node.category.id to ("    ".repeat(node.depth) + node.category.name) },
            onMostUsed = { ids -> viewModel.mostUsedFirst(ids) },
            onSave = { ids ->
                viewModel.saveOrder(ids)
                reordering = false
            },
            onDismiss = { reordering = false }
        )
    }

    state.pendingReassignment?.let { pending ->
        val allCategories = state.allCategories
        ReassignmentDialog(
            categoryName = pending.categoryToDelete.name,
            transactionCount = pending.transactionCount,
            otherCategories = allCategories.filter { it.id != pending.categoryToDelete.id },
            onConfirm = viewModel::confirmReassignmentAndDelete,
            onDismiss = viewModel::cancelReassignment
        )
    }
}

@Composable
private fun CategoriesEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No categories yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text("Add one to start organizing your transactions.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onAddSub: (() -> Unit)? = null
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickableCategory(onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(iconName = category.icon, colorHex = category.colorHex, size = 36.dp)
                Spacer(Modifier.width(MonetaSpacing.md))
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
            }
            if (onAddSub != null) {
                IconButton(onClick = onAddSub) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a sub-category inside " + category.name)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete category")
            }
        }
    }
}

private fun Modifier.clickableCategory(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun ReassignmentDialog(
    categoryName: String,
    transactionCount: Int,
    otherCategories: List<Category>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(otherCategories.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $transactionCount transactions?") },
        text = {
            Column {
                Text(
                    "\"$categoryName\" has $transactionCount transaction(s). Choose a category to move them to before deleting."
                )
                Spacer(Modifier.height(MonetaSpacing.sm))
                if (otherCategories.isEmpty()) {
                    Text(
                        "Create another category of this type first.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    otherCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableCategory { selectedId = category.id }
                                .padding(vertical = MonetaSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedId == category.id,
                                onClick = { selectedId = category.id }
                            )
                            Text(category.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let(onConfirm) },
                enabled = selectedId != null
            ) { Text("Move and delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
