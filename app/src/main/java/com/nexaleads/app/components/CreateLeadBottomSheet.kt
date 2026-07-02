package com.nexaleads.app.components

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.outlined.ContactPhone
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
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.utils.PhoneUtils

import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.CallingViewModel
import com.nexaleads.app.utils.CallLogEntry
import com.nexaleads.app.utils.ContactUtils
import com.nexaleads.app.utils.getRecentCallLogs
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import com.nexaleads.app.ui.viewmodel.LeadFormDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateLeadBottomSheet(
    viewModel: CallingViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onExistingLeadFound: (Lead) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val draft by viewModel.leadDraft.collectAsState()
    val productsList by viewModel.products.collectAsState()
    val pricesMap = remember(productsList) { productsList.associate { it.name to it.price } }

    var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var selectedNumber by remember { mutableStateOf(draft.selectedNumber) }
    var manualMode by remember { mutableStateOf(draft.manualMode) }

    // Form State
    var clientName by remember { mutableStateOf(draft.clientName) }
    var source by remember { mutableStateOf(draft.source) }
    var showSourcePopup by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf(draft.selectedProduct) }
    var showProductPopup by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf(draft.selectedStatus) }
    var selectedSubStatus by remember { mutableStateOf(draft.selectedSubStatus) }
    var selectedTimeSlot by remember { mutableStateOf(draft.selectedTimeSlot) }
    var selectedPaymentStatus by remember { mutableStateOf(draft.selectedPaymentStatus) }
    var remarkNotes by remember { mutableStateOf(draft.remarkNotes) }
    var followUpDate by remember { mutableStateOf(draft.followUpDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Order Dispatch Fields
    var shippingAddress by remember { mutableStateOf(draft.shippingAddress) }
    var shippingCity by remember { mutableStateOf(draft.shippingCity) }
    var shippingPincode by remember { mutableStateOf(draft.shippingPincode) }
    var paymentMethod by remember { mutableStateOf(draft.paymentMethod) }
    var orderAmount by remember { mutableStateOf(draft.orderAmount) }

    val calculatedTotal = remember(selectedProduct, pricesMap) { calculateTotalAmount(selectedProduct, pricesMap) }

    LaunchedEffect(calculatedTotal) {
        if (calculatedTotal > 0) {
            orderAmount = calculatedTotal.toInt().toString()
        }
    }

    LaunchedEffect(
        selectedNumber, manualMode, clientName, source, selectedProduct, 
        selectedStatus, selectedSubStatus, selectedTimeSlot, selectedPaymentStatus,
        remarkNotes, followUpDate, shippingAddress, shippingCity, shippingPincode,
        paymentMethod, orderAmount
    ) {
        viewModel.saveDraft(
            LeadFormDraft(
                selectedNumber = selectedNumber,
                manualMode = manualMode,
                clientName = clientName,
                source = source,
                selectedProduct = selectedProduct,
                selectedStatus = selectedStatus,
                selectedSubStatus = selectedSubStatus,
                selectedTimeSlot = selectedTimeSlot,
                selectedPaymentStatus = selectedPaymentStatus,
                remarkNotes = remarkNotes,
                followUpDate = followUpDate,
                shippingAddress = shippingAddress,
                shippingCity = shippingCity,
                shippingPincode = shippingPincode,
                paymentMethod = paymentMethod,
                orderAmount = orderAmount
            )
        )
    }

    val saveToContactsPref by viewModel.saveToContactsPreference.collectAsState()
    var isSaveToContactsToggleOn by remember(saveToContactsPref) { mutableStateOf(saveToContactsPref) }

    LaunchedEffect(isSaveToContactsToggleOn) {
        viewModel.setSaveToContactsPreference(isSaveToContactsToggleOn)
    }
    
    var isSaving by remember { mutableStateOf(false) }
    var isCheckingDuplicate by remember { mutableStateOf(false) }

    // WhatsApp Automation State
    var autoLaunchWhatsApp by remember { mutableStateOf(true) }
    var includeAddress by remember { mutableStateOf(true) }
    var includePaymentLink by remember { mutableStateOf(false) }
    var includeDispatchNote by remember { mutableStateOf(true) }
    var includeSupportPhone by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("English") }

    LaunchedEffect(paymentMethod, selectedPaymentStatus) {
        if (selectedPaymentStatus == "⏳ UPI Link Sent" || selectedPaymentStatus.contains("Link", ignoreCase = true)) {
            includePaymentLink = true
        } else if (paymentMethod.equals("COD", ignoreCase = true) || selectedPaymentStatus == "✅ Payment Verified") {
            includePaymentLink = false
        }
    }

    // Logic to save the lead and contact
    var pendingPhoneForContact by remember { mutableStateOf("") }
    var pendingNameForContact by remember { mutableStateOf("") }
    var pendingSubmitFn by remember { mutableStateOf<(() -> Unit)?>(null) }

    val sources = listOf("Facebook Ad", "Instagram Ad", "Direct Inbound", "WhatsApp", "Reference", "Other")

    val writeContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        coroutineScope.launch {
            if (isGranted) {
                ContactUtils.saveContactSilently(context, pendingNameForContact, pendingPhoneForContact)
            }
            pendingSubmitFn?.invoke()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            callLogs = getRecentCallLogs(context, limit = 100)
        } else {
            manualMode = true
        }
    }

    LaunchedEffect(Unit) {
        val permissionStatus = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        )
        if (permissionStatus == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            callLogs = getRecentCallLogs(context, limit = 100)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    val processNumberSelect: (String) -> Unit = { number ->
        isCheckingDuplicate = true
        selectedNumber = number
        coroutineScope.launch {
            val duplicate = viewModel.checkDuplicateLead(number)
            isCheckingDuplicate = false
            if (duplicate != null) {
                Toast.makeText(context, "Lead already exists! Opening...", Toast.LENGTH_SHORT).show()
                onDismiss()
                onExistingLeadFound(duplicate)
            }
        }
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
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextSecondary) }
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
        if (isCheckingDuplicate) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ModernViolet)
            }
        } else if (selectedNumber.isEmpty() && !manualMode) {
            // STEP 1: Call Log Picker
            LazyColumn(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Select Recent Caller", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (callLogs.isEmpty()) {
                    item {
                        Text("No recent calls found.", color = TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
                    }
                } else {
                    items(callLogs) { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { processNumberSelect(log.number) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(40.dp).background(SoftVioletBg, CircleShape), contentAlignment = Alignment.Center) {
                                    Text("📞", fontSize = 16.sp)
                                }
                                Column {
                                    Text(log.number, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                    Text(log.name ?: sdf.format(Date(log.timestamp)), fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                        HorizontalDivider(color = BorderSubtle)
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { manualMode = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enter Number Manually", color = ModernViolet, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // STEP 2: Create & Dispose Form
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(24.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("New Lead Registration", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 22.sp)
                        TextButton(
                            onClick = {
                                viewModel.clearDraft()
                                selectedNumber = ""
                                manualMode = false
                                clientName = ""
                                source = ""
                                selectedProduct = ""
                                selectedStatus = ""
                                selectedSubStatus = ""
                                selectedTimeSlot = ""
                                selectedPaymentStatus = ""
                                remarkNotes = ""
                                followUpDate = ""
                                shippingAddress = ""
                                shippingCity = ""
                                shippingPincode = ""
                                paymentMethod = ""
                                orderAmount = ""
                            }
                        ) {
                            Text("Clear", color = ModernViolet, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = selectedNumber,
                        onValueChange = { selectedNumber = it },
                        label = { Text("Phone Number", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ModernViolet,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        readOnly = !manualMode
                    )
                }

                item {
                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { clientName = it },
                        label = { Text("Client Name (Required)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ModernViolet,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { isSaveToContactsToggleOn = !isSaveToContactsToggleOn }) {
                                Icon(
                                    imageVector = if (isSaveToContactsToggleOn) Icons.Default.ContactPhone else Icons.Outlined.ContactPhone,
                                    contentDescription = "Save to Phonebook",
                                    tint = if (isSaveToContactsToggleOn) ModernViolet else BorderSubtle
                                )
                            }
                        }
                    )
                    if (isSaveToContactsToggleOn) {
                        Text("✓ Contact will be saved automatically", color = ModernViolet, fontSize = 10.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }
                }

                item {
                    // Side-by-side Trigger Chips for Source and Product
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SmartTriggerChip(
                            label = "Add Source",
                            selectedOption = source,
                            iconData = sourceIcons[source],
                            onClick = { showSourcePopup = true },
                            modifier = Modifier.weight(1f),
                            onClear = { source = "" }
                        )
                        
                        SmartTriggerChip(
                            label = "Add Product",
                            selectedOption = selectedProduct,
                            iconData = getIconForSelection(selectedProduct, productIcons),
                            emojiData = getEmojiForSelection(selectedProduct, productsList.associate { it.name to it.emojiIcon }),
                            onClick = { showProductPopup = true },
                            modifier = Modifier.weight(1f),
                            onClear = { selectedProduct = "" }
                        )
                    }
                    if (calculatedTotal > 0) {
                        Text(
                            text = "Total Order Value: ₹${calculatedTotal.toInt()}",
                            color = ModernViolet,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                }

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
                                                .clickable { 
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
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) Color(0xFFF43F5E) else SurfaceLight).border(1.dp, if (isSelected) Color(0xFFF43F5E) else BorderSubtle, RoundedCornerShape(12.dp)).clickable { selectedSubStatus = if (selectedSubStatus == sub) "" else sub }.padding(horizontal = 8.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                // Product dropdown replaced by animated side-by-side chip above

                if (selectedStatus == "Follow-up") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("FOLLOW-UP DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)).clickable { showDatePicker = true }.padding(14.dp)) {
                                Text(if (followUpDate.isEmpty()) "Select Date" else followUpDate, color = if (followUpDate.isEmpty()) TextSecondary else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("PREFERRED TIME SLOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("🌅 Morning", "☀️ Afternoon", "🌙 Evening").forEach { slot ->
                                    val isSelected = selectedTimeSlot == slot
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) StatusWarning else SurfaceLight).border(1.dp, if (isSelected) StatusWarning else BorderSubtle, RoundedCornerShape(12.dp)).clickable { selectedTimeSlot = if (selectedTimeSlot == slot) "" else slot }.padding(horizontal = 8.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(slot, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextPrimary, textAlign = TextAlign.Center, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedStatus == "Order Placed") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("DISPATCH DETAILS (REQUIRED)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ModernViolet, letterSpacing = 1.2.sp)
                            
                            OutlinedTextField(
                                value = shippingAddress, onValueChange = { shippingAddress = it },
                                label = { Text("Full Shipping Address") }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = shippingCity, onValueChange = { shippingCity = it },
                                    label = { Text("City") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = shippingPincode, onValueChange = { shippingPincode = it },
                                    label = { Text("Pincode") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                                )
                            }
                            OutlinedTextField(
                                value = orderAmount, onValueChange = { orderAmount = it },
                                label = { Text("Order Amount (₹)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                            )
                            Text("PAYMENT METHOD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("COD", "Prepaid").forEach { pm ->
                                    val isSelected = paymentMethod == pm
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) ModernViolet else SurfaceLight).border(1.dp, if (isSelected) ModernViolet else BorderSubtle, RoundedCornerShape(8.dp)).clickable { paymentMethod = if (paymentMethod == pm) "" else pm }.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) { Text(pm, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextSecondary) }
                                }
                            }
                            if (paymentMethod.equals("Prepaid", ignoreCase = true)) {
                                Text("PREPAID PAYMENT VERIFICATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusSuccess, letterSpacing = 1.2.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("⏳ UPI Link Sent", "✅ Payment Verified").forEach { pStatus ->
                                        val isSelected = selectedPaymentStatus == pStatus
                                        Box(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isSelected) StatusSuccess else SurfaceLight).border(1.dp, if (isSelected) StatusSuccess else BorderSubtle, RoundedCornerShape(12.dp)).clickable { selectedPaymentStatus = if (selectedPaymentStatus == pStatus) "" else pStatus }.padding(horizontal = 8.dp, vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(pStatus, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextPrimary, textAlign = TextAlign.Center, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SmartWhatsAppOrderCard(
                                customerName = clientName.trim(),
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

                item {
                    OutlinedTextField(
                        value = remarkNotes, onValueChange = { remarkNotes = it },
                        label = { Text("Conversation Notes (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ModernViolet, unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        minLines = 1, maxLines = 4
                    )
                }
                
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth().height(54.dp), 
                        enabled = !isSaving, 
                        colors = ButtonDefaults.buttonColors(containerColor = ModernViolet), 
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            if (isSaving) return@Button
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            
                            val rawPhone = selectedNumber.trim()
                            val sanitized = PhoneUtils.sanitizePhoneNumber(rawPhone)
                            val purePhone = if (sanitized.length >= 10) sanitized else rawPhone.replace(Regex("[^0-9+]"), "").trim()
                            
                            if (purePhone.length < 10) {
                                Toast.makeText(context, "Invalid Phone Number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (clientName.trim().isEmpty()) {
                                Toast.makeText(context, "Please enter client name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (source.isEmpty()) {
                                Toast.makeText(context, "Please select Lead Source", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedStatus.isEmpty()) {
                                Toast.makeText(context, "Please select Status", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if ((selectedStatus == "Order Placed" || selectedStatus == "Product Inquiry Only") && selectedProduct.isEmpty()) {
                                Toast.makeText(context, "Please select a Product", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedStatus == "Follow-up" && followUpDate.isEmpty()) {
                                Toast.makeText(context, "Please select a date", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            if (selectedStatus == "Order Placed") {
                                if (shippingAddress.isEmpty() || shippingCity.isEmpty() || shippingPincode.isEmpty() || paymentMethod.isEmpty() || orderAmount.isEmpty()) {
                                    Toast.makeText(context, "Please fill all dispatch details", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }

                            val submitFn = {
                                isSaving = true
                                
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

                                viewModel.createManualLead(
                                    name = clientName.trim(),
                                    phone = purePhone,
                                    source = source,
                                    status = selectedStatus,
                                    subStatus = finalSubStatus,
                                    notes = remarkNotes,
                                    followUpDate = finalFollowUpDate,
                                    followUpTimeSlot = finalTimeSlot,
                                    product = finalProduct,
                                    address = finalShippingAddress,
                                    city = finalShippingCity,
                                    pincode = finalShippingPincode,
                                    paymentMethod = finalPaymentMethod,
                                    orderAmount = finalOrderAmount,
                                    paymentStatus = finalPaymentStatus,
                                    onSuccess = {
                                        isSaving = false
                                        viewModel.clearDraft()
                                        Toast.makeText(context, "Lead saved successfully!", Toast.LENGTH_SHORT).show()
                                        if (selectedStatus == "Order Placed" && autoLaunchWhatsApp) {
                                            com.nexaleads.app.utils.WhatsAppSender.sendOrderConfirmation(
                                                context = context,
                                                phone = purePhone,
                                                customerName = clientName.trim(),
                                                products = selectedProduct,
                                                address = listOf(shippingAddress, shippingCity, shippingPincode).filter { it.isNotBlank() }.joinToString(", "),
                                                paymentMode = if (paymentMethod.equals("Prepaid", ignoreCase = true)) "$paymentMethod - $selectedPaymentStatus" else paymentMethod,
                                                includeAddress = includeAddress,
                                                includePaymentLink = includePaymentLink,
                                                includeDispatchNote = includeDispatchNote,
                                                includeSupportPhone = includeSupportPhone,
                                                language = selectedLanguage
                                            )
                                        }
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        isSaving = false
                                        Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            val processSaveLogic = {
                                if (isSaveToContactsToggleOn) {
                                    val permissionStatus = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_CONTACTS
                                    )
                                    if (permissionStatus == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        coroutineScope.launch {
                                            ContactUtils.saveContactSilently(context, clientName.trim(), purePhone)
                                            submitFn()
                                        }
                                    } else {
                                        pendingNameForContact = clientName.trim()
                                        pendingPhoneForContact = purePhone
                                        pendingSubmitFn = submitFn
                                        writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                                    }
                                } else {
                                    submitFn()
                                }
                            }

                            if (manualMode) {
                                isSaving = true
                                coroutineScope.launch {
                                    val dup = viewModel.checkDuplicateLead(purePhone)
                                    if (dup != null) {
                                        isSaving = false
                                        Toast.makeText(context, "Lead already exists! Opening...", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                        onExistingLeadFound(dup)
                                        return@launch
                                    } else {
                                        isSaving = false
                                        processSaveLogic()
                                    }
                                }
                            } else {
                                processSaveLogic()
                            }
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CleanWhite)
                        } else {
                            Text("Create & Save Lead", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CleanWhite)
                        }
                    }
                }
            }
        }
    }

    if (showSourcePopup) {
        SmartGridPopup(
            title = "Select Lead Source",
            options = sources,
            icons = sourceIcons,
            selectedOption = source,
            onSelect = { source = it; showSourcePopup = false },
            onDismiss = { showSourcePopup = false }
        )
    }

    if (showProductPopup) {
        val productNames = productsList.map { it.name }.ifEmpty { Constants.PRODUCTS }
        SmartGridPopup(
            title = "Select Product",
            options = productNames,
            icons = productIcons,
            emojis = productsList.associate { it.name to it.emojiIcon },
            prices = pricesMap,
            selectedOption = selectedProduct,
            isMultiSelect = true,
            onSelect = { selectedProduct = it },
            onDismiss = { showProductPopup = false }
        )
    }
}
