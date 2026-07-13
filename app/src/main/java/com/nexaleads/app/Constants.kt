package com.nexaleads.app

object Constants {
    const val STATUS_CALL_NOT_ANSWERED = "Call Not Answered"
    const val STATUS_INQUIRY = "Product Inquiry Only"
    const val STATUS_FOLLOW_UP = "Follow-up"
    const val STATUS_ORDER_PLACED = "Order Placed"
    const val STATUS_NOT_INTERESTED = "Not Interested"
    const val STATUS_INVALID = "Invalid"
    const val STATUS_ORDER_CANCELLED = "Order Cancelled"

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
        return when {
            trimmed.equals("No Answer", ignoreCase = true) ||
            trimmed.equals("Busy", ignoreCase = true) ||
            trimmed.equals("Busy / Cut", ignoreCase = true) ||
            trimmed.equals(STATUS_CALL_NOT_ANSWERED, ignoreCase = true) -> STATUS_CALL_NOT_ANSWERED

            trimmed.equals("Warm Lead", ignoreCase = true) ||
            trimmed.equals("Warm / On Hold", ignoreCase = true) ||
            trimmed.equals(STATUS_INQUIRY, ignoreCase = true) -> STATUS_INQUIRY

            trimmed.equals("Follow-up", ignoreCase = true) ||
            trimmed.equals("Visit Scheduled", ignoreCase = true) -> STATUS_FOLLOW_UP

            trimmed.equals("Converted", ignoreCase = true) ||
            trimmed.equals("Visited", ignoreCase = true) ||
            trimmed.equals("Visited (Actual)", ignoreCase = true) ||
            trimmed.equals(STATUS_ORDER_PLACED, ignoreCase = true) -> STATUS_ORDER_PLACED

            trimmed.equals(STATUS_NOT_INTERESTED, ignoreCase = true) -> STATUS_NOT_INTERESTED

            trimmed.equals("Invalid No.", ignoreCase = true) ||
            trimmed.equals("Invalid/Wrong Number", ignoreCase = true) ||
            trimmed.equals(STATUS_INVALID, ignoreCase = true) -> STATUS_INVALID
            
            trimmed.equals("Order Cancelled", ignoreCase = true) ||
            trimmed.equals("Cancelled", ignoreCase = true) -> STATUS_ORDER_CANCELLED

            else -> trimmed
        }
    }
}
