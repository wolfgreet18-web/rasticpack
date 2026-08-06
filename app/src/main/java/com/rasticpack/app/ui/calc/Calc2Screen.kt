package com.rasticpack.app.ui.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasticpack.app.engine.CalculatorEngine
import com.rasticpack.app.engine.Grain
import com.rasticpack.app.ui.theme.BlueBg
import com.rasticpack.app.ui.theme.GreenBg
import com.rasticpack.app.ui.theme.GreenDark
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

private fun formatNum(n: Double): String =
    NumberFormat.getIntegerInstance(Locale.US).format(Math.round(n))

private fun dimX(vararg n: Double): String =
    n.joinToString("×") { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }

@Composable
fun Calc2Screen(onBack: () -> Unit) {
    val viewModel: Calc2ViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💲 محاسبه کارتن",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(state.rows) { idx, row ->
                CartonRowCard(
                    index = idx,
                    row = row,
                    showDelete = state.rows.size > 1,
                    onChange = { updated -> viewModel.updateRow(row.localId) { updated } },
                    onDelete = { viewModel.removeRow(row.localId) }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.runCalculation() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isCalculating
                    ) {
                        Text(if (state.isCalculating) "در حال محاسبه..." else "محاسبه")
                    }
                    OutlinedButton(onClick = { viewModel.addRow() }) { Text("+ کارتن") }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.profitPercentText,
                    onValueChange = { viewModel.updateProfitPercent(it) },
                    label = { Text("درصد سود") },
                    modifier = Modifier.fillMaxWidth()
                )

                state.globalAlert?.let { alert ->
                    Spacer(Modifier.height(10.dp))
                    AlertBox(text = alert)
                }
            }

            if (state.showResults) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(16.dp))
                }

                items(state.results) { result ->
                    CartonResultCard(
                        result = result,
                        suggestOpen = state.suggestOpenFor == result.row.localId,
                        suggestBundle = state.suggestResults[result.row.localId],
                        onToggleSuggest = { viewModel.toggleSuggest(result.row.localId) },
                        onApplySuggestedDims = { l, w, h -> viewModel.applySuggestedDims(result.row.localId, l, w, h) }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                state.totals?.let { totals ->
                    item {
                        TotalsCard(totals)
                        Spacer(Modifier.height(12.dp))
                        SubmitInvoiceCard(
                            customerName = state.invoiceCustomerName,
                            onCustomerNameChange = { viewModel.updateInvoiceCustomerName(it) },
                            customerError = state.invoiceCustomerError,
                            alert = state.invoiceSubmitAlert,
                            isSubmitting = state.isSubmittingInvoice,
                            submitted = state.invoiceSubmitted,
                            submittedInvoiceId = state.submittedInvoiceId,
                            onSubmit = { viewModel.submitInvoice() }
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun AlertBox(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Text("⚠️ ", fontSize = 14.sp)
            Text(text, fontSize = 13.sp, color = Red700)
        }
    }
}

@Composable
private fun CartonRowCard(
    index: Int,
    row: CartonRowInput,
    showDelete: Boolean,
    onChange: (CartonRowInput) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(color = MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (showDelete) {
                    TextButton(onClick = onDelete) { Text("− حذف", color = Red700) }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("L", row.length, Modifier.weight(1f)) { onChange(row.copy(length = it)) }
                NumField("W", row.width, Modifier.weight(1f)) { onChange(row.copy(width = it)) }
                NumField("H", row.height, Modifier.weight(1f)) { onChange(row.copy(height = it)) }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("تعداد", row.qty, Modifier.weight(1f)) { onChange(row.copy(qty = it)) }
                NumField("لب چسب", row.glue, Modifier.weight(1f)) { onChange(row.copy(glue = it)) }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipToggle("3 لایه", row.layer == "3") { onChange(row.copy(layer = "3")) }
                ChipToggle("5 لایه", row.layer == "5") { onChange(row.copy(layer = "5")) }
            }
            Spacer(Modifier.height(6.dp))
            // ══ زیرمرحله ۱۱.۴.۲ — گوشی‌های کوچک/باریک ══
            // این ردیف ۴ دکمه (C/E/2T/KT) + یک Spacer را در کنار هم نشان می‌دهد؛ روی
            // گوشی‌های خیلی باریک یا با فونت بزرگ‌تر (از تنظیمات نمایشی)، مجموع عرض
            // می‌توانست از عرض صفحه بیشتر شود و دکمه‌ی آخر (KT) کلیپ/بریده شود.
            // معادل رفتار .tab-bar{overflow-x:auto} در وب: با horizontalScroll، اگر جا
            // نشد به‌جای کلیپ‌شدن، ردیف قابل‌اسکرول افقی می‌شود — روی گوشی‌های معمولی
            // که همه چیز جا می‌شود هیچ تفاوتی حس نمی‌شود.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChipToggle("C", row.flute == "C") { onChange(row.copy(flute = "C")) }
                ChipToggle("E", row.flute == "E") { onChange(row.copy(flute = "E")) }
                Spacer(Modifier.width(6.dp))
                ChipToggle("2T", row.paperType == "2T") { onChange(row.copy(paperType = "2T")) }
                ChipToggle("KT", row.paperType == "KT") { onChange(row.copy(paperType = "KT")) }
            }

            row.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Red700, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier,
        singleLine = true
    )
}

@Composable
private fun ChipToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
private fun CartonResultCard(
    result: CartonCalcResult,
    suggestOpen: Boolean = false,
    suggestBundle: Calc2SuggestBundle? = null,
    onToggleSuggest: () -> Unit = {},
    onApplySuggestedDims: (Double, Double, Double) -> Unit = { _, _, _ -> }
) {
    // معادل accordion «🖼️ تصویر شیت» در وب — پیش‌فرض بسته، با کلیک باز/بسته می‌شود.
    var showImage by remember(result.row.localId) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "${dimX(result.length, result.width, result.height)} سانتی‌متر · ${result.qty} کارتن",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            val best = result.sheets.firstOrNull()
            if (best != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "بهترین ورق: ${dimX(best.sheet.sh, best.sheet.sw)} · ${best.sheetsNeed ?: "—"} ورق · ${if (best.canFulfill) "✔ کافی" else "✘ ناکافی"}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text("ورق مناسبی در انبار پیدا نشد.", fontSize = 12.sp, color = TextMuted)
            }

            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBox("قیمت پایه", formatNum(result.basePriceWithWaste), Modifier.weight(1f))
                StatBox("سود کل", formatNum(result.totalProfit), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            StatBox("قیمت کل کارتن‌ها", formatNum(result.totalPrice), Modifier.fillMaxWidth(), big = true)

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showImage = !showImage },
                modifier = Modifier.fillMaxWidth(),
                enabled = best != null
            ) {
                Text(if (showImage) "🖼️ بستن تصویر شیت" else "🖼️ تصویر شیت")
            }

            if (showImage && best != null) {
                Spacer(Modifier.height(10.dp))
                val grain = Grain.HORIZONTAL // معادل grain='horizontal' ثابت در runCalc2 وب
                SheetLayoutCanvas(
                    sw = best.sheet.sw,
                    sh = best.sheet.sh,
                    bw = result.totalWidth,
                    bh = result.totalLength,
                    grain = grain,
                    layout = best.layout
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = GrainBadgeLabel(grain),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SheetLayoutLegend()
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onToggleSuggest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (suggestOpen) "📐 بستن پیشنهاد شیت" else "📐 پیشنهاد شیت")
            }

            if (suggestOpen) {
                Spacer(Modifier.height(10.dp))
                if (suggestBundle == null) {
                    Text("در حال محاسبه...", fontSize = 12.sp, color = TextMuted)
                } else {
                    Calc2SuggestPanel(
                        bw = result.totalWidth,
                        bh = result.totalLength,
                        bundle = suggestBundle,
                        onApplySuggestedDims = onApplySuggestedDims
                    )
                }
            }
        }
    }
}

