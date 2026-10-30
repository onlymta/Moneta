package com.moneta.app.ui.help

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing

private data class Faq(val question: String, val answer: String)

private val faqs = listOf(
    Faq(
        "How do I record an expense or income?",
        "Tap the + button on Home or Records. Choose Expense, Income or Transfer, pick the account and category, type the amount and save. " +
            "In Settings you can switch on the built-in calculator keypad and change how the form is laid out."
    ),
    Faq(
        "How do I add or reorder accounts?",
        "Open Accounts (the tab, or the side menu) and tap +. Use \"Reorder or sort accounts\" to put the ones you use most at the top. " +
            "That order is also the order of the pickers in the record form."
    ),
    Faq(
        "How do categories and sub-categories work?",
        "Side menu > Categories. Add your own, change icons and colours, and use Reorder so the common ones come first. " +
            "The Sub-categories switch on that page (a Expendora Plus feature) lets a category hold smaller ones, such as Food > Groceries."
    ),
    Faq(
        "How do I set a budget?",
        "Open the Budgets tab, tap a category and enter its monthly amount. Each month has its own budgets; an empty month offers to copy last month's. " +
            "Tap a category's name or icon to see its transactions, press and hold for its report, and use the arrow to open its sub-categories."
    ),
    Faq(
        "What happens when I spend more or less than a budget?",
        "In Settings you can turn on \"Roll over unspent budget\" (what was left is added to next month's budget) and " +
            "\"Take overspending off next month\" (the extra spent is taken off next month's budget)."
    ),
    Faq(
        "How do goals work?",
        "Side menu > Goals. Add a goal with or without a target date, order your goals, and choose which accounts pay for them. " +
            "Expendora hands your free savings to the goals from the top of the list down and shows which are covered and how much the others still need."
    ),
    Faq(
        "What are bills and recurring payments?",
        "Bills remind you what is due. Scheduled and recurring entries create your regular income and expenses on their own, so you don't type them every month."
    ),
    Faq(
        "How do I see where my money went?",
        "Open Reports. Choose a period, then open Expenses or Income and tap a category for its own analysis. " +
            "The charts need a short ad, or Expendora Plus."
    ),
    Faq(
        "Can I import my old records?",
        "Yes. Side menu > Import takes an Excel or CSV file. Side menu > Export saves your records as Excel, CSV or a PDF report (the PDF needs a short ad, or Plus)."
    ),
    Faq(
        "How do I back up and restore?",
        "Side menu > Backup & restore. Save a backup file anywhere, including Google Drive, and choose it again to restore on any phone. " +
            "Expendora Plus adds automatic backups and an extra copy in a folder you choose."
    ),
    Faq(
        "How do I change the currency, decimals or Home cards?",
        "Side menu > Settings. Currency, symbol, decimals, carry-forward balance, reminders, theme and the Home cards are all there."
    ),
    Faq(
        "What is Expendora Plus?",
        "Expendora is free for tracking your money. Plus is one payment, no subscription: no ads, always-on charts and PDF reports, sub-categories, " +
            "compact Home and automatic backups. You can try everything free for 14 days from the Plus page. Your data is never locked."
    )
)

/** Questions that open to their answers. Shared by the welcome flow and the Help page. */
@Composable
fun GuideCards(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
        faqs.forEach { faq -> FaqRow(faq) }
    }
}

@Composable
private fun FaqRow(faq: Faq) {
    var shown by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .clickable { shown = !shown }
                .padding(MonetaSpacing.cardPadding)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    faq.question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (shown) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (shown) "Hide the answer" else "Show the answer"
                )
            }
            if (shown) {
                Text(
                    faq.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MonetaSpacing.sm)
                )
            }
        }
    }
}

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Help and FAQs", onBack = onBack)
        GuideCards(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm)
        )
    }
}
