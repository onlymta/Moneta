package com.moneta.app.data.csvimport

/**
 * A minimal, dependency-free RFC 4180 CSV reader (the mirror of
 * [com.moneta.app.data.export.CsvExporter]'s writer). Handles quoted fields
 * containing commas, quotes, or newlines correctly - a naive `line.split(",")`
 * would silently corrupt any row where a Notes/Payee cell someone typed in
 * Excel happens to contain a comma, which is exactly the kind of file a
 * "bring my data in from another app" import needs to survive.
 */
object CsvTable {

    /** Parses [text] into rows of raw string cells. Blank trailing lines are dropped. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val n = text.length

        fun endField() {
            row.add(field.toString())
            field = StringBuilder()
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < n) {
            val ch = text[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < n && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\n' -> endRow()
                    '\r' -> if (i + 1 >= n || text[i + 1] != '\n') endRow()
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows.filterNot { r -> r.isEmpty() || (r.size == 1 && r[0].isBlank()) }
    }

    /** Quotes a field only if needed, doubling any embedded quotes (RFC 4180) - shared with the CSV exporter's own escaping rule. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
