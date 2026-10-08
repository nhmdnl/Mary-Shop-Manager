package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.PartyDao
import com.example.data.local.ProductDao
import com.example.data.local.ProductPriceHistoryDao
import com.example.data.local.SaleDao
import com.example.data.local.StockMovementDao
import com.example.data.local.TransactionDao
import com.example.data.model.AdjustmentDirection
import com.example.data.model.BulkAdjustmentMode
import com.example.data.model.BulkPriceTarget
import com.example.data.model.DashboardSummary
import com.example.data.model.DateRangeSelection
import com.example.data.model.DebtAgingSummary
import com.example.data.model.DebtRiskLevel
import com.example.data.model.DebtorAgingRow
import com.example.data.model.DiscountType
import com.example.data.model.InventorySummary
import com.example.data.model.LedgerEntry
import com.example.data.model.Party
import com.example.data.model.PartyStatementReport
import com.example.data.model.PartyType
import com.example.data.model.PartyWithBalance
import com.example.data.model.PaymentMethod
import com.example.data.model.PriceTier
import com.example.data.model.Product
import com.example.data.model.ProductPriceHistory
import com.example.data.model.ProductProfitItem
import com.example.data.model.ProductProfitReport
import com.example.data.model.ProductWithSupplier
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleLineItem
import com.example.data.model.SalePaymentMode
import com.example.data.model.SaleReceipt
import com.example.data.model.SalesReportSummary
import com.example.data.model.StatementTransactionItem
import com.example.data.model.StockMovement
import com.example.data.model.StockMovementReport
import com.example.data.model.StockMovementType
import com.example.data.model.StockMovementWithDetails
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithParty
import com.example.data.preferences.ShopSettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale

