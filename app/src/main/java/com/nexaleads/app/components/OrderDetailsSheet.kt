package com.nexaleads.app.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailsSheet(
    lead: Lead,
    onDismiss: () -> Unit,
    onSave: (Lead) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }

    // Form States
    var product by remember(lead) { mutableStateOf(lead.product) }
    var orderAmount by remember(lead) { mutableStateOf(lead.orderAmount) }
    var discountAmount by remember(lead) { mutableStateOf(lead.discountAmount) }
    var originalTotalValue by remember(lead) { mutableStateOf(lead.originalTotalValue) }
    var address by remember(lead) { mutableStateOf(lead.address) }
    var city by remember(lead) { mutableStateOf(lead.city) }
    var pincode by remember(lead) { mutableStateOf(lead.pincode) }
    var paymentMethod by remember(lead) { mutableStateOf(lead.paymentMethod) }
    var paymentStatus by remember(lead) { mutableStateOf(lead.paymentStatus ?: "") }
    var notes by remember(lead) { mutableStateOf(lead.notes) }
    var status by remember(lead) { mutableStateOf(lead.status) }

    val statusOptions = listOf(
        Constants.STATUS_ORDER_PLACED,
        Constants.STATUS_FOLLOW_UP,
        Constants.STATUS_NOT_INTERESTED,
        "Cancelled"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF16151A),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(40.dp).background(ModernViolet.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = ModernViolet, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text("Order Details", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(lead.name.ifBlank { "Unknown Customer" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            AnimatedContent(
                targetState = isEditMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { height -> height / 4 })
                        .togetherWith(fadeOut(animationSpec = tween(150)) + slideOutVertically(animationSpec = tween(150)) { height -> -height / 4 })
                },
                label = "edit_mode_transition"
            ) { editMode ->
                if (!editMode) {
                    // VIEW MODE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        // Digital Receipt Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = ModernViolet.copy(alpha = 0.5f), ambientColor = ModernViolet),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C24))
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("AMOUNT PAID", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("₹${lead.orderAmount.ifBlank { "0" }}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    if (lead.discountAmount.isNotBlank() && lead.discountAmount != "0") {
                                        Text("(-₹${lead.discountAmount})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusSuccess, modifier = Modifier.padding(bottom = 6.dp))
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                Divider(color = Color.White.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                ReceiptRow("Product", lead.product)
                                ReceiptRow("Original Value", if (lead.originalTotalValue.isNotBlank()) "₹${lead.originalTotalValue}" else "")
                                ReceiptRow("Converted On", formatConvertedAt(lead.convertedAt))
                                ReceiptRow("Status", lead.status)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Fulfillment Info
                        Text("FULFILLMENT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        InfoRow("Address", lead.address)
                        InfoRow("City", lead.city)
                        InfoRow("Pincode", lead.pincode)
                        InfoRow("Payment Method", lead.paymentMethod)
                        InfoRow("Payment Status", lead.paymentStatus ?: "")
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Notes
                        if (lead.notes.isNotBlank()) {
                            Text("NOTES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(16.dp)) {
                                Text(lead.notes, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        Spacer(modifier = Modifier.height(100.dp)) // padding for FAB
                    }
                } else {
                    // EDIT MODE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("EDIT ORDER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ModernViolet, letterSpacing = 1.sp)
                        
                        OutlinedTextField(
                            value = product,
                            onValueChange = { product = it },
                            label = { Text("Product / Plan") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = editTextFieldColors()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = orderAmount,
                                onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) orderAmount = it },
                                label = { Text("Final Amount (₹)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = editTextFieldColors()
                            )
                            OutlinedTextField(
                                value = discountAmount,
                                onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) discountAmount = it },
                                label = { Text("Discount (₹)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = editTextFieldColors()
                            )
                        }
                        
                        OutlinedTextField(
                            value = originalTotalValue,
                            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) originalTotalValue = it },
                            label = { Text("Original Value (₹)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = editTextFieldColors()
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = editTextFieldColors()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City") },
                                modifier = Modifier.weight(1f),
                                colors = editTextFieldColors()
                            )
                            OutlinedTextField(
                                value = pincode,
                                onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) pincode = it },
                                label = { Text("Pincode") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = editTextFieldColors()
                            )
                        }

                        Text("Status", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            statusOptions.take(2).forEach { option ->
                                val selected = status == option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) ModernViolet else Color.White.copy(alpha = 0.05f))
                                        .clickable { status = option }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(option, fontSize = 13.sp, color = if (selected) Color.White else Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            statusOptions.drop(2).forEach { option ->
                                val selected = status == option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) ModernViolet else Color.White.copy(alpha = 0.05f))
                                        .clickable { status = option }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(option, fontSize = 13.sp, color = if (selected) Color.White else Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            maxLines = 5,
                            colors = editTextFieldColors()
                        )
                        
                        Spacer(modifier = Modifier.height(100.dp)) // padding for FAB
                    }
                }
            }
        }
    }

    // Floating Buttons at Bottom
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (!isEditMode) {
            ExtendedFloatingActionButton(
                onClick = { isEditMode = true },
                modifier = Modifier.padding(bottom = 24.dp).height(56.dp).width(160.dp),
                containerColor = ModernViolet,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Order", fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16151A).copy(alpha = 0.9f))
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { isEditMode = false },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
                        val dateStr = dateFormat.format(Date())
                        
                        var updatedNotes = notes
                        if (lead.orderAmount != orderAmount || lead.product != product || lead.status != status) {
                            val auditLog = "\n[System - $dateStr]: Order updated. Amount: ${lead.orderAmount}->${orderAmount}, Product: ${lead.product}->${product}, Status: ${lead.status}->${status}"
                            updatedNotes = (notes + auditLog).trim()
                        }

                        val updatedLead = lead.copy(
                            product = product,
                            orderAmount = orderAmount,
                            discountAmount = discountAmount,
                            originalTotalValue = originalTotalValue,
                            address = address,
                            city = city,
                            pincode = pincode,
                            paymentMethod = paymentMethod,
                            paymentStatus = paymentStatus,
                            status = status,
                            notes = updatedNotes
                        )
                        onSave(updatedLead)
                        isEditMode = false
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ModernViolet)
                ) {
                    Text("Save & Sync", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

fun formatConvertedAt(convertedAt: String?): String {
    if (convertedAt == null) return "Unknown"
    return try {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = isoFormat.parse(convertedAt)
        val displayFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        date?.let { displayFormat.format(it) } ?: convertedAt
    } catch (e: Exception) {
        convertedAt
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun editTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ModernViolet,
    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
    cursorColor = ModernViolet,
    focusedLabelColor = ModernViolet,
    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.White.copy(alpha = 0.02f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
)
