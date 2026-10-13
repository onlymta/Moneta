package com.moneta.app.data.local

import com.moneta.app.data.local.entity.CategoryEntity
import com.moneta.app.data.local.entity.CategoryType

/**
 * The default category set from ARCHITECTURE.md / Section 12 of the original
 * brief. Inserted once on first app launch (see MonetaApplication /
 * SeedDatabaseUseCase) - after that, categories are entirely user-owned and
 * this list is never re-applied or used to overwrite user edits.
 */
object DefaultCategorySeed {

    fun expenseCategories(): List<CategoryEntity> = listOf(
        "Food" to "restaurant",
        "Groceries" to "shopping_cart",
        "Transport" to "directions_car",
        "Fuel" to "local_gas_station",
        "Shopping" to "shopping_bag",
        "Bills" to "receipt_long",
        "Utilities" to "bolt",
        "Rent" to "home",
        "Healthcare" to "local_hospital",
        "Education" to "school",
        "Entertainment" to "movie",
        "Subscriptions" to "subscriptions",
        "Travel" to "flight",
        "Personal" to "person",
        "Family" to "groups",
        "Gifts" to "card_giftcard",
        "Insurance" to "shield",
        "Taxes" to "account_balance",
        "Other" to "category"
    ).mapIndexed { index, (name, icon) ->
        CategoryEntity(
            name = name,
            type = CategoryType.EXPENSE,
            icon = icon,
            colorHex = expenseColorFor(index),
            sortOrder = index,
            isDefault = true
        )
    }

    fun incomeCategories(): List<CategoryEntity> = listOf(
        "Salary" to "work",
        "Freelance" to "laptop",
        "Business" to "store",
        "Bonus" to "redeem",
        "Gift" to "card_giftcard",
        "Refund" to "undo",
        "Interest" to "trending_up",
        "Other Income" to "category"
    ).mapIndexed { index, (name, icon) ->
        CategoryEntity(
            name = name,
            type = CategoryType.INCOME,
            icon = icon,
            colorHex = "#1E8E5A",
            sortOrder = index,
            isDefault = true
        )
    }

    // A small rotation of muted, distinguishable colors from the design system's
    // palette so default categories are visually distinct without looking random.
    private val expensePalette = listOf(
        "#C4432B", "#B8860B", "#5B6B76", "#7A5C99", "#2E7D91",
        "#946B4D", "#8A6D3B", "#4C6B8A", "#6B8A4C", "#8A4C6B"
    )

    private fun expenseColorFor(index: Int) = expensePalette[index % expensePalette.size]
}
