package com.rasticpack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.repo.PricingRepository
import com.rasticpack.app.ui.calc.Calc2Screen
import com.rasticpack.app.ui.customers.CustomersScreen
import com.rasticpack.app.ui.database.DatabaseTestScreen
import com.rasticpack.app.ui.engine.EngineTestScreen
import com.rasticpack.app.ui.inventory.InventoryScreen
import com.rasticpack.app.ui.invoices.InvoicesScreen
import com.rasticpack.app.ui.production.ProductionScreen
import com.rasticpack.app.ui.settings.SettingsScreen
import com.rasticpack.app.ui.stats.StatsScreen
import com.rasticpack.app.ui.theme.AppDisplaySettings
import com.rasticpack.app.ui.theme.AppTabBar
import com.rasticpack.app.ui.theme.ClickSound
import com.rasticpack.app.ui.theme.RasticPackTheme
import com.rasticpack.app.ui.theme.ResponsiveContentWidth
import kotlin.math.abs

/**
 * ══ زیرمرحله ۱۱.۱ — ترتیب تب‌های اصلی برای سوایپ ══
 * معادل دقیق tabOrder در وب (بخش «سوایپ برای تعویض تب‌ها»). صفحه‌ی «home» (خوش‌آمدگویی)
 * و صفحات تست (engineTest/databaseTest) در این چرخه نیستند — دقیقاً مثل وب که فقط ۷ تب
 * اصلی نوار تب سوایپ می‌شدند.
 */
