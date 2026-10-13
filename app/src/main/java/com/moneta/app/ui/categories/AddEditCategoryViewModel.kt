package com.moneta.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val categoryColorPalette = listOf(
    "#C4432B", "#B8860B", "#5B6B76", "#7A5C99", "#2E7D91",
    "#946B4D", "#8A6D3B", "#4C6B8A", "#6B8A4C", "#8A4C6B", "#1E8E5A", "#0F6B5C"
)

const val DEFAULT_CATEGORY_ICON = "category"

data class AddEditCategoryUiState(
    val isEditing: Boolean = false,
    val type: CategoryType = CategoryType.EXPENSE,
    val name: String = "",
    val icon: String = DEFAULT_CATEGORY_ICON,
    val colorHex: String = categoryColorPalette.first(),
    /** Top-level categories of this type that could be this category's parent (Section 12 subcategories). */
    val availableParents: List<Category> = emptyList(),
    val selectedParentId: Long? = null,
    val nameError: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class AddEditCategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditCategoryUiState())
    val uiState: StateFlow<AddEditCategoryUiState> = _uiState.asStateFlow()

    private var editingCategoryId: Long? = null

    /**
     * [forcedParentId] lets the Categories list's "Add subcategory under X"
     * action pre-select a parent directly, skipping a step for the most
     * common case of adding a subcategory from within its parent's context.
     */
    fun start(type: CategoryType, categoryId: Long?, forcedParentId: Long? = null) {
        _uiState.update { it.copy(type = type, selectedParentId = forcedParentId ?: it.selectedParentId) }
        loadParents(type, excludeId = categoryId)

        if (categoryId != null && editingCategoryId != categoryId) {
            editingCategoryId = categoryId
            viewModelScope.launch {
                val category = categoryRepository.getById(categoryId) ?: return@launch
                _uiState.update {
                    it.copy(
                        isEditing = true,
                        type = category.type,
                        name = category.name,
                        icon = category.icon,
                        colorHex = category.colorHex,
                        selectedParentId = category.parentCategoryId
                    )
                }
                loadParents(category.type, excludeId = categoryId)
            }
        }
    }

    private fun loadParents(type: CategoryType, excludeId: Long?) {
        viewModelScope.launch {
            val parents = categoryRepository.observeTopLevelByType(type).first()
                .filter { it.id != excludeId }
            _uiState.update { it.copy(availableParents = parents) }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, nameError = null) }

    fun onColorChange(colorHex: String) = _uiState.update { it.copy(colorHex = colorHex) }

    /** Tapping the currently-selected parent again clears it back to "top-level" (Section 12). */
    fun onParentSelected(id: Long?) = _uiState.update {
        it.copy(selectedParentId = if (it.selectedParentId == id) null else id)
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Give this category a name.") }
            return
        }
        viewModelScope.launch {
            val existingId = editingCategoryId
            if (existingId != null) {
                categoryRepository.renameOrRestyle(
                    Category(
                        id = existingId,
                        name = state.name.trim(),
                        type = state.type,
                        icon = state.icon,
                        colorHex = state.colorHex,
                        parentCategoryId = state.selectedParentId,
                        sortOrder = 0,
                        isDefault = false,
                        isArchived = false
                    ),
                    parentCategoryId = state.selectedParentId
                )
            } else {
                categoryRepository.createCategory(
                    name = state.name.trim(),
                    type = state.type,
                    icon = state.icon,
                    colorHex = state.colorHex,
                    parentCategoryId = state.selectedParentId
                )
            }
            _uiState.update { it.copy(saved = true) }
            onSaved()
        }
    }
}
