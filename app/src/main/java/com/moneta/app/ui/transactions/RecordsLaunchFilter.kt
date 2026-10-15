package com.moneta.app.ui.transactions

import com.moneta.app.ui.common.PeriodSelection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A one-shot hand-off: a screen (Home, Budgets, ...) sets what it wants Records
 * to show, then opens Records, which reads it once and clears it. This lets a
 * tap on a figure land on the matching list without putting filters in routes.
 */
@Singleton
class RecordsLaunchFilter @Inject constructor() {
    var type: TypeFilter? = null
    var period: PeriodSelection? = null
    var accountId: Long? = null
    var categoryIds: Set<Long>? = null
    var categoryTitle: String? = null

    fun clear() {
        type = null
        period = null
        accountId = null
        categoryIds = null
        categoryTitle = null
    }
}
