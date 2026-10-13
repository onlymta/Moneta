package com.moneta.app.data.repository

import androidx.room.withTransaction
import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.MonetaDatabase
import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.local.dao.TransactionDao
import com.moneta.app.data.local.entity.TagEntity
import com.moneta.app.data.local.entity.TransactionEntity
import com.moneta.app.data.local.entity.TransactionTagCrossRef
import com.moneta.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONLY place that mutates account balances as a side effect of
 * transaction changes (ARCHITECTURE.md Section 6 "Key integrity rules" and
 * Section 32 "Data integrity"). Every insert/edit/delete/undo below applies
 * its balance delta(s) inside a single Room database transaction so a crash
 * mid-operation can never leave balances and transaction rows disagreeing.
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val database: MonetaDatabase,
    private val transactionDao: TransactionDao
) {
    private val accountDao get() = database.accountDao()
    private val tagDao get() = database.tagDao()

    fun observeRecent(limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeInRange(range: DateRange): Flow<List<Transaction>> =
        transactionDao.observeInRange(range.start.toEpochDay(), range.endExclusive.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun observeTotalForTypeInRange(type: TransactionType, range: DateRange, currencyCode: String): Flow<Money> =
        transactionDao.observeTotalForTypeInRange(type, range.start.toEpochDay(), range.endExclusive.toEpochDay())
            .map { Money(it, currencyCode) }

    fun observeTotalsByCategory(type: TransactionType, range: DateRange): Flow<List<CategoryTotal>> =
        transactionDao.observeTotalsByCategory(type, range.start.toEpochDay(), range.endExclusive.toEpochDay())

    /** Used by budget progress (Phase 4): total EXPENSE spend in one category within a range. */
    fun observeTotalForCategoryInRange(categoryId: Long, range: DateRange, currencyCode: String): Flow<Money> =
        transactionDao.observeTotalForCategoryInRange(categoryId, range.start.toEpochDay(), range.endExclusive.toEpochDay())
            .map { Money(it, currencyCode) }

    /** One-shot: this account's net effect up to [asOf], used to reconstruct historical net worth (Section 17). */
    suspend fun getAccountNetEffectAsOf(accountId: Long, asOf: LocalDate): Long =
        transactionDao.getAccountNetEffectAsOf(accountId, asOf.toEpochDay())

    fun observeFiltered(
        accountId: Long? = null,
        categoryId: Long? = null,
        type: TransactionType? = null,
        range: DateRange,
        query: String? = null
    ): Flow<List<Transaction>> = transactionDao.observeFiltered(
        accountId, categoryId, type,
        range.start.toEpochDay(), range.endExclusive.toEpochDay(),
        query?.takeIf { it.isNotBlank() }
    ).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Transaction? = transactionDao.getById(id)?.toDomain()

    /** Records a new transaction and atomically applies its balance effect (Section 32). */
    suspend fun record(draft: TransactionDraft): Long = database.withTransaction {
        insertOne(draft)
    }

    /**
     * Records many transactions as a single, all-or-nothing database transaction
     * (used by CSV import - Section 22). Importing hundreds/thousands of rows one
     * [record] call at a time would open (and fsync) a separate SQLite transaction
     * per row, which is both far slower and means a crash partway through leaves
     * only some rows imported; wrapping the whole batch in one [withTransaction]
     * fixes both. Rows are applied in order, so a later row's account/category can
     * safely be one this same batch just created via the repositories beforehand.
     */
    suspend fun importAll(drafts: List<TransactionDraft>): List<Long> = database.withTransaction {
        drafts.map { draft -> insertOne(draft) }
    }

    /** Shared insert path for [record] and [importAll] - must only ever run inside an existing transaction. */
    private suspend fun insertOne(draft: TransactionDraft): Long {
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            type = draft.type,
            amountMinor = draft.amount.minorUnits,
            currencyCode = draft.amount.currencyCode,
            accountId = draft.accountId,
            toAccountId = draft.toAccountId,
            categoryId = draft.categoryId,
            date = draft.date.toEpochDay(),
            time = draft.timeMinutesSinceMidnight,
            payee = draft.payee,
            notes = draft.notes,
            paymentMethod = draft.paymentMethod,
            receiptUri = draft.receiptUri,
            createdAt = now,
            updatedAt = now
        )
        val id = transactionDao.insert(entity)
        applyBalanceEffect(entity, reverse = false)
        linkTags(id, draft.tagIds)
        return id
    }

    /** Edits an existing transaction: reverses the old balance effect, then applies the new one. */
    suspend fun edit(transactionId: Long, draft: TransactionDraft) = database.withTransaction {
        val existing = transactionDao.getById(transactionId)
            ?: error("Cannot edit transaction $transactionId: not found")
        applyBalanceEffect(existing, reverse = true)

        val updated = existing.copy(
            type = draft.type,
            amountMinor = draft.amount.minorUnits,
            currencyCode = draft.amount.currencyCode,
            accountId = draft.accountId,
            toAccountId = draft.toAccountId,
            categoryId = draft.categoryId,
            date = draft.date.toEpochDay(),
            time = draft.timeMinutesSinceMidnight,
            payee = draft.payee,
            notes = draft.notes,
            paymentMethod = draft.paymentMethod,
            receiptUri = draft.receiptUri,
            updatedAt = System.currentTimeMillis()
        )
        transactionDao.update(updated)
        applyBalanceEffect(updated, reverse = false)

        tagDao.clearTagsForTransaction(transactionId)
        linkTags(transactionId, draft.tagIds)
    }

    /** Soft-deletes and reverses the balance effect; recoverable via [undoDelete]. */
    suspend fun delete(transactionId: Long) = database.withTransaction {
        val existing = transactionDao.getById(transactionId) ?: return@withTransaction
        if (existing.isDeleted) return@withTransaction
        applyBalanceEffect(existing, reverse = true)
        transactionDao.softDelete(transactionId, System.currentTimeMillis())
    }

    suspend fun undoDelete(transactionId: Long) = database.withTransaction {
        val existing = transactionDao.getById(transactionId) ?: return@withTransaction
        if (!existing.isDeleted) return@withTransaction
        transactionDao.undoDelete(transactionId, System.currentTimeMillis())
        applyBalanceEffect(existing, reverse = false)
    }

    suspend fun duplicate(transactionId: Long): Long? {
        val existing = getById(transactionId) ?: return null
        return record(
            TransactionDraft(
                type = existing.type,
                amount = existing.amount,
                accountId = existing.accountId,
                toAccountId = existing.toAccountId,
                categoryId = existing.categoryId,
                date = existing.date,
                timeMinutesSinceMidnight = existing.timeMinutesSinceMidnight,
                payee = existing.payee,
                notes = existing.notes,
                paymentMethod = existing.paymentMethod,
                receiptUri = existing.receiptUri,
                tagIds = existing.tagIds
            )
        )
    }

    /**
     * Applies (or, when [reverse] is true, reverses) the balance effect of a single
     * transaction row. This is the one function in the whole codebase that is allowed
     * to know "income credits an account, expense debits it, a transfer moves money
     * between two accounts without changing total net worth" (Section 11/32).
     */
    private suspend fun applyBalanceEffect(entity: TransactionEntity, reverse: Boolean) {
        val sign = if (reverse) -1 else 1
        when (entity.type) {
            TransactionType.INCOME ->
                accountDao.adjustBalance(entity.accountId, sign * entity.amountMinor)
            TransactionType.EXPENSE ->
                accountDao.adjustBalance(entity.accountId, -sign * entity.amountMinor)
            TransactionType.TRANSFER -> {
                val toAccountId = entity.toAccountId
                    ?: error("Transfer transaction ${entity.id} is missing a destination account")
                accountDao.adjustBalance(entity.accountId, -sign * entity.amountMinor)
                accountDao.adjustBalance(toAccountId, sign * entity.amountMinor)
            }
        }
    }

    private suspend fun linkTags(transactionId: Long, tagIds: List<Long>) {
        tagIds.forEach { tagId ->
            tagDao.link(TransactionTagCrossRef(transactionId, tagId))
        }
    }

    /** Finds-or-creates tags by name, used by the Quick Add tag input (Section 7). */
    suspend fun resolveOrCreateTags(names: List<String>): List<Long> = names
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { name ->
            tagDao.findByName(name)?.id ?: tagDao.insert(TagEntity(name = name)).let {
                if (it > 0) it else tagDao.findByName(name)!!.id
            }
        }

    private suspend fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = id,
        type = type,
        amount = Money(amountMinor, currencyCode),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        date = LocalDate.ofEpochDay(date),
        timeMinutesSinceMidnight = time,
        payee = payee,
        notes = notes,
        paymentMethod = paymentMethod,
        receiptUri = receiptUri,
        tagIds = tagDao.tagIdsForTransaction(id)
    )
}
