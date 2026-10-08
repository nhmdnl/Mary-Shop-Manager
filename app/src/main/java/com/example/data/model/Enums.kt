package com.example.data.model

enum class PartyType(val label: String) {
    CUSTOMER("Customer"),
    SUPPLIER("Supplier"),
    BOTH("Customer & Supplier")
}

enum class TransactionType(val label: String) {
    SALE_ON_CREDIT("Sale on Credit"),
    PURCHASE_ON_CREDIT("Purchase on Credit"),
    PAYMENT_RECEIVED("Payment Received"),
    PAYMENT_MADE("Payment Made"),
    ADJUSTMENT("Adjustment")
}

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    MOBILE_MONEY("Mobile Money"),
    BANK("Bank"),
    OTHER("Other")
}

enum class AdjustmentDirection(val direction: Int, val label: String) {
    INCREASE_OWED(1, "Increase Amount Owed"),
    DECREASE_OWED(-1, "Decrease Amount Owed")
}

enum class StockMovementType(val label: String, val defaultIsAddition: Boolean) {
    PURCHASE_IN("Purchase in", true),
    SALE_OUT("Sale out", false),
    RETURN("Return", true),
    DAMAGE_LOSS("Damage / Loss", false),
    ADJUSTMENT("Adjustment", true)
}
