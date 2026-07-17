package com.nexaleads.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.nexaleads.app.Constants
import com.nexaleads.app.ui.theme.*

data class SmartIconData(
    val icon: ImageVector,
    val tint: Color = ModernViolet
)

// Custom Brand Icons (Feather-style vectors)
val BrandFacebook = ImageVector.Builder(
    name = "Facebook", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18f, 2f)
        lineTo(15f, 2f)
        curveTo(13.6739f, 2f, 12.4021f, 2.52678f, 11.4645f, 3.46447f)
        curveTo(10.5268f, 4.40215f, 10f, 5.67392f, 10f, 7f)
        lineTo(10f, 10f)
        lineTo(7f, 10f)
        lineTo(7f, 14f)
        lineTo(10f, 14f)
        lineTo(10f, 22f)
        lineTo(14f, 22f)
        lineTo(14f, 14f)
        lineTo(17f, 14f)
        lineTo(18f, 10f)
        lineTo(14f, 10f)
        lineTo(14f, 7f)
        curveTo(14f, 6.73478f, 14.1054f, 6.48043f, 14.2929f, 6.29289f)
        curveTo(14.4804f, 6.10536f, 14.7348f, 6f, 15f, 6f)
        lineTo(18f, 6f)
        lineTo(18f, 2f)
        close()
    }
}.build()

val BrandInstagram = ImageVector.Builder(
    name = "Instagram", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7f, 2f)
        lineTo(17f, 2f)
        curveTo(19.7614f, 2f, 22f, 4.23858f, 22f, 7f)
        lineTo(22f, 17f)
        curveTo(22f, 19.7614f, 19.7614f, 22f, 17f, 22f)
        lineTo(7f, 22f)
        curveTo(4.23858f, 22f, 2f, 19.7614f, 2f, 17f)
        lineTo(2f, 7f)
        curveTo(2f, 4.23858f, 4.23858f, 2f, 7f, 2f)
        close()
    }
    path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16f, 11.37f)
        curveTo(16.1234f, 12.2022f, 15.9813f, 13.0522f, 15.5938f, 13.799f)
        curveTo(15.2063f, 14.5458f, 14.5931f, 15.1514f, 13.8416f, 15.5297f)
        curveTo(13.0901f, 15.9079f, 12.2384f, 16.0396f, 11.4078f, 15.9082f)
        curveTo(10.5771f, 15.7768f, 9.80977f, 15.3892f, 9.21485f, 14.8016f)
        curveTo(8.61993f, 14.214f, 8.22659f, 13.4566f, 8.09011f, 12.626f)
        curveTo(7.95363f, 11.7954f, 8.08173f, 10.9421f, 8.45524f, 10.1866f)
        curveTo(8.82875f, 9.43118f, 9.4293f, 8.81156f, 10.173f, 8.41687f)
        curveTo(10.9167f, 8.02219f, 11.7668f, 7.87201f, 12.6f, 8f)
        curveTo(13.4735f, 8.13419f, 14.2831f, 8.54477f, 14.9126f, 9.17421f)
        curveTo(15.542f, 9.80366f, 15.9526f, 10.6133f, 16.0868f, 11.4868f)
        lineTo(16f, 11.37f)
        close()
    }
    path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17.5f, 6.5f)
        lineTo(17.51f, 6.5f)
    }
}.build()

