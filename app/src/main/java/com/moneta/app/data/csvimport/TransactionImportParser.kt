package com.moneta.app.data.csvimport

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.Category
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private const val DEFAULT_TIME_MINUTES = 720 // noon, for rows with no usable time
private const val UNCATEGORIZED = "Uncategorized"

/**
 * Turns a spreadsheet (already split into rows of text cells) into an
 * [ImportPreview] - a pure, read-only pass. It is deliberately forgiving:
 * messy dates, times and amounts are repaired, unknown accounts and categories
 * are planned for automatic creation, and only a row that truly cannot be
 * understood (no usable date or amount, or a transfer with no destination) is
 * skipped. Nothing is written until the user confirms the preview.
 */
object TransactionImportParser {

    fun parse(
        csvText: String,
        existingAccounts: List<Account>,
        existingCategories: List<Category>,
        defaultCurrencyCode: String
    ): ImportPreview {
        val clean = csvText.removePrefix("﻿")
        val table = CsvTable.parse(clean, CsvTable.detectDelimiter(clean))
        return parseTable(table, existingAccounts, existingCategories, defaultCurrencyCode)
    }

    // ---- column matching -------------------------------------------------------------

    private val ALIASES: Map<String, List<String>> = mapOf(
        TransactionCsvTemplate.COL_DATE to listOf("date", "transactiondate", "txndate", "datetime", "day", "when"),
        TransactionCsvTemplate.COL_TYPE to listOf("type", "transactiontype", "txntype", "kind"),
        TransactionCsvTemplate.COL_AMOUNT to listOf("amount", "amt", "value", "sum", "total"),
        TransactionCsvTemplate.COL_ACCOUNT to listOf("account", "accountname", "wallet", "fromaccount", "from", "source"),
        TransactionCsvTemplate.COL_TO_ACCOUNT to listOf("toaccount", "to", "transferto", "destination", "destinationaccount"),
        TransactionCsvTemplate.COL_CATEGORY to listOf("category", "categoryname", "cat", "head", "group"),
        TransactionCsvTemplate.COL_SUBCATEGORY to listOf("subcategory", "subcat", "sub"),
        TransactionCsvTemplate.COL_PAYEE to listOf("payee", "merchant", "party", "vendor", "shop"),
        TransactionCsvTemplate.COL_NOTES to listOf("notes", "note", "memo", "remarks", "description", "details"),
        TransactionCsvTemplate.COL_PAYMENT_METHOD to listOf("paymentmethod", "method", "mode", "paymentmode"),
        TransactionCsvTemplate.COL_TAGS to listOf("tags", "tag", "labels"),
        TransactionCsvTemplate.COL_TIME to listOf("time", "timeofday")
    )

    /** The fields a file can provide, in the order they are shown when matching columns. */
    val FIELDS: List<String> = listOf(
        TransactionCsvTemplate.COL_DATE,
        TransactionCsvTemplate.COL_AMOUNT,
        TransactionCsvTemplate.COL_TYPE,
        TransactionCsvTemplate.COL_ACCOUNT,
        TransactionCsvTemplate.COL_TO_ACCOUNT,
        TransactionCsvTemplate.COL_CATEGORY,
        TransactionCsvTemplate.COL_SUBCATEGORY,
        TransactionCsvTemplate.COL_PAYEE,
        TransactionCsvTemplate.COL_NOTES,
        TransactionCsvTemplate.COL_PAYMENT_METHOD,
        TransactionCsvTemplate.COL_TAGS,
        TransactionCsvTemplate.COL_TIME
    )

    val REQUIRED_FIELDS: Set<String> = setOf(TransactionCsvTemplate.COL_DATE, TransactionCsvTemplate.COL_AMOUNT)

    /** Which row holds the column headings: the first of the top rows that has both a date and an amount heading. */
    fun detectHeaderRow(table: List<List<String>>): Int {
        for (i in 0 until minOf(table.size, 15)) {
            val keys = table[i].map { headerKey(it) }.toSet()
            val hasDate = ALIASES[TransactionCsvTemplate.COL_DATE].orEmpty().any { it in keys }
            val hasAmount = ALIASES[TransactionCsvTemplate.COL_AMOUNT].orEmpty().any { it in keys }
            if (hasDate && hasAmount) return i
        }
        return 0
    }

