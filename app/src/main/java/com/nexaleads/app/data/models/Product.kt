package com.nexaleads.app.data.models

data class ComboItem(
    val productId: String = "",
    val quantity: Int = 1
)

data class Product(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val emojiIcon: String = "📦",
    val sortOrder: Int = 1,
    val isActive: Boolean = true,
    val isCombo: Boolean = false,
    val comboItems: List<ComboItem> = emptyList()
)
