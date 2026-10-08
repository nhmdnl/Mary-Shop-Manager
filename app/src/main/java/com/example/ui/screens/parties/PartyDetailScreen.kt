package com.example.ui.screens.parties

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LedgerEntry
import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.CreditGreenContainer
import com.example.ui.theme.CreditGreenText
import com.example.ui.theme.DebtRed
import com.example.ui.theme.DebtRedContainer
import com.example.ui.theme.DebtRedText
import com.example.ui.theme.PayableBlue
import com.example.ui.theme.PayableBlueContainer
import com.example.ui.theme.PayableBlueText
import com.example.ui.theme.SettledGray
import com.example.ui.theme.SettledGrayContainer
import com.example.ui.viewmodel.PartyDetailViewModel
import com.example.util.CsvExporter
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import com.example.util.PdfGenerator
import com.example.util.ShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyDetailScreen(
    viewModel: PartyDetailViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val party by viewModel.party.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val currentBalance by viewModel.currentBalance.collectAsStateWithLifecycle()
    val isOverLimit by viewModel.isOverCreditLimit.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statementReport by viewModel.statementReport.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    var showAddTxDialog by remember { mutableStateOf(false) }
    var initialTxType by remember { mutableStateOf(TransactionType.SALE_ON_CREDIT) }
    var txToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var txToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showDeletedEntries by remember { mutableStateOf(false) }

    if (party == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Party Details") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Party not found")
            }
        }
        return
    }

    val p = party!!
    val isCustomer = p.type == PartyType.CUSTOMER || p.type == PartyType.BOTH
    val isSupplier = p.type == PartyType.SUPPLIER

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = p.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = p.type.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Call action
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone.trim()}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.testTag("detail_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call party")
                    }

                    // Share Statement PDF
                    IconButton(
                        onClick = {
                            statementReport?.let { statement ->
                                val pdfFile = PdfGenerator.generatePartyStatementPdf(
                                    context = context,
                                    statement = statement,
                                    shopName = settings.shopName,
                                    shopPhone = settings.shopPhone,
                                    currency = settings.currency
                                )
                                ShareHelper.shareFile(
                                    context = context,
                                    file = pdfFile,
                                    mimeType = "application/pdf",
                                    chooserTitle = "Share Statement for ${p.name}"
                                )
                            }
                        },
                        modifier = Modifier.testTag("detail_share_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Share PDF Statement")
                    }

                    // Export Statement CSV
                    IconButton(
                        onClick = {
                            statementReport?.let { statement ->
                                val csvFile = CsvExporter.exportPartyStatementCsv(
                                    context = context,
                                    statement = statement,
                                    currency = settings.currency
                                )
                                ShareHelper.shareFile(
                                    context = context,
                                    file = csvFile,
                                    mimeType = "text/csv",
                                    chooserTitle = "Share Statement CSV"
                                )
                            }
                        },
                        modifier = Modifier.testTag("detail_share_csv_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Statement CSV")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Accessible Transaction Action Bar
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCustomer) {
                        Button(
                            onClick = {
                                initialTxType = TransactionType.SALE_ON_CREDIT
                                showAddTxDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DebtRed),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_sale_credit_button")
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sale Credit", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                initialTxType = TransactionType.PAYMENT_RECEIVED
                                showAddTxDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CreditGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_payment_received_button")
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Payment Recv", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = {
                                initialTxType = TransactionType.PURCHASE_ON_CREDIT
                                showAddTxDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PayableBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_purchase_credit_button")
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Purchase Credit", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                initialTxType = TransactionType.PAYMENT_MADE
                                showAddTxDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_payment_made_button")
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Payment Made", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Adjustment / Other button
                    OutlinedButton(
                        onClick = {
                            initialTxType = TransactionType.ADJUSTMENT
                            showAddTxDialog = true
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_adjustment_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Party Info & Current Balance Hero Card
            item {
                PartyHeroCard(
                    party = p,
                    currentBalance = currentBalance,
                    currency = settings.currency,
                    isOverCreditLimit = isOverLimit,
                    onSendReminder = { showReminderDialog = true }
                )
            }

            // Ledger Running Balance Header & Filter
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ledger History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Showing running balance • Immutable audit trail",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilterChip(
                        selected = showDeletedEntries,
                        onClick = { showDeletedEntries = !showDeletedEntries },
                        label = { Text("Audit Trail") }
                    )
                }
            }

            // Ledger Entries
            val displayedEntries = if (showDeletedEntries) ledger else ledger.filterNot { it.transaction.isDeleted }

            if (displayedEntries.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No transactions yet. Opening balance is ${CurrencyFormatter.format(p.openingBalance, settings.currency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(displayedEntries, key = { it.transaction.id }) { entry ->
                    LedgerEntryCard(
                        entry = entry,
                        party = p,
                        currency = settings.currency,
                        onEdit = { txToEdit = entry.transaction },
                        onDelete = { txToDelete = entry.transaction }
                    )
                }
            }
        }
    }

    // Add Transaction Dialog
    if (showAddTxDialog) {
        AddTransactionDialog(
            party = p,
            currentBalance = currentBalance,
            currency = settings.currency,
            initialType = initialTxType,
            onDismiss = { showAddTxDialog = false },
            onConfirm = { type, amount, method, ref, note, direction ->
                viewModel.addTransaction(
                    type = type,
                    amount = amount,
                    paymentMethod = method,
                    referenceNo = ref,
                    note = note,
                    adjustmentDirection = direction
                ) {
                    showAddTxDialog = false
                }
            }
        )
    }

    // Edit Transaction Dialog (creates reversal + replacement)
    txToEdit?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            currency = settings.currency,
            onDismiss = { txToEdit = null },
            onConfirm = { type, amount, method, ref, note, direction ->
                viewModel.editTransaction(
                    originalId = tx.id,
                    type = type,
                    amount = amount,
                    paymentMethod = method,
                    referenceNo = ref,
                    note = note,
                    adjustmentDirection = direction
                ) {
                    txToEdit = null
                }
            }
        )
    }

    // Soft Delete Dialog
    txToDelete?.let { tx ->
        SoftDeleteDialog(
            transaction = tx,
            currency = settings.currency,
            onDismiss = { txToDelete = null },
            onConfirm = { reason ->
                viewModel.softDeleteTransaction(tx.id, reason) {
                    txToDelete = null
                }
            }
        )
    }

    // Send Reminder Dialog
    if (showReminderDialog) {
        val balanceFormatted = CurrencyFormatter.format(currentBalance, settings.currency)
        SendReminderDialog(
            party = p,
            formattedBalance = balanceFormatted,
            shopName = settings.shopName,
            shopPhone = settings.shopPhone,
            onDismiss = { showReminderDialog = false }
        )
    }
}

