package com.moneta.app.data.backup

import androidx.room.withTransaction
import com.moneta.app.data.local.MonetaDatabase
import com.moneta.app.data.local.dao.AccountDao
import com.moneta.app.data.local.dao.BillDao
import com.moneta.app.data.local.dao.BudgetDao
import com.moneta.app.data.local.dao.CategoryDao
import com.moneta.app.data.local.dao.GoalDao
import com.moneta.app.data.local.dao.RecurringTransactionDao
import com.moneta.app.data.local.dao.TagDao
import com.moneta.app.data.local.dao.TransactionDao
import com.moneta.app.data.local.entity.AccountEntity
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.local.entity.BillEntity
import com.moneta.app.data.local.entity.BillStatus
import com.moneta.app.data.local.entity.BudgetEntity
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.CategoryEntity
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.GoalEntity
import com.moneta.app.data.local.entity.RecurrenceFrequency
import com.moneta.app.data.local.entity.RecurringTransactionEntity
import com.moneta.app.data.local.entity.TagEntity
import com.moneta.app.data.local.entity.TransactionEntity
import com.moneta.app.data.local.entity.TransactionTagCrossRef
import com.moneta.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Bumped whenever the backup JSON schema changes, so a future Moneta version can detect an old backup format. */
private const val BACKUP_SCHEMA_VERSION = 2

sealed class RestoreResult {
    data object Success : RestoreResult()
    data class Failed(val reason: String) : RestoreResult()
}

/**
 * Local backup/restore (Section 24). The backup is one plain JSON file (not a
 * zip - there are no receipt image files to bundle yet in this phase) saved
 * wherever the user chooses via the Storage Access Framework, containing
 * every table needed to fully reconstruct the app's data. Restore always
 * runs inside a single Room transaction: either every table is replaced, or
 * (on any parse/validation failure) nothing is touched at all.
 */
@Singleton
class BackupManager @Inject constructor(
    private val database: MonetaDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val billDao: BillDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val settingsRepository: com.moneta.app.data.settings.SettingsRepository,
    private val plusRepository: com.moneta.app.data.plus.PlusRepository
) {

    suspend fun createBackup(outputStream: OutputStream) {
        val root = JSONObject()
        root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
        root.put("createdAt", System.currentTimeMillis())

        root.put("accounts", JSONArray().apply {
            accountDao.observeAllAccounts().first().forEach { put(it.toJson()) }
        })
        root.put("categories", JSONArray().apply {
            categoryDao.observeAll().first().forEach { put(it.toJson()) }
        })
        root.put("transactions", JSONArray().apply {
            transactionDao.getAllForBackup().forEach { put(it.toJson()) }
        })
        root.put("tags", JSONArray().apply {
            tagDao.getAllForBackup().forEach { put(it.toJson()) }
        })
        root.put("transactionTags", JSONArray().apply {
            tagDao.getAllCrossRefsForBackup().forEach { put(it.toJson()) }
        })
        root.put("budgets", JSONArray().apply {
            budgetDao.observeAll().first().forEach { put(it.toJson()) }
        })
        root.put("goals", JSONArray().apply {
            goalDao.getAllForBackup().forEach { put(it.toJson()) }
        })
        root.put("bills", JSONArray().apply {
            billDao.observeAll().first().forEach { put(it.toJson()) }
        })
        root.put("recurringTransactions", JSONArray().apply {
            recurringTransactionDao.getAllForBackup().forEach { put(it.toJson()) }
        })

        root.put("settings", settingsRepository.exportAllJson())
        // Only the date the free trial ends, so restoring this file can't be used to get a fresh trial.
        // It never carries a purchase or any other Plus state.
        plusRepository.trialEndsAt()?.let { root.put("trialEndsAt", it) }

        outputStream.writer(Charsets.UTF_8).use { it.write(root.toString()) }
    }

    suspend fun restoreBackup(inputStream: InputStream): RestoreResult {
        val text = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return RestoreResult.Failed("This file isn't a valid Moneta backup.")
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion !in 1..BACKUP_SCHEMA_VERSION) {
            return RestoreResult.Failed("This backup was made by an incompatible version of Moneta.")
        }

        return try {
            val accounts = root.getJSONArray("accounts").map { it.toAccountEntity() }
            val categories = root.getJSONArray("categories").map { it.toCategoryEntity() }
            val transactions = root.getJSONArray("transactions").map { it.toTransactionEntity() }
            val tags = root.optJSONArray("tags")?.map { it.toTagEntity() } ?: emptyList()
            val crossRefs = root.optJSONArray("transactionTags")?.map { it.toCrossRef() } ?: emptyList()
            val budgets = root.optJSONArray("budgets")?.map { it.toBudgetEntity() } ?: emptyList()
            val goals = root.optJSONArray("goals")?.map { it.toGoalEntity() } ?: emptyList()
            val bills = root.optJSONArray("bills")?.map { it.toBillEntity() } ?: emptyList()
            val recurringTransactions = root.optJSONArray("recurringTransactions")?.map { it.toRecurringEntity() } ?: emptyList()

            database.withTransaction {
                // Dependency-safe delete order (children before parents).
                recurringTransactionDao.deleteAllForRestore()
                billDao.deleteAllForRestore()
                budgetDao.deleteAllForRestore()
                tagDao.deleteAllCrossRefsForRestore()
                tagDao.deleteAllTagsForRestore()
                transactionDao.deleteAllForRestore()
                goalDao.deleteAllForRestore()
                categoryDao.deleteAllForRestore()
                accountDao.deleteAllForRestore()

                // Dependency-safe insert order (parents before children).
                accountDao.insertAll(accounts)
                categoryDao.insertAll(parentsFirst(categories))
                transactionDao.insertAll(transactions)
                tagDao.insertAllTags(tags)
                tagDao.insertAllCrossRefs(crossRefs)
                budgetDao.insertAll(budgets)
                goalDao.insertAll(goals)
                billDao.insertAll(bills)
                recurringTransactionDao.insertAll(recurringTransactions)
            }
            // Settings come last and only after the data restored cleanly. Older backups simply have none.
            root.optJSONObject("settings")?.let { settingsRepository.importAllJson(it) }
            if (root.has("trialEndsAt")) plusRepository.adoptTrialEnd(root.optLong("trialEndsAt"))
            RestoreResult.Success
        } catch (e: Exception) {
            RestoreResult.Failed("Restore failed: ${e.message ?: "the backup file may be corrupted."}")
        }
    }
}

