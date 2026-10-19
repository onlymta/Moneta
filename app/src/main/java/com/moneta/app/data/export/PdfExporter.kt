package com.moneta.app.data.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PAGE_WIDTH = 595 // A4 at 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 36f
private const val ROW_HEIGHT = 20f

private val TEAL = 0xFF0F6B5C.toInt()
private val GREEN = 0xFF1B8A4B.toInt()
private val RED = 0xFFC62828.toInt()
private val GREY = 0xFF6B7280.toInt()
private val ZEBRA = 0xFFF3F6F5.toInt()

/**
 * Renders the transaction report as a PDF with Android's built-in PdfDocument: a coloured
 * title band showing who it is for and the period, three summary boxes, and a paginated,
 * aligned table (amounts right-aligned, income green, expenses red) with page numbers.
 */
object PdfExporter {

    fun write(
        outputStream: OutputStream,
        userName: String,
        periodLabel: String,
        rows: List<TransactionExportRow>,
        totalIncomeFormatted: String,
        totalExpenseFormatted: String,
        balanceFormatted: String
    ) {
        val document = PdfDocument()
        val bandPaint = Paint().apply { color = TEAL }
        val whiteTitle = Paint().apply { textSize = 22f; color = 0xFFFFFFFF.toInt(); typeface = Typeface.DEFAULT_BOLD }
        val whiteSmall = Paint().apply { textSize = 10.5f; color = 0xFFE0F2EF.toInt() }
        val labelPaint = Paint().apply { textSize = 9f; color = GREY }
        val bodyPaint = Paint().apply { textSize = 10f; color = 0xFF111827.toInt() }
        val headerPaint = Paint().apply { textSize = 10f; color = TEAL; typeface = Typeface.DEFAULT_BOLD }
        val boxPaint = Paint().apply { color = ZEBRA }
        val linePaint = Paint().apply { color = 0xFFD1D5DB.toInt(); strokeWidth = 0.8f }
        val rightBody = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightHeader = Paint(headerPaint).apply { textAlign = Paint.Align.RIGHT }
        val bigNumber = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }

        val colDate = MARGIN
        val colType = MARGIN + 66f
        val colCategory = MARGIN + 120f
        val colAccount = MARGIN + 270f
        val colAmountRight = PAGE_WIDTH - MARGIN

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 0f

        fun footer() {
            canvas.drawText("Moneta  ·  Page $pageNumber", MARGIN, PAGE_HEIGHT - 18f, labelPaint)
        }

        fun tableHeader() {
            canvas.drawText("Date", colDate, y, headerPaint)
            canvas.drawText("Type", colType, y, headerPaint)
            canvas.drawText("Category", colCategory, y, headerPaint)
            canvas.drawText("Account", colAccount, y, headerPaint)
            canvas.drawText("Amount", colAmountRight, y, rightHeader)
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 14f
        }

        fun newPage() {
            footer()
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN + 6f
            tableHeader()
        }

        // Title band
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 92f, bandPaint)
        canvas.drawText("Moneta", MARGIN, 38f, whiteTitle)
        canvas.drawText("Transaction report", MARGIN, 56f, whiteSmall)
        val who = if (userName.isBlank()) "Prepared for: -" else "Prepared for: $userName"
        canvas.drawText(who, MARGIN, 74f, whiteSmall)
        val rightWhite = Paint(whiteSmall).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Period: $periodLabel", PAGE_WIDTH - MARGIN, 56f, rightWhite)
        canvas.drawText(
            "Generated " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")),
            PAGE_WIDTH - MARGIN, 74f, rightWhite
        )

        // Summary boxes
        y = 112f
        val boxWidth = (PAGE_WIDTH - 2 * MARGIN - 20f) / 3f
        val boxes = listOf(
            Triple("Income", totalIncomeFormatted, GREEN),
            Triple("Expenses", totalExpenseFormatted, RED),
            Triple("Balance", balanceFormatted, TEAL)
        )
        boxes.forEachIndexed { i, box ->
            val left = MARGIN + i * (boxWidth + 10f)
            canvas.drawRoundRect(RectF(left, y, left + boxWidth, y + 46f), 8f, 8f, boxPaint)
            canvas.drawText(box.first, left + 10f, y + 17f, labelPaint)
            bigNumber.color = box.third
            canvas.drawText(box.second, left + 10f, y + 36f, bigNumber)
        }
        y += 46f + 16f
        canvas.drawText("${rows.size} transaction(s)", MARGIN, y, labelPaint)
        y += 20f

        tableHeader()

        rows.forEachIndexed { index, row ->
            if (y > PAGE_HEIGHT - MARGIN - ROW_HEIGHT) newPage()
            if (index % 2 == 1) {
                canvas.drawRect(MARGIN - 4f, y - 13f, PAGE_WIDTH - MARGIN + 4f, y + 6f, boxPaint)
            }
            canvas.drawText(row.date.toString(), colDate, y, bodyPaint)
            canvas.drawText(row.type, colType, y, bodyPaint)
            canvas.drawText(row.category.take(24), colCategory, y, bodyPaint)
            canvas.drawText(row.account.take(22), colAccount, y, bodyPaint)
            rightBody.color = when (row.type) {
                "Income" -> GREEN
                "Expense" -> RED
                else -> 0xFF111827.toInt()
            }
            canvas.drawText(row.amountFormatted, colAmountRight, y, rightBody)
            y += ROW_HEIGHT
        }

        footer()
        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }
}
