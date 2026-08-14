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
    
    private val _orders = MutableStateFlow<List<com.nexaleads.app.data.model.Order>>(emptyList())
    val orders: StateFlow<List<com.nexaleads.app.data.model.Order>> = _orders

    private val _lifetimeValue = MutableStateFlow(0L)
    val lifetimeValue: StateFlow<Long> = _lifetimeValue
    
    private val _myLtv = MutableStateFlow(0L)
    val myLtv: StateFlow<Long> = _myLtv

    private val _teamLtv = MutableStateFlow(0L)
    val teamLtv: StateFlow<Long> = _teamLtv

    private val _myOrdersCount = MutableStateFlow(0)
    val myOrdersCount: StateFlow<Int> = _myOrdersCount

    private val _teamOrdersCount = MutableStateFlow(0)
    val teamOrdersCount: StateFlow<Int> = _teamOrdersCount

    val currentUserId: String = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    
    private val _activeLeadContext = MutableStateFlow<Lead?>(null)
    val activeLeadContext: StateFlow<Lead?> = _activeLeadContext
    
    private var ordersJob: kotlinx.coroutines.Job? = null

    fun fetchCustomerData(phone: String, initialContextLeadId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch all leads for this phone
                val fetchedLeads = repository.getCustomerLeads(phone)
                
                // Sort leads so the newest/most active is first
                val sortedLeads = fetchedLeads.sortedByDescending { it.updatedAt ?: 0L }
                _leads.value = sortedLeads

                // Calculate LTV from the parent lead (the one that has totalOrdersCount > 0 or the first one)
                val parentLead = sortedLeads.maxByOrNull { it.totalOrdersCount } ?: sortedLeads.firstOrNull()
                // LTV is now dynamically calculated when orders are fetched
                
                // Set the active context lead
                if (initialContextLeadId != null) {
                    _activeLeadContext.value = sortedLeads.find { it.id == initialContextLeadId } ?: parentLead
                } else {
                    _activeLeadContext.value = parentLead
                }
                
                // Cancel previous orders observer
                ordersJob?.cancel()
                
                // Fetch orders for this customer if a parent lead is found
                if (parentLead != null) {
                    ordersJob = launch {
                        repository.getOrdersForCustomer(parentLead.phone).collect { customerOrders ->
                            // Sort locally to avoid composite index requirements
                            val sortedOrders = customerOrders.sortedByDescending { it.createdAtMillis }
                            _orders.value = sortedOrders
                            // Dynamically calculate accurate LTV across all orders, excluding Cancelled and RTO
                            val validOrders = sortedOrders.filter { it.status != "Order Cancelled" && it.status != "Cancelled" && it.status != "RTO" && it.status != "Returned" }
                            _lifetimeValue.value = validOrders.sumOf { it.orderAmountNum }
                            
                            // Split logic
                            val myValidOrders = validOrders.filter { it.assignedTo == currentUserId }
                            val teamValidOrders = validOrders.filter { it.assignedTo != currentUserId }
                            
                            _myLtv.value = myValidOrders.sumOf { it.orderAmountNum }
                            _teamLtv.value = teamValidOrders.sumOf { it.orderAmountNum }
                            
                            _myOrdersCount.value = sortedOrders.count { it.assignedTo == currentUserId }
                            _teamOrdersCount.value = sortedOrders.count { it.assignedTo != currentUserId }
                        }
                    }
                } else {
                    _orders.value = emptyList()
                    _lifetimeValue.value = 0L
                    _myLtv.value = 0L
                    _teamLtv.value = 0L
                    _myOrdersCount.value = 0
                    _teamOrdersCount.value = 0
                }

                // Fetch interactions for all these leads (in case there are still unmerged legacy leads)
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
