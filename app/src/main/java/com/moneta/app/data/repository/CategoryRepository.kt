package com.moneta.app.data.repository

import com.moneta.app.data.local.dao.CategoryDao
import com.moneta.app.data.local.entity.CategoryEntity
import com.moneta.app.data.local.entity.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How many transactions, repeating rules and sub-categories use a category. */
data class CategoryUsage(val transactions: Int, val recurring: Int, val children: Int)

sealed class CategoryDeletionResult {
    data object Deleted : CategoryDeletionResult()
    data class RequiresReassignment(val transactionCount: Int) : CategoryDeletionResult()
}

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {

    fun observeByType(type: CategoryType): Flow<List<Category>> =
        categoryDao.observeByType(type).map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Saves the order the user chose: the first id gets position 0, and so on. */
    suspend fun saveOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> categoryDao.setSortOrder(id, index) }
    }

    /** The same ids with the most used category first (ties keep their current order). */
    suspend fun mostUsedFirst(ids: List<Long>): List<Long> {
        val uses = categoryDao.useCounts().associate { it.id to it.uses }
        return ids.sortedByDescending { uses[it] ?: 0 }
    }

    suspend fun getById(id: Long): Category? = categoryDao.getById(id)?.toDomain()

    suspend fun createCategory(
        name: String,
        type: CategoryType,
        icon: String,
        colorHex: String,
        parentCategoryId: Long? = null
    ): Long {
        // Never make a second category with the same name in the same place: use the one already there.
        val same = categoryDao.observeByType(type).first().firstOrNull {
            it.parentCategoryId == parentCategoryId && it.name.trim().equals(name.trim(), ignoreCase = true)
        }
        if (same != null) return same.id
        return categoryDao.insert(
            CategoryEntity(
                name = name,
                type = type,
                icon = icon,
                colorHex = colorHex,
                parentCategoryId = parentCategoryId
            )
        )
    }

    suspend fun renameOrRestyle(category: Category, parentCategoryId: Long? = category.parentCategoryId) {
        val existing = categoryDao.getById(category.id) ?: return
        categoryDao.update(
            existing.copy(
                name = category.name,
                icon = category.icon,
                colorHex = category.colorHex,
                parentCategoryId = parentCategoryId
            )
        )
    }

    /** Section 12: top-level categories only, for a "pick a parent" list - subcategories can't nest further. */
    fun observeTopLevelByType(type: CategoryType): Flow<List<Category>> =
        categoryDao.observeByType(type).map { list -> list.filter { it.parentCategoryId == null }.map { it.toDomain() } }

    /** All subcategories that belong to [parentId], for grouping a category list by parent (Section 12). */
    fun observeSubcategoriesOf(parentId: Long): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.filter { it.parentCategoryId == parentId }.map { it.toDomain() } }

    /**
     * Section 12: categories with existing transactions must never be
     * silently deleted or have their history orphaned. Callers must first
     * check [CategoryDeletionResult.RequiresReassignment] and call
     * [deleteWithReassignment] with a target category chosen by the user.
     */
    suspend fun tryDelete(categoryId: Long): CategoryDeletionResult {
        val count = categoryDao.transactionCountForCategory(categoryId)
        return if (count == 0) {
            categoryDao.deleteHard(categoryId)
            CategoryDeletionResult.Deleted
        } else {
            CategoryDeletionResult.RequiresReassignment(count)
        }
    }

    /** What is attached to a category, so the delete screen can say exactly what will happen. */
    suspend fun usageOf(categoryId: Long): CategoryUsage = CategoryUsage(
        transactions = categoryDao.transactionCountForCategory(categoryId),
        recurring = categoryDao.recurringCountForCategory(categoryId),
        children = categoryDao.childCount(categoryId)
    )

    /**
     * Deletes a category. When it is in use, [moveTo] says where its transactions and repeating rules go first.
     * Sub-categories become ordinary categories (the database clears their parent).
     */
    suspend fun deleteCategory(categoryId: Long, moveTo: Long?) {
        if (moveTo != null && moveTo != categoryId) {
            categoryDao.reassignTransactions(categoryId, moveTo)
            categoryDao.reassignRecurring(categoryId, moveTo)
        }
        categoryDao.clearCategoryOnTransactions(categoryId)
        categoryDao.deleteHard(categoryId)
    }

    suspend fun deleteWithReassignment(categoryId: Long, reassignToCategoryId: Long) {
        categoryDao.reassignTransactions(categoryId, reassignToCategoryId)
        categoryDao.deleteHard(categoryId)
    }

    private fun CategoryEntity.toDomain() = Category(
        id = id,
        name = name,
        type = type,
        icon = icon,
        colorHex = colorHex,
        parentCategoryId = parentCategoryId,
        sortOrder = sortOrder,
        isDefault = isDefault,
        isArchived = isArchived
    )
}
