package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiscountType
import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.PriceTier
import com.example.data.model.Product
import com.example.data.model.ProductWithSupplier
import com.example.data.model.SaleLineItem
import com.example.data.model.SalePaymentMode
import com.example.data.model.SaleReceipt
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesViewModel(
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    val currency: StateFlow<String> = settingsManager.settings
        .map { it.currency }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "UGX"
        )

    val allowNegativeStock: StateFlow<Boolean> = settingsManager.settings
        .map { it.allowNegativeStock }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val shopName: StateFlow<String> = settingsManager.settings
        .map { it.shopName }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Mary Shop"
        )

    val shopPhone: StateFlow<String> = settingsManager.settings
        .map { it.shopPhone }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val allCustomers: StateFlow<List<Party>> = repository.allParties
        .map { parties -> parties.filter { it.type == PartyType.CUSTOMER || it.type == PartyType.BOTH } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val filteredProducts: StateFlow<List<ProductWithSupplier>> = combine(
        repository.allProductsWithSuppliers,
        _searchQuery,
        _selectedCategory
    ) { products, query, cat ->
        products.filter { item ->
            val p = item.product
            val matchesQuery = query.isBlank() ||
                    p.name.contains(query, ignoreCase = true) ||
                    p.sku.contains(query, ignoreCase = true) ||
                    p.category.contains(query, ignoreCase = true)

            val matchesCat = cat == null || p.category.equals(cat, ignoreCase = true)
            matchesQuery && matchesCat
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedCustomer = MutableStateFlow<Party?>(null)
    val selectedCustomer: StateFlow<Party?> = _selectedCustomer.asStateFlow()

    private val _activePricingTier = MutableStateFlow(PriceTier.RETAIL)
    val activePricingTier: StateFlow<PriceTier> = _activePricingTier.asStateFlow()

    private val _cartItems = MutableStateFlow<List<SaleLineItem>>(emptyList())
    val cartItems: StateFlow<List<SaleLineItem>> = _cartItems.asStateFlow()

    private val _discountType = MutableStateFlow(DiscountType.PERCENTAGE)
    val discountType: StateFlow<DiscountType> = _discountType.asStateFlow()

    private val _discountValueText = MutableStateFlow("")
    val discountValueText: StateFlow<String> = _discountValueText.asStateFlow()

    private val _paymentMode = MutableStateFlow(SalePaymentMode.CASH)
    val paymentMode: StateFlow<SalePaymentMode> = _paymentMode.asStateFlow()

    private val _cashTenderedText = MutableStateFlow("")
    val cashTenderedText: StateFlow<String> = _cashTenderedText.asStateFlow()

    private val _saleNotes = MutableStateFlow("")
    val saleNotes: StateFlow<String> = _saleNotes.asStateFlow()

    private val _completedReceipt = MutableStateFlow<SaleReceipt?>(null)
    val completedReceipt: StateFlow<SaleReceipt?> = _completedReceipt.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun selectCustomer(customer: Party?) {
        _selectedCustomer.value = customer
    }

    fun setPricingTier(tier: PriceTier) {
        _activePricingTier.value = tier
        // Update items in cart to match new tier unless custom price was manually typed
        _cartItems.value = _cartItems.value.map { item ->
            if (item.priceTier == PriceTier.CUSTOM && item.customPrice != null) {
                item
            } else {
                val newPrice = when (tier) {
                    PriceTier.RETAIL -> item.product.retailPrice
                    PriceTier.WHOLESALE -> if (item.product.wholesalePrice > 0L) item.product.wholesalePrice else item.product.retailPrice
                    PriceTier.CUSTOM -> item.unitPrice
                }
                item.copy(priceTier = tier, unitPrice = newPrice)
            }
        }
    }

    fun addProductToCart(product: Product) {
        val currentList = _cartItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.product.id == product.id }

        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            val newQty = existing.quantity + 1.0
            currentList[existingIndex] = existing.copy(quantity = newQty)
            _cartItems.value = currentList
        } else {
            val tier = _activePricingTier.value
            val unitPrice = when (tier) {
                PriceTier.RETAIL -> product.retailPrice
                PriceTier.WHOLESALE -> if (product.wholesalePrice > 0L) product.wholesalePrice else product.retailPrice
                PriceTier.CUSTOM -> product.retailPrice
            }
            val newItem = SaleLineItem(
                product = product,
                quantity = 1.0,
                priceTier = tier,
                unitPrice = unitPrice
            )
            currentList.add(newItem)
            _cartItems.value = currentList
        }
    }

    fun updateItemQuantity(productId: Long, newQty: Double) {
        if (newQty <= 0.0) {
            removeItemFromCart(productId)
            return
        }
        _cartItems.value = _cartItems.value.map { item ->
            if (item.product.id == productId) {
                item.copy(quantity = newQty)
            } else item
        }
    }

    fun updateItemUnitPrice(productId: Long, newPrice: Long) {
        val validPrice = newPrice.coerceAtLeast(0L)
        _cartItems.value = _cartItems.value.map { item ->
            if (item.product.id == productId) {
                item.copy(
                    unitPrice = validPrice,
                    customPrice = validPrice,
                    priceTier = PriceTier.CUSTOM
                )
            } else item
        }
    }

    fun removeItemFromCart(productId: Long) {
        _cartItems.value = _cartItems.value.filter { it.product.id != productId }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _discountValueText.value = ""
        _cashTenderedText.value = ""
        _saleNotes.value = ""
    }

    fun setDiscountType(type: DiscountType) {
        _discountType.value = type
    }

    fun setDiscountValueText(text: String) {
        _discountValueText.value = text
    }

    fun setPaymentMode(mode: SalePaymentMode) {
        _paymentMode.value = mode
    }

    fun setCashTenderedText(text: String) {
        _cashTenderedText.value = text
    }

    fun setSaleNotes(notes: String) {
        _saleNotes.value = notes
    }

    fun dismissReceipt() {
        _completedReceipt.value = null
    }

    fun clearMessages() {
        _userMessage.value = null
        _errorMessage.value = null
    }

    fun completeSale() {
        val items = _cartItems.value
        if (items.isEmpty()) {
            _errorMessage.value = "Please add at least one product to the sale"
            return
        }

        val mode = _paymentMode.value
        val customer = _selectedCustomer.value

        if (mode == SalePaymentMode.CREDIT && customer == null) {
            _errorMessage.value = "A credit sale requires selecting a customer to post to their ledger"
            return
        }

        val discountNum = _discountValueText.value.toDoubleOrNull() ?: 0.0
        val tendered = _cashTenderedText.value.toLongOrNull() ?: 0L
        val invoiceNo = "INV-" + SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())

        viewModelScope.launch {
            val result = repository.completeSale(
                items = items,
                customer = customer,
                paymentMode = mode,
                discountType = _discountType.value,
                discountValue = discountNum,
                cashTendered = tendered,
                invoiceNumber = invoiceNo,
                notes = _saleNotes.value
            )

            result.fold(
                onSuccess = { receipt ->
                    _completedReceipt.value = receipt
                    _cartItems.value = emptyList()
                    _discountValueText.value = ""
                    _cashTenderedText.value = ""
                    _saleNotes.value = ""
                    _userMessage.value = "Sale ${receipt.invoiceNumber} recorded successfully!"
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to complete sale"
                }
            )
        }
    }

    fun scanBarcode(rawCode: String) {
        val cleanCode = rawCode.trim()
        if (cleanCode.isBlank()) return

        viewModelScope.launch {
            val allProds = repository.allProductsWithSuppliers.first()
            val match = allProds.firstOrNull {
                it.product.sku.equals(cleanCode, ignoreCase = true) ||
                it.product.sku.trim() == cleanCode
            }

            if (match != null) {
                addProductToCart(match.product)
                _userMessage.value = "Scanned & added: ${match.product.name}"
            } else {
                _searchQuery.value = cleanCode
                _errorMessage.value = "No product found with barcode '$cleanCode'. Showing search or enter manually."
            }
        }
    }
}