val BrandWhatsApp = ImageVector.Builder(
    name = "WhatsApp", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21f, 11.5f)
        curveTo(21.0016f, 13.2089f, 20.4439f, 14.8729f, 19.41f, 16.24f)
        curveTo(18.3761f, 17.607f, 16.9248f, 18.5997f, 15.28f, 19.06f)
        curveTo(13.6353f, 19.5202f, 11.8906f, 19.4223f, 10.3f, 18.78f)
        lineTo(3f, 21f)
        lineTo(5.22f, 13.7f)
        curveTo(4.57774f, 12.1094f, 4.47976f, 10.3647f, 4.94f, 8.72f)
        curveTo(5.40026f, 7.07525f, 6.39296f, 5.62386f, 7.76f, 4.59f)
        curveTo(9.12705f, 3.55611f, 10.7911f, 2.99843f, 12.5f, 3f)
        curveTo(14.7533f, 3.00392f, 16.9134f, 3.89981f, 18.5066f, 5.49341f)
        curveTo(20.0998f, 7.08701f, 20.9961f, 9.24673f, 21f, 11.5f)
        close()
    }
}.build()

val sourceIcons = mapOf(
    "Facebook Ad" to SmartIconData(BrandFacebook, Color(0xFF1877F2)), // Facebook Blue
    "Instagram Ad" to SmartIconData(BrandInstagram, Color(0xFFE1306C)), // Insta Pink
    "Direct Inbound" to SmartIconData(Icons.Outlined.Call, Color(0xFF34A853)), // Green
    "WhatsApp" to SmartIconData(BrandWhatsApp, Color(0xFF25D366)), // WA Green
    "Reference" to SmartIconData(Icons.Outlined.People, Color(0xFFFF9800)), // Orange
    "Other" to SmartIconData(Icons.Outlined.MoreHoriz, Color(0xFF757575)) // Grey
)

val productIcons = mapOf(
    "Spirulina" to SmartIconData(Icons.Outlined.Eco, Color(0xFF2E7D32)),
    "Sea Buckthorn" to SmartIconData(Icons.Outlined.LocalFlorist, Color(0xFFD84315)),
    "Spirulina Face Pack" to SmartIconData(Icons.Outlined.Face, Color(0xFFC2185B)),
    "Spirulina Cookies" to SmartIconData(Icons.Outlined.Cookie, Color(0xFF8D6E63)),
    "Multiple / Combos" to SmartIconData(Icons.Outlined.CardGiftcard, Color(0xFFFBC02D))
)

val statusIcons = mapOf(
    Constants.STATUS_CALL_NOT_ANSWERED to SmartIconData(Icons.Outlined.PhoneMissed, Color(0xFFF43F5E)),
    Constants.STATUS_ORDER_PLACED to SmartIconData(Icons.Outlined.ShoppingCart, Color(0xFF10B981)),
    Constants.STATUS_FOLLOW_UP to SmartIconData(Icons.Outlined.Schedule, Color(0xFFF59E0B)),
    Constants.STATUS_INQUIRY to SmartIconData(Icons.Outlined.Info, Color(0xFF0EA5E9)),
    Constants.STATUS_NOT_INTERESTED to SmartIconData(Icons.Outlined.Cancel, Color(0xFF64748B)),
    Constants.STATUS_INVALID to SmartIconData(Icons.Outlined.Block, Color(0xFF94A3B8)),
    // Legacy mapping fallbacks
    "Invalid/Wrong Number" to SmartIconData(Icons.Outlined.Block, Color(0xFF94A3B8)),
    "Visit Scheduled" to SmartIconData(Icons.Outlined.Schedule, Color(0xFFF59E0B)),
    "Visited" to SmartIconData(Icons.Outlined.ShoppingCart, Color(0xFF10B981)),
    "Converted" to SmartIconData(Icons.Outlined.ShoppingCart, Color(0xFF10B981)),
    "No Answer" to SmartIconData(Icons.Outlined.PhoneMissed, Color(0xFFF43F5E)),
    "Busy" to SmartIconData(Icons.Outlined.PhoneMissed, Color(0xFFF43F5E)),
    "Warm Lead" to SmartIconData(Icons.Outlined.Info, Color(0xFF0EA5E9))
)

