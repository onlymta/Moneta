package com.moneta.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.ui.common.FormScreenScaffold
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AddEditCategoryScreen(
    type: CategoryType,
    categoryId: Long?,
    onDone: () -> Unit,
    forcedParentId: Long? = null,
    viewModel: AddEditCategoryViewModel = hiltViewModel()
) {
    LaunchedEffect(type, categoryId) {
        viewModel.start(type, categoryId, forcedParentId)
    }
    val state by viewModel.uiState.collectAsState()

    FormScreenScaffold(
        title = if (state.isEditing) "Edit category" else "Add ${state.type.name.lowercase()} category",
        onClose = onDone
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Category name") },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Color", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            items(categoryColorPalette) { colorHex ->
                val color = Color(android.graphics.Color.parseColor(colorHex))
                Column(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { viewModel.onColorChange(colorHex) }
                ) {
                    if (state.colorHex == colorHex) {
                        Text(
                            "\u2713",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // Section 12: subcategories - optional, one level deep. "None" (the default)
        // means this stays a top-level category; picking one nests it underneath.
        if (state.availableParents.isNotEmpty()) {
            Text("Parent category (optional)", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                items(state.availableParents, key = { it.id }) { parent ->
                    FilterChip(
                        selected = state.selectedParentId == parent.id,
                        onClick = { viewModel.onParentSelected(parent.id) },
                        label = { Text(parent.name) }
                    )
                }
            }
            Text(
                if (state.selectedParentId == null) {
                    "This will be a top-level category."
                } else {
                    "This will show nested under \"${state.availableParents.first { it.id == state.selectedParentId }.name}\"."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(0.dp))

        Button(onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}
