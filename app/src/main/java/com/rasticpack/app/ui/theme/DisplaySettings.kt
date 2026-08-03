package com.rasticpack.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ══ زیرمرحله ۱۱.۱ — اعمال واقعی «تنظیمات نمایشی» روی کل اپ ══
 * معادل دقیق --font-scale و --app-zoom در 4.html (که در ۱۰.۴ فقط ذخیره می‌شدند، ولی
 * روی هیچ‌جای UI اعمال نمی‌شدند). این دو مقدار از طریق CompositionLocal در دسترس کل
 * درخت Compose قرار می‌گیرند تا:
 *   - fontScale روی چگالی فونت (Density.fontScale) اعمال شود — دقیقاً مثل
 *     `html{font-size:calc(16px * var(--font-scale))}` در وب، که همه‌ی واحدهای sp را
 *     نسبت به این مقیاس تغییر می‌دهد.
 *   - zoom روی کل درخت با Modifier.scale() اعمال می‌شود — معادل `body{zoom:var(--app-zoom)}`.
 */
@Immutable
data class AppDisplaySettings(
    val fontScale: Int = 100,
    val zoom: Int = 85,
    val soundEnabled: Boolean = true
) {
    val fontScaleFactor: Float get() = fontScale / 100f
    val zoomFactor: Float get() = zoom / 100f
}

val LocalAppDisplaySettings = staticCompositionLocalOf { AppDisplaySettings() }
