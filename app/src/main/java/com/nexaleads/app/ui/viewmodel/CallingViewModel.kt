package com.nexaleads.app.ui.viewmodel

import com.nexaleads.app.Constants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.getPrimaryCategory
import com.nexaleads.app.data.models.Product
import com.nexaleads.app.data.models.Category
import com.nexaleads.app.data.repository.LeadRepository
import com.nexaleads.app.utils.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class LeadFormDraft(
    val selectedNumber: String = "",
    val manualMode: Boolean = false,
    val clientName: String = "",
    val source: String = "",
    val selectedProduct: String = "",
    val selectedStatus: String = "",
    val selectedSubStatus: String = "",
    val selectedTimeSlot: String = "",
    val selectedPaymentStatus: String = "",
    val remarkNotes: String = "",
    val followUpDate: String = "",
    val shippingAddress: String = "",
    val shippingCity: String = "",
    val shippingState: String = "",
    val shippingPincode: String = "",
    val paymentMethod: String = "",
    val orderAmount: String = "",
    val originalTotalValue: String = "",
    val discountAmount: String = ""
)

data class SalesMetrics(
    val todayOrdersCount: Int = 0,
    val todayRevenue: Int = 0,
    val weeklyRevenue: Int = 0,
    val activeOrdersCount: Int = 0 // Used for general target tracking if needed
)

data class DashboardMetrics(
    val pendingPaymentsCount: Int = 0,
    val dueFollowupsCount: Int = 0,
    val freshLeadsCount: Int = 0,
    val confirmedOrdersCount: Int = 0,
    val inquiriesCount: Int = 0,
    val attemptedCount: Int = 0,
    val rejectedCount: Int = 0,
    val dispatchedCount: Int = 0,
    val rtoCount: Int = 0,
    val deliveredCount: Int = 0,
    val retentionDueCount: Int = 0
)

sealed class PipelineState {
    object Idle : PipelineState()
    object Loading : PipelineState()
    data class Success(
        val settled: List<Lead>,
        val pending: List<Lead>,
        val lost: List<Lead>,
        val all: List<Lead>
    ) : PipelineState()
    data class Error(val message: String, val isIndexError: Boolean = false, val indexUrl: String? = null) : PipelineState()
}

