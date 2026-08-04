package com.rasticpack.app.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceMain
import com.rasticpack.app.ui.theme.TextSecondary

/*
 ══════════════════════════════════════════════════════════════════════════
  TopTabBar — معادل دقیق نوار تب بالای صفحه در 4.html:

      <nav class="tab-bar" role="tablist">
        <button class="tab-btn active" title="قیمت">💲</button>
        <button class="tab-btn" title="فاکتور">🧾</button>
        <button class="tab-btn" title="مشتری">👤</button>
        <button class="tab-btn" title="ورق"><svg ...></button>
        <button class="tab-btn" title="تولید">🏭</button>
        <button class="tab-btn" title="آمار">📊</button>
        <button class="tab-btn" title="تنظیمات">⚙️</button>
      </nav>

  این کامپوننت باید داخل همان کارت سفید گرد و سایه‌دار اصلی صفحه (معادل main.card
  در وب: پس‌زمینه‌ی سفید، رادیوس ۲۴dp، سایه‌ی نرم) و در بالاترین نقطه‌ی آن —
  دقیقاً بالای محتوای هر تب — قرار بگیرد. این جایگزینِ آن ۷ دکمه‌ی قرمز بزرگ
  عمودی است که الان در صفحه‌ی داشبورد/خانه دیده می‌شود، نه یک BottomNavigation
  و نه یک صفحه‌ی جدا.

  نکته‌ی جهت (RTL): در html چون <html dir="rtl"> است، اولین دکمه (💲 محاسبه)
  سمت راست‌ترین قرار می‌گیرد. اینجا فرض شده که همان‌طور که در بقیه‌ی پروژه
  (مثلاً Calc2Screen.kt — ردیف شماره‌گذاری کارت با Arrangement.End «سمت راست»
  چیده می‌شود) جهت‌گیری کلی Compose هم از قبل RTL تنظیم شده، پس یک Row معمولی
  با ترتیب زیر (از calc2 تا settings) خودش از راست شروع می‌شود — دقیقاً مثل html.
  اگر بعداً معلوم شد جهت‌گیری کلی LTR است، کافی‌ست همین لیست AppTab.values()
  را با .reversed() صدا بزنید یا کل Row را داخل
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)
  بگذارید.
 ══════════════════════════════════════════════════════════════════════════
*/

/**
 * هفت تب برنامه — دقیقاً به همان ترتیب و همان معادل‌های onclick="switchTab('...')" در وب.
 * هر مقدار enum را به اسکرین واقعی خودش وصل کنید (مثلاً CALC2 → Calc2Screen).
 */
enum class AppTab {
    CALC2,       // 💲 قیمت  → معادل panel-calc2 / switchTab('calc2')
    INVOICES,    // 🧾 فاکتور → معادل panel-invoices
    CUSTOMERS,   // 👤 مشتری → معادل panel-customers
    INVENTORY,   // 📋 ورق   → معادل panel-inventory (آیکن SVG اختصاصی، نه ایموجی)
    PRODUCTION,  // 🏭 تولید → معادل panel-production
    STATS,       // 📊 آمار  → معادل panel-stats
    SETTINGS     // ⚙️ تنظیمات → معادل panel-settings
}

/** برچسب فارسی هر تب — دقیقاً همان مقدار title="..." در دکمه‌ی html (فقط برای contentDescription/tooltip، نه متن روی دکمه). */
private fun AppTab.titleFa(): String = when (this) {
    AppTab.CALC2 -> "قیمت"
    AppTab.INVOICES -> "فاکتور"
    AppTab.CUSTOMERS -> "مشتری"
    AppTab.INVENTORY -> "ورق"
    AppTab.PRODUCTION -> "تولید"
    AppTab.STATS -> "آمار"
    AppTab.SETTINGS -> "تنظیمات"
}

/**
 * نوار تب بالای صفحه. مصرف‌کننده باید state انتخاب تب را نگه دارد و روی onSelect
 * محتوای زیر نوار را عوض کند (دقیقاً معادل switchTab(name,btn) در وب که panel فعال را
 * toggle می‌کند). این کامپوننت خودش هیچ محتوایی رندر نمی‌کند — فقط خود نوار تب است.
 */
