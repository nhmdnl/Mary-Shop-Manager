package com.example

import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.util.CurrencyFormatter
import com.example.util.ReminderHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun currencyFormatting_handlesLongCorrectly() {
        assertEquals("UGX 150,000", CurrencyFormatter.format(150000L, "UGX"))
        assertEquals("-UGX 50,000", CurrencyFormatter.format(-50000L, "UGX"))
        assertEquals("UGX 0", CurrencyFormatter.format(0L, "UGX"))
        assertEquals("+UGX 25,000", CurrencyFormatter.format(25000L, "UGX", showSign = true))
    }

    @Test
    fun reminderHelper_generatesMessage() {
        val message = ReminderHelper.generateMessage(
            partyName = "Sarah",
            formattedBalance = "UGX 280,000",
            shopName = "Mary Shop",
            shopPhone = "+256 700 123456"
        )
        assertTrue(message.contains("Sarah"))
        assertTrue(message.contains("UGX 280,000"))
        assertTrue(message.contains("Mary Shop"))
    }
}