@Composable
fun PartyHeroCard(
    party: Party,
    currentBalance: Long,
    currency: String,
    isOverCreditLimit: Boolean,
    onSendReminder: () -> Unit
) {
    val isCustomer = party.type == PartyType.CUSTOMER || party.type == PartyType.BOTH
    val isSupplier = party.type == PartyType.SUPPLIER

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOverCreditLimit -> DebtRedContainer
                isCustomer && currentBalance > 0 -> MaterialTheme.colorScheme.primaryContainer
                isSupplier && currentBalance > 0 -> PayableBlueContainer
                else -> SettledGrayContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Balance Title & Amount
            Text(
                text = when {
                    isCustomer && currentBalance > 0 -> "OUTSTANDING BALANCE (OWES YOU)"
                    isSupplier && currentBalance > 0 -> "OUTSTANDING PAYABLE (YOU OWE)"
                    currentBalance < 0 -> "ADVANCE BALANCE"
                    else -> "ACCOUNT SETTLED"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    isOverCreditLimit -> DebtRedText
                    isSupplier && currentBalance > 0 -> PayableBlueText
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = CurrencyFormatter.format(kotlin.math.abs(currentBalance), currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    isOverCreditLimit -> DebtRed
                    isSupplier && currentBalance > 0 -> PayableBlue
                    currentBalance == 0L -> SettledGray
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            // Credit Limit Progress & Warning
            if (isCustomer && party.creditLimit > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                val ratio = (currentBalance.toFloat() / party.creditLimit.toFloat()).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Credit Limit: ${CurrencyFormatter.format(party.creditLimit, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${((currentBalance.toFloat() / party.creditLimit) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverCreditLimit) DebtRed else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isOverCreditLimit) DebtRed else MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.5f)
                )

                if (isOverCreditLimit) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DebtRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Warning: Customer is over credit limit by ${CurrencyFormatter.format(currentBalance - party.creditLimit, currency)}!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = DebtRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action: Send Reminder Button
            Button(
                onClick = onSendReminder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverCreditLimit) DebtRed else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("send_reminder_button")
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Payment Reminder (SMS/WhatsApp)")
            }

            // Party Contact Details
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Phone: ${party.phone}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (party.address.isNotBlank()) {
                    Text(
                        text = party.address,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerEntryCard(
    entry: LedgerEntry,
    party: Party,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tx = entry.transaction
    var showMenu by remember { mutableStateOf(false) }

    val isVoided = tx.isDeleted
    val isReversal = tx.isReversal
    val wasReplaced = tx.replacementTransactionId != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ledger_entry_${tx.id}"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isVoided -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                isReversal -> AmberContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isVoided) 0.dp else 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Date, Badge, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = DateFormatter.formatDateTime(tx.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Audit Status Badges
                    if (isVoided) {
                        Surface(
                            color = DebtRedContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "VOIDED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = DebtRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isReversal) {
                        Surface(
                            color = AmberContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "REVERSAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberOnContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (wasReplaced) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "MODIFIED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 3-dots Menu for Edit / Delete
                if (!isVoided && !isReversal) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Entry (Immutable Audit)") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Void Entry (Soft Delete)") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DebtRed) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Transaction Type, Note, and Amounts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.type.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tx.paymentMethod.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tx.referenceNo.isNotBlank()) {
                            Text(
                                text = "• Ref: ${tx.referenceNo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (tx.note.isNotBlank()) {
                        Text(
                            text = tx.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (isVoided && !tx.deleteReason.isNullOrBlank()) {
                        Text(
                            text = "Void reason: ${tx.deleteReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DebtRed,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Financial Delta & Running Balance Column
                Column(horizontalAlignment = Alignment.End) {
                    val formattedDelta = CurrencyFormatter.format(entry.delta, currency, showSign = true)
                    Text(
                        text = formattedDelta,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isVoided -> MaterialTheme.colorScheme.outline
                            entry.delta > 0 -> DebtRed
                            entry.delta < 0 -> CreditGreen
                            else -> SettledGray
                        },
                        textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None
                    )

                    // Running balance after this transaction
                    if (!isVoided) {
                        Text(
                            text = "Bal: ${CurrencyFormatter.format(entry.runningBalance, currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
