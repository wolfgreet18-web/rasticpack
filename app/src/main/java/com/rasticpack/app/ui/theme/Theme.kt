package com.rasticpack.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// پس‌زمینه‌ی کلی صفحات — همون #f2efe8 که در body فایل HTML اصلی استفاده شده
private val Color_App_Background = androidx.compose.ui.graphics.Color(0xFFF2EFE8)

private val RasticPackColorScheme = lightColorScheme(
    primary = Red700,
    onPrimary = SurfaceMain,
    secondary = Gold,
    onSecondary = SurfaceMain,
    background = Color_App_Background,
    onBackground = TextPrimary,
    surface = SurfaceMain,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = Red600,
    onError = SurfaceMain
)

@Composable
fun RasticPackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RasticPackColorScheme,
        typography = Typography,
        content = content
    )
}
