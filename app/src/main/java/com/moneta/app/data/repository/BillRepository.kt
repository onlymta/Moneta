package com.moneta.app.data.repository

import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.BillDao
import com.moneta.app.data.local.entity.BillEntity
import com.moneta.app.data.local.entity.BillStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao,
    private val transactionRepository: TransactionRepository
) {
    fun observeAll(): Flow<List<Bill>> = billDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Bill? = billDao.getById(id)?.toDomain()

    suspend fun createBill(
        name: String,
        amount: Money,
        dueDate: LocalDate,
        accountId: Long?,
        categoryId: Long?
    ): Long = billDao.insert(
        BillEntity(
            name = name,
            amountMinor = amount.minorUnits,
            currencyCode = amount.currencyCode,
            dueDate = dueDate.toEpochDay(),
            accountId = accountId,
            categoryId = categoryId
        )
    )

    /**
     * Marks a bill paid (Section 16). When the bill has both an account and a
     * category set, this also records the matching expense transaction
     * through TransactionRepository's normal write path, so the account
     * balance updates correctly - never a shortcut that flips a status flag
     * without moving any money.
     */
    suspend fun markPaid(billId: Long): Boolean {
        val bill = billDao.getById(billId) ?: return false
        if (bill.status == BillStatus.PAID) return true

        var paidTransactionId: Long? = null
        if (bill.accountId != null && bill.categoryId != null) {
            paidTransactionId = transactionRepository.record(
                TransactionDraft(
                    type = com.moneta.app.data.local.entity.TransactionType.EXPENSE,
                    amount = Money(bill.amountMinor, bill.currencyCode),
                    accountId = bill.accountId,
                    categoryId = bill.categoryId,
                    date = LocalDate.now(),
                    timeMinutesSinceMidnight = LocalDate.now().let { 0 },
                    notes = "Bill: ${bill.name}"
                )
            )
        }
        billDao.update(bill.copy(status = BillStatus.PAID, paidTransactionId = paidTransactionId))
        return bill.accountId != null && bill.categoryId != null
    }

    suspend fun delete(billId: Long) = billDao.delete(billId)

    /** Section 16's status labels, computed from the due date rather than stored redundantly. */
    fun urgencyOf(bill: Bill, today: LocalDate = DateRange.todayDate()): BillUrgency = when {
        bill.status == BillStatus.PAID -> BillUrgency.PAID
        bill.dueDate.isBefore(today) -> BillUrgency.OVERDUE
        bill.dueDate.isEqual(today) -> BillUrgency.DUE_TODAY
        bill.dueDate.isBefore(today.plusDays(4)) -> BillUrgency.DUE_SOON
        else -> BillUrgency.UPCOMING
    }

    private fun BillEntity.toDomain() = Bill(
        id = id,
        name = name,
        amount = Money(amountMinor, currencyCode),
        dueDate = LocalDate.ofEpochDay(dueDate),
        accountId = accountId,
        categoryId = categoryId,
        status = status,
        paidTransactionId = paidTransactionId
    )
}
