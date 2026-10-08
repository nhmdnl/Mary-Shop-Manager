package com.example.util

import android.content.Context
import com.example.data.model.DebtAgingSummary
import com.example.data.model.PartyStatementReport
import com.example.data.model.ProductProfitReport
import com.example.data.model.SalesReportSummary
import com.example.data.model.StockMovementReport
import java.io.File
import java.io.FileWriter
import java.util.Locale

object CsvExporter {

    private fun escapeCsv(value: Any?): String {
        if (value == null) return ""
        val str = value.toString().replace("\"", "\"\"")
        return if (str.contains(",") || str.contains("\n") || str.contains("\"")) {
            "\"$str\""
        } else {
            str
        }
    }

    private fun getCsvDir(context: Context): File {
        return File(context.cacheDir, "csv_exports").apply { mkdirs() }
    }

    fun exportSalesReportCsv(
        context: Context,
        report: SalesReportSummary,
        currency: String
    ): File {
        val file = File(getCsvDir(context), "sales_report_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            // Metadata
            writer.appendLine("MARY SHOP - SALES REPORT")
            writer.appendLine("Period,${escapeCsv(DateFormatter.formatDate(report.startDate))} to ${escapeCsv(DateFormatter.formatDate(report.endDate))}")
            writer.appendLine("Total Sales ($currency),${report.totalSales}")
            writer.appendLine("Total Profit ($currency),${report.totalProfit}")
            writer.appendLine("Profit Margin %,${String.format(Locale.US, "%.2f", report.profitMarginPercent)}")
            writer.appendLine("Sales Count,${report.salesCount}")
            writer.appendLine("Cash Sales ($currency),${report.cashSalesTotal}")
            writer.appendLine("Credit Sales ($currency),${report.creditSalesTotal}")
            writer.appendLine()

            // Header
            writer.appendLine("Invoice #,Date,Customer,Payment Mode,Subtotal ($currency),Discount ($currency),Final Total ($currency),Cost ($currency),Profit ($currency),Notes")

            // Rows
            for (sale in report.sales) {
                writer.appendLine(
                    listOf(
                        escapeCsv(sale.invoiceNumber),
                        escapeCsv(DateFormatter.formatDateTime(sale.date)),
                        escapeCsv(sale.customerName ?: "Cash Customer"),
                        escapeCsv(sale.paymentMode.label),
                        sale.subtotal,
                        sale.discountAmount,
                        sale.finalTotal,
                        sale.totalCost,
                        sale.profit,
                        escapeCsv(sale.notes)
                    ).joinToString(",")
                )
            }
        }
        return file
    }

    fun exportProfitPerProductCsv(
        context: Context,
        report: ProductProfitReport,
        currency: String
    ): File {
        val file = File(getCsvDir(context), "profit_per_product_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.appendLine("MARY SHOP - PROFIT PER PRODUCT REPORT")
            writer.appendLine("Period,${escapeCsv(DateFormatter.formatDate(report.startDate))} to ${escapeCsv(DateFormatter.formatDate(report.endDate))}")
            writer.appendLine("Total Revenue ($currency),${report.totalRevenue}")
            writer.appendLine("Total Cost ($currency),${report.totalCost}")
            writer.appendLine("Total Profit ($currency),${report.totalProfit}")
            writer.appendLine("Overall Margin %,${String.format(Locale.US, "%.2f", report.overallMarginPercent)}")
            writer.appendLine()

            writer.appendLine("Product Name,Category,Unit,Quantity Sold,Revenue ($currency),Cost ($currency),Profit ($currency),Margin %")

            for (item in report.items) {
                writer.appendLine(
                    listOf(
                        escapeCsv(item.productName),
                        escapeCsv(item.category),
                        escapeCsv(item.unit),
                        String.format(Locale.US, "%.2f", item.quantitySold),
                        item.totalRevenue,
                        item.totalCost,
                        item.totalProfit,
                        String.format(Locale.US, "%.2f", item.profitMarginPercent)
                    ).joinToString(",")
                )
            }
        }
        return file
    }

