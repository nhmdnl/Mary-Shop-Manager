package com.example.ui.screens.reports

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DateRangeType
import com.example.data.model.DebtAgingSummary
import com.example.data.model.DebtRiskLevel
import com.example.data.model.DebtorAgingRow
import com.example.data.model.Party
import com.example.data.model.PartyStatementReport
import com.example.data.model.PartyType
import com.example.data.model.ProductProfitItem
import com.example.data.model.ProductProfitReport
import com.example.data.model.ReportTab
import com.example.data.model.SaleEntity
import com.example.data.model.SalesReportSummary
import com.example.data.model.StockMovementReport
import com.example.data.model.StockMovementType
import com.example.data.model.StockMovementWithDetails
import com.example.data.model.TransactionType
import com.example.ui.theme.DebtRed
import com.example.ui.theme.DebtRedContainer
import com.example.ui.theme.DebtRedText
import com.example.ui.theme.PayableBlue
import com.example.ui.theme.PayableBlueContainer
import com.example.ui.theme.PayableBlueText
import com.example.ui.viewmodel.ReportsViewModel
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToParty: (Long) -> Unit = {}
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val allParties by viewModel.allParties.collectAsStateWithLifecycle()
    val selectedPartyId by viewModel.selectedPartyId.collectAsStateWithLifecycle()

    val salesReport by viewModel.salesReport.collectAsStateWithLifecycle()
    val profitReport by viewModel.profitReport.collectAsStateWithLifecycle()
    val partyStatementReport by viewModel.partyStatementReport.collectAsStateWithLifecycle()
    val stockMovementReport by viewModel.stockMovementReport.collectAsStateWithLifecycle()
    val debtAgingReport by viewModel.debtAgingReport.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reports & Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedTab.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("reports_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // If in Party Statement, offer PDF export
                    if (selectedTab == ReportTab.STATEMENT && partyStatementReport != null) {
                        IconButton(
                            onClick = { viewModel.sharePartyStatementPdf(context) },
                            modifier = Modifier.testTag("share_statement_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Share PDF Statement"
                            )
                        }
                    }

                    // CSV Export for all reports
                    IconButton(
                        onClick = { viewModel.shareCsv(context) },
                        modifier = Modifier.testTag("share_report_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share CSV"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scrollable Tabs
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                ReportTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("report_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Date Range Bar (not needed for Debt Aging, as it's an as-of-now calculation)
            if (selectedTab != ReportTab.DEBT_AGING) {
                DateRangeFilterBar(
                    currentRangeType = dateRange.type,
                    onSelectRangeType = { viewModel.setDateRangeType(it) },
                    onCustomDatePick = { start, end -> viewModel.setCustomRange(start, end) }
                )
            }

            // Body Content based on active tab
            when (selectedTab) {
                ReportTab.SALES -> {
                    SalesReportContent(
                        report = salesReport,
                        currency = currency
                    )
                }
                ReportTab.PROFIT -> {
                    ProfitPerProductContent(
                        report = profitReport,
                        currency = currency
                    )
                }
                ReportTab.STATEMENT -> {
                    PartyStatementContent(
                        parties = allParties,
                        selectedPartyId = selectedPartyId,
                        onSelectParty = { viewModel.selectParty(it) },
                        statement = partyStatementReport,
                        currency = currency,
                        onSharePdf = { viewModel.sharePartyStatementPdf(context) },
                        onShareCsv = { viewModel.shareCsv(context) }
                    )
                }
                ReportTab.STOCK_MOVEMENT -> {
                    StockMovementContent(
                        report = stockMovementReport,
                        currency = currency
                    )
                }
                ReportTab.DEBT_AGING -> {
                    DebtAgingContent(
                        aging = debtAgingReport,
                        currency = currency,
                        onPartyClick = onNavigateToParty
                    )
                }
            }
        }
    }
}

@Composable
fun DateRangeFilterBar(
    currentRangeType: DateRangeType,
    onSelectRangeType: (DateRangeType) -> Unit,
    onCustomDatePick: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var showCustomPicker by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(DateRangeType.entries) { type ->
                FilterChip(
                    selected = currentRangeType == type,
                    onClick = {
                        if (type == DateRangeType.CUSTOM) {
                            showCustomPicker = true
                        } else {
                            onSelectRangeType(type)
                        }
                    },
                    label = {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("date_filter_${type.name.lowercase()}")
                )
            }
        }
    }

    if (showCustomPicker) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val startCal = Calendar.getInstance()
                startCal.set(year, month, dayOfMonth, 0, 0, 0)
                val start = startCal.timeInMillis
                val end = System.currentTimeMillis()
                onCustomDatePick(start, end)
                showCustomPicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

// -------------------------------------------------------------
// 1. SALES REPORT VIEW
// -------------------------------------------------------------
@Composable
fun SalesReportContent(
    report: SalesReportSummary?,
    currency: String
) {
    if (report == null || report.salesCount == 0) {
        EmptyReportView(
            message = "No sales recorded in this date range",
            icon = Icons.Default.TrendingUp
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // High Level KPI Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Revenue
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "TOTAL SALES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.format(report.totalSales, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${report.salesCount} invoices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Profit
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "GROSS PROFIT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.format(report.totalProfit, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B5E20)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Margin: ${String.format("%.1f", report.profitMarginPercent)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }

        // Secondary Payment Breakdown Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cash Received",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(report.cashSalesTotal, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Credit / Due",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(report.creditSalesTotal, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (report.creditSalesTotal > 0) DebtRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Sales Records (${report.sales.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(report.sales) { sale ->
            SaleReportRow(sale = sale, currency = currency)
        }
    }
}

@Composable
fun SaleReportRow(
    sale: SaleEntity,
    currency: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sale.invoiceNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (sale.paymentMode.label) {
                            "Cash" -> Color(0xFFE8F5E9)
                            "Credit" -> DebtRedContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = sale.paymentMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (sale.paymentMode.label) {
                                "Cash" -> Color(0xFF2E7D32)
                                "Credit" -> DebtRedText
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val partyLabel = sale.customerName ?: "Walk-in"
                Text(
                    text = "$partyLabel • ${DateFormatter.formatDate(sale.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.format(sale.finalTotal, currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+${CurrencyFormatter.format(sale.profit, currency)} profit",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. PROFIT PER PRODUCT VIEW
// -------------------------------------------------------------
@Composable
fun ProfitPerProductContent(
    report: ProductProfitReport?,
    currency: String
) {
    if (report == null || report.items.isEmpty()) {
        EmptyReportView(
            message = "No product sales recorded in this date range",
            icon = Icons.Default.AttachMoney
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Summary Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "NET PRODUCT PROFIT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.format(report.totalProfit, currency),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1B5E20)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "MARGIN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format("%.1f", report.overallMarginPercent)}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Product Revenue: ${CurrencyFormatter.format(report.totalRevenue, currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Cost of Goods: ${CurrencyFormatter.format(report.totalCost, currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Products Ranked by Profit (${report.items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(report.items) { item ->
            ProductProfitRow(item = item, currency = currency)
        }
    }
}

@Composable
fun ProductProfitRow(
    item: ProductProfitItem,
    currency: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.category} • Sold: ${item.quantitySold} ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.format(item.totalProfit, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "${String.format("%.1f", item.profitMarginPercent)}% margin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mini bar indicator of margin
            LinearProgressIndicator(
                progress = { (item.profitMarginPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF2E7D32),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Rev: ${CurrencyFormatter.format(item.totalRevenue, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Cost: ${CurrencyFormatter.format(item.totalCost, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 3. PARTY STATEMENT VIEW
// -------------------------------------------------------------
@Composable
fun PartyStatementContent(
    parties: List<Party>,
    selectedPartyId: Long?,
    onSelectParty: (Long) -> Unit,
    statement: PartyStatementReport?,
    currency: String,
    onSharePdf: () -> Unit,
    onShareCsv: () -> Unit
) {
    var showPartyMenu by remember { mutableStateOf(false) }
    val currentParty = parties.firstOrNull { it.id == selectedPartyId } ?: parties.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Party Selector Dropdown Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { showPartyMenu = true },
                        modifier = Modifier.testTag("statement_party_selector_button")
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentParty?.name ?: "Select Party",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = showPartyMenu,
                        onDismissRequest = { showPartyMenu = false }
                    ) {
                        parties.forEach { party ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(party.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${party.type.name} • ${party.phone}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectParty(party.id)
                                    showPartyMenu = false
                                }
                            )
                        }
                    }
                }

                // Export buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onSharePdf,
                        modifier = Modifier.testTag("statement_export_pdf_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Share PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onShareCsv,
                        modifier = Modifier.testTag("statement_export_csv_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share CSV",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (statement == null) {
            EmptyReportView(
                message = "Select a party to view their account statement",
                icon = Icons.Default.Person
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Party Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (statement.closingBalance > 0) DebtRedContainer else PayableBlueContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = statement.party.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (statement.closingBalance > 0) DebtRedText else PayableBlueText
                                )
                                Text(
                                    text = "${statement.party.phone} • ${statement.party.type.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (statement.closingBalance > 0) DebtRedText.copy(alpha = 0.8f) else PayableBlueText.copy(alpha = 0.8f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "BALANCE DUE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = CurrencyFormatter.format(statement.closingBalance, currency),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (statement.closingBalance > 0) DebtRed else Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Period Debit: ${CurrencyFormatter.format(statement.totalDebits, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Period Credit: ${CurrencyFormatter.format(statement.totalCredits, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Transaction Ledger Entries (${statement.transactions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (statement.transactions.isEmpty()) {
                item {
                    Text(
                        text = "No ledger entries in this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(statement.transactions) { line ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = DateFormatter.formatDateTime(line.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val amountText = if (line.debit > 0) {
                                    "+${CurrencyFormatter.format(line.debit, currency)}"
                                } else {
                                    "-${CurrencyFormatter.format(line.credit, currency)}"
                                }
                                val amountColor = if (line.debit > 0) DebtRed else Color(0xFF2E7D32)
                                Text(
                                    text = amountText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor
                                )
                                Text(
                                    text = "Bal: ${CurrencyFormatter.format(line.runningBalance, currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. STOCK MOVEMENT VIEW
// -------------------------------------------------------------
@Composable
fun StockMovementContent(
    report: StockMovementReport?,
    currency: String
) {
    if (report == null || report.movements.isEmpty()) {
        EmptyReportView(
            message = "No stock movements recorded in this date range",
            icon = Icons.Default.Inventory2
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // High level counters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StockMetricChip(
                    label = "Restocked",
                    count = "+${report.totalPurchaseInQty}",
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                StockMetricChip(
                    label = "Sold",
                    count = "-${report.totalSaleOutQty}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StockMetricChip(
                    label = "Adjusted",
                    count = "${report.totalAdjustmentQty}",
                    color = Color(0xFFE65100),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "Inventory Audit Log (${report.movements.size} events)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(report.movements) { item ->
            StockMovementRow(item = item, currency = currency)
        }
    }
}

@Composable
fun StockMetricChip(
    label: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
fun StockMovementRow(
    item: StockMovementWithDetails,
    currency: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (item.movement.type) {
                        StockMovementType.PURCHASE_IN -> Color(0xFF2E7D32)
                        StockMovementType.SALE_OUT -> MaterialTheme.colorScheme.primary
                        StockMovementType.RETURN -> Color(0xFF1565C0)
                        StockMovementType.DAMAGE_LOSS -> DebtRed
                        StockMovementType.ADJUSTMENT -> Color(0xFFE65100)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.movement.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = listOfNotNull(
                    DateFormatter.formatDateTime(item.movement.date),
                    item.movement.note.ifBlank { null }
                ).joinToString(" • ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val qtyPrefix = if (item.movement.isStockIn) "+${item.movement.quantity}" else "-${item.movement.quantity}"
                val qtyColor = if (item.movement.isStockIn) Color(0xFF2E7D32) else DebtRed
                Text(
                    text = "$qtyPrefix ${item.productUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = qtyColor
                )
                item.movement.unitPrice?.let { cost ->
                    if (cost > 0) {
                        Text(
                            text = "@ ${CurrencyFormatter.format(cost, currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. DEBT AGING VIEW (0–30 / 31–60 / 61–90 / 90+)
// -------------------------------------------------------------
@Composable
fun DebtAgingContent(
    aging: DebtAgingSummary?,
    currency: String,
    onPartyClick: (Long) -> Unit
) {
    if (aging == null || aging.totalReceivables <= 0) {
        EmptyReportView(
            message = "All accounts are settled! No outstanding debts found.",
            icon = Icons.Default.TrendingUp
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // High Level Outstanding Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DebtRedContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TOTAL OUTSTANDING RECEIVABLES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = DebtRedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.format(aging.totalReceivables, currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = DebtRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${aging.debtorCount} parties with unpaid debts",
                        style = MaterialTheme.typography.bodySmall,
                        color = DebtRedText.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 4 Aging Bucket Cards
        item {
            Text(
                text = "Aging Breakdown (0–30 / 31–60 / 61–90 / 90+)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgingBucketCard(
                    bucketName = "0–30 Days",
                    amount = aging.bucket0To30,
                    count = aging.debtors.count { it.riskLevel == DebtRiskLevel.CURRENT },
                    accentColor = Color(0xFF2E7D32),
                    currency = currency,
                    modifier = Modifier.weight(1f)
                )
                AgingBucketCard(
                    bucketName = "31–60 Days",
                    amount = aging.bucket31To60,
                    count = aging.debtors.count { it.riskLevel == DebtRiskLevel.ATTENTION },
                    accentColor = Color(0xFFF57C00),
                    currency = currency,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgingBucketCard(
                    bucketName = "61–90 Days",
                    amount = aging.bucket61To90,
                    count = aging.debtors.count { it.riskLevel == DebtRiskLevel.HIGH_RISK },
                    accentColor = Color(0xFFE64A19),
                    currency = currency,
                    modifier = Modifier.weight(1f)
                )
                AgingBucketCard(
                    bucketName = "90+ Days",
                    amount = aging.bucket90Plus,
                    count = aging.debtors.count { it.riskLevel == DebtRiskLevel.CRITICAL },
                    accentColor = Color(0xFFB71C1C),
                    currency = currency,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "All Debtors List (${aging.debtors.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(aging.debtors) { item ->
            PartyDebtAgingRow(
                item = item,
                currency = currency,
                onClick = { onPartyClick(item.party.id) }
            )
        }
    }
}

@Composable
fun AgingBucketCard(
    bucketName: String,
    amount: Long,
    count: Int,
    accentColor: Color,
    currency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = bucketName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = CurrencyFormatter.format(amount, currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count debtors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PartyDebtAgingRow(
    item: DebtorAgingRow,
    currency: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.party.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val bucketColor = when (item.riskLevel) {
                        DebtRiskLevel.CURRENT -> Color(0xFF2E7D32)
                        DebtRiskLevel.ATTENTION -> Color(0xFFF57C00)
                        DebtRiskLevel.HIGH_RISK -> Color(0xFFE64A19)
                        DebtRiskLevel.CRITICAL -> Color(0xFFB71C1C)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = bucketColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.riskLevel.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = bucketColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val oldestDebtDays = if (item.oldestDebtDate > 0) {
                    ((System.currentTimeMillis() - item.oldestDebtDate) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
                } else 0
                Text(
                    text = "${item.party.phone} • Oldest debt: $oldestDebtDays days ago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.format(item.totalDebt, currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = DebtRed
                )
                Text(
                    text = "View Ledger →",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Common Empty View
// -------------------------------------------------------------
@Composable
fun EmptyReportView(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
