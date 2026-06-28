package com.nexaleads.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    currentUserId: String,
    fullLeadsList: List<Lead>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val interactions by viewModel.interactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showPopup by remember { mutableStateOf(false) }
    var selectedLead by remember { mutableStateOf<Lead?>(null) }
    var selectedInteraction by remember { mutableStateOf<com.nexaleads.app.data.model.Interaction?>(null) }
    
    val context = LocalContext.current

    LaunchedEffect(currentUserId) {
        viewModel.fetchHistory(currentUserId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Recent History", 
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
                .padding(16.dp)
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(interactions) { interaction ->
                        val lead = fullLeadsList.find { it.id == interaction.leadId }
                        val leadName = lead?.name ?: "Unknown Lead"
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                                .clickable {
                                    if (lead != null) {
                                        selectedLead = lead
                                        selectedInteraction = interaction
                                        showPopup = true
                                    } else {
                                        Toast.makeText(context, "Lead not found in your roster.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceLight)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = leadName,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
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
                                    Text(
                                        text = timeStr,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = interaction.statusBefore.ifEmpty { "Pending" },
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "  →  ",
                                            color = ModernViolet,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = interaction.statusAfter,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (interaction.duration > 0) {
                                        val m = interaction.duration / 60
                                        val s = interaction.duration % 60
                                        val durationStr = if (m > 0) "${m}m ${s}s" else "${s}s"
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .background(AccentSurface, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("📞", fontSize = 10.sp)
                                            Text(durationStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPopup && selectedLead != null && selectedInteraction != null) {
        AlertDialog(
            onDismissRequest = { showPopup = false },
            title = { Text("Revert Action", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                val latestForLead = interactions.firstOrNull { it.leadId == selectedInteraction!!.leadId }
                val isLatest = latestForLead?.id == selectedInteraction!!.id
                
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
                    onClick = {
                        viewModel.revertInteraction(selectedInteraction!!)
                        Toast.makeText(context, "Lead reverted.", Toast.LENGTH_SHORT).show()
                        showPopup = false
                    }
                ) {
                    Text("Revert", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPopup = false }) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}
