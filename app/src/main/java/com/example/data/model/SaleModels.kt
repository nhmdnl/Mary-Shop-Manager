package com.example.data.model

enum class PriceTier(val label: String) {
    RETAIL("Retail"),
    WHOLESALE("Wholesale"),
    CUSTOM("Custom")
}

enum class DiscountType(val label: String) {
    PERCENTAGE("Percentage (%)"),
    FIXED("Fixed Amount")
}

enum class SalePaymentMode(val label: String) {
    CASH("Cash"),
    CREDIT("Credit (Ledger)")
}

enum class BulkPriceTarget(val label: String) {
    RETAIL("Retail Price"),
    WHOLESALE("Wholesale Price"),
    BOTH_RETAIL_AND_WHOLESALE("Both Retail & Wholesale"),
    COST("Cost Price")
}

enum class BulkAdjustmentMode(val label: String) {
    PERCENTAGE("Percentage (%)"),
    FIXED_AMOUNT("Fixed Amount")
}

data class SaleLineItem(
    val product: Product,
    val quantity: Double,
    val priceTier: PriceTier,
    val unitPrice: Long,
    val customPrice: Long? = null
) {
    val subtotal: Long
        get() = (quantity * unitPrice).toLong()

    val totalCost: Long
        get() = (quantity * product.costPrice).toLong()

    val profit: Long
        get() = subtotal - totalCost
}

data class SaleReceipt(
    val invoiceNumber: String,
    val date: Long = System.currentTimeMillis(),
    val items: List<SaleLineItem>,
    val subtotal: Long,
    val discountType: DiscountType,
    val discountValue: Double,
    val discountAmount: Long,
    val taxPercentage: Double = 0.0,
    val taxAmount: Long = 0L,
    val finalTotal: Long,
    val paymentMode: SalePaymentMode,
    val cashTendered: Long = 0L,
    val changeDue: Long = 0L,
    val customer: Party? = null,
    val newCustomerBalance: Long? = null,
    val notes: String = ""
)
