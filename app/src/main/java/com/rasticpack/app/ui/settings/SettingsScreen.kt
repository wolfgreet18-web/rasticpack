package com.rasticpack.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.data.repo.InventoryRepository
import com.rasticpack.app.ui.invoices.JalaliDate
import com.rasticpack.app.ui.theme.Gold
import com.rasticpack.app.ui.theme.GoldLight
import com.rasticpack.app.ui.theme.Green
import com.rasticpack.app.ui.theme.GreenBg
import com.rasticpack.app.ui.theme.Red600
import com.rasticpack.app.ui.theme.Red50
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.SurfaceMain
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.util.Locale

private fun formatNum(n: Double): String =
    if (n == 0.0) "" else NumberFormat.getInstance(Locale.US).format(Math.round(n))

/**
 * تب «تنظیمات» — تا زیرمرحله ۱۰.۴. معادل کل بخش TAB — تنظیمات / بکاپ در 4.html، به‌جز
 * خود بکاپ (backup-panel) که در ۱۰.۵ اضافه می‌شود. از این زیرمرحله به بعد، هر ۷ کارت
 * فعلی (⚖️ وزن ورق، 🗑️ قیمت ضایعات، 📊 وضعیت ورق، 📱 متن پیامک، 🧾 تنظیمات فاکتور،
 * 🚛 راننده وانت‌ها، 🖥️ تنظیمات نمایشی) به‌صورت دکمه‌ی تاشو هستند — فقط یکی در هر لحظه
 * باز است — دقیقاً معادل toggleSection/SETTINGS_PANEL_IDS در وب.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    var showDrivers by remember { mutableStateOf(false) }

    if (showDrivers) {
        DriversScreen(onBack = { showDrivers = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "⚙️ تنظیمات",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }
        Spacer(Modifier.height(12.dp))

        FoldableCard(
            title = "⚖️ وزن ورق",
            open = state.openSection == SettingsSection.SHEET_WEIGHT.name,
            onToggle = { viewModel.toggleSection(SettingsSection.SHEET_WEIGHT) }
        ) { SheetWeightContent(state = state, viewModel = viewModel) }

        Spacer(Modifier.height(10.dp))
        FoldableCard(
            title = "🗑️ قیمت ضایعات",
            open = state.openSection == SettingsSection.WASTE_PRICE.name,
            onToggle = { viewModel.toggleSection(SettingsSection.WASTE_PRICE) }
        ) { WastePriceContent(state = state, viewModel = viewModel) }

        Spacer(Modifier.height(10.dp))
        FoldableCard(
            title = "📊 وضعیت ورق",
            open = state.openSection == SettingsSection.SHEET_STATUS.name,
            onToggle = { viewModel.toggleSection(SettingsSection.SHEET_STATUS) }
        ) { SheetStatusContent(state = state, viewModel = viewModel) }

        Spacer(Modifier.height(10.dp))
        FoldableCard(
            title = "📱 متن پیامک",
            open = state.openSection == SettingsSection.SMS_TEMPLATE.name,
            onToggle = { viewModel.toggleSection(SettingsSection.SMS_TEMPLATE) }
        ) { SmsTemplateContent(state = state, viewModel = viewModel) }

        Spacer(Modifier.height(10.dp))
        FoldableCard(
            title = "🧾 تنظیمات فاکتور",
            open = state.openSection == SettingsSection.INVOICE_SETTINGS.name,
            onToggle = { viewModel.toggleSection(SettingsSection.INVOICE_SETTINGS) }
        ) { InvoiceSettingsContent(state = state, viewModel = viewModel) }

        Spacer(Modifier.height(10.dp))
        FoldableCard(
            title = "🚛 راننده وانت‌ها",
            open = state.openSection == SettingsSection.DRIVERS.name,
            onToggle = { viewModel.toggleSection(SettingsSection.DRIVERS) }
        ) { DriversEntryContent(onOpen = { showDrivers = true }) }

        Spacer(Modifier.height(10.dp))
        FoldableCard(
            title = "🖥️ تنظیمات نمایشی",
            open = state.openSection == SettingsSection.DISPLAY.name,
            onToggle = { viewModel.toggleSection(SettingsSection.DISPLAY) }
        ) { DisplaySettingsContent(state = state, viewModel = viewModel) }

        Spacer(Modifier.height(10.dp))
        FoldableCard(
            title = "💾 بکاپ",
            open = state.openSection == SettingsSection.BACKUP.name,
            onToggle = { viewModel.toggleSection(SettingsSection.BACKUP) }
        ) { BackupContent(state = state, viewModel = viewModel) }

        Spacer(Modifier.height(24.dp))
    }
}

/** کارت تاشو با هدر قابل‌کلیک — معادل دقیق دکمه‌های افقی + toggleSection در وب */
@Composable
private fun FoldableCard(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "▾",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.rotate(if (open) 180f else 0f)
                )
            }
            AnimatedVisibility(visible = open) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SheetWeightContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.sheetWeightLayer == "3",
            onClick = { viewModel.setSheetWeightLayer("3") },
            label = { Text("سه‌لایه") }
        )
        FilterChip(
            selected = state.sheetWeightLayer == "5",
            onClick = { viewModel.setSheetWeightLayer("5") },
            label = { Text("پنج‌لایه") }
        )
    }
    Spacer(Modifier.height(12.dp))
    var text by remember(state.sheetWeightLayer, state.loaded) {
        mutableStateOf(if (state.currentSheetWeight == 0.0) "" else formatNum(state.currentSheetWeight))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newVal ->
            text = newVal
            viewModel.updateSheetWeight(newVal.toDoubleOrNull() ?: 0.0)
        },
        label = { Text("وزن هر ۱ متر مربع ورق (گرم)") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WastePriceContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    var text by remember(state.loaded) {
        mutableStateOf(if (state.wastePrice == 0.0) "" else formatNum(state.wastePrice))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newVal ->
            val digitsOnly = newVal.filter { it.isDigit() }
            text = if (digitsOnly.isEmpty()) "" else formatNum(digitsOnly.toDouble())
            viewModel.updateWastePrice(digitsOnly.toDoubleOrNull() ?: 0.0)
        },
        label = { Text("قیمت هر ۱ کیلوگرم ضایعات (ریال)") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "این مقدار در تب «محاسبه کارتن» برای برآورد ارزش ضایعات هر ورق استفاده می‌شود.",
        fontSize = 11.sp,
        color = TextSecondary
    )
}

