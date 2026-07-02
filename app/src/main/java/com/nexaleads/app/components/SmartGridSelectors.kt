package com.nexaleads.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
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
    "Call Not Answered" to SmartIconData(Icons.Outlined.PhoneMissed, Color(0xFFD32F2F)),
    "Order Placed" to SmartIconData(Icons.Outlined.ShoppingCart, Color(0xFF388E3C)),
    "Follow-up" to SmartIconData(Icons.Outlined.Schedule, Color(0xFFF57C00)),
    "Product Inquiry Only" to SmartIconData(Icons.Outlined.Info, Color(0xFF1976D2)),
    "Not Interested" to SmartIconData(Icons.Outlined.Cancel, Color(0xFF757575)),
    "Invalid/Wrong Number" to SmartIconData(Icons.Outlined.Block, Color(0xFF9E9E9E))
)

@Composable
fun SmartTriggerChip(
    label: String,
    selectedOption: String,
    iconData: SmartIconData?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            if (isSelected && iconData != null) {
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
        }
    }
}

@Composable
fun SmartGridPopup(
    title: String,
    options: List<String>,
    icons: Map<String, SmartIconData>,
    columns: Int = 2,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceLight)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                
                val chunkedOptions = options.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunkedOptions.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { option ->
                                val iconData = icons[option]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(SurfaceLight)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                        .clickable { onSelect(option) }
                                        .padding(vertical = 16.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (iconData != null) {
                                            Icon(
                                                imageVector = iconData.icon,
                                                contentDescription = null,
                                                tint = iconData.tint,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Text(
                                            text = option,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            // Fill empty space if row has less items than columns
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
