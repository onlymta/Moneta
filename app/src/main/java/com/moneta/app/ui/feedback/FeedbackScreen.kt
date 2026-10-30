@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.feedback

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.moneta.app.BuildConfig
import com.moneta.app.core.AppInfo
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing

private val kinds = listOf("Idea", "Problem", "Praise")

/** A short form that opens the phone's email app with the feedback already written. */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(kinds[0]) }
    var message by rememberSaveable { mutableStateOf("") }
    var includeDetails by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Send feedback", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            Text(
                "Found a problem or have an idea? Tell me. I read every message.",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                kinds.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = { Text(option) }
                    )
                }
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Your message") },
                minLines = 5,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeDetails, onCheckedChange = { includeDetails = it })
                Text(
                    "Include app version and phone model (helps me fix problems)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = {
                    val body = buildString {
                        append(message.trim())
                        if (includeDetails) {
                            append("\n\n---\nExpendora ").append(BuildConfig.VERSION_NAME)
                            append(" (build ").append(BuildConfig.VERSION_CODE).append(")")
                            append("\nAndroid ").append(Build.VERSION.RELEASE)
                            append(" · ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                        }
                    }
                    val mail = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        if (AppInfo.CONTACT_EMAIL.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(AppInfo.CONTACT_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, "Expendora feedback: $kind")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    try {
                        context.startActivity(mail)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No email app found on this phone", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send feedback") }
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + AppInfo.PACKAGE_NAME)))
                    } catch (e: ActivityNotFoundException) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.PLAY_URL)))
                        } catch (e2: ActivityNotFoundException) {
                            Toast.makeText(context, "Couldn't open Google Play", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Rate Expendora on Google Play") }
        }
    }
}
