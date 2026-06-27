package com.enterprise.leadflow.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ModernViolet,
    secondary = TextSecondary,
    tertiary = BorderSubtle,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = CleanWhite,
    onSecondary = CleanWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = AccentSurface,
    onSurfaceVariant = TextSecondary
)

@Composable
fun LeadFlowCRMTheme(
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
val indianStatusLabels = mapOf("Follow-up" to "Follow-up", "Visit Scheduled" to "Visit Scheduled", "Visited" to "Visited (Actual)", "Converted" to "Converted", "No Answer" to "No Answer", "Busy" to "Busy / Cut", "Warm Lead" to "Warm / On Hold", "Not Interested" to "Not Interested", "Invalid" to "Invalid No.")

val statusColors = mapOf("Follow-up" to StatusWarning, "Visit Scheduled" to Color(0xFFF59E0B), "Visited" to Color(0xFF10B981), "Converted" to StatusSuccess, "No Answer" to StatusNeutral, "Busy" to StatusBusy, "Warm Lead" to Color(0xFF0EA5E9), "Not Interested" to StatusDanger, "Invalid" to Color(0xFF94A3B8))
