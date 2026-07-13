package com.nexaleads.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Business
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Workflow State
    val pendingCallLeadId by viewModel.pendingCallLeadId.collectAsState()
    val callStartTimestamp by viewModel.pendingCallTimestamp.collectAsState()
    
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
    val pendingMediaLead by viewModel.pendingMediaLead.collectAsState()
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
    val metrics by viewModel.dashboardMetrics.collectAsState()

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
            // TRUE SILICON VALLEY FLAT CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, BackgroundLight, BackgroundLight)
                        )
                    ) // Seamless fade out at the bottom
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        showCreateLeadSheet = true 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Premium standard height
                    shape = RoundedCornerShape(16.dp), // Modern curve, not generic pill
                    colors = ButtonDefaults.buttonColors(containerColor = ModernViolet),
                    elevation = null, // ZERO SHADOW. Flat is premium.
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Lead",
                            tint = CleanWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add New Lead",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanWhite,
                            letterSpacing = 0.5.sp
                        )
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
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT SIDE: Organization Branding (Reference Screenshot Style)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Organization Logo
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color(0xFF4ADE80), Color(0xFF16A34A)) 
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = CleanWhite, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Column {
                        Text(
                            text = "SUJATA",
                            fontSize = 20.sp,
                            color = Color(0xFF1E40AF), // Deep Blue like the reference
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Health & Wellness",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // RIGHT SIDE: User Profile (Reference Screenshot Style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showLogoutDialog = true } // Make the whole block clickable for logout
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Hi, $firstName",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Online",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // User Avatar
                    Surface(
                        shape = CircleShape,
                        color = ModernViolet,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = firstName.take(1).uppercase(),
                                color = CleanWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = {
                        Text("Log Out", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
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
            
            // TOP ZONE: Action Required
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "ACTION REQUIRED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PriorityWidget(
                        modifier = Modifier.weight(1f), 
                        title = "UPI Sent", 
                        count = metrics.pendingPaymentsCount, 
                        color = StatusDanger, 
                        onClick = { onSelectCategory("PENDING_PAYMENTS") }
                    )
                    PriorityWidget(
                        modifier = Modifier.weight(1f), 
                        title = "Follow-ups", 
                        count = metrics.dueFollowupsCount, 
                        color = StatusWarning, 
                        onClick = { onSelectCategory("FOLLOWUP") }
                    )
                    PriorityWidget(
                        modifier = Modifier.weight(1f), 
                        title = "Fresh", 
                        count = metrics.freshLeadsCount, 
                        color = ModernViolet, 
                        onClick = { onSelectCategory("PENDING") }
                    )
                }
            }
            
            // SECONDARY ZONE: Pipeline Grid
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
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
                        PipelineChip(modifier = Modifier.weight(1f), title = "Orders", count = metrics.confirmedOrdersCount, color = StatusSuccess, onClick = { onSelectCategory("CONVERTED") })
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
                        text = "ACTIVE CALL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = StatusSuccess,
                        letterSpacing = 1.5.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 0.dp, // Flat design
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.3f)),
                        onClick = { showDispositionSheet = true }
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        pendingCallLead!!.name.ifEmpty { "Unknown Name" }, 
                                        fontSize = 20.sp, 
                                        fontWeight = FontWeight.Black, 
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        pendingCallLead!!.phone, 
                                        fontSize = 14.sp, 
                                        color = TextSecondary, 
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp), 
                                    color = StatusSuccess, 
                                    strokeWidth = 3.dp
                                )
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(StatusSuccess.copy(alpha = 0.1f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Tap to Update Disposition", 
                                    color = StatusSuccess, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight, 
                                    contentDescription = null, 
                                    tint = StatusSuccess, 
                                    modifier = Modifier.size(18.dp)
                                )
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
fun PriorityWidget(modifier: Modifier = Modifier, title: String, count: Int, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 0.dp, // FLAT DESIGN IS PREMIUM
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                fontSize = 42.sp, // Reduced to fit 3 in a row
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = (-1.0).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 13.sp, // Reduced to fit 3 in a row
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
        shadowElevation = 0.dp, // FLAT DESIGN IS PREMIUM
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
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
                    .size(28.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(count.toString(), fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
            }
        }
    }
}
