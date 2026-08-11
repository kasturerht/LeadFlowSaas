package com.nexaleads.app.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.WhatsAppTemplate
import com.nexaleads.app.utils.WhatsAppSender

@Composable
fun SmartWhatsAppTemplateCard(
    status: String,
    autoLaunch: Boolean,
    onAutoLaunchChange: (Boolean) -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    templates: List<WhatsAppTemplate>,
    mockLead: Lead,
    orgName: String,
    messagingProfile: com.nexaleads.app.data.model.MessagingProfile?,
    productsList: List<com.nexaleads.app.data.models.Product> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val waDarkGreen = Color(0xFF128C7E)
    val waLightGreen = Color(0xFFE8F5E9)
    val surfaceColor = Color.White
    val borderLight = Color(0xFFF1F5F9)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)

    // Filter templates for current status
    val availableTemplates = templates.filter { it.isActive && com.nexaleads.app.Constants.normalizeStatus(it.statusTrigger).equals(status, ignoreCase = true) }
    
    // Available languages based on available templates
    val availableLanguages = availableTemplates.map { it.language }.distinct()
    
    // Ensure selected language is valid for this status, otherwise fallback
    LaunchedEffect(status, templates) {
        if (availableLanguages.isNotEmpty() && !availableLanguages.any { it.equals(selectedLanguage, ignoreCase = true) }) {
            val fallback = availableLanguages.firstOrNull { it.equals("English", ignoreCase = true) } ?: availableLanguages.first()
            onLanguageChange(fallback)
        }
    }

    val currentTemplate = availableTemplates.firstOrNull { it.language.equals(selectedLanguage, ignoreCase = true) }
        ?: availableTemplates.firstOrNull()

    val livePreviewText = remember(
        currentTemplate, mockLead, orgName, messagingProfile, selectedLanguage
    ) {
        if (currentTemplate != null) {
            WhatsAppSender.parseDynamicTemplate(
                templateText = currentTemplate.templateText,
                lead = mockLead,
                orgName = orgName,
                messagingProfile = messagingProfile,
                productsList = productsList
            )
        } else {
            WhatsAppSender.generateDispositionMessage(
                status = mockLead.status,
                customerName = mockLead.name,
                productName = mockLead.product,
                language = selectedLanguage,
                orgName = orgName
            )
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        shadowElevation = 6.dp,
        border = null
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(waLightGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💬", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "WhatsApp Automation",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = if (autoLaunch) "Launches instantly on Save" else "Manual mode",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }
                Switch(
                    checked = autoLaunch,
                    onCheckedChange = onAutoLaunchChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = waDarkGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCBD5E1),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            AnimatedVisibility(
                visible = autoLaunch,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = borderLight, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Client Language",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dynamic Language Selector based on available templates
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val languages = listOf("English" to "English", "Marathi" to "मराठी", "Hindi" to "हिंदी")
                        languages.forEach { (langCode, langLabel) ->
                            val hasTemplateInLang = availableTemplates.isEmpty() || availableLanguages.any { it.equals(langCode, ignoreCase = true) }
                            if (hasTemplateInLang) {
                                val isSelected = selectedLanguage.equals(langCode, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable { onLanguageChange(langCode) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = langLabel,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) textPrimary else textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Preview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp Message", livePreviewText))
                                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = waDarkGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = livePreviewText,
                            fontSize = 13.sp,
                            color = textPrimary,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    }
}
