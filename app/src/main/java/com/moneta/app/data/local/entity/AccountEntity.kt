package com.moneta.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountType { CASH, BANK, CREDIT_CARD, SAVINGS, E_WALLET, OTHER }

/**
 * A user-defined money container (Section 10). [currentBalanceMinor] is a
 * denormalized cache of openingBalanceMinor + all non-deleted transactions
 * affecting this account - it exists purely so the dashboard/account list can
 * read a single fast column instead of aggregating the full transaction table
 * on every frame. It is written ONLY by TransactionRepository's single
 * balance-mutation write path (ARCHITECTURE.md Section 6/32) - never updated
 * directly from the UI layer.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val type: AccountType,
    val icon: String,
    val colorHex: String,

    @ColumnInfo(name = "opening_balance_minor")
    val openingBalanceMinor: Long,

    @ColumnInfo(name = "current_balance_minor")
    val currentBalanceMinor: Long,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "include_in_net_worth")
    val includeInNetWorth: Boolean = true,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