@HiltViewModel
class CallingViewModel @Inject constructor(
    private val repository: LeadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    private var callerName: String = "Agent"

    private val _leads = MutableStateFlow<List<Lead>>(emptyList())
    val leads: StateFlow<List<Lead>> = _leads

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _salesMetrics = MutableStateFlow(SalesMetrics())
    val salesMetrics: StateFlow<SalesMetrics> = _salesMetrics

    private val _dashboardMetrics = MutableStateFlow(DashboardMetrics())
    val dashboardMetrics: StateFlow<DashboardMetrics> = _dashboardMetrics

    private val _telecallerContact = MutableStateFlow<String>("+91 98347 83503")
    val telecallerContact: StateFlow<String> = _telecallerContact

    private val _orgName = MutableStateFlow<String>("ORGANIZATION")
    val orgName: StateFlow<String> = _orgName

    private val _pendingMediaLead = MutableStateFlow<Lead?>(null)
    val pendingMediaLead: StateFlow<Lead?> = _pendingMediaLead

    fun setPendingMediaLead(lead: Lead?) {
        _pendingMediaLead.value = lead
    }

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val prefs: SharedPreferences = context.getSharedPreferences("leadflow_prefs", Context.MODE_PRIVATE)

    private val _pendingCallLeadId = MutableStateFlow<String?>(prefs.getString("pending_call_lead_id", null))
    val pendingCallLeadId: StateFlow<String?> = _pendingCallLeadId

    private val _pendingCallTimestamp = MutableStateFlow<Long?>(
        if (prefs.contains("pending_call_timestamp")) prefs.getLong("pending_call_timestamp", 0L) else null
    )
    val pendingCallTimestamp: StateFlow<Long?> = _pendingCallTimestamp

    private val _pipelineActivityState = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val pipelineActivityState: StateFlow<PipelineState> = _pipelineActivityState
    
    private var pipelineJob: kotlinx.coroutines.Job? = null

    fun loadRevenueBreakdown() {
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            _pipelineActivityState.value = PipelineState.Loading
            _leads.collect { activities ->
                try {
                    val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    val todayStr = isoFormat.format(java.util.Date())
                    val todayStartMillis = isoFormat.parse(todayStr)?.time ?: 0L
                    
                    val settled = mutableListOf<Lead>()
                    val pending = mutableListOf<Lead>()
                    val lost = mutableListOf<Lead>()
                    val todayActivities = mutableListOf<Lead>()
                    
                    activities.forEach { lead ->
                        val isUpdatedToday = (lead.updatedAt ?: 0L) >= todayStartMillis
                        if (!isUpdatedToday) return@forEach
                        
                        todayActivities.add(lead)
                        
                        val category = lead.getPrimaryCategory()
                        val isValidRevenue = category == "CONVERTED" || category == "DISPATCHED" || category == "DELIVERED"
                        val isUpiPending = lead.paymentMethod.equals("Prepaid", ignoreCase = true) && 
                                           lead.paymentStatus?.equals("Link Sent", ignoreCase = true) == true
                                           
                        val fallbackCAt = lead.updatedAt?.let {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                .format(java.util.Date(it))
                        }
                        val cAt = lead.convertedAt ?: fallbackCAt ?: ""
                        
                        if (isValidRevenue && cAt >= todayStr && !isUpiPending) {
                            settled.add(lead)
                        } else if (isUpiPending) {
                            pending.add(lead)
                        } else if (category == "REJECTED" || category == "RTO") {
                            lost.add(lead)
                        }
                    }
                    
                    _pipelineActivityState.value = PipelineState.Success(settled, pending, lost, todayActivities)
                } catch (e: Exception) {
                    _pipelineActivityState.value = PipelineState.Error(
                        message = e.message ?: "Failed to load pipeline activity",
                        isIndexError = false,
                        indexUrl = null
                    )
                }
            }
        }
    }

    fun clearRevenueBreakdown() {
        pipelineJob?.cancel()
        pipelineJob = null
        _pipelineActivityState.value = PipelineState.Idle
    }

    init {
        // Auto-clear ghost calls (older than 2 hours)
        val currentTimestamp = _pendingCallTimestamp.value
        if (currentTimestamp != null && currentTimestamp > 0L) {
            val twoHoursInMillis = 2 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - currentTimestamp > twoHoursInMillis) {
                clearPendingCall()
            }
        }
        
        // Auto-clear ghost invoices (older than 2 hours)
        val invoiceTimestamp = prefs.getLong("pending_invoice_timestamp", 0L)
        if (invoiceTimestamp > 0L) {
            val twoHoursInMillis = 2 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - invoiceTimestamp > twoHoursInMillis) {
                clearPendingInvoice()
            }
        }
        
    }

    private fun isRevenue(status: String?, paymentMethod: String?, paymentStatus: String?): Boolean {
        if (status != "Order Placed" && status != "Dispatched" && status != "Delivered") return false
        if (paymentMethod == "Prepaid" && paymentStatus == "Link Sent") return false
        return true
    }

    private fun updateSalesMetricsOptimistically(amountDelta: Long, countDelta: Int) {
        // No-op: Realtime Firebase flow handles this automatically and accurately now
    }

    private fun updateMetricsOptimistically(
        oldStatus: String?,
        newStatus: String?,
        oldPaymentMethod: String? = null,
        oldPaymentStatus: String? = null,
        newPaymentMethod: String? = null,
        newPaymentStatus: String? = null
    ) {
        // No-op: Realtime Firebase count flows handle this automatically and accurately now
    }

    private fun refreshMetricsBackground() {
        // No-op: Realtime Firebase count flows handle this automatically and accurately now
    }

    fun setPendingCall(leadId: String) {
        val timestamp = System.currentTimeMillis()
        prefs.edit()
            .putString("pending_call_lead_id", leadId)
            .putLong("pending_call_timestamp", timestamp)
            .apply()
        _pendingCallLeadId.value = leadId
        _pendingCallTimestamp.value = timestamp
    }

    fun clearPendingCall() {
        prefs.edit()
            .remove("pending_call_lead_id")
            .remove("pending_call_timestamp")
            .apply()
        _pendingCallLeadId.value = null
        _pendingCallTimestamp.value = null
    }

    private val _pendingInvoiceLead = MutableStateFlow<Lead?>(null)
    val pendingInvoiceLead: StateFlow<Lead?> = _pendingInvoiceLead

    fun setPendingInvoice(lead: Lead) {
        prefs.edit()
            .putString("pending_invoice_lead_id", lead.id)
            .putLong("pending_invoice_timestamp", System.currentTimeMillis())
            .apply()
        _pendingInvoiceLead.value = lead
    }

    fun clearPendingInvoice() {
        prefs.edit()
            .remove("pending_invoice_lead_id")
            .remove("pending_invoice_timestamp")
            .apply()
        _pendingInvoiceLead.value = null
    }

    private fun loadDraftFromPrefs(): LeadFormDraft {
        return LeadFormDraft(
            selectedNumber = prefs.getString("draft_number", "") ?: "",
            manualMode = prefs.getBoolean("draft_manual", false),
            clientName = prefs.getString("draft_name", "") ?: "",
            source = prefs.getString("draft_source", "") ?: "",
            selectedProduct = prefs.getString("draft_product", "") ?: "",
            selectedStatus = prefs.getString("draft_status", "") ?: "",
            selectedSubStatus = prefs.getString("draft_sub_status", "") ?: "",
            selectedTimeSlot = prefs.getString("draft_time_slot", "") ?: "",
            selectedPaymentStatus = prefs.getString("draft_payment_status", "") ?: "",
            remarkNotes = prefs.getString("draft_remark", "") ?: "",
            followUpDate = prefs.getString("draft_followup", "") ?: "",
            shippingAddress = prefs.getString("draft_address", "") ?: "",
            shippingCity = prefs.getString("draft_city", "") ?: "",
            shippingState = prefs.getString("draft_state", "") ?: "",
            shippingPincode = prefs.getString("draft_pincode", "") ?: "",
            paymentMethod = prefs.getString("draft_payment_method", "") ?: "",
            orderAmount = prefs.getString("draft_order_amount", "") ?: "",
            originalTotalValue = prefs.getString("draft_original_total", "") ?: "",
            discountAmount = prefs.getString("draft_discount_amount", "") ?: ""
        )
    }

    private val _leadDraft = MutableStateFlow(loadDraftFromPrefs())
    val leadDraft: StateFlow<LeadFormDraft> = _leadDraft

    fun saveDraft(draft: LeadFormDraft) {
        prefs.edit().apply {
            putString("draft_number", draft.selectedNumber)
            putBoolean("draft_manual", draft.manualMode)
            putString("draft_name", draft.clientName)
            putString("draft_source", draft.source)
            putString("draft_product", draft.selectedProduct)
            putString("draft_status", draft.selectedStatus)
            putString("draft_sub_status", draft.selectedSubStatus)
            putString("draft_time_slot", draft.selectedTimeSlot)
            putString("draft_payment_status", draft.selectedPaymentStatus)
            putString("draft_remark", draft.remarkNotes)
            putString("draft_followup", draft.followUpDate)
            putString("draft_address", draft.shippingAddress)
            putString("draft_city", draft.shippingCity)
            putString("draft_state", draft.shippingState)
            putString("draft_pincode", draft.shippingPincode)
            putString("draft_payment_method", draft.paymentMethod)
            putString("draft_order_amount", draft.orderAmount)
            putString("draft_original_total", draft.originalTotalValue)
            putString("draft_discount_amount", draft.discountAmount)
        }.apply()
        _leadDraft.value = draft
    }

    fun clearDraft() {
        saveDraft(LeadFormDraft())
    }

    private val _saveToContactsPreference = MutableStateFlow(prefs.getBoolean("pref_save_contacts", true))
    val saveToContactsPreference: StateFlow<Boolean> = _saveToContactsPreference

    fun setSaveToContactsPreference(save: Boolean) {
        prefs.edit().putBoolean("pref_save_contacts", save).apply()
        _saveToContactsPreference.value = save
    }

    private val _leadsLimit = MutableStateFlow(50L)
    val leadsLimit: StateFlow<Long> = _leadsLimit

    fun loadMoreLeads() {
        _leadsLimit.value += 50L
    }

    fun initialize(userId: String, name: String, contactNumber: String, orgId: String, orgName: String) {
        if (_currentUserId.value == userId) return
        _currentUserId.value = userId
        callerName = name
        _telecallerContact.value = contactNumber
        _orgName.value = orgName
        repository.setOrgId(orgId)
        
        viewModelScope.launch {
            repository.seedProductsIfEmpty()
            repository.getProducts()
                .catch { e -> e.printStackTrace() }
                .collect { fetchedProducts ->
                    _products.value = fetchedProducts
                }
        }
        
        viewModelScope.launch {
            repository.getCategories()
                .catch { e -> e.printStackTrace() }
                .collect { fetchedCategories ->
                    _categories.value = fetchedCategories
                }
        }

        viewModelScope.launch {
            _isLoading.value = true
            
            _leadsLimit.flatMapLatest { limit ->
                repository.getLeadsForUser(userId, limit)
            }
            .catch { e -> 
                e.printStackTrace()
                _errorMessage.value = "Failed to load data: ${e.message}"
                _isLoading.value = false
            }
            .collect { newLeads ->
                _leads.value = newLeads
                _isLoading.value = false
                val pendingInvoiceId = prefs.getString("pending_invoice_lead_id", null)
                if (pendingInvoiceId != null && _pendingInvoiceLead.value == null) {
                    _pendingInvoiceLead.value = newLeads.find { it.id == pendingInvoiceId }
                }
            }
        }
        
        viewModelScope.launch {
            repository.getDashboardMetricsFlow(userId)
                .catch { e -> e.printStackTrace() }
                .collect { metrics ->
                    _dashboardMetrics.value = DashboardMetrics(
                        pendingPaymentsCount = metrics["pendingPayments"]?.toInt() ?: 0,
                        dueFollowupsCount = metrics["dueFollowups"]?.toInt() ?: 0,
                        freshLeadsCount = metrics["freshLeads"]?.toInt() ?: 0,
                        confirmedOrdersCount = metrics["confirmedOrders"]?.toInt() ?: 0,
                        inquiriesCount = metrics["inquiries"]?.toInt() ?: 0,
                        attemptedCount = metrics["attempted"]?.toInt() ?: 0,
                        rejectedCount = metrics["rejected"]?.toInt() ?: 0,
                        dispatchedCount = metrics["dispatched"]?.toInt() ?: 0,
                        rtoCount = metrics["rto"]?.toInt() ?: 0,
                        deliveredCount = metrics["delivered"]?.toInt() ?: 0,
                        retentionDueCount = 0
                    )
                }
        }
        
        // Listen to real-time sales metrics forever for this user
        viewModelScope.launch {
            repository.getSalesMetricsFlow(userId)
                .catch { e -> e.printStackTrace() }
                .collect { sales ->
                    _salesMetrics.value = SalesMetrics(
                        todayOrdersCount = sales["todayCount"]?.toInt() ?: 0,
                        todayRevenue = sales["todayRev"]?.toInt() ?: 0,
                        weeklyRevenue = sales["weekRev"]?.toInt() ?: 0,
                        activeOrdersCount = sales["totalActive"]?.toInt() ?: 0
                    )
                }
        }
    }

    fun saveDisposition(
        lead: Lead,
        status: String,
        notes: String,
        newInteractionNote: String,
        followUpDate: String?,
        product: String,
        address: String,
        city: String,
        state: String,
        pincode: String,
        paymentMethod: String,
        orderAmount: String,
        originalTotalValue: String,
        discountAmount: String,
        callDurationSeconds: Int,
        subStatus: String? = null,
        followUpTimeSlot: String? = null,
        paymentStatus: String? = null,
        isSuspiciousShortCall: Boolean = false,
        baseProductsBreakdown: String = "",
        onSuccess: (String, Lead) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val isoTimestamp = isoFormat.format(Date())

                val amtNum = orderAmount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                val isNewRev = isRevenue(status, paymentMethod, paymentStatus)
                val wasOldRev = isRevenue(lead.status, lead.paymentMethod, lead.paymentStatus)
                val finalConvertedAt = if (isNewRev) {
                    if (wasOldRev && lead.convertedAt != null) lead.convertedAt else isoTimestamp
                } else null

                val updateMap = mutableMapOf<String, Any?>(
                    "status" to status,
                    "notes" to notes,
                    "updatedAt" to isoTimestamp,
                    "convertedAt" to finalConvertedAt,
                    "followUpDate" to followUpDate,
                    "product" to product,
                    "address" to address,
                    "city" to city,
                    "state" to state,
                    "pincode" to pincode,
                    "paymentMethod" to paymentMethod,
                    "orderAmount" to orderAmount,
                    "orderAmountNum" to amtNum,
                    "originalTotalValue" to originalTotalValue,
                    "discountAmount" to discountAmount,
                    "subStatus" to subStatus,
                    "followUpTimeSlot" to followUpTimeSlot,
                    "paymentStatus" to paymentStatus,
                    "isSuspiciousShortCall" to isSuspiciousShortCall,
                    "baseProductsBreakdown" to baseProductsBreakdown
                )

                val logId = "i-" + UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = lead.id,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = lead.status,
                    statusAfter = status,
                    notes = newInteractionNote,
                    timestamp = isoTimestamp,
                    duration = callDurationSeconds,
                    followUpDate = followUpDate,
                    isVisitLog = false,
                    subStatus = subStatus,
                    followUpTimeSlot = followUpTimeSlot,
                    paymentStatus = paymentStatus,
                    isSuspiciousShortCall = isSuspiciousShortCall,
                    product = product,
                    address = address,
                    city = city,
                    pincode = pincode,
                    paymentMethod = paymentMethod,
                    orderAmount = orderAmount,
                    orderAmountNum = amtNum,
                    originalTotalValue = originalTotalValue,
                    discountAmount = discountAmount
                )
                repository.updateLeadAndAddInteractionBatch(lead.id, updateMap, interaction)
                
                var amtDelta = 0L
                var countDelta = 0
                if (isNewRev && !wasOldRev) {
                    amtDelta = amtNum
                    countDelta = 1
                } else if (!isNewRev && wasOldRev) {
                    amtDelta = -(lead.orderAmountNum)
                    countDelta = -1
                } else if (isNewRev && wasOldRev) {
                    amtDelta = amtNum - lead.orderAmountNum
                    countDelta = 0
                }
                updateSalesMetricsOptimistically(amtDelta, countDelta)
                
                updateMetricsOptimistically(
                    oldStatus = lead.status,
                    newStatus = status,
                    oldPaymentMethod = lead.paymentMethod,
                    oldPaymentStatus = lead.paymentStatus,
                    newPaymentMethod = paymentMethod,
                    newPaymentStatus = paymentStatus
                )
                onSuccess(logId, lead)
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun createReorder(
        parentLead: Lead,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val isoTimestamp = isoFormat.format(Date())

                val newLeadId = "l-" + UUID.randomUUID().toString()

                val amtNum = parentLead.orderAmount.toLongOrNull() ?: 0L
                val newLead = Lead(
                    id = newLeadId,
                    name = parentLead.name,
                    phone = parentLead.phone,
                    source = parentLead.source,
                    status = "Order Placed", 
                    notes = "📞 Reorder created from ${parentLead.id}",
                    assignedTo = parentLead.assignedTo,
                    address = parentLead.address,
                    city = parentLead.city,
                    pincode = parentLead.pincode,
                    parentLeadId = parentLead.id,
                    orderAmount = parentLead.orderAmount,
                    orderAmountNum = amtNum,
                    convertedAt = isoTimestamp,
                    paymentMethod = parentLead.paymentMethod,
                    paymentStatus = parentLead.paymentStatus,
                    isReorder = true
                )

                val logId = "i-" + UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = newLeadId,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = "New",
                    statusAfter = "Order Placed",
                    notes = "Reorder created",
                    timestamp = isoTimestamp,
                    duration = 0,
                    isVisitLog = false
                )

                val errorMsg = repository.createManualLeadBatch(newLead, interaction)
                if (errorMsg == null) {
                    updateMetricsOptimistically(oldStatus = null, newStatus = "Order Placed")
                    val isRev = isRevenue("Order Placed", parentLead.paymentMethod, parentLead.paymentStatus)
                    updateSalesMetricsOptimistically(if (isRev) amtNum else 0L, if (isRev) 1 else 0)
                    onSuccess()
                } else {
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun undoDisposition(
        leadId: String,
        previousStatus: String,
        previousNotes: String,
        previousFollowUpDate: String?,
        interactionId: String
    ) {
        viewModelScope.launch {
            if (repository.recalculateLeadStateAndBatch(leadId, interactionId)) {
                refreshMetricsBackground()
            }
        }
    }

    fun logAction(leadId: String, action: String, notes: String) {
        viewModelScope.launch {
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val isoTimestamp = isoFormat.format(Date())
                val logId = "act-" + UUID.randomUUID().toString().take(6)
                
                val interaction = Interaction(
                    id = logId,
                    leadId = leadId,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = "",
                    statusAfter = action,
                    notes = notes,
                    timestamp = isoTimestamp,
                    duration = 0,
                    followUpDate = null,
                    isVisitLog = false
                )
                repository.addInteraction(interaction)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun checkDuplicateLead(phone: String): Lead? {
        val sanitized = PhoneUtils.sanitizePhoneNumber(phone)
        // 1. Check in-memory list first to handle any old DB records that might contain spaces/dashes
        val localMatch = leads.value.find { PhoneUtils.sanitizePhoneNumber(it.phone) == sanitized }
        if (localMatch != null) return localMatch
        
        // 2. Fallback to network repository (handles leads not currently in memory)
        return repository.checkDuplicateLead(phone)
    }

    fun createManualLead(
        name: String,
        phone: String,
        source: String,
        status: String,
        subStatus: String = "",
        notes: String,
        followUpDate: String?,
        followUpTimeSlot: String = "",
        product: String,
        address: String,
        city: String,
        state: String,
        pincode: String,
        paymentMethod: String,
        orderAmount: String,
        originalTotalValue: String,
        discountAmount: String,
        paymentStatus: String = "",
        onSuccess: (String, Lead) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val isoTimestamp = isoFormat.format(Date())

                val newLeadId = "l-" + UUID.randomUUID().toString()
                
                val finalNotes = if (notes.trim().isNotEmpty()) "📞 ${notes.trim()}" else ""

                val amtNum = orderAmount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                val isNewRev = isRevenue(status, paymentMethod, paymentStatus)
                val finalConvertedAt = if (isNewRev) isoTimestamp else null

                val lead = Lead(
                    id = newLeadId,
                    name = name,
                    phone = phone,
                    source = source,
                    status = status,
                    subStatus = subStatus,
                    notes = finalNotes,
                    label = "Manual Inbound",
                    followUpDate = followUpDate,
                    followUpTimeSlot = followUpTimeSlot,
                    archived = false,
                    assignedTo = _currentUserId.value ?: "",
                    product = product,
                    address = address,
                    city = city,
                    state = state,
                    pincode = pincode,
                    paymentMethod = paymentMethod,
                    orderAmount = orderAmount,
                    orderAmountNum = amtNum,
                    originalTotalValue = originalTotalValue,
                    discountAmount = discountAmount,
                    paymentStatus = paymentStatus,
                    convertedAt = finalConvertedAt
                )

                val logId = "i-" + UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = newLeadId,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = "New",
                    statusAfter = status,
                    subStatus = subStatus,
                    notes = notes.trim(),
                    timestamp = isoTimestamp,
                    duration = 0, // Manual entry
                    followUpDate = followUpDate,
                    followUpTimeSlot = followUpTimeSlot,
                    isVisitLog = false
                )

                val errorMsg = repository.createManualLeadBatch(lead, interaction)
                if (errorMsg == null) {
                    updateMetricsOptimistically(
                        oldStatus = null,
                        newStatus = status,
                        newPaymentMethod = paymentMethod,
                        newPaymentStatus = paymentStatus
                    )
                    updateSalesMetricsOptimistically(if (isNewRev) amtNum else 0L, if (isNewRev) 1 else 0)
                    onSuccess(logId, lead)
                } else {
                    onError("DB Error: $errorMsg")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun updateLead(lead: Lead) {
        viewModelScope.launch {
            try {
                val amtNum = lead.orderAmount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                val isRev = isRevenue(lead.status, lead.paymentMethod, lead.paymentStatus)
                val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
                val finalConvertedAt = if (isRev) {
                    if (lead.convertedAt.isNullOrEmpty()) isoTimestamp else lead.convertedAt
                } else null

                val updateMap = mapOf(
                    "name" to lead.name,
                    "phone" to lead.phone,
                    "source" to lead.source,
                    "status" to lead.status,
                    "subStatus" to lead.subStatus,
                    "notes" to lead.notes,
                    "followUpDate" to lead.followUpDate,
                    "followUpTimeSlot" to lead.followUpTimeSlot,
                    "product" to lead.product,
                    "address" to lead.address,
                    "city" to lead.city,
                    "state" to lead.state,
                    "pincode" to lead.pincode,
                    "paymentMethod" to lead.paymentMethod,
                    "orderAmount" to lead.orderAmount,
                    "orderAmountNum" to amtNum,
                    "originalTotalValue" to lead.originalTotalValue,
                    "discountAmount" to lead.discountAmount,
                    "paymentStatus" to lead.paymentStatus,
                    "dispatchStatus" to lead.dispatchStatus,
                    "cancellationReason" to lead.cancellationReason,
                    "cancellationNotes" to lead.cancellationNotes,
                    "cancellationRequestedAt" to lead.cancellationRequestedAt,
                    "convertedAt" to finalConvertedAt
                )

                val diffNotes = buildString {
                    val oldLead = _leads.value.find { it.id == lead.id }
                    if (oldLead != null) {
                        if (oldLead.name != lead.name) append("Name: ${oldLead.name} -> ${lead.name}\n")
                        if (oldLead.phone != lead.phone) append("Phone: ${oldLead.phone} -> ${lead.phone}\n")
                        if (oldLead.address != lead.address) append("Address updated\n")
                        if (oldLead.product != lead.product) append("Product: ${oldLead.product} -> ${lead.product}\n")
                        if (oldLead.orderAmount != lead.orderAmount) append("Amount: ${oldLead.orderAmount} -> ${lead.orderAmount}\n")
                    }
                }.trim()

                val finalNote = if (diffNotes.isNotEmpty()) "📝 Details Updated:\n$diffNotes" else "📝 Lead Details Updated"

                val logId = "i-edit-" + UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = lead.id,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = lead.status,
                    statusAfter = lead.status,
                    notes = finalNote,
                    timestamp = isoTimestamp
                )
                repository.updateLeadAndAddInteractionBatch(lead.id, updateMap, interaction)
                refreshMetricsBackground()
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    fun requestOrderCancellation(
        lead: Lead,
        reason: String,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (lead.dispatchStatus == "Dispatched" || lead.status == "Dispatched") {
                    onError("Cannot cancel: Order has already been dispatched.")
                    return@launch
                }

                val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val updates = mapOf(
                    "status" to "Cancellation Pending",
                    "cancellationReason" to reason,
                    "cancellationNotes" to notes,
                    "cancellationRequestedAt" to isoTimestamp,
                    "convertedAt" to null,
                    "orderAmountNum" to 0L
                )

                val logId = "i-cancel-" + java.util.UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = lead.id,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = lead.status,
                    statusAfter = "Cancellation Pending",
                    notes = "Cancellation Requested: $reason | $notes",
                    timestamp = isoTimestamp
                )
                repository.updateLeadAndAddInteractionBatch(lead.id, updates, interaction)
                updateMetricsOptimistically(oldStatus = lead.status, newStatus = "Cancellation Pending")
                val isRev = isRevenue(lead.status, lead.paymentMethod, lead.paymentStatus)
                if (isRev) updateSalesMetricsOptimistically(-(lead.orderAmountNum), -1)
                onSuccess()
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    onError("Access Denied: This order was dispatched in the background.")
                } else {
                    onError(e.message ?: "Database Error")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Unknown Error")
            }
        }
    }

    fun withdrawCancellationRequest(
        lead: Lead,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (lead.dispatchStatus == "Dispatched" || lead.status == "Dispatched") {
                    onError("Action locked: Order has been dispatched.")
                    return@launch
                }

                val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                val amtNum = lead.orderAmount.toLongOrNull() ?: 0L
                val updates = mapOf(
                    "status" to "Order Placed",
                    "cancellationReason" to null,
                    "cancellationNotes" to null,
                    "cancellationRequestedAt" to null,
                    "convertedAt" to isoTimestamp,
                    "orderAmountNum" to amtNum
                )

                val logId = "i-withdraw-" + java.util.UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = lead.id,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = lead.status,
                    statusAfter = "Order Placed",
                    notes = "Cancellation Request Withdrawn by Telecaller",
                    timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date())
                )
                repository.updateLeadAndAddInteractionBatch(lead.id, updates, interaction)
                updateMetricsOptimistically(oldStatus = lead.status, newStatus = "Order Placed")
                val isRev = isRevenue("Order Placed", lead.paymentMethod, lead.paymentStatus)
                if (isRev) updateSalesMetricsOptimistically(amtNum, 1)
                onSuccess()
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    onError("Access Denied: This order was dispatched in the background.")
                } else {
                    onError(e.message ?: "Database Error")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to withdraw request")
            }
        }
    }

    fun cancelOrder(
        lead: Lead,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (lead.dispatchStatus == "Dispatched" || lead.status == "Dispatched") {
                    onError("Action locked: Order has been dispatched.")
                    return@launch
                }

                val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val updates = mapOf(
                    "status" to Constants.STATUS_ORDER_CANCELLED,
                    "cancellationReason" to reason,
                    "cancellationRequestedAt" to isoTimestamp,
                    "convertedAt" to null,
                    "orderAmountNum" to 0L
                )

                val logId = "i-cancel-direct-" + java.util.UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = lead.id,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = lead.status,
                    statusAfter = Constants.STATUS_ORDER_CANCELLED,
                    notes = "Order Cancelled Directly by Telecaller. Reason: $reason",
                    timestamp = isoTimestamp
                )
                repository.updateLeadAndAddInteractionBatch(lead.id, updates, interaction)
                updateMetricsOptimistically(oldStatus = lead.status, newStatus = Constants.STATUS_ORDER_CANCELLED)
                val isRev = isRevenue(lead.status, lead.paymentMethod, lead.paymentStatus)
                if (isRev) updateSalesMetricsOptimistically(-(lead.orderAmountNum), -1)
                onSuccess()
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    onError("Access Denied: This order was dispatched in the background.")
                } else {
                    onError(e.message ?: "Database Error")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to cancel order")
            }
        }
    }

    fun archiveLead(leadId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val lead = _leads.value.find { it.id == leadId }
                val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
                val updates = mapOf(
                    "archived" to true, 
                    "status" to "Invalid",
                    "convertedAt" to null,
                    "orderAmountNum" to 0L
                )
                
                val logId = "i-arch-" + UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = leadId,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = lead?.status ?: "Unknown",
                    statusAfter = "Invalid (Archived)",
                    notes = "🗑️ Lead Archived/Deleted by User",
                    timestamp = isoTimestamp
                )
                
                repository.updateLeadAndAddInteractionBatch(leadId, updates, interaction)
                if (lead != null) {
                    updateMetricsOptimistically(oldStatus = lead.status, newStatus = null)
                    val isRev = isRevenue(lead.status, lead.paymentMethod, lead.paymentStatus)
                    if (isRev) updateSalesMetricsOptimistically(-(lead.orderAmountNum), -1)
                } else {
                    refreshMetricsBackground()
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete lead")
            }
        }
    }


}
