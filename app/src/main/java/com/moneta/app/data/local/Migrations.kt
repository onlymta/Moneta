package com.moneta.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds the `budgets` table for Phase 4. Purely additive - no
 * existing table is altered or dropped, so every Phase 1-3 user's accounts,
 * categories, and transactions are untouched (Section 45).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS budgets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scope TEXT NOT NULL,
                category_id INTEGER,
                amount_minor INTEGER NOT NULL,
                currency_code TEXT NOT NULL,
                period TEXT NOT NULL,
                custom_period_start INTEGER,
                custom_period_end INTEGER,
                alert_threshold_percent INTEGER NOT NULL DEFAULT 90,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_category_id ON budgets(category_id)")
    }
}

/**
 * v2 -> v3: adds `goals` (Section 14) and `bills` (Section 16). Both purely
 * additive - no existing table altered (Section 45).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                icon TEXT NOT NULL,
                target_amount_minor INTEGER NOT NULL,
                current_amount_minor INTEGER NOT NULL DEFAULT 0,
                currency_code TEXT NOT NULL,
                target_date INTEGER,
                linked_account_id INTEGER,
                is_archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(linked_account_id) REFERENCES accounts(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_linked_account_id ON goals(linked_account_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                amount_minor INTEGER NOT NULL,
                currency_code TEXT NOT NULL,
                due_date INTEGER NOT NULL,
                account_id INTEGER,
                category_id INTEGER,
                status TEXT NOT NULL DEFAULT 'UNPAID',
                paid_transaction_id INTEGER,
                FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE SET NULL,
                FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_account_id ON bills(account_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_category_id ON bills(category_id)")
    }
}

/**
 * v3 -> v4: adds `recurring_transactions` (Section 15). Purely additive - no
 * existing table altered (Section 45). This was deliberately deferred past
 * every earlier phase specifically to avoid shipping the idempotent-
 * generation logic half-built; see RecurringTransactionRepository for that.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurring_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                template_type TEXT NOT NULL,
                amount_minor INTEGER NOT NULL,
                currency_code TEXT NOT NULL,
                account_id INTEGER NOT NULL,
                to_account_id INTEGER,
                category_id INTEGER,
                frequency TEXT NOT NULL,
                interval_count INTEGER NOT NULL DEFAULT 1,
                start_date INTEGER NOT NULL,
                end_date INTEGER,
                next_occurrence_date INTEGER NOT NULL,
                reminder_enabled INTEGER NOT NULL DEFAULT 0,
                reminder_offset_days INTEGER NOT NULL DEFAULT 1,
                is_active INTEGER NOT NULL DEFAULT 1,
                notes TEXT,
                payee TEXT,
                FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
                FOREIGN KEY(to_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
                FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_account_id ON recurring_transactions(account_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_to_account_id ON recurring_transactions(to_account_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_category_id ON recurring_transactions(category_id)")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
