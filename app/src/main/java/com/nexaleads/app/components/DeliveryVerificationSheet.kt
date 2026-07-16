package com.nexaleads.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Lead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryVerificationSheet(
    lead: Lead,
    onDismiss: () -> Unit,
    onVerifyOptionSelected: (String, String?) -> Unit // (status, reasonOrDate)
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // For Follow-up later
    var showDatePicker by remember { mutableStateOf(false) }
    

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Verify Delivery",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1F2937)
                )
                Text(
                    text = "For ${lead.name.ifEmpty { "Customer" }}",
                    fontSize = 16.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
                if (!lead.trackingNumber.isNullOrEmpty()) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFF6FF)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Tracking: ${lead.trackingNumber}", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Option 1: Delivered
                VerificationOption(
                    icon = Icons.Rounded.CheckCircle,
                    iconTint = Color(0xFF10B981),
                    title = "Yes, Delivered Successfully",
                    subtitle = "Close this lead and mark as successful.",
                    onClick = { onVerifyOptionSelected(Constants.STATUS_DELIVERED, null) }
                )

                // Option 2: Still Waiting
                VerificationOption(
                    icon = Icons.Rounded.Schedule,
                    iconTint = Color(0xFFF59E0B),
                    title = "Not Yet (Still Waiting)",
                    subtitle = "Set a follow-up date to check again later.",
                    onClick = { showDatePicker = true }
                )
            }
        }
    }


    if (showDatePicker) {
        // Simple fallback to picking tomorrow if native picker is complex for now
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Set Follow-up Date", fontWeight = FontWeight.Bold) },
            text = { Text("Automatically schedule a follow-up for 2 days from now?") },
            confirmButton = {
                Button(onClick = {
                    showDatePicker = false
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 2)
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    onVerifyOptionSelected(Constants.STATUS_FOLLOW_UP, sdf.format(cal.time))
                }) {
                    Text("Yes, Remind me in 2 days")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun VerificationOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick() },
        color = Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 13.sp, color = Color(0xFF6B7280))
            }
        }
    }
}
