package com.enterprise.leadflow

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.enterprise.leadflow.data.model.getPrimaryCategory
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.enterprise.leadflow.components.CreateLeadBottomSheet
import com.enterprise.leadflow.components.DispositionBottomSheet
import com.enterprise.leadflow.data.model.Lead
import com.enterprise.leadflow.ui.theme.*
import com.enterprise.leadflow.ui.viewmodel.CallingViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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
    val activeLeads = leads.filter { !it.archived }
    
    val pendingCount = activeLeads.count { it.getPrimaryCategory() == "PENDING" }
    val followupCount = activeLeads.count { it.getPrimaryCategory() == "FOLLOWUP" }
    val visitScheduledCount = activeLeads.count { it.getPrimaryCategory() == "VISIT_SCHEDULED" }
    val visitedCount = activeLeads.count { it.getPrimaryCategory() == "VISITED" }
    val attemptedCount = activeLeads.count { it.getPrimaryCategory() == "ATTEMPTED" }
    val convertedCount = activeLeads.count { it.getPrimaryCategory() == "CONVERTED" }
    val rejectedCount = activeLeads.count { it.getPrimaryCategory() == "REJECTED" }

    // Next Best Action Logic
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayStr = sdf.format(Date())
    
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        currentHour < 12 -> "Good Morning,"
        currentHour < 17 -> "Prime Time,"
        else -> "Good Evening,"
    }

    val nextBestLead = remember(activeLeads) {
        val followUpDue = activeLeads.firstOrNull { it.getPrimaryCategory() == "FOLLOWUP" && (it.followUpDate ?: "") <= todayStr }
        if (followUpDue != null) return@remember Pair(followUpDue, "Follow-up due today")
        
        val visitDue = activeLeads.firstOrNull { it.getPrimaryCategory() == "VISIT_SCHEDULED" && (it.followUpDate ?: "") <= todayStr }
        if (visitDue != null) return@remember Pair(visitDue, "Visit scheduled today")

        val pending = activeLeads.firstOrNull { it.getPrimaryCategory() == "PENDING" }
        if (pending != null) return@remember Pair(pending, "Hot pending lead waiting")

        null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "$greeting $callerName!", 
                            fontWeight = FontWeight.Black, 
                            fontSize = 24.sp,
                            color = TextPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = StatusDanger, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateLeadSheet = true },
                containerColor = ModernViolet,
                contentColor = CleanWhite,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Add Lead", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BackgroundLight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            
            // TOP ZONE: Information & Context
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "EXPLORE LISTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp
                )

                // 2x2 Glassmorphism Grid -> Expanded to 7 categories
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        GridCard(modifier = Modifier.weight(1f), title = "Pending", count = pendingCount, color = StatusWarning, onClick = { onSelectCategory("PENDING") })
                        GridCard(modifier = Modifier.weight(1f), title = "Follow-ups", count = followupCount, color = ModernViolet, onClick = { onSelectCategory("FOLLOWUP") })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        GridCard(modifier = Modifier.weight(1f), title = "Visit Sch.", count = visitScheduledCount, color = StatusSuccess, onClick = { onSelectCategory("VISIT_SCHEDULED") })
                        GridCard(modifier = Modifier.weight(1f), title = "Visited", count = visitedCount, color = StatusSuccess, onClick = { onSelectCategory("VISITED") })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        GridCard(modifier = Modifier.weight(1f), title = "Attempted", count = attemptedCount, color = StatusBusy, onClick = { onSelectCategory("ATTEMPTED") })
                        GridCard(modifier = Modifier.weight(1f), title = "Converted", count = convertedCount, color = StatusSuccess, onClick = { onSelectCategory("CONVERTED") })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        GridCard(modifier = Modifier.fillMaxWidth(), title = "Rejected", count = rejectedCount, color = StatusDanger, onClick = { onSelectCategory("REJECTED") })
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // BOTTOM ZONE: Cockpit Action Zone
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "NEXT BEST ACTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp
                )

                if (nextBestLead != null) {
                    val (lead, reason) = nextBestLead
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = ModernViolet.copy(alpha = 0.08f), ambientColor = ModernViolet.copy(alpha = 0.02f)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ModernViolet.copy(alpha = 0.1f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("🎯 $reason", color = ModernViolet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Column {
                                Text(lead.name.ifEmpty { "Unknown Name" }, fontSize = 28.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(lead.label.ifEmpty { "General Inquiry" } + " • " + lead.source, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                            
                            if (pendingCallLead?.id == lead.id) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(AccentSurface)
                                        .border(1.dp, ModernViolet.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ModernViolet, strokeWidth = 2.dp)
                                        Text("Waiting for Status", color = ModernViolet, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    
                                    Button(
                                        onClick = { showDispositionSheet = true },
                                        modifier = Modifier.fillMaxWidth().height(50.dp).background(Brush.horizontalGradient(listOf(ModernViolet, ModernVioletDark)), RoundedCornerShape(12.dp)),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Update Status 📝", color = CleanWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    
                                    TextButton(
                                        onClick = { 
                                            viewModel.clearPendingCall()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Didn't Call / Cancel ✖", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            } else {
                                SlideToAction(
                                    text = "Slide to Dial",
                                    onComplete = {
                                        viewModel.setPendingCall(lead.id)
                                        try {
                                            val dialNum = lead.phone.replace(Regex("[^0-9+]"), "")
                                            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$dialNum") }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // No dialer app found
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🎉", fontSize = 48.sp)
                            Text("Inbox Zero!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            Text("You've crushed all your priority calls. Take a break!", fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.04f), ambientColor = Color.Black.copy(alpha = 0.01f))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onHistoryClick() 
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.size(40.dp).background(AccentSurface, CircleShape), contentAlignment = Alignment.Center) {
                                Text("⏱️", fontSize = 18.sp)
                            }
                            Column {
                                Text("Recent History", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                                Text("Review or undo recent calls", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text("➔", fontSize = 18.sp, color = TextSecondary, fontWeight = FontWeight.Black)
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
                            com.enterprise.leadflow.utils.WhatsAppSender.sendTemplates(
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
                viewModel.clearPendingCall() // Clear it so it doesn't pop up infinitely on every resume!
            },
            onSaveSuccess = { newStatus ->
                val savedLead = pendingCallLead!!
                showDispositionSheet = false
                viewModel.clearPendingCall()
                
                // Business Logic Filter: Don't send WhatsApp for Invalid or Not Interested
                if (newStatus != "Invalid" && newStatus != "Not Interested" && newStatus != "Deleted") {
                    viewModel.setPendingMediaLead(savedLead)
                    com.enterprise.leadflow.utils.WhatsAppSender.sendTemplates(
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
fun GridCard(modifier: Modifier = Modifier, title: String, count: Int, color: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .height(96.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = color.copy(alpha = 0.08f), ambientColor = color.copy(alpha = 0.02f))
            .clip(RoundedCornerShape(20.dp))
            .clickable { 
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick() 
            },
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                }
            }
            Text(count.toString(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        }
    }
}

@Composable
fun SlideToAction(text: String, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var maxWidth by remember { mutableIntStateOf(0) }
    val thumbSize = 64.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }
    
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "snapBack"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(elevation = 12.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.04f), ambientColor = Color.Black.copy(alpha = 0.01f))
            .clip(CircleShape)
            .background(SurfaceLight)
            .border(0.5.dp, BorderSubtle, CircleShape)
            .onSizeChanged { maxWidth = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        val alpha = 1f - (animatedOffsetX / maxWidth.coerceAtLeast(1).toFloat())
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().alpha(alpha.coerceIn(0f, 1f)).padding(start = 24.dp),
            textAlign = TextAlign.Center,
            color = TextPrimary.copy(alpha = 0.6f),
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            letterSpacing = 1.sp
        )
        
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .size(64.dp)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(ModernViolet, ModernVioletDark)))
                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = ModernViolet.copy(alpha = 0.25f))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val maxDrag = maxWidth - thumbSizePx
                            if (offsetX > maxDrag * 0.75f) {
                                offsetX = maxDrag
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onComplete()
                            } else {
                                offsetX = 0f
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val maxDrag = maxWidth - thumbSizePx
                        offsetX = (offsetX + dragAmount).coerceIn(0f, maxDrag)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("📞", fontSize = 20.sp)
        }
    }
}
