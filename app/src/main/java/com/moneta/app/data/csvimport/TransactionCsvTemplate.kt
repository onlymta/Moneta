package com.moneta.app.data.csvimport

import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * The single source of truth for the import CSV's column contract - both the
 * downloadable template and [TransactionImportParser] read column names from
 * here, so the two can never drift apart.
 *
 * Columns are matched by header name (case-insensitive), not position, so
 * reordering them in Excel doesn't break the import.
 */
object TransactionCsvTemplate {
    const val COL_DATE = "Date"
    const val COL_TYPE = "Type"
    const val COL_AMOUNT = "Amount"
    const val COL_ACCOUNT = "Account"
    const val COL_TO_ACCOUNT = "To Account"
    const val COL_CATEGORY = "Category"
    const val COL_SUBCATEGORY = "Subcategory"
    const val COL_PAYEE = "Payee"
    const val COL_NOTES = "Notes"
    const val COL_PAYMENT_METHOD = "Payment Method"
    const val COL_TAGS = "Tags"
    const val COL_TIME = "Time"

    val HEADER = listOf(
        COL_DATE, COL_TYPE, COL_AMOUNT, COL_ACCOUNT, COL_TO_ACCOUNT,
        COL_CATEGORY, COL_SUBCATEGORY, COL_PAYEE, COL_NOTES,
        COL_PAYMENT_METHOD, COL_TAGS, COL_TIME
    )

    /** Sample rows shipped in the template so the format is shown by example, not just described. */
    val EXAMPLE_ROWS = listOf(
        listOf("2026-01-15", "Expense", "450", "Cash", "", "Food", "Groceries", "Al-Fatah", "Weekly shop", "Cash", "essentials", ""),
        listOf("2026-01-16", "Income", "85000", "Bank", "", "Salary", "", "", "January salary", "", "", "09:00"),
        listOf("2026-01-18", "Transfer", "5000", "Bank", "Cash", "", "", "", "ATM withdrawal", "", "", "")
    )

    /**
     * Writes the downloadable template: header row + a few example rows the user
     * overwrites/extends with their own data. "Account" and "To Account" values
     * must match account names that already exist in Moneta (create them under
     * More -> Accounts first); "Category"/"Subcategory" values that don't exist
     * yet are created automatically on import.
     */
    fun write(outputStream: OutputStream) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writer.appendLine(HEADER.joinToString(",") { CsvTable.escape(it) })
            EXAMPLE_ROWS.forEach { row ->
                writer.appendLine(row.joinToString(",") { CsvTable.escape(it) })
            }
        }
    }

    /** The downloadable Excel template: a Transactions sheet with examples and a short Guide sheet. */
    fun writeXlsx(outputStream: OutputStream) {
        val transactions = XlsxSheet(
            name = "Transactions",
            rows = listOf(HEADER) + EXAMPLE_ROWS,
            numberColumns = setOf(2),
            columnWidths = listOf(13, 11, 12, 16, 16, 16, 16, 16, 24, 15, 14, 8)
        )
        val guide = XlsxSheet(
            name = "Guide",
            rows = listOf(
                listOf("Column", "What to enter"),
                listOf("Date", "Required. Like 2026-01-15 or 15/01/2026."),
                listOf("Amount", "Required. A positive number. Negative also works for expenses."),
                listOf("Type", "Expense, Income or Transfer. Optional."),
                listOf("Account", "Account name. Created if it does not exist."),
                listOf("To Account", "Only for transfers."),
                listOf("Category / Subcategory", "Created if missing. Optional."),
                listOf("Payee, Notes, Payment Method, Tags", "Optional. Separate several tags with ;"),
                listOf("Time", "Optional, like 14:30."),
                listOf("", ""),
                listOf("Tip", "Delete the example rows, then add yours. Your own column names also work: Moneta lets you match them.")
            ),
            columnWidths = listOf(34, 80)
        )
        XlsxWriter.write(outputStream, listOf(transactions, guide))
    }
}
