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
    // Seeding & Demo Data
    // -------------------------------------------------------------
    suspend fun seedSampleDataIfEmpty() {
        if (partyDao.getPartyCount() == 0) {
            seedSampleData()
        } else if (productDao.countProducts() == 0) {
            seedProductsOnly()
        }
    }

    suspend fun resetToSampleData() {
        saleDao.deleteAllSales()
        stockMovementDao.deleteAllMovements()
        productDao.deleteAllProducts()
        transactionDao.clearAllTransactions()
        partyDao.clearAllParties()
        seedSampleData()
    }

    private suspend fun seedProductsOnly() {
        val suppliers = partyDao.getAllPartiesSync().filter { it.type == PartyType.SUPPLIER || it.type == PartyType.BOTH }
        val s1Id = suppliers.getOrNull(0)?.id
        val s2Id = suppliers.getOrNull(1)?.id
        val s3Id = suppliers.getOrNull(2)?.id
        seed15Products(s1Id, s2Id, s3Id)
    }

    private suspend fun seedSampleData() {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        // 5 Customers
        val c1Id = partyDao.insertParty(
            Party(
                name = "Sarah Namubiru",
                phone = "+256 772 123456",
                type = PartyType.CUSTOMER,
                address = "Kawempe Market, Stall 14",
                notes = "Buys posho & cooking oil weekly. Very reliable.",
                openingBalance = 50_000L,
                creditLimit = 250_000L,
                createdAt = now - 20 * day
            )
        )

        val c2Id = partyDao.insertParty(
            Party(
                name = "David Ochieng",
                phone = "+256 752 987654",
                type = PartyType.CUSTOMER,
                address = "Kalerwe, Near Total Station",
                notes = "Small canteen owner. Prefers paying via MTN Mobile Money.",
                openingBalance = 0L,
                creditLimit = 500_000L,
                createdAt = now - 18 * day
            )
        )

        val c3Id = partyDao.insertParty(
            Party(
                name = "Grace Akello",
                phone = "+256 701 456789",
                type = PartyType.CUSTOMER,
                address = "Wandegeya Flats, Block C",
                notes = "Bakery provisions buyer. Settles promptly at month end.",
                openingBalance = 20_000L,
                creditLimit = 100_000L,
                createdAt = now - 15 * day
            )
        )

        val c4Id = partyDao.insertParty(
            Party(
                name = "John Baptist Mukasa",
                phone = "+256 782 334455",
                type = PartyType.CUSTOMER,
                address = "Nakasero Road, Shop 8",
                notes = "Soft drinks retailer.",
                openingBalance = 100_000L,
                creditLimit = 400_000L,
                createdAt = now - 12 * day
            )
        )

        val c5Id = partyDao.insertParty(
            Party(
                name = "Brenda Kiconco",
                phone = "+256 774 889900",
                type = PartyType.BOTH,
                address = "Gayaza Trading Centre",
                notes = "Supplies fresh eggs & honey, purchases bulk groceries.",
                openingBalance = 0L,
                creditLimit = 300_000L,
                createdAt = now - 10 * day
            )
        )

        // 3 Suppliers
        val s1Id = partyDao.insertParty(
            Party(
                name = "Mukwano Wholesale Traders",
                phone = "+256 414 112233",
                type = PartyType.SUPPLIER,
                address = "Mukwano Complex, 6th Street",
                notes = "Edible oils, laundry bar soap & plastics wholesale.",
                openingBalance = 400_000L,
                creditLimit = 0L,
                createdAt = now - 30 * day
            )
        )

        val s2Id = partyDao.insertParty(
            Party(
                name = "Kampala Flour Mills Ltd",
                phone = "+256 414 445566",
                type = PartyType.SUPPLIER,
                address = "Industrial Area, 7th Street",
                notes = "Supreme baking flour, fortified maize meal.",
                openingBalance = 0L,
                creditLimit = 0L,
                createdAt = now - 25 * day
            )
        )

        val s3Id = partyDao.insertParty(
            Party(
                name = "Nile Agro Distributors",
                phone = "+256 705 667788",
                type = PartyType.SUPPLIER,
                address = "Jinja Road, Banda Depot",
                notes = "Beverages, dairy, and confectionery supplies.",
                openingBalance = 150_000L,
                creditLimit = 0L,
                createdAt = now - 22 * day
            )
        )

        // Transactions for Customer 1 (Sarah Namubiru - will be over credit limit: 280,000 > 250,000)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c1Id,
                type = TransactionType.SALE_ON_CREDIT,
                amount = 150_000L,
                date = now - 14 * day,
                paymentMethod = PaymentMethod.OTHER,
                referenceNo = "INV-1021",
                note = "5 bags Posho flour (50kg total) & 10kg Sugar"
            )
        )
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c1Id,
                type = TransactionType.PAYMENT_RECEIVED,
                amount = 80_000L,
                date = now - 10 * day,
                paymentMethod = PaymentMethod.MOBILE_MONEY,
                referenceNo = "MM-98442",
                note = "Partial payment via MTN Mobile Money"
            )
        )
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c1Id,
                type = TransactionType.SALE_ON_CREDIT,
                amount = 160_000L,
                date = now - 3 * day,
                paymentMethod = PaymentMethod.OTHER,
                referenceNo = "INV-1088",
                note = "Cooking oil 20L & 2 cartons laundry soap"
            )
        )

        // Transactions for Customer 2 (David Ochieng - balance 120,000)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c2Id,
                type = TransactionType.SALE_ON_CREDIT,
                amount = 220_000L,
                date = now - 8 * day,
                paymentMethod = PaymentMethod.OTHER,
                referenceNo = "INV-1045",
                note = "Rice 50kg & Sugar 25kg"
            )
        )
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c2Id,
                type = TransactionType.PAYMENT_RECEIVED,
                amount = 100_000L,
                date = now - 2 * day,
                paymentMethod = PaymentMethod.CASH,
                referenceNo = "REC-540",
                note = "Cash payment on delivery"
            )
        )

        // Transactions for Customer 3 (Grace Akello - balance 110,000 > 100,000 credit limit)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c3Id,
                type = TransactionType.SALE_ON_CREDIT,
                amount = 90_000L,
                date = now - 5 * day,
                paymentMethod = PaymentMethod.OTHER,
                referenceNo = "INV-1067",
                note = "Wheat flour 50kg for bakery"
            )
        )

        // Transactions for Customer 4 (John Baptist Mukasa - balance 0 settled)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c4Id,
                type = TransactionType.PAYMENT_RECEIVED,
                amount = 100_000L,
                date = now - 1 * day,
                paymentMethod = PaymentMethod.BANK,
                referenceNo = "BNK-4321",
                note = "Settled previous balance in full"
            )
        )

        // Transactions for Customer 5 (Brenda Kiconco - advance balance -50,000)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = c5Id,
                type = TransactionType.PAYMENT_RECEIVED,
                amount = 50_000L,
                date = now - 4 * day,
                paymentMethod = PaymentMethod.MOBILE_MONEY,
                referenceNo = "MM-3341",
                note = "Deposit towards weekend egg delivery"
            )
        )

        // Transactions for Supplier 1 (Mukwano Wholesale Traders - payable 600,000)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = s1Id,
                type = TransactionType.PURCHASE_ON_CREDIT,
                amount = 500_000L,
                date = now - 16 * day,
                paymentMethod = PaymentMethod.OTHER,
                referenceNo = "SUP-MK-901",
                note = "Stock restock: 5 jerrycans oil & 10 boxes soap"
            )
        )
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = s1Id,
                type = TransactionType.PAYMENT_MADE,
                amount = 300_000L,
                date = now - 7 * day,
                paymentMethod = PaymentMethod.BANK,
                referenceNo = "TX-8921",
                note = "Cheque payment part settlement"
            )
        )

        // Transactions for Supplier 2 (Kampala Flour Mills Ltd - payable 450,000)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = s2Id,
                type = TransactionType.PURCHASE_ON_CREDIT,
                amount = 450_000L,
                date = now - 11 * day,
                paymentMethod = PaymentMethod.OTHER,
                referenceNo = "SUP-KFM-22",
                note = "15 sacks fortified maize meal"
            )
        )

        // Transactions for Supplier 3 (Nile Agro Distributors - payable 0 settled)
        transactionDao.insertTransaction(
            TransactionEntity(
                partyId = s3Id,
                type = TransactionType.PAYMENT_MADE,
                amount = 150_000L,
                date = now - 2 * day,
                paymentMethod = PaymentMethod.MOBILE_MONEY,
                referenceNo = "MM-8891",
                note = "Full clearance for drink crates"
            )
        )

        // Seed 15 sample products & initial stock movements!
        seed15Products(s1Id, s2Id, s3Id)

        // Seed sample sales for today and past days
        seedSampleSales(c1Id, c2Id, c3Id)
    }

    private suspend fun seedSampleSales(c1Id: Long, c2Id: Long, c3Id: Long) {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        val prods = productDao.getAllProductsDirect()
        val pSugar = prods.find { it.sku == "SUG-50K-01" } ?: prods.getOrNull(0)
        val pOil = prods.find { it.sku == "OIL-20L-02" } ?: prods.getOrNull(1)
        val pFlour = prods.find { it.sku == "FLR-50K-03" } ?: prods.getOrNull(2)
        val pRice = prods.find { it.sku == "RCE-50K-04" } ?: prods.getOrNull(3)
        val pSoap = prods.find { it.sku == "SOP-BAR-05" } ?: prods.getOrNull(4)
        val pSalt = prods.find { it.sku == "SLT-01K-06" } ?: prods.getOrNull(5)

        if (pSugar == null) return

        // 1. Sale Today (Cash Walk-in)
        val sale1 = SaleEntity(
            invoiceNumber = "INV-2026-001",
            date = now - 2 * 3600 * 1000L,
            customerId = null,
            customerName = "Walk-in Cash Customer",
            subtotal = 303_000L,
            discountType = DiscountType.FIXED,
            discountValue = 3_000.0,
            discountAmount = 3_000L,
            finalTotal = 300_000L,
            totalCost = 255_000L,
            profit = 45_000L,
            paymentMode = SalePaymentMode.CASH,
            cashTendered = 300_000L,
            changeDue = 0L,
            notes = "Counter retail cash sale"
        )
        val s1Id = saleDao.insertSale(sale1)
        val items1 = mutableListOf<SaleItemEntity>()
        items1.add(
            SaleItemEntity(
                saleId = s1Id,
                invoiceNumber = sale1.invoiceNumber,
                productId = pSugar.id,
                productName = pSugar.name,
                category = pSugar.category,
                quantity = 1.0,
                unit = pSugar.unit,
                costPrice = pSugar.costPrice,
                unitPrice = pSugar.retailPrice,
                subtotal = pSugar.retailPrice,
                profit = pSugar.retailPrice - pSugar.costPrice,
                date = sale1.date
            )
        )
        if (pOil != null) {
            items1.add(
                SaleItemEntity(
                    saleId = s1Id,
                    invoiceNumber = sale1.invoiceNumber,
                    productId = pOil.id,
                    productName = pOil.name,
                    category = pOil.category,
                    quantity = 1.0,
                    unit = pOil.unit,
                    costPrice = pOil.costPrice,
                    unitPrice = pOil.retailPrice,
                    subtotal = pOil.retailPrice,
                    profit = pOil.retailPrice - pOil.costPrice,
                    date = sale1.date
                )
            )
        }
        saleDao.insertSaleItems(items1)

        // 2. Sale Today (Credit - Sarah Namubiru)
        val sale2 = SaleEntity(
            invoiceNumber = "INV-2026-002",
            date = now - 4 * 3600 * 1000L,
            customerId = c1Id,
            customerName = "Sarah Namubiru",
            subtotal = 375_000L,
            discountType = DiscountType.FIXED,
            discountValue = 0.0,
            discountAmount = 0L,
            finalTotal = 375_000L,
            totalCost = 320_000L,
            profit = 55_000L,
            paymentMode = SalePaymentMode.CREDIT,
            cashTendered = 0L,
            changeDue = 0L,
            notes = "Weekly provision supplies on credit"
        )
        val s2Id = saleDao.insertSale(sale2)
        val items2 = mutableListOf<SaleItemEntity>()
        if (pFlour != null) {
            items2.add(
                SaleItemEntity(
                    saleId = s2Id,
                    invoiceNumber = sale2.invoiceNumber,
                    productId = pFlour.id,
                    productName = pFlour.name,
                    category = pFlour.category,
                    quantity = 1.0,
                    unit = pFlour.unit,
                    costPrice = pFlour.costPrice,
                    unitPrice = pFlour.retailPrice,
                    subtotal = pFlour.retailPrice,
                    profit = pFlour.retailPrice - pFlour.costPrice,
                    date = sale2.date
                )
            )
        }
        if (pRice != null) {
            items2.add(
                SaleItemEntity(
                    saleId = s2Id,
                    invoiceNumber = sale2.invoiceNumber,
                    productId = pRice.id,
                    productName = pRice.name,
                    category = pRice.category,
                    quantity = 1.0,
                    unit = pRice.unit,
                    costPrice = pRice.costPrice,
                    unitPrice = pRice.retailPrice,
                    subtotal = pRice.retailPrice,
                    profit = pRice.retailPrice - pRice.costPrice,
                    date = sale2.date
                )
            )
        }
        saleDao.insertSaleItems(items2)

        // 3. Sale Today (Cash - soap & salt)
        val sale3 = SaleEntity(
            invoiceNumber = "INV-2026-003",
            date = now - 35 * 60 * 1000L,
            customerId = null,
            customerName = "Walk-in Cash Customer",
            subtotal = 103_500L,
            discountType = DiscountType.FIXED,
            discountValue = 0.0,
            discountAmount = 0L,
            finalTotal = 103_500L,
            totalCost = 81_000L,
            profit = 22_500L,
            paymentMode = SalePaymentMode.CASH,
            cashTendered = 105_000L,
            changeDue = 1_500L,
            notes = "Soap boxes & table salt"
        )
        val s3Id = saleDao.insertSale(sale3)
        val items3 = mutableListOf<SaleItemEntity>()
        if (pSoap != null) {
            items3.add(
                SaleItemEntity(
                    saleId = s3Id,
                    invoiceNumber = sale3.invoiceNumber,
                    productId = pSoap.id,
                    productName = pSoap.name,
                    category = pSoap.category,
                    quantity = 2.0,
                    unit = pSoap.unit,
                    costPrice = pSoap.costPrice,
                    unitPrice = pSoap.retailPrice,
                    subtotal = pSoap.retailPrice * 2,
                    profit = (pSoap.retailPrice - pSoap.costPrice) * 2,
                    date = sale3.date
                )
            )
        }
        if (pSalt != null) {
            items3.add(
                SaleItemEntity(
                    saleId = s3Id,
                    invoiceNumber = sale3.invoiceNumber,
                    productId = pSalt.id,
                    productName = pSalt.name,
                    category = pSalt.category,
                    quantity = 5.0,
                    unit = pSalt.unit,
                    costPrice = pSalt.costPrice,
                    unitPrice = pSalt.retailPrice,
                    subtotal = pSalt.retailPrice * 5,
                    profit = (pSalt.retailPrice - pSalt.costPrice) * 5,
                    date = sale3.date
                )
            )
        }
        saleDao.insertSaleItems(items3)

        // 4. Sale 2 days ago (David Ochieng)
        val sale4 = SaleEntity(
            invoiceNumber = "INV-2026-004",
            date = now - 2 * day,
            customerId = c2Id,
            customerName = "David Ochieng",
            subtotal = 220_000L,
            discountType = DiscountType.FIXED,
            discountValue = 0.0,
            discountAmount = 0L,
            finalTotal = 220_000L,
            totalCost = 185_000L,
            profit = 35_000L,
            paymentMode = SalePaymentMode.CASH,
            cashTendered = 220_000L,
            changeDue = 0L,
            notes = "Canteen stock purchase"
        )
        val s4Id = saleDao.insertSale(sale4)
        val items4 = mutableListOf<SaleItemEntity>()
        if (pRice != null) {
            items4.add(
                SaleItemEntity(
                    saleId = s4Id,
                    invoiceNumber = sale4.invoiceNumber,
                    productId = pRice.id,
                    productName = pRice.name,
                    category = pRice.category,
                    quantity = 1.0,
                    unit = pRice.unit,
                    costPrice = pRice.costPrice,
                    unitPrice = pRice.retailPrice,
                    subtotal = pRice.retailPrice,
                    profit = pRice.retailPrice - pRice.costPrice,
                    date = sale4.date
                )
            )
        }
        saleDao.insertSaleItems(items4)

        // 5. Sale 5 days ago (Grace Akello)
        val sale5 = SaleEntity(
            invoiceNumber = "INV-2026-005",
            date = now - 5 * day,
            customerId = c3Id,
            customerName = "Grace Akello",
            subtotal = 90_000L,
            discountType = DiscountType.FIXED,
            discountValue = 0.0,
            discountAmount = 0L,
            finalTotal = 90_000L,
            totalCost = 72_000L,
            profit = 18_000L,
            paymentMode = SalePaymentMode.CREDIT,
            cashTendered = 0L,
            changeDue = 0L,
            notes = "Bakery provisions"
        )
        val s5Id = saleDao.insertSale(sale5)
        val items5 = mutableListOf<SaleItemEntity>()
        if (pFlour != null) {
            items5.add(
                SaleItemEntity(
                    saleId = s5Id,
                    invoiceNumber = sale5.invoiceNumber,
                    productId = pFlour.id,
                    productName = pFlour.name,
                    category = pFlour.category,
                    quantity = 0.5,
                    unit = pFlour.unit,
                    costPrice = pFlour.costPrice,
                    unitPrice = 90_000L,
                    subtotal = 90_000L,
                    profit = 18_000L,
                    date = sale5.date
                )
            )
        }
        saleDao.insertSaleItems(items5)
    }

    private suspend fun seed15Products(s1Id: Long?, s2Id: Long?, s3Id: Long?) {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        val products = listOf(
            Product(
                name = "Kakira White Sugar 50kg",
                sku = "SUG-50K-01",
                category = "Commodities",
                unit = "50kg Sack",
                costPrice = 130_000L,
                retailPrice = 155_000L,
                wholesalePrice = 145_000L,
                stockQty = 12.0,
                reorderLevel = 5.0,
                supplierId = s3Id,
                notes = "High-velocity commodity. Sourced from Nile Agro."
            ),
            Product(
                name = "Supreme Fortified Maize Flour 25kg",
                sku = "MZF-25K-02",
                category = "Grains & Flour",
                unit = "25kg Bag",
                costPrice = 48_000L,
                retailPrice = 60_000L,
                wholesalePrice = 54_000L,
                stockQty = 18.0,
                reorderLevel = 6.0,
                supplierId = s2Id,
                notes = "Grade 1 posho flour from Kampala Flour Mills."
            ),
            Product(
                name = "Super Fine Tanzanian Rice 50kg",
                sku = "RCE-50K-03",
                category = "Grains & Flour",
                unit = "50kg Bag",
                costPrice = 140_000L,
                retailPrice = 175_000L,
                wholesalePrice = 160_000L,
                stockQty = 3.0, // LOW STOCK (reorderLevel = 5.0)
                reorderLevel = 5.0,
                supplierId = s2Id,
                notes = "Aromatic long grain rice. Restock needed immediately!"
            ),
            Product(
                name = "Mukwano Fortified Cooking Oil 20L",
                sku = "OIL-20L-04",
                category = "Oils & Fats",
                unit = "20L Jerrycan",
                costPrice = 95_000L,
                retailPrice = 115_000L,
                wholesalePrice = 105_000L,
                stockQty = 8.0,
                reorderLevel = 4.0,
                supplierId = s1Id,
                notes = "Yellow jerrycan. Consistent fast seller."
            ),
            Product(
                name = "White Star Laundry Bar Soap (Box 25)",
                sku = "SOAP-BX-05",
                category = "Hygiene & Cleaning",
                unit = "Box of 25",
                costPrice = 55_000L,
                retailPrice = 70_000L,
                wholesalePrice = 62_000L,
                stockQty = 2.0, // LOW STOCK (reorderLevel = 5.0)
                reorderLevel = 5.0,
                supplierId = s1Id,
                notes = "Long bar multipurpose soap. Only 2 boxes remaining!"
            ),
            Product(
                name = "Blue Band Margarine 500g",
                sku = "BLB-500-06",
                category = "Groceries",
                unit = "Tub",
                costPrice = 6_500L,
                retailPrice = 8_500L,
                wholesalePrice = 7_500L,
                stockQty = 24.0,
                reorderLevel = 10.0,
                supplierId = s3Id,
                notes = "Fortified with Omega 3 & vitamins."
            ),
            Product(
                name = "Omo Hand Washing Powder 500g",
                sku = "OMO-500-07",
                category = "Hygiene & Cleaning",
                unit = "Packet",
                costPrice = 3_800L,
                retailPrice = 5_000L,
                wholesalePrice = 4_400L,
                stockQty = 1.0, // LOW STOCK (reorderLevel = 8.0)
                reorderLevel = 8.0,
                supplierId = s1Id,
                notes = "Critical low stock. Customers requesting daily."
            ),
            Product(
                name = "Rwenzori Mineral Water 500ml (24 Pack)",
                sku = "RWE-24P-08",
                category = "Beverages",
                unit = "Carton of 24",
                costPrice = 11_000L,
                retailPrice = 16_000L,
                wholesalePrice = 13_500L,
                stockQty = 15.0,
                reorderLevel = 5.0,
                supplierId = s3Id,
                notes = "Pure natural mineral water."
            ),
            Product(
                name = "Coca-Cola Classic 300ml Glass (Crate 24)",
                sku = "COK-CRT-09",
                category = "Beverages",
                unit = "Crate of 24",
                costPrice = 22_000L,
                retailPrice = 30_000L,
                wholesalePrice = 26_000L,
                stockQty = 7.0,
                reorderLevel = 3.0,
                supplierId = s3Id,
                notes = "Bottle deposit managed separately."
            ),
            Product(
                name = "Royco Mchuzi Mix Beef 200g",
                sku = "ROY-200-10",
                category = "Spices & Food Additives",
                unit = "Container",
                costPrice = 3_200L,
                retailPrice = 4_500L,
                wholesalePrice = 3_800L,
                stockQty = 0.0, // OUT OF STOCK (reorderLevel = 5.0)
                reorderLevel = 5.0,
                supplierId = s3Id,
                notes = "Completely out of stock! Put on urgent purchase order."
            ),
            Product(
                name = "Tororo Portland Cement 50kg (CEM II)",
                sku = "CEM-50K-11",
                category = "Hardware & Building",
                unit = "50kg Bag",
                costPrice = 32_000L,
                retailPrice = 38_000L,
                wholesalePrice = 35_000L,
                stockQty = 30.0,
                reorderLevel = 10.0,
                supplierId = s3Id,
                notes = "32.5R construction cement. Store on wooden pallets."
            ),
            Product(
                name = "Ariel Active Foam Detergent 1kg",
                sku = "ARL-1KG-12",
                category = "Hygiene & Cleaning",
                unit = "Packet",
                costPrice = 8_000L,
                retailPrice = 10_500L,
                wholesalePrice = 9_200L,
                stockQty = 14.0,
                reorderLevel = 6.0,
                supplierId = s1Id,
                notes = "Premium washing powder."
            ),
            Product(
                name = "Fresh Dairy UHT Whole Milk 500ml (Pack 12)",
                sku = "MLK-12P-13",
                category = "Dairy",
                unit = "Carton of 12",
                costPrice = 24_000L,
                retailPrice = 32_000L,
                wholesalePrice = 28_000L,
                stockQty = 2.0, // LOW STOCK (reorderLevel = 5.0)
                reorderLevel = 5.0,
                supplierId = s3Id,
                notes = "Long life UHT milk pouches. Fast selling in mornings."
            ),
            Product(
                name = "Nomi Multi-Purpose Detergent 1kg",
                sku = "NOM-1KG-14",
                category = "Hygiene & Cleaning",
                unit = "Packet",
                costPrice = 6_000L,
                retailPrice = 8_000L,
                wholesalePrice = 7_000L,
                stockQty = 20.0,
                reorderLevel = 5.0,
                supplierId = s1Id,
                notes = "Economy washing powder."
            ),
            Product(
                name = "Nuvita Digestive Biscuits (Box of 40)",
                sku = "NUV-BX-15",
                category = "Snacks & Confectionery",
                unit = "Box of 40",
                costPrice = 16_000L,
                retailPrice = 22_000L,
                wholesalePrice = 19_000L,
                stockQty = 9.0,
                reorderLevel = 4.0,
                supplierId = s3Id,
                notes = "Snack favorite for schoolchildren."
            )
        )

        for (product in products) {
            val pId = productDao.insertProduct(product)

            // Seed initial baseline price history
            productPriceHistoryDao.insert(
                ProductPriceHistory(
                    productId = pId,
                    costPrice = product.costPrice,
                    retailPrice = product.retailPrice,
                    wholesalePrice = product.wholesalePrice,
                    changeReason = "Initial baseline catalog pricing",
                    date = now - 20 * day
                )
            )

            // Seed an earlier price history adjustment on Kakira Sugar to showcase historical audit trail
            if (product.sku == "SUG-50K-01") {
                productPriceHistoryDao.insert(
                    ProductPriceHistory(
                        productId = pId,
                        costPrice = 130_000L,
                        retailPrice = 155_000L,
                        wholesalePrice = 145_000L,
                        previousCostPrice = 120_000L,
                        previousRetailPrice = 140_000L,
                        previousWholesalePrice = 135_000L,
                        changeReason = "Factory price revision (+10.7%)",
                        date = now - 8 * day
                    )
                )
            }

            // Seed initial Purchase In movement
            stockMovementDao.insertMovement(
                StockMovement(
                    productId = pId,
                    type = StockMovementType.PURCHASE_IN,
                    quantity = product.stockQty + 5.0,
                    isStockIn = true,
                    unitPrice = product.costPrice,
                    date = now - 15 * day,
                    referenceNo = "BATCH-INIT-${pId}",
                    note = "Initial stock intake from supplier",
                    partyId = product.supplierId,
                    resultingStock = product.stockQty + 5.0
                )
            )

            // Seed a Sale Out movement
            if (product.stockQty > 0.0) {
                stockMovementDao.insertMovement(
                    StockMovement(
                        productId = pId,
                        type = StockMovementType.SALE_OUT,
                        quantity = 5.0,
                        isStockIn = false,
                        unitPrice = product.retailPrice,
                        date = now - 4 * day,
                        referenceNo = "POS-SALE-0${pId}",
                        note = "Regular shop counter sale",
                        resultingStock = product.stockQty
                    )
                )
            }
        }
    }
}
