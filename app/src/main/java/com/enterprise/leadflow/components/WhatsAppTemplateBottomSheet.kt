package com.enterprise.leadflow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.leadflow.data.model.Lead
import com.enterprise.leadflow.ui.theme.*
import com.enterprise.leadflow.utils.WhatsAppSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppTemplateBottomSheet(
    lead: Lead,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    var sendText by remember { mutableStateOf(false) }
    var sendImage by remember { mutableStateOf(false) }
    var sendPdf by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val hasSelection = sendText || sendImage || sendPdf

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CleanWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "WhatsApp Templates",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                "Select what you want to send to ${lead.name}",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Checkboxes
            TemplateOptionRow(
                title = "Welcome Text & Address",
                subtitle = "Sends personalized greeting and office address",
                isChecked = sendText,
                onCheckedChange = { sendText = it }
            )
            TemplateOptionRow(
                title = "Visiting Card (Image)",
                subtitle = "Sends your digital business card",
                isChecked = sendImage,
                onCheckedChange = { sendImage = it }
            )
            TemplateOptionRow(
                title = "Project Brochure (PDF)",
                subtitle = "Sends the full detailed brochure",
                isChecked = sendPdf,
                onCheckedChange = { sendPdf = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = hasSelection && !isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ModernViolet,
                    disabledContainerColor = SurfaceLight
                ),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    if (isSending || !hasSelection) return@Button
                    isSending = true
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    
                    WhatsAppSender.sendTemplates(context, lead, sendText, sendImage, sendPdf)
                    onDismiss()
                }
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CleanWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send to WhatsApp", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TemplateOptionRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = ModernViolet)
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
    }
}
