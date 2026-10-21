package com.moneta.app.core.money

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How amounts are labelled on screen: which currency, and whether the symbol/code is shown at all.
 * This is Compose state, so every screen that formats a [Money] updates when the user changes it.
 * Stored amounts are never touched - only the label changes.
 */
object DisplayCurrency {
    var code: String by mutableStateOf("PKR")
    var showSymbol: Boolean by mutableStateOf(true)

    /** Decimals shown on amounts (0, 1 or 2). */
    var decimals: Int by mutableStateOf(2)

    /** Currency choices offered in Settings: code to name. */
    val choices: List<Pair<String, String>> = listOf(
        "PKR" to "Pakistani rupee",
        "USD" to "US dollar",
        "EUR" to "Euro",
        "GBP" to "British pound",
        "INR" to "Indian rupee",
        "BDT" to "Bangladeshi taka",
        "AED" to "UAE dirham",
        "SAR" to "Saudi riyal",
        "QAR" to "Qatari riyal",
        "KWD" to "Kuwaiti dinar",
        "TRY" to "Turkish lira",
        "EGP" to "Egyptian pound",
        "MYR" to "Malaysian ringgit",
        "SGD" to "Singapore dollar",
        "CAD" to "Canadian dollar",
        "AUD" to "Australian dollar",
        "JPY" to "Japanese yen",
        "CNY" to "Chinese yuan"
    )

    /** A short label for amount entry fields, e.g. "PKR " or "" when symbols are hidden. */
    fun entryPrefix(): String = if (showSymbol) "$code " else ""
}