/**
 * زیرمرحله ۱۰.۴ — معادل status-panel در 4.html: نمودار میله‌ای موجودی هر ورق (رنگ بر
 * اساس آستانه‌ی هشدار: سبز=کافی، زرد=زیر آستانه، قرمز=صفر) + لیست ورودی آستانه برای هر ورق.
 * توجه: در وب این بخش هم اسمش «وضعیت ورق» است و هم داخل تب «تنظیمات» قرار دارد (نه تب «ورق») —
 * دقیقاً همین‌جا هم رعایت شده.
 */
@Composable
private fun SheetStatusContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    Text(
        "برای هر ورق یک «آستانه هشدار» وارد کنید — وقتی موجودی به آن عدد یا کمتر برسد، میله همان ورق در نمودار زرد می‌شود.",
        fontSize = 12.sp,
        color = TextSecondary
    )
    Spacer(Modifier.height(12.dp))

    if (state.sheets.isEmpty()) {
        Text(
            "هیچ ورقی ثبت نشده — از تب «ورق» اضافه کنید.",
            fontSize = 13.sp,
            color = TextMuted,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            textAlign = TextAlign.Center
        )
        return
    }

    SheetStatusChart(sheets = state.sheets, thresholds = state.thresholds)
    Spacer(Modifier.height(16.dp))

    state.sheets.forEach { sheet ->
        SheetThresholdRow(sheet = sheet, threshold = state.thresholds[sheet.id], viewModel = viewModel)
        Spacer(Modifier.height(8.dp))
    }
}