/**
 * Puts parent categories before their sub-categories. A category row points at its parent, so inserting a
 * child first fails the database's foreign key check; the saved order can have a child ahead of its parent
 * once categories have been reordered. A parent that is missing from the file is cleared.
 */
private fun parentsFirst(categories: List<CategoryEntity>): List<CategoryEntity> {
    val known = categories.map { it.id }.toSet()
    val placed = HashSet<Long>()
    val result = ArrayList<CategoryEntity>(categories.size)
    var waiting = categories.map { c ->
        if (c.parentCategoryId != null && c.parentCategoryId !in known) c.copy(parentCategoryId = null) else c
    }
    while (waiting.isNotEmpty()) {
        val ready = waiting.filter { it.parentCategoryId == null || it.parentCategoryId in placed }
        if (ready.isEmpty()) {
            // A loop of parents (should never happen): flatten what is left so the restore still works.
            result.addAll(waiting.map { it.copy(parentCategoryId = null) })
            break
        }
        ready.forEach { placed.add(it.id) }
        result.addAll(ready)
        waiting = waiting.filter { it.id !in placed }
    }
    return result
}

// --- Entity <-> JSON mapping ---
// Kept as extension functions in this file (rather than on the entities themselves)
// so the entity classes stay free of serialization concerns (Section 46).

private fun AccountEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("type", type.name); put("icon", icon); put("colorHex", colorHex)
    put("openingBalanceMinor", openingBalanceMinor); put("currentBalanceMinor", currentBalanceMinor)
    put("currencyCode", currencyCode); put("includeInNetWorth", includeInNetWorth); put("isArchived", isArchived)
    put("sortOrder", sortOrder); put("createdAt", createdAt)
}

private fun JSONObject.toAccountEntity() = AccountEntity(
    id = getLong("id"), name = getString("name"), type = AccountType.valueOf(getString("type")),
    icon = getString("icon"), colorHex = getString("colorHex"),
    openingBalanceMinor = getLong("openingBalanceMinor"), currentBalanceMinor = getLong("currentBalanceMinor"),
    currencyCode = getString("currencyCode"), includeInNetWorth = getBoolean("includeInNetWorth"),
    isArchived = getBoolean("isArchived"), sortOrder = getInt("sortOrder"), createdAt = getLong("createdAt")
)

