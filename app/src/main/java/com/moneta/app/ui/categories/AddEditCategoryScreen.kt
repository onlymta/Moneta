package com.moneta.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.moneta.app.ui.common.IconMapper
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

    var iconPickerOpen by remember { mutableStateOf(false) }
    var parentMenuOpen by remember { mutableStateOf(false) }
    val currentTint = Color(android.graphics.Color.parseColor(IconMapper.colorFor(state.icon)))

    FormScreenScaffold(
        title = if (state.isEditing) "Edit category" else "New ${state.type.name.lowercase()} category",
        onClose = onDone
    ) {
        // Icon and name share one row; tapping the icon opens the icon list.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(currentTint.copy(alpha = 0.18f))
                    .border(2.dp, currentTint, CircleShape)
                    .clickable { iconPickerOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconMapper.iconFor(state.icon),
                    contentDescription = "Change icon",
                    tint = currentTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Category name") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                modifier = Modifier.weight(1f)
            )
        }

        if (state.availableParents.isNotEmpty()) {
            val parentLabel = state.availableParents.firstOrNull { it.id == state.selectedParentId }?.label
            Box {
                androidx.compose.material3.Surface(
                    onClick = { parentMenuOpen = true },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = MonetaSpacing.md, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inside", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(parentLabel ?: "Nothing (top-level category)", style = MaterialTheme.typography.bodyLarge)
                        }
                        Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
                androidx.compose.material3.DropdownMenu(expanded = parentMenuOpen, onDismissRequest = { parentMenuOpen = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Nothing (top-level category)") },
                        onClick = {
                            parentMenuOpen = false
                            if (state.selectedParentId != null) viewModel.onParentSelected(state.selectedParentId)
                        }
                    )
                    state.availableParents.forEach { parent ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(parent.label) },
                            onClick = {
                                parentMenuOpen = false
                                if (state.selectedParentId != parent.id) viewModel.onParentSelected(parent.id)
                            }
                        )
                    }
                }
            }
        }

        Button(onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }

    if (iconPickerOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { iconPickerOpen = false },
            title = { Text("Choose an icon") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconMapper.pickerNames.chunked(5).forEach { rowNames ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowNames.forEach { name ->
                                val selected = state.icon == name
                                val tint = Color(android.graphics.Color.parseColor(IconMapper.colorFor(name)))
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) tint.copy(alpha = 0.28f) else tint.copy(alpha = 0.10f))
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) tint else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.onIconChange(name)
                                            iconPickerOpen = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconMapper.iconFor(name),
                                        contentDescription = name,
                                        tint = tint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            repeat(5 - rowNames.size) { Spacer(Modifier.size(44.dp)) }
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { iconPickerOpen = false }) { Text("Close") } }
        )
    }
}
