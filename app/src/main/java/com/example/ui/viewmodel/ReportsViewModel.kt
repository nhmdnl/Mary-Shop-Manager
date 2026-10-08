package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DateRangeSelection
import com.example.data.model.DateRangeType
import com.example.data.model.DebtAgingSummary
import com.example.data.model.Party
import com.example.data.model.PartyStatementReport
import com.example.data.model.ProductProfitReport
import com.example.data.model.ReportTab
import com.example.data.model.SalesReportSummary
import com.example.data.model.StockMovementReport
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import com.example.util.CsvExporter
import com.example.util.PdfGenerator
import com.example.util.ShareHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ReportTab.SALES)
    val selectedTab: StateFlow<ReportTab> = _selectedTab.asStateFlow()

    private val _dateRange = MutableStateFlow(DateRangeSelection(type = DateRangeType.THIS_MONTH))
    val dateRange: StateFlow<DateRangeSelection> = _dateRange.asStateFlow()

    private val _selectedPartyId = MutableStateFlow<Long?>(null)
    val selectedPartyId: StateFlow<Long?> = _selectedPartyId.asStateFlow()

    val allParties: StateFlow<List<Party>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currency: StateFlow<String> = settingsManager.settings
        .map { it.currency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "UGX")

    val shopName: StateFlow<String> = settingsManager.settings
        .map { it.shopName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Mary Shop")

    val shopPhone: StateFlow<String> = settingsManager.settings
        .map { it.shopPhone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // 1. Sales Report
    val salesReport: StateFlow<SalesReportSummary?> = _dateRange
        .flatMapLatest { range ->
            repository.getSalesReport(range.startTimestamp, range.endTimestamp)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 2. Profit Per Product Report
    val profitReport: StateFlow<ProductProfitReport?> = _dateRange
        .flatMapLatest { range ->
            repository.getProductProfitReport(range.startTimestamp, range.endTimestamp)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 3. Party Statement Report
    val partyStatementReport: StateFlow<PartyStatementReport?> = combine(
        _selectedPartyId,
        _dateRange,
        allParties
    ) { partyId, range, parties ->
        val targetId = partyId ?: parties.firstOrNull()?.id
        Triple(targetId, range.startTimestamp, range.endTimestamp)
    }.flatMapLatest { (targetId, start, end) ->
        if (targetId != null) {
            repository.getPartyStatement(targetId, start, end)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 4. Stock Movement Report
    val stockMovementReport: StateFlow<StockMovementReport?> = _dateRange
        .flatMapLatest { range ->
            repository.getStockMovementReport(range.startTimestamp, range.endTimestamp)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 5. Debt Aging Report (as of current date)
    val debtAgingReport: StateFlow<DebtAgingSummary?> = repository.debtAgingSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectTab(tab: ReportTab) {
        _selectedTab.value = tab
    }

    fun setDateRangeType(type: DateRangeType) {
        _dateRange.value = _dateRange.value.copy(type = type)
    }

    fun setCustomRange(start: Long, end: Long) {
        _dateRange.value = DateRangeSelection(
            type = DateRangeType.CUSTOM,
            customStart = start,
            customEnd = end
        )
    }

    fun selectParty(partyId: Long) {
        _selectedPartyId.value = partyId
    }

    fun exportCsv(context: Context): File? {
        val curr = currency.value
        return when (_selectedTab.value) {
            ReportTab.SALES -> salesReport.value?.let { CsvExporter.exportSalesReportCsv(context, it, curr) }
            ReportTab.PROFIT -> profitReport.value?.let { CsvExporter.exportProfitPerProductCsv(context, it, curr) }
            ReportTab.STATEMENT -> partyStatementReport.value?.let { CsvExporter.exportPartyStatementCsv(context, it, curr) }
            ReportTab.STOCK_MOVEMENT -> stockMovementReport.value?.let { CsvExporter.exportStockMovementsCsv(context, it, curr) }
            ReportTab.DEBT_AGING -> debtAgingReport.value?.let { CsvExporter.exportDebtAgingCsv(context, it, curr) }
        }
    }

    fun shareCsv(context: Context) {
        val file = exportCsv(context) ?: return
        ShareHelper.shareFile(
            context = context,
            file = file,
            mimeType = "text/csv",
            chooserTitle = "Export CSV Report"
        )
    }

    fun exportPartyStatementPdf(context: Context): File? {
        val stmt = partyStatementReport.value ?: return null
        return PdfGenerator.generatePartyStatementPdf(
            context = context,
            statement = stmt,
            shopName = shopName.value,
            shopPhone = shopPhone.value,
            currency = currency.value
        )
    }

    fun sharePartyStatementPdf(context: Context) {
        val file = exportPartyStatementPdf(context) ?: return
        ShareHelper.shareFile(
            context = context,
            file = file,
            mimeType = "application/pdf",
            chooserTitle = "Share Statement PDF"
        )
    }
}
