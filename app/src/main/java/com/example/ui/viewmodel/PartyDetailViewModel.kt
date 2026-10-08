package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LedgerEntry
import com.example.data.model.Party
import com.example.data.model.PartyStatementReport
import com.example.data.model.PartyType
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.preferences.ShopSettings
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PartyDetailViewModel(
    val partyId: Long,
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    val party: StateFlow<Party?> = repository.getParty(partyId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val ledger: StateFlow<List<LedgerEntry>> = repository.getLedgerForParty(partyId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val settings: StateFlow<ShopSettings> = settingsManager.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShopSettings()
        )

    val statementReport: StateFlow<PartyStatementReport?> = repository.getPartyStatement(partyId, 0L, Long.MAX_VALUE)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Current balance computed from latest active ledger entry (or opening balance if no transactions)
    val currentBalance: StateFlow<Long> = combine(party, ledger) { p, entries ->
        if (p == null) return@combine 0L
        val activeEntries = entries.filterNot { it.transaction.isDeleted }
        if (activeEntries.isEmpty()) {
            p.openingBalance
        } else {
            // First entry in the list is the latest transaction because entries are reversed in repository
            activeEntries.first().runningBalance
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val isOverCreditLimit: StateFlow<Boolean> = combine(party, currentBalance) { p, bal ->
        if (p == null) return@combine false
        (p.type == PartyType.CUSTOMER || p.type == PartyType.BOTH) &&
                p.creditLimit > 0L && bal > p.creditLimit
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addTransaction(
        type: TransactionType,
        amount: Long,
        paymentMethod: PaymentMethod,
        referenceNo: String,
        note: String,
        adjustmentDirection: Int = 1,
        date: Long = System.currentTimeMillis(),
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val transaction = TransactionEntity(
                partyId = partyId,
                type = type,
                amount = amount,
                date = date,
                paymentMethod = paymentMethod,
                referenceNo = referenceNo.trim(),
                note = note.trim(),
                adjustmentDirection = adjustmentDirection
            )
            repository.addTransaction(transaction)
            _userMessage.value = "Transaction recorded successfully"
            onComplete?.invoke()
        }
    }

    /**
     * Edit transaction according to Rule #7:
     * Never edit in place: creates a reversal plus a new entry!
     */
    fun editTransaction(
        originalId: Long,
        type: TransactionType,
        amount: Long,
        paymentMethod: PaymentMethod,
        referenceNo: String,
        note: String,
        adjustmentDirection: Int = 1,
        date: Long = System.currentTimeMillis(),
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val newTransaction = TransactionEntity(
                    partyId = partyId,
                    type = type,
                    amount = amount,
                    date = date,
                    paymentMethod = paymentMethod,
                    referenceNo = referenceNo.trim(),
                    note = note.trim(),
                    adjustmentDirection = adjustmentDirection
                )
                repository.editTransaction(originalId, newTransaction)
                _userMessage.value = "Audit trail updated: Reversal & new entry created"
                onComplete?.invoke()
            } catch (e: Exception) {
                _userMessage.value = "Failed to update: ${e.message}"
            }
        }
    }

    /**
     * Soft delete transaction according to Rule #7:
     * Soft delete only!
     */
    fun softDeleteTransaction(id: Long, reason: String = "Deleted by user", onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.softDeleteTransaction(id, reason)
            _userMessage.value = "Transaction voided (soft deleted)"
            onComplete?.invoke()
        }
    }

    fun updateParty(
        name: String,
        phone: String,
        type: PartyType,
        address: String,
        notes: String,
        creditLimit: Long
    ) {
        val current = party.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = name.trim(),
                phone = phone.trim(),
                type = type,
                address = address.trim(),
                notes = notes.trim(),
                creditLimit = creditLimit,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateParty(updated)
            _userMessage.value = "Party updated"
        }
    }
}
