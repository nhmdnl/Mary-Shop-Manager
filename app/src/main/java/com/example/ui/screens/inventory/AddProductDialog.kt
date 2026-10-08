package com.example.ui.screens.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Party
import com.example.ui.components.BarcodeScannerModal
import com.example.util.CurrencyFormatter

@Composable
fun AddProductDialog(
    currency: String,
    suppliers: List<Party>,
    onDismiss: () -> Unit,
    onConfirm: (
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
        notes: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Grains & Flour") }
    var unit by remember { mutableStateOf("pcs") }
    var costPriceText by remember { mutableStateOf("") }
    var retailPriceText by remember { mutableStateOf("") }
    var wholesalePriceText by remember { mutableStateOf("") }
    var stockQtyText by remember { mutableStateOf("0") }
    var reorderLevelText by remember { mutableStateOf("5") }
    var selectedSupplier by remember { mutableStateOf<Party?>(null) }
    var notes by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var showSupplierMenu by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }

    val commonCategories = listOf(
        "Grains & Flour", "Commodities", "Oils & Fats", "Hygiene & Cleaning",
        "Beverages", "Dairy", "Groceries", "Snacks", "Spices", "Hardware"
    )

    val commonUnits = listOf("pcs", "kg", "50kg Sack", "25kg Bag", "20L Jerrycan", "Box", "Carton", "Packet", "Tub", "Crate")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add New Product",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = null
                    },
                    label = { Text("Product Name *") },
                    placeholder = { Text("e.g. Kakira Sugar 50kg") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_product_name_input")
                )

                // SKU / Barcode text field
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU / Barcode") },
                    placeholder = { Text("e.g. SUG-50K-01 or 600100123") },
                    trailingIcon = {
                        IconButton(
                            onClick = { showBarcodeScanner = true },
                            modifier = Modifier.testTag("scan_sku_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode with Camera",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_product_sku_input")
                )

                // Category Selection
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonCategories.forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Or Type Custom Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Unit Selection
                Text(
                    text = "Packaging Unit",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonUnits.forEach { u ->
                        FilterChip(
                            selected = unit.equals(u, ignoreCase = true),
                            onClick = { unit = u },
                            label = { Text(u, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Or Type Custom Unit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Pricing Row: Cost & Retail
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = costPriceText,
                        onValueChange = { costPriceText = it },
                        label = { Text("Cost Price ($currency)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_product_cost_input")
                    )

                    OutlinedTextField(
                        value = retailPriceText,
                        onValueChange = { retailPriceText = it },
                        label = { Text("Retail Price ($currency) *") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_product_retail_input")
                    )
                }

                // Wholesale Price
                OutlinedTextField(
                    value = wholesalePriceText,
                    onValueChange = { wholesalePriceText = it },
                    label = { Text("Wholesale Price ($currency, Optional)") },
                    placeholder = { Text("e.g. For bulk buyers") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Margin & Markup % Calculation
                val costVal = costPriceText.toLongOrNull() ?: 0L
                val retailVal = retailPriceText.toLongOrNull() ?: 0L
                val wholesaleVal = wholesalePriceText.toLongOrNull() ?: 0L

                if (costVal > 0L || retailVal > 0L) {
                    com.example.util.LivePriceMarginMarkupCard(
                        costPrice = costVal,
                        sellingPrice = retailVal,
                        currency = currency,
                        title = "Retail Margin & Markup"
                    )
                }

                if (wholesaleVal > 0L) {
                    com.example.util.LivePriceMarginMarkupCard(
                        costPrice = costVal,
                        sellingPrice = wholesaleVal,
                        currency = currency,
                        title = "Wholesale Margin & Markup"
                    )
                }

                // Stock & Reorder Level Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockQtyText,
                        onValueChange = { stockQtyText = it },
                        label = { Text("Initial Stock Qty") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_product_stock_input")
                    )

                    OutlinedTextField(
                        value = reorderLevelText,
                        onValueChange = { reorderLevelText = it },
                        label = { Text("Reorder Level") },
                        placeholder = { Text("5") },
                        supportingText = { Text("Low-stock alert threshold") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_product_reorder_input")
                    )
                }

                // Supplier Link Dropdown
                Text(
                    text = "Supplier Link",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = selectedSupplier?.name ?: "None (Unassigned)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Primary Supplier") },
                        trailingIcon = {
                            IconButton(onClick = { showSupplierMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select supplier")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showSupplierMenu,
                        onDismissRequest = { showSupplierMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Unassigned)") },
                            onClick = {
                                selectedSupplier = null
                                showSupplierMenu = false
                            }
                        )
                        suppliers.forEach { supp ->
                            DropdownMenuItem(
                                text = { Text(supp.name) },
                                onClick = {
                                    selectedSupplier = supp
                                    showSupplierMenu = false
                                }
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Storage location, alternate suppliers, etc.)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = "Product name is required"
                        return@Button
                    }
                    val cost = CurrencyFormatter.parseAmount(costPriceText) ?: 0L
                    val retail = CurrencyFormatter.parseAmount(retailPriceText) ?: 0L
                    val wholesale = CurrencyFormatter.parseAmount(wholesalePriceText) ?: retail
                    val stock = stockQtyText.toDoubleOrNull() ?: 0.0
                    val reorder = reorderLevelText.toDoubleOrNull() ?: 5.0

                    onConfirm(
                        name,
                        sku,
                        category,
                        unit,
                        cost,
                        retail,
                        wholesale,
                        stock,
                        reorder,
                        selectedSupplier?.id,
                        notes
                    )
                },
                modifier = Modifier.testTag("add_product_submit_button")
            ) {
                Text("Save Product")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showBarcodeScanner) {
        BarcodeScannerModal(
            title = "Scan Product Barcode",
            subtitle = "Point camera at item's barcode or enter SKU",
            onBarcodeScanned = { scannedCode ->
                sku = scannedCode
                showBarcodeScanner = false
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }
}

