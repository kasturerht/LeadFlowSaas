package com.nexaleads.app.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class Order(
    val id: String = "",
    val customerId: String = "",
    val assignedTo: String = "",
    val product: String = "",
    val baseProductsBreakdown: String = "",
    val originalTotalValue: String = "",
    val discountAmount: String = "",
    val orderAmount: String = "",
    val orderAmountNum: Long = 0L,
    val paymentMethod: String = "",
    val paymentStatus: String? = null,
    val status: String = "Order Placed", // e.g., Order Placed, Dispatched, Delivered, RTO, Cancelled
    val subStatus: String? = null,
    val courierPartner: String? = null,
    val trackingNumber: String? = null,
    val dispatchStatus: String? = null,
    val deliveredAt: String? = null,
    val cancellationReason: String? = null,
    val cancellationNotes: String? = null,
    val cancellationRequestedAt: String? = null,
    val rtoCount: Int = 0,
    val createdAt: String = "", // ISO string
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isReorder: Boolean = false
)
