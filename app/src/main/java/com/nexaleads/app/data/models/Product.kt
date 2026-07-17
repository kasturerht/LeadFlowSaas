package com.nexaleads.app.data.models

data class BundledProduct(
    val productId: String = "",
    val quantity: Int = 1
)

data class Product(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0, // Legacy fallback
    val mrp: Double = 0.0,
    val offerPrice: Double = 0.0,
    val bottomPrice: Double = 0.0,
    val shippingFee: Double = 50.0, // Default 50 as requested
    val description: String = "",
    val emojiIcon: String = "📦",
    val sortOrder: Int = 1,
    val isActive: Boolean = true,
    val type: String = "single", // 'single' or 'combo'
    val bundledProducts: List<BundledProduct> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val consumptionDays: Int = 30 // Retention Engine: Days the product lasts
) {
    fun isComboProduct(): Boolean = type == "combo"
    
    // Helper methods for dynamic pricing (handles legacy auto-update logic)
    fun getEffectiveOfferPrice(): Double = if (offerPrice > 0) offerPrice else price
    fun getEffectiveBottomPrice(): Double = if (bottomPrice > 0) bottomPrice else price
    fun getEffectiveMrp(): Double = if (mrp > 0) mrp else (price * 1.2)
}
