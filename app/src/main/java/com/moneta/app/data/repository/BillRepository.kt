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

    suspend fun getAll(): List<Bill> = billDao.getAll().map { it.toDomain() }

    suspend fun createBill(
        name: String,
        amount: Money,
        dueDate: LocalDate,
        accountId: Long?,
        categoryId: Long?,
        reminderDaysBefore: Int = -1,
        notes: String? = null
    ): Long = billDao.insert(
        BillEntity(
            name = name,
            amountMinor = amount.minorUnits,
            currencyCode = amount.currencyCode,
            dueDate = dueDate.toEpochDay(),
            accountId = accountId,
            categoryId = categoryId,
            reminderDaysBefore = reminderDaysBefore,
            notes = notes
        )
    )

    suspend fun updateBill(
        id: Long,
        name: String,
        amount: Money,
        dueDate: LocalDate,
        accountId: Long?,
        categoryId: Long?,
        reminderDaysBefore: Int,
        notes: String?
    ) {
        val existing = billDao.getById(id) ?: return
        billDao.update(
            existing.copy(
                name = name,
                amountMinor = amount.minorUnits,
                currencyCode = amount.currencyCode,
                dueDate = dueDate.toEpochDay(),
                accountId = accountId,
                categoryId = categoryId,
                reminderDaysBefore = reminderDaysBefore,
                notes = notes
            )
        )
    }

    /** Only flips the status to paid; no money moves. Use [recordPayment] to also record the expense. */
    suspend fun markPaid(billId: Long) {
        val bill = billDao.getById(billId) ?: return
        if (bill.status == BillStatus.PAID) return
        billDao.update(bill.copy(status = BillStatus.PAID))
    }

    /** Puts a bill back to unpaid. Refused when a payment transaction was recorded (delete that transaction instead). */
    suspend fun markUnpaid(billId: Long): Boolean {
        val bill = billDao.getById(billId) ?: return false
        if (bill.paidTransactionId != null) return false
        billDao.update(bill.copy(status = BillStatus.UNPAID))
        return true
    }

    /**
     * Marks the bill paid AND records the matching expense through TransactionRepository's normal write path,
     * so the account balance updates. Returns false (and changes nothing) when the bill has no account or category.
     */
    suspend fun recordPayment(billId: Long): Boolean {
        val bill = billDao.getById(billId) ?: return false
        if (bill.status == BillStatus.PAID) return true
        val accountId = bill.accountId ?: return false
        val categoryId = bill.categoryId ?: return false
        val now = java.time.LocalTime.now()
        val txId = transactionRepository.record(
            TransactionDraft(
                type = com.moneta.app.data.local.entity.TransactionType.EXPENSE,
                amount = Money(bill.amountMinor, bill.currencyCode),
                accountId = accountId,
                categoryId = categoryId,
                date = LocalDate.now(),
                timeMinutesSinceMidnight = now.hour * 60 + now.minute,
                notes = "Bill: ${bill.name}"
            )
        )
        billDao.update(bill.copy(status = BillStatus.PAID, paidTransactionId = txId))
        return true
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
        paidTransactionId = paidTransactionId,
        reminderDaysBefore = reminderDaysBefore,
        notes = notes
    )
}
