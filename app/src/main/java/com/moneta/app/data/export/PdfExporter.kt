package com.moneta.app.data.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PAGE_WIDTH = 595 // A4 at 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val ROW_HEIGHT = 20f

/**
 * Renders a simple, honest transaction-report PDF: app name, reporting
 * period, generated timestamp, a summary block, and a paginated table
 * (Section 22). Deliberately plain typography over anything fancy - this is
 * a real, working export using only Android's built-in PdfDocument, not a
 * placeholder that produces an invalid file (Section 47).
 */
object PdfExporter {

    fun write(
        outputStream: OutputStream,
        reportTitle: String,
        periodLabel: String,
        rows: List<TransactionExportRow>,
        totalIncomeFormatted: String,
        totalExpenseFormatted: String
    ) {
        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD }
        val labelPaint = Paint().apply { textSize = 10f; color = 0xFF666666.toInt() }
        val bodyPaint = Paint().apply { textSize = 11f }
        val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        // Header block
        canvas.drawText("Moneta", MARGIN, y, titlePaint)
        y += 24f
        canvas.drawText(reportTitle, MARGIN, y, bodyPaint)
        y += 16f
        canvas.drawText(periodLabel, MARGIN, y, labelPaint)
        y += 14f
        canvas.drawText(
            "Generated ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
            MARGIN, y, labelPaint
        )
        y += 24f

        // Summary block
        canvas.drawText("Income: $totalIncomeFormatted", MARGIN, y, bodyPaint)
        canvas.drawText("Expense: $totalExpenseFormatted", MARGIN + 220f, y, bodyPaint)
        y += 28f

        // Table header
        drawTableRow(canvas, y, headerPaint, "Date", "Type", "Category / Account", "Amount")
        y += ROW_HEIGHT
        canvas.drawLine(MARGIN, y - 6f, PAGE_WIDTH - MARGIN, y - 6f, labelPaint)

        rows.forEach { row ->
            if (y > PAGE_HEIGHT - MARGIN - ROW_HEIGHT) {
                newPage()
                drawTableRow(canvas, y, headerPaint, "Date", "Type", "Category / Account", "Amount")
                y += ROW_HEIGHT
            }
            drawTableRow(
                canvas, y, bodyPaint,
                row.date.toString(), row.type, "${row.category} / ${row.account}", row.amountFormatted
            )
            y += ROW_HEIGHT
        }

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    private fun drawTableRow(canvas: Canvas, y: Float, paint: Paint, date: String, type: String, categoryAccount: String, amount: String) {
        canvas.drawText(date, MARGIN, y, paint)
        canvas.drawText(type, MARGIN + 80f, y, paint)
        canvas.drawText(categoryAccount.take(38), MARGIN + 150f, y, paint)
        canvas.drawText(amount, PAGE_WIDTH - MARGIN - 80f, y, paint)
    }
}
