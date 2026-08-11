package com.nexaleads.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.repository.LeadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class Customer360ViewModel @Inject constructor(
    private val repository: LeadRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _leads = MutableStateFlow<List<Lead>>(emptyList())
    val leads: StateFlow<List<Lead>> = _leads

    private val _interactions = MutableStateFlow<List<Interaction>>(emptyList())
    val interactions: StateFlow<List<Interaction>> = _interactions

    private val _lifetimeValue = MutableStateFlow(0L)
    val lifetimeValue: StateFlow<Long> = _lifetimeValue
    
    private val _activeLeadContext = MutableStateFlow<Lead?>(null)
    val activeLeadContext: StateFlow<Lead?> = _activeLeadContext

    fun fetchCustomerData(phone: String, initialContextLeadId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch all leads for this phone
                val fetchedLeads = repository.getCustomerLeads(phone)
                
                // Sort leads so the newest/most active is first
                val sortedLeads = fetchedLeads.sortedByDescending { it.updatedAt ?: 0L }
                _leads.value = sortedLeads

                // Calculate LTV
                var ltv = 0L
                sortedLeads.forEach { lead ->
                    val normStatus = Constants.normalizeStatus(lead.status)
                    if (normStatus == Constants.STATUS_DELIVERED || normStatus == Constants.STATUS_ORDER_PLACED || normStatus == Constants.STATUS_DISPATCHED) {
                        ltv += lead.orderAmountNum
                    }
                }
                _lifetimeValue.value = ltv
                
                // Set the active context lead (either the one they clicked, or the most recent one)
                if (initialContextLeadId != null) {
                    _activeLeadContext.value = sortedLeads.find { it.id == initialContextLeadId } ?: sortedLeads.firstOrNull()
                } else {
                    _activeLeadContext.value = sortedLeads.firstOrNull()
                }

                // Fetch interactions for all these leads
                val leadIds = sortedLeads.map { it.id }
                if (leadIds.isNotEmpty()) {
                    _interactions.value = repository.getCustomerInteractions(leadIds)
                } else {
                    _interactions.value = emptyList()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setActiveContext(lead: Lead) {
        _activeLeadContext.value = lead
    }
    
    suspend fun diagnoseRawData(phone: String): String {
        return repository.diagnoseRawData(phone)
    }
}
