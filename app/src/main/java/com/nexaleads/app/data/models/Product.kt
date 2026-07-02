package com.nexaleads.app.data.models

data class Product(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val emojiIcon: String = "📦",
    val sortOrder: Int = 1,
    val isActive: Boolean = true
)
