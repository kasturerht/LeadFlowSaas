package com.nexaleads.app.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nexaleads.app.ui.theme.*
import com.nexaleads.app.utils.WhatsAppSender

@Composable
fun SmartWhatsAppOrderCard(
    customerName: String,
    products: String,
    address: String,
    paymentMode: String,
    autoLaunch: Boolean,
    onAutoLaunchChange: (Boolean) -> Unit,
    includeAddress: Boolean,
    onIncludeAddressChange: (Boolean) -> Unit,
    includePaymentLink: Boolean,
    onIncludePaymentLinkChange: (Boolean) -> Unit,
    includeDispatchNote: Boolean,
    onIncludeDispatchNoteChange: (Boolean) -> Unit,
    includeSupportPhone: Boolean,
    onIncludeSupportPhoneChange: (Boolean) -> Unit,
    selectedLanguage: String = "English",
    onLanguageChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val waDarkGreen = Color(0xFF128C7E)
    val waLightGreen = Color(0xFFE8F5E9)
    val surfaceColor = Color.White
    val borderLight = Color(0xFFF1F5F9)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)

    var internalLanguage by remember { mutableStateOf(selectedLanguage) }
    val currentLanguage = if (onLanguageChange != null) selectedLanguage else internalLanguage
    val handleLanguageChange: (String) -> Unit = { lang ->
        internalLanguage = lang
        onLanguageChange?.invoke(lang)
    }

    val isCod = paymentMode.equals("COD", ignoreCase = true) || paymentMode.contains("Cash", ignoreCase = true)

    val livePreviewText = remember(
        customerName, products, address, paymentMode,
        includeAddress, includePaymentLink, includeDispatchNote, includeSupportPhone, currentLanguage
    ) {
        WhatsAppSender.generateOrderMessage(
            customerName = customerName,
            products = products,
            address = address,
            paymentMode = paymentMode,
            includeAddress = includeAddress,
            includePaymentLink = includePaymentLink,
            includeDispatchNote = includeDispatchNote,
            includeSupportPhone = includeSupportPhone,
            language = currentLanguage
        )
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
                        text = "Message Personalization",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimalist Toggles
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LuxuryToggleChip(
                            label = "Address",
                            icon = "📍",
                            selected = includeAddress,
                            onClick = { onIncludeAddressChange(!includeAddress) },
                            modifier = Modifier.weight(1f)
                        )
                        LuxuryToggleChip(
                            label = "Pay Link",
                            icon = "💳",
                            selected = !isCod && includePaymentLink,
                            enabled = !isCod,
                            onClick = { if (!isCod) onIncludePaymentLinkChange(!includePaymentLink) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LuxuryToggleChip(
                            label = "Dispatch",
                            icon = "🚚",
                            selected = includeDispatchNote,
                            onClick = { onIncludeDispatchNoteChange(!includeDispatchNote) },
                            modifier = Modifier.weight(1f)
                        )
                        LuxuryToggleChip(
                            label = "Support",
                            icon = "📞",
                            selected = includeSupportPhone,
                            onClick = { onIncludeSupportPhoneChange(!includeSupportPhone) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Client Language",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // iOS Style Segmented Control
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
                            val isSelected = currentLanguage.equals(langCode, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .clickable { handleLanguageChange(langCode) }
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

@Composable
private fun LuxuryToggleChip(
    label: String,
    icon: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waDarkGreen = Color(0xFF128C7E)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> Color(0xFFF8FAFC)
                    selected -> waDarkGreen.copy(alpha = 0.08f)
                    else -> Color.White
                }
            )
            .border(
                width = 1.dp,
                color = when {
                    !enabled -> Color(0xFFF1F5F9)
                    selected -> waDarkGreen.copy(alpha = 0.3f)
                    else -> Color(0xFFE2E8F0)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            !enabled -> Color(0xFFE2E8F0)
                            selected -> waDarkGreen
                            else -> Color(0xFFF1F5F9)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected && enabled) {
                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected && enabled) FontWeight.SemiBold else FontWeight.Medium,
                color = if (!enabled) Color(0xFF94A3B8) else if (selected) textPrimary else textSecondary
            )
        }
    }
}