/** نمودار میله‌ای وضعیت موجودی — معادل drawSheetStatusChart در وب */
@Composable
private fun SheetStatusChart(sheets: List<InventorySheetEntity>, thresholds: Map<Int, Int>) {
    val maxQty = (sheets.maxOfOrNull { it.qty } ?: 1).coerceAtLeast(1)
    val chartHeightDp = 130.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceMain),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            sheets.forEach { sheet ->
                val threshold = thresholds[sheet.id]
                val color = when {
                    sheet.qty == 0 -> Red600
                    threshold != null && sheet.qty <= threshold -> Gold
                    else -> Green
                }
                val fraction = (sheet.qty.toDouble() / maxQty).toFloat().coerceIn(0f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(58.dp)
                ) {
                    Text(
                        JalaliDate.toFaDigits(sheet.qty),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height((chartHeightDp.value * fraction).coerceAtLeast(4f).dp)
                            .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${fmtDim(sheet.sh)}×${fmtDim(sheet.sw)}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        InventoryRepository.layerLabel(sheet.layer),
                        fontSize = 10.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    if (threshold != null) {
                        Text(
                            "آستانه: ${JalaliDate.toFaDigits(threshold)}",
                            fontSize = 9.sp,
                            color = Gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun fmtDim(n: Double): String =
    if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

/** یک ردیف ورق در لیست تنظیم آستانه — معادل هر <div> در renderSheetStatusPanel وب */
@Composable
private fun SheetThresholdRow(sheet: InventorySheetEntity, threshold: Int?, viewModel: SettingsViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceMain, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "${fmtDim(sheet.sh)}×${fmtDim(sheet.sw)} · ${InventoryRepository.layerLabel(sheet.layer)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "موجودی: ${JalaliDate.toFaDigits(sheet.qty)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        var text by remember(sheet.id, threshold) { mutableStateOf(threshold?.toString() ?: "") }
        OutlinedTextField(
            value = text,
            onValueChange = { newVal ->
                text = newVal
                viewModel.updateThreshold(sheet.id, newVal)
            },
            placeholder = { Text("آستانه", fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.widthIn(min = 78.dp, max = 92.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        )
    }
}

/**
 * زیرمرحله ۱۰.۲ — معادل sms-panel در 4.html: متن پیامک (برای ارسال از تب فاکتورها)
 * با راهنمای متغیرهای قابل‌استفاده ({نام}، {شرکت}، {تعداد}، {تاریخ}) که هنگام
 * ارسال واقعی با مقدار واقعی جایگزین می‌شوند (منطق fillSmsTemplate در وب).
 */
@Composable
private fun SmsTemplateContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    var text by remember(state.loaded) { mutableStateOf(state.smsTemplate) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            viewModel.updateSmsTemplate(it)
        },
        label = { Text("متن پیامک (برای ارسال از تب فاکتورها)") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = GoldLight),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(11.dp)) {
            Text("💡", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "می‌توانید از این متغیرها در متن استفاده کنید — هنگام ارسال با مقدار واقعی جایگزین می‌شوند:\n" +
                    "{نام} نام مشتری · {شرکت} نام شرکت · {تعداد} تعداد ورق فاکتور · {تاریخ} تاریخ فاکتور",
                fontSize = 12.sp,
                color = Color(0xFF78350F)
            )
        }
    }
}

/**
 * زیرمرحله ۱۰.۲ — معادل invset-panel در 4.html: اطلاعات سربرگ فاکتور PDF که برای مشتری
 * فرستاده می‌شود (نام شرکت، تماس، آدرس، یادداشت پایین فاکتور، و اطلاعات بانکی برای پیامک
 * شماره کارت/شبا). هر فیلد بلافاصله (بدون دکمه‌ی ذخیره‌ی جدا) در دیتابیس ذخیره می‌شود —
 * دقیقاً معادل oninput="updateInvoiceSetting(...)" در وب.
 */
@Composable
private fun InvoiceSettingsContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    Text(
        "این اطلاعات بالای هر فاکتور PDF که برای مشتری می‌فرستید نمایش داده می‌شود.",
        fontSize = 12.sp,
        color = TextSecondary
    )
    Spacer(Modifier.height(12.dp))

    SettingsTextField(
        label = "نام شرکت",
        value = state.invCompanyName,
        loadedKey = state.loaded,
        onValueChange = viewModel::updateInvoiceCompanyName
    )
    Spacer(Modifier.height(10.dp))
    SettingsTextField(
        label = "شماره تماس",
        value = state.invPhone,
        loadedKey = state.loaded,
        keyboardType = KeyboardType.Phone,
        onValueChange = viewModel::updateInvoicePhone
    )
    Spacer(Modifier.height(10.dp))
    SettingsTextField(
        label = "آدرس (اختیاری)",
        value = state.invAddress,
        loadedKey = state.loaded,
        onValueChange = viewModel::updateInvoiceAddress
    )
    Spacer(Modifier.height(10.dp))
    SettingsTextField(
        label = "یادداشت پایین فاکتور (اختیاری)",
        value = state.invFooter,
        loadedKey = state.loaded,
        onValueChange = viewModel::updateInvoiceFooter
    )
    Spacer(Modifier.height(10.dp))
    SettingsTextField(
        label = "شماره کارت (برای پیامک)",
        value = state.invCardNumber,
        loadedKey = state.loaded,
        onValueChange = viewModel::updateInvoiceCardNumber
    )
    Spacer(Modifier.height(10.dp))
    SettingsTextField(
        label = "شماره شبا (برای پیامک)",
        value = state.invShaba,
        loadedKey = state.loaded,
        onValueChange = viewModel::updateInvoiceShaba
    )
    Spacer(Modifier.height(10.dp))
    SettingsTextField(
        label = "نام و نام خانوادگی صاحب حساب (برای پیامک)",
        value = state.invAccountHolderName,
        loadedKey = state.loaded,
        onValueChange = viewModel::updateInvoiceAccountHolderName
    )
}

/** محتوای کارت راننده وانت‌ها — معادل دکمه‌ی تاشوی 🚛 در وب که به صفحه‌ی مستقل می‌رود */
@Composable
private fun DriversEntryContent(onOpen: () -> Unit) {
    Text(
        "افزودن، ویرایش و حذف راننده‌های وانت برای ارسال بار.",
        fontSize = 12.sp,
        color = TextSecondary
    )
    Spacer(Modifier.height(10.dp))
    androidx.compose.material3.Button(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth()
    ) { Text("مدیریت راننده‌ها") }
}

/**
 * زیرمرحله ۱۰.۴ — معادل display-panel در 4.html: اسلایدر اندازه‌ی فونت (۸۰٪ تا ۱۵۰٪)،
 * اسلایدر اندازه‌ی کل نرم‌افزار/زوم (۸۰٪ تا ۱۵۰٪)، سوییچ صدای کلیک دکمه‌ها، و دکمه‌ی
 * بازگشت به حالت پیش‌فرض (۱۰۰٪ فونت / ۸۵٪ زوم / صدا روشن).
 * توجه: در اپ اندروید، بر خلاف وب، این مقادیر فعلاً فقط ذخیره می‌شوند (اعمال واقعی زوم/
 * فونت سراسری روی کل UI به مرحله ۱۱ — پرداخت‌های نهایی — موکول شده، همان‌طور که فونت
 * وزیرمتن واقعی هم آنجا اضافه می‌شود).
 */
@Composable
private fun DisplaySettingsContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    Text(
        "اندازه متن‌ها و اندازه کل نرم‌افزار را متناسب با گوشی خودتان تنظیم کنید.",
        fontSize = 12.sp,
        color = TextSecondary
    )
    Spacer(Modifier.height(18.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("اندازه فونت متن‌ها", fontSize = 13.sp)
        Text(
            "${JalaliDate.toFaDigits(state.dispFontScale)}٪",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Slider(
        value = state.dispFontScale.toFloat(),
        onValueChange = { viewModel.updateDisplayFontScale(it.toInt()) },
        valueRange = 80f..150f,
        steps = 13 // گام ۵٪ از ۸۰ تا ۱۵۰
    )

    Spacer(Modifier.height(14.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("اندازه کل نرم‌افزار", fontSize = 13.sp)
        Text(
            "${JalaliDate.toFaDigits(state.dispZoom)}٪",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Slider(
        value = state.dispZoom.toFloat(),
        onValueChange = { viewModel.updateDisplayZoom(it.toInt()) },
        valueRange = 80f..150f,
        steps = 13
    )

    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("صدای کلیک دکمه‌ها", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.dispSoundEnabled) "🔊 روشن" else "🔇 خاموش", fontSize = 12.sp, color = TextSecondary)
            Switch(
                checked = state.dispSoundEnabled,
                onCheckedChange = { viewModel.toggleClickSound() },
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    androidx.compose.material3.OutlinedButton(
        onClick = { viewModel.resetDisplaySettings() },
        modifier = Modifier.fillMaxWidth()
    ) { Text("↺ بازگشت به حالت پیش‌فرض") }
}

/**
 * زیرمرحله ۱۰.۵ — معادل backup-panel در 4.html: بکاپ‌گیری (خروجی JSON)، بازیابی از فایل،
 * و ناحیه‌ی خطر (پاک‌کردن کامل اطلاعات).
 *
 * تفاوت آگاهانه با وب: در وب دکمه‌ی «بکاپ» یا از Share API استفاده می‌کرد یا مستقیم به
 * پوشه‌ی دانلودها می‌ریخت (چون مرورگر گزینه‌ی انتخاب مسیر نداشت). در اندروید بومی، معادل
 * درست‌تر و استاندارد Storage Access Framework (SAF) است — با ACTION_CREATE_DOCUMENT کاربر
 * خودش مسیر/نام فایل را روی گوشی انتخاب می‌کند (دقیقاً همان تجربه‌ی «انتخاب مقصد» که در
 * وب با Share Sheet شبیه‌سازی می‌شد)، و با ACTION_OPEN_DOCUMENT برای بازیابی فایل انتخاب
 * می‌شود — بدون نیاز به مجوز ذخیره‌سازی گسترده (Scoped Storage-friendly).
 */
@Composable
private fun BackupContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearConfirm1 by remember { mutableStateOf(false) }
    var showClearConfirm2 by remember { mutableStateOf(false) }
    // محتوای بکاپ آماده‌شده که منتظر است کاربر مسیر ذخیره را از SAF انتخاب کند
    var pendingBackupContent by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val content = pendingBackupContent
        pendingBackupContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                // پیام خطا از قبل توسط requestBackup تنظیم شده؛ اینجا فقط برای اطمینان چیزی عوض نمی‌کنیم
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }
                if (text != null) viewModel.restoreFromJson(text)
            } catch (e: Exception) {
                // خطای خواندن فایل — پیام کلی از طریق restoreFromJson با ورودی نامعتبر هم پوشش داده می‌شود
            }
        }
    }

    Text(
        "اطلاعات شما به‌صورت خودکار روی دستگاه ذخیره می‌شود.\nبرای حفظ نسخه پشتیبان یا انتقال به گوشی دیگر از دو دکمه زیر استفاده کنید.",
        fontSize = 13.sp,
        color = TextSecondary,
        lineHeight = 20.sp
    )
    Spacer(Modifier.height(16.dp))

    // دکمه ۱: بکاپ — معادل doBackup در وب
    Button(
        onClick = {
            viewModel.requestBackup { fileName, content ->
                pendingBackupContent = content
                createDocumentLauncher.launch(fileName)
            }
        },
        enabled = !state.backupBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.backupBusy) {
            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text("💾 بکاپ")
    }

    Spacer(Modifier.height(12.dp))

    // دکمه ۲: بازیابی بکاپ — معادل input type=file در وب
    Button(
        onClick = { openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
        enabled = !state.backupBusy,
        colors = ButtonDefaults.buttonColors(containerColor = Green),
        modifier = Modifier.fillMaxWidth()
    ) { Text("📥 بازیابی بکاپ") }

    // پیام وضعیت — معادل backupMsg(ok,text) در وب
    state.backupMessage?.let { msg ->
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (state.backupMessageIsError) Red50 else GreenBg
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                Text(if (state.backupMessageIsError) "⚠️" else "✅", fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = if (state.backupMessageIsError) Color(0xFF991B1B) else Color(0xFF166534)
                )
            }
        }
    }

    Spacer(Modifier.height(32.dp))
    Text(
        "⚠️ ناحیهٔ خطر",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Red600
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { showClearConfirm1 = true },
        enabled = !state.backupBusy,
        colors = ButtonDefaults.buttonColors(containerColor = Red600),
        modifier = Modifier.fillMaxWidth()
    ) { Text("🗑 پاک کردن کامل اطلاعات") }

    // معادل دو confirm() پشت‌سرهم در وب — یک تأییدیه‌ی اول و یک تأییدیه‌ی نهایی
    if (showClearConfirm1) {
        AlertDialog(
            onDismissRequest = { showClearConfirm1 = false },
            title = { Text("پاک کردن کامل اطلاعات") },
            text = { Text("تمام اطلاعات (موجودی، مشتریان و فاکتورها) پاک می‌شود. مطمئن هستید؟") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm1 = false
                    showClearConfirm2 = true
                }) { Text("ادامه", color = Red600) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm1 = false }) { Text("انصراف") }
            }
        )
    }
    if (showClearConfirm2) {
        AlertDialog(
            onDismissRequest = { showClearConfirm2 = false },
            title = { Text("تأیید نهایی") },
            text = { Text("این عمل قابل بازگشت نیست. تأیید نهایی؟") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm2 = false
                    viewModel.clearAllData()
                }) { Text("پاک کن", color = Red600) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm2 = false }) { Text("انصراف") }
            }
        )
    }
}

/** فیلد متنی ساده با نگه‌داشتن مقدار محلی — تا تایپ کاربر با هر رندر مجدد قطع نشود */
@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    loadedKey: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    var text by remember(loadedKey) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}
