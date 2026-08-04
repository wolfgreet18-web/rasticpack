package com.rasticpack.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasticpack.app.ui.calc.Calc2Screen
import com.rasticpack.app.ui.nav.AppTab
import com.rasticpack.app.ui.nav.TopTabBar
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.SurfaceMain
import com.rasticpack.app.ui.theme.TextMuted

/*
 ══════════════════════════════════════════════════════════════════════════
  RasticPackApp — معادل دقیق ساختار زیر در 4.html:

      <div class="app-wrapper">          → Column با پس‌زمینه‌ی #f2efe8 (رنگ body)
        <main class="card">              → Card سفید گرد ۲۴dp با بردر و سایه‌ی نرم
          <nav class="tab-bar">...</nav> → TopTabBar (ساخته‌شده در پیام قبل)
          <section class="tab-panel active">...</section>  → محتوای تب انتخاب‌شده
        </main>
      </div>

  ⚠️ فرض‌های این فایل (چون دسترسی به فایل واقعی داشبورد/Navigation شما نداشتم):
  - این فایل باید *جایگزینِ* هر Composable ای شود که الان آن هفت دکمه‌ی قرمز
    عمودی (تنظیمات/آمار/فاکتورها/تولید/مشتری‌ها/موجودی/محاسبه) را می‌سازد.
    یعنی هرجا آن صفحه در MainActivity یا NavHost شما صدا زده می‌شود،
    به‌جایش RasticPackApp() را صدا بزنید.
  - فقط تب «محاسبه کارتن» به اسکرین واقعی (Calc2Screen) وصل است، چون تنها
    فایلی بود که برایم فرستاده شد. شش تب دیگر فعلاً TabPlaceholder نشان
    می‌دهند — به‌محض این‌که فایل Kotlin هرکدام را بفرستید، همان‌جا با
    Composable واقعی‌شان جایگزین می‌کنم.
  - اگر پکیج/نام Composable های شما (مثلاً InvoicesScreen، CustomersScreen)
    از قبل با نام دیگری وجود دارد، فقط کافی‌ست در when زیر جایگزین کنید —
    ساختار TopTabBar و کارت اصلی دست‌نخورده می‌ماند.
 ══════════════════════════════════════════════════════════════════════════
*/

/** رنگ پس‌زمینه‌ی کل صفحه — دقیقاً معادل body{background:#f2efe8} در وب. */
private val PageBackground = Color(0xFFF2EFE8)

@Composable
fun RasticPackApp() {
    var selectedTab by remember { mutableStateOf(AppTab.CALC2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            // معادل .app-wrapper{padding:20px 16px 40px} — تقریب با پدینگ یکنواخت
            .padding(16.dp)
    ) {
        // معادل main.card: پس‌زمینه‌ی سفید، رادیوس var(--r-xl)=24px، بردر ۱px و سایه‌ی نرم
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceMain),
            border = BorderStroke(1.dp, BorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column {
                // ══ نوار تب بالای کارت — همان درخواستِ «مرحله‌ی اول» ══
                TopTabBar(selected = selectedTab, onSelect = { selectedTab = it })

                // معادل .tab-panel{padding:26px} — پدینگ محتوای هر تب
                Column(modifier = Modifier.padding(20.dp)) {
                    when (selectedTab) {
                        AppTab.CALC2 -> Calc2Screen(onBack = {})
                        AppTab.INVOICES -> TabPlaceholder("فاکتورها")
                        AppTab.CUSTOMERS -> TabPlaceholder("مشتری‌ها")
                        AppTab.INVENTORY -> TabPlaceholder("موجودی ورق")
                        AppTab.PRODUCTION -> TabPlaceholder("مراحل تولید")
                        AppTab.STATS -> TabPlaceholder("آمار سود")
                        AppTab.SETTINGS -> TabPlaceholder("تنظیمات")
                    }
                }
            }
        }
    }
}

/** جای‌گیرِ موقت برای تب‌هایی که هنوز فایل Kotlin‌شان به من نرسیده. */
@Composable
private fun TabPlaceholder(name: String) {
    Text(
        text = "تب «$name» — این بخش هنوز به RasticPackApp وصل نشده.\nفایل Kotlin این تب را بفرستید تا اینجا جایگزین شود.",
        fontSize = 13.sp,
        color = TextMuted
    )
}