private fun CategoryEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("type", type.name); put("icon", icon); put("colorHex", colorHex)
    put("parentCategoryId", parentCategoryId ?: JSONObject.NULL); put("sortOrder", sortOrder)
    put("isDefault", isDefault); put("isArchived", isArchived)
}

private fun JSONObject.toCategoryEntity() = CategoryEntity(
    id = getLong("id"), name = getString("name"), type = CategoryType.valueOf(getString("type")),
    icon = getString("icon"), colorHex = getString("colorHex"),
    parentCategoryId = if (isNull("parentCategoryId")) null else getLong("parentCategoryId"),
    sortOrder = getInt("sortOrder"), isDefault = getBoolean("isDefault"), isArchived = getBoolean("isArchived")
)

private fun TransactionEntity.toJson() = JSONObject().apply {
    put("id", id); put("type", type.name); put("amountMinor", amountMinor); put("currencyCode", currencyCode)
    put("accountId", accountId); put("toAccountId", toAccountId ?: JSONObject.NULL)
    put("categoryId", categoryId ?: JSONObject.NULL); put("date", date); put("time", time)
    put("payee", payee ?: JSONObject.NULL); put("notes", notes ?: JSONObject.NULL)
    put("paymentMethod", paymentMethod ?: JSONObject.NULL); put("receiptUri", receiptUri ?: JSONObject.NULL)
    put("latitude", latitude ?: JSONObject.NULL); put("longitude", longitude ?: JSONObject.NULL)
    put("recurringTransactionId", recurringTransactionId ?: JSONObject.NULL)
    put("isDeleted", isDeleted); put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun JSONObject.toTransactionEntity() = TransactionEntity(
    id = getLong("id"), type = TransactionType.valueOf(getString("type")), amountMinor = getLong("amountMinor"),
    currencyCode = getString("currencyCode"), accountId = getLong("accountId"),
    toAccountId = if (isNull("toAccountId")) null else getLong("toAccountId"),
    categoryId = if (isNull("categoryId")) null else getLong("categoryId"),
    date = getLong("date"), time = getInt("time"),
    payee = if (isNull("payee")) null else getString("payee"),
    notes = if (isNull("notes")) null else getString("notes"),
    paymentMethod = if (isNull("paymentMethod")) null else getString("paymentMethod"),
    receiptUri = if (isNull("receiptUri")) null else getString("receiptUri"),
    latitude = if (isNull("latitude")) null else getDouble("latitude"),
    longitude = if (isNull("longitude")) null else getDouble("longitude"),
    recurringTransactionId = if (isNull("recurringTransactionId")) null else getLong("recurringTransactionId"),
    isDeleted = getBoolean("isDeleted"), createdAt = getLong("createdAt"), updatedAt = getLong("updatedAt")
)

private fun TagEntity.toJson() = JSONObject().apply { put("id", id); put("name", name) }
private fun JSONObject.toTagEntity() = TagEntity(id = getLong("id"), name = getString("name"))

private fun TransactionTagCrossRef.toJson() = JSONObject().apply {
    put("transactionId", transactionId); put("tagId", tagId)
}
private fun JSONObject.toCrossRef() = TransactionTagCrossRef(
    transactionId = getLong("transactionId"), tagId = getLong("tagId")
)

private fun BudgetEntity.toJson() = JSONObject().apply {
    put("id", id); put("scope", scope.name); put("categoryId", categoryId ?: JSONObject.NULL)
    put("amountMinor", amountMinor); put("currencyCode", currencyCode); put("period", period.name)
    put("customPeriodStart", customPeriodStart ?: JSONObject.NULL)
    put("customPeriodEnd", customPeriodEnd ?: JSONObject.NULL)
    put("alertThresholdPercent", alertThresholdPercent); put("isEnabled", isEnabled)
}
private fun JSONObject.toBudgetEntity() = BudgetEntity(
    id = getLong("id"), scope = BudgetScope.valueOf(getString("scope")),
    categoryId = if (isNull("categoryId")) null else getLong("categoryId"),
    amountMinor = getLong("amountMinor"), currencyCode = getString("currencyCode"),
    period = BudgetPeriod.valueOf(getString("period")),
    customPeriodStart = if (isNull("customPeriodStart")) null else getLong("customPeriodStart"),
    customPeriodEnd = if (isNull("customPeriodEnd")) null else getLong("customPeriodEnd"),
    alertThresholdPercent = getInt("alertThresholdPercent"), isEnabled = getBoolean("isEnabled")
)

private fun GoalEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("icon", icon); put("targetAmountMinor", targetAmountMinor)
    put("currentAmountMinor", currentAmountMinor); put("currencyCode", currencyCode)
    put("targetDate", targetDate ?: JSONObject.NULL); put("linkedAccountId", linkedAccountId ?: JSONObject.NULL)
    put("isArchived", isArchived); put("createdAt", createdAt)
}
private fun JSONObject.toGoalEntity() = GoalEntity(
    id = getLong("id"), name = getString("name"), icon = getString("icon"),
    targetAmountMinor = getLong("targetAmountMinor"), currentAmountMinor = getLong("currentAmountMinor"),
    currencyCode = getString("currencyCode"),
    targetDate = if (isNull("targetDate")) null else getLong("targetDate"),
    linkedAccountId = if (isNull("linkedAccountId")) null else getLong("linkedAccountId"),
    isArchived = getBoolean("isArchived"), createdAt = getLong("createdAt")
)

