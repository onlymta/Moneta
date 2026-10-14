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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.Category
import com.moneta.app.ui.common.IconBadge
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun CategoriesScreen(
    onAddCategory: (CategoryType) -> Unit,
    onEditCategory: (Long) -> Unit,
    onAddSubcategory: (CategoryType, Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categories") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddCategory(state.selectedType) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
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

            if (!state.isLoading && state.topLevelCategories.isEmpty()) {
                CategoriesEmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = MonetaSpacing.screenHorizontal, end = MonetaSpacing.screenHorizontal, top = MonetaSpacing.screenHorizontal, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    items(state.topLevelCategories, key = { it.id }) { category ->
                        Column {
                            CategoryRow(
                                category = category,
                                onClick = { onEditCategory(category.id) },
                                onDelete = { viewModel.requestDelete(category) }
                            )
                            val children = state.subcategoriesByParent[category.id].orEmpty()
                            if (children.isNotEmpty()) {
                                Column(modifier = Modifier.padding(start = MonetaSpacing.xl, top = MonetaSpacing.xs)) {
                                    children.forEach { sub ->
                                        CategoryRow(
                                            category = sub,
                                            onClick = { onEditCategory(sub.id) },
                                            onDelete = { viewModel.requestDelete(sub) },
                                            modifier = Modifier.padding(bottom = MonetaSpacing.xs)
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { onAddSubcategory(state.selectedType, category.id) }) {
                                Text("+ Add subcategory under ${category.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingReassignment?.let { pending ->
        val allCategories = state.topLevelCategories + state.subcategoriesByParent.values.flatten()
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
    modifier: Modifier = Modifier
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
