package com.example.data.model

import java.util.Calendar

enum class ReportTab(val title: String) {
    SALES("Sales"),
    PROFIT("Profit / Product"),
    STATEMENT("Party Statement"),
    STOCK_MOVEMENT("Stock Movement"),
    DEBT_AGING("Debt Aging")
}

enum class DateRangeType(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    ALL_TIME("All Time"),
    CUSTOM("Custom")
}

data class DateRangeSelection(
    val type: DateRangeType = DateRangeType.THIS_MONTH,
    val customStart: Long = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L,
    val customEnd: Long = System.currentTimeMillis()
) {
    val startTimestamp: Long
        get() = when (type) {
            DateRangeType.TODAY -> startOfDay(System.currentTimeMillis())
            DateRangeType.YESTERDAY -> startOfDay(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
            DateRangeType.THIS_WEEK -> {
                val cal = Calendar.getInstance()
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                startOfDay(cal.timeInMillis)
            }
            DateRangeType.THIS_MONTH -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                startOfDay(cal.timeInMillis)
            }
            DateRangeType.LAST_30_DAYS -> startOfDay(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L)
            DateRangeType.ALL_TIME -> 0L
            DateRangeType.CUSTOM -> startOfDay(customStart)
        }

    val endTimestamp: Long
        get() = when (type) {
            DateRangeType.TODAY -> endOfDay(System.currentTimeMillis())
            DateRangeType.YESTERDAY -> endOfDay(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
            DateRangeType.THIS_WEEK -> endOfDay(System.currentTimeMillis())
            DateRangeType.THIS_MONTH -> endOfDay(System.currentTimeMillis())
            DateRangeType.LAST_30_DAYS -> endOfDay(System.currentTimeMillis())
            DateRangeType.ALL_TIME -> Long.MAX_VALUE
            DateRangeType.CUSTOM -> endOfDay(customEnd)
        }

    companion object {
        fun startOfDay(time: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun endOfDay(time: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return cal.timeInMillis
        }
    }
}

// -------------------------------------------------------------
// 1. Sales Report
// -------------------------------------------------------------
data class SalesReportSummary(
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val totalSales: Long = 0L,
    val totalProfit: Long = 0L,
    val totalCost: Long = 0L,
    val salesCount: Int = 0,
    val cashSalesTotal: Long = 0L,
    val creditSalesTotal: Long = 0L,
    val cashSalesCount: Int = 0,
    val creditSalesCount: Int = 0,
    val totalDiscounts: Long = 0L,
    val averageSaleValue: Long = 0L,
    val profitMarginPercent: Double = 0.0,
    val sales: List<SaleEntity> = emptyList()
)

// -------------------------------------------------------------
// 2. Profit Per Product Report
// -------------------------------------------------------------
data class ProductProfitItem(
    val productId: Long,
    val productName: String,
    val category: String,
    val unit: String,
    val quantitySold: Double,
    val totalRevenue: Long,
    val totalCost: Long,
    val totalProfit: Long,
    val profitMarginPercent: Double
)

data class ProductProfitReport(
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val totalRevenue: Long = 0L,
    val totalCost: Long = 0L,
    val totalProfit: Long = 0L,
    val overallMarginPercent: Double = 0.0,
    val totalUnitsSold: Double = 0.0,
    val items: List<ProductProfitItem> = emptyList()
)

// -------------------------------------------------------------
// 3. Party Statement Report
// -------------------------------------------------------------
data class StatementTransactionItem(
    val transaction: TransactionEntity,
    val date: Long,
    val referenceNo: String,
    val description: String,
    val debit: Long,       // Increase party debt/credit
    val credit: Long,      // Decrease party debt/credit (e.g. payment)
    val runningBalance: Long
)

data class PartyStatementReport(
    val party: Party,
    val startDate: Long,
    val endDate: Long,
    val openingBalance: Long,
    val transactions: List<StatementTransactionItem> = emptyList(),
    val totalDebits: Long = 0L,
    val totalCredits: Long = 0L,
    val closingBalance: Long = 0L,
    val netChange: Long = 0L
)

// -------------------------------------------------------------
// 4. Stock Movement Report
// -------------------------------------------------------------
data class StockMovementReport(
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val movements: List<StockMovementWithDetails> = emptyList(),
    val totalMovementsCount: Int = 0,
    val totalPurchaseInQty: Double = 0.0,
    val totalSaleOutQty: Double = 0.0,
    val totalReturnQty: Double = 0.0,
    val totalDamageQty: Double = 0.0,
    val totalAdjustmentQty: Double = 0.0
)

// -------------------------------------------------------------
// 5. Debt Aging Report (0–30 / 31–60 / 61–90 / 90+)
// -------------------------------------------------------------
enum class DebtRiskLevel(val label: String) {
    CURRENT("Current"),
    ATTENTION("31-60d Due"),
    HIGH_RISK("61-90d Overdue"),
    CRITICAL("90d+ Critical")
}

data class DebtorAgingRow(
    val party: Party,
    val totalDebt: Long,
    val amount0To30: Long,
    val amount31To60: Long,
    val amount61To90: Long,
    val amount90Plus: Long,
    val oldestDebtDate: Long,
    val riskLevel: DebtRiskLevel
)

data class DebtAgingSummary(
    val asOfDate: Long = System.currentTimeMillis(),
    val totalReceivables: Long = 0L,
    val bucket0To30: Long = 0L,
    val bucket31To60: Long = 0L,
    val bucket61To90: Long = 0L,
    val bucket90Plus: Long = 0L,
    val debtorCount: Int = 0,
    val debtors: List<DebtorAgingRow> = emptyList()
)
