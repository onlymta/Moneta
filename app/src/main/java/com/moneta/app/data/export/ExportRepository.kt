package com.moneta.app.data.export

import com.moneta.app.core.date.DateRange
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_CURRENCY = "PKR"

@Singleton
class ExportRepository @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val rowMapper: ExportRowMapper
) {
    suspend fun exportCsv(range: DateRange, outputStream: OutputStream) {
        val rows = rowMapper.map(transactionRepository.observeFiltered(range = range).first())
        CsvExporter.write(outputStream, rows)
    }

    suspend fun exportPdf(range: DateRange, reportTitle: String, periodLabel: String, outputStream: OutputStream) {
        val transactions = transactionRepository.observeFiltered(range = range).first()
        val rows = rowMapper.map(transactions)
        val income = transactionRepository.observeTotalForTypeInRange(TransactionType.INCOME, range, DEFAULT_CURRENCY).first()
        val expense = transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, range, DEFAULT_CURRENCY).first()
        PdfExporter.write(
            outputStream = outputStream,
            reportTitle = reportTitle,
            periodLabel = periodLabel,
            rows = rows,
            totalIncomeFormatted = income.formatted(),
            totalExpenseFormatted = expense.formatted()
        )
    }
}
