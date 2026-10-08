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
import com.example.data.model.Product
import com.example.ui.components.BarcodeScannerModal
import com.example.util.CurrencyFormatter
import com.example.util.LivePriceMarginMarkupCard

@Composable
fun EditProductDialog(
    product: Product,
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
        reorderLevel: Double,
        supplierId: Long?,
        notes: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var sku by remember { mutableStateOf(product.sku) }
    var category by remember { mutableStateOf(product.category) }
    var unit by remember { mutableStateOf(product.unit) }
    var costPriceText by remember { mutableStateOf(product.costPrice.toString()) }
    var retailPriceText by remember { mutableStateOf(product.retailPrice.toString()) }
    var wholesalePriceText by remember { mutableStateOf(product.wholesalePrice.toString()) }
    var reorderLevelText by remember { mutableStateOf(if (product.reorderLevel % 1.0 == 0.0) product.reorderLevel.toInt().toString() else product.reorderLevel.toString()) }
    var selectedSupplier by remember { mutableStateOf(suppliers.find { it.id == product.supplierId }) }
    var notes by remember { mutableStateOf(product.notes) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var showSupplierMenu by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }

    val commonCategories = listOf(
        "Grains & Flour", "Commodities", "Oils & Fats", "Hygiene & Cleaning",
        "Beverages", "Dairy", "Groceries", "Snacks", "Spices", "Hardware"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Product & Pricing",
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
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_product_name_input")
                )

                // SKU / Barcode
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU / Barcode") },
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
                    modifier = Modifier.fillMaxWidth().testTag("edit_product_sku_input")
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
                    label = { Text("Custom Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Unit
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Packaging Unit") },
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_product_cost_input")
                    )

                    OutlinedTextField(
                        value = retailPriceText,
                        onValueChange = { retailPriceText = it },
                        label = { Text("Retail Price ($currency) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_product_retail_input")
                    )
                }

                // Wholesale Price
                OutlinedTextField(
                    value = wholesalePriceText,
                    onValueChange = { wholesalePriceText = it },
                    label = { Text("Wholesale Price ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_product_wholesale_input")
                )

                // LIVE MARGIN AND MARKUP % CALCULATION (CRITICAL REQUIREMENT)
                val costVal = costPriceText.toLongOrNull() ?: 0L
                val retailVal = retailPriceText.toLongOrNull() ?: 0L
                val wholesaleVal = wholesalePriceText.toLongOrNull() ?: 0L

                if (costVal > 0L || retailVal > 0L) {
                    LivePriceMarginMarkupCard(
                        costPrice = costVal,
                        sellingPrice = retailVal,
                        currency = currency,
                        title = "Live Retail Margin & Markup"
                    )
                }

                if (wholesaleVal > 0L) {
                    LivePriceMarginMarkupCard(
                        costPrice = costVal,
                        sellingPrice = wholesaleVal,
                        currency = currency,
                        title = "Live Wholesale Margin & Markup"
                    )
                }

                // Reorder Level
                OutlinedTextField(
                    value = reorderLevelText,
                    onValueChange = { reorderLevelText = it },
                    label = { Text("Low Stock Alert Level ($unit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_product_reorder_input")
                )

                // Supplier Selector
                Text(
                    text = "Linked Supplier",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = selectedSupplier?.name ?: "No Linked Supplier",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Supplier") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { showSupplierMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Supplier")
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = showSupplierMenu,
                        onDismissRequest = { showSupplierMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Unlinked)") },
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
                    label = { Text("Notes / Specifications") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = "Name cannot be empty"
                        return@Button
                    }
                    val cost = costPriceText.toLongOrNull() ?: 0L
                    val retail = retailPriceText.toLongOrNull() ?: 0L
                    val wholesale = wholesalePriceText.toLongOrNull() ?: 0L
                    val reorder = reorderLevelText.toDoubleOrNull() ?: 5.0

                    onConfirm(
                        name,
                        sku,
                        category,
                        unit,
                        cost,
                        retail,
                        wholesale,
                        reorder,
                        selectedSupplier?.id,
                        notes
                    )
                },
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text("Save Changes")
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

