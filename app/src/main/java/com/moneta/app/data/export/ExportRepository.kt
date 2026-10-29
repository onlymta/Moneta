package com.moneta.app.data.export

import com.moneta.app.core.date.DateRange
import com.moneta.app.data.csvimport.XlsxSheet
import com.moneta.app.data.csvimport.XlsxWriter
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Who the export is for and what it covers; printed at the top of the Excel and PDF files. */
data class ExportInfo(
    val userName: String,
    val periodLabel: String,
    val currencyCode: String
)

@Singleton
class ExportRepository @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val rowMapper: ExportRowMapper
) {
    suspend fun exportCsv(range: DateRange, outputStream: OutputStream) {
        val rows = rowMapper.map(transactionRepository.observeFiltered(range = range).first())
        CsvExporter.write(outputStream, rows)
    }

    suspend fun exportPdf(range: DateRange, info: ExportInfo, outputStream: OutputStream) {
        val transactions = transactionRepository.observeFiltered(range = range).first()
        val rows = rowMapper.map(transactions)
        val income = transactionRepository.observeTotalForTypeInRange(TransactionType.INCOME, range, info.currencyCode).first()
        val expense = transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, range, info.currencyCode).first()
        PdfExporter.write(
            outputStream = outputStream,
            userName = info.userName,
            periodLabel = info.periodLabel,
            rows = rows,
            totalIncomeFormatted = income.formatted(),
            totalExpenseFormatted = expense.formatted(),
            balanceFormatted = (income - expense).formatted()
        )
    }

    suspend fun exportXlsx(range: DateRange, info: ExportInfo, outputStream: OutputStream) {
        val transactions = transactionRepository.observeFiltered(range = range).first()
        val rows = rowMapper.map(transactions)

        fun plain(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
        val incomeMinor = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minorUnits }
        val expenseMinor = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }

        val sheetRows = ArrayList<List<String>>()
        val titleRows = HashSet<Int>()
        val boldRows = HashSet<Int>()
        sheetRows += listOf("Paisabook  ·  Transaction report")
        titleRows += 0
        sheetRows += listOf("Prepared for", info.userName.ifBlank { "-" })
        sheetRows += listOf("Period", info.periodLabel)
        sheetRows += listOf("Currency", info.currencyCode)
        sheetRows += listOf("Generated", LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")))
        sheetRows += listOf("")
        boldRows += sheetRows.size
        sheetRows += listOf("Total income", "", "", "", plain(incomeMinor))
        boldRows += sheetRows.size
        sheetRows += listOf("Total expenses", "", "", "", plain(expenseMinor))
        boldRows += sheetRows.size
        sheetRows += listOf("Balance", "", "", "", plain(incomeMinor - expenseMinor))
        sheetRows += listOf("")
        val headerIndex = sheetRows.size
        sheetRows += listOf("Date", "Type", "Category", "Account", "Amount", "Payee", "Notes")
        rows.forEach { r ->
            sheetRows += listOf(r.date.toString(), r.type, r.category, r.account, r.amountPlain, r.payee, r.notes)
        }

        // Summary tab: expenses by category.
        val byCategory = rows.filter { it.type == "Expense" }
            .groupBy { it.category.ifBlank { "Uncategorized" } }
            .map { (name, list) -> name to list.sumOf { BigDecimal(it.amountPlain) } }
            .sortedByDescending { it.second }
        val summaryRows = ArrayList<List<String>>()
        summaryRows += listOf("Category", "Spent")
        byCategory.forEach { summaryRows += listOf(it.first, it.second.toPlainString()) }

        XlsxWriter.write(
            outputStream,
            listOf(
                XlsxSheet(
                    name = "Transactions",
                    rows = sheetRows,
                    numberColumns = setOf(4),
                    headerRowIndexes = setOf(headerIndex),
                    boldRows = boldRows,
                    titleRows = titleRows,
                    columnWidths = listOf(16, 12, 22, 20, 14, 20, 34)
                ),
                XlsxSheet(
                    name = "By category",
                    rows = summaryRows,
                    numberColumns = setOf(1),
                    columnWidths = listOf(28, 16)
                )
            )
        )
    }
}
