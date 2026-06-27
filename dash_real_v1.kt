"package com.enterprise.leadflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    callerName: String,
    leads: List<Lead>,
    onSelectCategory: (String) -> Unit,
    onLogout: () -> Unit
) {
    // 1. Calculate stats from leads list
    // Converted: status == "Converted"
    val convertedCount = leads.count { it.status == "Converted" }
    // Follow-ups (Warm): status == "Warm"
    val followupCount = leads.count { it.status == "Warm" }
    // Pending: status is not Converted and not Not Interested (e.g., New, Ringing, Busy, Cold, empty)
    val pendingCount = leads.count { 
        it.status != "Converted" && it.status != "Not Interested" && it.status != "Warm" 
    }
    val totalCount = leads.size

    val conversionRate = if (totalCount > 0) {
        (convertedCount.toFloat() / totalCount.toFloat() * 100).toInt()
    } else {
        0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "LeadFlow CRM", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = 
<truncated 9834 bytes>