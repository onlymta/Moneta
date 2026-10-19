package com.moneta.app.data.csvimport

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One worksheet: a name, its rows of text, and which columns hold numbers. */
data class XlsxSheet(
    val name: String,
    val rows: List<List<String>>,
    /** Zero-based columns whose cells are written as numbers (when the text is a number). */
    val numberColumns: Set<Int> = emptySet(),
    /** Rows (zero-based) styled as a header (dark fill, white bold text). */
    val headerRowIndexes: Set<Int> = setOf(0),
    /** Rows (zero-based) shown in bold, e.g. a totals line. */
    val boldRows: Set<Int> = emptySet(),
    /** Rows (zero-based) shown as a large title. */
    val titleRows: Set<Int> = emptySet(),
    /** Column widths in characters; missing entries use a default. */
    val columnWidths: List<Int> = emptyList()
)

/**
 * Writes a basic .xlsx workbook with no extra library (an .xlsx is a zip of XML files).
 * Text is stored inline, numbers as numbers, with a few simple styles.
 */
object XlsxWriter {

    // Style ids defined in styles.xml below.
    private const val STYLE_NORMAL = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_BOLD = 2
    private const val STYLE_TITLE = 3
    private const val STYLE_NUMBER = 4

    fun write(out: OutputStream, sheets: List<XlsxSheet>) {
        val list = if (sheets.isEmpty()) listOf(XlsxSheet("Sheet1", emptyList())) else sheets
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(list.size))
            put(zip, "_rels/.rels", ROOT_RELS)
            put(zip, "xl/workbook.xml", workbook(list))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(list.size))
            put(zip, "xl/styles.xml", STYLES)
            list.forEachIndexed { index, sheet ->
                put(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet))
            }
        }
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 32 && ch != '\n' && ch != '\t' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun sheetXml(sheet: XlsxSheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        val columnCount = sheet.rows.maxOfOrNull { it.size } ?: 0
        if (columnCount > 0) {
            sb.append("<cols>")
            for (c in 0 until columnCount) {
                val width = sheet.columnWidths.getOrNull(c) ?: 16
                sb.append("""<col min="${c + 1}" max="${c + 1}" width="$width" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            sb.append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                if (value.isNotEmpty() || r in sheet.headerRowIndexes) {
                    val ref = columnName(c) + (r + 1)
                    val style = when {
                        r in sheet.titleRows -> STYLE_TITLE
                        r in sheet.headerRowIndexes -> STYLE_HEADER
                        r in sheet.boldRows -> STYLE_BOLD
                        else -> STYLE_NORMAL
                    }
                    val number = if (c in sheet.numberColumns && r !in sheet.headerRowIndexes && r !in sheet.titleRows) value.toDoubleOrNull() else null
                    if (number != null) {
                        val numStyle = if (style == STYLE_BOLD) STYLE_BOLD else STYLE_NUMBER
                        sb.append("""<c r="$ref" s="$numStyle"><v>$value</v></c>""")
                    } else {
                        sb.append("""<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${escape(value)}</t></is></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun contentTypes(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sb.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            sb.append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun workbook(sheets: List<XlsxSheet>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            val safeName = escape(sheet.name.filter { it !in "[]:*?/\\" }.take(31).ifBlank { "Sheet${index + 1}" })
            sb.append("""<sheet name="$safeName" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRels(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            sb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        sb.append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        sb.append("</Relationships>")
        return sb.toString()
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    // 0 normal, 1 header (white bold on teal), 2 bold, 3 title (large bold), 4 number (2 decimals).
    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="4">""" +
            """<font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="16"/><color rgb="FF0F6B5C"/><name val="Calibri"/></font>""" +
            """</fonts>""" +
            """<fills count="3">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FF0F6B5C"/><bgColor indexed="64"/></patternFill></fill>""" +
            """</fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="5">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
            """<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """</cellXfs>""" +
            """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            """</styleSheet>"""
}
