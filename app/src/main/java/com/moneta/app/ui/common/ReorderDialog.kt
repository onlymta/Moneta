package com.moneta.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Lets the user put a list in the order they want. The first item is the first one offered in the
 * record form. [onMostUsed], when given, adds a button that puts the most used items first.
 */
@Composable
fun ReorderDialog(
    title: String,
    items: List<Pair<Long, String>>,
    onMostUsed: (suspend (List<Long>) -> List<Long>)?,
    onSave: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var order by remember { mutableStateOf(items.map { it.first }) }
    val names = remember(items) { items.toMap() }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "The ones at the top are offered first when you record something.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onMostUsed != null) {
                    OutlinedButton(
                        onClick = { scope.launch { order = onMostUsed(order) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Put the most used first") }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    order.forEachIndexed { index, id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (index + 1).toString() + ". " + (names[id] ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = index > 0,
                                onClick = {
                                    val list = order.toMutableList()
                                    list.removeAt(index)
                                    list.add(index - 1, id)
                                    order = list
                                }
                            ) { Text("Up") }
                            TextButton(
                                enabled = index < order.size - 1,
                                onClick = {
                                    val list = order.toMutableList()
                                    list.removeAt(index)
                                    list.add(index + 1, id)
                                    order = list
                                }
                            ) { Text("Down") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(order) }) { Text("Save order") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
