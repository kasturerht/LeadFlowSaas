package com.nexaleads.app.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.getPrimaryCategory
import com.nexaleads.app.getCallDurationFromSystemLog
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.CallingViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DispositionBottomSheet(
    lead: Lead,
    viewModel: CallingViewModel,
    sheetState: SheetState,
    snackbarHostState: SnackbarHostState,
    callStartTimestamp: Long?,
    onDismiss: () -> Unit,
    onSaveSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val mainScope = rememberCoroutineScope()
    val productsList by viewModel.products.collectAsState()
    val pricesMap = remember(productsList) { productsList.associate { it.name to it.getEffectiveOfferPrice() } }
    val bottomPricesMap = remember(productsList) { productsList.associate { it.name to it.getEffectiveBottomPrice() } }
    val shippingFeesMap = remember(productsList) { productsList.associate { it.name to it.shippingFee } }

    var selectedProduct by remember { mutableStateOf(lead.product) }
    var showProductPopup by remember { mutableStateOf(false) }
    var userModifiedProducts by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf("") }
    var remarkNotes by remember { mutableStateOf("") }
    var followUpDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var attemptedSaveWithoutNotes by remember { mutableStateOf(false) }

    // Part 3: Behavioral & UX tracking state
    var selectedSubStatus by remember { mutableStateOf(lead.subStatus ?: "") }
    var selectedTimeSlot by remember { mutableStateOf(lead.followUpTimeSlot ?: "") }
    var selectedPaymentStatus by remember { mutableStateOf(lead.paymentStatus ?: "") }
    var showShortCallWarningDialog by remember { mutableStateOf(false) }
    var hasConfirmedShortCall by remember { mutableStateOf(false) }
    var calculatedDuration by remember { mutableStateOf(0) }

    // Order Dispatch Fields
    var shippingAddress by remember { mutableStateOf(lead.address) }
    var shippingCity by remember { mutableStateOf(lead.city) }
    var shippingPincode by remember { mutableStateOf(lead.pincode) }
    var paymentMethod by remember { mutableStateOf(lead.paymentMethod) }
    var orderAmount by remember { mutableStateOf(lead.orderAmount) }

    // WhatsApp Automation State
    var autoLaunchWhatsApp by remember { mutableStateOf(true) }
    var includeAddress by remember { mutableStateOf(true) }
    var includePaymentLink by remember { mutableStateOf(false) }
    var includeDispatchNote by remember { mutableStateOf(true) }
    var includeSupportPhone by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("Marathi") }
    val telecallerContact by viewModel.telecallerContact.collectAsState()

    var originalTotalValue by remember { mutableStateOf(lead.originalTotalValue ?: "") }
    var discountAmount by remember { mutableStateOf(lead.discountAmount ?: "") }

    val calculatedTotal = remember(selectedProduct, pricesMap) { calculateTotalAmount(selectedProduct, pricesMap) }
    val calculatedBottomTotal = remember(selectedProduct, bottomPricesMap) { calculateTotalBottomPrice(selectedProduct, bottomPricesMap) }
    val calculatedShipping = remember(selectedProduct, shippingFeesMap) { calculateTotalShippingFee(selectedProduct, shippingFeesMap) }

    val isConverted = remember(lead.status, lead.dispatchStatus) {
        lead.status.equals("Order Placed", ignoreCase = true) || 
        lead.status.equals("Converted", ignoreCase = true) || 
        lead.getPrimaryCategory() == "CONVERTED"
    }
    val isDispatched = remember(lead.status, lead.dispatchStatus) {
        lead.status.equals("Dispatched", ignoreCase = true) || 
        (lead.dispatchStatus != null && lead.dispatchStatus.equals("Dispatched", ignoreCase = true))
    }
    val isCancelled = remember(lead.status) {
        lead.status.equals("Order Cancelled", ignoreCase = true) || 
        lead.status.equals(Constants.STATUS_ORDER_CANCELLED, ignoreCase = true)
    }
    val isDelivered = remember(lead.status) {
        lead.status.equals("Delivered", ignoreCase = true)
    }
    val isLocked = isDispatched || isCancelled || isDelivered

    var showCancellationReasonDialog by remember { mutableStateOf(false) }

    LaunchedEffect(productsList) {
        if (productsList.isNotEmpty() && selectedProduct.isNotEmpty()) {
            val sanitized = sanitizeSelectedProducts(selectedProduct, productsList.map { it.name })
            if (sanitized != selectedProduct) {
                selectedProduct = sanitized
            }
        }
    }

    LaunchedEffect(calculatedTotal) {
        val newTotalStr = calculatedTotal.toInt().toString()
        if (userModifiedProducts) {
            orderAmount = newTotalStr
            originalTotalValue = newTotalStr
            discountAmount = "0"
            userModifiedProducts = false
        } else if (orderAmount.isEmpty() || orderAmount == "0") {
             orderAmount = newTotalStr
             originalTotalValue = newTotalStr
             discountAmount = "0"
        }
    }

    LaunchedEffect(paymentMethod, selectedPaymentStatus) {
        if (selectedPaymentStatus == "Link Sent" || selectedPaymentStatus.contains("Link", ignoreCase = true)) {
            includePaymentLink = true
        } else if (paymentMethod.equals("COD", ignoreCase = true) || selectedPaymentStatus == "Paid") {
            includePaymentLink = false
        }
    }

    val customTagsPrefKey = "custom_tags_fmcg"
    val sharedPrefs = context.getSharedPreferences("LeadFlowPrefs", Context.MODE_PRIVATE)
    var userCustomTags by remember { mutableStateOf(sharedPrefs.getStringSet(customTagsPrefKey, setOf())?.toList() ?: emptyList()) }
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var newCustomTagText by remember { mutableStateOf("") }

    val executeSave: (Int) -> Unit = { durationSeconds ->
        isSaving = true
        val currentLead = lead
        val previousStatus = currentLead.status
        val previousNotes = currentLead.notes
        
        val finalNotes = if (remarkNotes.trim().isNotEmpty()) {
            if (previousNotes.isEmpty()) {
                remarkNotes.trim()
            } else {
                "$previousNotes\n\n📞 ${remarkNotes.trim()}"
            }
        } else {
            previousNotes
        }

        val previousFollowUpDate = currentLead.followUpDate
        val leadIdToRevert = currentLead.id
        val isHighIntent = selectedStatus == Constants.STATUS_ORDER_PLACED || selectedStatus == "Order Placed" || selectedStatus == Constants.STATUS_INQUIRY || selectedStatus == "Product Inquiry Only"
        // Data Sanitization based on final status
        val isProductRelevant = selectedStatus in listOf(Constants.STATUS_INQUIRY, "Product Inquiry Only", "Product Inquiry", Constants.STATUS_ORDER_PLACED, "Order Placed", Constants.STATUS_FOLLOW_UP, "Follow-up")
        val isFollowUpRelevant = selectedStatus in listOf(Constants.STATUS_FOLLOW_UP, "Follow-up")
        val isOrderRelevant = selectedStatus in listOf(Constants.STATUS_ORDER_PLACED, "Order Placed")
        val isSubStatusRelevant = selectedStatus in listOf(Constants.STATUS_CALL_NOT_ANSWERED, "No Answer", "Busy")

        val finalSubStatus = if (isSubStatusRelevant) selectedSubStatus else ""
        val finalProduct = if (isProductRelevant) selectedProduct else ""
        val finalFollowUpDate = if (isFollowUpRelevant && followUpDate.isNotEmpty()) followUpDate else null
        val finalTimeSlot = if (isFollowUpRelevant) selectedTimeSlot else ""
        val finalShippingAddress = if (isOrderRelevant) shippingAddress else ""
        val finalShippingCity = if (isOrderRelevant) shippingCity else ""
        val finalShippingPincode = if (isOrderRelevant) shippingPincode else ""
        val finalPaymentMethod = if (isOrderRelevant) paymentMethod else ""
        val finalPaymentStatus = if (isOrderRelevant) selectedPaymentStatus else ""
        val finalOrderAmount = if (isOrderRelevant) orderAmount else ""
        val finalOriginalTotal = if (isOrderRelevant) originalTotalValue else ""
        val finalDiscountAmount = if (isOrderRelevant) discountAmount else ""
        
        viewModel.saveDisposition(
            lead = currentLead,
            status = selectedStatus,
            notes = finalNotes,
            newInteractionNote = remarkNotes.trim(),
            followUpDate = finalFollowUpDate,
            product = finalProduct,
            address = finalShippingAddress,
            city = finalShippingCity,
            state = "",
            pincode = finalShippingPincode,
            paymentMethod = finalPaymentMethod,
            orderAmount = finalOrderAmount,
            originalTotalValue = finalOriginalTotal,
            discountAmount = finalDiscountAmount,
            callDurationSeconds = durationSeconds,
            subStatus = finalSubStatus,
            followUpTimeSlot = finalTimeSlot,
            paymentStatus = finalPaymentStatus,
            isSuspiciousShortCall = (durationSeconds < 5 && isHighIntent),
            baseProductsBreakdown = com.nexaleads.app.utils.ProductUtils.calculateBaseProductsBreakdown(finalProduct, productsList),
            onSuccess = { interactionId, updatedLead ->
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    if ((selectedStatus == "Order Placed" || selectedStatus == Constants.STATUS_ORDER_PLACED) && autoLaunchWhatsApp) {
                        
                        if (finalPaymentStatus.contains("Verified", ignoreCase = true)) {
                            viewModel.setPendingInvoice(updatedLead)
                        }

                        com.nexaleads.app.utils.WhatsAppSender.sendOrderConfirmation(
                            context = context,
                            phone = currentLead.phone,
                            customerName = currentLead.name,
                            products = selectedProduct,
                            address = listOf(shippingAddress, shippingCity, shippingPincode).filter { it.isNotBlank() }.joinToString(", "),
                            paymentMode = if (paymentMethod.equals("Prepaid", ignoreCase = true)) "$paymentMethod - $selectedPaymentStatus" else paymentMethod,
                            originalTotal = finalOriginalTotal,
                            discountAmount = finalDiscountAmount,
                            includeAddress = includeAddress,
                            includePaymentLink = includePaymentLink,
                            includeDispatchNote = includeDispatchNote,
                            includeSupportPhone = includeSupportPhone,
                            supportNumber = telecallerContact,
                            language = selectedLanguage
                        )
                    }
                    onSaveSuccess(selectedStatus)
                    isSaving = false
                    
                    mainScope.launch {
                        val result = snackbarHostState.showSnackbar(message = "Lead saved.", actionLabel = "UNDO", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDisposition(leadIdToRevert, previousStatus, previousNotes, previousFollowUpDate, interactionId)
                            Toast.makeText(context, "Undo successful. Lead reverted.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onError = { error ->
                isSaving = false
                Toast.makeText(context, "Failed: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showShortCallWarningDialog) {
        AlertDialog(
            onDismissRequest = { showShortCallWarningDialog = false },
            title = { Text("⚠️ Short Call Detected", color = StatusDanger, fontWeight = FontWeight.Bold) },
            text = { Text("The recorded call duration is under 5 seconds (${calculatedDuration}s). Are you sure a real order/inquiry occurred? This disposition will be flagged for Admin review.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShortCallWarningDialog = false
                        hasConfirmedShortCall = true
                        executeSave(calculatedDuration)
                    }
                ) {
                    Text("Yes, Confirm", color = StatusDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortCallWarningDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceLight
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= System.currentTimeMillis() - 86400000L
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        followUpDate = sdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = ModernViolet, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = SurfaceLight)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = ModernViolet,
                    headlineContentColor = TextPrimary,
                    selectedDayContainerColor = ModernViolet,
                    selectedDayContentColor = CleanWhite,
                    todayDateBorderColor = ModernViolet,
                    todayContentColor = ModernViolet
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLight,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                Column {
                    Text(lead.name.ifEmpty { "Unknown Name" }, fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 22.sp)
                }
            }
            
            if (isCancelled) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("❌ ORDER CANCELLED", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This order was cancelled. Reason: ${lead.cancellationReason.orEmpty().ifEmpty { "Not specified" }}",
                                color = Color(0xFF991B1B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (isDispatched) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔒 ORDER DISPATCHED", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This parcel is packed and shipped. Modifying, deleting, or requesting cancellation is disabled.",
                                color = Color(0xFF991B1B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            // SECTION: STATUS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("STATUS DISPOSITION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                    val chunkedStatuses = Constants.PROCESSED_STATUSES.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        chunkedStatuses.forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { option ->
                                    val isSelected = selectedStatus == option
                                    val iconData = statusIcons[option]
                                    val labelText = indianStatusLabels[option] ?: option
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) (iconData?.tint ?: ModernViolet).copy(alpha = 0.08f) else SurfaceLight)
                                            .border(1.dp, if (isSelected) (iconData?.tint ?: ModernViolet) else BorderSubtle, RoundedCornerShape(16.dp))
                                            .clickable(enabled = !isLocked) { 
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                val newStatus = if (selectedStatus == option) "" else option
                                                selectedStatus = newStatus
                                                if (newStatus == Constants.STATUS_INQUIRY || newStatus == "Product Inquiry Only" || newStatus == "Product Inquiry" || newStatus == Constants.STATUS_ORDER_PLACED || newStatus == "Order Placed" || newStatus == Constants.STATUS_FOLLOW_UP || newStatus == "Follow-up") {
                                                    if (selectedProduct.isEmpty()) {
                                                        showProductPopup = true
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (iconData != null) {
                                                Icon(
                                                    imageVector = iconData.icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) iconData.tint else TextSecondary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Text(labelText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) TextPrimary else TextSecondary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                repeat(2 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            // SECTION: SUB-STATUS (Conditional for Call Not Answered)
            if (selectedStatus == Constants.STATUS_CALL_NOT_ANSWERED || selectedStatus == "No Answer" || selectedStatus == "Busy") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CALL OUTCOME REASON", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E), letterSpacing = 1.2.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("🔔 Ringing", "🔴 Busy", "📵 Switched Off").forEach { sub ->
                                val isSelected = selectedSubStatus == sub
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) Color(0xFFF43F5E) else SurfaceLight).border(1.dp, if (isSelected) Color(0xFFF43F5E) else BorderSubtle, RoundedCornerShape(12.dp)).clickable(enabled = !isLocked) { selectedSubStatus = if (selectedSubStatus == sub) "" else sub }.padding(horizontal = 8.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: PRODUCT INTERESTED IN (Conditional)
            if (selectedStatus == "Order Placed" || selectedStatus == Constants.STATUS_ORDER_PLACED || selectedStatus == "Product Inquiry Only" || selectedStatus == Constants.STATUS_INQUIRY || selectedStatus == "Follow-up" || selectedStatus == Constants.STATUS_FOLLOW_UP) {
                item {
                    SmartTriggerChip(
                        label = "Add Product",
                        selectedOption = selectedProduct,
                        iconData = getIconForSelection(selectedProduct, productIcons),
                        emojiData = getEmojiForSelection(selectedProduct, productsList.associate { it.name to it.emojiIcon }),
                        onClick = { if (!isLocked) showProductPopup = true },
                        modifier = Modifier.fillMaxWidth(0.5f),
                        onClear = { if (!isLocked) selectedProduct = "" }
                    )
                    if (calculatedTotal > 0) {
                        Text(
                            text = "Total Value: ₹${calculatedTotal.toInt()}",
                            color = ModernViolet,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                        if (calculatedBottomTotal > 0) {
                            Text(
                                text = "Min Limit: ₹${calculatedBottomTotal.toInt()}",
                                color = Color(0xFFD97706),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }

            // SECTION: DATE & TIME SLOT
            if (selectedStatus == "Follow-up" || selectedStatus == Constants.STATUS_FOLLOW_UP) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("FOLLOW-UP DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)).clickable(enabled = !isLocked) { showDatePicker = true }.padding(14.dp)) {
                            Text(if (followUpDate.isEmpty()) "Select Date" else followUpDate, color = if (followUpDate.isEmpty()) TextSecondary else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Text("PREFERRED TIME SLOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("🌅 Morning", "☀️ Afternoon", "🌙 Evening").forEach { slot ->
                                val isSelected = selectedTimeSlot == slot
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) StatusWarning else SurfaceLight).border(1.dp, if (isSelected) StatusWarning else BorderSubtle, RoundedCornerShape(12.dp)).clickable(enabled = !isLocked) { selectedTimeSlot = if (selectedTimeSlot == slot) "" else slot }.padding(horizontal = 8.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(slot, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextPrimary, textAlign = TextAlign.Center, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: ORDER DETAILS
            if (selectedStatus == "Order Placed" || selectedStatus == Constants.STATUS_ORDER_PLACED) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("DISPATCH DETAILS (REQUIRED)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ModernViolet, letterSpacing = 1.2.sp)
                        
                        OutlinedTextField(
                            value = shippingAddress, onValueChange = { shippingAddress = it },
                            label = { Text("Full Shipping Address") }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp), readOnly = isLocked
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = shippingCity, onValueChange = { shippingCity = it },
                                label = { Text("City") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                readOnly = isLocked
                            )
                            OutlinedTextField(
                                value = shippingPincode, onValueChange = { shippingPincode = it },
                                label = { Text("Pincode") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                readOnly = isLocked
                            )
                        }

                        val origVal = originalTotalValue.toIntOrNull() ?: 0
                        val finalVal = orderAmount.toIntOrNull() ?: 0
                        val diff = origVal - finalVal

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("💰 FINAL DEAL VALUE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = orderAmount,
                                onValueChange = { 
                                    orderAmount = it
                                    val fVal = it.toIntOrNull() ?: 0
                                    discountAmount = (origVal - fVal).coerceAtLeast(0).toString()
                                },
                                label = { Text("Final Price (₹)") }, 
                                modifier = Modifier.fillMaxWidth(), 
                                shape = RoundedCornerShape(12.dp),
                                readOnly = isLocked,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            // Shipping details UI
                            if (calculatedShipping > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFF6FF)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🚚 Shipping Fee (Total): ", fontSize = 12.sp, color = Color(0xFF1E3A8A), fontWeight = FontWeight.SemiBold)
                                    Text("+₹${calculatedShipping.toInt()}", fontSize = 13.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("Add to final amount", fontSize = 10.sp, color = Color(0xFF3B82F6))
                                }
                            } else if (calculatedTotal > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFECFDF5)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🚚 Free Shipping", fontSize = 13.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                                }
                            }

                            if (diff > 0) {
                                val discountPercent = if (origVal > 0) (diff * 100) / origVal else 0
                                if (discountPercent > 50) {
                                    Text("⚠️ Discount is over 50%. Please verify this deal.", color = Color(0xFFEAB308), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                                } else {
                                    Text("🎉 Customer Saves: ₹$diff", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                                }
                            } else if (diff < 0) {
                                Text("📈 Custom Upsell", color = Color(0xFF3B82F6), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                            }
                            
                            if (finalVal > 0 && calculatedBottomTotal > 0 && finalVal < calculatedBottomTotal) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFEF2F2)).border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(8.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🚫 ERROR: Final amount cannot be less than Bottom Price (₹${calculatedBottomTotal.toInt()})",
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Text("PAYMENT METHOD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("COD", "Prepaid").forEach { pm ->
                                val isSelected = paymentMethod == pm
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) ModernViolet else SurfaceLight).border(1.dp, if (isSelected) ModernViolet else BorderSubtle, RoundedCornerShape(8.dp)).clickable(enabled = !isLocked) { paymentMethod = if (paymentMethod == pm) "" else pm }.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) { Text(pm, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextSecondary) }
                            }
                        }
                        if (paymentMethod.equals("Prepaid", ignoreCase = true)) {
                            Text("PREPAID PAYMENT VERIFICATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusSuccess, letterSpacing = 1.2.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Link Sent", "Paid").forEach { pStatus ->
                                    val isSelected = selectedPaymentStatus == pStatus
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) StatusSuccess else SurfaceLight).border(1.dp, if (isSelected) StatusSuccess else BorderSubtle, RoundedCornerShape(12.dp)).clickable(enabled = !isLocked) { selectedPaymentStatus = if (selectedPaymentStatus == pStatus) "" else pStatus }.padding(horizontal = 8.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(pStatus, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextPrimary, textAlign = TextAlign.Center, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SmartWhatsAppOrderCard(
                            customerName = lead.name,
                            products = selectedProduct,
                            address = listOf(shippingAddress, shippingCity, shippingPincode).filter { it.isNotBlank() }.joinToString(", "),
                            paymentMode = if (paymentMethod.equals("Prepaid", ignoreCase = true)) "$paymentMethod - $selectedPaymentStatus" else paymentMethod,
                            autoLaunch = autoLaunchWhatsApp,
                            onAutoLaunchChange = { autoLaunchWhatsApp = it },
                            includeAddress = includeAddress,
                            onIncludeAddressChange = { includeAddress = it },
                            includePaymentLink = includePaymentLink,
                            onIncludePaymentLinkChange = { includePaymentLink = it },
                            includeDispatchNote = includeDispatchNote,
                            onIncludeDispatchNoteChange = { includeDispatchNote = it },
                            includeSupportPhone = includeSupportPhone,
                            onIncludeSupportPhoneChange = { includeSupportPhone = it },
                            selectedLanguage = selectedLanguage,
                            onLanguageChange = { selectedLanguage = it }
                        )
                    }
                }
            }

            // SECTION: CONVERSATION NOTES
            item {
                OutlinedTextField(
                    value = remarkNotes, onValueChange = { remarkNotes = it },
                    label = { Text("Conversation Notes (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ModernViolet, unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    readOnly = isLocked,
                    minLines = 1, maxLines = 4
                )
            }

            // SECTION: AI PREDICTIVE CHIPS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✨ QUICK RESPONSE TAGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ModernViolet, letterSpacing = 1.2.sp)
                    val quickTags = listOf(
                        "🎯 Interested", "💰 Price Too High", "📦 Order Confirmed", 
                        "⏳ Will order next month", "❌ Not interested", "👨‍⚕️ Asking for doctor advice",
                        "📱 Sent details on WhatsApp", "📞 Did not pick up", "🗓️ Call later"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        quickTags.forEach { tag ->
                            Box(modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceLight)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                .clickable(enabled = !isLocked) { remarkNotes = if (remarkNotes.isEmpty()) tag else "$remarkNotes | $tag" }
                                .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(tag, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                        
                        userCustomTags.forEach { tag ->
                            Box(modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ModernViolet.copy(alpha=0.04f))
                                .border(1.dp, ModernViolet.copy(alpha=0.2f), RoundedCornerShape(6.dp))
                                .clickable(enabled = !isLocked) { remarkNotes = if (remarkNotes.isEmpty()) tag else "$remarkNotes | $tag" }
                                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tag, fontSize = 12.sp, color = ModernViolet, fontWeight = FontWeight.Medium)
                                    Text("✕", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Black, modifier = Modifier.clickable(enabled = !isLocked) {
                                        val updatedList = userCustomTags.toMutableList().apply { remove(tag) }
                                        userCustomTags = updatedList
                                        sharedPrefs.edit().putStringSet(customTagsPrefKey, updatedList.toSet()).apply()
                                    }.padding(2.dp))
                                }
                            }
                        }
                        
                        Box(modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, ModernViolet, RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLocked) { newCustomTagText = ""; showCustomTagDialog = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("+ Add Note", fontSize = 12.sp, color = ModernViolet, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth().height(54.dp), enabled = !isSaving && !isLocked, colors = ButtonDefaults.buttonColors(containerColor = ModernViolet), shape = RoundedCornerShape(16.dp),
                    onClick = {
                        if (isSaving) return@Button
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val currentLead = lead

                        if (selectedStatus.isEmpty()) {
                            Toast.makeText(context, "Please select a Status Disposition", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if ((selectedStatus == "Order Placed" || selectedStatus == "Product Inquiry Only") && selectedProduct.isEmpty()) {
                            Toast.makeText(context, "Please select a Product", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (selectedStatus == "Follow-up" && followUpDate.trim().isEmpty()) {
                            Toast.makeText(context, "Please select a date for Follow-up", Toast.LENGTH_SHORT).show()
                            attemptedSaveWithoutNotes = true
                            return@Button
                        }
                        
                        if (selectedStatus == "Order Placed") {
                            if (shippingAddress.isEmpty() || shippingCity.isEmpty() || shippingPincode.isEmpty() || paymentMethod.isEmpty() || orderAmount.isEmpty()) {
                                Toast.makeText(context, "Please fill all dispatch details", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val enteredAmt = orderAmount.toIntOrNull() ?: 0
                            if (calculatedBottomTotal > 0 && enteredAmt < calculatedBottomTotal) {
                                Toast.makeText(context, "Amount cannot be below limit (₹${calculatedBottomTotal.toInt()})", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        if (remarkNotes.trim().isEmpty() && !attemptedSaveWithoutNotes) {
                            attemptedSaveWithoutNotes = true
                            Toast.makeText(context, "Please enter notes or click Save again to skip", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val now = System.currentTimeMillis()
                        val durationSeconds = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                            val sysDuration = getCallDurationFromSystemLog(context, currentLead.phone)
                            if (sysDuration >= 0) sysDuration else if (callStartTimestamp != null && callStartTimestamp > 0L) ((now - callStartTimestamp) / 1000).coerceIn(1, 3600).toInt() else 30
                        } else {
                            if (callStartTimestamp != null && callStartTimestamp > 0L) ((now - callStartTimestamp) / 1000).coerceIn(1, 3600).toInt() else 30
                        }
                        calculatedDuration = durationSeconds

                        val isHighIntent = selectedStatus == Constants.STATUS_ORDER_PLACED || selectedStatus == "Order Placed" || selectedStatus == Constants.STATUS_INQUIRY || selectedStatus == "Product Inquiry Only"
                        if (durationSeconds < 5 && isHighIntent && !hasConfirmedShortCall) {
                            showShortCallWarningDialog = true
                            return@Button
                        }
                        
                        executeSave(durationSeconds)
                    }
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CleanWhite)
                    } else {
                        Text("Save & Sync", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CleanWhite)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLocked) {
                    if (isDelivered) {
                        Button(
                            onClick = {
                                if (isSaving) return@Button
                                isSaving = true
                                viewModel.createReorder(
                                    parentLead = lead,
                                    onSuccess = {
                                        isSaving = false
                                        Toast.makeText(context, "Reorder Created successfully!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        isSaving = false
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔄 Create Reorder", color = CleanWhite, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Do not show any cancellation or delete button, warning is already at the top
                    }
                } else if (isConverted) {
                    Button(
                        onClick = { showCancellationReasonDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("❌ Cancel Order Directly", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            viewModel.archiveLead(
                                leadId = lead.id,
                                onSuccess = {
                                    isSaving = false
                                    Toast.makeText(context, "Lead Deleted", Toast.LENGTH_SHORT).show()
                                    onSaveSuccess("Deleted")
                                },
                                onError = { err ->
                                    isSaving = false
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Lead entirely", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            viewModel.archiveLead(
                                leadId = lead.id,
                                onSuccess = {
                                    isSaving = false
                                    Toast.makeText(context, "Lead Deleted", Toast.LENGTH_SHORT).show()
                                    onSaveSuccess("Deleted")
                                },
                                onError = { err ->
                                    isSaving = false
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Lead", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCustomTagDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTagDialog = false },
            title = { Text("Add Personal Note", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newCustomTagText,
                    onValueChange = { newCustomTagText = it },
                    placeholder = { Text("e.g. Call after 5 PM") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModernViolet, focusedLabelColor = ModernViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            containerColor = SurfaceLight,
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCustomTagText.trim()
                        if (trimmed.isNotEmpty() && !userCustomTags.contains(trimmed)) {
                            val updatedList = userCustomTags + trimmed
                            userCustomTags = updatedList
                            sharedPrefs.edit().putStringSet(customTagsPrefKey, updatedList.toSet()).apply()
                        }
                        showCustomTagDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ModernViolet),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", color = CleanWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTagDialog = false }) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
    
    if (showProductPopup) {
        PremiumProductSelector(
            productsList = productsList,
            categoriesList = viewModel.categories.collectAsState().value,
            selectedOption = selectedProduct,
            onSelect = {
                selectedProduct = it
                userModifiedProducts = true
            },
            onDismiss = { showProductPopup = false }
        )
    }

    if (showCancellationReasonDialog) {
        var localReason by remember { mutableStateOf("") }
        var localNotes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCancellationReasonDialog = false },
            title = { Text("Cancel Order", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select a reason for cancellation:", fontSize = 13.sp, color = TextSecondary)
                    
                    val reasons = listOf(
                        "Client Changed Mind",
                        "Double Entry / Error",
                        "Payment Failed",
                        "Alternative Found",
                        "Other"
                    )
                    
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        reasons.forEach { r ->
                            val isSel = localReason == r
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) ModernViolet.copy(alpha = 0.1f) else SurfaceLight)
                                    .border(1.dp, if (isSel) ModernViolet else BorderSubtle, RoundedCornerShape(8.dp))
                                    .clickable { localReason = r }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(r, fontSize = 12.sp, color = if (isSel) ModernViolet else TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = localNotes,
                        onValueChange = { localNotes = it },
                        label = { Text("Additional Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModernViolet, unfocusedBorderColor = BorderSubtle),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (localReason.isEmpty()) {
                            Toast.makeText(context, "Please select a reason", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showCancellationReasonDialog = false
                        viewModel.cancelOrder(
                            lead = lead,
                            reason = if (localNotes.isNotEmpty()) "$localReason: $localNotes" else localReason,
                            onSuccess = {
                                Toast.makeText(context, "Order Cancelled.", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ModernViolet),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel Order", color = CleanWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancellationReasonDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceLight
        )
    }
}
