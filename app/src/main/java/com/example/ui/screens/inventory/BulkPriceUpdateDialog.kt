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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

@Composable
fun BulkPriceUpdateDialog(
    categories: List<String>,
    currentSelectedCategory: String?,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (
        category: String,
        isPercentage: Boolean,
        adjustmentValue: Double,
        updateRetail: Boolean,
        updateWholesale: Boolean,
        updateCost: Boolean,
        reason: String
    ) -> Unit
) {
    val initialCat = if (!currentSelectedCategory.isNullOrBlank()) currentSelectedCategory
    else categories.firstOrNull() ?: "General"

    var selectedCategory by remember { mutableStateOf(initialCat) }
    var isPercentage by remember { mutableStateOf(true) }
    var isIncrease by remember { mutableStateOf(true) }
    var valueText by remember { mutableStateOf("10") }
    var updateRetail by remember { mutableStateOf(true) }
    var updateWholesale by remember { mutableStateOf(true) }
    var updateCost by remember { mutableStateOf(false) }
    var reasonText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PriceChange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Bulk Price Update",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Adjust prices across all products in a chosen category by a percentage or fixed amount.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Select Category
                Text(
                    text = "Target Category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                if (categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory.equals(cat, ignoreCase = true),
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = { selectedCategory = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bulk_category_input")
                )

                // 2. Type: Percentage vs Fixed
                Text(
                    text = "Adjustment Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isPercentage,
                        onClick = { isPercentage = true },
                        label = { Text("Percentage (%)") },
                        modifier = Modifier.weight(1f).testTag("bulk_mode_percentage")
                    )
                    FilterChip(
                        selected = !isPercentage,
                        onClick = { isPercentage = false },
                        label = { Text("Fixed Amount ($currency)") },
                        modifier = Modifier.weight(1f).testTag("bulk_mode_fixed")
                    )
                }

                // Direction: Increase vs Decrease
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isIncrease,
                        onClick = { isIncrease = true },
                        leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                        label = { Text("Increase (+)") },
                        modifier = Modifier.weight(1f).testTag("bulk_direction_increase")
                    )
                    FilterChip(
                        selected = !isIncrease,
                        onClick = { isIncrease = false },
                        leadingIcon = { Icon(Icons.Default.TrendingDown, contentDescription = null) },
                        label = { Text("Decrease (-)") },
                        modifier = Modifier.weight(1f).testTag("bulk_direction_decrease")
                    )
                }

                // Value input
                OutlinedTextField(
                    value = valueText,
                    onValueChange = {
                        valueText = it
                        errorText = null
                    },
                    label = { Text(if (isPercentage) "Percentage Rate (%)" else "Adjustment Amount ($currency)") },
                    placeholder = { Text(if (isPercentage) "e.g. 10" else "e.g. 2000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorText != null,
                    supportingText = { errorText?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bulk_adjustment_input")
                )

                // 3. Price types to apply to
                Text(
                    text = "Apply Adjustment To",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = updateRetail,
                            onCheckedChange = { updateRetail = it },
                            modifier = Modifier.testTag("bulk_checkbox_retail")
                        )
                        Text("Retail Price", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = updateWholesale,
                            onCheckedChange = { updateWholesale = it },
                            modifier = Modifier.testTag("bulk_checkbox_wholesale")
                        )
                        Text("Wholesale Price", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = updateCost,
                            onCheckedChange = { updateCost = it },
                            modifier = Modifier.testTag("bulk_checkbox_cost")
                        )
                        Text("Cost Price", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Reason for price change
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Change Reason / Audit Note") },
                    placeholder = { Text("e.g. Annual inflation revision") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bulk_reason_input")
                )

                // Summary Preview Card
                val rawVal = valueText.toDoubleOrNull() ?: 0.0
                val signedVal = if (isIncrease) rawVal else -rawVal
                val modeDesc = if (isPercentage) {
                    val signStr = if (signedVal >= 0) "+$rawVal%" else "-$rawVal%"
                    "$signStr of current price"
                } else {
                    val signStr = if (signedVal >= 0) "+$currency $rawVal" else "-$currency $rawVal"
                    "$signStr"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Adjustment Summary",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Category '$selectedCategory' will have prices modified by $modeDesc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val num = valueText.toDoubleOrNull()
                    if (num == null || num <= 0.0) {
                        errorText = "Enter a valid positive number"
                        return@Button
                    }
                    if (selectedCategory.isBlank()) {
                        errorText = "Select or enter a category"
                        return@Button
                    }
                    if (!updateRetail && !updateWholesale && !updateCost) {
                        errorText = "Select at least one price type"
                        return@Button
                    }

                    val finalVal = if (isIncrease) num else -num
                    val reason = reasonText.ifBlank {
                        val sign = if (isIncrease) "+" else "-"
                        val unitStr = if (isPercentage) "%" else " $currency"
                        "Bulk category adjustment: $sign$num$unitStr"
                    }

                    onConfirm(
                        selectedCategory,
                        isPercentage,
                        finalVal,
                        updateRetail,
                        updateWholesale,
                        updateCost,
                        reason
                    )
                },
                modifier = Modifier.testTag("bulk_confirm_button")
            ) {
                Text("Apply Price Changes")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
