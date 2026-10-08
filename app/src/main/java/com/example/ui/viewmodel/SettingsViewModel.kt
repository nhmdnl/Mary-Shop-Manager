package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.ShopSettings
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import com.example.util.DatabaseBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = settingsManager.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShopSettings()
        )

    val isAppLocked: StateFlow<Boolean> = settingsManager.isAppLocked

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun setCurrency(currency: String) {
        settingsManager.updateCurrency(currency)
        _statusMessage.value = "Default currency updated to $currency"
    }

    fun updateShopInfo(name: String, phone: String) {
        settingsManager.updateShopInfo(name.trim(), phone.trim())
        _statusMessage.value = "Shop details saved"
    }

    fun updateTaxSettings(enabled: Boolean, percentage: Double) {
        settingsManager.updateTaxSettings(enabled, percentage)
        _statusMessage.value = if (enabled) {
            "Sales Tax / VAT set to ${percentage}%"
        } else {
            "Sales Tax / VAT disabled"
        }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        settingsManager.updateThemeMode(mode)
        _statusMessage.value = "Theme changed to ${mode.label}"
    }

    fun updateLanguage(langCode: String) {
        settingsManager.updateLanguage(langCode)
        _statusMessage.value = when (langCode) {
            "sw" -> "Lugha imebadilishwa kuwa Kiswahili"
            "en" -> "Language set to English"
            else -> "Language set to System Default"
        }
    }

    fun setPin(pin: String): Boolean {
        val success = settingsManager.setPin(pin)
        if (success) {
            _statusMessage.value = "PIN Lock successfully enabled"
        } else {
            _errorMessage.value = "PIN must be exactly 4 digits"
        }
        return success
    }

    fun disablePin(currentPin: String): Boolean {
        val success = settingsManager.disablePin(currentPin)
        if (success) {
            _statusMessage.value = "PIN Lock disabled"
        } else {
            _errorMessage.value = "Incorrect PIN. Unable to disable PIN lock."
        }
        return success
    }

    fun unlockApp(pin: String): Boolean {
        val success = settingsManager.unlockApp(pin)
        if (!success) {
            _errorMessage.value = "Incorrect PIN. Please try again."
        }
        return success
    }

    fun lockApp() {
        settingsManager.lockApp()
    }

    fun setAllowNegativeStock(allow: Boolean) {
        settingsManager.updateAllowNegativeStock(allow)
        _statusMessage.value = if (allow) {
            "Negative stock allowed: You can now record sales even when stock is zero"
        } else {
            "Strict stock control active: Stock cannot fall below zero"
        }
    }

    fun backupDatabase(context: Context, destinationUri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = DatabaseBackupManager.backupDatabase(context, destinationUri)
            _isBusy.value = false
            result.onSuccess { bytes ->
                val kb = bytes / 1024
                _statusMessage.value = "Database backup saved successfully (${kb} KB)"
            }.onFailure { e ->
                _errorMessage.value = "Backup failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun restoreDatabase(context: Context, sourceUri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = DatabaseBackupManager.restoreDatabase(context, sourceUri)
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Database restored successfully!"
                onComplete()
            }.onFailure { e ->
                _errorMessage.value = "Restore failed: ${e.localizedMessage ?: "Invalid backup file"}"
            }
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetToSampleData()
            _statusMessage.value = "Demo data reset successfully (5 Customers, 3 Suppliers & 15 Products with Movements)"
        }
    }
}

