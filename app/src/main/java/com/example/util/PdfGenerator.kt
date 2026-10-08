package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.PartyStatementReport
import com.example.data.model.PartyType
import com.example.data.model.SalePaymentMode
import com.example.data.model.SaleReceipt
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

object PdfGenerator {

    private fun formatMoney(amount: Long, currency: String): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
        return "$currency $formatted"
    }

    fun generateReceiptPdf(
        context: Context,
        receipt: SaleReceipt,
        shopName: String,
        shopPhone: String,
        currency: String
    ): File {
        val pdfDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val sanitizedInvoice = receipt.invoiceNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(pdfDir, "receipt_$sanitizedInvoice.pdf")

        val pageWidth = 420
        // Calculate dynamic height based on item count
        val baseHeight = 520
        val itemsHeight = (receipt.items.size * 32).coerceAtLeast(32)
        val pageHeight = (baseHeight + itemsHeight).coerceAtLeast(600)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 12f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 60, 120) // Deep modern navy
            textSize = 18f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val subTitlePaint = Paint().apply {
            color = Color.rgb(90, 100, 110)
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val boldPaint = Paint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 12f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val linePaint = Paint().apply {
            color = Color.rgb(220, 224, 230)
            strokeWidth = 1f
        }

        val dashLinePaint = Paint().apply {
            color = Color.rgb(180, 190, 200)
            strokeWidth = 1.2f
        }

        var y = 35f
        val centerX = pageWidth / 2f
        val leftX = 24f
        val rightX = pageWidth - 24f

        // Top decorative accent bar
        val barPaint = Paint().apply { color = Color.rgb(20, 60, 120) }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f, barPaint)

        // 1. Shop Header
        canvas.drawText(shopName.ifBlank { "Mary Shop" }, centerX, y, titlePaint)
        y += 16f
        if (shopPhone.isNotBlank()) {
            canvas.drawText("Tel: $shopPhone", centerX, y, subTitlePaint)
            y += 14f
        }
        val headerTagPaint = Paint().apply {
            color = Color.rgb(100, 110, 120)
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("SALES RECEIPT", centerX, y, headerTagPaint)
        y += 16f

        // Divider
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 18f

        // 2. Receipt Metadata
        textPaint.textSize = 11f
        canvas.drawText("Invoice #:", leftX, y, textPaint)
        canvas.drawText(receipt.invoiceNumber, leftX + 65f, y, boldPaint)

        val dateStr = DateFormatter.formatDateTime(receipt.date)
        val dateWidth = textPaint.measureText(dateStr)
        canvas.drawText(dateStr, rightX - dateWidth, y, textPaint)
        y += 16f

        canvas.drawText("Customer:", leftX, y, textPaint)
        val customerName = receipt.customer?.name ?: "Cash / Walk-in Customer"
        canvas.drawText(customerName, leftX + 65f, y, boldPaint)

        val modeStr = "Payment: ${receipt.paymentMode.label}"
        val modeWidth = textPaint.measureText(modeStr)
        canvas.drawText(modeStr, rightX - modeWidth, y, textPaint)
        y += 20f

        // Divider
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 18f

        // 3. Table Header
        val headerBgPaint = Paint().apply { color = Color.rgb(245, 247, 250) }
        canvas.drawRect(leftX, y - 14f, rightX, y + 6f, headerBgPaint)

        val thPaint = Paint().apply {
            color = Color.rgb(60, 70, 80)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("ITEM", leftX + 4f, y, thPaint)
        canvas.drawText("QTY", leftX + 175f, y, thPaint)
        canvas.drawText("PRICE", leftX + 225f, y, thPaint)
        val thTotal = "TOTAL"
        canvas.drawText(thTotal, rightX - thPaint.measureText(thTotal) - 4f, y, thPaint)
        y += 20f

        // 4. Line Items
        val rowPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 10.5f
            isAntiAlias = true
        }

        for (item in receipt.items) {
            val itemName = if (item.product.name.length > 22) item.product.name.take(20) + "…" else item.product.name
            canvas.drawText(itemName, leftX + 4f, y, rowPaint)

            val qtyStr = "${formatQty(item.quantity)} ${item.product.unit}"
            canvas.drawText(qtyStr, leftX + 175f, y, rowPaint)

            val priceStr = NumberFormat.getNumberInstance(Locale.US).format(item.unitPrice)
            canvas.drawText(priceStr, leftX + 225f, y, rowPaint)

            val totalStr = NumberFormat.getNumberInstance(Locale.US).format(item.subtotal)
            canvas.drawText(totalStr, rightX - rowPaint.measureText(totalStr) - 4f, y, rowPaint)

            y += 20f
        }

        y += 4f
        canvas.drawLine(leftX, y, rightX, y, dashLinePaint)
        y += 18f

        // 5. Financial Summary
        val summaryLabelPaint = Paint().apply {
            color = Color.rgb(90, 95, 105)
            textSize = 11f
            isAntiAlias = true
        }
        val summaryValuePaint = Paint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 11f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun drawSummaryRow(label: String, value: String, isBold: Boolean = false) {
            val pLabel = if (isBold) boldPaint else summaryLabelPaint
            val pVal = if (isBold) boldPaint else summaryValuePaint
            canvas.drawText(label, leftX + 130f, y, pLabel)
            val valWidth = pVal.measureText(value)
            canvas.drawText(value, rightX - valWidth - 4f, y, pVal)
            y += 17f
        }

        drawSummaryRow("Subtotal:", formatMoney(receipt.subtotal, currency))

        if (receipt.discountAmount > 0L) {
            drawSummaryRow("Discount:", "-${formatMoney(receipt.discountAmount, currency)}")
        }

        if (receipt.taxAmount > 0L) {
            drawSummaryRow("Tax (${receipt.taxPercentage}%):", "+${formatMoney(receipt.taxAmount, currency)}")
        }

        // Grand Total Box
        y += 4f
        val totalBoxPaint = Paint().apply { color = Color.rgb(238, 244, 255) }
        canvas.drawRect(leftX + 120f, y - 14f, rightX, y + 14f, totalBoxPaint)

        val grandTotalLabelPaint = Paint().apply {
            color = Color.rgb(20, 60, 120)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val grandTotalValPaint = Paint().apply {
            color = Color.rgb(20, 60, 120)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("TOTAL DUE:", leftX + 130f, y + 2f, grandTotalLabelPaint)
        val grandTotalStr = formatMoney(receipt.finalTotal, currency)
        canvas.drawText(grandTotalStr, rightX - grandTotalValPaint.measureText(grandTotalStr) - 4f, y + 2f, grandTotalValPaint)
        y += 28f

        // Payment Breakdown
        if (receipt.paymentMode == SalePaymentMode.CASH) {
            if (receipt.cashTendered > 0L) {
                drawSummaryRow("Cash Tendered:", formatMoney(receipt.cashTendered, currency))
                drawSummaryRow("Change Due:", formatMoney(receipt.changeDue, currency))
            }
        } else {
            drawSummaryRow("Mode:", "Credit Sale (Unpaid)")
            receipt.newCustomerBalance?.let { bal ->
                drawSummaryRow("Customer Balance:", formatMoney(bal, currency))
            }
        }

        if (receipt.notes.isNotBlank()) {
            y += 6f
            canvas.drawText("Notes: ${receipt.notes}", leftX, y, summaryLabelPaint)
            y += 16f
        }

        y += 12f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 22f

        // 6. Footer
        val footerPaint = Paint().apply {
            color = Color.rgb(120, 130, 140)
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Thank you for your business!", centerX, y, footerPaint)
        y += 14f
        canvas.drawText("Mary Shop Management System", centerX, y, footerPaint)

        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    fun generatePartyStatementPdf(
        context: Context,
        statement: PartyStatementReport,
        shopName: String,
        shopPhone: String,
        currency: String
    ): File {
        val pdfDir = File(context.cacheDir, "statements").apply { mkdirs() }
        val sanitizedParty = statement.party.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(pdfDir, "statement_${sanitizedParty}_${System.currentTimeMillis()}.pdf")

        val pageWidth = 595 // A4 standard width in points
        val baseHeight = 560
        val txHeight = (statement.transactions.size * 26).coerceAtLeast(26)
        val pageHeight = (baseHeight + txHeight).coerceAtLeast(842)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        val leftX = 36f
        val rightX = pageWidth - 36f
        var y = 44f

        // Accent top bar
        val barPaint = Paint().apply { color = Color.rgb(20, 60, 120) }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 8f, barPaint)

        // 1. Header (Shop Name + Statement Title)
        val titlePaint = Paint().apply {
            color = Color.rgb(20, 60, 120)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(shopName.ifBlank { "Mary Shop" }, leftX, y, titlePaint)

        val stmtTitlePaint = Paint().apply {
            color = Color.rgb(80, 90, 100)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val typeLabel = when (statement.party.type) {
            PartyType.CUSTOMER -> "CUSTOMER STATEMENT"
            PartyType.SUPPLIER -> "SUPPLIER STATEMENT"
            PartyType.BOTH -> "PARTY ACCOUNT STATEMENT"
        }
        canvas.drawText(typeLabel, rightX, y, stmtTitlePaint)
        y += 18f

        val subPaint = Paint().apply {
            color = Color.rgb(100, 110, 120)
            textSize = 10f
            isAntiAlias = true
        }
        if (shopPhone.isNotBlank()) {
            canvas.drawText("Tel: $shopPhone", leftX, y, subPaint)
        }

        val periodStr = "Period: ${DateFormatter.formatDate(statement.startDate)} - ${DateFormatter.formatDate(statement.endDate)}"
        val rightSubPaint = Paint(subPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(periodStr, rightX, y, rightSubPaint)
        y += 18f

        // Divider
        val linePaint = Paint().apply {
            color = Color.rgb(215, 220, 228)
            strokeWidth = 1f
        }
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 24f

        // 2. Party Information Box
        val infoBoxPaint = Paint().apply { color = Color.rgb(248, 250, 252) }
        canvas.drawRect(leftX, y - 10f, rightX, y + 54f, infoBoxPaint)

        val boldInfoPaint = Paint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val regularInfoPaint = Paint().apply {
            color = Color.rgb(60, 65, 75)
            textSize = 10.5f
            isAntiAlias = true
        }

        canvas.drawText("Statement For:", leftX + 12f, y + 6f, subPaint)
        canvas.drawText(statement.party.name, leftX + 12f, y + 24f, boldInfoPaint)
        if (statement.party.phone.isNotBlank()) {
            canvas.drawText("Phone: ${statement.party.phone}", leftX + 12f, y + 40f, regularInfoPaint)
        }
        if (statement.party.address.isNotBlank()) {
            canvas.drawText("Address: ${statement.party.address}", leftX + 220f, y + 24f, regularInfoPaint)
        }
        if (statement.party.creditLimit > 0L) {
            canvas.drawText("Credit Limit: ${formatMoney(statement.party.creditLimit, currency)}", leftX + 220f, y + 40f, regularInfoPaint)
        }
        y += 74f

        // 3. Summary KPIs (4 Cards in a row)
        val kpiWidth = (rightX - leftX - 24f) / 4f
        val kpiBgPaint = Paint().apply { color = Color.rgb(240, 244, 250) }

        fun drawKpiCard(x: Float, label: String, amount: Long, highlight: Boolean = false) {
            val bg = if (highlight) Color.rgb(230, 240, 255) else Color.rgb(245, 247, 250)
            kpiBgPaint.color = bg
            canvas.drawRect(x, y, x + kpiWidth, y + 46f, kpiBgPaint)

            val lblP = Paint().apply {
                color = Color.rgb(100, 110, 120)
                textSize = 9f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valP = Paint().apply {
                color = if (highlight) Color.rgb(20, 60, 140) else Color.rgb(30, 35, 45)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            canvas.drawText(label, x + 8f, y + 16f, lblP)
            canvas.drawText(formatMoney(amount, currency), x + 8f, y + 36f, valP)
        }

        drawKpiCard(leftX, "OPENING BALANCE", statement.openingBalance)
        drawKpiCard(leftX + kpiWidth + 8f, "TOTAL DEBITS", statement.totalDebits)
        drawKpiCard(leftX + (kpiWidth + 8f) * 2, "TOTAL CREDITS", statement.totalCredits)
        drawKpiCard(leftX + (kpiWidth + 8f) * 3, "CLOSING BALANCE", statement.closingBalance, highlight = true)
        y += 66f

        // 4. Transactions Table Header
        val thBgPaint = Paint().apply { color = Color.rgb(30, 45, 70) }
        canvas.drawRect(leftX, y, rightX, y + 22f, thBgPaint)

        val thWhitePaint = Paint().apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("DATE", leftX + 8f, y + 15f, thWhitePaint)
        canvas.drawText("REF / INVOICE", leftX + 85f, y + 15f, thWhitePaint)
        canvas.drawText("DESCRIPTION", leftX + 175f, y + 15f, thWhitePaint)
        canvas.drawText("DEBIT (+)", leftX + 335f, y + 15f, thWhitePaint)
        canvas.drawText("CREDIT (-)", leftX + 410f, y + 15f, thWhitePaint)
        val thBal = "BALANCE"
        canvas.drawText(thBal, rightX - thWhitePaint.measureText(thBal) - 8f, y + 15f, thWhitePaint)
        y += 24f

        // Table Rows
        val rowTextPaint = Paint().apply {
            color = Color.rgb(40, 45, 55)
            textSize = 9.5f
            isAntiAlias = true
        }
        val zebraPaint = Paint().apply { color = Color.rgb(250, 251, 253) }

        var isEven = false
        for (item in statement.transactions) {
            if (isEven) {
                canvas.drawRect(leftX, y, rightX, y + 20f, zebraPaint)
            }
            isEven = !isEven

            val rowY = y + 14f
            val dateStr = DateFormatter.formatDate(item.date)
            canvas.drawText(dateStr, leftX + 8f, rowY, rowTextPaint)

            val refStr = if (item.referenceNo.isNotBlank()) item.referenceNo else "-"
            canvas.drawText(refStr, leftX + 85f, rowY, rowTextPaint)

            val desc = if (item.description.length > 25) item.description.take(23) + "…" else item.description
            canvas.drawText(desc, leftX + 175f, rowY, rowTextPaint)

            val debitStr = if (item.debit > 0L) NumberFormat.getNumberInstance(Locale.US).format(item.debit) else "-"
            canvas.drawText(debitStr, leftX + 335f, rowY, rowTextPaint)

            val creditStr = if (item.credit > 0L) NumberFormat.getNumberInstance(Locale.US).format(item.credit) else "-"
            canvas.drawText(creditStr, leftX + 410f, rowY, rowTextPaint)

            val balStr = NumberFormat.getNumberInstance(Locale.US).format(item.runningBalance)
            val balP = Paint(rowTextPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            canvas.drawText(balStr, rightX - balP.measureText(balStr) - 8f, rowY, balP)

            y += 20f
        }

        // Bottom border
        canvas.drawLine(leftX, y + 2f, rightX, y + 2f, linePaint)
        y += 22f

        // Statement Final Note / Signoff
        val closingNotePaint = Paint().apply {
            color = Color.rgb(20, 60, 120)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val balanceDueMsg = if (statement.closingBalance > 0L) {
            "Total Outstanding Due: ${formatMoney(statement.closingBalance, currency)}"
        } else if (statement.closingBalance < 0L) {
            "Credit Balance in Favor: ${formatMoney(-statement.closingBalance, currency)}"
        } else {
            "Account is fully settled (Zero Balance)"
        }
        canvas.drawText(balanceDueMsg, leftX + 8f, y, closingNotePaint)
        y += 24f

        val footerPaint = Paint().apply {
            color = Color.rgb(130, 140, 150)
            textSize = 9f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated on ${DateFormatter.formatDateTime(System.currentTimeMillis())} • Powered by Mary Shop", pageWidth / 2f, y, footerPaint)

        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    private fun formatQty(qty: Double): String {
        return if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format(Locale.US, "%.2f", qty)
    }
}