    fun exportPartyStatementCsv(
        context: Context,
        statement: PartyStatementReport,
        currency: String
    ): File {
        val file = File(getCsvDir(context), "statement_${statement.party.name.replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.appendLine("MARY SHOP - STATEMENT OF ACCOUNT")
            writer.appendLine("Party Name,${escapeCsv(statement.party.name)}")
            writer.appendLine("Party Phone,${escapeCsv(statement.party.phone)}")
            writer.appendLine("Party Type,${escapeCsv(statement.party.type.label)}")
            writer.appendLine("Statement Period,${escapeCsv(DateFormatter.formatDate(statement.startDate))} to ${escapeCsv(DateFormatter.formatDate(statement.endDate))}")
            writer.appendLine("Opening Balance ($currency),${statement.openingBalance}")
            writer.appendLine("Total Debits ($currency),${statement.totalDebits}")
            writer.appendLine("Total Credits ($currency),${statement.totalCredits}")
            writer.appendLine("Closing Balance Due ($currency),${statement.closingBalance}")
            writer.appendLine()

            writer.appendLine("Date,Reference No,Description,Debit ($currency),Credit ($currency),Running Balance ($currency)")

            for (tx in statement.transactions) {
                writer.appendLine(
                    listOf(
                        escapeCsv(DateFormatter.formatDateTime(tx.date)),
                        escapeCsv(tx.referenceNo),
                        escapeCsv(tx.description),
                        if (tx.debit > 0L) tx.debit else "",
                        if (tx.credit > 0L) tx.credit else "",
                        tx.runningBalance
                    ).joinToString(",")
                )
            }
        }
        return file
    }

    fun exportStockMovementsCsv(
        context: Context,
        report: StockMovementReport,
        currency: String
    ): File {
        val file = File(getCsvDir(context), "stock_movements_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.appendLine("MARY SHOP - STOCK MOVEMENTS REPORT")
            writer.appendLine("Period,${escapeCsv(DateFormatter.formatDate(report.startDate))} to ${escapeCsv(DateFormatter.formatDate(report.endDate))}")
            writer.appendLine("Total Movements,${report.totalMovementsCount}")
            writer.appendLine("Total Purchases In Qty,${String.format(Locale.US, "%.2f", report.totalPurchaseInQty)}")
            writer.appendLine("Total Sales Out Qty,${String.format(Locale.US, "%.2f", report.totalSaleOutQty)}")
            writer.appendLine("Total Damaged/Loss Qty,${String.format(Locale.US, "%.2f", report.totalDamageQty)}")
            writer.appendLine()

            writer.appendLine("Date,Product,Movement Type,Direction,Quantity,Unit,Unit Price ($currency),Resulting Stock,Party,Reference No,Note")

            for (m in report.movements) {
                writer.appendLine(
                    listOf(
                        escapeCsv(DateFormatter.formatDateTime(m.movement.date)),
                        escapeCsv(m.productName),
                        escapeCsv(m.movement.type.label),
                        if (m.movement.isStockIn) "IN (+)" else "OUT (-)",
                        String.format(Locale.US, "%.2f", m.movement.quantity),
                        escapeCsv(m.productUnit),
                        m.movement.unitPrice ?: "",
                        String.format(Locale.US, "%.2f", m.movement.resultingStock),
                        escapeCsv(m.partyName ?: ""),
                        escapeCsv(m.movement.referenceNo),
                        escapeCsv(m.movement.note)
                    ).joinToString(",")
                )
            }
        }
        return file
    }

    fun exportDebtAgingCsv(
        context: Context,
        aging: DebtAgingSummary,
        currency: String
    ): File {
        val file = File(getCsvDir(context), "debt_aging_report_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.appendLine("MARY SHOP - DEBT AGING REPORT")
            writer.appendLine("As Of Date,${escapeCsv(DateFormatter.formatDateTime(aging.asOfDate))}")
            writer.appendLine("Total Outstanding Receivables ($currency),${aging.totalReceivables}")
            writer.appendLine("0 - 30 Days (Current),${aging.bucket0To30}")
            writer.appendLine("31 - 60 Days (Due),${aging.bucket31To60}")
            writer.appendLine("61 - 90 Days (Overdue),${aging.bucket61To90}")
            writer.appendLine("90+ Days (Critical),${aging.bucket90Plus}")
            writer.appendLine("Debtors Count,${aging.debtorCount}")
            writer.appendLine()

            writer.appendLine("Customer Name,Phone,Address,Total Debt ($currency),0-30 Days,31-60 Days,61-90 Days,90+ Days,Oldest Unpaid Debt Date,Risk Level,Credit Limit ($currency)")

            for (debtor in aging.debtors) {
                writer.appendLine(
                    listOf(
                        escapeCsv(debtor.party.name),
                        escapeCsv(debtor.party.phone),
                        escapeCsv(debtor.party.address),
                        debtor.totalDebt,
                        debtor.amount0To30,
                        debtor.amount31To60,
                        debtor.amount61To90,
                        debtor.amount90Plus,
                        if (debtor.oldestDebtDate > 0L) escapeCsv(DateFormatter.formatDate(debtor.oldestDebtDate)) else "N/A",
                        escapeCsv(debtor.riskLevel.label),
                        debtor.party.creditLimit
                    ).joinToString(",")
                )
            }
        }
        return file
    }
}
