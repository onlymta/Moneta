package com.moneta.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CategoryType { INCOME, EXPENSE }

/**
 * A transaction category (Section 12). [parentCategoryId] allows exactly one
 * level of subcategory nesting (e.g. Food -> Restaurants) - deeper nesting is
 * intentionally unsupported to keep the category picker fast and simple
 * (Section 12: "do not make the category system unnecessarily complicated").
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parent_category_id"), Index("type")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val type: CategoryType,
    val icon: String,
    val colorHex: String,

    @ColumnInfo(name = "parent_category_id")
    val parentCategoryId: Long? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false
)
