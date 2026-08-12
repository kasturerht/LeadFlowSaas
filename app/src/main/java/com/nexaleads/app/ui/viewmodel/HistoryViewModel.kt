package com.nexaleads.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.repository.LeadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: LeadRepository
) : ViewModel() {

    private val _interactions = MutableStateFlow<List<Interaction>>(emptyList())
    val interactions: StateFlow<List<Interaction>> = _interactions

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun fetchHistory(userId: String, orgId: String) {
        viewModelScope.launch {
            repository.setOrgId(orgId)
            _isLoading.value = true
            _error.value = null
            try {
                val interactionsList = repository.getRecentInteractions(userId)
                _interactions.value = interactionsList
                _isLoading.value = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e // Don't swallow cancellation
                _isLoading.value = false
                android.util.Log.e("HistoryViewModel", "Error fetching history: ${e.message}", e)
                if (e.message?.contains("INDEX_REQUIRED") == true || e.message?.contains("FAILED_PRECONDITION") == true) {
                    _error.value = "Database Index Building. Please wait a few minutes."
                } else {
                    _error.value = e.message
                }
            }
        }
    }

    fun revertInteraction(interaction: Interaction, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val success = repository.recalculateLeadStateAndBatch(interaction.leadId, interaction.id)
                if (success) {
                    // Flow will automatically update the UI, no need to manually filter here
                    // Toast will be shown by UI
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed: recalculateLeadStateAndBatch returned false", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
