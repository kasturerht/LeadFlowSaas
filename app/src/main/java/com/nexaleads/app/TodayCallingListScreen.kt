package com.nexaleads.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nexaleads.app.ui.viewmodel.SalesMetrics
import com.nexaleads.app.components.DeliveryVerificationSheet


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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    val salesMetrics by viewModel.salesMetrics.collectAsState(initial = SalesMetrics())
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingMediaLead by viewModel.pendingMediaLead.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
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
    var leadForQuickActions by remember { mutableStateOf<Lead?>(null) }
    val quickActionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            colors = DatePickerDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.White)
        ) {
            DatePicker(
                state = datePickerState, 
                colors = DatePickerDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.White,
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
            "CONVERTED" -> nonArchived.filter { it.getPrimaryCategory() == "CONVERTED" && !(it.paymentMethod.equals("Prepaid", ignoreCase = true) && it.paymentStatus?.equals("Link Sent", ignoreCase = true) == true) }
            "PENDING_PAYMENTS" -> nonArchived.filter { it.getPrimaryCategory() == "CONVERTED" && it.paymentMethod.equals("Prepaid", ignoreCase = true) && it.paymentStatus?.equals("Link Sent", ignoreCase = true) == true }
            "REJECTED" -> nonArchived.filter { it.getPrimaryCategory() == "REJECTED" }
            "INQUIRY" -> nonArchived.filter { it.getPrimaryCategory() == "INQUIRY" }
            "RTO" -> nonArchived.filter { it.getPrimaryCategory() == "RTO" }
            "DISPATCHED" -> nonArchived.filter { it.getPrimaryCategory() == "DISPATCHED" }.sortedBy { it.followUpDate }
            "DELIVERED" -> nonArchived.filter { it.getPrimaryCategory() == "DELIVERED" }
            "RETENTION_DUE" -> {
                val isoFull = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
                val calTarget = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                calTarget.add(java.util.Calendar.DAY_OF_YEAR, 7)
                val targetDateStr = isoFull.format(calTarget.time)
                nonArchived.filter { 
                    it.getPrimaryCategory() == "DELIVERED" && 
                    !it.exhaustionDate.isNullOrEmpty() && 
                    it.exhaustionDate <= targetDateStr 
                }
            }
            "ALL" -> nonArchived
            else -> emptyList()
        }
    }

    var selectedOrderIdForDetails by remember { mutableStateOf<String?>(null) }
    var selectedDeliveryVerifyLead by remember { mutableStateOf<com.nexaleads.app.data.model.Lead?>(null) }
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
        "CONVERTED" -> "Confirmed Orders"
        "PENDING_PAYMENTS" -> "Pending Payments (UPI Sent)"
        "REJECTED" -> "Rejected Leads"
        "INQUIRY" -> "Inquiries"
        "RTO" -> "RTOs & Returned Leads"
        "DISPATCHED" -> "In-Transit (Verify)"
        "DELIVERED" -> "Delivered Orders"
        "RETENTION_DUE" -> "Retention Due Leads"
        "ALL" -> "Recent History"
        else -> "Leads"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = { 
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search name or phone...", color = TextSecondary, fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(end = 8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = ModernViolet,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.setSearchMode(false) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
                )
            } else {
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
                        IconButton(onClick = { viewModel.setSearchMode(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
                        }
                        TextButton(onClick = onLogout) {
                            Text("Logout", color = StatusDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
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

            // Premium Segmented Controls
            if (uniqueLabels.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    items(uniqueLabels) { labelName ->
                        val isSelected = selectedLabelFilter == labelName
                        Box(
                            modifier = Modifier
                                .shadow(if (isSelected) 4.dp else 0.dp, RoundedCornerShape(20.dp), spotColor = ModernViolet.copy(alpha = 0.3f))
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) ModernViolet else androidx.compose.ui.graphics.Color.White)
                                .clickable { selectedLabelFilter = labelName }
                                .border(1.dp, if (isSelected) Color.Transparent else BorderSubtle.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(labelName, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) CleanWhite else TextSecondary)
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

            if (isSearching && searchQuery.isNotEmpty()) {
                if (isSearchLoading && searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ModernViolet)
                    }
                } else if (searchResults.isEmpty() && searchQuery.isNotEmpty() && !isSearchLoading) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No leads found for \"$searchQuery\"", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 4.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            PremiumLeadCard(
                                item = item,
                                statusColors = statusColors,
                                indianStatusLabels = indianStatusLabels,
                                onClick = { selectedOrderIdForDetails = item.id },
                                onLongClick = {
                                    leadForQuickActions = item
                                },
                                onCallClick = { cleanNum, dialNum ->
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(cleanNum))
                                    android.widget.Toast.makeText(context, "Number Copied to Dialer.", android.widget.Toast.LENGTH_SHORT).show()
                                    selectedLead = item
                                    viewModel.setPendingCall(item.id)
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:$dialNum") }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {}
                                    showBottomSheet = true
                                },
                                onWhatsAppClick = {
                                    selectedLead = item
                                    showWhatsAppSheet = true
                                },
                                onEditClick = {
                                    selectedOrderIdForDetails = item.id
                                },
                                onVerifyDeliveryClick = if (item.getPrimaryCategory() == "DISPATCHED") { { selectedDeliveryVerifyLead = item } } else null
                            )
                        }
                    }
                }
            } else if (filteredLeads.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("🏆", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Inbox Zero!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You've crushed all your calls for this list.", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filter == "CONVERTED") {
                        item {
                            SalesLedgerDashboard(metrics = salesMetrics, callerName = callerName)
                        }
                        
                        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
                        val todayStr = isoFormat.format(java.util.Date())
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                        val yesterdayStr = isoFormat.format(cal.time)
                        
                        val grouped = filteredLeads.groupBy { lead ->
                            val fallbackCAt = lead.updatedAt?.let {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(it))
                            } ?: ""
                            val cAt = lead.convertedAt ?: fallbackCAt
                            if (cAt.startsWith(todayStr)) "Today"
                            else if (cAt.startsWith(yesterdayStr)) "Yesterday"
                            else "Earlier"
                        }
                        
                        val order = listOf("Today", "Yesterday", "Earlier")
                        order.forEach { groupName ->
                            val groupLeads = grouped[groupName]
                            if (!groupLeads.isNullOrEmpty()) {
                                item {
                                    Text(
                                        text = groupName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                    )
                                }
                                items(groupLeads!!, key = { it.id }) { item ->
                        PremiumLeadCard(
                            item = item,
                            statusColors = statusColors,
                            indianStatusLabels = indianStatusLabels,
                            onClick = { selectedOrderIdForDetails = item.id },
                            onLongClick = {
                                
                                leadForQuickActions = item
                            },
                            onCallClick = { cleanNum, dialNum ->
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(cleanNum))
                                android.widget.Toast.makeText(context, "Number Copied to Dialer.", android.widget.Toast.LENGTH_SHORT).show()
                                selectedLead = item
                                viewModel.setPendingCall(item.id)
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:$dialNum") }
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                                showBottomSheet = true
                            },
                            onWhatsAppClick = {
                                selectedLead = item
                                showWhatsAppSheet = true
                            },
                            onEditClick = {
                                selectedOrderIdForDetails = item.id
                            },
                            onVerifyDeliveryClick = if (item.getPrimaryCategory() == "DISPATCHED") { { selectedDeliveryVerifyLead = item } } else null
                        )
                                }
                            }
                        }
                    } else {
                        items(filteredLeads, key = { it.id }) { item ->
                        PremiumLeadCard(
                            item = item,
                            statusColors = statusColors,
                            indianStatusLabels = indianStatusLabels,
                            onClick = { selectedOrderIdForDetails = item.id },
                            onLongClick = {
                                
                                leadForQuickActions = item
                            },
                            onCallClick = { cleanNum, dialNum ->
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(cleanNum))
                                android.widget.Toast.makeText(context, "Number Copied to Dialer.", android.widget.Toast.LENGTH_SHORT).show()
                                selectedLead = item
                                viewModel.setPendingCall(item.id)
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:$dialNum") }
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                                showBottomSheet = true
                            },
                            onWhatsAppClick = {
                                selectedLead = item
                                showWhatsAppSheet = true
                            },
                            onEditClick = {
                                selectedOrderIdForDetails = item.id
                            },
                            onVerifyDeliveryClick = if (item.getPrimaryCategory() == "DISPATCHED") { { selectedDeliveryVerifyLead = item } } else null
                        )
                        }
                    }
                }
            }
        }

        if (selectedDeliveryVerifyLead != null) {
            DeliveryVerificationSheet(
                lead = selectedDeliveryVerifyLead!!,
                onDismiss = { selectedDeliveryVerifyLead = null },
                onVerifyOptionSelected = { status, reasonOrDate ->
                    var updatedLead = selectedDeliveryVerifyLead!!.copy(status = status)
                    if (status == Constants.STATUS_FOLLOW_UP && reasonOrDate != null) {
                        updatedLead = updatedLead.copy(
                            followUpDate = reasonOrDate,
                            status = Constants.STATUS_DISPATCHED
                        )
                    }
                    
                    if (status == Constants.STATUS_DELIVERED) {
                         val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
                         updatedLead = updatedLead.copy(convertedAt = isoFormat.format(java.util.Date()))
                    }
                    
                    viewModel.updateLead(updatedLead)
                    selectedDeliveryVerifyLead = null
                }
            )
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



    
    if (selectedOrderIdForDetails != null) {
        val leadToEdit = leads.find { it.id == selectedOrderIdForDetails }
        if (leadToEdit != null) {
            com.nexaleads.app.components.CreateLeadBottomSheet(
                viewModel = viewModel,
                sheetState = sheetState,
                leadToEdit = leadToEdit,
                onDismiss = { selectedOrderIdForDetails = null },
                onExistingLeadFound = { existingLead ->
                    selectedOrderIdForDetails = null
                    selectedLead = existingLead
                    showBottomSheet = true
                }
            )
        } else {
            selectedOrderIdForDetails = null
        }
    }
    
    if (leadForQuickActions != null) {
        val lead = leadForQuickActions!!
        val isDispatched = lead.dispatchStatus == "Dispatched" || lead.status == "Dispatched"
        val isConverted = lead.status.equals("Order Placed", ignoreCase = true) || lead.status.equals("Converted", ignoreCase = true) || lead.getPrimaryCategory() == "CONVERTED"
        val isCancelled = lead.status.equals("Order Cancelled", ignoreCase = true) || lead.status.equals(Constants.STATUS_ORDER_CANCELLED, ignoreCase = true)

        var isCancelling by remember { mutableStateOf(false) }
        var selectedReason by remember { mutableStateOf("") }
        var cancelNotes by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { leadForQuickActions = null },
            sheetState = quickActionsSheetState,
            containerColor = androidx.compose.ui.graphics.Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .animateContentSize(animationSpec = tween(300)),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(ModernViolet, ModernVioletDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lead.name.take(1).uppercase(Locale.ROOT).ifEmpty { "#" },
                            color = CleanWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lead.name.ifEmpty { "Unknown Name" },
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getMaskedLeadPhone(lead),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Soft Status Badge
                    val badgeColor = when {
                        isDispatched -> Color(0xFFFEF2F2)
                        isCancelled -> Color(0xFFFEF2F2)
                        isConverted -> Color(0xFFECFDF5)
                        else -> Color(0xFFF3F4F6)
                    }
                    val badgeTextColor = when {
                        isDispatched -> Color(0xFFEF4444)
                        isCancelled -> Color(0xFFEF4444)
                        isConverted -> Color(0xFF10B981)
                        else -> TextSecondary
                    }
                    val badgeText = when {
                        isDispatched -> "Dispatched"
                        isCancelled -> "Cancelled"
                        isConverted -> "Order Placed"
                        else -> lead.status
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(badgeColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                }

                Divider(color = BorderSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)

                if (isDispatched) {
                    // Dispatched Locked Info Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(0.5.dp, Color(0xFFFCA5A5), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🔒", fontSize = 18.sp)
                            Column {
                                Text("Order Dispatched & Locked", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B), fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("This parcel has been dispatched. Telecallers cannot edit, cancel, or delete this record.", color = Color(0xFFB91C1C), fontSize = 12.sp)
                            }
                        }
                    }
                } else if (isCancelled) {
                    // Cancelled Info Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF9FAFB))
                            .border(0.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("❌", fontSize = 18.sp)
                            Column {
                                Text("Order Cancelled", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Reason: ${lead.cancellationReason.orEmpty().ifEmpty { "Not specified" }}", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                } else if (isCancelling) {
                    // Cancellation Reason Panel inside bottom sheet!
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCancelling = false },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("←", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ModernViolet)
                            Text("Back to Quick Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ModernViolet)
                        }

                        Text("Select reason for cancellation:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        
                        val reasons = listOf("Client Changed Mind", "Double Entry / Error", "Payment Failed", "Other")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            reasons.forEach { r ->
                                val isSel = selectedReason == r
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) ModernViolet.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.White)
                                        .border(1.dp, if (isSel) ModernViolet else BorderSubtle, RoundedCornerShape(12.dp))
                                        .clickable { selectedReason = r }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(r, fontSize = 12.sp, color = if (isSel) ModernViolet else TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = cancelNotes,
                            onValueChange = { cancelNotes = it },
                            label = { Text("Additional notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModernViolet, unfocusedBorderColor = BorderSubtle)
                        )

                        Button(
                            onClick = {
                                if (selectedReason.isEmpty()) {
                                    Toast.makeText(context, "Please select a reason", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.cancelOrder(
                                    lead = lead,
                                    reason = if (cancelNotes.isNotEmpty()) "$selectedReason: $cancelNotes" else selectedReason,
                                    onSuccess = {
                                        Toast.makeText(context, "Order Cancelled successfully", Toast.LENGTH_SHORT).show()
                                        leadForQuickActions = null
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ModernViolet)
                        ) {
                            Text("Confirm Cancellation", color = CleanWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Quick Action List Items
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        
                        // Action 1: Call Client
                        QuickActionRow(
                            icon = Icons.Filled.Call,
                            iconBg = Color(0xFFEEF2FF),
                            iconColor = Color(0xFF6366F1),
                            title = "Voice Call Client",
                            subtitle = "Call via default phone app",
                            onClick = {
                                leadForQuickActions = null
                                val dialNum = getCleanLeadPhone(lead)
                                if (dialNum.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$dialNum") }
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // Action 2: WhatsApp Client
                        QuickActionRow(
                            icon = Icons.Filled.Phone,
                            iconBg = Color(0xFFECFDF5),
                            iconColor = Color(0xFF10B981),
                            title = "Send WhatsApp Template",
                            subtitle = "Share confirmation or payment links",
                            onClick = {
                                leadForQuickActions = null
                                selectedLead = lead
                                showWhatsAppSheet = true
                            }
                        )

                        // Action 3: Edit Details
                        QuickActionRow(
                            icon = Icons.Filled.Edit,
                            iconBg = Color(0xFFFFF7ED),
                            iconColor = Color(0xFFF97316),
                            title = "View & Edit Details",
                            subtitle = "View call logs, address or change status",
                            onClick = {
                                leadForQuickActions = null
                                selectedOrderIdForDetails = lead.id
                            }
                        )

                        // Action 4: Cancel Order (Only if converted)
                        if (isConverted) {
                            QuickActionRow(
                                icon = Icons.Filled.Close,
                                iconBg = Color(0xFFFEF2F2),
                                iconColor = Color(0xFFEF4444),
                                title = "Cancel Order Directly",
                                subtitle = "Mark this placed order as cancelled",
                                onClick = {
                                    isCancelling = true
                                }
                            )
                        }

                        // Action 5: Delete Lead (Always shown if not locked)
                        QuickActionRow(
                            icon = Icons.Filled.Delete,
                            iconBg = Color(0xFFF3F4F6),
                            iconColor = Color(0xFF4B5563),
                            title = "Delete Lead entirely",
                            subtitle = "Remove this lead from calling queue",
                            onClick = {
                                viewModel.archiveLead(
                                    leadId = lead.id,
                                    onSuccess = {
                                        Toast.makeText(context, "Lead deleted successfully", Toast.LENGTH_SHORT).show()
                                        leadForQuickActions = null
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
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
            containerColor = androidx.compose.ui.graphics.Color.White,
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




@Composable
fun SalesLedgerDashboard(metrics: SalesMetrics, callerName: String) {
    val targetOrders = 5
    val progress = (metrics.todayOrdersCount.toFloat() / targetOrders).coerceIn(0f, 1f)
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = ModernViolet, spotColor = ModernViolet.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
                    )
                )
        ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = ModernViolet, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                val firstName = callerName.split(" ").firstOrNull() ?: "My"
                Text(
                    "${firstName}'s Ledger",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "₹${metrics.todayRevenue}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text("Today's Revenue", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("₹${metrics.weeklyRevenue}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                    Text("This Week", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
                
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.1f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ModernViolet,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${metrics.todayOrdersCount}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            "/ $targetOrders",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun QuickActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .border(0.5.dp, BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = "→",
            color = BorderSubtle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PremiumLeadCard(
    item: com.nexaleads.app.data.model.Lead,
    statusColors: Map<String, Color>,
    indianStatusLabels: Map<String, String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCallClick: (String, String) -> Unit,
    onWhatsAppClick: () -> Unit,
    onEditClick: () -> Unit,
    onVerifyDeliveryClick: (() -> Unit)? = null
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), spotColor = ModernViolet.copy(alpha = 0.15f), ambientColor = Color.Transparent)
            .animateContentSize(animationSpec = tween(300)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle.copy(alpha=0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rawName = getDisplayLeadName(item)
                    val displayName = rawName.split(" ").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() } }
                    Text(displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (item.isSuspiciousShortCall) {
                        Text("⚠️", fontSize = 13.sp)
                    }
                    if (item.rtoCount > 0) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFEF2F2)).border(0.5.dp, Color(0xFFFCA5A5), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("⚠️ RTO Attempt: ${item.rtoCount}", fontSize = 9.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Black)
                        }
                    }
                }
                val statusColor = statusColors[item.status] ?: ModernViolet
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(statusColor.copy(alpha = 0.08f)).border(0.5.dp, statusColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                    val statusLabel = indianStatusLabels[item.status] ?: item.status
                    val fullStatusLabel = if (!item.subStatus.isNullOrEmpty()) "$statusLabel • ${item.subStatus}" else statusLabel
                    Text(fullStatusLabel.uppercase(Locale.ROOT), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = statusColor)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            // Dynamic Meta Row - Pill styled
            val metaParts = mutableListOf<String>()
            if (item.product.isNotEmpty()) {
                val products = item.product.split(",").map { it.trim() }
                if (products.size > 1) {
                    metaParts.add("📦 ${products.first()} (+${products.size - 1})")
                } else {
                    metaParts.add("📦 ${products.first()}")
                }
            }
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
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    metaParts.forEach { part ->
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text(text = part, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                        }
                    }
                }
            }

            val displayNotes = if (item.notes.contains("\n\n📞 ")) item.notes.substringAfterLast("\n\n📞 ") else { if (item.status == "New" || item.status == "Pending" || item.status.isEmpty()) "" else item.notes }
            if (displayNotes.trim().isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(displayNotes.trim(), fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.9f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            val maskedPhone = getMaskedLeadPhone(item)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Phone
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Phone",
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary
                    )
                    Text(maskedPhone, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.5.sp)
                }
                
                // Quick Actions (July 2026 - Floating "Smart" Actions)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onVerifyDeliveryClick == null) {
                        // Edit Action (Glass Surface)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.04f))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onEditClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(18.dp),
                                tint = TextSecondary
                            )
                        }
                    } else {
                        // Verify Action (Luxury Success Gradient)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onVerifyDeliveryClick()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .height(42.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = CleanWhite
                                )
                                Text("VERIFY", color = CleanWhite, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                            }
                        }
                    }

                    // WhatsApp Action (Premium Logo with Glow)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF25D366).copy(alpha = 0.08f))
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onWhatsAppClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.nexaleads.app.R.drawable.ic_whatsapp),
                            contentDescription = "WhatsApp",
                            modifier = Modifier.size(22.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }

                    // Call Action (Silicon Valley "Primary" Action)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = ModernViolet.copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(ModernViolet, ModernVioletDark)
                                )
                            )
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                val cleanNum = getCleanLeadPhone(item)
                                if (cleanNum.isNotEmpty()) {
                                    val dialNum = getFormattedLeadPhone(item)
                                    onCallClick(cleanNum, dialNum)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

