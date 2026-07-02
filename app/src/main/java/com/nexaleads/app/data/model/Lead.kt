package com.nexaleads.app.data.model

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
    val orderAmount: String = ""
)

fun Lead.getPrimaryCategory(): String {
    if (this.archived) return "ARCHIVED"
    if (this.status == "Order Placed") return "CONVERTED"
    if (this.status == "Not Interested" || this.status == "Invalid") return "REJECTED"
    if (this.status == "Follow-up") return "FOLLOWUP"
    if (this.status == "Call Not Answered") return "ATTEMPTED"
    if (this.status == "Product Inquiry Only") return "INQUIRY"
    return "PENDING"
}
