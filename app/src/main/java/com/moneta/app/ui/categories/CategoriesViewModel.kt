package com.moneta.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryDeletionResult
import com.moneta.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val selectedType: CategoryType = CategoryType.EXPENSE,
    /** Top-level categories only - each may have entries in [subcategoriesByParent]. */
    val topLevelCategories: List<Category> = emptyList(),
    val subcategoriesByParent: Map<Long, List<Category>> = emptyMap(),
    /** Non-null while the user is being asked to pick a replacement category for a blocked delete (Section 12). */
    val pendingReassignment: PendingReassignment? = null
)

data class PendingReassignment(
    val categoryToDelete: Category,
    val transactionCount: Int
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val selectedTypeFlow = MutableStateFlow(CategoryType.EXPENSE)
    private val pendingReassignmentFlow = MutableStateFlow<PendingReassignment?>(null)

    // StateFlow already only emits when the value actually changes (it's conflated by
    // equality), so no explicit distinctUntilChanged() is needed - kotlinx.coroutines
    // treats calling it directly on a StateFlow as a compile error, not just redundant.
    private val categoriesFlow = selectedTypeFlow
        .flatMapLatest { categoryRepository.observeByType(it) }

    val uiState: StateFlow<CategoriesUiState> = combine(
        selectedTypeFlow, categoriesFlow, pendingReassignmentFlow
    ) { type, categories, pending ->
        // Section 12: one level of subcategory nesting - group the flat list from the
        // repository into top-level categories plus a lookup of each one's children,
        // so the screen can render the hierarchy without a second query.
        val topLevel = categories.filter { it.parentCategoryId == null }
        val byParent = categories.filter { it.parentCategoryId != null }
            .groupBy { it.parentCategoryId!! }
        CategoriesUiState(
            isLoading = false,
            selectedType = type,
            topLevelCategories = topLevel,
            subcategoriesByParent = byParent,
            pendingReassignment = pending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun onTypeSelected(type: CategoryType) {
        selectedTypeFlow.value = type
    }

    /** Section 12: safe deletion - if the category has transactions, this surfaces a reassignment prompt instead. */
    fun requestDelete(category: Category) {
        viewModelScope.launch {
            when (val result = categoryRepository.tryDelete(category.id)) {
                CategoryDeletionResult.Deleted -> Unit
                is CategoryDeletionResult.RequiresReassignment ->
                    pendingReassignmentFlow.value = PendingReassignment(category, result.transactionCount)
            }
        }
    }

    fun confirmReassignmentAndDelete(reassignToCategoryId: Long) {
        val pending = pendingReassignmentFlow.value ?: return
        viewModelScope.launch {
            categoryRepository.deleteWithReassignment(pending.categoryToDelete.id, reassignToCategoryId)
            pendingReassignmentFlow.value = null
        }
    }

    fun cancelReassignment() {
        pendingReassignmentFlow.value = null
    }
}
