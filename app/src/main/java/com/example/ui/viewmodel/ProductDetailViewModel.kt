package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.Product
import com.example.data.model.ProductPriceHistory
import com.example.data.model.ProductWithSupplier
import com.example.data.model.StockMovement
import com.example.data.model.StockMovementType
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(
    val productId: Long,
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    val productWithSupplier: StateFlow<ProductWithSupplier?> = repository.getProductWithSupplier(productId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val movements: StateFlow<List<StockMovement>> = repository.getStockMovementsForProduct(productId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val priceHistory: StateFlow<List<ProductPriceHistory>> = repository.getPriceHistoryForProduct(productId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currency: StateFlow<String> = settingsManager.settings
        .map { it.currency }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "UGX"
        )

    val suppliers: StateFlow<List<Party>> = repository.allParties
        .map { parties -> parties.filter { it.type == PartyType.SUPPLIER || it.type == PartyType.BOTH } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allowNegativeStock: StateFlow<Boolean> = settingsManager.settings
        .map { it.allowNegativeStock }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearMessages() {
        _userMessage.value = null
        _errorMessage.value = null
    }

    fun recordMovement(
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

    fun updateProduct(
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
        val current = productWithSupplier.value?.product ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = name.trim(),
                sku = sku.trim(),
                category = category.trim().ifBlank { "General" },
                unit = unit.trim().ifBlank { "pcs" },
                costPrice = costPrice,
                retailPrice = retailPrice,
                wholesalePrice = wholesalePrice,
                reorderLevel = reorderLevel,
                supplierId = supplierId,
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProduct(updated)
            _userMessage.value = "Product details saved"
            onSuccess()
        }
    }

    fun deleteProduct(onSuccess: () -> Unit) {
        val current = productWithSupplier.value?.product ?: return
        viewModelScope.launch {
            repository.deleteProduct(current)
            onSuccess()
        }
    }
}
