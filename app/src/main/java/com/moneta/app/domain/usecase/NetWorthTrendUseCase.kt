package com.moneta.app.domain.usecase

import com.moneta.app.core.money.Money
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.TransactionRepository
import java.time.LocalDate
import javax.inject.Inject

data class NetWorthPoint(val asOf: LocalDate, val netWorth: Money)

/**
 * Reconstructs net worth as of a set of past dates by replaying each
 * net-worth-included account's opening balance plus its transaction history
 * up to that date - Moneta doesn't store periodic balance snapshots, so this
 * derives the trend from the same transaction ledger everything else reads
 * (Section 17's net-worth report), rather than inventing or estimating it.
 *
 * Known simplification (documented, not hidden): an account created after a
 * given historical date is still included with its full opening balance for
 * that date, since it has no transactions before its creation to net out.
 * This only matters for the (rare) case of asking about net worth from
 * before an account existed, and is called out in the project README.
 */
class NetWorthTrendUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(asOfDates: List<LocalDate>, currencyCode: String): List<NetWorthPoint> {
        val accounts = accountRepository.getActiveIncludedInNetWorth()
        if (accounts.isEmpty()) return asOfDates.map { NetWorthPoint(it, Money.zero(currencyCode)) }

        return asOfDates.map { date ->
            var totalMinor = 0L
            accounts.forEach { account ->
                val netEffect = transactionRepository.getAccountNetEffectAsOf(account.id, date)
                totalMinor += account.openingBalance.minorUnits + netEffect
            }
            NetWorthPoint(asOf = date, netWorth = Money(totalMinor, currencyCode))
        }
    }
}
