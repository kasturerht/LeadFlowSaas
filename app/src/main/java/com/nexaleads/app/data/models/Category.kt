package com.nexaleads.app.data.models

import androidx.annotation.Keep

@Keep
data class Category(
    val id: String = "",
    val name: String = "",
    val color: String = "#ffffff",
    val icon: String = "📦",
    val isActive: Boolean = true,
    val order: Int = 0
)
