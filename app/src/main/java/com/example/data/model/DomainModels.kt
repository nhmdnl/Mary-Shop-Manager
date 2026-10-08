package com.example.data.model

data class LedgerEntry(
    val transaction: TransactionEntity,
    val delta: Long,
    val runningBalance: Long,
    val isOverLimit: Boolean
)

data class TransactionWithParty(
    val transaction: TransactionEntity,
    val partyName: String,
    val partyType: PartyType
)

data class PartyWithBalance(
    val party: Party,
    val balance: Long, // Customer positive = they owe me; Supplier positive = I owe them
    val receivables: Long, // What they owe me (if positive customer balance)
    val payables: Long,    // What I owe them (if positive supplier balance)
    val lastActivityDate: Long,
    val isOverCreditLimit: Boolean,
    val transactionCount: Int
)

data class DashboardSummary(
    val todaySales: Long = 0L,
    val todayCashIn: Long = 0L,
    val todayCashOut: Long = 0L,
    val todayProfit: Long = 0L,
    val todaySalesCount: Int = 0,
    val totalReceivables: Long = 0L,
    val totalPayables: Long = 0L,
    val netPosition: Long = 0L,
    val stockValueAtCost: Long = 0L,
    val stockValueAtRetail: Long = 0L,
    val potentialProfit: Long = 0L,
    val partiesOverCreditLimit: List<PartyWithBalance> = emptyList(),
    val recentTransactions: List<TransactionWithParty> = emptyList(),
    val totalCustomerCount: Int = 0,
    val totalSupplierCount: Int = 0,
    val lowStockCount: Int = 0
)

data class ProductWithSupplier(
    val product: Product,
    val supplier: Party? = null,
    val isLowStock: Boolean = product.stockQty <= product.reorderLevel,
    val isOutOfStock: Boolean = product.stockQty <= 0.0
) {
    val marginPercent: Double
        get() = if (product.costPrice > 0L) {
            ((product.retailPrice - product.costPrice).toDouble() / product.costPrice.toDouble()) * 100.0
        } else 0.0
}

data class StockMovementWithDetails(
    val movement: StockMovement,
    val productName: String,
    val productUnit: String,
    val partyName: String? = null
)

data class InventorySummary(
    val totalProductsCount: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val totalStockValueAtCost: Long = 0L,
    val totalStockValueAtRetail: Long = 0L,
    val categories: List<String> = emptyList()
)