@Composable
fun SmartTriggerChip(
    label: String,
    selectedOption: String,
    iconData: SmartIconData?,
    emojiData: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null
) {
    val isSelected = selectedOption.isNotEmpty()
    val borderColor = if (isSelected) (iconData?.tint ?: ModernViolet) else BorderSubtle
    val bgColor = if (isSelected) (iconData?.tint ?: ModernViolet).copy(alpha = 0.06f) else SurfaceLight

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
                else Modifier.drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    )
                }
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSelected && emojiData != null) {
                Text(
                    text = emojiData,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
            } else if (isSelected && iconData != null) {
                Icon(
                    imageVector = iconData.icon,
                    contentDescription = null,
                    tint = iconData.tint,
                    modifier = Modifier.size(20.dp).padding(end = 8.dp)
                )
            } else if (!isSelected) {
                Text("➕ ", fontSize = 13.sp, color = TextSecondary)
            }
            Text(
                text = if (isSelected) selectedOption else label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = if (isSelected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isSelected && onClear != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.08f))
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

data class ParsedProduct(val mainName: String, val badge: String)

fun parseProductNameAndBadge(fullName: String): ParsedProduct {
    val regex = Regex("^(.*?)\\s*\\(([^)]+)\\)\$")
    val match = regex.find(fullName)
    return if (match != null && match.groupValues.size == 3) {
        ParsedProduct(match.groupValues[1].trim(), match.groupValues[2].trim())
    } else {
        ParsedProduct(fullName.trim(), "")
    }
}

fun extractProductFamily(fullName: String): String {
    val regex = Regex("^(.*?)(?:\\s+\\d+)?(?:\\s*\\([^)]+\\))?$")
    val match = regex.find(fullName)
    return match?.groupValues?.get(1)?.trim() ?: fullName
}

fun getDynamicProductColor(productName: String, defaultTint: Color): Color {
    val lower = productName.lowercase()
    return when {
        lower.contains("combo") -> Color(0xFFD97706) // Golden
        lower.contains("tablet") -> Color(0xFF059669) // Emerald
        lower.contains("capsule") -> Color(0xFF2563EB) // Blue
        lower.contains("powder") -> Color(0xFF9333EA) // Purple
        else -> defaultTint
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartGridPopup(
    title: String,
    options: List<String>,
    icons: Map<String, SmartIconData>,
    emojis: Map<String, String>? = null,
    prices: Map<String, Double>? = null,
    columns: Int = 2,
    selectedOption: String = "",
    isMultiSelect: Boolean = false,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val qtyMap = remember(selectedOption) { parseProductQuantities(selectedOption) }
    var searchQuery by remember { mutableStateOf("") }
    val view = LocalView.current
    
    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isBlank()) options else options.filter { it.contains(searchQuery, ignoreCase = true) }
    }
    
    val familyMap = remember(filteredOptions) { 
        filteredOptions.groupBy { extractProductFamily(it) } 
    }
    var expandedFamily by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 650.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(32.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Premium Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color(0xFF1E293B),
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selectedOption.isNotEmpty()) {
                            TextButton(
                                onClick = { 
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelect("") 
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            ) {
                                Text("Clear", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                            }
                        }
                        if (isMultiSelect) {
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(14.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                            ) {
                                Text("Done", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }
                
                // Sleek Frameless Search Bar
                AnimatedVisibility(
                    visible = options.size > 5,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search products...", color = Color(0xFF94A3B8), fontSize = 15.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 10.dp)
                ) {
                    if (isMultiSelect) {
                        val families = familyMap.keys.toList()
                        items(families.size) { index ->
                            val familyName = families[index]
                            val familyVariants = familyMap[familyName] ?: emptyList()
                            val totalFamilyQty = familyVariants.sumOf { qtyMap[it] ?: 0 }
                            val isFamilyExpanded = expandedFamily == familyName
                            
                            val firstVariant = familyVariants.first()
                            val baseColor = getDynamicProductColor(familyName, icons[firstVariant]?.tint ?: Color(0xFF8B5CF6))
                            
                            val animatedBgColor by animateColorAsState(
                                targetValue = if (totalFamilyQty > 0 || isFamilyExpanded) baseColor.copy(alpha = 0.08f) else Color(0xFFF8FAFC),
                                animationSpec = tween(300)
                            )
                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (totalFamilyQty > 0 || isFamilyExpanded) baseColor.copy(alpha = 0.5f) else Color(0xFFE2E8F0),
                                animationSpec = tween(300)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(animatedBgColor)
                                    .border(
                                        width = if (totalFamilyQty > 0 || isFamilyExpanded) 1.5.dp else 1.dp,
                                        color = animatedBorderColor,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            ) {
                                // Family Header Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            if (familyVariants.size > 1) {
                                                expandedFamily = if (isFamilyExpanded) null else familyName
                                            } else {
                                                val option = firstVariant
                                                val currentQty = qtyMap[option] ?: 0
                                                val newMap = qtyMap.toMutableMap()
                                                if (currentQty > 0) {
                                                    newMap.remove(option)
                                                } else {
                                                    newMap[option] = 1
                                                }
                                                onSelect(formatProductQuantities(newMap))
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Squircle Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(baseColor.copy(alpha = 0.08f))
                                                .border(1.dp, baseColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (emojis != null && emojis[firstVariant] != null) {
                                                Text(text = emojis[firstVariant]!!, fontSize = 24.sp)
                                            } else if (icons != null && icons[firstVariant] != null) {
                                                Icon(
                                                    imageVector = icons[firstVariant]!!.icon,
                                                    contentDescription = null,
                                                    tint = baseColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = familyName,
                                                fontSize = 15.sp,
                                                fontWeight = if (totalFamilyQty > 0) FontWeight.ExtraBold else FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (familyVariants.size > 1) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(baseColor.copy(alpha = 0.12f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Customisable",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = baseColor
                                                        )
                                                    }
                                                } else {
                                                    if (prices != null && prices[firstVariant] != null) {
                                                        Text(
                                                            text = "₹${prices[firstVariant]?.toInt() ?: 0}",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Right Side (Button or Total Qty)
                                    if (familyVariants.size > 1) {
                                        if (totalFamilyQty > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(baseColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$totalFamilyQty",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (isFamilyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand",
                                                tint = Color(0xFF94A3B8)
                                            )
                                        }
                                    } else {
                                        // It's a single variant, show the Add/Quantity pill directly
                                        val currentQty = qtyMap[firstVariant] ?: 0
                                        val isOptionSelected = currentQty > 0
                                        AnimatedContent(
                                            targetState = isOptionSelected,
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f) togetherWith
                                                fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.92f)
                                            }, label = "qty_selector"
                                        ) { selected ->
                                            if (selected) {
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(24.dp))
                                                        .background(Color.White)
                                                        .border(1.dp, baseColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(if (currentQty <= 1) Color(0xFFFEF2F2) else Color(0xFFF8FAFC))
                                                            .clickable {
                                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                val newMap = qtyMap.toMutableMap()
                                                                if (currentQty <= 1) {
                                                                    newMap.remove(firstVariant)
                                                                } else {
                                                                    newMap[firstVariant] = currentQty - 1
                                                                }
                                                                onSelect(formatProductQuantities(newMap))
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (currentQty <= 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                                                            contentDescription = "Decrease",
                                                            tint = if (currentQty <= 1) Color(0xFFEF4444) else Color(0xFF334155),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "$currentQty",
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = baseColor,
                                                        modifier = Modifier.widthIn(min = 16.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(baseColor.copy(alpha = 0.1f))
                                                            .clickable {
                                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                val newMap = qtyMap.toMutableMap()
                                                                newMap[firstVariant] = currentQty + 1
                                                                onSelect(formatProductQuantities(newMap))
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Increase",
                                                            tint = baseColor,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(Color(0xFFF8FAFC))
                                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("Add", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Expanded Variants List
                                AnimatedVisibility(
                                    visible = isFamilyExpanded && familyVariants.size > 1,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        familyVariants.forEachIndexed { vIndex, variant ->
                                            val parsed = parseProductNameAndBadge(variant)
                                            val variantQty = qtyMap[variant] ?: 0
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = if (parsed.badge.isNotEmpty()) parsed.badge else "1 Box",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF334155)
                                                    )
                                                    if (prices != null && prices[variant] != null) {
                                                        Text(
                                                            text = "₹${prices[variant]?.toInt() ?: 0}",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                }
                                                
                                                // Variant Quantity Pill
                                                if (variantQty > 0) {
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(24.dp))
                                                            .background(Color.White)
                                                            .border(1.dp, baseColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .clip(CircleShape)
                                                                .background(if (variantQty <= 1) Color(0xFFFEF2F2) else Color(0xFFF8FAFC))
                                                                .clickable {
                                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                    val newMap = qtyMap.toMutableMap()
                                                                    if (variantQty <= 1) {
                                                                        newMap.remove(variant)
                                                                    } else {
                                                                        newMap[variant] = variantQty - 1
                                                                    }
                                                                    onSelect(formatProductQuantities(newMap))
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = if (variantQty <= 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                                                                contentDescription = "Decrease",
                                                                tint = if (variantQty <= 1) Color(0xFFEF4444) else Color(0xFF334155),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = "$variantQty",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = baseColor,
                                                            modifier = Modifier.widthIn(min = 16.dp),
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .clip(CircleShape)
                                                                .background(baseColor.copy(alpha = 0.1f))
                                                                .clickable {
                                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                    val newMap = qtyMap.toMutableMap()
                                                                    newMap[variant] = variantQty + 1
                                                                    onSelect(formatProductQuantities(newMap))
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "Increase",
                                                                tint = baseColor,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(20.dp))
                                                            .background(Color.White)
                                                            .border(1.dp, baseColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                                            .clickable {
                                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                val newMap = qtyMap.toMutableMap()
                                                                newMap[variant] = 1
                                                                onSelect(formatProductQuantities(newMap))
                                                            }
                                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("Add", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = baseColor)
                                                    }
                                                }
                                            }
                                            
                                            if (vIndex < familyVariants.size - 1) {
                                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Single Select Mode (Masonry / Chunked Grid)
                        val chunkedOptions = filteredOptions.chunked(columns)
                        items(chunkedOptions.size) { index ->
                            val rowItems = chunkedOptions[index]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { option ->
                                    val iconData = icons[option]
                                    val isOptionSelected = option.equals(selectedOption, ignoreCase = true)
                                    val baseColor = getDynamicProductColor(option, iconData?.tint ?: Color(0xFF8B5CF6))
                                    
                                    val animatedBgColor by animateColorAsState(
                                        targetValue = if (isOptionSelected) baseColor.copy(alpha = 0.08f) else Color(0xFFF8FAFC),
                                        animationSpec = tween(300)
                                    )
                                    val animatedBorderColor by animateColorAsState(
                                        targetValue = if (isOptionSelected) baseColor.copy(alpha = 0.6f) else Color(0xFFE2E8F0),
                                        animationSpec = tween(300)
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(animatedBgColor)
                                            .border(
                                                width = if (isOptionSelected) 1.5.dp else 1.dp,
                                                color = animatedBorderColor,
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clickable { 
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                if (isOptionSelected) onSelect("") else onSelect(option)
                                                onDismiss() // Automatically dismiss for single select
                                            }
                                            .padding(vertical = 18.dp, horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (emojis != null && emojis[option] != null) {
                                                Text(
                                                    text = emojis[option]!!,
                                                    fontSize = 32.sp
                                                )
                                            } else if (iconData != null) {
                                                Icon(
                                                    imageVector = iconData.icon,
                                                    contentDescription = null,
                                                    tint = baseColor,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            val parsed = parseProductNameAndBadge(option)
                                            Text(
                                                text = parsed.mainName,
                                                fontSize = 14.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = if (isOptionSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (parsed.badge.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(baseColor.copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = parsed.badge,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = baseColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getIconForSelection(selection: String, icons: Map<String, SmartIconData>): SmartIconData? {
    if (selection.isBlank()) return null
    val firstItem = selection.split(",").firstOrNull()?.trim() ?: return null
    val cleanName = firstItem.replace(Regex("\\s*\\(\\d+[xX]\\)\$"), "").trim()
    return icons[cleanName] ?: icons[firstItem]
}

fun getEmojiForSelection(selection: String, emojis: Map<String, String>?): String? {
    if (emojis == null || selection.isBlank()) return null
    val firstItem = selection.split(",").firstOrNull()?.trim() ?: return null
    val cleanName = firstItem.replace(Regex("\\s*\\(\\d+[xX]\\)\$"), "").trim()
    return emojis[cleanName] ?: emojis[firstItem]
}

fun parseProductQuantities(selectedString: String): Map<String, Int> {
    if (selectedString.isBlank()) return emptyMap()
    val map = mutableMapOf<String, Int>()
    val items = selectedString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (item in items) {
        val match = Regex("^(.*?)\\s*\\((\\d+)[xX]\\)\$").find(item)
        if (match != null && match.groupValues.size == 3) {
            val name = match.groupValues[1].trim()
            val qty = match.groupValues[2].toIntOrNull() ?: 1
            map[name] = qty
        } else {
            map[item] = 1
        }
    }
    return map
}

private fun formatProductQuantities(map: Map<String, Int>): String {
    return map.filterValues { it > 0 }
        .map { "${it.key} (${it.value}x)" }
        .joinToString(", ")
}

fun sanitizeSelectedProducts(selectedString: String, masterProducts: List<String>): String {
    if (selectedString.isBlank()) return ""
    val qtyMap = parseProductQuantities(selectedString)
    val newMap = mutableMapOf<String, Int>()
    
    for ((product, qty) in qtyMap) {
        val exactMatch = masterProducts.find { it.equals(product, ignoreCase = true) }
        if (exactMatch != null) {
            newMap[exactMatch] = qty
        } else {
            val fuzzyMatch = masterProducts.find { 
                it.contains(product, ignoreCase = true) || product.contains(it, ignoreCase = true)
            }
            if (fuzzyMatch != null) {
                newMap[fuzzyMatch] = qty
            } else {
                newMap[product] = qty
            }
        }
    }
    return formatProductQuantities(newMap)
}

fun calculateTotalAmount(selectedString: String, prices: Map<String, Double>): Double {
    if (selectedString.isBlank()) return 0.0
    val qtyMap = parseProductQuantities(selectedString)
    var total = 0.0
    for ((product, qty) in qtyMap) {
        val price = prices[product] ?: 0.0
        total += price * qty
    }
    return total
}

fun calculateTotalBottomPrice(selectedString: String, bottomPrices: Map<String, Double>): Double {
    if (selectedString.isBlank()) return 0.0
    val qtyMap = parseProductQuantities(selectedString)
    var total = 0.0
    for ((product, qty) in qtyMap) {
        val price = bottomPrices[product] ?: 0.0
        total += price * qty
    }
    return total
}

fun calculateTotalShippingFee(selectedString: String, shippingFees: Map<String, Double>): Double {
    if (selectedString.isBlank()) return 0.0
    val qtyMap = parseProductQuantities(selectedString)
    var maxFee = 0.0
    for ((product, _) in qtyMap) {
        val fee = shippingFees[product] ?: 0.0
        if (fee > maxFee) {
            maxFee = fee
        }
    }
    return maxFee
}

@Composable
fun SmartGridInline(
    title: String? = null,
    options: List<String>,
    icons: Map<String, SmartIconData>,
    emojis: Map<String, String>? = null,
    prices: Map<String, Double>? = null,
    columns: Int = 2,
    selectedOption: String = "",
    isMultiSelect: Boolean = false,
    onSelect: (String) -> Unit
) {
    val qtyMap = remember(selectedOption) { parseProductQuantities(selectedOption) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (selectedOption.isNotEmpty()) {
                    TextButton(
                        onClick = { onSelect("") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                    ) {
                        Text("Clear", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                    }
                }
            }
        }
        
        if (isMultiSelect) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { option ->
                    val iconData = icons[option]
                    val currentQty = qtyMap[option] ?: 0
                    val isOptionSelected = currentQty > 0
                    val cardTint = iconData?.tint ?: ModernViolet
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isOptionSelected) cardTint.copy(alpha = 0.06f) else SurfaceLight)
                            .border(
                                width = if (isOptionSelected) 1.5.dp else 1.dp,
                                color = if (isOptionSelected) cardTint else BorderSubtle,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { 
                                val newMap = qtyMap.toMutableMap()
                                if (isOptionSelected) {
                                    newMap[option] = currentQty + 1
                                } else {
                                    newMap[option] = 1
                                }
                                onSelect(formatProductQuantities(newMap))
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isOptionSelected) cardTint.copy(alpha = 0.15f) else cardTint.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (emojis != null && emojis[option] != null) {
                                    Text(
                                        text = emojis[option]!!,
                                        fontSize = 20.sp
                                    )
                                } else if (iconData != null) {
                                    Icon(
                                        imageVector = iconData.icon,
                                        contentDescription = null,
                                        tint = cardTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = option,
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = if (isOptionSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (prices != null && prices[option] != null) {
                                    Text(
                                        text = "₹${prices[option]?.toInt() ?: 0}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cardTint
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        if (isOptionSelected) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .border(1.dp, cardTint.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (currentQty <= 1) Color(0xFFF43F5E).copy(alpha = 0.12f) else CleanWhite)
                                        .clickable {
                                            val newMap = qtyMap.toMutableMap()
                                            if (currentQty <= 1) {
                                                newMap.remove(option)
                                            } else {
                                                newMap[option] = currentQty - 1
                                            }
                                            onSelect(formatProductQuantities(newMap))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (currentQty <= 1) Icons.Default.Close else Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        tint = if (currentQty <= 1) Color(0xFFF43F5E) else TextPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Text(
                                    text = "$currentQty",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = cardTint
                                )

                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(cardTint)
                                        .clickable {
                                            val newMap = qtyMap.toMutableMap()
                                            newMap[option] = currentQty + 1
                                            onSelect(formatProductQuantities(newMap))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase",
                                        tint = CleanWhite,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cardTint.copy(alpha = 0.08f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ Add",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardTint
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val chunkedOptions = options.chunked(columns)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                chunkedOptions.forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { option ->
                            val iconData = icons[option]
                            val isOptionSelected = option.equals(selectedOption, ignoreCase = true)
                            val cardTint = iconData?.tint ?: ModernViolet
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isOptionSelected) cardTint.copy(alpha = 0.08f) else SurfaceLight)
                                    .border(
                                        width = if (isOptionSelected) 1.5.dp else 1.dp,
                                        color = if (isOptionSelected) cardTint else BorderSubtle,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { 
                                        if (isOptionSelected) onSelect("") else onSelect(option)
                                    }
                                    .padding(vertical = 16.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (emojis != null && emojis[option] != null) {
                                        Text(
                                            text = emojis[option]!!,
                                            fontSize = 28.sp
                                        )
                                    } else if (iconData != null) {
                                        Icon(
                                            imageVector = iconData.icon,
                                            contentDescription = null,
                                            tint = cardTint,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text(
                                        text = option,
                                        fontSize = 13.sp,
                                        fontWeight = if (isOptionSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
