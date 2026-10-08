package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

data class ShopSettings(
    val currency: String = "UGX",
    val shopName: String = "Mary Shop",
    val shopPhone: String = "+256 700 123456",
    val allowNegativeStock: Boolean = false,
    val taxEnabled: Boolean = false,
    val taxPercentage: Double = 0.0,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isPinEnabled: Boolean = false,
    val pinCode: String = "",
    val appLanguage: String = "SYSTEM" // "SYSTEM", "en", "sw"
)

class ShopSettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mary_shop_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ShopSettings> = _settings.asStateFlow()

    private val _isAppLocked = MutableStateFlow(
        prefs.getBoolean(KEY_IS_PIN_ENABLED, false) && prefs.getString(KEY_PIN_CODE, "").isNullOrBlank().not()
    )
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private fun loadSettings(): ShopSettings {
        val themeModeStr = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val themeMode = runCatching { AppThemeMode.valueOf(themeModeStr) }.getOrDefault(AppThemeMode.SYSTEM)
        val isPinOn = prefs.getBoolean(KEY_IS_PIN_ENABLED, false)
        val savedPin = prefs.getString(KEY_PIN_CODE, "") ?: ""

        return ShopSettings(
            currency = prefs.getString(KEY_CURRENCY, "UGX") ?: "UGX",
            shopName = prefs.getString(KEY_SHOP_NAME, "Mary Shop") ?: "Mary Shop",
            shopPhone = prefs.getString(KEY_SHOP_PHONE, "+256 700 123456") ?: "+256 700 123456",
            allowNegativeStock = prefs.getBoolean(KEY_ALLOW_NEGATIVE_STOCK, false),
            taxEnabled = prefs.getBoolean(KEY_TAX_ENABLED, false),
            taxPercentage = prefs.getFloat(KEY_TAX_PERCENTAGE, 0f).toDouble(),
            themeMode = themeMode,
            isPinEnabled = isPinOn && savedPin.length >= 4,
            pinCode = savedPin,
            appLanguage = prefs.getString(KEY_APP_LANGUAGE, "SYSTEM") ?: "SYSTEM"
        )
    }

    fun updateCurrency(newCurrency: String) {
        prefs.edit().putString(KEY_CURRENCY, newCurrency).apply()
        _settings.value = _settings.value.copy(currency = newCurrency)
    }

    fun updateShopInfo(name: String, phone: String) {
        prefs.edit()
            .putString(KEY_SHOP_NAME, name)
            .putString(KEY_SHOP_PHONE, phone)
            .apply()
        _settings.value = _settings.value.copy(shopName = name, shopPhone = phone)
    }

    fun updateAllowNegativeStock(allow: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_NEGATIVE_STOCK, allow).apply()
        _settings.value = _settings.value.copy(allowNegativeStock = allow)
    }

    fun updateTaxSettings(enabled: Boolean, percentage: Double) {
        val safePercentage = percentage.coerceIn(0.0, 100.0)
        prefs.edit()
            .putBoolean(KEY_TAX_ENABLED, enabled)
            .putFloat(KEY_TAX_PERCENTAGE, safePercentage.toFloat())
            .apply()
        _settings.value = _settings.value.copy(taxEnabled = enabled, taxPercentage = safePercentage)
    }

    fun updateThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun updateLanguage(langCode: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, langCode).apply()
        _settings.value = _settings.value.copy(appLanguage = langCode)
    }

    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        prefs.edit()
            .putBoolean(KEY_IS_PIN_ENABLED, true)
            .putString(KEY_PIN_CODE, pin)
            .apply()
        _settings.value = _settings.value.copy(isPinEnabled = true, pinCode = pin)
        _isAppLocked.value = false
        return true
    }

    fun disablePin(currentPin: String): Boolean {
        if (_settings.value.pinCode.isNotEmpty() && _settings.value.pinCode != currentPin) {
            return false
        }
        prefs.edit()
            .putBoolean(KEY_IS_PIN_ENABLED, false)
            .putString(KEY_PIN_CODE, "")
            .apply()
        _settings.value = _settings.value.copy(isPinEnabled = false, pinCode = "")
        _isAppLocked.value = false
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val currentPin = _settings.value.pinCode
        return currentPin.isNotEmpty() && currentPin == pin
    }

    fun unlockApp(pin: String): Boolean {
        if (!_settings.value.isPinEnabled) {
            _isAppLocked.value = false
            return true
        }
        if (verifyPin(pin)) {
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun lockApp() {
        if (_settings.value.isPinEnabled) {
            _isAppLocked.value = true
        }
    }

    fun forceUnlock() {
        _isAppLocked.value = false
    }

    companion object {
        private const val KEY_CURRENCY = "key_currency"
        private const val KEY_SHOP_NAME = "key_shop_name"
        private const val KEY_SHOP_PHONE = "key_shop_phone"
        private const val KEY_ALLOW_NEGATIVE_STOCK = "key_allow_negative_stock"
        private const val KEY_TAX_ENABLED = "key_tax_enabled"
        private const val KEY_TAX_PERCENTAGE = "key_tax_percentage"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_IS_PIN_ENABLED = "key_is_pin_enabled"
        private const val KEY_PIN_CODE = "key_pin_code"
        private const val KEY_APP_LANGUAGE = "key_app_language"

        val AVAILABLE_CURRENCIES = listOf("UGX", "KES", "TZS", "RWF", "USD", "EUR", "GBP", "NGN", "GHS", "ZAR")
    }
}

