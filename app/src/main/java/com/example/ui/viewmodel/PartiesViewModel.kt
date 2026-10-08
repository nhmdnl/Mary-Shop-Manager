package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Party
import com.example.data.model.PartyType
import com.example.data.model.PartyWithBalance
import com.example.data.preferences.ShopSettingsManager
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BalanceFilter(val label: String) {
    ALL("All Parties"),
    OWES_ME("Owes Me"),
    I_OWE("I Owe"),
    SETTLED("Settled")
}

enum class TypeFilter(val label: String) {
    ALL("All Types"),
    CUSTOMERS("Customers"),
    SUPPLIERS("Suppliers")
}

enum class SortOption(val label: String) {
    BALANCE_DESC("Highest Balance"),
    BALANCE_ASC("Lowest Balance"),
    LAST_ACTIVITY_DESC("Recent Activity"),
    NAME_ASC("Name (A-Z)")
}

class PartiesViewModel(
    private val repository: ShopRepository,
    private val settingsManager: ShopSettingsManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _balanceFilter = MutableStateFlow(BalanceFilter.ALL)
    val balanceFilter: StateFlow<BalanceFilter> = _balanceFilter.asStateFlow()

    private val _typeFilter = MutableStateFlow(TypeFilter.ALL)
    val typeFilter: StateFlow<TypeFilter> = _typeFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.BALANCE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    val currency: StateFlow<String> = settingsManager.settings
        .map { it.currency }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "UGX"
        )

    val filteredParties: StateFlow<List<PartyWithBalance>> = combine(
        repository.partiesWithBalances,
        _searchQuery,
        _balanceFilter,
        _typeFilter,
        _sortOption
    ) { parties, query, bFilter, tFilter, sort ->
        parties
            .filter { partyWithBalance ->
                val p = partyWithBalance.party
                // Search filter
                val matchesQuery = query.isBlank() ||
                        p.name.contains(query, ignoreCase = true) ||
                        p.phone.contains(query, ignoreCase = true) ||
                        p.address.contains(query, ignoreCase = true)

                // Type filter
                val matchesType = when (tFilter) {
                    TypeFilter.ALL -> true
                    TypeFilter.CUSTOMERS -> p.type == PartyType.CUSTOMER || p.type == PartyType.BOTH
                    TypeFilter.SUPPLIERS -> p.type == PartyType.SUPPLIER || p.type == PartyType.BOTH
                }

                // Balance filter
                val matchesBalance = when (bFilter) {
                    BalanceFilter.ALL -> true
                    BalanceFilter.OWES_ME -> partyWithBalance.receivables > 0
                    BalanceFilter.I_OWE -> partyWithBalance.payables > 0
                    BalanceFilter.SETTLED -> partyWithBalance.balance == 0L
                }

                matchesQuery && matchesType && matchesBalance
            }
            .sortedWith { a, b ->
                when (sort) {
                    SortOption.BALANCE_DESC -> kotlin.math.abs(b.balance).compareTo(kotlin.math.abs(a.balance))
                    SortOption.BALANCE_ASC -> kotlin.math.abs(a.balance).compareTo(kotlin.math.abs(b.balance))
                    SortOption.LAST_ACTIVITY_DESC -> b.lastActivityDate.compareTo(a.lastActivityDate)
                    SortOption.NAME_ASC -> a.party.name.compareTo(b.party.name, ignoreCase = true)
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onBalanceFilterSelected(filter: BalanceFilter) {
        _balanceFilter.value = filter
    }

    fun onTypeFilterSelected(filter: TypeFilter) {
        _typeFilter.value = filter
    }

    fun onSortOptionSelected(option: SortOption) {
        _sortOption.value = option
    }

    fun addParty(
        name: String,
        phone: String,
        type: PartyType,
        address: String,
        notes: String,
        openingBalance: Long,
        creditLimit: Long,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val party = Party(
                name = name.trim(),
                phone = phone.trim(),
                type = type,
                address = address.trim(),
                notes = notes.trim(),
                openingBalance = openingBalance,
                creditLimit = creditLimit
            )
            val id = repository.addParty(party)
            onSuccess(id)
        }
    }

    fun deleteParty(party: Party) {
        viewModelScope.launch {
            repository.deleteParty(party)
        }
    }
}
