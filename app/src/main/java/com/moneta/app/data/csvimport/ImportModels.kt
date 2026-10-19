package com.moneta.app.data.csvimport

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.local.entity.TransactionType
import java.time.LocalDate

/** One thing wrong (error) or auto-adjusted (warning) about a specific data row, 1-indexed against the sheet including its header row. */
data class ImportIssue(val rowNumber: Int, val message: String)

/** An account named in the file that doesn't exist yet; it is created on import. */
data class NewAccountPlan(val name: String, val type: AccountType)

/**
 * A validated row. Accounts and categories are kept as NAMES here and are
 * resolved (and created when missing) at commit time by
 * [TransactionImportCommitter], so nothing is written during preview and a
 * name repeated across thousands of rows is created exactly once.
 */
data class PendingImportRow(
    val rowNumber: Int,
    val type: TransactionType,
    val amount: Money,
    val accountName: String,
    val toAccountName: String?,
    /** Null for a transfer. */
    val categoryTopLevelName: String?,
    val categorySubName: String?,
    val date: LocalDate,
    val timeMinutesSinceMidnight: Int,
    val payee: String?,
    val notes: String?,
    val paymentMethod: String?,
    val tagNames: List<String>
)

/** The result of reading and validating a whole file, before anything is written to the database. */
data class ImportPreview(
    val dataRowCount: Int,
    val rows: List<PendingImportRow>,
    /** Rows that match a transaction already in the app; they are not imported again. */
    val duplicates: List<PendingImportRow> = emptyList(),
    /** Rows that cannot be imported at all (they will be skipped), with the reason. */
    val errors: List<ImportIssue>,
    /** Things that were fixed or filled in automatically, row by row. */
    val warnings: List<ImportIssue>,
    /** Categories (formatted "Type: Top level > Sub") that don't exist yet and will be created on import. */
    val newCategoryLabels: Set<String>,
    val newAccounts: List<NewAccountPlan> = emptyList(),
    /** File-level remarks, e.g. how ambiguous dates were read. */
    val notes: List<String> = emptyList(),
    val incomeMinor: Long = 0L,
    val expenseMinor: Long = 0L,
    val transferCount: Int = 0,
    val currencyCode: String = "",
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null
) {
    val readyToImportCount: Int get() = rows.size
}

/** What actually happened after the user confirmed and the batch was written. */
data class ImportOutcome(
    val importedCount: Int,
    val createdCategoryCount: Int,
    val createdAccountCount: Int = 0
)

/** Lower-cases and collapses whitespace so "  Cash  Wallet" and "cash wallet" are the same name. */
fun normalizeImportName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

/** Best-effort account type from a free-text account name (the user can change it later). */
fun guessAccountType(name: String): AccountType {
    val n = name.lowercase()
    return when {
        n.contains("cash") || n.contains("wallet cash") -> AccountType.CASH
        n.contains("credit") || n.contains("card") -> AccountType.CREDIT_CARD
        n.contains("saving") -> AccountType.SAVINGS
        n.contains("jazz") || n.contains("easypaisa") || n.contains("easy paisa") ||
            n.contains("sadapay") || n.contains("nayapay") || n.contains("wallet") -> AccountType.E_WALLET
        n.contains("bank") || n.contains("hbl") || n.contains("ubl") || n.contains("mcb") ||
            n.contains("meezan") || n.contains("alfalah") || n.contains("account") -> AccountType.BANK
        else -> AccountType.OTHER
    }
}
