package com.enterprise.leadflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.leadflow.data.model.Interaction
import com.enterprise.leadflow.data.repository.LeadRepository
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

    fun fetchHistory(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _interactions.value = repository.getRecentInteractions(userId)
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun revertInteraction(interaction: Interaction) {
        viewModelScope.launch {
            try {
                val success = repository.recalculateLeadStateAndBatch(interaction.leadId, interaction.id)
                if (success) {
                    _interactions.value = _interactions.value.filter { it.id != interaction.id }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
