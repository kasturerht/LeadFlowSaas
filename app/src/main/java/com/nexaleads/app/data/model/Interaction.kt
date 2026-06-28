package com.nexaleads.app.data.model

data class Interaction(
    val id: String = "",
    val leadId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val statusBefore: String = "",
    val statusAfter: String = "",
    val notes: String = "",
    val timestamp: String = "",
    val duration: Int = 0,
    val followUpDate: String? = null,
    val isVisitLog: Boolean = false
)
