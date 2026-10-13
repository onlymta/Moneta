package com.moneta.app.data.csvimport

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.Category
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Turns raw CSV text into an [ImportPreview] - a pure, read-only pass (no
 * database writes, no injected dependencies) so the whole file can be
 * validated and shown to the user as "N ready, M with problems" before they
 * commit to anything (Section 22/31: never surprise the user with a
 * partially-applied bulk change).
 */
object TransactionImportParser {

    fun parse(
        csvText: String,
        existingAccounts: List<Account>,
        existingCategories: List<Category>,
        defaultCurrencyCode: String
    ): ImportPreview {
        val table = CsvTable.parse(csvText)
        if (table.isEmpty()) {
            return ImportPreview(0, emptyList(), listOf(ImportIssue(1, "The file is empty.")), emptyList(), emptySet())
        }

        val headerIndex = table.first()
            .mapIndexed { index, name -> name.trim().lowercase() to index }
            .toMap()

        fun columnIndex(name: String): Int? = headerIndex[name.lowercase()]

        val requiredColumns = listOf(
            TransactionCsvTemplate.COL_DATE, TransactionCsvTemplate.COL_TYPE,
            TransactionCsvTemplate.COL_AMOUNT, TransactionCsvTemplate.COL_ACCOUNT
        )
        val missingColumns = requiredColumns.filter { columnIndex(it) == null }
        if (missingColumns.isNotEmpty()) {
            val message = "Missing required column(s): ${missingColumns.joinToString(", ")}. " +
                "Download the template to see the expected headers."
            return ImportPreview(0, emptyList(), listOf(ImportIssue(1, message)), emptyList(), emptySet())
        }

        fun cell(row: List<String>, name: String): String =
            columnIndex(name)?.let { row.getOrNull(it) }?.trim().orEmpty()

        val accountsByName = existingAccounts.associateBy { it.name.trim().lowercase() }
        val topLevelByKey = existingCategories.filter { it.parentCategoryId == null }
            .associateBy { it.type to it.name.trim().lowercase() }
        val subByKey = existingCategories.filter { it.parentCategoryId != null }
            .associateBy { Triple(it.type, it.parentCategoryId, it.name.trim().lowercase()) }
        // For a sub-category name check we only need to know the parent's id, which
        // may itself be a category this same file is about to create - resolved by
        // name below rather than requiring the parent to already exist in the DB.
        val topLevelIdByKey = topLevelByKey.mapValues { it.value.id }

        val dataRows = table.drop(1)
        val pending = mutableListOf<PendingImportRow>()
        val errors = mutableListOf<ImportIssue>()
        val warnings = mutableListOf<ImportIssue>()
        val newCategoryLabels = mutableSetOf<String>()

        dataRows.forEachIndexed { dataIndex, row ->
            val rowNumber = dataIndex + 2 // +1 for the header row, +1 to become 1-indexed
            if (row.all { it.isBlank() }) return@forEachIndexed

            val rowErrors = mutableListOf<String>()

            val dateStr = cell(row, TransactionCsvTemplate.COL_DATE)
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: DateTimeParseException) {
                rowErrors += "Invalid or missing date (use YYYY-MM-DD): '$dateStr'"
                null
            }

            val typeStr = cell(row, TransactionCsvTemplate.COL_TYPE)
            val type = when (typeStr.trim().lowercase()) {
                "income" -> TransactionType.INCOME
                "expense" -> TransactionType.EXPENSE
                "transfer" -> TransactionType.TRANSFER
                else -> {
                    rowErrors += "Type must be Income, Expense, or Transfer (got '$typeStr')"
                    null
                }
            }

            val amountStr = cell(row, TransactionCsvTemplate.COL_AMOUNT)
            // The sign is taken from the Type column, not the amount, so a bank-style
            // "-450" for an expense is accepted the same as a plain "450".
            val amount = Money.fromDecimalInput(amountStr.removePrefix("-"), defaultCurrencyCode)
            if (amount == null || amount.isZero) {
                rowErrors += "Invalid or missing amount: '$amountStr'"
            }

            val accountStr = cell(row, TransactionCsvTemplate.COL_ACCOUNT)
            val account = accountsByName[accountStr.lowercase()]
            if (account == null) {
                rowErrors += "Account '$accountStr' doesn't exist yet - add it under More > Accounts, then re-import."
            }

            var toAccountId: Long? = null
            var categoryTop: String? = null
            var categorySub: String? = null

            val toAccountStr = cell(row, TransactionCsvTemplate.COL_TO_ACCOUNT)
            val categoryStr = cell(row, TransactionCsvTemplate.COL_CATEGORY)
            val subcategoryStr = cell(row, TransactionCsvTemplate.COL_SUBCATEGORY)

            if (type == TransactionType.TRANSFER) {
                if (toAccountStr.isBlank()) {
                    rowErrors += "Transfer rows need a 'To Account'."
                } else {
                    val toAccount = accountsByName[toAccountStr.lowercase()]
                    if (toAccount == null) {
                        rowErrors += "To Account '$toAccountStr' doesn't exist yet - add it under More > Accounts, then re-import."
                    } else if (account != null && toAccount.id == account.id) {
                        rowErrors += "Account and To Account must be different for a transfer."
                    } else {
                        toAccountId = toAccount.id
                    }
                }
                if (categoryStr.isNotBlank() || subcategoryStr.isNotBlank()) {
                    warnings += ImportIssue(rowNumber, "Category/Subcategory ignored - transfers don't have a category.")
                }
            } else {
                if (toAccountStr.isNotBlank()) {
                    warnings += ImportIssue(rowNumber, "'To Account' ignored - it only applies to transfers.")
                }
                if (categoryStr.isBlank()) {
                    rowErrors += "Category is required for Income/Expense rows."
                } else {
                    categoryTop = categoryStr
                    categorySub = subcategoryStr.ifBlank { null }
                }
            }

            val timeStr = cell(row, TransactionCsvTemplate.COL_TIME)
            val timeMinutes = if (timeStr.isBlank()) {
                720 // noon - a neutral default for historical rows with no recorded time of day.
            } else {
                try {
                    LocalTime.parse(timeStr).let { it.hour * 60 + it.minute }
                } catch (e: DateTimeParseException) {
                    warnings += ImportIssue(rowNumber, "Invalid time '$timeStr' (use HH:mm) - defaulted to 12:00.")
                    720
                }
            }

            if (rowErrors.isNotEmpty()) {
                rowErrors.forEach { errors += ImportIssue(rowNumber, it) }
                return@forEachIndexed
            }

            val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            if (categoryTop != null) {
                val typeLabel = if (categoryType == CategoryType.INCOME) "Income" else "Expense"
                val topKey = categoryType to categoryTop.trim().lowercase()
                val topExists = topLevelIdByKey.containsKey(topKey)
                if (!topExists) {
                    newCategoryLabels += "$typeLabel: ${categoryTop.trim()}"
                }
                if (categorySub != null) {
                    val parentId = topLevelIdByKey[topKey]
                    val subExists = parentId != null &&
                        subByKey.containsKey(Triple(categoryType, parentId, categorySub.trim().lowercase()))
                    if (!subExists) {
                        newCategoryLabels += "$typeLabel: ${categoryTop.trim()} > ${categorySub.trim()}"
                    }
                }
            }

            pending += PendingImportRow(
                rowNumber = rowNumber,
                type = requireNotNull(type),
                amount = requireNotNull(amount),
                accountId = requireNotNull(account).id,
                toAccountId = toAccountId,
                categoryTopLevelName = categoryTop,
                categorySubName = categorySub,
                date = requireNotNull(date),
                timeMinutesSinceMidnight = timeMinutes,
                payee = cell(row, TransactionCsvTemplate.COL_PAYEE).ifBlank { null },
                notes = cell(row, TransactionCsvTemplate.COL_NOTES).ifBlank { null },
                paymentMethod = cell(row, TransactionCsvTemplate.COL_PAYMENT_METHOD).ifBlank { null },
                tagNames = cell(row, TransactionCsvTemplate.COL_TAGS)
                    .split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
            )
        }

        return ImportPreview(
            dataRowCount = dataRows.count { row -> !row.all { it.isBlank() } },
            rows = pending,
            errors = errors,
            warnings = warnings,
            newCategoryLabels = newCategoryLabels
        )
    }
}
