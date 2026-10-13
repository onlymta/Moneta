package com.moneta.app.data.csvimport

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.TransactionType
import java.time.LocalDate

/** One thing wrong (error) or worth flagging (warning) about a specific data row, 1-indexed against the sheet including its header row. */
data class ImportIssue(val rowNumber: Int, val message: String)

/**
 * A structurally-valid row whose account (and destination account, for a
 * transfer) are already resolved to real ids, but whose category is still
 * just names - see [TransactionImportCommitter]. Categories are resolved at
 * commit time, not parse time, so that the same new category name repeated
 * across hundreds of rows is created exactly once instead of once per row,
 * and so nothing is written to the database during preview.
 */
data class PendingImportRow(
    val rowNumber: Int,
    val type: TransactionType,
    val amount: Money,
    val accountId: Long,
    val toAccountId: Long?,
    /** Null for a transfer, or for an income/expense row with no category cell resolved yet. */
    val categoryTopLevelName: String?,
    val categorySubName: String?,
    val date: LocalDate,
    val timeMinutesSinceMidnight: Int,
    val payee: String?,
    val notes: String?,
    val paymentMethod: String?,
    val tagNames: List<String>
)

/** The result of parsing and validating a whole CSV file, before anything is written to the database. */
data class ImportPreview(
    val dataRowCount: Int,
    val rows: List<PendingImportRow>,
    val errors: List<ImportIssue>,
    val warnings: List<ImportIssue>,
    /** Category names (formatted "Type: Top level > Sub") that don't exist yet and will be created on import. */
    val newCategoryLabels: Set<String>
) {
    val readyToImportCount: Int get() = rows.size
}

/** What actually happened after the user confirmed and the batch was written. */
data class ImportOutcome(val importedCount: Int, val createdCategoryCount: Int)
