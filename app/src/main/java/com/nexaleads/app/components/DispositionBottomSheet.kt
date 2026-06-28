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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.getCallDurationFromSystemLog
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.CallingViewModel
import kotlinx.coroutines.launch

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

    var selectedStatus by remember { mutableStateOf("") }
    var remarkNotes by remember { mutableStateOf("") }
    var followUpDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isVisitToggleOn by remember { mutableStateOf(false) }
    var attemptedSaveWithoutNotes by remember { mutableStateOf(false) }

    val customTagsPrefKey = "custom_tags"
    val sharedPrefs = context.getSharedPreferences("LeadFlowPrefs", Context.MODE_PRIVATE)
    var userCustomTags by remember { mutableStateOf(sharedPrefs.getStringSet(customTagsPrefKey, setOf())?.toList() ?: emptyList()) }
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var newCustomTagText by remember { mutableStateOf("") }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        followUpDate = sdf.format(java.util.Date(millis))
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
            
            // SECTION: STATUS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("STATUS DISPOSITION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                    val options = Constants.PROCESSED_STATUSES
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        options.forEach { option ->
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
                                        if (option == "Visited") {
                                            isVisitToggleOn = true
                                        }
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

            // SECTION: DATE
            if (selectedStatus == "Follow-up" || selectedStatus == "Visit Scheduled" || selectedStatus == "Visited") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (selectedStatus == "Follow-up") "CALLBACK DATE" else "NEXT DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)).clickable { showDatePicker = true }.padding(14.dp)) {
                            Text(if (followUpDate.isEmpty()) "Select Date" else followUpDate, color = if (followUpDate.isEmpty()) TextSecondary else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // SECTION: CONVERSATION NOTES
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

            // SECTION: INSTANT VISIT TOGGLE
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

            // SECTION: AI PREDICTIVE CHIPS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✨ AI PREDICTIVE CHIPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ModernViolet, letterSpacing = 1.2.sp)
                    val quickTags = listOf(
                        "🎯 USA / Canada", "🎯 UK / Europe", "🎯 Australia / NZ",
                        "🎓 12th / UG Completed", "📊 Low Percentage", "📝 IELTS / PTE Pending",
                        "💰 Needs Education Loan", "💸 Looking for Scholarship",
                        "👨‍👩‍👦 Parent Answered", "📱 WhatsApp Details Sent"
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
                                .clickable { remarkNotes = if (remarkNotes.isEmpty()) tag else "$remarkNotes | $tag" }
                                .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(tag, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                        
                        userCustomTags.forEach { tag ->
                            Box(modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ModernViolet.copy(alpha=0.04f))
                                .border(1.dp, ModernViolet.copy(alpha=0.2f), RoundedCornerShape(6.dp))
                                .clickable { remarkNotes = if (remarkNotes.isEmpty()) tag else "$remarkNotes | $tag" }
                                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tag, fontSize = 12.sp, color = ModernViolet, fontWeight = FontWeight.Medium)
                                    Text("✕", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Black, modifier = Modifier.clickable {
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
                            .clickable { newCustomTagText = ""; showCustomTagDialog = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("+ Add Note", fontSize = 12.sp, color = ModernViolet, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth().height(54.dp), enabled = !isSaving, colors = ButtonDefaults.buttonColors(containerColor = ModernViolet), shape = RoundedCornerShape(16.dp),
                    onClick = {
                        if (isSaving) return@Button
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val currentLead = lead

                        if (selectedStatus.isEmpty()) {
                            Toast.makeText(context, "कृपया योग्य Status निवडा (Select Status)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if ((selectedStatus == "Follow-up" || selectedStatus == "Visit Scheduled" || selectedStatus == "Visited") && followUpDate.trim().isEmpty()) {
                            Toast.makeText(context, "Please select a date for Follow-up or Visit", Toast.LENGTH_SHORT).show()
                            attemptedSaveWithoutNotes = true
                            return@Button
                        }

                        if (remarkNotes.trim().isEmpty() && !attemptedSaveWithoutNotes) {
                            attemptedSaveWithoutNotes = true
                            Toast.makeText(context, "Notes भरा किंवा Skip करण्यासाठी पुन्हा Save दाबा", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSaving = true
                        
                        val now = System.currentTimeMillis()
                        val durationSeconds = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                            val sysDuration = getCallDurationFromSystemLog(context, currentLead.phone)
                            if (sysDuration >= 0) sysDuration else if (callStartTimestamp != null && callStartTimestamp > 0L) ((now - callStartTimestamp) / 1000).coerceIn(1, 3600).toInt() else 30
                        } else {
                            if (callStartTimestamp != null && callStartTimestamp > 0L) ((now - callStartTimestamp) / 1000).coerceIn(1, 3600).toInt() else 30
                        }

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
                        
                        viewModel.saveDisposition(
                            lead = currentLead,
                            status = selectedStatus,
                            notes = finalNotes,
                            newInteractionNote = remarkNotes.trim(),
                            followUpDate = if ((selectedStatus == "Follow-up" || selectedStatus == "Visit Scheduled" || selectedStatus == "Visited") && followUpDate.isNotEmpty()) followUpDate else null,
                            callDurationSeconds = durationSeconds,
                            isVisitLog = isVisitToggleOn,
                            onSuccess = { interactionId ->
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
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
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CleanWhite)
                    } else {
                        Text("Save & Sync", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CleanWhite)
                    }
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
                    Text("Delete Lead", color = Color.Red, fontWeight = FontWeight.Bold)
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
}