class ShopRepository(
    private val database: AppDatabase,
    private val partyDao: PartyDao,
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val productPriceHistoryDao: ProductPriceHistoryDao,
    private val settingsManager: ShopSettingsManager,
    private val saleDao: SaleDao = database.saleDao()
) {
    // -------------------------------------------------------------
    // Parties & Ledger
    // -------------------------------------------------------------
    val allParties: Flow<List<Party>> = partyDao.getAllParties()

    fun getParty(id: Long): Flow<Party?> = partyDao.getPartyById(id)

    suspend fun getPartySync(id: Long): Party? = partyDao.getPartyByIdSync(id)

    suspend fun addParty(party: Party): Long = partyDao.insertParty(party)

    suspend fun updateParty(party: Party) = partyDao.updateParty(party)

    suspend fun deleteParty(party: Party) = partyDao.deleteParty(party)

    fun getTransactionsForParty(partyId: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForParty(partyId)

    suspend fun addTransaction(transaction: TransactionEntity): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun editTransaction(originalId: Long, newTransaction: TransactionEntity): Pair<Long, Long> {
        val original = transactionDao.getTransactionById(originalId)
            ?: throw IllegalArgumentException("Original transaction not found")

        // 1. Create reversal transaction
        val reversal = original.copy(
            id = 0L,
            isReversal = true,
            reversedTransactionId = original.id,
            date = System.currentTimeMillis(),
            note = "Reversal of #${original.id}: ${original.note}".trim()
        )
        val reversalId = transactionDao.insertTransaction(reversal)

        // 2. Insert new transaction
        val newEntry = newTransaction.copy(
            id = 0L,
            note = "${newTransaction.note} (Replaced #${original.id})".trim()
        )
        val newId = transactionDao.insertTransaction(newEntry)

        // 3. Update original with replacement link
        transactionDao.updateTransaction(
            original.copy(replacementTransactionId = newId)
        )

        return Pair(reversalId, newId)
    }

    suspend fun softDeleteTransaction(id: Long, reason: String = "Deleted by user") {
        transactionDao.softDeleteTransaction(
            id = id,
            deletedAt = System.currentTimeMillis(),
            reason = reason
        )
    }

    fun calculateDelta(partyType: PartyType, transaction: TransactionEntity): Long {
        if (transaction.isDeleted) return 0L

        var baseDelta: Long = when (partyType) {
            PartyType.CUSTOMER -> {
                when (transaction.type) {
                    TransactionType.SALE_ON_CREDIT -> transaction.amount
                    TransactionType.PAYMENT_RECEIVED -> -transaction.amount
                    TransactionType.PURCHASE_ON_CREDIT -> -transaction.amount
                    TransactionType.PAYMENT_MADE -> transaction.amount
                    TransactionType.ADJUSTMENT -> transaction.adjustmentDirection * transaction.amount
                }
            }
            PartyType.SUPPLIER -> {
                when (transaction.type) {
                    TransactionType.PURCHASE_ON_CREDIT -> transaction.amount
                    TransactionType.PAYMENT_MADE -> -transaction.amount
                    TransactionType.SALE_ON_CREDIT -> -transaction.amount
                    TransactionType.PAYMENT_RECEIVED -> transaction.amount
                    TransactionType.ADJUSTMENT -> transaction.adjustmentDirection * transaction.amount
                }
            }
            PartyType.BOTH -> {
                when (transaction.type) {
                    TransactionType.SALE_ON_CREDIT -> transaction.amount
                    TransactionType.PAYMENT_RECEIVED -> -transaction.amount
                    TransactionType.PURCHASE_ON_CREDIT -> -transaction.amount
                    TransactionType.PAYMENT_MADE -> transaction.amount
                    TransactionType.ADJUSTMENT -> transaction.adjustmentDirection * transaction.amount
                }
            }
        }

        if (transaction.isReversal) {
            baseDelta = -baseDelta
        }

        return baseDelta
    }

    val partiesWithBalances: Flow<List<PartyWithBalance>> = combine(
        partyDao.getAllParties(),
        transactionDao.getActiveTransactions()
    ) { parties, transactions ->
        val txsByParty = transactions.groupBy { it.partyId }

        parties.map { party ->
            val partyTxs = txsByParty[party.id] ?: emptyList()
            var currentBalance = party.openingBalance
            var lastActivity = party.createdAt

            val sortedTxs = partyTxs.sortedBy { it.date }
            for (tx in sortedTxs) {
                currentBalance += calculateDelta(party.type, tx)
                if (tx.date > lastActivity) {
                    lastActivity = tx.date
                }
            }

            val receivables = when (party.type) {
                PartyType.CUSTOMER, PartyType.BOTH -> if (currentBalance > 0) currentBalance else 0L
                PartyType.SUPPLIER -> if (currentBalance < 0) -currentBalance else 0L
            }

            val payables = when (party.type) {
                PartyType.SUPPLIER -> if (currentBalance > 0) currentBalance else 0L
                PartyType.CUSTOMER, PartyType.BOTH -> if (currentBalance < 0) -currentBalance else 0L
            }

            val isOverLimit = (party.type == PartyType.CUSTOMER || party.type == PartyType.BOTH) &&
                    party.creditLimit > 0L && currentBalance > party.creditLimit

            PartyWithBalance(
                party = party,
                balance = currentBalance,
                receivables = receivables,
                payables = payables,
                lastActivityDate = lastActivity,
                isOverCreditLimit = isOverLimit,
                transactionCount = partyTxs.size
            )
        }
    }

    fun getLedgerForParty(partyId: Long): Flow<List<LedgerEntry>> = combine(
        partyDao.getPartyById(partyId),
        transactionDao.getTransactionsForParty(partyId)
    ) { party, transactions ->
        if (party == null) return@combine emptyList()

        var running = party.openingBalance
        val entries = mutableListOf<LedgerEntry>()

        val sortedTxs = transactions.sortedBy { it.date }
        for (tx in sortedTxs) {
            val delta = calculateDelta(party.type, tx)
            if (!tx.isDeleted) {
                running += delta
            }
            val isOver = (party.type == PartyType.CUSTOMER || party.type == PartyType.BOTH) &&
                    party.creditLimit > 0L && running > party.creditLimit

            entries.add(
                LedgerEntry(
                    transaction = tx,
                    delta = delta,
                    runningBalance = running,
                    isOverLimit = isOver
                )
            )
        }
        entries.reversed()
    }

    // -------------------------------------------------------------
    // Phase 2: Products & Inventory Management
    // -------------------------------------------------------------
    val allProductsWithSuppliers: Flow<List<ProductWithSupplier>> = combine(
        productDao.getAllProducts(),
        partyDao.getAllParties()
    ) { products, parties ->
        val supplierMap = parties.associateBy { it.id }
        products.map { product ->
            ProductWithSupplier(
                product = product,
                supplier = product.supplierId?.let { supplierMap[it] },
                isLowStock = product.stockQty <= product.reorderLevel,
                isOutOfStock = product.stockQty <= 0.0
            )
        }
    }

    val dashboardSummary: Flow<DashboardSummary> = combine(
        partiesWithBalances,
        transactionDao.getActiveTransactions(),
        partyDao.getAllParties(),
        saleDao.getAllSales(),
        allProductsWithSuppliers
    ) { partyBalances, activeTxs, allPartiesList, allSales, allProducts ->
        val partyMap = allPartiesList.associateBy { it.id }

        val totalReceivables = partyBalances.sumOf { it.receivables }
        val totalPayables = partyBalances.sumOf { it.payables }
        val net = totalReceivables - totalPayables
        val overLimit = partyBalances.filter { it.isOverCreditLimit }

        val recentTxs = activeTxs.take(15)
        val recentWithParty = recentTxs.map { tx ->
            val p = partyMap[tx.partyId]
            TransactionWithParty(
                transaction = tx,
                partyName = p?.name ?: "Unknown Party",
                partyType = p?.type ?: PartyType.CUSTOMER
            )
        }

        val customersCount = partyBalances.count { it.party.type == PartyType.CUSTOMER || it.party.type == PartyType.BOTH }
        val suppliersCount = partyBalances.count { it.party.type == PartyType.SUPPLIER || it.party.type == PartyType.BOTH }

        val startOfDay = DateRangeSelection.startOfDay(System.currentTimeMillis())
        val endOfDay = DateRangeSelection.endOfDay(System.currentTimeMillis())

        val todaySalesList = allSales.filter { it.date in startOfDay..endOfDay }
        val todaySales = todaySalesList.sumOf { it.finalTotal }
        val todayProfit = todaySalesList.sumOf { it.profit }
        val todaySalesCount = todaySalesList.size

        val cashSalesToday = todaySalesList.filter { it.paymentMode == SalePaymentMode.CASH }.sumOf { it.finalTotal }
        val paymentsReceivedToday = activeTxs.filter { it.type == TransactionType.PAYMENT_RECEIVED && it.date in startOfDay..endOfDay }.sumOf { it.amount }
        val todayCashIn = cashSalesToday + paymentsReceivedToday

        val todayCashOut = activeTxs.filter { it.type == TransactionType.PAYMENT_MADE && it.date in startOfDay..endOfDay }.sumOf { it.amount }

        val stockCost = allProducts.sumOf { (it.product.costPrice * it.product.stockQty).toLong() }
        val stockRetail = allProducts.sumOf { (it.product.retailPrice * it.product.stockQty).toLong() }
        val potentialProfit = (stockRetail - stockCost).coerceAtLeast(0L)
        val lowStock = allProducts.count { it.isLowStock }

        DashboardSummary(
            todaySales = todaySales,
            todayCashIn = todayCashIn,
            todayCashOut = todayCashOut,
            todayProfit = todayProfit,
            todaySalesCount = todaySalesCount,
            totalReceivables = totalReceivables,
            totalPayables = totalPayables,
            netPosition = net,
            stockValueAtCost = stockCost,
            stockValueAtRetail = stockRetail,
            potentialProfit = potentialProfit,
            partiesOverCreditLimit = overLimit,
            recentTransactions = recentWithParty,
            totalCustomerCount = customersCount,
            totalSupplierCount = suppliersCount,
            lowStockCount = lowStock
        )
    }

    val lowStockProducts: Flow<List<ProductWithSupplier>> = allProductsWithSuppliers.map { list ->
        list.filter { it.isLowStock }
    }

    val lowStockCount: Flow<Int> = lowStockProducts.map { it.size }

    val inventorySummary: Flow<InventorySummary> = allProductsWithSuppliers.map { list ->
        val lowCount = list.count { it.isLowStock }
        val outCount = list.count { it.isOutOfStock }
        val costVal = list.sumOf { (it.product.costPrice * it.product.stockQty).toLong() }
        val retailVal = list.sumOf { (it.product.retailPrice * it.product.stockQty).toLong() }
        val cats = list.map { it.product.category }.distinct().sorted()

        InventorySummary(
            totalProductsCount = list.size,
            lowStockCount = lowCount,
            outOfStockCount = outCount,
            totalStockValueAtCost = costVal,
            totalStockValueAtRetail = retailVal,
            categories = cats
        )
    }

    fun getProduct(id: Long): Flow<Product?> = productDao.getProductById(id)

    fun getProductWithSupplier(id: Long): Flow<ProductWithSupplier?> = combine(
        productDao.getProductById(id),
        partyDao.getAllParties()
    ) { prod, parties ->
        if (prod == null) null
        else {
            val supp = prod.supplierId?.let { sId -> parties.find { it.id == sId } }
            ProductWithSupplier(
                product = prod,
                supplier = supp,
                isLowStock = prod.stockQty <= prod.reorderLevel,
                isOutOfStock = prod.stockQty <= 0.0
            )
        }
    }

    fun getStockMovementsForProduct(productId: Long): Flow<List<StockMovement>> =
        stockMovementDao.getMovementsForProduct(productId)

    fun getPriceHistoryForProduct(productId: Long): Flow<List<ProductPriceHistory>> =
        productPriceHistoryDao.getPriceHistoryForProduct(productId)

    suspend fun addProduct(product: Product): Long {
        val id = productDao.insertProduct(product)
        productPriceHistoryDao.insert(
            ProductPriceHistory(
                productId = id,
                costPrice = product.costPrice,
                retailPrice = product.retailPrice,
                wholesalePrice = product.wholesalePrice,
                changeReason = "Initial baseline pricing",
                date = product.createdAt
            )
        )
        return id
    }

    suspend fun updateProduct(product: Product, changeReason: String = "Manual price/product update") {
        val old = productDao.getProductByIdDirect(product.id)
        productDao.updateProduct(product)
        if (old != null && (old.costPrice != product.costPrice || old.retailPrice != product.retailPrice || old.wholesalePrice != product.wholesalePrice)) {
            productPriceHistoryDao.insert(
                ProductPriceHistory(
                    productId = product.id,
                    costPrice = product.costPrice,
                    retailPrice = product.retailPrice,
                    wholesalePrice = product.wholesalePrice,
                    previousCostPrice = old.costPrice,
                    previousRetailPrice = old.retailPrice,
                    previousWholesalePrice = old.wholesalePrice,
                    changeReason = changeReason,
                    date = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteProduct(product: Product) {
        productPriceHistoryDao.deleteForProduct(product.id)
        stockMovementDao.deleteMovementsForProduct(product.id)
        productDao.deleteProduct(product)
    }

    /**
     * Records a stock movement and updates product stock level.
     * Enforces: "Stock can't go below zero unless a setting allows it."
     */
    suspend fun recordStockMovement(
        productId: Long,
        type: StockMovementType,
        quantity: Double,
        isStockIn: Boolean,
        unitPrice: Long? = null,
        referenceNo: String = "",
        note: String = "",
        partyId: Long? = null,
        date: Long = System.currentTimeMillis()
    ): Result<StockMovement> {
        if (quantity <= 0.0) {
            return Result.failure(IllegalArgumentException("Quantity must be greater than zero"))
        }

        val product = productDao.getProductByIdDirect(productId)
            ?: return Result.failure(IllegalArgumentException("Product with ID $productId not found"))

        val delta = if (isStockIn) quantity else -quantity
        val newStock = product.stockQty + delta
        val allowNegative = settingsManager.settings.value.allowNegativeStock

        if (newStock < 0.0 && !allowNegative) {
            val formattedCurrent = formatQty(product.stockQty)
            val formattedQty = formatQty(quantity)
            val formattedNew = formatQty(newStock)
            return Result.failure(
                IllegalStateException(
                    "Stock cannot fall below zero ($formattedNew ${product.unit}). Current stock is $formattedCurrent ${product.unit}. You requested reduction of $formattedQty ${product.unit}. Enable 'Allow Negative Stock' in Settings if you want to bypass this."
                )
            )
        }

        val movement = StockMovement(
            productId = productId,
            type = type,
            quantity = quantity,
            isStockIn = isStockIn,
            unitPrice = unitPrice,
            date = date,
            referenceNo = referenceNo.trim(),
            note = note.trim(),
            partyId = partyId,
            resultingStock = newStock
        )

        val movementId = stockMovementDao.insertMovement(movement)
        productDao.updateStockQty(productId, newStock, date)

        return Result.success(movement.copy(id = movementId))
    }

    // -------------------------------------------------------------
    // Phase 3: Bulk Price Update, Quick Sale & Ledger Posting
    // -------------------------------------------------------------
    suspend fun getPartyBalanceDirect(partyId: Long): Long {
        val party = partyDao.getPartyByIdSync(partyId) ?: return 0L
        val txs = transactionDao.getActiveTransactionsForPartyDirect(partyId)
        var balance = party.openingBalance
        for (tx in txs) {
            balance += calculateDelta(party.type, tx)
        }
        return balance
    }

    suspend fun bulkUpdateCategoryPrices(
        category: String?,
        targetPrice: BulkPriceTarget,
        adjustmentMode: BulkAdjustmentMode,
        adjustmentValue: Double,
        isIncrease: Boolean,
        reason: String = "Bulk Price Update"
    ): Result<Int> {
        if (adjustmentValue <= 0.0) {
            return Result.failure(IllegalArgumentException("Adjustment value must be greater than zero"))
        }

        return try {
            val allProducts = productDao.getAllProductsDirect()
            val targetProducts = if (category.isNullOrBlank() || category == "All Categories") {
                allProducts
            } else {
                allProducts.filter { it.category.equals(category, ignoreCase = true) }
            }

            if (targetProducts.isEmpty()) {
                return Result.success(0)
            }

            val multiplier = if (isIncrease) 1 else -1
            val now = System.currentTimeMillis()
            val historyEntries = mutableListOf<ProductPriceHistory>()

            for (prod in targetProducts) {
                var newCost = prod.costPrice
                var newRetail = prod.retailPrice
                var newWholesale = prod.wholesalePrice

                when (targetPrice) {
                    BulkPriceTarget.RETAIL -> {
                        newRetail = calculateAdjustedPrice(prod.retailPrice, adjustmentMode, adjustmentValue, multiplier)
                    }
                    BulkPriceTarget.WHOLESALE -> {
                        newWholesale = calculateAdjustedPrice(prod.wholesalePrice, adjustmentMode, adjustmentValue, multiplier)
                    }
                    BulkPriceTarget.BOTH_RETAIL_AND_WHOLESALE -> {
                        newRetail = calculateAdjustedPrice(prod.retailPrice, adjustmentMode, adjustmentValue, multiplier)
                        newWholesale = calculateAdjustedPrice(prod.wholesalePrice, adjustmentMode, adjustmentValue, multiplier)
                    }
                    BulkPriceTarget.COST -> {
                        newCost = calculateAdjustedPrice(prod.costPrice, adjustmentMode, adjustmentValue, multiplier)
                    }
                }

                val updatedProd = prod.copy(
                    costPrice = newCost,
                    retailPrice = newRetail,
                    wholesalePrice = newWholesale,
                    updatedAt = now
                )
                productDao.updateProduct(updatedProd)

                val modeStr = if (adjustmentMode == BulkAdjustmentMode.PERCENTAGE) "$adjustmentValue%" else "$adjustmentValue"
                historyEntries.add(
                    ProductPriceHistory(
                        productId = prod.id,
                        costPrice = newCost,
                        retailPrice = newRetail,
                        wholesalePrice = newWholesale,
                        previousCostPrice = prod.costPrice,
                        previousRetailPrice = prod.retailPrice,
                        previousWholesalePrice = prod.wholesalePrice,
                        changeReason = "$reason (${if (isIncrease) "+" else "-"}$modeStr on ${targetPrice.label})",
                        date = now
                    )
                )
            }

            productPriceHistoryDao.insertAll(historyEntries)
            Result.success(targetProducts.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateAdjustedPrice(
        currentPrice: Long,
        mode: BulkAdjustmentMode,
        value: Double,
        multiplier: Int
    ): Long {
        return when (mode) {
            BulkAdjustmentMode.PERCENTAGE -> {
                val delta = currentPrice * (value / 100.0) * multiplier
                kotlin.math.round(currentPrice + delta).toLong().coerceAtLeast(0L)
            }
            BulkAdjustmentMode.FIXED_AMOUNT -> {
                val delta = (value * multiplier).toLong()
                (currentPrice + delta).coerceAtLeast(0L)
            }
        }
    }

    suspend fun completeSale(
        items: List<SaleLineItem>,
        customer: Party?,
        paymentMode: SalePaymentMode,
        discountType: DiscountType,
        discountValue: Double,
        cashTendered: Long,
        invoiceNumber: String,
        notes: String = ""
    ): Result<SaleReceipt> {
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Sale must have at least one product."))
        }

        if (paymentMode == SalePaymentMode.CREDIT && customer == null) {
            return Result.failure(IllegalArgumentException("Customer is required for credit sales. Please select or add a customer."))
        }

        val allowNegative = settingsManager.settings.value.allowNegativeStock

        // Verify stock availability
        if (!allowNegative) {
            for (item in items) {
                val liveProd = productDao.getProductByIdDirect(item.product.id)
                    ?: return Result.failure(IllegalArgumentException("Product '${item.product.name}' was not found."))
                if (liveProd.stockQty < item.quantity) {
                    val availableStr = formatQty(liveProd.stockQty)
                    val requestedStr = formatQty(item.quantity)
                    return Result.failure(
                        IllegalStateException(
                            "Cannot complete sale: Insufficient stock for '${item.product.name}'. Available: $availableStr ${item.product.unit}, Requested: $requestedStr ${item.product.unit}. Enable 'Allow Negative Stock' in Settings if needed."
                        )
                    )
                }
            }
        }

        val currentSettings = settingsManager.settings.value
        val subtotal = items.sumOf { it.subtotal }
        val discountAmount = when (discountType) {
            DiscountType.PERCENTAGE -> {
                val pct = discountValue.coerceIn(0.0, 100.0)
                (subtotal * (pct / 100.0)).toLong()
            }
            DiscountType.FIXED -> {
                discountValue.toLong().coerceIn(0L, subtotal)
            }
        }
        val discountedSubtotal = (subtotal - discountAmount).coerceAtLeast(0L)
        val taxPercentage = if (currentSettings.taxEnabled) currentSettings.taxPercentage else 0.0
        val taxAmount = if (taxPercentage > 0.0) {
            (discountedSubtotal * (taxPercentage / 100.0)).toLong()
        } else 0L
        val finalTotal = (discountedSubtotal + taxAmount).coerceAtLeast(0L)
        val changeDue = if (paymentMode == SalePaymentMode.CASH) {
            (cashTendered - finalTotal).coerceAtLeast(0L)
        } else 0L

        val now = System.currentTimeMillis()
        val itemSummary = items.joinToString(", ") { "${formatQty(it.quantity)}x ${it.product.name}" }

        // 1. Post to Customer Ledger if Credit Sale
        var newCustomerBal: Long? = null
        if (paymentMode == SalePaymentMode.CREDIT && customer != null) {
            val saleNote = if (notes.isNotBlank()) {
                "$notes ($itemSummary)"
            } else {
                "Quick Sale #$invoiceNumber: $itemSummary"
            }

            val tx = TransactionEntity(
                partyId = customer.id,
                type = TransactionType.SALE_ON_CREDIT,
                amount = finalTotal,
                date = now,
                paymentMethod = PaymentMethod.CASH,
                referenceNo = invoiceNumber,
                note = saleNote
            )
            transactionDao.insertTransaction(tx)
            newCustomerBal = getPartyBalanceDirect(customer.id)
        }

        // 2. Reduce Stock for every product & write StockMovement audit trail
        for (item in items) {
            val liveProd = productDao.getProductByIdDirect(item.product.id) ?: continue
            val newStock = liveProd.stockQty - item.quantity

            val movement = StockMovement(
                productId = item.product.id,
                type = StockMovementType.SALE_OUT,
                quantity = item.quantity,
                isStockIn = false,
                unitPrice = item.unitPrice,
                date = now,
                referenceNo = invoiceNumber,
                note = "Quick Sale to ${customer?.name ?: "Cash Customer"}",
                partyId = customer?.id,
                resultingStock = newStock
            )
            stockMovementDao.insertMovement(movement)
            productDao.updateStockQty(item.product.id, newStock, now)
        }

        // 3. Record Sale & Line Items in SaleDao for reporting & analytics
        val totalCost = items.sumOf { (it.product.costPrice * it.quantity).toLong() }
        val saleProfit = (finalTotal - totalCost)

        val saleEntity = SaleEntity(
            invoiceNumber = invoiceNumber,
            date = now,
            customerId = customer?.id,
            customerName = customer?.name,
            subtotal = subtotal,
            discountType = discountType,
            discountValue = discountValue,
            discountAmount = discountAmount,
            finalTotal = finalTotal,
            totalCost = totalCost,
            profit = saleProfit,
            paymentMode = paymentMode,
            cashTendered = cashTendered,
            changeDue = changeDue,
            notes = notes
        )
        val saleId = saleDao.insertSale(saleEntity)

        val saleItems = items.map { item ->
            SaleItemEntity(
                saleId = saleId,
                invoiceNumber = invoiceNumber,
                productId = item.product.id,
                productName = item.product.name,
                category = item.product.category,
                quantity = item.quantity,
                unit = item.product.unit,
                costPrice = item.product.costPrice,
                unitPrice = item.unitPrice,
                subtotal = item.subtotal,
                profit = item.subtotal - (item.product.costPrice * item.quantity).toLong(),
                priceTier = item.priceTier,
                date = now
            )
        }
        saleDao.insertSaleItems(saleItems)

        val receipt = SaleReceipt(
            invoiceNumber = invoiceNumber,
            date = now,
            items = items,
            subtotal = subtotal,
            discountType = discountType,
            discountValue = discountValue,
            discountAmount = discountAmount,
            taxPercentage = taxPercentage,
            taxAmount = taxAmount,
            finalTotal = finalTotal,
            paymentMode = paymentMode,
            cashTendered = cashTendered,
            changeDue = changeDue,
            customer = customer,
            newCustomerBalance = newCustomerBal,
            notes = notes
        )

        return Result.success(receipt)
    }

    companion object {
        fun formatQty(qty: Double): String {
            return if (qty % 1.0 == 0.0) {
                qty.toInt().toString()
            } else {
                String.format(Locale.US, "%.2f", qty)
            }
        }
    }

    // -------------------------------------------------------------
    // Phase 4: Reports & Analytics
    // -------------------------------------------------------------
    fun getSalesReport(startDate: Long, endDate: Long): Flow<SalesReportSummary> =
        saleDao.getSalesBetween(startDate, endDate).map { salesList ->
            val totalSales = salesList.sumOf { it.finalTotal }
            val totalCost = salesList.sumOf { it.totalCost }
            val totalProfit = salesList.sumOf { it.profit }
            val totalDiscounts = salesList.sumOf { it.discountAmount }
            val cashSales = salesList.filter { it.paymentMode == SalePaymentMode.CASH }
            val creditSales = salesList.filter { it.paymentMode == SalePaymentMode.CREDIT }
            val avg = if (salesList.isNotEmpty()) totalSales / salesList.size else 0L
            val margin = if (totalSales > 0L) (totalProfit.toDouble() / totalSales.toDouble()) * 100.0 else 0.0

            SalesReportSummary(
                startDate = startDate,
                endDate = endDate,
                totalSales = totalSales,
                totalProfit = totalProfit,
                totalCost = totalCost,
                salesCount = salesList.size,
                cashSalesTotal = cashSales.sumOf { it.finalTotal },
                creditSalesTotal = creditSales.sumOf { it.finalTotal },
                cashSalesCount = cashSales.size,
                creditSalesCount = creditSales.size,
                totalDiscounts = totalDiscounts,
                averageSaleValue = avg,
                profitMarginPercent = margin,
                sales = salesList
            )
        }

    fun getProductProfitReport(startDate: Long, endDate: Long): Flow<ProductProfitReport> =
        saleDao.getSaleItemsBetween(startDate, endDate).map { itemsList ->
            val grouped = itemsList.groupBy { it.productId }
            val profitItems = grouped.map { (prodId, items) ->
                val first = items.first()
                val totalQty = items.sumOf { it.quantity }
                val totalRev = items.sumOf { it.subtotal }
                val totalCst = items.sumOf { (it.costPrice * it.quantity).toLong() }
                val profit = totalRev - totalCst
                val margin = if (totalRev > 0L) (profit.toDouble() / totalRev.toDouble()) * 100.0 else 0.0

                ProductProfitItem(
                    productId = prodId,
                    productName = first.productName,
                    category = first.category,
                    unit = first.unit,
                    quantitySold = totalQty,
                    totalRevenue = totalRev,
                    totalCost = totalCst,
                    totalProfit = profit,
                    profitMarginPercent = margin
                )
            }.sortedByDescending { it.totalProfit }

            val totalRev = profitItems.sumOf { it.totalRevenue }
            val totalCst = profitItems.sumOf { it.totalCost }
            val totalProf = profitItems.sumOf { it.totalProfit }
            val totalUnits = profitItems.sumOf { it.quantitySold }
            val overallMargin = if (totalRev > 0L) (totalProf.toDouble() / totalRev.toDouble()) * 100.0 else 0.0

            ProductProfitReport(
                startDate = startDate,
                endDate = endDate,
                totalRevenue = totalRev,
                totalCost = totalCst,
                totalProfit = totalProf,
                overallMarginPercent = overallMargin,
                totalUnitsSold = totalUnits,
                items = profitItems
            )
        }

    fun getPartyStatement(partyId: Long, startDate: Long, endDate: Long): Flow<PartyStatementReport> =
        combine(
            partyDao.getPartyById(partyId),
            transactionDao.getTransactionsForParty(partyId)
        ) { party, allTxs ->
            if (party == null) {
                return@combine PartyStatementReport(
                    party = Party(name = "Unknown Party", phone = "", type = PartyType.CUSTOMER),
                    startDate = startDate,
                    endDate = endDate,
                    openingBalance = 0L
                )
            }

            val validTxs = allTxs.filter { !it.isDeleted }.sortedBy { it.date }
            val preTxs = validTxs.filter { it.date < startDate }
            val periodTxs = validTxs.filter { it.date in startDate..endDate }

            var opening = party.openingBalance
            for (tx in preTxs) {
                opening += calculateDelta(party.type, tx)
            }

            var running = opening
            var totalDebits = 0L
            var totalCredits = 0L
            val statementItems = mutableListOf<StatementTransactionItem>()

            for (tx in periodTxs) {
                val delta = calculateDelta(party.type, tx)
                running += delta

                val isDebit = delta > 0L
                val debitAmt = if (isDebit) delta else 0L
                val creditAmt = if (!isDebit) -delta else 0L

                totalDebits += debitAmt
                totalCredits += creditAmt

                val desc = if (tx.note.isNotBlank()) tx.note else tx.type.label
                statementItems.add(
                    StatementTransactionItem(
                        transaction = tx,
                        date = tx.date,
                        referenceNo = tx.referenceNo,
                        description = desc,
                        debit = debitAmt,
                        credit = creditAmt,
                        runningBalance = running
                    )
                )
            }

            PartyStatementReport(
                party = party,
                startDate = startDate,
                endDate = endDate,
                openingBalance = opening,
                transactions = statementItems,
                totalDebits = totalDebits,
                totalCredits = totalCredits,
                closingBalance = running,
                netChange = running - opening
            )
        }

    fun getStockMovementReport(startDate: Long, endDate: Long): Flow<StockMovementReport> =
        combine(
            stockMovementDao.getMovementsBetween(startDate, endDate),
            productDao.getAllProducts(),
            partyDao.getAllParties()
        ) { movements, products, parties ->
            val productMap = products.associateBy { it.id }
            val partyMap = parties.associateBy { it.id }

            val detailedMovements = movements.map { m ->
                val prod = productMap[m.productId]
                StockMovementWithDetails(
                    movement = m,
                    productName = prod?.name ?: "Unknown Product",
                    productUnit = prod?.unit ?: "units",
                    partyName = m.partyId?.let { partyMap[it]?.name }
                )
            }

            val totalIn = detailedMovements.filter { it.movement.type == StockMovementType.PURCHASE_IN }.sumOf { it.movement.quantity }
            val totalOut = detailedMovements.filter { it.movement.type == StockMovementType.SALE_OUT }.sumOf { it.movement.quantity }
            val totalRet = detailedMovements.filter { it.movement.type == StockMovementType.RETURN }.sumOf { it.movement.quantity }
            val totalDam = detailedMovements.filter { it.movement.type == StockMovementType.DAMAGE_LOSS }.sumOf { it.movement.quantity }
            val totalAdj = detailedMovements.filter { it.movement.type == StockMovementType.ADJUSTMENT }.sumOf { it.movement.quantity }

            StockMovementReport(
                startDate = startDate,
                endDate = endDate,
                movements = detailedMovements,
                totalMovementsCount = detailedMovements.size,
                totalPurchaseInQty = totalIn,
                totalSaleOutQty = totalOut,
                totalReturnQty = totalRet,
                totalDamageQty = totalDam,
                totalAdjustmentQty = totalAdj
            )
        }

    val debtAgingSummary: Flow<DebtAgingSummary> = combine(
        partiesWithBalances,
        transactionDao.getActiveTransactions()
    ) { balances, activeTxs ->
        val now = System.currentTimeMillis()
        val d30 = now - 30L * 24 * 3600 * 1000L
        val d60 = now - 60L * 24 * 3600 * 1000L
        val d90 = now - 90L * 24 * 3600 * 1000L

        val customerDebtors = balances.filter {
            (it.party.type == PartyType.CUSTOMER || it.party.type == PartyType.BOTH) && it.receivables > 0L
        }

        val debtorRows = customerDebtors.map { debtor ->
            val partyId = debtor.party.id
            val partyTxs = activeTxs.filter { it.partyId == partyId }.sortedBy { it.date }

            var paymentsTotal = partyTxs
                .filter { it.type == TransactionType.PAYMENT_RECEIVED || (it.type == TransactionType.ADJUSTMENT && it.adjustmentDirection == -1) }
                .sumOf { it.amount }

            // Chronological debits
            val debits = mutableListOf<Pair<Long, Long>>()
            if (debtor.party.openingBalance > 0L) {
                debits.add(Pair(debtor.party.createdAt, debtor.party.openingBalance))
            }
            partyTxs.filter {
                it.type == TransactionType.SALE_ON_CREDIT || (it.type == TransactionType.ADJUSTMENT && it.adjustmentDirection == 1)
            }.forEach {
                debits.add(Pair(it.date, it.amount))
            }

            var b0 = 0L
            var b30 = 0L
            var b60 = 0L
            var b90 = 0L
            var oldestDate = 0L

            for ((date, amount) in debits) {
                if (paymentsTotal >= amount) {
                    paymentsTotal -= amount
                } else {
                    val unpaid = amount - paymentsTotal
                    paymentsTotal = 0L
                    if (oldestDate == 0L) oldestDate = date

                    when {
                        date >= d30 -> b0 += unpaid
                        date >= d60 -> b30 += unpaid
                        date >= d90 -> b60 += unpaid
                        else -> b90 += unpaid
                    }
                }
            }

            val computedSum = b0 + b30 + b60 + b90
            val diff = debtor.receivables - computedSum
            if (diff > 0L) {
                b0 += diff
            }

            val risk = when {
                b90 > 0L -> DebtRiskLevel.CRITICAL
                b60 > 0L -> DebtRiskLevel.HIGH_RISK
                b30 > 0L -> DebtRiskLevel.ATTENTION
                else -> DebtRiskLevel.CURRENT
            }

            DebtorAgingRow(
                party = debtor.party,
                totalDebt = debtor.receivables,
                amount0To30 = b0,
                amount31To60 = b30,
                amount61To90 = b60,
                amount90Plus = b90,
                oldestDebtDate = if (oldestDate > 0L) oldestDate else debtor.party.createdAt,
                riskLevel = risk
            )
        }.sortedByDescending { it.totalDebt }

        val totalReceivables = debtorRows.sumOf { it.totalDebt }
        val tot0 = debtorRows.sumOf { it.amount0To30 }
        val tot30 = debtorRows.sumOf { it.amount31To60 }
        val tot60 = debtorRows.sumOf { it.amount61To90 }
        val tot90 = debtorRows.sumOf { it.amount90Plus }

        DebtAgingSummary(
            asOfDate = now,
            totalReceivables = totalReceivables,
            bucket0To30 = tot0,
            bucket31To60 = tot30,
            bucket61To90 = tot60,
            bucket90Plus = tot90,
            debtorCount = debtorRows.size,
            debtors = debtorRows
        )
    }

    suspend fun getSaleById(id: Long): SaleEntity? = saleDao.getSaleById(id)

    suspend fun getSaleByInvoiceNumber(invoiceNo: String): SaleEntity? = saleDao.getSaleByInvoiceNumber(invoiceNo)

    suspend fun getItemsForSaleDirect(saleId: Long): List<SaleItemEntity> =
        saleDao.getItemsForSale(saleId).map { it }.let { flow ->
            // direct fetch
            val all = saleDao.getSaleItemsBetweenDirect(0L, Long.MAX_VALUE)
            all.filter { it.saleId == saleId }
        }

    // -------------------------------------------------------------
    // Data Management
    // -------------------------------------------------------------
    suspend fun clearAllData() {
        saleDao.deleteAllSales()
        stockMovementDao.deleteAllMovements()
        productDao.deleteAllProducts()
        transactionDao.clearAllTransactions()
        partyDao.clearAllParties()
    }

    suspend fun seedSampleDataIfEmpty() {
        // Mock data removed - user fills data during testing
    }

    suspend fun resetToSampleData() {
        clearAllData()
    }
}
