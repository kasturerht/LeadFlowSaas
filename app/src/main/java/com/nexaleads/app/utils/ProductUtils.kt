package com.nexaleads.app.utils

import com.nexaleads.app.data.models.Product
import com.nexaleads.app.components.parseProductQuantities

object ProductUtils {

    fun calculateBaseProductsBreakdown(selectedString: String, masterProducts: List<Product>): String {
        if (selectedString.isBlank()) return ""
        
        val qtyMap = parseProductQuantities(selectedString)
        val baseProductQtyMap = mutableMapOf<String, Int>()
        
        for ((productName, selectedQty) in qtyMap) {
            val product = masterProducts.find { it.name.equals(productName, ignoreCase = true) }
            
            if (product != null) {
                if (product.isComboProduct() && product.bundledProducts.isNotEmpty()) {
                    for (comboItem in product.bundledProducts) {
                        val baseProduct = masterProducts.find { it.id == comboItem.productId }
                        if (baseProduct != null) {
                            val totalQty = comboItem.quantity * selectedQty
                            baseProductQtyMap[baseProduct.name] = (baseProductQtyMap[baseProduct.name] ?: 0) + totalQty
                        }
                    }
                } else {
                    baseProductQtyMap[product.name] = (baseProductQtyMap[product.name] ?: 0) + selectedQty
                }
            } else {
                // If product not found in master list, just add it as is
                baseProductQtyMap[productName] = (baseProductQtyMap[productName] ?: 0) + selectedQty
            }
        }
        
        return baseProductQtyMap.filterValues { it > 0 }
            .map { "${it.key} x ${it.value}" }
            .joinToString(", ")
    }
}
