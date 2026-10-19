package com.moneta.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A tiny calculator for the amount field: numbers joined with + - × ÷, with the usual
 * order of operations. Used so "250+80×2" can be typed on the in-app keypad and saved as 410.
 */
object AmountCalculator {

    private const val OPERATORS = "+-×÷*/"

    fun isOperator(ch: Char): Boolean = ch in OPERATORS

    fun hasOperator(input: String): Boolean = input.any { isOperator(it) }

    /** The value of [expression], or null when it is incomplete or invalid (e.g. "5+" or "8÷0"). */
    fun evaluate(expression: String): BigDecimal? {
        val s = expression.replace(" ", "").replace('*', '×').replace('/', '÷')
        if (s.isEmpty()) return null
        val parser = Parser(s)
        val value = parser.parseExpression() ?: return null
        if (parser.pos != s.length) return null
        return value
    }

    /** The value as a plain decimal string with at most 2 decimals, or null if it can't be evaluated. */
    fun evaluateToPlainString(expression: String): String? =
        evaluate(expression)?.setScale(2, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString()

    private class Parser(private val s: String) {
        var pos = 0

        fun parseExpression(): BigDecimal? {
            var left = parseTerm() ?: return null
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos]
                pos += 1
                val right = parseTerm() ?: return null
                left = if (op == '+') left.add(right) else left.subtract(right)
            }
            return left
        }

        private fun parseTerm(): BigDecimal? {
            var left = parseNumber() ?: return null
            while (pos < s.length && (s[pos] == '×' || s[pos] == '÷')) {
                val op = s[pos]
                pos += 1
                val right = parseNumber() ?: return null
                left = if (op == '×') {
                    left.multiply(right)
                } else {
                    if (right.signum() == 0) return null
                    left.divide(right, 6, RoundingMode.HALF_UP)
                }
            }
            return left
        }

        private fun parseNumber(): BigDecimal? {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos += 1
            if (start == pos) return null
            return s.substring(start, pos).toBigDecimalOrNull()
        }
    }
}
