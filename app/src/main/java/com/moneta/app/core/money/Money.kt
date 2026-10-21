package com.moneta.app.core.money

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * The single safe representation of a monetary amount used everywhere in Moneta.
 *
 * Per ARCHITECTURE.md Section 32 / Section 7: financial calculations must never
 * use Double/Float, since binary floating point cannot represent every decimal
 * currency value exactly and repeated add/subtract operations will drift over
 * time (a classic source of "my balance doesn't add up" bugs in finance apps).
 *
 * [minorUnits] is the amount expressed in the smallest unit of the currency
 * (e.g. cents for USD, paisa for PKR/INR) as a [Long]. All arithmetic is exact
 * integer arithmetic.
 *
 * Every place in the codebase that touches a transaction amount, an account
 * balance, a budget limit, or a goal target should hold a [Money], never a
 * raw Long/Double - this makes it a compile error to accidentally add two
 * amounts of different currencies or to slip a Double into the money path.
 */
data class Money(
    val minorUnits: Long,
    val currencyCode: String
) : Comparable<Money> {

    init {
        require(currencyCode.length == 3) {
            "currencyCode must be a 3-letter ISO 4217 code, was '$currencyCode'"
        }
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Cannot combine amounts in different currencies: $currencyCode vs ${other.currencyCode}. " +
                "Convert explicitly first if this is intentional (not currently supported in Phase 1)."
        }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits + other.minorUnits, currencyCode)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits - other.minorUnits, currencyCode)
    }

    operator fun unaryMinus(): Money = Money(-minorUnits, currencyCode)

    /** Multiplies by an integer scalar (e.g. splitting a recurring amount across N occurrences). */
    operator fun times(scalar: Int): Money = Money(minorUnits * scalar, currencyCode)

    val isNegative: Boolean get() = minorUnits < 0
    val isPositive: Boolean get() = minorUnits > 0
    val isZero: Boolean get() = minorUnits == 0L

    fun abs(): Money = Money(minorUnits.absoluteValue, currencyCode)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    /**
     * Percentage of [other] that this amount represents, e.g. spent.percentOf(budget).
     * Returns null if [other] is zero to avoid a division-by-zero crash - callers
     * (e.g. budget progress UI) must handle "no budget set" explicitly rather than
     * seeing a NaN or Infinity.
     */
    fun percentOf(other: Money): Double? {
        requireSameCurrency(other)
        if (other.minorUnits == 0L) return null
        return (minorUnits.toDouble() / other.minorUnits.toDouble()) * 100.0
    }

    /**
     * Formats using the platform currency formatter for display only.
     * This is the ONLY place Double is allowed to appear in the money path,
     * because [NumberFormat] requires it - it is used purely for rendering,
     * never for further arithmetic on the result.
     */
    fun formatted(locale: Locale = Locale.getDefault()): String {
        // The label comes from the user's display-currency setting; the number itself is unchanged.
        // Building a NumberFormat is slow, so one is kept per thread and rebuilt only when the
        // currency, symbol setting or language changes (lists format many amounts per frame).
        val code = DisplayCurrency.code
        val showSymbol = DisplayCurrency.showSymbol
        val decimals = DisplayCurrency.decimals
        val key = locale.toString() + "|" + code + "|" + showSymbol + "|" + decimals
        val holder = formatCache.get() ?: FormatHolder().also { formatCache.set(it) }
        if (holder.key != key || holder.format == null) {
            val currency = runCatching { Currency.getInstance(code) }.getOrNull()
            val fractionDigits = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
            holder.divisor = Math.pow(10.0, fractionDigits.toDouble())
            holder.format = if (!showSymbol) {
                NumberFormat.getNumberInstance(locale).apply {
                    minimumFractionDigits = decimals
                    maximumFractionDigits = decimals
                }
            } else {
                NumberFormat.getCurrencyInstance(locale).apply {
                    if (currency != null) setCurrency(currency)
                    minimumFractionDigits = decimals
                    maximumFractionDigits = decimals
                }
            }
            holder.key = key
        }
        return holder.format?.format(minorUnits / holder.divisor).orEmpty()
    }

    /**
     * Formats this amount as a plain decimal string suitable for re-populating an
     * editable amount field (no currency symbol/grouping) - e.g. 150000 minor units
     * of a 2-decimal currency becomes "1500.00". Kept separate from [formatted],
     * which is presentation-only and includes locale currency symbols/grouping.
     */
    fun toEditableDecimalString(fractionDigits: Int = 2): String {
        val divisor = Math.pow(10.0, fractionDigits.toDouble())
        val value = minorUnits / divisor
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    companion object {
        fun zero(currencyCode: String) = Money(0L, currencyCode)

        /** Sums a list of same-currency amounts safely; empty list needs an explicit currency to default to. */
        fun sum(amounts: List<Money>, currencyIfEmpty: String): Money {
            if (amounts.isEmpty()) return zero(currencyIfEmpty)
            return amounts.reduce { acc, money -> acc + money }
        }

        /**
         * Constructs a [Money] from a user-typed decimal string (e.g. from the Quick Add
         * numeric keypad), given how many fraction digits the currency uses. Returns null
         * for invalid/unparseable input rather than throwing, so the UI can show a friendly
         * validation error (Section 31) instead of crashing on bad user input.
         */
        fun fromDecimalInput(input: String, currencyCode: String, fractionDigits: Int = 2): Money? {
            val normalized = input.trim().replace(",", "")
            if (normalized.isEmpty()) return null
            val decimal = normalized.toBigDecimalOrNull() ?: return null
            if (decimal.signum() < 0) return null
            val scaled = decimal.movePointRight(fractionDigits)
            val minorUnits = scaled.toLongOrNullExact() ?: return null
            return Money(minorUnits, currencyCode)
        }

        private fun String.toBigDecimalOrNull(): java.math.BigDecimal? =
            runCatching { java.math.BigDecimal(this) }.getOrNull()

        private fun java.math.BigDecimal.toLongOrNullExact(): Long? =
            runCatching { this.longValueExact() }.getOrNull()
                ?: runCatching { this.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }
}

private class FormatHolder {
    var key: String = ""
    var format: NumberFormat? = null
    var divisor: Double = 100.0
}

private val formatCache = ThreadLocal<FormatHolder>()
