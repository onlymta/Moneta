package com.moneta.app.domain.usecase

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.repository.CategoryRepository
import javax.inject.Inject

/** One slice of a spending-or-income-by-category breakdown, with display-ready name/color resolved. */
data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val colorHex: String,
    val amount: Money,
    val percentOfTotal: Double
)

/**
 * Resolves raw [CategoryTotal] rows (category id + amount) from
 * TransactionDao into display-ready [CategorySlice]s. This is the ONE place
 * that logic lives - both HomeViewModel and ReportsViewModel call this
 * rather than each re-deriving names/colors/percentages themselves
 * (ARCHITECTURE.md Section 46: no duplicate business logic).
 */
class CategoryBreakdownUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(totals: List<CategoryTotal>, total: Money): List<CategorySlice> {
        if (total.isZero) return emptyList()
        return totals
            .filter { it.totalMinor > 0 }
            .mapNotNull { row ->
                val category = row.categoryId?.let { categoryRepository.getById(it) } ?: return@mapNotNull null
                val amount = Money(row.totalMinor, total.currencyCode)
                CategorySlice(
                    categoryId = category.id,
                    name = category.name,
                    colorHex = category.colorHex,
                    amount = amount,
                    percentOfTotal = amount.percentOf(total) ?: 0.0
                )
            }
            .sortedByDescending { it.amount.minorUnits }
    }
}
