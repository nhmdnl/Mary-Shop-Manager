package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DebtRed

enum class PinDialogMode {
    SETUP,
    CHANGE,
    DISABLE
}

@Composable
fun PinSettingsDialog(
    mode: PinDialogMode,
    isCurrentPinValid: (String) -> Boolean,
    onSavePin: (String) -> Unit,
    onDisablePin: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val title = when (mode) {
        PinDialogMode.SETUP -> "Set 4-Digit Security PIN"
        PinDialogMode.CHANGE -> "Change Security PIN"
        PinDialogMode.DISABLE -> "Disable Security PIN"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // If changing or disabling, ask for current PIN first
                if (mode == PinDialogMode.CHANGE || mode == PinDialogMode.DISABLE) {
                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                currentPinInput = it
                                errorText = null
                            }
                        },
                        label = { Text("Current 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_current_pin_input")
                    )
                }

                // If setting up or changing, ask for new PIN & confirm
                if (mode == PinDialogMode.SETUP || mode == PinDialogMode.CHANGE) {
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                newPinInput = it
                                errorText = null
                            }
                        },
                        label = { Text("New 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_new_pin_input")
                    )

                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                confirmPinInput = it
                                errorText = null
                            }
                        },
                        label = { Text("Confirm 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_confirm_pin_input")
                    )
                }

                AnimatedVisibility(visible = errorText != null) {
                    errorText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = DebtRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        PinDialogMode.SETUP -> {
                            if (newPinInput.length != 4) {
                                errorText = "PIN must be exactly 4 digits."
                                return@Button
                            }
                            if (newPinInput != confirmPinInput) {
                                errorText = "PINs do not match. Please re-enter."
                                return@Button
                            }
                            onSavePin(newPinInput)
                            onDismiss()
                        }
                        PinDialogMode.CHANGE -> {
                            if (!isCurrentPinValid(currentPinInput)) {
                                errorText = "Current PIN is incorrect."
                                return@Button
                            }
                            if (newPinInput.length != 4) {
                                errorText = "New PIN must be exactly 4 digits."
                                return@Button
                            }
                            if (newPinInput != confirmPinInput) {
                                errorText = "New PIN confirmation does not match."
                                return@Button
                            }
                            onSavePin(newPinInput)
                            onDismiss()
                        }
                        PinDialogMode.DISABLE -> {
                            val success = onDisablePin(currentPinInput)
                            if (success) {
                                onDismiss()
                            } else {
                                errorText = "Current PIN is incorrect."
                            }
                        }
                    }
                },
                colors = if (mode == PinDialogMode.DISABLE) ButtonDefaults.buttonColors(containerColor = DebtRed) else ButtonDefaults.buttonColors(),
                modifier = Modifier.testTag("pin_dialog_confirm_button")
            ) {
                Text(
                    text = when (mode) {
                        PinDialogMode.SETUP -> "Enable PIN"
                        PinDialogMode.CHANGE -> "Update PIN"
                        PinDialogMode.DISABLE -> "Disable PIN"
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("pin_dialog_dismiss_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
