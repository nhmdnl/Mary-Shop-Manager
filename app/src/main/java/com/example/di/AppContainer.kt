package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository

interface AppContainer {
    val shopRepository: ShopRepository
    val settingsManager: ShopSettingsManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val settingsManager: ShopSettingsManager by lazy {
        ShopSettingsManager(context)
    }

    override val shopRepository: ShopRepository by lazy {
        ShopRepository(
            database = database,
            partyDao = database.partyDao(),
            transactionDao = database.transactionDao(),
            productDao = database.productDao(),
            stockMovementDao = database.stockMovementDao(),
            productPriceHistoryDao = database.productPriceHistoryDao(),
            settingsManager = settingsManager,
            saleDao = database.saleDao()
        )
    }
}
