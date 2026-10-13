package com.moneta.app.data.export

import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Writes [TransactionExportRow]s as CSV. Handles quoting/escaping properly
 * (commas, quotes, newlines in notes/payee) rather than naive string joining,
 * since a naive join would silently corrupt rows with a comma in a note.
 */
object CsvExporter {

    private val HEADER = listOf("Date", "Type", "Category", "Account", "Amount", "Payee", "Notes", "Reference")

    fun write(outputStream: OutputStream, rows: List<TransactionExportRow>) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writer.appendLine(HEADER.joinToString(",") { it.csvEscaped() })
            rows.forEach { row ->
                val line = listOf(
                    row.date.toString(),
                    row.type,
                    row.category,
                    row.account,
                    row.amountFormatted,
                    row.payee,
                    row.notes,
                    row.reference.toString()
                ).joinToString(",") { it.csvEscaped() }
                writer.appendLine(line)
            }
        }
    }

    /** Quotes a field if it contains a comma, quote, or newline; doubles any embedded quotes (RFC 4180). */
    private fun String.csvEscaped(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}
