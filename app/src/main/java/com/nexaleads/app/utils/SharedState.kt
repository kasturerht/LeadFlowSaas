package com.nexaleads.app.utils

import kotlinx.coroutines.flow.MutableStateFlow

object SharedState {
    var sharedWhatsAppNumber: String? = null
    var sharedWhatsAppName: String? = null
    
    // Trigger to let DashboardScreen know a new lead was shared
    val onNewSharedLead = MutableStateFlow(false)
    
    fun clear() {
        sharedWhatsAppNumber = null
        sharedWhatsAppName = null
        onNewSharedLead.value = false
    }
}