@Composable
fun TopTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // ══ خط جداکننده‌ی پایین نوار — معادل border-bottom:1.5px solid var(--border) در .tab-bar.
        // زیر همه‌ی تب‌ها کشیده می‌شود؛ زیر تب فعال به‌طور طبیعی با پس‌زمینه‌ی قرمزش پوشانده
        // می‌شود (چون تب فعال با پدینگ بیشتر و رادیوس بالا کمی پایین‌تر می‌آید) — همان جلوه‌ی
        // margin-bottom:-1.5px در وب. ══
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(BorderColor)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceMain)
        ) {
            AppTab.values().forEach { tab ->
                TabBarItem(
                    tab = tab,
                    isActive = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabBarItem(
    tab: AppTab,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ══ معادل .tab-btn.active: پس‌زمینه‌ی قرمز برند (--brand)، رادیوس فقط بالا (26px→26dp)،
    // پدینگ کمی بیشتر از بالا (16dp/13dp به‌جای 14dp/12dp)، سایه‌ی رو به بالا
    // (0 -3px 10px rgba(185,28,28,.22)) — چون Modifier.shadow استاندارد کامپوز فقط سایه‌ی
    // رو به پایین دارد، اینجا با یک shadow معمولی تقریب زده شده؛ برای تطبیق ۱۰۰٪ پیکسلی
    // بعداً می‌شود یک drawBehind سفارشی برای سایه‌ی معکوس اضافه کرد. ══
    val shape = if (isActive) {
        RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(0.dp)
    }

    Box(
        modifier = modifier
            .then(
                if (isActive) Modifier.shadow(elevation = 5.dp, shape = shape, clip = false)
                else Modifier
            )
            .background(color = if (isActive) Red700 else Color.Transparent, shape = shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = tab.titleFa() }
            .padding(
                top = if (isActive) 16.dp else 14.dp,
                bottom = if (isActive) 13.dp else 12.dp,
                start = 2.dp,
                end = 2.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        TabIcon(tab = tab, tint = if (isActive) Color.White else TextSecondary)
    }
}

/** آیکن هر تب — برای ۶ تب همان ایموجی دقیق html، برای «ورق» همان SVG اختصاصی (نه ایموجی). */
@Composable
private fun TabIcon(tab: AppTab, tint: Color) {
    when (tab) {
        AppTab.CALC2 -> Text("💲", fontSize = 20.sp)
        AppTab.INVOICES -> Text("🧾", fontSize = 20.sp)
        AppTab.CUSTOMERS -> Text("👤", fontSize = 20.sp)
        AppTab.INVENTORY -> InventoryTabIcon(tint = tint)
        AppTab.PRODUCTION -> Text("🏭", fontSize = 20.sp)
        AppTab.STATS -> Text("📊", fontSize = 20.sp)
        AppTab.SETTINGS -> Text("⚙️", fontSize = 20.sp)
    }
    // توجه: در خود html هم رنگ‌شدن به‌هنگام فعال‌شدن فقط برای آیکن SVG (ورق) با
    // stroke="currentColor" اتفاق می‌افتد؛ ایموجی‌ها چون گلیف‌های رنگیِ از پیش‌تعیین‌شده‌اند
    // اصلاً رنگشان با CSS عوض نمی‌شود — پس اینجا هم tint فقط روی InventoryTabIcon اثر دارد
    // و برای بقیه بی‌اثر است؛ این عمداً همان رفتار وب است، نه یک محدودیت این پیاده‌سازی.
}

/**
 * بازسازی پیکسلی آیکن SVG تب «ورق» در html:
 * <svg viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.7">
 *   <rect x="4" y="3" width="15" height="18" rx="1.6"/>
 *   <path d="M4 8.2h15M4 13h15M4 17.8h15"/>
 * </svg>
 */
@Composable
private fun InventoryTabIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val scale = this.size.width / 24f
        val strokeWidthPx = 1.7f * scale
        val cornerPx = 1.6f * scale

        drawRoundRect(
            color = tint,
            topLeft = Offset(4f * scale, 3f * scale),
            size = Size(15f * scale, 18f * scale),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        listOf(8.2f, 13f, 17.8f).forEach { y ->
            drawLine(
                color = tint,
                start = Offset(4f * scale, y * scale),
                end = Offset(19f * scale, y * scale),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

/*
 ══ نحوه‌ی استفاده (نمونه) ══

 @Composable
 fun MainCard() {
     var selectedTab by remember { mutableStateOf(AppTab.CALC2) }

     // معادل main.card در وب: کارت سفید گرد با سایه که هم نوار تب هم محتوا داخلش است
     Card(
         shape = RoundedCornerShape(24.dp),
         colors = CardDefaults.cardColors(containerColor = SurfaceMain)
     ) {
         Column {
             TopTabBar(selected = selectedTab, onSelect = { selectedTab = it })

             // معادل .tab-panel.active — فقط محتوای تب انتخاب‌شده رندر می‌شود
             when (selectedTab) {
                 AppTab.CALC2 -> Calc2Screen(onBack = { })
                 AppTab.INVOICES -> InvoicesScreen(...)
                 AppTab.CUSTOMERS -> CustomersScreen(...)
                 AppTab.INVENTORY -> InventoryScreen(...)
                 AppTab.PRODUCTION -> ProductionScreen(...)
                 AppTab.STATS -> StatsScreen(...)
                 AppTab.SETTINGS -> SettingsScreen(...)
             }
         }
     }
 }
*/
