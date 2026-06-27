package com.enterprise.leadflow.data.model

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
    val visited: Boolean = false,
    val assignedTo: String = ""
)

fun Lead.getPrimaryCategory(): String {
    if (this.archived) return "ARCHIVED"
    if (this.status == "Converted") return "CONVERTED"
    if (this.status == "Not Interested" || this.status == "Invalid") return "REJECTED"
    if (this.status == "Visited" || this.visited) return "VISITED"
    if (this.status == "Visit Scheduled") return "VISIT_SCHEDULED"
    if (this.status == "Follow-up") return "FOLLOWUP"
    if (this.status == "No Answer" || this.status == "Busy" || this.status == "Warm Lead") return "ATTEMPTED"
    return "PENDING"
}
