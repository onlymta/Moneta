package com.moneta.app.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing

private data class GuideItem(val title: String, val body: String)

private val guideItems = listOf(
    GuideItem(
        "Record a transaction",
        "Tap the + button on Home or Records. Pick expense, income or transfer, choose the account and category, type the amount and save. " +
            "In Settings you can switch on the built-in calculator keypad and change how the form is laid out."
    ),
    GuideItem(
        "Accounts",
        "Cash, bank, wallet, savings and cards each get their own balance. Open the Accounts tab (or the side menu) to add one, see its history, " +
            "or use Reorder to put the ones you use most at the top of the record form."
    ),
    GuideItem(
        "Categories and sub-categories",
        "Side menu > Categories. Add your own, change icons and colours, and reorder them so the common ones come first. " +
            "The Sub-categories switch (a Plus feature) lets a category hold smaller ones, such as Food > Groceries."
    ),
    GuideItem(
        "Budgets",
        "The Budgets tab shows each category's monthly budget against what you spent. Tap a name to see its transactions, press and hold for its report, " +
            "and use the arrow to open sub-categories."
    ),
    GuideItem(
        "Budget plan",
        "Side menu > Budget plan. Enter your expected income and savings, give the rest of the money to your categories, " +
            "and choose what happens next month when you spend more or less than a budget."
    ),
    GuideItem(
        "Goals",
        "Save for a trip or an emergency fund. Add a goal with or without a date, order your goals, and choose which accounts pay for them. " +
            "Moneta shows which goals your savings can cover and how much the others still need."
    ),
    GuideItem(
        "Bills and recurring payments",
        "Bills remind you what is due. Scheduled and recurring entries create your regular income and expenses on their own."
    ),
    GuideItem(
        "Reports",
        "See where the money went by period, compare months, and open any category for its own analysis. " +
            "Charts need a short ad, or Moneta Plus."
    ),
    GuideItem(
        "Import and export",
        "Import old records from Excel or CSV from the side menu. Export your data to Excel, CSV or a PDF report (the PDF needs a short ad, or Plus)."
    ),
    GuideItem(
        "Backup and restore",
        "Side menu > Backup & restore. Save a backup file anywhere, including Google Drive, and restore it on any phone. " +
            "Plus adds automatic backups and an extra copy in a folder you choose."
    ),
    GuideItem(
        "Settings",
        "Currency, decimals, carry-forward balance, the Home cards, reminders and the look of the app are all in Settings."
    ),
    GuideItem(
        "Moneta Plus",
        "Free forever for tracking money. Plus is one payment, no subscription: no ads, always-on charts, sub-categories, compact Home and automatic backups. " +
            "You can try everything free for 14 days from the Plus page."
    )
)

/** The guide cards, shared by the welcome flow and the Help page. */
@Composable
fun GuideCards(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
        guideItems.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Help and guide", onBack = onBack)
        GuideCards(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm)
        )
    }
}
