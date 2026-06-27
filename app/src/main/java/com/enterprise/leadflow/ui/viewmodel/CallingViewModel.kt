package com.enterprise.leadflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.leadflow.data.model.Interaction
import com.enterprise.leadflow.data.model.Lead
import com.enterprise.leadflow.data.repository.LeadRepository
import com.enterprise.leadflow.utils.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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

@HiltViewModel
class CallingViewModel @Inject constructor(
    private val repository: LeadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    private var callerName: String = "Agent"

    private val _leads = MutableStateFlow<List<Lead>>(emptyList())
    val leads: StateFlow<List<Lead>> = _leads

    private val _pendingMediaLead = MutableStateFlow<Lead?>(null)
    val pendingMediaLead: StateFlow<Lead?> = _pendingMediaLead

    fun setPendingMediaLead(lead: Lead?) {
        _pendingMediaLead.value = lead
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("leadflow_prefs", Context.MODE_PRIVATE)

    private val _pendingCallLeadId = MutableStateFlow<String?>(prefs.getString("pending_call_lead_id", null))
    val pendingCallLeadId: StateFlow<String?> = _pendingCallLeadId

    private val _pendingCallTimestamp = MutableStateFlow<Long?>(
        if (prefs.contains("pending_call_timestamp")) prefs.getLong("pending_call_timestamp", 0L) else null
    )
    val pendingCallTimestamp: StateFlow<Long?> = _pendingCallTimestamp

    init {
        // Auto-clear ghost calls (older than 2 hours)
        val currentTimestamp = _pendingCallTimestamp.value
        if (currentTimestamp != null && currentTimestamp > 0L) {
            val twoHoursInMillis = 2 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - currentTimestamp > twoHoursInMillis) {
                clearPendingCall()
            }
        }
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

    fun initialize(userId: String, name: String) {
        if (_currentUserId.value == userId) return
        _currentUserId.value = userId
        callerName = name
        viewModelScope.launch {
            repository.getLeadsForUser(userId)
                .catch { /* Handle error */ }
                .collect { newLeads ->
                    _leads.value = newLeads
                }
        }
    }

    fun saveDisposition(
        lead: Lead,
        status: String,
        notes: String,
        newInteractionNote: String,
        followUpDate: String?,
        isVisitLog: Boolean,
        callDurationSeconds: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val isoTimestamp = isoFormat.format(Date())

                val updateMap = mutableMapOf<String, Any?>(
                    "status" to status,
                    "notes" to notes,
                    "updatedAt" to isoTimestamp,
                    "followUpDate" to followUpDate
                )

                if (isVisitLog || lead.visited) {
                    updateMap["visited"] = true
                }

                repository.updateLead(lead.id, updateMap)

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
                    isVisitLog = isVisitLog
                )
                repository.addInteraction(interaction)
                onSuccess(logId)
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
                repository.recalculateLeadStateAndBatch(leadId, interactionId)
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
        notes: String,
        followUpDate: String?,
        isVisitLog: Boolean,
        onSuccess: (String) -> Unit,
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

                val lead = Lead(
                    id = newLeadId,
                    name = name,
                    phone = phone,
                    source = source,
                    status = status,
                    notes = finalNotes,
                    label = "Manual Inbound",
                    followUpDate = followUpDate,
                    archived = false,
                    visited = isVisitLog,
                    assignedTo = _currentUserId.value ?: ""
                )

                val logId = "i-" + UUID.randomUUID().toString().take(6)
                val interaction = Interaction(
                    id = logId,
                    leadId = newLeadId,
                    callerId = _currentUserId.value ?: "",
                    callerName = callerName,
                    statusBefore = "New",
                    statusAfter = status,
                    notes = notes.trim(),
                    timestamp = isoTimestamp,
                    duration = 0, // Manual entry
                    followUpDate = followUpDate,
                    isVisitLog = isVisitLog
                )

                val success = repository.createManualLeadBatch(lead, interaction)
                if (success) {
                    onSuccess(newLeadId)
                } else {
                    onError("Failed to save to database")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun archiveLead(leadId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateLead(leadId, mapOf("archived" to true, "status" to "Invalid"))
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete lead")
            }
        }
    }
}
