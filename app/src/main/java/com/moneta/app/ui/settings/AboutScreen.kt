package com.moneta.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing

private const val PACKAGE_NAME = com.moneta.app.core.AppInfo.PACKAGE_NAME

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenFeedback: () -> Unit = {}) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "About", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text(com.moneta.app.core.AppInfo.APP_FULL_NAME, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Version ${com.moneta.app.BuildConfig.VERSION_NAME} (build ${com.moneta.app.BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "A simple, private money tracker. It works without an account and without internet, and your data stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = MonetaSpacing.sm)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    AboutAction(
                        Icons.Outlined.SystemUpdate,
                        "Check for updates",
                        "Opens Expendora on Google Play. If an Update button shows, tap it."
                    ) { openPlayListing(context) }
                    AboutAction(Icons.Outlined.StarOutline, "Rate Expendora", "A rating helps other people find it") {
                        openPlayListing(context)
                    }
                    AboutAction(Icons.Outlined.Share, "Share Expendora", "Send the app link to a friend") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Try Expendora, a private money tracker: https://play.google.com/store/apps/details?id=$PACKAGE_NAME"
                            )
                        }
                        launch(context, Intent.createChooser(send, "Share Expendora"))
                    }
                    AboutAction(Icons.Outlined.MailOutline, "Send feedback", "Report a problem or suggest something") {
                        onOpenFeedback()
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text("Developer: ${com.moneta.app.core.AppInfo.DEVELOPER_NAME}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Expendora is built and looked after by ${com.moneta.app.core.AppInfo.DEVELOPER_NAME}, an independent developer. There are no accounts and no tracking of your transactions. Ads (when shown) and the one-time Expendora Plus purchase are handled by Google.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "Your feedback decides what gets built next, so please get in touch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MonetaSpacing.cardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(MonetaSpacing.md))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun openPlayListing(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_NAME"))
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME")))
    }
}

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to do that", Toast.LENGTH_SHORT).show()
    }
}
