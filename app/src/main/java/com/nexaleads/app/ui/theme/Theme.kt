package com.nexaleads.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nexaleads.app.Constants

private val LightColorScheme = lightColorScheme(
    primary = ModernViolet,
    secondary = TextSecondary,
    tertiary = BorderSubtle,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = CleanWhite,
    onSecondary = CleanWhite,
    onTertiary = CleanWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = AccentSurface,
    onSurfaceVariant = TextSecondary,
    error = StatusDanger
)

@Composable
fun LeadFlowSaaSTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = BackgroundLight.value.toInt()
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val indianStatusLabels = mapOf(
    Constants.STATUS_FOLLOW_UP to "Follow-up",
    Constants.STATUS_ORDER_PLACED to "Order Placed",
    Constants.STATUS_CALL_NOT_ANSWERED to "No Answer",
    Constants.STATUS_INQUIRY to "Product Inquiry",
    Constants.STATUS_NOT_INTERESTED to "Not Interested",
    Constants.STATUS_INVALID to "Invalid No.",
    // Legacy mapping fallbacks
    "Visit Scheduled" to "Follow-up",
    "Visited" to "Order Placed",
    "Converted" to "Order Placed",
    "No Answer" to "No Answer",
    "Busy" to "No Answer",
    "Warm Lead" to "Product Inquiry"
)

val statusColors = mapOf(
    Constants.STATUS_FOLLOW_UP to StatusWarning,
    Constants.STATUS_ORDER_PLACED to StatusSuccess,
    Constants.STATUS_CALL_NOT_ANSWERED to Color(0xFFF43F5E), // Rose Red
    Constants.STATUS_INQUIRY to Color(0xFF0EA5E9), // Sky Blue
    Constants.STATUS_NOT_INTERESTED to StatusDanger,
    Constants.STATUS_INVALID to Color(0xFF94A3B8), // Muted Slate
    // Legacy mapping fallbacks
    "Visit Scheduled" to Color(0xFFF59E0B),
    "Visited" to Color(0xFF10B981),
    "Converted" to StatusSuccess,
    "No Answer" to StatusNeutral,
    "Busy" to StatusBusy,
    "Warm Lead" to Color(0xFF0EA5E9)
)
