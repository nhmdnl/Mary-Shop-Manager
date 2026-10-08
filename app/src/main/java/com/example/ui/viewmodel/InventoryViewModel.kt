package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BulkAdjustmentMode
import com.example.data.model.BulkPriceTarget
import com.example.data.model.InventorySummary
import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.Product
import com.example.data.model.ProductWithSupplier
import com.example.data.model.StockMovement
import com.example.data.model.StockMovementType
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class InventorySortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    STOCK_ASC("Lowest Stock First"),
    STOCK_DESC("Highest Stock First"),
    MARGIN_DESC("Highest Margin %"),
    PRICE_DESC("Highest Retail Price")
}

class InventoryViewModel(
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _showLowStockOnly = MutableStateFlow(false)
    val showLowStockOnly: StateFlow<Boolean> = _showLowStockOnly.asStateFlow()

    private val _sortOption = MutableStateFlow(InventorySortOption.STOCK_ASC)
    val sortOption: StateFlow<InventorySortOption> = _sortOption.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

    val summary: StateFlow<InventorySummary> = repository.inventorySummary
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = InventorySummary()
        )

    val lowStockCount: StateFlow<Int> = repository.lowStockCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val suppliers: StateFlow<List<Party>> = repository.allParties
        .map { parties -> parties.filter { it.type == PartyType.SUPPLIER || it.type == PartyType.BOTH } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredProducts: StateFlow<List<ProductWithSupplier>> = combine(
        repository.allProductsWithSuppliers,
        _searchQuery,
        _selectedCategory,
        _showLowStockOnly,
        _sortOption
    ) { products, query, cat, lowOnly, sort ->
        products
            .filter { item ->
                val p = item.product
                val matchesQuery = query.isBlank() ||
                        p.name.contains(query, ignoreCase = true) ||
                        p.sku.contains(query, ignoreCase = true) ||
                        p.category.contains(query, ignoreCase = true) ||
                        (item.supplier?.name?.contains(query, ignoreCase = true) == true)

                val matchesCategory = cat == null || p.category.equals(cat, ignoreCase = true)

                val matchesLowStock = !lowOnly || item.isLowStock

                matchesQuery && matchesCategory && matchesLowStock
            }
            .sortedWith { a, b ->
                val pa = a.product
                val pb = b.product
                when (sort) {
                    InventorySortOption.NAME_ASC -> pa.name.compareTo(pb.name, ignoreCase = true)
                    InventorySortOption.STOCK_ASC -> pa.stockQty.compareTo(pb.stockQty)
                    InventorySortOption.STOCK_DESC -> pb.stockQty.compareTo(pa.stockQty)
                    InventorySortOption.MARGIN_DESC -> b.marginPercent.compareTo(a.marginPercent)
                    InventorySortOption.PRICE_DESC -> pb.retailPrice.compareTo(pa.retailPrice)
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleLowStockOnly() {
        _showLowStockOnly.value = !_showLowStockOnly.value
    }

    fun setLowStockOnly(enable: Boolean) {
        _showLowStockOnly.value = enable
    }

    fun onSortOptionSelected(option: InventorySortOption) {
        _sortOption.value = option
    }

    fun clearMessages() {
        _userMessage.value = null
        _errorMessage.value = null
    }

    fun addProduct(
        name: String,
        sku: String,
        category: String,
        unit: String,
        costPrice: Long,
        retailPrice: Long,
        wholesalePrice: Long,
        stockQty: Double,
        reorderLevel: Double,
        supplierId: Long?,
        notes: String,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val product = Product(
                name = name.trim(),
                sku = sku.trim(),
                category = category.trim().ifBlank { "General" },
                unit = unit.trim().ifBlank { "pcs" },
                costPrice = costPrice,
                retailPrice = retailPrice,
                wholesalePrice = wholesalePrice,
                stockQty = stockQty,
                reorderLevel = reorderLevel,
                supplierId = supplierId,
                notes = notes.trim()
            )
            val newId = repository.addProduct(product)

            if (stockQty > 0.0) {
                repository.recordStockMovement(
                    productId = newId,
                    type = StockMovementType.PURCHASE_IN,
                    quantity = stockQty,
                    isStockIn = true,
                    unitPrice = costPrice,
                    referenceNo = "INIT-STOCK",
                    note = "Initial stock quantity when product created",
                    partyId = supplierId
                )
            }

            _userMessage.value = "Product '$name' added successfully"
            onSuccess(newId)
        }
    }

    fun updateProduct(
        id: Long,
        name: String,
        sku: String,
        category: String,
        unit: String,
        costPrice: Long,
        retailPrice: Long,
        wholesalePrice: Long,
        reorderLevel: Double,
        supplierId: Long?,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val current = repository.getProduct(id)
            // fetch current directly or update
            // let's create updated Product object
            // to preserve current stockQty
            val existing = repository.getProductWithSupplier(id)
            // We can fetch from repository
            viewModelScope.launch {
                // repository update
            }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            _userMessage.value = "'${product.name}' removed from inventory"
        }
    }

    fun recordStockMovement(
        productId: Long,
        type: StockMovementType,
        quantity: Double,
        isStockIn: Boolean,
        unitPrice: Long?,
        referenceNo: String,
        note: String,
        partyId: Long?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.recordStockMovement(
                productId = productId,
                type = type,
                quantity = quantity,
                isStockIn = isStockIn,
                unitPrice = unitPrice,
                referenceNo = referenceNo,
                note = note,
                partyId = partyId
            )

            result.fold(
                onSuccess = { movement ->
                    val sign = if (isStockIn) "+" else "-"
                    _userMessage.value = "Stock updated: $sign${ShopRepository.formatQty(quantity)} (${type.label}). Resulting stock: ${ShopRepository.formatQty(movement.resultingStock)}"
                    onSuccess()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to record stock movement"
                }
            )
        }
    }

    fun bulkUpdateCategoryPrices(
        category: String,
        isPercentage: Boolean,
        adjustmentValue: Double,
        updateRetail: Boolean,
        updateWholesale: Boolean,
        updateCost: Boolean,
        reason: String,
        onSuccess: (updatedCount: Int) -> Unit
    ) {
        viewModelScope.launch {
            val target = when {
                updateCost -> BulkPriceTarget.COST
                updateRetail && updateWholesale -> BulkPriceTarget.BOTH_RETAIL_AND_WHOLESALE
                updateRetail -> BulkPriceTarget.RETAIL
                updateWholesale -> BulkPriceTarget.WHOLESALE
                else -> BulkPriceTarget.RETAIL
            }
            val mode = if (isPercentage) BulkAdjustmentMode.PERCENTAGE else BulkAdjustmentMode.FIXED_AMOUNT
            val isIncrease = adjustmentValue >= 0.0
            val absVal = kotlin.math.abs(adjustmentValue)

            val result = repository.bulkUpdateCategoryPrices(
                category = category,
                targetPrice = target,
                adjustmentMode = mode,
                adjustmentValue = absVal,
                isIncrease = isIncrease,
                reason = reason
            )

            result.fold(
                onSuccess = { count ->
                    _userMessage.value = "Updated prices for $count products in '$category'"
                    onSuccess(count)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to update prices"
                }
            )
        }
    }
}
