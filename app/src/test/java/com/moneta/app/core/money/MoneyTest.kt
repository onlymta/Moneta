package com.moneta.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `addition sums minor units exactly`() {
        val a = Money(150_00L, "PKR")
        val b = Money(50_00L, "PKR")
        assertEquals(Money(200_00L, "PKR"), a + b)
    }

    @Test
    fun `subtraction can go negative`() {
        val a = Money(50_00L, "PKR")
        val b = Money(150_00L, "PKR")
        val result = a - b
        assertEquals(-100_00L, result.minorUnits)
        assertTrue(result.isNegative)
    }

    @Test
    fun `mixing currencies throws rather than silently combining`() {
        val pkr = Money(100_00L, "PKR")
        val usd = Money(100_00L, "USD")
        assertThrows(IllegalArgumentException::class.java) { pkr + usd }
    }

    @Test
    fun `percentOf returns null for a zero denominator instead of crashing or returning infinity`() {
        val spent = Money(100_00L, "PKR")
        val zeroBudget = Money.zero("PKR")
        assertNull(spent.percentOf(zeroBudget))
    }

    @Test
    fun `percentOf computes the expected percentage`() {
        val spent = Money(50_00L, "PKR")
        val budget = Money(200_00L, "PKR")
        assertEquals(25.0, spent.percentOf(budget)!!, 0.0001)
    }

    @Test
    fun `fromDecimalInput parses a plain decimal string into minor units`() {
        val money = Money.fromDecimalInput("1500.50", "PKR")
        assertEquals(150050L, money?.minorUnits)
    }

    @Test
    fun `fromDecimalInput rejects negative amounts`() {
        assertNull(Money.fromDecimalInput("-5", "PKR"))
    }

    @Test
    fun `fromDecimalInput rejects garbage input instead of throwing`() {
        assertNull(Money.fromDecimalInput("not a number", "PKR"))
    }

    @Test
    fun `fromDecimalInput handles a bare integer with no decimal point`() {
        val money = Money.fromDecimalInput("500", "PKR")
        assertEquals(50000L, money?.minorUnits)
    }

    @Test
    fun `sum of an empty list returns zero in the requested currency`() {
        val result = Money.sum(emptyList(), currencyIfEmpty = "PKR")
        assertEquals(Money.zero("PKR"), result)
    }

    @Test
    fun `comparison respects minor units`() {
        val small = Money(10_00L, "PKR")
        val large = Money(20_00L, "PKR")
        assertTrue(small < large)
        assertTrue(large > small)
    }
}
