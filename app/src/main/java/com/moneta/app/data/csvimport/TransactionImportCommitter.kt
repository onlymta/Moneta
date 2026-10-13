package com.moneta.app.data.csvimport

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionDraft
import com.moneta.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Neutral defaults for a category the import creates on the fly - the user can restyle it afterwards under Categories. */
private const val IMPORTED_CATEGORY_ICON = "category"
private const val IMPORTED_CATEGORY_COLOR = "#5B6B76"

/**
 * Turns a validated [ImportPreview]'s rows into real transactions.
 *
 * Category names are resolved here rather than in [TransactionImportParser]
 * so a name repeated across hundreds of rows is only ever created once. This
 * step (creating missing categories) runs as its own set of inserts before
 * the transactions themselves are written in one batch via
 * [TransactionRepository.importAll]; if the batch insert fails partway, any
 * categories already created are simply left in place (harmless - they just
 * show up as unused categories) rather than the whole import needing to be
 * one giant cross-repository transaction.
 */
@Singleton
class TransactionImportCommitter @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend fun commit(rows: List<PendingImportRow>): ImportOutcome {
        val currencyCode = rows.first().amount.currencyCode

        // Accounts named in the file that don't exist yet are created here (balance 0).
        val accountIds = accountRepository.observeActiveAccounts().first()
            .associate { normalizeImportName(it.name) to it.id }
            .toMutableMap()
        var createdAccounts = 0

        suspend fun resolveAccount(name: String): Long {
            val key = normalizeImportName(name)
            accountIds[key]?.let { return it }
            val type = guessAccountType(name)
            val id = accountRepository.createAccount(
                name = name.trim(),
                type = type,
                icon = iconForAccountType(type),
                colorHex = IMPORTED_CATEGORY_COLOR,
                openingBalance = Money.zero(currencyCode)
            )
            accountIds[key] = id
            createdAccounts++
            return id
        }

        val existingCategories = categoryRepository.observeAll().first()
        val topLevelIds = existingCategories.filter { it.parentCategoryId == null }
            .associate { (it.type to normalizeImportName(it.name)) to it.id }
            .toMutableMap()
        val subIds = existingCategories.filter { it.parentCategoryId != null }
            .associate { Triple(it.type, it.parentCategoryId, normalizeImportName(it.name)) to it.id }
            .toMutableMap()

        var createdCount = 0

        suspend fun resolveTopLevel(type: CategoryType, name: String): Long {
            val key = type to normalizeImportName(name)
            topLevelIds[key]?.let { return it }
            val id = categoryRepository.createCategory(
                name = name.trim(),
                type = type,
                icon = IMPORTED_CATEGORY_ICON,
                colorHex = IMPORTED_CATEGORY_COLOR
            )
            topLevelIds[key] = id
            createdCount++
            return id
        }

        suspend fun resolveSub(type: CategoryType, parentId: Long, name: String): Long {
            val key = Triple(type, parentId, normalizeImportName(name))
            subIds[key]?.let { return it }
            val id = categoryRepository.createCategory(
                name = name.trim(),
                type = type,
                icon = IMPORTED_CATEGORY_ICON,
                colorHex = IMPORTED_CATEGORY_COLOR,
                parentCategoryId = parentId
            )
            subIds[key] = id
            createdCount++
            return id
        }

        val tagCache = mutableMapOf<String, Long>()
        suspend fun resolveTags(names: List<String>): List<Long> {
            val unresolved = names.filter { it !in tagCache }
            if (unresolved.isNotEmpty()) {
                val ids = transactionRepository.resolveOrCreateTags(unresolved)
                unresolved.zip(ids).forEach { (name, id) -> tagCache[name] = id }
            }
            return names.map { requireNotNull(tagCache[it]) }
        }

        val drafts = rows.map { row ->
            val categoryId: Long? = when {
                row.type == TransactionType.TRANSFER -> null
                row.categoryTopLevelName == null -> null
                else -> {
                    val categoryType = if (row.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                    val topId = resolveTopLevel(categoryType, row.categoryTopLevelName)
                    if (row.categorySubName != null) resolveSub(categoryType, topId, row.categorySubName) else topId
                }
            }

            TransactionDraft(
                type = row.type,
                amount = row.amount,
                accountId = resolveAccount(row.accountName),
                toAccountId = row.toAccountName?.let { resolveAccount(it) },
                categoryId = categoryId,
                date = row.date,
                timeMinutesSinceMidnight = row.timeMinutesSinceMidnight,
                payee = row.payee,
                notes = row.notes,
                paymentMethod = row.paymentMethod,
                tagIds = resolveTags(row.tagNames)
            )
        }

        val importedIds = transactionRepository.importAll(drafts)
        return ImportOutcome(
            importedCount = importedIds.size,
            createdCategoryCount = createdCount,
            createdAccountCount = createdAccounts
        )
    }

    private fun iconForAccountType(type: AccountType): String = when (type) {
        AccountType.CASH -> "payments"
        AccountType.BANK -> "account_balance"
        AccountType.CREDIT_CARD -> "credit_card"
        AccountType.SAVINGS -> "savings"
        AccountType.E_WALLET -> "account_balance_wallet"
        AccountType.OTHER -> "category"
    }
}
