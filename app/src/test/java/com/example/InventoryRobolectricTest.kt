package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.BulkAdjustmentMode
import com.example.data.model.BulkPriceTarget
import com.example.data.model.DiscountType
import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.PriceTier
import com.example.data.model.Product
import com.example.data.model.SaleLineItem
import com.example.data.model.SalePaymentMode
import com.example.data.model.StockMovementType
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import com.example.util.PricingHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InventoryRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var settingsManager: ShopSettingsManager
    private lateinit var repository: ShopRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsManager = ShopSettingsManager(context)
        // ensure default strict stock control
        settingsManager.updateAllowNegativeStock(false)

        repository = ShopRepository(
            database = database,
            partyDao = database.partyDao(),
            transactionDao = database.transactionDao(),
            productDao = database.productDao(),
            stockMovementDao = database.stockMovementDao(),
            productPriceHistoryDao = database.productPriceHistoryDao(),
            settingsManager = settingsManager
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testProductCreationAndInitialStock() = runBlocking {
        val product = Product(
            name = "Sugar 50kg",
            sku = "SUG-50",
            category = "Commodities",
            unit = "50kg Sack",
            costPrice = 130_000L,
            retailPrice = 155_000L,
            wholesalePrice = 145_000L,
            stockQty = 10.0,
            reorderLevel = 3.0
        )
        val id = repository.addProduct(product)
        assertTrue(id > 0)

        val retrieved = repository.getProduct(id).first()
        assertEquals("Sugar 50kg", retrieved?.name)
        assertEquals(10.0, retrieved?.stockQty ?: 0.0, 0.001)
        assertEquals(155_000L, retrieved?.retailPrice)
    }

    @Test
    fun testStockMovementPurchaseInAndSaleOut() = runBlocking {
        val product = Product(
            name = "Bar Soap Box",
            sku = "SOAP-25",
            category = "Hygiene",
            unit = "Box",
            costPrice = 55_000L,
            retailPrice = 70_000L,
            stockQty = 5.0,
            reorderLevel = 2.0
        )
        val id = repository.addProduct(product)

        // Purchase In 10 units -> total 15
        val purchaseResult = repository.recordStockMovement(
            productId = id,
            type = StockMovementType.PURCHASE_IN,
            quantity = 10.0,
            isStockIn = true,
            unitPrice = 55_000L,
            referenceNo = "PO-101",
            note = "Weekly restock",
            partyId = null
        )
        assertTrue(purchaseResult.isSuccess)
        var updated = repository.getProduct(id).first()
        assertEquals(15.0, updated?.stockQty ?: 0.0, 0.001)

        // Sale Out 4 units -> total 11
        val saleResult = repository.recordStockMovement(
            productId = id,
            type = StockMovementType.SALE_OUT,
            quantity = 4.0,
            isStockIn = false,
            unitPrice = 70_000L,
            referenceNo = "SALE-01",
            note = "Customer counter purchase",
            partyId = null
        )
        assertTrue(saleResult.isSuccess)
        updated = repository.getProduct(id).first()
        assertEquals(11.0, updated?.stockQty ?: 0.0, 0.001)

        // Verify movement records
        val movements = repository.getStockMovementsForProduct(id).first()
        assertEquals(2, movements.size)
    }

    @Test
    fun testStockCannotGoBelowZeroWhenDisallowed() = runBlocking {
        settingsManager.updateAllowNegativeStock(false)

        val product = Product(
            name = "Limited Item",
            sku = "LIM-01",
            category = "General",
            unit = "pcs",
            costPrice = 1000L,
            retailPrice = 2000L,
            stockQty = 3.0,
            reorderLevel = 1.0
        )
        val id = repository.addProduct(product)

        // Try to sell 5 when only 3 in stock
        val result = repository.recordStockMovement(
            productId = id,
            type = StockMovementType.SALE_OUT,
            quantity = 5.0,
            isStockIn = false,
            unitPrice = 2000L,
            referenceNo = "FAIL-SALE",
            note = "Excess sale",
            partyId = null
        )

        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertTrue(errorMsg.contains("cannot fall below zero"))

        // Stock remains 3.0
        val updated = repository.getProduct(id).first()
        assertEquals(3.0, updated?.stockQty ?: 0.0, 0.001)
    }

    @Test
    fun testStockCanGoBelowZeroWhenAllowedBySetting() = runBlocking {
        settingsManager.updateAllowNegativeStock(true)

        val product = Product(
            name = "Flexible Item",
            sku = "FLEX-01",
            category = "General",
            unit = "pcs",
            costPrice = 1000L,
            retailPrice = 2000L,
            stockQty = 2.0,
            reorderLevel = 1.0
        )
        val id = repository.addProduct(product)

        // Sell 5 when only 2 in stock -> resulting stock is -3.0
        val result = repository.recordStockMovement(
            productId = id,
            type = StockMovementType.SALE_OUT,
            quantity = 5.0,
            isStockIn = false,
            unitPrice = 2000L,
            referenceNo = "NEG-SALE",
            note = "Allowed negative sale",
            partyId = null
        )

        assertTrue(result.isSuccess)
        val updated = repository.getProduct(id).first()
        assertEquals(-3.0, updated?.stockQty ?: 0.0, 0.001)
    }

    @Test
    fun testLowStockCountCalculation() = runBlocking {
        repository.addProduct(
            Product(name = "Low Item 1", sku = "L1", category = "G", unit = "pcs", costPrice = 1, retailPrice = 2, stockQty = 2.0, reorderLevel = 5.0)
        )
        repository.addProduct(
            Product(name = "Low Item 2", sku = "L2", category = "G", unit = "pcs", costPrice = 1, retailPrice = 2, stockQty = 0.0, reorderLevel = 3.0)
        )
        repository.addProduct(
            Product(name = "Healthy Item", sku = "H1", category = "G", unit = "pcs", costPrice = 1, retailPrice = 2, stockQty = 20.0, reorderLevel = 5.0)
        )

        val lowCount = repository.lowStockCount.first()
        assertEquals(2, lowCount)
    }

    @Test
    fun testLiveMarginAndMarkupCalculation() {
        // Cost: 8,000, Retail: 10,000 -> Margin: (10000 - 8000)/10000 = 20%
        // Markup: (10000 - 8000)/8000 = 25%
        val margin = PricingHelper.calculateMarginPercent(10_000L, 8_000L)
        val markup = PricingHelper.calculateMarkupPercent(10_000L, 8_000L)

        assertEquals(20.0, margin, 0.01)
        assertEquals(25.0, markup, 0.01)
    }

    @Test
    fun testCompleteSaleReducesStockAndAppliesDiscount() = runBlocking {
        val prod = Product(
            name = "Cooking Oil 1L",
            sku = "OIL-1L",
            category = "Cooking",
            unit = "bottles",
            costPrice = 7_000L,
            retailPrice = 9_000L,
            wholesalePrice = 8_500L,
            stockQty = 10.0,
            reorderLevel = 2.0
        )
        val prodId = repository.addProduct(prod)
        val savedProd = repository.getProduct(prodId).first()!!

        val lineItem = SaleLineItem(
            product = savedProd,
            quantity = 2.0,
            priceTier = PriceTier.RETAIL,
            unitPrice = savedProd.retailPrice
        )

        // Subtotal = 2 * 9,000 = 18,000. Apply 10% discount -> 1800 off -> Final 16,200.
        val saleResult = repository.completeSale(
            items = listOf(lineItem),
            customer = null,
            paymentMode = SalePaymentMode.CASH,
            discountType = DiscountType.PERCENTAGE,
            discountValue = 10.0,
            cashTendered = 20_000L,
            invoiceNumber = "INV-TEST-001"
        )

        assertTrue(saleResult.isSuccess)
        val receipt = saleResult.getOrThrow()
        assertEquals(18_000L, receipt.subtotal)
        assertEquals(1_800L, receipt.discountAmount)
        assertEquals(16_200L, receipt.finalTotal)
        assertEquals(3_800L, receipt.changeDue)

        // Verify stock reduced from 10 to 8
        val updatedProd = repository.getProduct(prodId).first()!!
        assertEquals(8.0, updatedProd.stockQty, 0.001)

        // Verify stock movement
        val movements = repository.getStockMovementsForProduct(prodId).first()
        val saleOutMovement = movements.find { it.type == StockMovementType.SALE_OUT }
        assertTrue(saleOutMovement != null)
        assertEquals(2.0, saleOutMovement!!.quantity, 0.001)
    }

    @Test
    fun testCreditSalePostsToCustomerLedger() = runBlocking {
        val customer = Party(
            name = "Test Debtor",
            phone = "0700000001",
            type = PartyType.CUSTOMER,
            openingBalance = 0L,
            creditLimit = 100_000L
        )
        val customerId = repository.addParty(customer)
        val savedCustomer = customer.copy(id = customerId)

        val prod = Product(
            name = "Rice 25kg",
            sku = "RICE-25",
            category = "Grains",
            unit = "bags",
            costPrice = 80_000L,
            retailPrice = 95_000L,
            wholesalePrice = 90_000L,
            stockQty = 5.0,
            reorderLevel = 1.0
        )
        val prodId = repository.addProduct(prod)
        val savedProd = repository.getProduct(prodId).first()!!

        val lineItem = SaleLineItem(
            product = savedProd,
            quantity = 1.0,
            priceTier = PriceTier.RETAIL,
            unitPrice = 95_000L
        )

        val result = repository.completeSale(
            items = listOf(lineItem),
            customer = savedCustomer,
            paymentMode = SalePaymentMode.CREDIT,
            discountType = DiscountType.FIXED,
            discountValue = 5_000.0,
            cashTendered = 0L,
            invoiceNumber = "INV-CREDIT-01",
            notes = "Bakery order on credit"
        )

        assertTrue(result.isSuccess)
        val receipt = result.getOrThrow()
        assertEquals(90_000L, receipt.finalTotal)

        // Verify customer ledger balance now owes 90,000 UGX
        val partyBalance = repository.getPartyBalanceDirect(customerId)
        assertEquals(90_000L, partyBalance)
        val ledger = repository.getLedgerForParty(customerId).first()
        assertEquals(1, ledger.size)
        assertEquals("INV-CREDIT-01", ledger.first().transaction.referenceNo)
    }

    @Test
    fun testBulkPriceUpdateByCategory() = runBlocking {
        repository.addProduct(
            Product(name = "Juice Box 1L", sku = "JB-1", category = "Beverages", unit = "pcs", costPrice = 4000L, retailPrice = 5000L, wholesalePrice = 4500L, stockQty = 20.0, reorderLevel = 5.0)
        )
        repository.addProduct(
            Product(name = "Soda Can 330ml", sku = "SC-1", category = "Beverages", unit = "pcs", costPrice = 1500L, retailPrice = 2000L, wholesalePrice = 1800L, stockQty = 30.0, reorderLevel = 10.0)
        )

        // Bulk update: 10% increase to both retail & wholesale
        val bulkResult = repository.bulkUpdateCategoryPrices(
            category = "Beverages",
            targetPrice = BulkPriceTarget.BOTH_RETAIL_AND_WHOLESALE,
            adjustmentMode = BulkAdjustmentMode.PERCENTAGE,
            adjustmentValue = 10.0,
            isIncrease = true,
            reason = "Inflation adjustment"
        )

        assertTrue(bulkResult.isSuccess)
        assertEquals(2, bulkResult.getOrThrow())

        val allProds = repository.allProductsWithSuppliers.first().map { it.product }
        val juice = allProds.find { it.sku == "JB-1" }!!
        val soda = allProds.find { it.sku == "SC-1" }!!

        // Juice retail: 5000 + 10% = 5500; wholesale: 4500 + 10% = 4950
        assertEquals(5500L, juice.retailPrice)
        assertEquals(4950L, juice.wholesalePrice)

        // Soda retail: 2000 + 10% = 2200; wholesale: 1800 + 10% = 1980
        assertEquals(2200L, soda.retailPrice)
        assertEquals(1980L, soda.wholesalePrice)

        // Verify price history was recorded for both
        val juiceHistory = repository.getPriceHistoryForProduct(juice.id).first()
        assertTrue(juiceHistory.isNotEmpty())
        assertEquals(5500L, juiceHistory.first().retailPrice)
        assertTrue(juiceHistory.first().changeReason.contains("Inflation adjustment"))
    }
}
