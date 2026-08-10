package com.nexaleads.app.data.model

import androidx.annotation.Keep

@Keep
data class WhatsAppTemplate(
    val id: String = "",
    val statusTrigger: String = "",
    val language: String = "",
    val templateText: String = "",
    val isActive: Boolean = false,
    val updatedAt: String = ""
)
