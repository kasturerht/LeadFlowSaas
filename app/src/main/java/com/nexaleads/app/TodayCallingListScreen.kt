package com.nexaleads.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nexaleads.app.data.model.getPrimaryCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexaleads.app.components.DispositionBottomSheet
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.CallingViewModel
import kotlinx.coroutines.launch
import java.util.*

fun getDisplayLeadName(lead: Lead): String {
    val name = lead.name.trim()
    val source = lead.source.trim()
    val lowName = name.lowercase()
    if (lowName == "fb" || lowName == "ig" || lowName == "facebook" || lowName == "instagram" || name.isEmpty()) {
        if (source.isNotEmpty() && source.lowercase() != "bulk upload") {
            return source
        }
    }
    return if (name.isNotEmpty()) name else "Lead"
}

fun getCleanLeadPhone(lead: Lead): String {
    val cleanPhone = lead.phone.replace("[^\\d+]".toRegex(), "")
    if (cleanPhone.length >= 10) return cleanPhone
    val cleanNotes = lead.notes.replace("[^\\d+]".toRegex(), "")
    if (cleanNotes.length >= 10) return cleanNotes
    return ""
}

fun getFormattedLeadPhone(lead: Lead): String {
    val phone = getCleanLeadPhone(lead)
    if (phone.length == 10 && !phone.startsWith("+")) return "+91$phone"
    if (phone.length == 12 && phone.startsWith("91")) return "+$phone"
    return phone
}

fun getMaskedLeadPhone(lead: Lead): String {
    val p = getFormattedLeadPhone(lead)
    return if (p.length >= 10) {
        p.take(6) + "****" + p.takeLast(2)
    } else {
        p.ifEmpty { "No Number" }
    }
}

