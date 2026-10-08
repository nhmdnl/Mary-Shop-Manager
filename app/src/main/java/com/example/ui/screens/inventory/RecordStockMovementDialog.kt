package com.example.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Party
import com.example.data.model.Product
import com.example.data.model.StockMovementType
import com.example.data.repository.ShopRepository
import com.example.ui.theme.DebtRed
import com.example.ui.theme.DebtRedContainer
import com.example.util.CurrencyFormatter

@Composable
fun RecordStockMovementDialog(
    product: Product,
    currency: String,
    allowNegativeStock: Boolean,
    parties: List<Party>,
    initialType: StockMovementType = StockMovementType.PURCHASE_IN,
    onDismiss: () -> Unit,
    onConfirm: (
        type: StockMovementType,
        quantity: Double,
        isStockIn: Boolean,
        unitPrice: Long?,
        referenceNo: String,
        note: String,
        partyId: Long?
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var isAddition by remember { mutableStateOf(initialType.defaultIsAddition) }
    var quantityText by remember { mutableStateOf("") }
    var unitPriceText by remember {
        mutableStateOf(
            if (initialType == StockMovementType.PURCHASE_IN && product.costPrice > 0) product.costPrice.toString()
            else if (initialType == StockMovementType.SALE_OUT && product.retailPrice > 0) product.retailPrice.toString()
            else ""
        )
    }
    var referenceNo by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedParty by remember {
        mutableStateOf(parties.find { it.id == product.supplierId })
    }
    var showPartyMenu by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf<String?>(null) }

    val currentStock = product.stockQty
    val parsedQty = quantityText.toDoubleOrNull() ?: 0.0

    // When type changes, adjust default direction and suggested unit price
    fun onTypeSelected(type: StockMovementType) {
        selectedType = type
        isAddition = when (type) {
            StockMovementType.PURCHASE_IN -> true
            StockMovementType.SALE_OUT -> false
            StockMovementType.DAMAGE_LOSS -> false
            StockMovementType.RETURN -> true // default customer return
            StockMovementType.ADJUSTMENT -> true // default count gain
        }
        unitPriceText = when (type) {
            StockMovementType.PURCHASE_IN -> if (product.costPrice > 0) product.costPrice.toString() else ""
            StockMovementType.SALE_OUT -> if (product.retailPrice > 0) product.retailPrice.toString() else ""
            else -> ""
        }
    }

    val resultingStock by remember(currentStock, parsedQty, isAddition) {
        derivedStateOf {
            if (isAddition) currentStock + parsedQty else currentStock - parsedQty
        }
    }

    val wouldGoNegative = resultingStock < 0.0 && !allowNegativeStock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Record Stock Movement",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${product.name} (${product.unit})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Movement Type Chips
                Text(
                    text = "Movement Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == StockMovementType.PURCHASE_IN,
                            onClick = { onTypeSelected(StockMovementType.PURCHASE_IN) },
                            label = { Text("Purchase In (+)") },
                            modifier = Modifier.weight(1f).testTag("movement_type_purchase_in")
                        )
                        FilterChip(
                            selected = selectedType == StockMovementType.SALE_OUT,
                            onClick = { onTypeSelected(StockMovementType.SALE_OUT) },
                            label = { Text("Sale Out (-)") },
                            modifier = Modifier.weight(1f).testTag("movement_type_sale_out")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == StockMovementType.RETURN,
                            onClick = { onTypeSelected(StockMovementType.RETURN) },
                            label = { Text("Return") },
                            modifier = Modifier.weight(1f).testTag("movement_type_return")
                        )
                        FilterChip(
                            selected = selectedType == StockMovementType.DAMAGE_LOSS,
                            onClick = { onTypeSelected(StockMovementType.DAMAGE_LOSS) },
                            label = { Text("Damage / Loss (-)") },
                            modifier = Modifier.weight(1f).testTag("movement_type_damage")
                        )
                        FilterChip(
                            selected = selectedType == StockMovementType.ADJUSTMENT,
                            onClick = { onTypeSelected(StockMovementType.ADJUSTMENT) },
                            label = { Text("Adjustment") },
                            modifier = Modifier.weight(1f).testTag("movement_type_adjustment")
                        )
                    }
                }

                // If RETURN or ADJUSTMENT, allow choosing direction
                if (selectedType == StockMovementType.RETURN) {
                    Text(
                        text = "Return Direction",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isAddition,
                            onClick = { isAddition = true },
                            label = { Text("Customer Return (+ In)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isAddition,
                            onClick = { isAddition = false },
                            label = { Text("Supplier Return (- Out)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (selectedType == StockMovementType.ADJUSTMENT) {
                    Text(
                        text = "Stock Count Adjustment",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isAddition,
                            onClick = { isAddition = true },
                            label = { Text("Surplus / Found (+ In)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isAddition,
                            onClick = { isAddition = false },
                            label = { Text("Shortage / Loss (- Out)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Quantity Input
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it
                        if (it.toDoubleOrNull() != null && it.toDouble() > 0) {
                            quantityError = null
                        }
                    },
                    label = { Text("Quantity (${product.unit}) *") },
                    placeholder = { Text("e.g. 5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = quantityError != null || wouldGoNegative,
                    supportingText = {
                        if (quantityError != null) Text(quantityError!!)
                        else if (wouldGoNegative) Text("Stock cannot fall below zero unless allowed in settings", color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("movement_quantity_input")
                )

                // Stock Calculation Indicator Card
                Surface(
                    color = if (wouldGoNegative) DebtRedContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Stock:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${ShopRepository.formatQty(currentStock)} ${product.unit}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Movement Delta:", style = MaterialTheme.typography.bodyMedium)
                            val sign = if (isAddition) "+" else "-"
                            Text(
                                "$sign${ShopRepository.formatQty(parsedQty)} ${product.unit}",
                                fontWeight = FontWeight.Bold,
                                color = if (isAddition) MaterialTheme.colorScheme.primary else DebtRed,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Resulting Stock:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${ShopRepository.formatQty(resultingStock)} ${product.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (wouldGoNegative) DebtRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (wouldGoNegative) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = DebtRed,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    text = "Cannot proceed: Stock cannot fall below zero. Enable 'Allow Negative Stock' in More/Settings to bypass this rule.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DebtRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Unit Price (Optional)
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it },
                    label = { Text("Unit Price ($currency, Optional)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Reference No
                OutlinedTextField(
                    value = referenceNo,
                    onValueChange = { referenceNo = it },
                    label = { Text("Reference / Receipt / Batch No.") },
                    placeholder = { Text("e.g. REC-1021 or BATCH-04") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Linked Party (Supplier or Customer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = selectedParty?.name ?: "None (General / Direct)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Related Party (Supplier / Customer)") },
                        trailingIcon = {
                            IconButton(onClick = { showPartyMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select party")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showPartyMenu,
                        onDismissRequest = { showPartyMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (General / Direct)") },
                            onClick = {
                                selectedParty = null
                                showPartyMenu = false
                            }
                        )
                        parties.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.name} (${p.type.label})") },
                                onClick = {
                                    selectedParty = p
                                    showPartyMenu = false
                                }
                            )
                        }
                    }
                }

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Description") },
                    placeholder = { Text("e.g. Restocked shelf from delivery truck") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedQty <= 0.0) {
                        quantityError = "Please enter a quantity greater than zero"
                        return@Button
                    }
                    if (wouldGoNegative) {
                        return@Button
                    }
                    val unitPrice = CurrencyFormatter.parseAmount(unitPriceText)

                    onConfirm(
                        selectedType,
                        parsedQty,
                        isAddition,
                        unitPrice,
                        referenceNo,
                        note,
                        selectedParty?.id
                    )
                },
                enabled = parsedQty > 0.0 && !wouldGoNegative,
                modifier = Modifier.testTag("record_movement_confirm_button")
            ) {
                Text("Confirm Movement")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
