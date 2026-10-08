package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales",
    indices = [
        Index("date"),
        Index("customerId"),
        Index("invoiceNumber", unique = true)
    ]
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val invoiceNumber: String,
    val date: Long = System.currentTimeMillis(),
    val customerId: Long? = null,
    val customerName: String? = null,
    val subtotal: Long,
    val discountType: DiscountType = DiscountType.FIXED,
    val discountValue: Double = 0.0,
    val discountAmount: Long = 0L,
    val finalTotal: Long,
    val totalCost: Long = 0L,
    val profit: Long = 0L,
    val paymentMode: SalePaymentMode = SalePaymentMode.CASH,
    val cashTendered: Long = 0L,
    val changeDue: Long = 0L,
    val notes: String = ""
)
