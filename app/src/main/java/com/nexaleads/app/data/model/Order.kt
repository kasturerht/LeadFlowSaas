package com.nexaleads.app.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class Order(
    val id: String = "",
    val customerId: String = "",
    val customerPhone: String = "",
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
    val deliveredAt: Any? = null,
    val cancellationReason: String? = null,
    val cancellationNotes: String? = null,
    val cancellationRequestedAt: Any? = null,
    val rtoCount: Int = 0,
    val createdAt: Any? = null, // Can be ISO string or Firebase Timestamp
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isReorder: Boolean = false
)

fun Order.getCreatedAtString(): String {
    val ca = this.createdAt
    if (ca is String) return ca
    if (ca is com.google.firebase.Timestamp) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(ca.toDate())
    }
    return ""
}
