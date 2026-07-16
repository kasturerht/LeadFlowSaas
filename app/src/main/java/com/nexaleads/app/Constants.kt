package com.nexaleads.app

object Constants {
    const val STATUS_CALL_NOT_ANSWERED = "Call Not Answered"
    const val STATUS_INQUIRY = "Product Inquiry Only"
    const val STATUS_FOLLOW_UP = "Follow-up"
    const val STATUS_ORDER_PLACED = "Order Placed"
    const val STATUS_NOT_INTERESTED = "Not Interested"
    const val STATUS_INVALID = "Invalid"
    const val STATUS_ORDER_CANCELLED = "Order Cancelled"
    const val STATUS_DISPATCHED = "Dispatched"
    const val STATUS_DELIVERED = "Delivered"
    const val STATUS_RTO = "RTO"
    const val STATUS_RETURNED = "Returned"

    val PROCESSED_STATUSES = listOf(
        STATUS_CALL_NOT_ANSWERED,
        STATUS_INQUIRY,
        STATUS_FOLLOW_UP,
        STATUS_ORDER_PLACED,
        STATUS_NOT_INTERESTED,
        STATUS_INVALID
    )

    val PRODUCTS = listOf(
        "Spirulina",
        "Sea Buckthorn",
        "Spirulina Face Pack",
        "Spirulina Cookies",
        "Multiple / Combos"
    )

    fun normalizeStatus(raw: String?): String {
        if (raw == null || raw.trim().isEmpty()) return "Pending"
        val trimmed = raw.trim()
        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        return when (lower) {
            "no answer", "busy", "busy / cut", STATUS_CALL_NOT_ANSWERED.lowercase(java.util.Locale.ROOT) -> STATUS_CALL_NOT_ANSWERED
            "warm lead", "warm / on hold", STATUS_INQUIRY.lowercase(java.util.Locale.ROOT) -> STATUS_INQUIRY
            "follow-up", "visit scheduled" -> STATUS_FOLLOW_UP
            "converted", "visited", "visited (actual)", STATUS_ORDER_PLACED.lowercase(java.util.Locale.ROOT) -> STATUS_ORDER_PLACED
            STATUS_NOT_INTERESTED.lowercase(java.util.Locale.ROOT) -> STATUS_NOT_INTERESTED
            "invalid no.", "invalid/wrong number", STATUS_INVALID.lowercase(java.util.Locale.ROOT) -> STATUS_INVALID
            "order cancelled", "cancelled" -> STATUS_ORDER_CANCELLED
            STATUS_DISPATCHED.lowercase(java.util.Locale.ROOT) -> STATUS_DISPATCHED
            STATUS_DELIVERED.lowercase(java.util.Locale.ROOT) -> STATUS_DELIVERED
            STATUS_RTO.lowercase(java.util.Locale.ROOT), "return", "returned" -> STATUS_RTO
            else -> trimmed
        }
    }
}
