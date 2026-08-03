package com.rasticpack.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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
fun RasticPackTheme(
    displaySettings: AppDisplaySettings = AppDisplaySettings(),
    content: @Composable () -> Unit
) {
    // ══ زیرمرحله ۱۱.۱ — اعمال fontScale روی Density سراسری ══
    // معادل html{font-size:calc(16px*var(--font-scale))} در وب: تمام واحدهای sp در کل
    // درخت Compose با همین ضریب مقیاس می‌شوند، بدون نیاز به دست‌کاری تک‌تک Textها.
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, displaySettings.fontScaleFactor) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * displaySettings.fontScaleFactor
        )
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAppDisplaySettings provides displaySettings
    ) {
        MaterialTheme(
            colorScheme = RasticPackColorScheme,
            typography = Typography,
            content = content
        )
    }
}
