package com.moneta.app.ui.reports

import com.moneta.app.ui.common.PeriodSelection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot hand-off to the Reports page: another screen (Budgets, Records) sets which category it wants
 * to see analysed, then opens Reports, which reads it once and clears it.
 */
@Singleton
class ReportsLaunch @Inject constructor() {
    var categoryId: Long? = null
    var isIncome: Boolean = false
    var period: PeriodSelection? = null

    fun clear() {
        categoryId = null
        isIncome = false
        period = null
    }
}
