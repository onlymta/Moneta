package com.moneta.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneta.app.ui.theme.MonetaSpacing

/**
 * The standard shell for a full-screen add/edit form (Quick Add, Add
 * Account, Add Category, Add Budget, Add Goal, Add Bill, Add Recurring,
 * Contribute to Goal). Every one of these is reached by pushing a new
 * screen onto the nav stack with no bottom bar and no FAB of its own, so
 * without an explicit close affordance the only way out was the system back
 * gesture - not discoverable enough for everyone. This puts a visible X in
 * the top bar on every one of them, consistently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreenScaffold(
    title: String,
    onClose: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        val baseModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(MonetaSpacing.screenHorizontal)
        Column(
            modifier = if (scrollable) baseModifier.verticalScroll(rememberScrollState()) else baseModifier,
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg),
            content = content
        )
    }
}
