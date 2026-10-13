package com.moneta.app.domain.insights

import javax.inject.Inject

/**
 * Runs [ALL_INSIGHT_RULES] against a gathered [InsightContext] and returns
 * only the rules that had enough data to say something (Section 19). Budget
 * risks and multi-month warnings are ranked above routine trend statements
 * (Section 20: "prioritized/sorted before being shown as a feed of cards").
 */
class InsightsEngine @Inject constructor() {

    fun generate(context: InsightContext, rules: List<InsightRule> = ALL_INSIGHT_RULES): List<Insight> =
        rules.mapNotNull { it.evaluate(context) }
            .sortedByDescending { it.severity.ordinal }
}