fun getCallDurationFromSystemLog(context: android.content.Context, phoneNumber: String): Int {
    val cleanSearchNum = phoneNumber.replace("[^\\d]".toRegex(), "")
    if (cleanSearchNum.isEmpty()) return -1
    var duration = -1
    try {
        val resolver = context.contentResolver
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.DATE, CallLog.Calls.TYPE)
        val cursor: Cursor? = resolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")
        cursor?.use { c ->
            val numberCol = c.getColumnIndex(CallLog.Calls.NUMBER)
            val durationCol = c.getColumnIndex(CallLog.Calls.DURATION)
            val typeCol = c.getColumnIndex(CallLog.Calls.TYPE)
            if (numberCol >= 0 && durationCol >= 0 && typeCol >= 0) {
                while (c.moveToNext()) {
                    val num = c.getString(numberCol) ?: ""
                    val cleanNum = num.replace("[^\\d]".toRegex(), "")
                    if (cleanNum.endsWith(cleanSearchNum) || cleanSearchNum.endsWith(cleanNum)) {
                        if (c.getInt(typeCol) == CallLog.Calls.OUTGOING_TYPE) {
                            duration = c.getInt(durationCol)
                            break
                        }
                    }
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return duration
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodayCallingListScreen(
    currentUserId: String,
    callerName: String,
    filter: String,
    fullLeadsList: List<Lead>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: CallingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingMediaLead by viewModel.pendingMediaLead.collectAsState()
    var showMediaPromptForLead by remember { mutableStateOf<Lead?>(null) }
    var sendBrochureChecked by remember { mutableStateOf(true) }
    var sendVisitingCardChecked by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (pendingMediaLead != null) {
                    showMediaPromptForLead = pendingMediaLead
                    viewModel.setPendingMediaLead(null)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var selectedLead by remember { mutableStateOf<Lead?>(null) }
    var isVisitToggleOn by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showWhatsAppSheet by remember { mutableStateOf(false) }
    val whatsappSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val mainScope = rememberCoroutineScope()

    var selectedStatus by remember { mutableStateOf("Follow-up") }
    var remarkNotes by remember { mutableStateOf("") }
    var followUpDate by remember { mutableStateOf("") }
    val callStartTimestamp by viewModel.pendingCallTimestamp.collectAsState()
    
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var newCustomTagText by remember { mutableStateOf("") }
    
    val sharedPrefs = remember { context.getSharedPreferences("CustomTagsPrefs", Context.MODE_PRIVATE) }
    val customTagsPrefKey = "tags_$currentUserId"
    var userCustomTags by remember { 
        mutableStateOf(sharedPrefs.getStringSet(customTagsPrefKey, emptySet())?.toList() ?: emptyList()) 
    }

    var followupTabState by remember { mutableStateOf("DUE") } 
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") } }
    val todayStr = remember { sdf.format(Date()) }

    val calendar = remember { Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")) }
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Call Log Denied. Timing defaults to local stopwatch.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }
    var showDatePicker by remember { mutableStateOf(false) }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                        cal.timeInMillis = millis
                        val formattedMonth = String.format("%02d", cal.get(Calendar.MONTH) + 1)
                        val formattedDay = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
                        followUpDate = "${cal.get(Calendar.YEAR)}-$formattedMonth-$formattedDay"
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
                    containerColor = SurfaceLight,
                    titleContentColor = ModernViolet,
                    headlineContentColor = TextPrimary,
                    weekdayContentColor = TextSecondary,
                    subheadContentColor = TextPrimary,
                    yearContentColor = TextPrimary,
                    currentYearContentColor = ModernViolet,
                    selectedYearContentColor = CleanWhite,
                    selectedYearContainerColor = ModernViolet,
                    dayContentColor = TextPrimary,
                    disabledDayContentColor = BorderSubtle,
                    selectedDayContentColor = CleanWhite,
                    selectedDayContainerColor = ModernViolet,
                    todayContentColor = ModernViolet,
                    todayDateBorderColor = ModernViolet
                )
            )
        }
    }

    val leads = remember(fullLeadsList, filter, followupTabState, todayStr) {
        val nonArchived = fullLeadsList.filter { !it.archived }
        when (filter) {
            "PENDING" -> nonArchived.filter { it.getPrimaryCategory() == "PENDING" }
            "ATTEMPTED" -> nonArchived.filter { it.getPrimaryCategory() == "ATTEMPTED" }
            "FOLLOWUP" -> {
                if (followupTabState == "DUE") {
                    nonArchived.filter { it.getPrimaryCategory() == "FOLLOWUP" && (it.followUpDate.isNullOrEmpty() || it.followUpDate <= todayStr) }
                } else {
                    nonArchived.filter { it.getPrimaryCategory() == "FOLLOWUP" && !it.followUpDate.isNullOrEmpty() && it.followUpDate > todayStr }
                }
            }
            "VISIT_SCHEDULED" -> {
                if (followupTabState == "DUE") {
                    nonArchived.filter { it.getPrimaryCategory() == "VISIT_SCHEDULED" && (it.followUpDate.isNullOrEmpty() || it.followUpDate <= todayStr) }
                } else {
                    nonArchived.filter { it.getPrimaryCategory() == "VISIT_SCHEDULED" && !it.followUpDate.isNullOrEmpty() && it.followUpDate > todayStr }
                }
            }
            "VISITED" -> nonArchived.filter { it.getPrimaryCategory() == "VISITED" }
            "CONVERTED" -> nonArchived.filter { it.getPrimaryCategory() == "CONVERTED" }
            "REJECTED" -> nonArchived.filter { it.getPrimaryCategory() == "REJECTED" }
            else -> nonArchived
        }
    }

    var selectedLabelFilter by remember { mutableStateOf("ALL") }

    val uniqueLabels = remember(leads) {
        listOf("ALL") + leads.map { it.label.trim().ifEmpty { "General" } }.distinct().sorted()
    }

    val filteredLeads = remember(leads, selectedLabelFilter) {
        if (selectedLabelFilter == "ALL") leads else leads.filter { (it.label.trim().ifEmpty { "General" }) == selectedLabelFilter }
    }

    val titleText = when (filter) {
        "PENDING" -> "Fresh Leads"
        "ATTEMPTED" -> "Attempted (Unreached)"
        "FOLLOWUP" -> "Follow-ups"
        "VISIT_SCHEDULED" -> "Visit Scheduled"
        "VISITED" -> "Visited Deals"
        "CONVERTED" -> "Converted Deals"
        "REJECTED" -> "Rejected Leads"
        else -> "All Leads"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(text = titleText, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = StatusDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(16.dp)
        ) {
            // Roster details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CALLING WORKSPACE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Caller: $callerName", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AccentSurface).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("${leads.size} Records", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            // Clean Label Filter deck
            if (uniqueLabels.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    items(uniqueLabels) { labelName ->
                        val isSelected = selectedLabelFilter == labelName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ModernViolet else SurfaceLight)
                                .clickable { selectedLabelFilter = labelName }
                                .border(1.dp, if (isSelected) Color.Transparent else BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(labelName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CleanWhite else TextSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filter == "FOLLOWUP" || filter == "VISIT_SCHEDULED") {
                TabRow(selectedTabIndex = if (followupTabState == "DUE") 0 else 1, containerColor = BackgroundLight, contentColor = ModernViolet) {
                    Tab(
                        selected = followupTabState == "DUE",
                        onClick = { followupTabState = "DUE" },
                        text = {
                            val count = fullLeadsList.count { 
                                !it.archived && 
                                (if (filter == "FOLLOWUP") it.getPrimaryCategory() == "FOLLOWUP" else it.getPrimaryCategory() == "VISIT_SCHEDULED") && 
                                (it.followUpDate.isNullOrEmpty() || it.followUpDate <= todayStr) 
                            }
                            Text("Due ($count)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    )
                    Tab(
                        selected = followupTabState == "UPCOMING",
                        onClick = { followupTabState = "UPCOMING" },
                        text = {
                            val count = fullLeadsList.count { 
                                !it.archived && 
                                (if (filter == "FOLLOWUP") it.getPrimaryCategory() == "FOLLOWUP" else it.getPrimaryCategory() == "VISIT_SCHEDULED") && 
                                !it.followUpDate.isNullOrEmpty() && it.followUpDate > todayStr 
                            }
                            Text("Upcoming ($count)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    )
                }
            }

            if (filteredLeads.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("🏆", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Inbox Zero!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You've crushed all your calls for this list.", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredLeads) { item ->
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.03f), ambientColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val rawName = getDisplayLeadName(item)
                                        val displayName = rawName.split(" ").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() } }
                                        Text(displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        if (item.isSuspiciousShortCall) {
                                            Text("⚠️", fontSize = 13.sp)
                                        }
                                    }
                                    val statusColor = statusColors[item.status] ?: ModernViolet
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(statusColor.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                                        val statusLabel = indianStatusLabels[item.status] ?: item.status
                                        val fullStatusLabel = if (!item.subStatus.isNullOrEmpty()) "$statusLabel • ${item.subStatus}" else statusLabel
                                        Text(fullStatusLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                // Dynamic Meta Row
                                val metaParts = mutableListOf<String>()
                                if (item.product.isNotEmpty()) metaParts.add("📦 ${item.product}")
                                if (item.orderAmount.isNotEmpty()) metaParts.add("₹${item.orderAmount}")
                                if (item.city.isNotEmpty()) metaParts.add("📍 ${item.city}")
                                if (item.source.isNotEmpty()) metaParts.add(item.source.uppercase(Locale.ROOT))
                                val labelText = if (item.label.isEmpty()) "General" else item.label
                                metaParts.add(labelText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
                                
                                if (!item.followUpDate.isNullOrEmpty()) {
                                    val formattedDate = try {
                                        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                        val outputFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.US)
                                        val date = inputFormat.parse(item.followUpDate)
                                        if (date != null) "📅 ${outputFormat.format(date)}" else "📅 ${item.followUpDate}"
                                    } catch (e: Exception) {
                                        "📅 ${item.followUpDate}"
                                    }
                                    val dateWithSlot = if (!item.followUpTimeSlot.isNullOrEmpty()) "$formattedDate (${item.followUpTimeSlot})" else formattedDate
                                    metaParts.add(dateWithSlot)
                                }
                                
                                if (metaParts.isNotEmpty()) {
                                    Text(
                                        text = metaParts.joinToString(" • "),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                val displayNotes = if (item.notes.contains("\n\n📞 ")) item.notes.substringAfterLast("\n\n📞 ") else { if (item.status == "New" || item.status == "Pending" || item.status.isEmpty()) "" else item.notes }
                                if (displayNotes.trim().isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(displayNotes.trim(), fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.9f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                
                                Spacer(modifier = Modifier.height(14.dp))

                                val maskedPhone = getMaskedLeadPhone(item)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Subtle Phone Text on Left
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = "Phone",
                                            modifier = Modifier.size(14.dp),
                                            tint = TextSecondary
                                        )
                                        Text(maskedPhone, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                    }
                                    
                                    // Quick Actions on Right
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // Call Action
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                val cleanNum = getCleanLeadPhone(item)
                                                if (cleanNum.isNotEmpty()) {
                                                    val dialNum = getFormattedLeadPhone(item)
                                                    clipboardManager.setText(AnnotatedString(cleanNum))
                                                    Toast.makeText(context, "Number Copied to Dialer.", Toast.LENGTH_SHORT).show()
                                                    selectedLead = item
                                                    viewModel.setPendingCall(item.id)
                                                    try {
                                                        val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$dialNum") }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {}
                                                    showBottomSheet = true
                                                }
                                            },
                                            modifier = Modifier.size(42.dp).background(ModernViolet, CircleShape)
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Call",
                                                modifier = Modifier.size(20.dp),
                                                tint = Color.White
                                            )
                                        }
                                        
                                        // WhatsApp Quick Action
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                selectedLead = item
                                                showWhatsAppSheet = true
                                            },
                                            modifier = Modifier.size(42.dp).background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape)
                                        ) {
                                            androidx.compose.material3.Icon(
                                                painter = androidx.compose.ui.res.painterResource(id = com.nexaleads.app.R.drawable.ic_whatsapp),
                                                contentDescription = "WhatsApp",
                                                modifier = Modifier.size(24.dp),
                                                tint = androidx.compose.ui.graphics.Color.Unspecified
                                            )
                                        }
                                        
                                        // Edit Action
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                selectedLead = item
                                                showBottomSheet = true
                                            },
                                            modifier = Modifier.size(42.dp).background(SurfaceLight, CircleShape).border(1.dp, BorderSubtle, CircleShape)
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                modifier = Modifier.size(18.dp),
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showMediaPromptForLead != null) {
            AlertDialog(
                onDismissRequest = { showMediaPromptForLead = null },
                title = { Text("Send Media on WhatsApp") },
                text = { 
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Select what you want to send to ${showMediaPromptForLead!!.name}:")
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sendBrochureChecked,
                                onCheckedChange = { sendBrochureChecked = it },
                                colors = CheckboxDefaults.colors(checkedColor = ModernViolet)
                            )
                            Text("Brochure (PDF)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sendVisitingCardChecked,
                                onCheckedChange = { sendVisitingCardChecked = it },
                                colors = CheckboxDefaults.colors(checkedColor = ModernViolet)
                            )
                            Text("Visiting Card (Image)")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ModernViolet),
                        onClick = {
                            if (sendBrochureChecked || sendVisitingCardChecked) {
                                com.nexaleads.app.utils.WhatsAppSender.sendTemplates(
                                    context,
                                    showMediaPromptForLead!!,
                                    sendText = false,
                                    sendImage = sendVisitingCardChecked,
                                    sendPdf = sendBrochureChecked
                                )
                            }
                            showMediaPromptForLead = null
                        }
                    ) {
                        Text("Send Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMediaPromptForLead = null }) {
                        Text("Skip", color = TextSecondary)
                    }
                },
                containerColor = CleanWhite,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary
            )
        }

        if (showBottomSheet && selectedLead != null) {
            DispositionBottomSheet(
                lead = selectedLead!!,
                viewModel = viewModel,
                sheetState = sheetState,
                snackbarHostState = snackbarHostState,
                callStartTimestamp = callStartTimestamp,
                onDismiss = {
                    showBottomSheet = false
                    viewModel.clearPendingCall()
                },
                onSaveSuccess = { newStatus ->
                    val savedLead = selectedLead!!
                    showBottomSheet = false
                    selectedLead = null
                    viewModel.clearPendingCall()
                    
                    // Business Logic Filter: Don't send WhatsApp for Invalid or Not Interested
                    if (newStatus != "Invalid" && newStatus != "Not Interested" && newStatus != "Deleted") {
                        viewModel.setPendingMediaLead(savedLead)
                        com.nexaleads.app.utils.WhatsAppSender.sendTemplates(
                            context,
                            savedLead,
                            sendText = true,
                            sendImage = false,
                            sendPdf = false
                        )
                    }
                }
            )
        }

        if (showWhatsAppSheet && selectedLead != null) {
            com.nexaleads.app.components.WhatsAppTemplateBottomSheet(
                lead = selectedLead!!,
                sheetState = whatsappSheetState,
                onDismiss = {
                    showWhatsAppSheet = false
                    selectedLead = null
                }
            )
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
                    modifier = Modifier.background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ModernViolet, ModernVioletDark)), RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
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