private val SWIPE_TAB_ORDER = listOf(
    "calc2", "invoices", "customers", "inventory", "production", "stats", "settings"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val db = remember { AppDatabase.getInstance(context) }
            val pricingRepo = remember { PricingRepository(db) }

            // ══ زیرمرحله ۱۱.۱ — بارگذاری تنظیمات نمایشی از دیتابیس و اعمال سراسری ══
            var displaySettings by remember { mutableStateOf(AppDisplaySettings()) }
            var settingsReloadTick by remember { mutableStateOf(0) }
            LaunchedEffect(settingsReloadTick) {
                val disp = pricingRepo.getDisplaySettings()
                displaySettings = AppDisplaySettings(
                    fontScale = disp.fontScale,
                    zoom = disp.zoom,
                    soundEnabled = disp.soundEnabled
                )
            }

            RasticPackTheme(displaySettings = displaySettings) {
                // کل اپ راست‌به‌چپ (فارسی) نمایش داده می‌شود
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    // ══ صفحه‌ی پیش‌فرض همان تب اول نوار تب‌هاست (💲 محاسبه کارتن) —
                    // دقیقاً مثل وب که class="tab-btn active" روی اولین دکمه (calc2) است. ══
                    var screen by remember { mutableStateOf("calc2") }
                    var productionApplyId by remember { mutableStateOf<Int?>(null) }

                    // ══ زیرمرحله ۱۱.۱ — سوایپ برای تعویض تب‌ها ══
                    // معادل دقیق منطق threshold/restraint/maxTime در وب: فقط وقتی صفحه‌ی فعلی
                    // یکی از ۷ تب اصلی باشد سوایپ فعال است؛ جابجایی افقی به‌اندازه کافی بزرگ‌تر
                    // از جابجایی عمودی باشد تا با اسکرول عمودی تداخل نکند.
                    val swipeModifier = Modifier.pointerInput(screen) {
                        val idx = SWIPE_TAB_ORDER.indexOf(screen)
                        if (idx == -1) return@pointerInput
                        var totalDx = 0f
                        var totalDy = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDx = 0f; totalDy = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDx += dragAmount
                                totalDy += change.positionChange().y
                            },
                            onDragEnd = {
                                val THRESHOLD = 90f
                                val RESTRAINT = 260f
                                if (abs(totalDx) >= THRESHOLD && abs(totalDy) <= RESTRAINT) {
                                    val curIdx = SWIPE_TAB_ORDER.indexOf(screen)
                                    if (curIdx != -1) {
                                        // dx<0 یعنی سوایپ به چپ → تب بعدی؛ dx>0 یعنی به راست → تب قبلی
                                        // (هم‌راستا با چیدمان RTL و منطق شرط dx<0 در وب)
                                        if (totalDx < 0) {
                                            SWIPE_TAB_ORDER.getOrNull(curIdx - 1)?.let { screen = it }
                                        } else {
                                            SWIPE_TAB_ORDER.getOrNull(curIdx + 1)?.let { screen = it }
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // ══ زیرمرحله ۱۱.۳ — صدای کلیک دکمه‌ها (سراسری) ══
                    // معادل دقیق document.addEventListener('click', ..., true) در وب: با گوش‌دادن
                    // در فاز اولیه/capture (PointerEventPass.Initial)، هر لمسِ پایین‌رفتن انگشت
                    // (down) در هر نقطه از درخت Compose — قبل از این‌که هر دکمه/کنترل داخلی خودش
                    // آن را مصرف کند — شناسایی و صدا پخش می‌شود؛ دقیقاً مثل رفتار وب که با کلیک
                    // روی هر button, .btn, .tab-btn, .subtype-btn, .bttab, .mt, .rank-badge,
                    // .inv-card-header, .stock-badge صدا پخش می‌کرد، بدون نیاز به افزودن دستی
                    // این منطق به تک‌تک صدها onClick سراسر اپ.
                    val clickSoundModifier = Modifier.pointerInput(displaySettings.soundEnabled) {
                        if (!displaySettings.soundEnabled) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            ClickSound.play()
                        }
                    }

                    // ══ زیرمرحله ۱۱.۱ — اعمال «اندازه کل نرم‌افزار» (زوم) روی کل درخت ══
                    // معادل body{zoom:var(--app-zoom)} در وب.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(clickSoundModifier)
                            .then(if (SWIPE_TAB_ORDER.contains(screen)) swipeModifier else Modifier)
                            .graphicsLayer {
                                val z = displaySettings.zoomFactor
                                scaleX = z
                                scaleY = z
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                    ) {
                        // ══ زیرمرحله ۱۱.۴.۱ — محدود کردن عرض محتوا روی صفحه‌ی بزرگ/تبلت ══
                        // معادل دقیق .app-wrapper{max-width:900px;margin:0 auto} در وب.
                        // روی گوشی معمولی (عرض صفحه ≤ ۹۰۰dp) هیچ تفاوتی حس نمی‌شود.
                        ResponsiveContentWidth {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // ══ نوار تب اصلی — همیشه بالای صفحه، دقیقاً مثل .tab-bar در وب،
                            // فقط برای ۷ تب اصلی نمایش داده می‌شود (نه در صفحات تست). ══
                            if (SWIPE_TAB_ORDER.contains(screen)) {
                                AppTabBar(
                                    current = screen,
                                    onSelect = { tabKey ->
                                        if (tabKey == "production") productionApplyId = null
                                        screen = tabKey
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                when (screen) {
                                    "engineTest" -> EngineTestScreen(onBack = { screen = "calc2" })
                                    "databaseTest" -> DatabaseTestScreen(onBack = { screen = "calc2" })
                                    "calc2" -> Calc2Screen(onBack = { screen = "calc2" })
                                    "inventory" -> InventoryScreen(onBack = { screen = "calc2" })
                                    "customers" -> CustomersScreen(onBack = { screen = "calc2" })
                                    "invoices" -> InvoicesScreen(
                                        onBack = { screen = "calc2" },
                                        onNavigateToProduction = { firstId ->
                                            productionApplyId = firstId
                                            screen = "production"
                                        }
                                    )
                                    "production" -> ProductionScreen(
                                        onBack = { screen = "calc2" },
                                        initialApplyId = productionApplyId
                                    )
                                    "stats" -> StatsScreen(onBack = { screen = "calc2" })
                                    "settings" -> SettingsScreen(onBack = {
                                        // با خروج از تنظیمات، تنظیمات نمایشی دوباره از دیتابیس خوانده می‌شود
                                        // تا تغییرات فونت/زوم/صدا بلافاصله روی کل اپ اعمال شوند.
                                        settingsReloadTick++
                                        screen = "calc2"
                                    })
                                    else -> Calc2Screen(onBack = { screen = "calc2" })
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ══ زیرمرحله ۱۱.۳ — آزادسازی منابع AudioTrack هنگام بسته‌شدن کامل اپ ══
        ClickSound.release()
    }
}

@Composable
fun WelcomeScreen(
    onOpenEngineTest: () -> Unit,
    onOpenDatabaseTest: () -> Unit,
    onOpenCalc2: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenInvoices: () -> Unit,
    onOpenProduction: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // ══ زیرمرحله ۱۱.۴.۳ — چرخش افقی (landscape) ══
        // قبلاً این ستون با verticalArrangement=Center و بدون اسکرول بود؛ روی صفحه‌ی
        // افقیِ کوتاه (مثل گوشی در حالت landscape) ارتفاع محتوا از ارتفاع صفحه بیشتر
        // می‌شد و پایین لیست (دکمه‌های تب‌ها) بریده/غیرقابل‌دسترس می‌شد. اضافه‌کردن
        // verticalScroll باعث می‌شود در چنین حالتی محتوا با اسکرول عمودی کامل در
        // دسترس بماند — دقیقاً همان راه‌حلی که در وب صفحه با overflow طبیعی مرورگر
        // اسکرول می‌شد. روی گوشی در حالت عمودی معمولی (که جا برای همه چیز هست)
        // هیچ تغییر ظاهری حس نمی‌شود چون محتوا از ارتفاع صفحه کوچک‌تر است.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // لوگوی ساده — همون سه‌فلش برند، به‌صورت متن جایگزین موقت
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "رستیک پک",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "نرم‌افزار مدیریت تولید و فروش کارتن",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "✅ مرحله ۰ تا ۱۱.۴.۲   ·   🟡 مرحله ۱۱.۴.۳ — چرخش افقی (landscape)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "بازبینی چرخش افقی انجام شد: چون همه‌ی ۷ تب اصلی (قیمت، فاکتور، مشتری، " +
                            "ورق، تولید، آمار، تنظیمات) از قبل با LazyColumn یا verticalScroll ساخته " +
                            "شده بودند، در حالت افقی هم محتوایشان به‌صورت خودکار قابل‌اسکرول می‌ماند و " +
                            "نیازی به تغییر نداشتند. تنها نقطه‌ی خطر واقعی همین صفحه‌ی خوش‌آمدگویی بود " +
                            "که با verticalArrangement=Center و بدون اسکرول نوشته شده بود — روی گوشی " +
                            "در حالت افقی (که ارتفاع صفحه کم است)، پایین لیست دکمه‌ها ممکن بود بریده " +
                            "شود. حالا این صفحه هم با اسکرول عمودی قابل‌دسترسی کامل است. مانیفست اپ از " +
                            "قبل هیچ قفلی روی جهت صفحه نداشت، پس چرخش گوشی همیشه توسط سیستم مجاز بوده. " +
                            "صدای کلیک، فونت وزیرمتن، اسکرول افقی دکمه‌های C/E/2T/KT، سوایپ/زوم و " +
                            "پشتیبانی تبلت (۱۱.۱ تا ۱۱.۴.۲) هم همچنان فعال هستند.",
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("⚙️ تب تنظیمات (کامل شد — شامل بکاپ/بازیابی)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) {
                Text("📊 تب آمار سود (مرحله ۹)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenInvoices, modifier = Modifier.fillMaxWidth()) {
                Text("🧾 تب فاکتورها (پیامک/PDF — مرحله ۷)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenProduction, modifier = Modifier.fillMaxWidth()) {
                Text("🏭 تب مراحل تولید (کامل شد — با رسم بلانک)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenCustomers, modifier = Modifier.fillMaxWidth()) {
                Text("👤 تب مشتری‌ها (مرحله ۵)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenInventory, modifier = Modifier.fillMaxWidth()) {
                Text("📋 تب موجودی ورق (مرحله ۴)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenCalc2, modifier = Modifier.fillMaxWidth()) {
                Text("💲 تب محاسبه کارتن (مرحله ۳)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenEngineTest, modifier = Modifier.fillMaxWidth()) {
                Text("🧮 تست موتور محاسبه")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onOpenDatabaseTest, modifier = Modifier.fillMaxWidth()) {
                Text("🗄️ تست دیتابیس (۱۰٬۰۰۰ فاکتور)")
            }
        }
    }
}
