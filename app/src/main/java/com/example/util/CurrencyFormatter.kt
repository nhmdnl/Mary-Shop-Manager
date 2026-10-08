package com.example.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    /**
     * Formats an amount stored as Long into a clean currency string.
     * Example: 150000L, "UGX" -> "UGX 150,000"
     */
    fun format(amount: Long, currency: String = "UGX", showSign: Boolean = false): String {
        val absVal = kotlin.math.abs(amount)
        val formattedNumber = numberFormat.format(absVal)
        return when {
            amount < 0 -> "-$currency $formattedNumber"
            amount > 0 && showSign -> "+$currency $formattedNumber"
            else -> "$currency $formattedNumber"
        }
    }

    /**
     * Parse user text input into a Long amount safely.
     * Disallows decimals or handles them as whole units for zero-decimal currencies like UGX.
     */
    fun parseAmount(input: String): Long? {
        val clean = input.filter { it.isDigit() }
        return clean.toLongOrNull()
    }
}
