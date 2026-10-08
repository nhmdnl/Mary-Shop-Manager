package com.example.ui.screens.parties

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.example.data.model.AdjustmentDirection
import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionType
import com.example.ui.theme.DebtRed
import com.example.ui.theme.DebtRedContainer
import com.example.util.CurrencyFormatter

@Composable
fun AddTransactionDialog(
    party: Party,
    currentBalance: Long,
    currency: String,
    initialType: TransactionType = TransactionType.SALE_ON_CREDIT,
    onDismiss: () -> Unit,
    onConfirm: (
        type: TransactionType,
        amount: Long,
        paymentMethod: PaymentMethod,
        referenceNo: String,
        note: String,
        adjustmentDirection: Int
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var referenceNo by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var adjustmentDirection by remember { mutableStateOf(1) } // 1 for increase owed, -1 for decrease

    var amountError by remember { mutableStateOf<String?>(null) }

    val parsedAmount = CurrencyFormatter.parseAmount(amountText) ?: 0L

    // Calculate simulated new balance to warn about credit limit
    val projectedBalance = remember(party, currentBalance, selectedType, parsedAmount, adjustmentDirection) {
        val delta = when (party.type) {
            PartyType.CUSTOMER, PartyType.BOTH -> {
                when (selectedType) {
                    TransactionType.SALE_ON_CREDIT -> parsedAmount
                    TransactionType.PAYMENT_RECEIVED -> -parsedAmount
                    TransactionType.PURCHASE_ON_CREDIT -> -parsedAmount
                    TransactionType.PAYMENT_MADE -> parsedAmount
                    TransactionType.ADJUSTMENT -> adjustmentDirection * parsedAmount
                }
            }
            PartyType.SUPPLIER -> {
                when (selectedType) {
                    TransactionType.PURCHASE_ON_CREDIT -> parsedAmount
                    TransactionType.PAYMENT_MADE -> -parsedAmount
                    TransactionType.SALE_ON_CREDIT -> -parsedAmount
                    TransactionType.PAYMENT_RECEIVED -> parsedAmount
                    TransactionType.ADJUSTMENT -> adjustmentDirection * parsedAmount
                }
            }
        }
        currentBalance + delta
    }

    val willExceedCreditLimit = (party.type == PartyType.CUSTOMER || party.type == PartyType.BOTH) &&
            party.creditLimit > 0L && projectedBalance > party.creditLimit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "New Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${party.name} (${party.type.label})",
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
                // Transaction Types
                Text(
                    text = "Transaction Type",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Recommended options based on party type
                val allowedTypes = when (party.type) {
                    PartyType.CUSTOMER -> listOf(
                        TransactionType.SALE_ON_CREDIT,
                        TransactionType.PAYMENT_RECEIVED,
                        TransactionType.ADJUSTMENT
                    )
                    PartyType.SUPPLIER -> listOf(
                        TransactionType.PURCHASE_ON_CREDIT,
                        TransactionType.PAYMENT_MADE,
                        TransactionType.ADJUSTMENT
                    )
                    PartyType.BOTH -> TransactionType.entries
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allowedTypes.chunked(2).forEach { rowTypes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowTypes.forEach { tType ->
                                FilterChip(
                                    selected = selectedType == tType,
                                    onClick = { selectedType = tType },
                                    label = { Text(tType.label, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tx_type_${tType.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // If Adjustment, choose direction
                if (selectedType == TransactionType.ADJUSTMENT) {
                    Text(
                        text = "Adjustment Direction",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = adjustmentDirection == 1,
                            onClick = { adjustmentDirection = 1 },
                            label = { Text("Increase Debt (+)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = adjustmentDirection == -1,
                            onClick = { adjustmentDirection = -1 },
                            label = { Text("Discount/Decrease (-)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        if (it.isNotBlank()) amountError = null
                    },
                    label = { Text("Amount ($currency) *") },
                    placeholder = { Text("e.g. 50,000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountError != null,
                    supportingText = {
                        if (amountError != null) {
                            Text(amountError!!)
                        } else if (parsedAmount > 0) {
                            Text(CurrencyFormatter.format(parsedAmount, currency))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_amount_input")
                )

                // Quick preset amounts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10_000L, 50_000L, 100_000L, 200_000L).forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                amountText = preset.toString()
                                amountError = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${preset / 1000}k",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Credit Limit Warning Banner
                if (willExceedCreditLimit) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DebtRedContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = DebtRed
                            )
                            Column {
                                Text(
                                    text = "Credit Limit Exceeded Warning!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DebtRed
                                )
                                Text(
                                    text = "This will push balance to ${CurrencyFormatter.format(projectedBalance, currency)} (Credit Limit: ${CurrencyFormatter.format(party.creditLimit, currency)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DebtRed
                                )
                            }
                        }
                    }
                }

                // Payment Method
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Reference No
                OutlinedTextField(
                    value = referenceNo,
                    onValueChange = { referenceNo = it },
                    label = { Text("Receipt / Ref No. (Optional)") },
                    placeholder = { Text("e.g. REC-102, MM-8812") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Note / Items Description
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes / Items (Optional)") },
                    placeholder = { Text("e.g. 5 bags Sugar, 2 cartons soap") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedAmount <= 0) {
                        amountError = "Amount must be greater than 0"
                        return@Button
                    }
                    onConfirm(
                        selectedType,
                        parsedAmount,
                        selectedMethod,
                        referenceNo,
                        note,
                        adjustmentDirection
                    )
                },
                modifier = Modifier.testTag("tx_submit_button")
            ) {
                Text("Record Entry")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
