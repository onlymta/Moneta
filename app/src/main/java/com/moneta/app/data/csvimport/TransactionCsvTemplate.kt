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
    private val EXAMPLE_ROWS = listOf(
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
}
