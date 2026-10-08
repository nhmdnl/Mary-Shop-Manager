package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DashboardSummary
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    val summary: StateFlow<DashboardSummary> = repository.dashboardSummary
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardSummary()
        )

    val currency: StateFlow<String> = settingsManager.settings
        .map { it.currency }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "UGX"
        )

    val shopName: StateFlow<String> = settingsManager.settings
        .map { it.shopName }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Mary Shop"
        )

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetToSampleData()
        }
    }
}
