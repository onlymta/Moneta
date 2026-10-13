package com.moneta.app.data.repository

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.AccountDao
import com.moneta.app.data.local.entity.AccountEntity
import com.moneta.app.data.local.entity.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class ArchiveOrDeleteResult {
    data object Archived : ArchiveOrDeleteResult()
    data object Deleted : ArchiveOrDeleteResult()
    data class Blocked(val reason: String) : ArchiveOrDeleteResult()
}

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {

    fun observeActiveAccounts(): Flow<List<Account>> =
        accountDao.observeActiveAccounts().map { list -> list.map { it.toDomain() } }

    fun observeAllAccounts(): Flow<List<Account>> =
        accountDao.observeAllAccounts().map { list -> list.map { it.toDomain() } }

    fun observeTotalNetWorth(currencyCode: String): Flow<Money> =
        accountDao.observeTotalNetWorthMinor().map { Money(it, currencyCode) }

    /** One-shot list of accounts counted toward net worth, used by the net-worth-trend report (Section 17). */
    suspend fun getActiveIncludedInNetWorth(): List<Account> =
        accountDao.getActiveIncludedInNetWorth().map { it.toDomain() }

    suspend fun getById(id: Long): Account? = accountDao.getById(id)?.toDomain()

    suspend fun createAccount(
        name: String,
        type: AccountType,
        icon: String,
        colorHex: String,
        openingBalance: Money,
        includeInNetWorth: Boolean = true
    ): Long {
        val entity = AccountEntity(
            name = name,
            type = type,
            icon = icon,
            colorHex = colorHex,
            openingBalanceMinor = openingBalance.minorUnits,
            currentBalanceMinor = openingBalance.minorUnits,
            currencyCode = openingBalance.currencyCode,
            includeInNetWorth = includeInNetWorth,
            createdAt = System.currentTimeMillis()
        )
        return accountDao.insert(entity)
    }

    suspend fun updateAccountDetails(account: Account) {
        val existing = accountDao.getById(account.id) ?: return
        // Only mutable "details" fields are updated here - balances are never
        // touched from this path, only from TransactionRepository's write path.
        accountDao.update(
            existing.copy(
                name = account.name,
                icon = account.icon,
                colorHex = account.colorHex,
                includeInNetWorth = account.includeInNetWorth
            )
        )
    }

    /**
     * Archiving is the recommended path for removing an account with history
     * (Section 10) - hard delete is only allowed when there are zero
     * transactions, otherwise the caller must archive instead of silently
     * losing data.
     */
    suspend fun archiveOrDelete(accountId: Long): ArchiveOrDeleteResult {
        val count = accountDao.transactionCountForAccount(accountId)
        return if (count == 0) {
            accountDao.deleteHard(accountId)
            ArchiveOrDeleteResult.Deleted
        } else {
            accountDao.archive(accountId)
            ArchiveOrDeleteResult.Archived
        }
    }

    private fun AccountEntity.toDomain() = Account(
        id = id,
        name = name,
        type = type,
        icon = icon,
        colorHex = colorHex,
        openingBalance = Money(openingBalanceMinor, currencyCode),
        currentBalance = Money(currentBalanceMinor, currencyCode),
        includeInNetWorth = includeInNetWorth,
        isArchived = isArchived,
        sortOrder = sortOrder
    )
}