    /**
     * Matches the file's headings to Moneta's fields by name (ignoring case, spaces and punctuation).
     * The result maps a field to the zero-based column that holds it; fields with no match are absent.
     */
    fun suggestMapping(header: List<String>): Map<String, Int> {
        val headerIndex = HashMap<String, Int>()
        header.forEachIndexed { index, name ->
            val key = headerKey(name)
            if (key.isNotEmpty() && !headerIndex.containsKey(key)) headerIndex[key] = index
        }
        val result = LinkedHashMap<String, Int>()
        val used = HashSet<Int>()
        FIELDS.forEach { field ->
            for (alias in ALIASES[field].orEmpty()) {
                val idx = headerIndex[alias]
                if (idx != null && idx !in used) {
                    result[field] = idx
                    used += idx
                    break
                }
            }
        }
        return result
    }

    /** Two transactions with the same key are treated as the same transaction already in the app. */
    fun duplicateKey(
        date: LocalDate,
        amountMinor: Long,
        type: TransactionType,
        accountName: String,
        toAccountName: String?
    ): String =
        date.toString() + "|" + amountMinor + "|" + type.name + "|" + normalizeImportName(accountName) +
            "|" + normalizeImportName(toAccountName.orEmpty())

    private fun headerKey(raw: String): String =
        raw.removePrefix("﻿").lowercase().filter { it.isLetterOrDigit() }

