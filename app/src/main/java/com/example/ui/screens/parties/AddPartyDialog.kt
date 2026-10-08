package com.example.ui.screens.parties

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.PartyType
import com.example.util.CurrencyFormatter

@Composable
fun AddPartyDialog(
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        phone: String,
        type: PartyType,
        address: String,
        notes: String,
        openingBalance: Long,
        creditLimit: Long
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PartyType.CUSTOMER) }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var openingBalanceText by remember { mutableStateOf("") }
    var creditLimitText by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add New Party",
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
                // Party Type Selection
                Text(
                    text = "Party Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PartyType.entries.forEach { pType ->
                        FilterChip(
                            selected = type == pType,
                            onClick = { type = pType },
                            label = { Text(pType.label) },
                            modifier = Modifier.testTag("party_type_${pType.name.lowercase()}")
                        )
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = null
                    },
                    label = { Text("Full Name *") },
                    placeholder = { Text("e.g. Sarah Namubiru") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_party_name_input")
                )

                // Phone field
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        if (it.isNotBlank()) phoneError = null
                    },
                    label = { Text("Phone Number *") },
                    placeholder = { Text("e.g. +256 772 123456") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phoneError != null,
                    supportingText = { phoneError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_party_phone_input")
                )

                // Address field
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop Location / Address") },
                    placeholder = { Text("e.g. Kawempe Market, Stall 14") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Opening Balance
                OutlinedTextField(
                    value = openingBalanceText,
                    onValueChange = { openingBalanceText = it },
                    label = {
                        Text(
                            if (type == PartyType.CUSTOMER) "Opening Balance (Owes you in $currency)"
                            else "Opening Balance (You owe in $currency)"
                        )
                    },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Credit Limit (for customers or both)
                if (type == PartyType.CUSTOMER || type == PartyType.BOTH) {
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = it },
                        label = { Text("Credit Limit ($currency)") },
                        placeholder = { Text("e.g. 300,000 (0 for no limit)") },
                        supportingText = { Text("Warns you when customer exceeds this credit amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("Payment habits, alternate contacts, etc.") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false
                    if (name.isBlank()) {
                        nameError = "Name is required"
                        hasError = true
                    }
                    if (phone.isBlank()) {
                        phoneError = "Phone is required"
                        hasError = true
                    }
                    if (!hasError) {
                        val openingBal = CurrencyFormatter.parseAmount(openingBalanceText) ?: 0L
                        val limit = CurrencyFormatter.parseAmount(creditLimitText) ?: 0L
                        onConfirm(name, phone, type, address, notes, openingBal, limit)
                    }
                },
                modifier = Modifier.testTag("add_party_submit_button")
            ) {
                Text("Save Party")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
