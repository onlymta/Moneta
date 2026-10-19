package com.moneta.app.data.export

import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.Transaction
import com.moneta.app.data.repository.TransactionRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * One human-readable export row (Section 22: "do not create a useless raw
 * database dump" - names are resolved here, not raw foreign-key ids).
 */
data class TransactionExportRow(
    val date: LocalDate,
    val type: String,
    val category: String,
    val account: String,
    val amountFormatted: String,
    /** The amount as a plain decimal like 1250.50, for spreadsheets. */
    val amountPlain: String,
    val payee: String,
    val notes: String,
    val reference: Long
)

/**
 * Turns raw [Transaction]s into [TransactionExportRow]s. Both CsvExporter and
 * PdfExporter consume the same rows from here, so a CSV and a PDF for the
 * same period can never show different data (Section 22/17's "single
 * calculation" principle applied to exports).
 */
class ExportRowMapper @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend fun map(transactions: List<Transaction>): List<TransactionExportRow> {
        // Resolve names once per distinct id rather than once per row.
        val accountNames = mutableMapOf<Long, String>()
        val categoryNames = mutableMapOf<Long, String>()

        return transactions.map { transaction ->
            val accountName = accountNames.getOrPut(transaction.accountId) {
                accountRepository.getById(transaction.accountId)?.name ?: "Unknown account"
            }
            val categoryName = transaction.categoryId?.let { id ->
                categoryNames.getOrPut(id) { categoryRepository.getById(id)?.name ?: "Unknown category" }
            } ?: if (transaction.toAccountId != null) {
                "Transfer to " + (accountNames.getOrPut(transaction.toAccountId) {
                    accountRepository.getById(transaction.toAccountId)?.name ?: "Unknown account"
                })
            } else {
                ""
            }

            TransactionExportRow(
                date = transaction.date,
                type = transaction.type.name.lowercase().replaceFirstChar { it.uppercase() },
                category = categoryName,
                account = accountName,
                amountFormatted = transaction.amount.formatted(),
                amountPlain = java.math.BigDecimal.valueOf(transaction.amount.minorUnits, 2).toPlainString(),
                payee = transaction.payee.orEmpty(),
                notes = transaction.notes.orEmpty(),
                reference = transaction.id
            )
        }
    }
}