private fun BillEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("amountMinor", amountMinor); put("currencyCode", currencyCode)
    put("dueDate", dueDate); put("accountId", accountId ?: JSONObject.NULL)
    put("categoryId", categoryId ?: JSONObject.NULL); put("status", status.name)
    put("paidTransactionId", paidTransactionId ?: JSONObject.NULL)
}
private fun JSONObject.toBillEntity() = BillEntity(
    id = getLong("id"), name = getString("name"), amountMinor = getLong("amountMinor"),
    currencyCode = getString("currencyCode"), dueDate = getLong("dueDate"),
    accountId = if (isNull("accountId")) null else getLong("accountId"),
    categoryId = if (isNull("categoryId")) null else getLong("categoryId"),
    status = BillStatus.valueOf(getString("status")),
    paidTransactionId = if (isNull("paidTransactionId")) null else getLong("paidTransactionId")
)

private fun RecurringTransactionEntity.toJson() = JSONObject().apply {
    put("id", id); put("templateType", templateType.name); put("amountMinor", amountMinor)
    put("currencyCode", currencyCode); put("accountId", accountId); put("toAccountId", toAccountId ?: JSONObject.NULL)
    put("categoryId", categoryId ?: JSONObject.NULL); put("frequency", frequency.name)
    put("intervalCount", intervalCount); put("startDate", startDate); put("endDate", endDate ?: JSONObject.NULL)
    put("nextOccurrenceDate", nextOccurrenceDate); put("reminderEnabled", reminderEnabled)
    put("reminderOffsetDays", reminderOffsetDays); put("isActive", isActive)
    put("notes", notes ?: JSONObject.NULL); put("payee", payee ?: JSONObject.NULL)
}
private fun JSONObject.toRecurringEntity() = RecurringTransactionEntity(
    id = getLong("id"), templateType = TransactionType.valueOf(getString("templateType")),
    amountMinor = getLong("amountMinor"), currencyCode = getString("currencyCode"),
    accountId = getLong("accountId"), toAccountId = if (isNull("toAccountId")) null else getLong("toAccountId"),
    categoryId = if (isNull("categoryId")) null else getLong("categoryId"),
    frequency = RecurrenceFrequency.valueOf(getString("frequency")), intervalCount = getInt("intervalCount"),
    startDate = getLong("startDate"), endDate = if (isNull("endDate")) null else getLong("endDate"),
    nextOccurrenceDate = getLong("nextOccurrenceDate"), reminderEnabled = getBoolean("reminderEnabled"),
    reminderOffsetDays = getInt("reminderOffsetDays"), isActive = getBoolean("isActive"),
    notes = if (isNull("notes")) null else getString("notes"),
    payee = if (isNull("payee")) null else getString("payee")
)

/** Small JSONArray helper so call sites read as a normal Kotlin `.map`. */
private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }
