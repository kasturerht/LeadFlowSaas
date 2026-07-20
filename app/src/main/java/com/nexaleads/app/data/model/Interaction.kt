package com.nexaleads.app.data.model

import androidx.annotation.Keep

@Keep
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
    val isVisitLog: Boolean = false,
    val subStatus: String? = null,
    val followUpTimeSlot: String? = null,
    val paymentStatus: String? = null,
    val isSuspiciousShortCall: Boolean = false,
    val product: String? = null,
    val address: String? = null,
    val city: String? = null,
    val pincode: String? = null,
    val paymentMethod: String? = null,
    val orderAmount: String? = null,
    val orderAmountNum: Long = 0L,
    val originalTotalValue: String? = null,
    val discountAmount: String? = null
)
