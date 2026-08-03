package com.rasticpack.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * ══ زیرمرحله ۱۱.۴.۱ — پشتیبانی از صفحه‌ی بزرگ/تبلت ══
 * معادل دقیق .app-wrapper{max-width:900px;margin:0 auto} در وب (فایل ۴.html):
 * روی گوشی‌های معمولی هیچ تغییری حس نمی‌شود (چون عرض صفحه معمولاً از ۹۰۰dp کمتر
 * است و max-width اصلاً فعال نمی‌شود)، اما روی تبلت یا گوشی‌های تا‌شو/بزرگ، محتوا
 * به‌جای کش‌آمدن روی کل عرض، در وسط صفحه با عرضی محدود (حداکثر ۹۰۰dp) نمایش داده
 * می‌شود — دقیقاً همان رفتار نسخه‌ی وب در مرورگر دسکتاپ/تبلت.
 *
 * این یک Composable «wrapper» ساده است، نه CompositionLocal — یک‌بار در MainActivity
 * دور سوییچ صفحات (when(screen)) صدا زده می‌شود، پس هیچ‌کدام از فایل‌های Screen موجود
 * (Calc2Screen, InventoryScreen, ...) نیازی به تغییر ندارند.
 */
private val MAX_CONTENT_WIDTH_DP = 900

@Composable
fun ResponsiveContentWidth(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    if (screenWidthDp <= MAX_CONTENT_WIDTH_DP) {
        // گوشی معمولی — بدون تغییر، دقیقاً مثل قبل از این زیرمرحله
        Box(modifier = modifier.fillMaxSize()) { content() }
    } else {
        // تبلت/صفحه بزرگ — محتوا در وسط با عرض محدود، دقیقاً مثل margin:0 auto در وب
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH_DP.dp)
                    .fillMaxHeight()
            ) {
                content()
            }
        }
    }
}