/**
 * محتوای پنل «📐 پیشنهاد شیت» — معادل renderCalc2SuggestSheet در وب (بدون بخش برش دوتکه،
 * که در زیرمرحله‌ی ۳.۴ اضافه می‌شود).
 */
@Composable
private fun Calc2SuggestPanel(
    bw: Double,
    bh: Double,
    bundle: Calc2SuggestBundle,
    onApplySuggestedDims: (Double, Double, Double) -> Unit
) {
    Column {
        StatBox(
            label = "ابعاد بازشده‌ی همین کارتن (طول × عرض)",
            value = "${dimX(bh, bw)} سانتی‌متر",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Text("📏 پیشنهاد ابعاد کارتن نزدیک (بر اساس موجودی — پرت صفر)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (bundle.cartonSuggestions.isEmpty()) {
            EmptyHint("در محدودهٔ نزدیک به ابعاد درخواستی، ابعاد کارتنی که روی شیت‌های موجود پرت صفر بدهد پیدا نشد.")
        } else {
            bundle.cartonSuggestions.forEachIndexed { i, c ->
                SuggestionCard(
                    highlighted = i == 0,
                    onClick = { onApplySuggestedDims(c.length, c.width, c.height) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${if (i == 0) "🏆" else "▫️"} ${dimX(c.length, c.width, c.height)} سانتی‌متر",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text("✔ پرت صفر", color = GreenDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "روی ورق موجود ${dimX(c.sheetSh, c.sheetSw)} (موجودی ${c.sheetQty})",
                        fontSize = 11.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("کارتن در هر ورق", "${c.count}", Modifier.weight(1f))
                        MiniStat("چیدمان (ستون×ردیف)", "${c.cols}×${c.rows}", Modifier.weight(1f))
                        MiniStat("اختلاف با درخواست (سانت)", "${c.diff}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "👆 اعمال این ابعاد و محاسبه مجدد",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ══ ۳.۴ — پیشنهاد برش دوتکه — معادل بخش twoPieceHtml در renderCalc2SuggestSheet وب ══
        // نمایش داده می‌شود اگر: (الف) اصلاً راه یک‌تکه‌ای نداریم — پس دوتکه تنها راه است،
        // یا (ب) دوتکه پرت کمتری نسبت به یک‌تکه دارد.
        val tp = bundle.twoPiece
        val singleBest = tp.singleBest
        val showTwoPiece = tp.best != null && (singleBest == null || tp.best.wastePercent < singleBest.wastePercent)
        if (showTwoPiece && tp.best != null) {
            Spacer(Modifier.height(14.dp))
            TwoPieceSuggestionCard(tp)
        }

        Spacer(Modifier.height(14.dp))
        Text("🏆 پیشنهاد ابعاد شیت خام برای پرت صفر", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (bundle.zeroWasteOptions.isEmpty()) {
            EmptyHint("با این ابعاد کارتن، چیدمان مناسبی در بازه معقول شیت پیدا نشد.")
        } else {
            bundle.zeroWasteOptions.forEachIndexed { i, o ->
                SuggestionCard(highlighted = i == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${if (i == 0) "🏆" else "▫️"} ${dimX(o.sh, o.sw)} سانتی‌متر", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val zero = o.wastePercent <= 0.05
                        Text(
                            if (zero) "✔ پرت صفر" else "پرت ${o.wastePercent}٪",
                            color = if (zero) GreenDark else Color(0xFFD97706),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("کارتن در هر ورق", "${o.count}", Modifier.weight(1f))
                        MiniStat("چیدمان (ستون×ردیف)", "${o.cols}×${o.rows}", Modifier.weight(1f))
                        MiniStat("مساحت هر ورق (m²)", "${o.areaM2}", Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("📦 نزدیک‌ترین شیت‌های موجود در انبار", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (bundle.existingClose.isEmpty()) {
            EmptyHint("در انبار فعلی ورقی از این لایه ثبت نشده.")
        } else {
            bundle.existingClose.forEach { s ->
                SuggestionCard(highlighted = s.wastePercent < 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${dimX(s.sheet.sh, s.sheet.sw)} · ${Calc2ViewModel.layerLabel(s.sheet.layer)}",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        val near = s.wastePercent < 1
                        Text(
                            if (near) "✔ تقریباً بدون پرت" else "پرت ${s.wastePercent}٪",
                            color = if (near) GreenDark else Color(0xFFD97706),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("کارتن در هر ورق", "${s.layout.total}", Modifier.weight(1f))
                        MiniStat("موجودی این ورق", "${s.sheet.qty}", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * کارت پیشنهاد برش دوتکه — معادل دقیق بلوک twoPieceHtml در renderCalc2SuggestSheet وب.
 * چیدمان: عنوان، توضیح متنی، (در صورت وجود مقایسه) دو‌کارتِ هزینه‌ی یک‌تکه/دوتکه +
 * کارتِ خلاصه‌ی صرفه‌جویی/هزینه‌ی اضافی، وگرنه فقط کارتِ هزینه‌ی دوتکه، سپس جزئیات ابعاد.
 */
@Composable
private fun TwoPieceSuggestionCard(tp: com.rasticpack.app.engine.TwoPieceSuggestion) {
    val best = tp.best ?: return
    val singleBest = tp.singleBest
    val hasComparison = tp.savingsPerCarton != null
    val hasCostTwo = tp.costPerCartonTwo != null
    val cheaper = hasComparison && (tp.savingsPerCarton ?: 0.0) > 0

    Card(
        colors = CardDefaults.cardColors(containerColor = GreenBg),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF16A34A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "✂️ پیشنهاد: برش دو تکه" + if (singleBest != null) " — پرت کمتر" else " — تنها راه تولید با موجودی فعلی",
                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenDark
            )
            Spacer(Modifier.height(6.dp))
            val desc = if (singleBest != null)
                "به‌جای بلانک یک‌تکه، کارتن از خط تای روبه‌روی لپ چسب اصلی (نه وسط یک پنل — تا هیچ دری نصفه نشود) به دو تکهٔ کاملاً هم‌اندازه بریده و با یک لب چسب اضافه به‌هم متصل می‌شود. با این روش پرت از ${singleBest.wastePercent}٪ به ${best.wastePercent}٪ کاهش می‌یابد."
            else
                "هیچ ورق موجود در انبار به‌تنهایی برای بلانک یک‌تکه این کارتن کافی نیست — با این روش پرت آن ${best.wastePercent}٪ است و تولید روی موجودی فعلی امکان‌پذیر می‌شود."
            Text(desc, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))

            if (hasComparison) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBox("هزینه هر کارتن — یک‌تکه", formatNum(tp.costPerCartonSingle ?: 0.0), Modifier.weight(1f))
                    StatBox("هزینه هر کارتن — دو‌تکه", formatNum(tp.costPerCartonTwo ?: 0.0), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                val savings = tp.savingsPerCarton ?: 0.0
                val total = tp.totalSavings ?: 0.0
                StatBox(
                    label = (if (cheaper) "صرفه‌جویی" else "هزینه اضافی") + " کل: ${formatNum(kotlin.math.abs(total))} ریال",
                    value = (if (cheaper) "${formatNum(savings)} ریال ارزان‌تر" else "${formatNum(kotlin.math.abs(savings))} ریال گران‌تر") + " هر کارتن",
                    modifier = Modifier.fillMaxWidth(), big = true
                )
            } else if (hasCostTwo) {
                StatBox(
                    label = "هزینه هر کارتن با روش دوتکه (مقایسه با یک‌تکه ممکن نیست — چون یک‌تکه اصلاً روی موجودی جا نمی‌شود)",
                    value = formatNum(tp.costPerCartonTwo ?: 0.0),
                    modifier = Modifier.fillMaxWidth(), big = true
                )
                Spacer(Modifier.height(8.dp))
                StatBox("هزینه کل", formatNum((tp.costPerCartonTwo ?: 0.0) * tp.piecesNeeded / 2), Modifier.fillMaxWidth())
            } else {
                EmptyHint("برای محاسبه قیمت، ابتدا قیمت ورق را در تب «ورق» ثبت کنید.")
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("ابعاد هر تکه (عرض×طول)", dimX(tp.pieceHt, tp.pieceLen), Modifier.weight(1f))
                MiniStat("تعداد کل تکه لازم (۲ به‌ازای هر کارتن)", "${tp.piecesNeeded}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("بهترین ورق برای این تکه", dimX(best.sheet.sh, best.sheet.sw), Modifier.weight(1f))
                MiniStat("تکه در هر ورق", "${best.perSheet}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "* بدون احتساب هزینه چسب اضافه و کار اضافی اتصال دو تکه",
                fontSize = 10.sp, color = TextMuted
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    highlighted: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (highlighted) Color(0xFFD97706) else MaterialTheme.colorScheme.outlineVariant
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, fontSize = 10.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 8.dp))
}

/** راهنمای رنگ‌ها زیر تصویر — معادل دقیق .legend در وب (سه مورد: جهت صحیح/ناصحیح/پرت). */
@Composable
private fun SheetLayoutLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendItem(Color(0xFF16A34A), "جهت صحیح")
        LegendItem(Color(0xFFDC2626), "جهت ناصحیح")
        LegendItem(Color(0xFF94A3B8), "پرت")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 5.dp)
                .background(color = color, shape = RoundedCornerShape(3.dp))
                .width(12.dp)
                .height(12.dp)
        )
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GreenBg),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = if (big) 14.dp else 10.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = if (big) 17.sp else 14.sp, color = GreenDark)
            Text(label, fontSize = 11.sp, color = TextMuted)
        }
    }
}

/**
 * کارت «ثبت فاکتور» — معادل دقیق بخش c2sec-invoice-all در 4.html:
 * فیلد نام مشتری (که باید از قبل در تب «مشتری‌ها» ثبت شده باشد)، دکمه‌ی «ثبت فاکتور»،
 * پیام خطا زیر فیلد (اگر نام خالی یا مشتری پیدا نشود)، پیام هشدار سراسری (کمبود موجودی)،
 * و بعد از ثبت موفق دکمه غیرفعال می‌شود و پیام موفقیت نشان داده می‌شود.
 */
@Composable
private fun SubmitInvoiceCard(
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    customerError: String?,
    alert: String?,
    isSubmitting: Boolean,
    submitted: Boolean,
    submittedInvoiceId: Int?,
    onSubmit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BlueBg),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = !submitted && !isSubmitting,
                    modifier = Modifier.weight(0.8f)
                ) {
                    Text(
                        when {
                            submitted -> "✔ فاکتور ثبت شد"
                            isSubmitting -> "در حال ثبت..."
                            else -> "ثبت فاکتور"
                        }
                    )
                }
                OutlinedTextField(
                    value = customerName,
                    onValueChange = onCustomerNameChange,
                    label = { Text("نام مشتری") },
                    singleLine = true,
                    enabled = !submitted,
                    isError = customerError != null,
                    modifier = Modifier.weight(1.6f)
                )
            }
            customerError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = Red700)
            }
            alert?.let {
                Spacer(Modifier.height(10.dp))
                AlertBox(text = it)
            }
            if (submitted && submittedInvoiceId != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "فاکتور شماره #$submittedInvoiceId با موفقیت ثبت شد — از تب «فاکتورها» قابل مشاهده است.",
                    fontSize = 12.sp,
                    color = GreenDark
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(totals: Calc2Totals) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BlueBg),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("جمع کل", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBox("مبلغ کل", formatNum(totals.totalPrice), Modifier.weight(1f))
                StatBox("سود کل", formatNum(totals.totalProfit), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            StatBox("ضایعات کل", formatNum(totals.totalWaste), Modifier.fillMaxWidth())
        }
    }
}