    // ---- date / time / amount repair ------------------------------------------------

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val RE_YMD = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val RE_DMY = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})""")
    private val RE_D_MON_Y = Regex("""^(\d{1,2})[\s-]+([A-Za-z]{3,9})[\s,.-]+(\d{2,4})""")
    private val RE_MON_D_Y = Regex("""^([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})""")
    private val RE_SERIAL = Regex("""^(\d{5})(\.\d+)?$""")
    private val RE_TIME = Regex("""^(\d{1,2})[:.](\d{2})(?:[:.]\d{1,2})?\s*([AaPp])?\.?[Mm]?\.?$""")

    private class ParsedDate(val date: LocalDate, val rest: String)

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        try {
            LocalDate.of(y, m, d)
        } catch (e: Exception) {
            null
        }

    private fun fullYear(y: Int): Int = if (y < 100) 2000 + y else y

    private fun monthNumber(name: String): Int? {
        val idx = MONTHS.indexOf(name.lowercase().take(3))
        return if (idx >= 0) idx + 1 else null
    }

    private fun parseDateCell(raw: String, dayFirst: Boolean): ParsedDate? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        RE_YMD.find(s)?.let { m ->
            val date = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            if (date != null) return ParsedDate(date, s.substring(m.range.last + 1).trim().trimStart('T', ' '))
        }
        RE_DMY.find(s)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val y = fullYear(m.groupValues[3].toInt())
            val date = if (dayFirst) safeDate(y, b, a) ?: safeDate(y, a, b) else safeDate(y, a, b) ?: safeDate(y, b, a)
            if (date != null) return ParsedDate(date, s.substring(m.range.last + 1).trim().trimStart('T', ' '))
        }
        RE_D_MON_Y.find(s)?.let { m ->
            val month = monthNumber(m.groupValues[2])
            if (month != null) {
                val date = safeDate(fullYear(m.groupValues[3].toInt()), month, m.groupValues[1].toInt())
                if (date != null) return ParsedDate(date, s.substring(m.range.last + 1).trim())
            }
        }
        RE_MON_D_Y.find(s)?.let { m ->
            val month = monthNumber(m.groupValues[1])
            if (month != null) {
                val date = safeDate(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())
                if (date != null) return ParsedDate(date, s.substring(m.range.last + 1).trim())
            }
        }
        RE_SERIAL.find(s)?.let { m ->
            val serial = m.groupValues[1].toInt()
            if (serial in 20000..80000) {
                val date = LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
                val fraction = m.groupValues[2]
                return ParsedDate(date, if (fraction.isEmpty()) "" else "0$fraction")
            }
        }
        return null
    }

    /** Returns minutes since midnight, or null when the text isn't a recognisable time. */
    private fun parseTimeText(raw: String): Int? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val looksLikeDayFraction = s.startsWith("0.") && s.all { it.isDigit() || it == '.' }
        if (!looksLikeDayFraction) RE_TIME.find(s)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            val marker = m.groupValues[3].lowercase()
            if (marker == "p" && hour < 12) hour += 12
            if (marker == "a" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) return hour * 60 + minute
            return null
        }
        val fraction = s.toDoubleOrNull()
        if (fraction != null && fraction >= 0.0 && fraction < 1.0) {
            return Math.round(fraction * 1440.0).toInt().coerceIn(0, 1439)
        }
        return null
    }

    private class ParsedAmount(val money: Money, val negative: Boolean)

    private val RE_PLAIN_NUMBER = Regex("""^-?\d+(\.\d+)?([eE][+-]?\d+)?$""")

    private fun parseAmountCell(raw: String, currencyCode: String): ParsedAmount? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val negative = trimmed.contains('-') || trimmed.contains('(')
        val decimal: BigDecimal = try {
            if (RE_PLAIN_NUMBER.matches(trimmed)) {
                BigDecimal(trimmed)
            } else {
                var s = trimmed.filter { it.isDigit() || it == '.' || it == ',' }.trim('.', ',')
                if (s.contains(',') && s.contains('.')) {
                    s = s.replace(",", "")
                } else if (s.contains(',')) {
                    s = if (Regex(""",\d{1,2}$""").containsMatchIn(s) && s.count { it == ',' } == 1) {
                        s.replace(',', '.')
                    } else {
                        s.replace(",", "")
                    }
                }
                if (s.isEmpty()) return null
                BigDecimal(s)
            }
        } catch (e: NumberFormatException) {
            return null
        }
        val money = Money.fromDecimalInput(
            decimal.abs().setScale(2, RoundingMode.HALF_UP).toPlainString(),
            currencyCode
        ) ?: return null
        if (money.isZero) return null
        return ParsedAmount(money, negative)
    }

    private fun parseTypeCell(raw: String): TransactionType? = when (raw.trim().lowercase()) {
        "income", "credit", "cr", "deposit", "in", "salary", "received", "+" -> TransactionType.INCOME
        "expense", "debit", "dr", "withdrawal", "out", "spend", "spent", "payment", "paid", "-" -> TransactionType.EXPENSE
        "transfer", "xfer", "move", "internal transfer" -> TransactionType.TRANSFER
        else -> null
    }

    // ---- main ------------------------------------------------------------------------

    fun parseTable(
        table: List<List<String>>,
        existingAccounts: List<Account>,
        existingCategories: List<Category>,
        defaultCurrencyCode: String,
        mapping: Map<String, Int>? = null,
        existingKeys: Map<String, Int> = emptyMap(),
        rowOffset: Int = 0
    ): ImportPreview {
        if (table.isEmpty()) {
            return ImportPreview(0, emptyList(), listOf(ImportIssue(1, "The file is empty.")), emptyList(), emptySet())
        }

        val headerIndex = HashMap<String, Int>()
        table.first().forEachIndexed { index, name ->
            val key = headerKey(name)
            if (key.isNotEmpty() && !headerIndex.containsKey(key)) headerIndex[key] = index
        }

        fun columnIndex(name: String): Int? {
            if (mapping != null) {
                val chosen = mapping[name]
                return if (chosen != null && chosen >= 0) chosen else null
            }
            for (alias in ALIASES[name].orEmpty()) {
                headerIndex[alias]?.let { return it }
            }
            return null
        }

        val missingColumns = listOf(TransactionCsvTemplate.COL_DATE, TransactionCsvTemplate.COL_AMOUNT)
            .filter { columnIndex(it) == null }
        if (missingColumns.isNotEmpty()) {
            val message = "Couldn't find the ${missingColumns.joinToString(" and ")} column. " +
                "Match it to a column of your file, or download the template to see the expected headings."
            return ImportPreview(0, emptyList(), listOf(ImportIssue(1, message)), emptyList(), emptySet())
        }

        fun cell(row: List<String>, name: String): String =
            columnIndex(name)?.let { row.getOrNull(it) }?.trim().orEmpty()

        val dataRows = table.drop(1)

        // Decide once per file whether 05/06/2026 means 5 June (day first) or May 6.
        var dayFirstEvidence = false
        var monthFirstEvidence = false
        var ambiguousCount = 0
        dataRows.forEach { row ->
            RE_DMY.find(cell(row, TransactionCsvTemplate.COL_DATE))?.let { m ->
                if (!RE_YMD.containsMatchIn(m.value)) {
                    val a = m.groupValues[1].toInt()
                    val b = m.groupValues[2].toInt()
                    if (a > 12) dayFirstEvidence = true
                    if (b > 12) monthFirstEvidence = true
                    if (a <= 12 && b <= 12 && a != b) ambiguousCount++
                }
            }
        }
        val dayFirst = !(monthFirstEvidence && !dayFirstEvidence)

        val accountNames = HashMap<String, String>() // normalized -> display
        existingAccounts.forEach { accountNames[normalizeImportName(it.name)] = it.name }
        val newAccounts = LinkedHashMap<String, NewAccountPlan>()
        val defaultAccountName = existingAccounts.firstOrNull()?.name ?: "Cash"

        fun registerAccount(rawName: String): String {
            val key = normalizeImportName(rawName)
            accountNames[key]?.let { return it }
            val display = rawName.trim().replace(Regex("\\s+"), " ")
            accountNames[key] = display
            newAccounts[key] = NewAccountPlan(display, guessAccountType(display))
            return display
        }

        val topLevelIdByKey = existingCategories.filter { it.parentCategoryId == null }
            .associate { (it.type to normalizeImportName(it.name)) to it.id }
        val subKeys = existingCategories.filter { it.parentCategoryId != null }
            .map { Triple(it.type, it.parentCategoryId, normalizeImportName(it.name)) }
            .toSet()

        val pending = mutableListOf<PendingImportRow>()
        val duplicates = mutableListOf<PendingImportRow>()
        val remainingExisting = HashMap<String, Int>(existingKeys)
        val errors = mutableListOf<ImportIssue>()
        val warnings = mutableListOf<ImportIssue>()
        val newCategoryLabels = linkedSetOf<String>()
        var guessedTypeCount = 0

        dataRows.forEachIndexed { dataIndex, row ->
            val rowNumber = dataIndex + 2 + rowOffset
            if (row.all { it.isBlank() }) return@forEachIndexed

            val rowErrors = mutableListOf<String>()
            val rowWarnings = mutableListOf<String>()

            // Date (and a time that may be riding along in the same cell)
            val dateRaw = cell(row, TransactionCsvTemplate.COL_DATE)
            val parsedDate = parseDateCell(dateRaw, dayFirst)
            if (parsedDate == null) {
                rowErrors += if (dateRaw.isBlank()) "No date." else "Couldn't understand the date '$dateRaw'."
            }

            // Amount
            val amountRaw = cell(row, TransactionCsvTemplate.COL_AMOUNT)
            val parsedAmount = parseAmountCell(amountRaw, defaultCurrencyCode)
            if (parsedAmount == null) {
                rowErrors += if (amountRaw.isBlank()) "No amount." else "Couldn't understand the amount '$amountRaw'."
            }

            // Type - repaired from the amount's sign when missing or unrecognised.
            val typeRaw = cell(row, TransactionCsvTemplate.COL_TYPE)
            var type = parseTypeCell(typeRaw)
            if (type == null && parsedAmount != null) {
                type = if (parsedAmount.negative) TransactionType.EXPENSE else TransactionType.INCOME
                guessedTypeCount++
            }

            // Accounts
            val accountRaw = cell(row, TransactionCsvTemplate.COL_ACCOUNT)
            val accountName = if (accountRaw.isBlank()) {
                rowWarnings += "No account - used '$defaultAccountName'."
                registerAccount(defaultAccountName)
            } else {
                registerAccount(accountRaw)
            }

            var toAccountName: String? = null
            var categoryTop: String? = null
            var categorySub: String? = null
            val toAccountRaw = cell(row, TransactionCsvTemplate.COL_TO_ACCOUNT)
            val categoryRaw = cell(row, TransactionCsvTemplate.COL_CATEGORY)
            val subcategoryRaw = cell(row, TransactionCsvTemplate.COL_SUBCATEGORY)

            if (type == TransactionType.TRANSFER) {
                if (toAccountRaw.isBlank()) {
                    rowErrors += "Transfer has no 'To Account'."
                } else {
                    val toName = registerAccount(toAccountRaw)
                    if (normalizeImportName(toName) == normalizeImportName(accountName)) {
                        rowErrors += "Transfer from and to the same account ('$toName')."
                    } else {
                        toAccountName = toName
                    }
                }
                if (categoryRaw.isNotBlank() || subcategoryRaw.isNotBlank()) {
                    rowWarnings += "Category ignored (transfers don't have one)."
                }
            } else if (type != null) {
                if (toAccountRaw.isNotBlank()) rowWarnings += "'To Account' ignored (only for transfers)."
                var top = categoryRaw.trim()
                var sub = subcategoryRaw.trim()
                if (sub.isEmpty() && (top.contains(">") || top.contains(":"))) {
                    val parts = top.split(">", ":").map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.size >= 2) {
                        top = parts[0]
                        sub = parts[1]
                    }
                }
                if (top.isEmpty()) {
                    top = UNCATEGORIZED
                    sub = ""
                    rowWarnings += "No category - filed under '$UNCATEGORIZED'."
                }
                categoryTop = top
                categorySub = sub.ifBlank { null }
            }

            // Time: its own column wins, otherwise whatever came with the date, otherwise noon.
            val timeRaw = cell(row, TransactionCsvTemplate.COL_TIME)
            val timeMinutes: Int = run {
                val fromColumn = if (timeRaw.isBlank()) null else parseTimeText(timeRaw)
                if (fromColumn != null) return@run fromColumn
                if (timeRaw.isNotBlank()) {
                    rowWarnings += "Time '$timeRaw' not understood - used 12:00."
                    return@run DEFAULT_TIME_MINUTES
                }
                val fromDate = parsedDate?.rest?.let { parseTimeText(it) }
                fromDate ?: DEFAULT_TIME_MINUTES
            }

            if (rowErrors.isNotEmpty() || parsedDate == null || parsedAmount == null || type == null) {
                rowErrors.forEach { errors += ImportIssue(rowNumber, it) }
                return@forEachIndexed
            }
            rowWarnings.forEach { warnings += ImportIssue(rowNumber, it) }

            val candidate = PendingImportRow(
                rowNumber = rowNumber,
                type = type,
                amount = parsedAmount.money,
                accountName = accountName,
                toAccountName = toAccountName,
                categoryTopLevelName = categoryTop,
                categorySubName = categorySub,
                date = parsedDate.date,
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

            // Already in the app? Each existing transaction can match only one row of the file.
            val dupKey = duplicateKey(parsedDate.date, parsedAmount.money.minorUnits, type, accountName, toAccountName)
            val left = remainingExisting[dupKey] ?: 0
            if (left > 0) {
                remainingExisting[dupKey] = left - 1
                duplicates += candidate
                return@forEachIndexed
            }

            if (categoryTop != null) {
                val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                val typeLabel = if (categoryType == CategoryType.INCOME) "Income" else "Expense"
                val topKey = categoryType to normalizeImportName(categoryTop)
                val parentId = topLevelIdByKey[topKey]
                if (parentId == null) newCategoryLabels += "$typeLabel: $categoryTop"
                if (categorySub != null) {
                    val subExists = parentId != null &&
                        subKeys.contains(Triple(categoryType, parentId, normalizeImportName(categorySub)))
                    if (!subExists) newCategoryLabels += "$typeLabel: $categoryTop > $categorySub"
                }
            }

            pending += candidate
        }

        // Only accounts actually used by a surviving row are really "new".
        val usedAccountKeys = HashSet<String>()
        pending.forEach { r ->
            usedAccountKeys += normalizeImportName(r.accountName)
            r.toAccountName?.let { usedAccountKeys += normalizeImportName(it) }
        }
        val plannedAccounts = newAccounts.filter { (key, _) -> key in usedAccountKeys }.values.toList()

        val notes = mutableListOf<String>()
        if (guessedTypeCount > 0) {
            notes += "$guessedTypeCount row(s) had no clear Type, so it was guessed from the amount " +
                "(negative = expense, positive = income). Check the sample rows below."
        }
        if (ambiguousCount > 0) {
            notes += if (dayFirst) {
                "Dates like 05/06/2026 were read as day/month/year (5 June)."
            } else {
                "Dates like 05/06/2026 were read as month/day/year (May 6)."
            }
        }
        if (plannedAccounts.isNotEmpty()) {
            notes += "New accounts start with a balance of 0; the imported transactions then build up their balance. " +
                "You can rename them or set their type later under More > Accounts."
        }

        return ImportPreview(
            dataRowCount = dataRows.count { row -> !row.all { it.isBlank() } },
            rows = pending,
            duplicates = duplicates,
            errors = errors,
            warnings = warnings,
            newCategoryLabels = newCategoryLabels,
            newAccounts = plannedAccounts,
            notes = notes,
            incomeMinor = pending.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minorUnits },
            expenseMinor = pending.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits },
            transferCount = pending.count { it.type == TransactionType.TRANSFER },
            currencyCode = defaultCurrencyCode,
            firstDate = pending.minOfOrNull { it.date },
            lastDate = pending.maxOfOrNull { it.date }
        )
    }
}
