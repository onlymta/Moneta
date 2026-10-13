package com.moneta.app.data.csvimport

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the first sheet of an .xlsx workbook into rows of text cells, with no
 * extra library: an .xlsx file is a zip of XML files. Numbers come back as
 * their raw text, so an Excel date arrives as a serial number (for example
 * 45672) and a time as a fraction of a day - [TransactionImportParser]
 * understands both.
 */
object XlsxReader {

    fun read(bytes: ByteArray): List<List<String>> {
        var sharedBytes: ByteArray? = null
        val sheets = sortedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedBytes = zip.readBytes()
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheets[name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val sheetBytes = sheets["xl/worksheets/sheet1.xml"] ?: sheets.values.firstOrNull()
            ?: error("No worksheet found in this Excel file.")
        val shared = sharedBytes?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(sheetBytes, shared)
    }

    private fun newParser(data: ByteArray): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(data), "UTF-8")
        return parser
    }

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val parser = newParser(data)
        val result = mutableListOf<String>()
        var current: StringBuilder? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (parser.name == "si") {
                    current = StringBuilder()
                } else if (parser.name == "t") {
                    val text = parser.nextText()
                    current?.append(text)
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "si") {
                result.add(current?.toString().orEmpty())
                current = null
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<List<String>> {
        val parser = newParser(data)
        val rows = mutableListOf<List<String>>()
        var row: MutableList<String>? = null
        var colIndex = 0
        var cellType = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "row" -> row = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        colIndex = columnIndexOf(parser.getAttributeValue(null, "r").orEmpty())
                        val current = row
                        if (current != null) {
                            while (current.size < colIndex) current.add("")
                        }
                    }
                    "v" -> {
                        val raw = parser.nextText()
                        val value = if (cellType == "s") shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty() else raw
                        setCell(row, colIndex, value)
                    }
                    "t" -> {
                        // Inline string cell (<c t="inlineStr"><is><t>..</t></is></c>)
                        if (cellType == "inlineStr") setCell(row, colIndex, parser.nextText())
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "row") {
                row?.let { rows.add(it) }
                row = null
            }
            event = parser.next()
        }
        return rows
    }

    private fun setCell(row: MutableList<String>?, index: Int, value: String) {
        if (row == null) return
        while (row.size <= index) row.add("")
        row[index] = value
    }

    /** "B7" -> 1, "AA3" -> 26 (zero-based column). Returns 0 if the reference is missing. */
    private fun columnIndexOf(reference: String): Int {
        var result = 0
        var seen = false
        for (ch in reference) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
                seen = true
            } else if (ch in 'a'..'z') {
                result = result * 26 + (ch - 'a' + 1)
                seen = true
            } else {
                break
            }
        }
        return if (seen) result - 1 else 0
    }
}
