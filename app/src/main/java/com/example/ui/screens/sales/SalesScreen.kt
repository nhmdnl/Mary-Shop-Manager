package com.example.ui.screens.sales

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DiscountType
import com.example.data.model.Party
import com.example.data.model.PriceTier
import com.example.data.model.Product
import com.example.data.model.ProductWithSupplier
import com.example.data.model.SaleLineItem
import com.example.data.model.SalePaymentMode
import com.example.data.model.SaleReceipt
import com.example.data.repository.ShopRepository
import com.example.ui.components.BarcodeScannerModal
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.CreditGreenContainer
import com.example.ui.theme.CreditGreenText
import com.example.ui.theme.DebtRed
import com.example.ui.theme.DebtRedContainer
import com.example.ui.theme.DebtRedText
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import com.example.util.PdfGenerator
import com.example.util.ShareHelper
import com.example.ui.viewmodel.SalesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    viewModel: SalesViewModel,
    onNavigateToParties: () -> Unit
) {
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val activePricingTier by viewModel.activePricingTier.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val discountType by viewModel.discountType.collectAsStateWithLifecycle()
    val discountValueText by viewModel.discountValueText.collectAsStateWithLifecycle()
    val paymentMode by viewModel.paymentMode.collectAsStateWithLifecycle()
    val cashTenderedText by viewModel.cashTenderedText.collectAsStateWithLifecycle()
    val saleNotes by viewModel.saleNotes.collectAsStateWithLifecycle()
    val completedReceipt by viewModel.completedReceipt.collectAsStateWithLifecycle()
    val shopName by viewModel.shopName.collectAsStateWithLifecycle()
    val shopPhone by viewModel.shopPhone.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCustomerMenu by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }

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

    // Calculations
    val subtotal = cartItems.sumOf { it.subtotal }
    val discountNum = discountValueText.toDoubleOrNull() ?: 0.0
    val discountAmount = when (discountType) {
        DiscountType.PERCENTAGE -> {
            val pct = discountNum.coerceIn(0.0, 100.0)
            (subtotal * (pct / 100.0)).toLong()
        }
        DiscountType.FIXED -> {
            discountNum.toLong().coerceIn(0L, subtotal)
        }
    }
    val finalTotal = (subtotal - discountAmount).coerceAtLeast(0L)
    val cashTendered = cashTenderedText.toLongOrNull() ?: 0L
    val changeDue = if (paymentMode == SalePaymentMode.CASH) {
        (cashTendered - finalTotal).coerceAtLeast(0L)
    } else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text("Quick Sale / POS", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${cartItems.size} items in cart • Total: ${CurrencyFormatter.format(finalTotal, currency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (cartItems.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearCart() },
                            modifier = Modifier.testTag("clear_cart_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Cart")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Customer Selection & Pricing Tier Row
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Customer Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Customer",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedCustomer != null) {
                                Text(
                                    text = "Reset to Walk-in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { viewModel.selectCustomer(null) }
                                )
                            }
                        }

                        // Customer Picker Box
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCustomer?.name ?: "Walk-in Customer (Cash)",
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showCustomerMenu = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Customer")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCustomerMenu = true }
                                    .testTag("customer_selector_field")
                            )

                            DropdownMenu(
                                expanded = showCustomerMenu,
                                onDismissRequest = { showCustomerMenu = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Walk-in Customer (Cash)") },
                                    onClick = {
                                        viewModel.selectCustomer(null)
                                        showCustomerMenu = false
                                    }
                                )
                                allCustomers.forEach { customer ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(customer.name, fontWeight = FontWeight.SemiBold)
                                                if (customer.phone.isNotBlank()) {
                                                    Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectCustomer(customer)
                                            showCustomerMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Pricing Tier Selection
                        Text(
                            text = "Pricing Tier",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PriceTier.entries.forEach { tier ->
                                FilterChip(
                                    selected = activePricingTier == tier,
                                    onClick = { viewModel.setPricingTier(tier) },
                                    label = { Text(tier.label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pricing_tier_${tier.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // 2. Product Search & Catalog Carousel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Add Products to Sale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search by name, SKU or barcode...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pos_product_search_input")
                        )

                        IconButton(
                            onClick = { showBarcodeScanner = true },
                            modifier = Modifier
                                .size(52.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                .testTag("btn_scan_sale_barcode")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode with Camera",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Categories
                    val categories = listOf("All") + filteredProducts.map { it.product.category }.distinct().take(6)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = (cat == "All" && selectedCategory == null) || (selectedCategory == cat)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.onCategorySelected(if (cat == "All") null else cat)
                                },
                                label = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    // Product Quick-Add List (Horizontal Cards)
                    if (filteredProducts.isEmpty()) {
                        Text(
                            text = "No products found matching '$searchQuery'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(filteredProducts, key = { it.product.id }) { item ->
                                QuickAddProductCard(
                                    item = item,
                                    activeTier = activePricingTier,
                                    currency = currency,
                                    onAddClick = { viewModel.addProductToCart(item.product) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Cart Items Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Cart (${cartItems.size} items)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (cartItems.isNotEmpty()) {
                        Text(
                            text = "Subtotal: ${CurrencyFormatter.format(subtotal, currency)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (cartItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Your cart is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Search or tap any product card above to add items to this sale.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(cartItems, key = { it.product.id }) { lineItem ->
                    CartItemRow(
                        lineItem = lineItem,
                        currency = currency,
                        onQuantityChange = { newQty -> viewModel.updateItemQuantity(lineItem.product.id, newQty) },
                        onPriceChange = { newPrice -> viewModel.updateItemUnitPrice(lineItem.product.id, newPrice) },
                        onRemove = { viewModel.removeItemFromCart(lineItem.product.id) }
                    )
                }
            }

            // 4. Discounts & Payment Settlement Card (Only if cart has items)
            if (cartItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Discount & Settlement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Discount section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = discountType == DiscountType.PERCENTAGE,
                                    onClick = { viewModel.setDiscountType(DiscountType.PERCENTAGE) },
                                    label = { Text("% Off") },
                                    modifier = Modifier.testTag("discount_type_percentage")
                                )
                                FilterChip(
                                    selected = discountType == DiscountType.FIXED,
                                    onClick = { viewModel.setDiscountType(DiscountType.FIXED) },
                                    label = { Text("Fixed Off") },
                                    modifier = Modifier.testTag("discount_type_fixed")
                                )

                                OutlinedTextField(
                                    value = discountValueText,
                                    onValueChange = { viewModel.setDiscountValueText(it) },
                                    label = { Text(if (discountType == DiscountType.PERCENTAGE) "Discount %" else "Discount $currency") },
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("discount_value_input")
                                )
                            }

                            // Subtotal & Discount calculations
                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gross Subtotal:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyFormatter.format(subtotal, currency), fontWeight = FontWeight.Medium)
                            }

                            if (discountAmount > 0L) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Discount Applied:", color = DebtRed)
                                    Text("-${CurrencyFormatter.format(discountAmount, currency)}", color = DebtRed, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Final Total Due:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    CurrencyFormatter.format(finalTotal, currency),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            HorizontalDivider()

                            // Payment Method: Cash vs Credit
                            Text(
                                text = "Payment Method",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = paymentMode == SalePaymentMode.CASH,
                                    onClick = { viewModel.setPaymentMode(SalePaymentMode.CASH) },
                                    label = { Text("Cash") },
                                    modifier = Modifier.weight(1f).testTag("payment_mode_cash")
                                )
                                FilterChip(
                                    selected = paymentMode == SalePaymentMode.CREDIT,
                                    onClick = { viewModel.setPaymentMode(SalePaymentMode.CREDIT) },
                                    label = { Text("Credit (Ledger)") },
                                    modifier = Modifier.weight(1f).testTag("payment_mode_credit")
                                )
                            }

                            // Cash Tendered & Change Due (If Cash)
                            if (paymentMode == SalePaymentMode.CASH) {
                                OutlinedTextField(
                                    value = cashTenderedText,
                                    onValueChange = { viewModel.setCashTenderedText(it) },
                                    label = { Text("Cash Given by Customer ($currency)") },
                                    placeholder = { Text(finalTotal.toString()) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("cash_tendered_input")
                                )

                                if (cashTendered >= finalTotal && finalTotal > 0L) {
                                    Surface(
                                        color = CreditGreenContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Change to Return:",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = CreditGreenText
                                            )
                                            Text(
                                                text = CurrencyFormatter.format(changeDue, currency),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = CreditGreenText
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Credit sale explanation
                                Surface(
                                    color = if (selectedCustomer != null) AmberContainer else DebtRedContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (selectedCustomer != null) {
                                            Text(
                                                text = "Credit Sale to ${selectedCustomer?.name}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberOnContainer
                                            )
                                            Text(
                                                text = "Posts ${CurrencyFormatter.format(finalTotal, currency)} directly to customer's ledger, increasing their outstanding debt.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AmberOnContainer
                                            )
                                        } else {
                                            Text(
                                                text = "Customer Required for Credit Sales",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = DebtRedText
                                            )
                                            Text(
                                                text = "Please select a customer from the dropdown above to post this sale to their ledger.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = DebtRedText
                                            )
                                        }
                                    }
                                }
                            }

                            // Sale Notes
                            OutlinedTextField(
                                value = saleNotes,
                                onValueChange = { viewModel.setSaleNotes(it) },
                                label = { Text("Sale Notes / Remarks (Optional)") },
                                placeholder = { Text("e.g. Counter sale or delivery note") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Complete Sale Button
                            val isCreditWithoutCustomer = paymentMode == SalePaymentMode.CREDIT && selectedCustomer == null
                            Button(
                                onClick = { viewModel.completeSale() },
                                enabled = !isCreditWithoutCustomer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("complete_sale_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (paymentMode == SalePaymentMode.CREDIT) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (paymentMode == SalePaymentMode.CREDIT)
                                        "Record Credit Sale (${CurrencyFormatter.format(finalTotal, currency)})"
                                    else
                                        "Charge ${CurrencyFormatter.format(finalTotal, currency)} (Cash)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Receipt Confirmation Dialog
    completedReceipt?.let { receipt ->
        ReceiptDialog(
            receipt = receipt,
            currency = currency,
            shopName = shopName,
            shopPhone = shopPhone,
            onDismiss = { viewModel.dismissReceipt() }
        )
    }

    if (showBarcodeScanner) {
        BarcodeScannerModal(
            title = "Scan Product Barcode",
            subtitle = "Scan barcode to add to sale or enter SKU manually",
            onBarcodeScanned = { scannedCode ->
                showBarcodeScanner = false
                viewModel.scanBarcode(scannedCode)
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }
}


@Composable
fun QuickAddProductCard(
    item: ProductWithSupplier,
    activeTier: PriceTier,
    currency: String,
    onAddClick: () -> Unit
) {
    val prod = item.product
    val displayPrice = when (activeTier) {
        PriceTier.RETAIL -> prod.retailPrice
        PriceTier.WHOLESALE -> if (prod.wholesalePrice > 0L) prod.wholesalePrice else prod.retailPrice
        PriceTier.CUSTOM -> prod.retailPrice
    }

    Card(
        modifier = Modifier
            .width(170.dp)
            .clickable { onAddClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = prod.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )

            Text(
                text = prod.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ShopRepository.formatQty(prod.stockQty)} ${prod.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (prod.stockQty <= 0) DebtRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (prod.stockQty <= 0) FontWeight.Bold else FontWeight.Normal
                )

                Text(
                    text = CurrencyFormatter.format(displayPrice, currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = onAddClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("add_to_cart_${prod.id}")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun CartItemRow(
    lineItem: SaleLineItem,
    currency: String,
    onQuantityChange: (Double) -> Unit,
    onPriceChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    var showEditPriceDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lineItem.product.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = lineItem.priceTier.label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Text(
                            text = "@ ${CurrencyFormatter.format(lineItem.unitPrice, currency)} / ${lineItem.product.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { showEditPriceDialog = true }
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("remove_item_${lineItem.product.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove item",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quantity adjusters & Line Subtotal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { onQuantityChange(lineItem.quantity - 1.0) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("qty_minus_${lineItem.product.id}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease Quantity", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "${ShopRepository.formatQty(lineItem.quantity)} ${lineItem.product.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { onQuantityChange(lineItem.quantity + 1.0) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .testTag("qty_plus_${lineItem.product.id}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase Quantity", modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = CurrencyFormatter.format(lineItem.subtotal, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showEditPriceDialog) {
        var priceInput by remember { mutableStateOf(lineItem.unitPrice.toString()) }
        AlertDialog(
            onDismissRequest = { showEditPriceDialog = false },
            title = { Text("Set Custom Price") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter custom unit price for ${lineItem.product.name}:")
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Unit Price ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("custom_price_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newPrice = priceInput.toLongOrNull()
                        if (newPrice != null) {
                            onPriceChange(newPrice)
                        }
                        showEditPriceDialog = false
                    }
                ) {
                    Text("Apply Price")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditPriceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReceiptDialog(
    receipt: SaleReceipt,
    currency: String,
    shopName: String,
    shopPhone: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CreditGreen)
                Text("Sale Complete!", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Receipt #${receipt.invoiceNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = DateFormatter.formatDateTime(receipt.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = if (receipt.paymentMode == SalePaymentMode.CREDIT) AmberContainer else CreditGreenContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Payment: ${receipt.paymentMode.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (receipt.paymentMode == SalePaymentMode.CREDIT) AmberOnContainer else CreditGreenText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                receipt.customer?.let { customer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Customer: ${customer.name}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (receipt.paymentMode == SalePaymentMode.CREDIT) {
                                receipt.newCustomerBalance?.let { newBal ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "New Customer Balance: ${CurrencyFormatter.format(newBal, currency)} (Owes you)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DebtRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Items list
                Text("Items Sold:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                receipt.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${ShopRepository.formatQty(item.quantity)}x ${item.product.name}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = CurrencyFormatter.format(item.subtotal, currency),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal:")
                    Text(CurrencyFormatter.format(receipt.subtotal, currency))
                }

                if (receipt.discountAmount > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Discount:")
                        Text("-${CurrencyFormatter.format(receipt.discountAmount, currency)}", color = DebtRed)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        CurrencyFormatter.format(receipt.finalTotal, currency),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (receipt.paymentMode == SalePaymentMode.CASH && receipt.cashTendered > 0L) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cash Tendered:")
                        Text(CurrencyFormatter.format(receipt.cashTendered, currency))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Change Returned:", fontWeight = FontWeight.Bold)
                        Text(CurrencyFormatter.format(receipt.changeDue, currency), fontWeight = FontWeight.Bold, color = CreditGreen)
                    }
                }

                if (receipt.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Note: ${receipt.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_receipt_button")
            ) {
                Text("Done / Next")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val pdfFile = PdfGenerator.generateReceiptPdf(
                        context = context,
                        receipt = receipt,
                        shopName = shopName,
                        shopPhone = shopPhone,
                        currency = currency
                    )
                    ShareHelper.shareFile(
                        context = context,
                        file = pdfFile,
                        mimeType = "application/pdf",
                        chooserTitle = "Share Receipt #${receipt.invoiceNumber}"
                    )
                },
                modifier = Modifier.testTag("share_receipt_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share PDF")
            }
        }
    )
}
