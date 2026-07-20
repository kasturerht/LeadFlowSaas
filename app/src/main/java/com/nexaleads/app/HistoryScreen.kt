package com.nexaleads.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    currentUserId: String,
    orgId: String,
    fullLeadsList: List<Lead>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val interactions by viewModel.interactions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    var showPopup by remember { mutableStateOf(false) }
    var selectedLead by remember { mutableStateOf<Lead?>(null) }
    var selectedInteraction by remember { mutableStateOf<Interaction?>(null) }
    
    val context = LocalContext.current

    LaunchedEffect(currentUserId, orgId) {
        viewModel.fetchHistory(currentUserId, orgId)
    }

    // Grouping logic
    val groupedInteractions = remember(interactions) {
        groupInteractionsByDate(interactions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Recent Activity", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 20.sp,
                        color = TextPrimary
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "←", 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.Black, 
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text(
                            text = "Logout", 
                            color = StatusDanger, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
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
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ModernViolet)
                }
            } else if (interactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recent activity found.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp, start = 8.dp, end = 16.dp)
                ) {
                    groupedInteractions.forEach { (dateHeader, items) ->
                        item {
                            DateHeader(dateHeader)
                        }
                        itemsIndexed(items) { index, interaction ->
                            val lead = fullLeadsList.find { it.id == interaction.leadId }
                            val isLast = index == items.size - 1
                            TimelineHistoryItem(
                                interaction = interaction,
                                leadName = lead?.name ?: "Unknown Lead",
                                isLast = isLast,
                                onClick = {
                                    if (lead != null) {
                                        selectedLead = lead
                                        selectedInteraction = interaction
                                        showPopup = true
                                    } else {
                                        Toast.makeText(context, "Lead not found in your roster.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPopup && selectedLead != null && selectedInteraction != null) {
        RevertDialog(
            interactions = interactions,
            selectedInteraction = selectedInteraction!!,
            onDismiss = { showPopup = false },
            onConfirm = {
                viewModel.revertInteraction(selectedInteraction!!)
                Toast.makeText(context, "Action Reverted.", Toast.LENGTH_SHORT).show()
                showPopup = false
            }
        )
    }
}

@Composable
fun DateHeader(dateHeader: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateHeader,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = if (dateHeader == "Today") ModernViolet else TextPrimary
        )
    }
}

@Composable
fun TimelineHistoryItem(
    interaction: Interaction,
    leadName: String,
    isLast: Boolean,
    onClick: () -> Unit
) {
    // Parse timestamp to time only
    val timeStr = try {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = isoFormat.parse(interaction.timestamp)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        if (date != null) timeFormat.format(date) else ""
    } catch (e: Exception) {
        ""
    }

    // Determine Action Type for Icons and Colors
    val isCancel = interaction.statusAfter.contains("Cancel", ignoreCase = true)
    val isArchive = interaction.statusAfter.contains("Archived", ignoreCase = true) || interaction.notes.contains("deleted", ignoreCase = true)
    val isEdit = interaction.statusAfter.contains("Edit", ignoreCase = true) || interaction.notes.contains("Updated", ignoreCase = true) || interaction.notes.contains("Custom Tag", ignoreCase = true)
    val isRTO = interaction.statusAfter.contains("RTO", ignoreCase = true)
    val isCall = interaction.duration > 0 || interaction.notes.contains("Call", ignoreCase = true)
    
    val actionColor = when {
        isCancel || isArchive -> StatusDanger
        isRTO -> StatusWarning
        isCall -> StatusSuccess
        isEdit -> Color(0xFF3B82F6)
        else -> ModernViolet
    }

    val actionIcon: ImageVector = when {
        isCancel -> Icons.Rounded.Cancel
        isArchive -> Icons.Rounded.Delete
        isRTO -> Icons.Rounded.Autorenew
        isCall -> Icons.Rounded.Phone
        isEdit -> Icons.Rounded.Edit
        else -> Icons.Rounded.Info
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline Column
        Column(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(actionColor.copy(alpha = 0.15f), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    tint = actionColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Vertical Line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .padding(vertical = 4.dp)
                        .drawBehind {
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(size.width / 2, 0f),
                                end = Offset(size.width / 2, size.height),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                )
            }
        }

        // Card Content
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp, end = 8.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.05f))
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = leadName,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeStr,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statusBefore = interaction.statusBefore.ifEmpty { "Pending" }
                    if (statusBefore != interaction.statusAfter) {
                        Text(
                            text = statusBefore,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = ModernViolet,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(14.dp)
                        )
                    }
                    
                    Text(
                        text = interaction.statusAfter,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (interaction.duration > 0) {
                        val m = interaction.duration / 60
                        val s = interaction.duration % 60
                        val durationStr = if (m > 0) "${m}m ${s}s" else "${s}s"
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(ModernViolet.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HeadsetMic,
                                contentDescription = null,
                                tint = ModernViolet,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = durationStr,
                                color = ModernViolet,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                if (interaction.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = interaction.notes,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundLight, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RevertDialog(
    interactions: List<Interaction>,
    selectedInteraction: Interaction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revert Action", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            val latestForLead = interactions.firstOrNull { it.leadId == selectedInteraction.leadId }
            val isLatest = latestForLead?.id == selectedInteraction.id
            
            if (isLatest) {
                Text("Are you sure you want to revert this action? The lead will go back to its previous status or Pending.", color = TextSecondary)
            } else {
                Text("This is an older history record. Deleting this will remove it from history, but the Lead's current status will remain '${latestForLead?.statusAfter}' because newer actions exist.", color = TextSecondary)
            }
        },
        containerColor = SurfaceLight,
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = ModernViolet),
                shape = RoundedCornerShape(8.dp),
                onClick = onConfirm
            ) {
                Text("Revert", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    )
}

// Utility to Group Interactions by Date
fun groupInteractionsByDate(interactions: List<Interaction>): Map<String, List<Interaction>> {
    val grouped = mutableMapOf<String, MutableList<Interaction>>()
    
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    
    val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

    for (interaction in interactions) {
        val dateStr = interaction.timestamp
        if (dateStr.isEmpty()) continue
        
        try {
            val date = isoFormat.parse(dateStr)
            if (date != null) {
                val itemCal = Calendar.getInstance().apply { time = date }
                
                val header = when {
                    isSameDay(itemCal, todayCal) -> "Today"
                    isSameDay(itemCal, yesterdayCal) -> "Yesterday"
                    else -> dateFormat.format(date)
                }
                
                if (!grouped.containsKey(header)) {
                    grouped[header] = mutableListOf()
                }
                grouped[header]!!.add(interaction)
            }
        } catch (e: Exception) {
            // Fallback for non-iso strings if any
            if (!grouped.containsKey("Older")) grouped["Older"] = mutableListOf()
            grouped["Older"]!!.add(interaction)
        }
    }
    
    // Convert to Map
    return grouped
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
