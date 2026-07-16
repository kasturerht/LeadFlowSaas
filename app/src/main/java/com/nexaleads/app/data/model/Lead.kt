package com.nexaleads.app.data.model

import com.nexaleads.app.Constants

data class Lead(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val source: String = "",
    val status: String = "",
    val notes: String = "",
    val label: String = "",
    val followUpDate: String? = null,
    val archived: Boolean = false,
    val assignedTo: String = "",
    val product: String = "",
    val address: String = "",
    val city: String = "",
    val pincode: String = "",
    val paymentMethod: String = "",
    val orderAmount: String = "",
    val subStatus: String? = null,
    val followUpTimeSlot: String? = null,
    val paymentStatus: String? = null,
    val isSuspiciousShortCall: Boolean = false,
    val state: String = "",
    val originalTotalValue: String = "",
    val discountAmount: String = "",
    val convertedAt: String? = null,
    val dispatchStatus: String? = null,
    val cancellationReason: String? = null,
    val cancellationNotes: String? = null,
    val cancellationRequestedAt: String? = null,
    val courierPartner: String? = null,
    val trackingNumber: String? = null,
    val rtoCount: Int = 0,
    val baseProductsBreakdown: String = "",
    val events: List<LeadEvent> = emptyList()
)

data class LeadEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String = "",
    val timestamp: String = "",
    val title: String = "",
    val description: String = ""
)

fun Lead.getPrimaryCategory(): String {
    if (this.archived) return "ARCHIVED"
    val normStatus = Constants.normalizeStatus(this.status)
    if (normStatus == Constants.STATUS_RTO) return "RTO"
    if (normStatus == Constants.STATUS_DELIVERED) return "DELIVERED"
    if (normStatus == Constants.STATUS_DISPATCHED) return "DISPATCHED"
    if (normStatus == Constants.STATUS_ORDER_PLACED) return "CONVERTED"
    if (normStatus == Constants.STATUS_NOT_INTERESTED || normStatus == Constants.STATUS_INVALID || normStatus == Constants.STATUS_ORDER_CANCELLED) return "REJECTED"
    if (normStatus == Constants.STATUS_FOLLOW_UP) return "FOLLOWUP"
    if (normStatus == Constants.STATUS_CALL_NOT_ANSWERED) return "ATTEMPTED"
    if (normStatus == Constants.STATUS_INQUIRY) return "INQUIRY"
    return "PENDING"
}
