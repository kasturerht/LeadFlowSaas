package com.nexaleads.app.components

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.utils.PhoneUtils

import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.CallingViewModel
import com.nexaleads.app.utils.CallLogEntry
import com.nexaleads.app.utils.ContactUtils
import com.nexaleads.app.utils.getRecentCallLogs
import kotlinx.coroutines.launch
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

    var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var selectedNumber by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf(false) }

    // Form State
    var clientName by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("") }
    var remarkNotes by remember { mutableStateOf("") }
    var followUpDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isVisitToggleOn by remember { mutableStateOf(false) }
    var isSaveToContactsToggleOn by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isCheckingDuplicate by remember { mutableStateOf(false) }

    // Logic to save the lead and contact
    var pendingPhoneForContact by remember { mutableStateOf("") }
    var pendingNameForContact by remember { mutableStateOf("") }
    var pendingSubmitFn by remember { mutableStateOf<(() -> Unit)?>(null) }

    val sources = listOf("Facebook Ad", "Direct Inbound", "Walk-in", "WhatsApp", "Reference", "Other")

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
        val datePickerState = rememberDatePickerState()
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
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(24.dp)
            ) {
                item {
                    Text("New Lead Registration", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 22.sp)
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
                        singleLine = true
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LEAD SOURCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            sources.forEach { src ->
                                val isSelected = source == src
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) ModernViolet else SurfaceLight)
                                        .border(1.dp, if (isSelected) ModernViolet else BorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { source = src }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(src, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextSecondary)
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("STATUS DISPOSITION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Constants.PROCESSED_STATUSES.forEach { option ->
                                val isSelected = selectedStatus == option
                                val statusColor = statusColors[option] ?: ModernViolet
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) statusColor else SurfaceLight)
                                        .border(1.dp, if (isSelected) statusColor else BorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { 
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            selectedStatus = option
                                            if (option == "Visited") isVisitToggleOn = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(indianStatusLabels[option]?.substringAfter(" ") ?: option, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextSecondary)
                                }
                            }
                        }
                    }
                }

                if (selectedStatus == "Follow-up" || selectedStatus == "Visit Scheduled" || selectedStatus == "Visited") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (selectedStatus == "Follow-up") "CALLBACK DATE" else "NEXT DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)).clickable { showDatePicker = true }.padding(14.dp)) {
                                Text(if (followUpDate.isEmpty()) "Select Date" else followUpDate, color = if (followUpDate.isEmpty()) TextSecondary else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CONVERSATION NOTES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                        OutlinedTextField(
                            value = remarkNotes, onValueChange = { remarkNotes = it },
                            placeholder = { Text("Enter detailed notes here...", fontSize = 13.sp, color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AccentSurface, unfocusedContainerColor = AccentSurface,
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(110.dp)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceLight)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Save Client to Phonebook", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Text("Auto-saves the name & number so you know when they call back.", color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                        Switch(
                            checked = isSaveToContactsToggleOn,
                            onCheckedChange = { isSaveToContactsToggleOn = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CleanWhite, checkedTrackColor = ModernViolet)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceLight)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Client visited office today?", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Text("Turn this on if they came but rejected/converted instantly.", color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                        Switch(
                            checked = isVisitToggleOn,
                            onCheckedChange = { isVisitToggleOn = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CleanWhite, checkedTrackColor = ModernViolet)
                        )
                    }
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
                                Toast.makeText(context, "Please select Status Disposition", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if ((selectedStatus == "Follow-up" || selectedStatus == "Visit Scheduled" || selectedStatus == "Visited") && followUpDate.isEmpty()) {
                                Toast.makeText(context, "Please select a date", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val submitFn = {
                                isSaving = true
                                viewModel.createManualLead(
                                    name = clientName.trim(),
                                    phone = purePhone,
                                    source = source,
                                    status = selectedStatus,
                                    notes = remarkNotes,
                                    followUpDate = if ((selectedStatus == "Follow-up" || selectedStatus == "Visit Scheduled" || selectedStatus == "Visited") && followUpDate.isNotEmpty()) followUpDate else null,
                                    isVisitLog = isVisitToggleOn,
                                    onSuccess = {
                                        isSaving = false
                                        Toast.makeText(context, "Lead saved and logged!", Toast.LENGTH_SHORT).show()
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
}
