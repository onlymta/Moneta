package com.moneta.app.data.repository

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.GoalDao
import com.moneta.app.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao
) {
    fun observeActiveGoals(): Flow<List<Goal>> =
        goalDao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Goal? = goalDao.getById(id)?.toDomain()

    suspend fun createGoal(
        name: String,
        icon: String,
        targetAmount: Money,
        targetDate: LocalDate?,
        linkedAccountId: Long? = null
    ): Long = goalDao.insert(
        GoalEntity(
            name = name,
            icon = icon,
            targetAmountMinor = targetAmount.minorUnits,
            currencyCode = targetAmount.currencyCode,
            targetDate = targetDate?.toEpochDay(),
            linkedAccountId = linkedAccountId,
            createdAt = System.currentTimeMillis()
        )
    )

    /** Adds (or, with a negative amount, removes) a contribution toward this goal's saved amount. */
    suspend fun contribute(goalId: Long, amount: Money) {
        goalDao.addContribution(goalId, amount.minorUnits)
    }

    suspend fun updateDetails(goal: Goal) {
        val existing = goalDao.getById(goal.id) ?: return
        goalDao.update(
            existing.copy(
                name = goal.name,
                icon = goal.icon,
                targetAmountMinor = goal.targetAmount.minorUnits,
                targetDate = goal.targetDate?.toEpochDay()
            )
        )
    }

    suspend fun archive(goalId: Long) = goalDao.archive(goalId)

    suspend fun delete(goalId: Long) = goalDao.delete(goalId)

    private fun GoalEntity.toDomain() = Goal(
        id = id,
        name = name,
        icon = icon,
        targetAmount = Money(targetAmountMinor, currencyCode),
        currentAmount = Money(currentAmountMinor, currencyCode),
        targetDate = targetDate?.let { LocalDate.ofEpochDay(it) },
        linkedAccountId = linkedAccountId,
        isArchived = isArchived
    )
}
