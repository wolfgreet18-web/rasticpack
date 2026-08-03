package com.rasticpack.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas

/**
 * ══ نوار تب اصلی — معادل دقیق .tab-bar / .tab-btn / .tab-btn.active در 4.html ══
 *
 * ظاهر وب:
 * - نوار سفید با یک خط جداکننده‌ی نازک پایین (.tab-bar { border-bottom })
 * - تب غیرفعال: بدون پس‌زمینه، متن خاکستری (--tx2)
 * - تب فعال: پس‌زمینه قرمز برند (--brand)، متن سفید، فقط گوشه‌های بالا گرد (26px 26px 0 0)،
 *   کمی بلندتر از بقیه (padding-top بیشتر) طوری که به‌نظر برسد از نوار «بیرون زده»،
 *   با سایه‌ی ملایم قرمز به سمت بالا (box-shadow: 0 -3px 10px rgba(185,28,28,.22))
 *
 * هفت تب (به همان ترتیب وب): قیمت 💲، فاکتور 🧾، مشتری 👤، ورق (آیکن خطی سفارشی)،
 * تولید 🏭، آمار 📊، تنظیمات ⚙️.
 */

enum class AppTab(val key: String, val emoji: String?) {
    Calc2("calc2", "💲"),
    Invoices("invoices", "🧾"),
    Customers("customers", "👤"),
    Inventory("inventory", null), // آیکن خطی سفارشی — دقیقاً مثل SVG ورق در HTML
    Production("production", "🏭"),
    Stats("stats", "📊"),
    Settings("settings", "⚙️")
}

@Composable
fun AppTabBar(
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceMain),
            verticalAlignment = Alignment.Bottom
        ) {
            AppTab.values().forEach { tab ->
                val isActive = tab.key == current
                TabBarButton(
                    tab = tab,
                    isActive = isActive,
                    onClick = { onSelect(tab.key) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // معادل .tab-bar{border-bottom:1.5px solid var(--border)} در وب —
        // خط جداکننده‌ی نازک زیر کل نوار تب که آن را از محتوای صفحه جدا می‌کند.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(BorderColor)
        )
    }
}

@Composable
private fun TabBarButton(
    tab: AppTab,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topRadius = 26.dp

    Box(
        modifier = modifier
            .then(
                if (isActive) {
                    // تب فعال کمی بالاتر می‌زند (margin-bottom منفی در وب) و سایه‌ی قرمز دارد
                    Modifier
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(topStart = topRadius, topEnd = topRadius),
                            ambientColor = Red700.copy(alpha = 0.22f),
                            spotColor = Red700.copy(alpha = 0.22f)
                        )
                        .clip(RoundedCornerShape(topStart = topRadius, topEnd = topRadius))
                        .background(Red700)
                } else {
                    Modifier.background(Color.Transparent)
                }
            )
            .clickable(onClick = onClick)
            .padding(
                top = if (isActive) 16.dp else 14.dp,
                bottom = if (isActive) 13.dp else 12.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        if (tab.emoji != null) {
            Text(
                text = tab.emoji,
                fontSize = if (isActive) 19.sp else 18.sp,
                color = if (isActive) Color.White else TextSecondary,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        } else {
            // آیکن خطی سفارشی برای «ورق» — دقیقاً معادل SVG rect+lines در HTML
            InventoryTabIcon(active = isActive)
        }
    }
}

/**
 * معادل SVG داخل تب «ورق» در HTML:
 * <rect x="4" y="3" width="15" height="18" rx="1.6"/>
 * <path d="M4 8.2h15M4 13h15M4 17.8h15"/>
 * یعنی یک برگه با سه خط افقی داخلش (خطوط سند/ورق).
 */
@Composable
private fun InventoryTabIcon(active: Boolean) {
    val color = if (active) Color.White else TextSecondary
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = w / 13f // ~1.7 در viewBox 24x24

        // مستطیل ورق با گوشه‌های گرد (rx نسبی از 24x24 viewBox: x4 y3 w15 h18 rx1.6)
        val rectLeft = w * (4f / 24f)
        val rectTop = h * (3f / 24f)
        val rectW = w * (15f / 24f)
        val rectH = h * (18f / 24f)
        val corner = w * (1.6f / 24f)

        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(rectLeft, rectTop),
            size = Size(rectW, rectH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            style = Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        val lineStartX = w * (4f / 24f)
        val lineEndX = w * (19f / 24f)
        listOf(8.2f, 13f, 17.8f).forEach { yUnits ->
            val y = h * (yUnits / 24f)
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(lineStartX, y),
                end = androidx.compose.ui.geometry.Offset(lineEndX, y),
                strokeWidth = strokeW,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
