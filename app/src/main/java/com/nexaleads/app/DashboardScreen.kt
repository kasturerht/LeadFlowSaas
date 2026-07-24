package com.nexaleads.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import com.nexaleads.app.ui.viewmodel.PipelineState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexaleads.app.components.CreateLeadBottomSheet
import com.nexaleads.app.components.DispositionBottomSheet
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.getPrimaryCategory
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.CallingViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    callerName: String,
    viewModel: CallingViewModel,
    leads: List<Lead>,
    onSelectCategory: (String) -> Unit,
    onHistoryClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Workflow State
    val pendingCallLeadId by viewModel.pendingCallLeadId.collectAsStateWithLifecycle()
    val callStartTimestamp by viewModel.pendingCallTimestamp.collectAsStateWithLifecycle()
    
    val pendingCallLead = remember(leads, pendingCallLeadId) {
        val lead = leads.find { it.id == pendingCallLeadId }
        if (pendingCallLeadId != null && lead == null && leads.isNotEmpty()) {
            viewModel.clearPendingCall()
        }
        lead
    }
    var showDispositionSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showCreateLeadSheet by remember { mutableStateOf(false) }
    val createLeadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingMediaLead by viewModel.pendingMediaLead.collectAsStateWithLifecycle()
    var showMediaPromptForLead by remember { mutableStateOf<Lead?>(null) }
    var sendBrochureChecked by remember { mutableStateOf(true) }
    var sendVisitingCardChecked by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (pendingCallLead != null) {
                    showDispositionSheet = true
                }
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

    // Metrics
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val orgName by viewModel.orgName.collectAsStateWithLifecycle()
    val pipelineActivityState by viewModel.pipelineActivityState.collectAsStateWithLifecycle()

    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        currentHour < 12 -> "Good Morning,"
        currentHour < 17 -> "Good Afternoon,"
        else -> "Good Evening,"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = BackgroundLight,
        bottomBar = {
            // SILICON VALLEY 2026 FLOATING ACTION DOCK
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, BackgroundLight.copy(alpha = 0.9f), BackgroundLight)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Surface(
                    onClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        showCreateLeadSheet = true 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = ModernViolet.copy(alpha = 0.4f)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(ModernViolet, ModernVioletDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Smart Pulsing Icon Container
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CleanWhite.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = CleanWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Create New Lead",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = CleanWhite,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp) // Extra breathing room for the fade out
        ) {


            // ULTRA PREMIUM SILICON VALLEY HEADER
            val firstName = callerName.split(" ").firstOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: callerName
            
            val orgNameParts = orgName.split(" ", limit = 2)
            val brandName = orgNameParts.firstOrNull()?.uppercase() ?: "ORGANIZATION"
            val subTitle = if (orgNameParts.size > 1) orgNameParts[1].uppercase() else ""
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT SIDE: Organization Branding
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = brandName,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (subTitle.isNotEmpty()) {
                        Text(
                            text = subTitle,
                            fontSize = 9.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 18.dp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Search Button
                IconButton(
                    onClick = { 
                        viewModel.setSearchMode(true)
                        onSelectCategory("ALL")
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, BorderSubtle.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Leads",
                        tint = ModernViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // RIGHT SIDE: User Profile (Premium "Smart" Glass Pill)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showLogoutDialog = true }
                        .background(Color.White)
                        .border(
                            width = 1.dp,
                            color = BorderSubtle.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = firstName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 0.2.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.sp,
                                color = StatusSuccess,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Avatar with Gradient & Subtle Inner Border
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(ModernViolet, ModernVioletDark)
                                )
                            )
                    ) {
                        Text(
                            text = firstName.take(1).uppercase(),
                            color = CleanWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            if (showLogoutDialog) {
                val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""

                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = {
                        Text("Settings & Log Out", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
                    },
                    text = {
                        Text("Are you sure you want to log out of your session?", color = TextSecondary)
                    },
                    containerColor = SurfaceLight,
                    confirmButton = {
                        Button(
                            onClick = { 
                                showLogoutDialog = false
                                onLogout() 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Yes, Log Out", color = CleanWhite, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                )
            }
            
            // PRIMARY METRICS: SALES OVERVIEW (APPLE HEALTH STYLE)
            val salesMetrics by viewModel.salesMetrics.collectAsStateWithLifecycle()
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sales Overview",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERFORMANCE OVERVIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Live Updates",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusSuccess,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(StatusSuccess.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SalesMetricCard(
                        modifier = Modifier.weight(1.2f),
                        title = "Today's Revenue",
                        value = "₹${formatter.format(salesMetrics.todayRevenue)}",
                        subtitle = "${salesMetrics.todayOrdersCount} Orders Today",
                        icon = Icons.Rounded.Business,
                        color = ModernViolet,
                        onClick = { viewModel.loadRevenueBreakdown() }
                    )
                    SalesMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Weekly",
                        value = "₹${formatter.format(salesMetrics.weeklyRevenue)}",
                        subtitle = "Last 7 Days",
                        icon = Icons.Rounded.History,
                        color = Color(0xFF10B981)
                    )
                }
            }

            // TOP ZONE: Action Required
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "ACTION REQUIRED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Fresh Leads",
                            count = metrics.freshLeadsCount,
                            color = ModernViolet,
                            icon = "✨",
                            onClick = { onSelectCategory("PENDING") }
                        )
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Follow-ups",
                            count = metrics.dueFollowupsCount,
                            color = StatusWarning,
                            icon = "⏳",
                            onClick = { onSelectCategory("FOLLOWUP") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Retention Due",
                            count = metrics.retentionDueCount,
                            color = Color(0xFFF59E0B), // Amber color
                            icon = "🔄",
                            onClick = { onSelectCategory("RETENTION_DUE") }
                        )
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            title = "In-Transit",
                            count = metrics.dispatchedCount,
                            color = Color(0xFF0EA5E9),
                            icon = "🚚",
                            onClick = { onSelectCategory("DISPATCHED") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            title = "RTOs",
                            count = metrics.rtoCount,
                            color = StatusDanger,
                            icon = "⚠️",
                            onClick = { onSelectCategory("RTO") }
                        )
                        // Empty spacer or another metric can go here
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            
            // SECONDARY ZONE: Pipeline Grid
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PIPELINE OVERVIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PipelineChip(modifier = Modifier.weight(1f), title = "Total Orders", count = metrics.confirmedOrdersCount, color = StatusSuccess, onClick = { onSelectCategory("CONVERTED") })
                        PipelineChip(modifier = Modifier.weight(1f), title = "Delivered", count = metrics.deliveredCount, color = Color(0xFF10B981), onClick = { onSelectCategory("DELIVERED") })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PipelineChip(modifier = Modifier.weight(1f), title = "UPI Pending", count = metrics.pendingPaymentsCount, color = Color(0xFFF97316), onClick = { onSelectCategory("PENDING_PAYMENTS") })
                        PipelineChip(modifier = Modifier.weight(1f), title = "Inquiries", count = metrics.inquiriesCount, color = StatusBusy, onClick = { onSelectCategory("INQUIRY") })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PipelineChip(modifier = Modifier.weight(1f), title = "No Answer", count = metrics.attemptedCount, color = TextSecondary, onClick = { onSelectCategory("ATTEMPTED") })
                        PipelineChip(modifier = Modifier.weight(1f), title = "Rejected", count = metrics.rejectedCount, color = StatusDanger, onClick = { onSelectCategory("REJECTED") })
                    }
                }
            }

            // SEAMLESS FLAT HISTORY ROW (Apple iOS Style, no bulky box)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onHistoryClick() 
                    }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "History",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "View Recent History", 
                        fontSize = 15.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Go",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ACTIVE CALL BANNER
            if (pendingCallLead != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "LIVE SESSION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = StatusSuccess,
                        letterSpacing = 1.5.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.2f)),
                        onClick = { showDispositionSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(24.dp)
                                )
                                // Pulsing animation dot
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color.White, CircleShape)
                                        .padding(2.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(StatusSuccess, CircleShape))
                                }
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pendingCallLead!!.name.ifEmpty { "Customer" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "In-call duration: Active",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
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

    if (showDispositionSheet && pendingCallLead != null) {
        DispositionBottomSheet(
            lead = pendingCallLead!!,
            viewModel = viewModel,
            sheetState = sheetState,
            snackbarHostState = snackbarHostState,
            callStartTimestamp = callStartTimestamp,
            onDismiss = {
                showDispositionSheet = false
                viewModel.clearPendingCall()
            },
            onSaveSuccess = { newStatus ->
                val savedLead = pendingCallLead!!
                showDispositionSheet = false
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
    
    if (pipelineActivityState !is PipelineState.Idle) {
        val state = pipelineActivityState
        var selectedFilter by remember { mutableStateOf("All") }
        
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearRevenueBreakdown() },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "Pipeline Activity Ledger",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Real-time updates for today",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                when (state) {
                    is PipelineState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ModernViolet)
                        }
                    }
                    is PipelineState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(state.message, color = StatusDanger, textAlign = TextAlign.Center, fontSize = 14.sp)
                        }
                    }
                    is PipelineState.Success -> {
                        // Summary Cards Row
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Settled
                            Column(modifier = Modifier.weight(1f).background(StatusSuccess.copy(alpha=0.1f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Text("✅ Settled", fontSize = 12.sp, color = StatusSuccess)
                                val sum = state.settled.sumOf { it.orderAmount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L }
                                Text("₹$sum", fontSize = 16.sp, fontWeight = FontWeight.Black, color = StatusSuccess)
                            }
                            // Pending
                            Column(modifier = Modifier.weight(1f).background(StatusWarning.copy(alpha=0.1f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Text("⏳ Pending", fontSize = 12.sp, color = StatusWarning)
                                val sum = state.pending.sumOf { it.orderAmount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L }
                                Text("₹$sum", fontSize = 16.sp, fontWeight = FontWeight.Black, color = StatusWarning)
                            }
                            // Lost
                            Column(modifier = Modifier.weight(1f).background(StatusDanger.copy(alpha=0.1f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Text("❌ Lost", fontSize = 12.sp, color = StatusDanger)
                                val sum = state.lost.sumOf { it.orderAmount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L }
                                Text("₹$sum", fontSize = 16.sp, fontWeight = FontWeight.Black, color = StatusDanger)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Filters
                        val filters = listOf("All", "Settled", "Pending", "Lost")
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filters.forEach { filter ->
                                val isSelected = selectedFilter == filter
                                val bgColor = if (isSelected) ModernViolet else BackgroundLight
                                val contentColor = if (isSelected) Color.White else TextSecondary
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(bgColor)
                                        .clickable { selectedFilter = filter }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(filter, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val displayList = when(selectedFilter) {
                            "Settled" -> state.settled
                            "Pending" -> state.pending
                            "Lost" -> state.lost
                            else -> state.all
                        }
                        
                        if (displayList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                Text("No activity in this category.", color = TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(displayList) { lead ->
                                    val isSettled = state.settled.contains(lead)
                                    val isPending = state.pending.contains(lead)
                                    val isLost = state.lost.contains(lead)
                                    
                                    val rowBg = when {
                                        isSettled -> StatusSuccess.copy(alpha=0.05f)
                                        isPending -> StatusWarning.copy(alpha=0.05f)
                                        isLost -> StatusDanger.copy(alpha=0.05f)
                                        else -> BackgroundLight
                                    }
                                    
                                    val amtColor = when {
                                        isSettled -> StatusSuccess
                                        isPending -> StatusWarning
                                        isLost -> StatusDanger
                                        else -> TextSecondary
                                    }
                                    val prefix = when {
                                        isSettled -> "+ ₹"
                                        isPending -> "⏳ ₹"
                                        isLost -> "- ₹"
                                        else -> "₹"
                                    }
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(rowBg, RoundedCornerShape(12.dp))
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = lead.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = lead.status,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = amtColor
                                            )
                                        }
                                        
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$prefix${lead.orderAmount}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black,
                                                color = amtColor
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val time = try {
                                                lead.updatedAt?.let { timestamp ->
                                                    val date = java.util.Date(timestamp)
                                                    val hm = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                                                    hm.format(date)
                                                } ?: "N/A"
                                            } catch (e: Exception) { "N/A" }
                                            
                                            Text(
                                                text = time,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }

    if (showCreateLeadSheet) {
        CreateLeadBottomSheet(
            viewModel = viewModel,
            sheetState = createLeadSheetState,
            onDismiss = { showCreateLeadSheet = false },
            onExistingLeadFound = { existingLead ->
                showCreateLeadSheet = false
                viewModel.setPendingCall(existingLead.id)
                showDispositionSheet = true
            }
        )
    }
}

@Composable
fun SalesMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) modifier.height(110.dp).clickable { onClick() } else modifier.height(110.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            
            Column {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    color: Color,
    icon: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.5f)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = icon,
                    fontSize = 20.sp
                )
                Text(
                    text = count.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun PipelineChip(modifier: Modifier = Modifier, title: String, count: Int, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.5f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = count.toString(), fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
            }
        }
    }
}
