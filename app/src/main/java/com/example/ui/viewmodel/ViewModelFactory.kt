package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.MaryShopApplication
import com.example.di.AppContainer

object AppViewModelProvider {
    val Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as MaryShopApplication
            val container = application.container

            return when {
                modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                    DashboardViewModel(container.shopRepository, container.settingsManager) as T
                }
                modelClass.isAssignableFrom(PartiesViewModel::class.java) -> {
                    PartiesViewModel(container.shopRepository, container.settingsManager) as T
                }
                modelClass.isAssignableFrom(InventoryViewModel::class.java) -> {
                    InventoryViewModel(container.shopRepository, container.settingsManager) as T
                }
                modelClass.isAssignableFrom(SalesViewModel::class.java) -> {
                    SalesViewModel(container.shopRepository, container.settingsManager) as T
                }
                modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                    SettingsViewModel(container.shopRepository, container.settingsManager) as T
                }
                modelClass.isAssignableFrom(ReportsViewModel::class.java) -> {
                    ReportsViewModel(container.shopRepository, container.settingsManager) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    fun partyDetailFactory(partyId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as MaryShopApplication
            val container = application.container
            return PartyDetailViewModel(partyId, container.shopRepository, container.settingsManager) as T
        }
    }

    fun productDetailFactory(productId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as MaryShopApplication
            val container = application.container
            return ProductDetailViewModel(productId, container.shopRepository, container.settingsManager) as T
        }
    }
}
