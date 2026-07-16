package com.nexaleads.app.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexaleads.app.data.models.Product
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumProductSelector(
    productsList: List<Product>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    var searchQuery by remember { mutableStateOf("") }
    
    // Parse existing selection using the utility in SmartGridSelectors.kt
    var qtyMap by remember(selectedOption) { 
        mutableStateOf(parseProductQuantities(selectedOption).toMutableMap())
    }

    val categories = listOf("All", "🔥 Combos", "💊 Supplements", "🧴 Cosmetics", "🌿 Powders", "🍪 Edibles")
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredProducts = remember(productsList, searchQuery, selectedCategory) {
        var list = productsList
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
        }
        if (selectedCategory != "All") {
            val filterTerm = when(selectedCategory) {
                "🔥 Combos" -> "combo"
                "💊 Supplements" -> "Health Supplements"
                "🧴 Cosmetics" -> "Personal Care"
                "🌿 Powders" -> "Powders & Extracts"
                "🍪 Edibles" -> "Edibles"
                else -> ""
            }
            list = if (selectedCategory == "🔥 Combos") {
                list.filter { it.isCombo || it.name.contains("combo", ignoreCase = true) }
            } else {
                list.filter { 
                    it.description.contains(filterTerm, ignoreCase = true) ||
                    when (selectedCategory) {
                        "💊 Supplements" -> it.name.contains("spirulina", true) && !it.name.contains("facepack", true) && !it.name.contains("cream", true) && !it.name.contains("cookies", true) || it.name.contains("seabuckthorn", true)
                        "🧴 Cosmetics" -> it.name.contains("oil", true) || it.name.contains("shampoo", true) || it.name.contains("facewash", true) || it.name.contains("soap", true) || it.name.contains("cream", true) || it.name.contains("facepack", true)
                        "🌿 Powders" -> it.name.contains("powder", true) || it.name.contains("extract", true)
                        "🍪 Edibles" -> it.name.contains("cookies", true)
                        else -> false
                    }
                }
            }
        }
        list.sortedBy { it.sortOrder }
    }

    val totalItems = qtyMap.values.sum()
    val totalPrice = productsList.sumOf { (qtyMap[it.name] ?: 0) * it.price }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Products",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = Color(0xFF1E293B),
                            letterSpacing = (-0.5).sp
                        )
                        if (totalItems > 0) {
                            TextButton(
                                onClick = { 
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    qtyMap = mutableMapOf()
                                }
                            ) {
                                Text("Clear", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search...", color = Color(0xFF94A3B8)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF1F5F9),
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Category Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProducts) { product ->
                        val qty = qtyMap[product.name] ?: 0
                        ProductCard(
                            product = product,
                            masterProducts = productsList,
                            quantity = qty,
                            onIncrement = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                val newMap = qtyMap.toMutableMap()
                                newMap[product.name] = qty + 1
                                qtyMap = newMap
                            },
                            onDecrement = {
                                if (qty > 0) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    val newMap = qtyMap.toMutableMap()
                                    if (qty == 1) newMap.remove(product.name) else newMap[product.name] = qty - 1
                                    qtyMap = newMap
                                }
                            }
                        )
                    }
                }

                // Sticky Bottom Cart
                Surface(
                    color = Color.White,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total ($totalItems items)",
                                fontSize = 14.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "₹${totalPrice.toInt()}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E293B)
                            )
                        }
                        
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val resultString = buildSelectedProductsString(qtyMap)
                                onSelect(resultString)
                                onDismiss()
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .widthIn(min = 160.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    masterProducts: List<Product>,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val isCombo = product.isCombo
    
    val bgBrush = if (isCombo) {
        Brush.linearGradient(colors = listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)))
    } else {
        Brush.linearGradient(colors = listOf(Color.White, Color.White))
    }
    
    val borderColor = if (isCombo) Color(0xFFFCD34D) else if (quantity > 0) Color(0xFF3B82F6) else Color(0xFFE2E8F0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgBrush)
            .border(width = if (isCombo || quantity > 0) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon / Emoji Box
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isCombo) Color(0xFFF59E0B).copy(alpha = 0.1f) else Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = product.emojiIcon.ifEmpty { "📦" }, fontSize = 24.sp)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            if (isCombo) {
                Text(
                    text = "🏆 COMBO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFD97706),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Text(
                text = product.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Text(
                text = "₹${product.price.toInt()}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCombo) Color(0xFFD97706) else Color(0xFF3B82F6),
                modifier = Modifier.padding(top = 2.dp)
            )
            
            if (isCombo && product.comboItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val breakdownText = product.comboItems.joinToString(", ") { ci ->
                    val baseProd = masterProducts.find { it.id == ci.productId }
                    "${ci.quantity}x ${baseProd?.name ?: "Unknown"}"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF9C3))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "📦 Includes: $breakdownText",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF854D0E)
                    )
                }
            }
        }
        
        // Stepper
        if (quantity == 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9))
                    .clickable { onIncrement() }
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .background(Color(0xFFEFF6FF))
            ) {
                IconButton(onClick = onDecrement, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Remove", tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                }
                Text(
                    text = quantity.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = onIncrement, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

fun buildSelectedProductsString(qtyMap: Map<String, Int>): String {
    val items = mutableListOf<String>()
    for ((name, qty) in qtyMap) {
        if (qty > 0) {
            items.add("$name (${qty}x)")
        }
    }
    return items.joinToString(", ")
}
