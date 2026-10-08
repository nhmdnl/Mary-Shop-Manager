package com.example.ui.screens.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Product
import com.example.data.model.ProductWithSupplier
import com.example.data.model.StockMovementType
import com.example.ui.components.BarcodeScannerModal
import com.example.data.repository.ShopRepository
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.DebtRed
import com.example.ui.theme.DebtRedContainer
import com.example.ui.theme.DebtRedText
import com.example.ui.viewmodel.InventorySortOption
import com.example.ui.viewmodel.InventoryViewModel
import com.example.util.CurrencyFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onProductClick: (Long) -> Unit = {}
) {
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val allowNegativeStock by viewModel.allowNegativeStock.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val showLowStockOnly by viewModel.showLowStockOnly.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showBulkPriceDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var movementProduct by remember { mutableStateOf<Product?>(null) }
    var movementInitialType by remember { mutableStateOf(StockMovementType.PURCHASE_IN) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Inventory & Products",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${summary.totalProductsCount} products • $lowStockCount low stock",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBulkPriceDialog = true },
                        modifier = Modifier.testTag("bulk_price_update_button")
                    ) {
                        Icon(Icons.Default.PriceChange, contentDescription = "Bulk Price Update")
                    }
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_inventory_button")
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Sort Inventory")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            InventorySortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.onSortOptionSelected(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Product") },
                modifier = Modifier.testTag("add_product_fab")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar & Barcode Scanner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search product name, SKU, category...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inventory_search_input")
                    )

                    IconButton(
                        onClick = { showBarcodeScanner = true },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            .testTag("inventory_scan_barcode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Product Barcode",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Low Stock Alert Banner (if any low-stock items exist)
            if (lowStockCount > 0) {
                item {
                    Surface(
                        color = if (showLowStockOnly) MaterialTheme.colorScheme.errorContainer else DebtRedContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleLowStockOnly() }
                            .testTag("low_stock_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DebtRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Low stock alert",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$lowStockCount Low Stock Item${if (lowStockCount > 1) "s" else ""}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = if (showLowStockOnly) "Showing low stock only. Tap to show all."
                                    else "Items at or below reorder level. Tap to view list.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            FilterChip(
                                selected = showLowStockOnly,
                                onClick = { viewModel.toggleLowStockOnly() },
                                label = { Text(if (showLowStockOnly) "Viewing Low" else "Filter Low") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DebtRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Category Chips Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null && !showLowStockOnly,
                        onClick = {
                            viewModel.onCategorySelected(null)
                            viewModel.setLowStockOnly(false)
                        },
                        label = { Text("All (${summary.totalProductsCount})") }
                    )

                    FilterChip(
                        selected = showLowStockOnly,
                        onClick = { viewModel.toggleLowStockOnly() },
                        label = {
                            Text("⚠️ Low Stock ($lowStockCount)")
                        },
                        colors = if (lowStockCount > 0) FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DebtRed,
                            selectedLabelColor = Color.White
                        ) else FilterChipDefaults.filterChipColors()
                    )

                    summary.categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory.equals(cat, ignoreCase = true) && !showLowStockOnly,
                            onClick = {
                                viewModel.setLowStockOnly(false)
                                if (selectedCategory.equals(cat, ignoreCase = true)) {
                                    viewModel.onCategorySelected(null)
                                } else {
                                    viewModel.onCategorySelected(cat)
                                }
                            },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // Inventory Summary Overview Cards
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Total Stock Value (Cost)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                CurrencyFormatter.format(summary.totalStockValueAtCost, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Total Potential Retail",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                CurrencyFormatter.format(summary.totalStockValueAtRetail, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Products Header & Current Sort Display
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Products (${products.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sorted by: ${sortOption.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Empty State
            if (products.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || showLowStockOnly || selectedCategory != null)
                                "No products match current filters"
                            else "No products in inventory yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || showLowStockOnly || selectedCategory != null)
                                "Try resetting search or category filters"
                            else "Tap '+ Add Product' below to start tracking your shop items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Products List
            items(
                items = products,
                key = { it.product.id }
            ) { item ->
                ProductItemCard(
                    item = item,
                    currency = currency,
                    onClick = { onProductClick(item.product.id) },
                    onStockInClick = {
                        movementProduct = item.product
                        movementInitialType = StockMovementType.PURCHASE_IN
                    },
                    onStockOutClick = {
                        movementProduct = item.product
                        movementInitialType = StockMovementType.SALE_OUT
                    }
                )
            }
        }
    }

    // Add Product Dialog
    if (showAddDialog) {
        AddProductDialog(
            currency = currency,
            suppliers = suppliers,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, sku, category, unit, cost, retail, wholesale, stock, reorder, suppId, notes ->
                viewModel.addProduct(
                    name = name,
                    sku = sku,
                    category = category,
                    unit = unit,
                    costPrice = cost,
                    retailPrice = retail,
                    wholesalePrice = wholesale,
                    stockQty = stock,
                    reorderLevel = reorder,
                    supplierId = suppId,
                    notes = notes,
                    onSuccess = { showAddDialog = false }
                )
            }
        )
    }

    // Quick Movement Dialog
    movementProduct?.let { prod ->
        RecordStockMovementDialog(
            product = prod,
            currency = currency,
            allowNegativeStock = allowNegativeStock,
            parties = suppliers,
            initialType = movementInitialType,
            onDismiss = { movementProduct = null },
            onConfirm = { type, qty, isAddition, unitPrice, ref, note, partyId ->
                viewModel.recordStockMovement(
                    productId = prod.id,
                    type = type,
                    quantity = qty,
                    isStockIn = isAddition,
                    unitPrice = unitPrice,
                    referenceNo = ref,
                    note = note,
                    partyId = partyId,
                    onSuccess = { movementProduct = null }
                )
            }
        )
    }

    if (showBulkPriceDialog) {
        val categories = products.map { it.product.category }.distinct().filter { it.isNotBlank() }
        BulkPriceUpdateDialog(
            categories = categories,
            currentSelectedCategory = selectedCategory,
            currency = currency,
            onDismiss = { showBulkPriceDialog = false },
            onConfirm = { cat, isPct, adjVal, retail, wholesale, cost, reason ->
                viewModel.bulkUpdateCategoryPrices(
                    category = cat,
                    isPercentage = isPct,
                    adjustmentValue = adjVal,
                    updateRetail = retail,
                    updateWholesale = wholesale,
                    updateCost = cost,
                    reason = reason,
                    onSuccess = { showBulkPriceDialog = false }
                )
            }
        )
    }

    if (showBarcodeScanner) {
        BarcodeScannerModal(
            title = "Scan Product Barcode",
            subtitle = "Scan barcode to search inventory or enter SKU",
            onBarcodeScanned = { scannedCode ->
                showBarcodeScanner = false
                viewModel.onSearchQueryChanged(scannedCode)
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }
}


@Composable
fun ProductItemCard(
    item: ProductWithSupplier,
    currency: String,
    onClick: () -> Unit,
    onStockInClick: () -> Unit,
    onStockOutClick: () -> Unit
) {
    val product = item.product
    val isOut = item.isOutOfStock
    val isLow = item.isLowStock

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Category, SKU badge & Stock Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (product.sku.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = product.sku,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Stock status badge
                val (badgeBg, badgeFg, badgeText) = when {
                    isOut -> Triple(DebtRedContainer, DebtRedText, "Out of Stock (0)")
                    isLow -> Triple(
                        AmberContainer,
                        AmberOnContainer,
                        "Low: ${ShopRepository.formatQty(product.stockQty)} ${product.unit}"
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        "${ShopRepository.formatQty(product.stockQty)} ${product.unit}"
                    )
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLow || isOut) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Product Name & Unit
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Supplier link if available
            item.supplier?.let { supp ->
                Text(
                    text = "Supplier: ${supp.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pricing Grid: Cost, Retail, Margin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Cost Price",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        CurrencyFormatter.format(product.costPrice, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column {
                    Text(
                        "Retail Price",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        CurrencyFormatter.format(product.retailPrice, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Margin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val marginFormatted = String.format(Locale.US, "+%.1f%%", item.marginPercent)
                    Text(
                        marginFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.marginPercent >= 20.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Action Row: Quick Stock In / Out buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStockInClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("quick_stock_in_${product.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stock In (+)", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onStockOutClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("quick_stock_out_${product.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stock Out (-)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
