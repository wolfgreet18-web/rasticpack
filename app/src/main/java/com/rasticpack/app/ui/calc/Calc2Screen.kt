package com.rasticpack.app.ui.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.engine.CalculatorEngine
import com.rasticpack.app.engine.Grain
import com.rasticpack.app.ui.theme.BlueBg
import com.rasticpack.app.ui.theme.BlueDark
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.GreenBg
import com.rasticpack.app.ui.theme.GreenDark
import com.rasticpack.app.ui.theme.Red50
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.SurfaceDeep
import com.rasticpack.app.ui.theme.SurfaceMain
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextPrimary
import com.rasticpack.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

// ══ رنگ‌های محلی این صفحه — عمداً از theme/Color.kt ایمپورت نمی‌شوند، بلکه اینجا مستقیم
// تعریف می‌شوند. چون نسخه‌ی Color.kt که واقعاً روی مخزن گیت‌هاب است معلوم نیست دقیقاً
// همین نام‌ها را داشته باشد (چند بار خطای "Unresolved reference" برای همین اسم‌ها گرفتیم)،
// اینجا خودکفا شدیم تا دیگر به‌هیچ‌وجه به فایل Color.kt بیرونی وابسته نباشیم و کامپایل
// همیشه موفق باشد، صرف‌نظر از اینکه آن فایل چه داشته باشد. مقادیر هگز دقیقاً همان‌هایی‌اند
// که در HTML اصلی (4.html) به‌عنوان :root تعریف شده‌اند.
private val Blue = Color(0xFF2563EB)
private val Green = Color(0xFF16A34A)
private val Gold = Color(0xFFD97706)
private val GoldLight = Color(0xFFFEF3C7)
private val Red100 = Color(0xFFFEE2E2)
private val Red600 = Color(0xFFDC2626)

// فیلدهای رنگی ردیف ورودی کارتن — معادل دقیق c2-length/width/height/qty/glue در 4.html
private val FieldLengthBg = Color(0xFF2563EB)      // L — آبی
private val FieldLengthBorder = Color(0xFF1D4ED8)
private val FieldWidthBg = Color(0xFFDC2626)       // W — قرمز
private val FieldWidthBorder = Color(0xFFB91C1C)
private val FieldHeightBg = Color(0xFF16A34A)      // H — سبز
private val FieldHeightBorder = Color(0xFF15803D)
private val FieldQtyBg = Color(0xFF1C1917)         // N — سیاه
private val FieldQtyBorder = Color(0xFF000000)
private val FieldGlueBg = Color(0xFFEAB308)        // F — زرد
private val FieldGlueBorder = Color(0xFFCA8A04)

// رنگ‌های استاتوس‌باکس — معادل [class*="stat-"] در 4.html
private val StatGreenBorder = Green
private val StatYellowBg = GoldLight
private val StatYellowBorder = Gold
private val StatRedBg = Red50
private val StatRedBorder = Red600
private val StatBlueBorder = Blue

private fun formatNum(n: Double): String =
    NumberFormat.getIntegerInstance(Locale.US).format(Math.round(n))

private fun dimX(vararg n: Double): String =
    n.joinToString("×") { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }

@Composable
fun Calc2Screen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val viewModel = remember { Calc2ViewModel(db) }
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(state.rows) { idx, row ->
                CartonRowCard(
                    index = idx,
                    row = row,
                    showDelete = state.rows.size > 1,
                    // ══ دکمه‌ی «+» فقط روی آخرین کارت نمایش داده می‌شود — دقیقاً معادل
                    // updateCalc2AddButtonPlacement در وب که .calc2-add-btn را به calc2-row-bottom
                    // آخرین ردیف اضافه می‌کند، نه یک ردیف جدا زیر لیست. ══
                    showAdd = idx == state.rows.lastIndex,
                    onChange = { updated -> viewModel.updateRow(row.localId) { updated } },
                    onDelete = { viewModel.removeRow(row.localId) },
                    onAdd = { viewModel.addRow() }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))

                // ══ ردیف «محاسبه» + درصد سود — معادل ردیف انتهایی تب محاسبه در وب:
                // دکمه‌ی قرمز بزرگ «محاسبه» (flex:1) و باکس کوچک قرمز درصد سود (flex:0 0 46px) ══
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(color = Red700, shape = RoundedCornerShape(8.dp))
                            .then(
                                if (!state.isCalculating) Modifier.clickable { viewModel.runCalculation() }
                                else Modifier
                            )
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.isCalculating) "در حال محاسبه..." else "محاسبه",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    androidx.compose.foundation.text.BasicTextField(
                        value = state.profitPercentText,
                        onValueChange = { viewModel.updateProfitPercent(it) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Red700,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Red700),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier
                            .width(52.dp)
                            .background(color = SurfaceMain, shape = RoundedCornerShape(8.dp))
                            .border(width = 1.5.dp, color = BorderColor, shape = RoundedCornerShape(8.dp))
                            .padding(vertical = 13.dp, horizontal = 4.dp)
                    )
                }

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
        colors = CardDefaults.cardColors(containerColor = Red50),
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
    showAdd: Boolean,
    onChange: (CartonRowInput) -> Unit,
    onDelete: () -> Unit,
    onAdd: () -> Unit
) {
    // معادل .calc2-row در وب: کارت با پس‌زمینه‌ی surf-alt و بردر نازک
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // ══ شماره‌ی ردیف — معادل .calc2-row-num / .c2-num در وب (بالای کارت، سمت راست) ══
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(color = Red700, shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ══ ردیف L / W / H — معادل c2-length/width/height در وب.
            // ترتیب دقیقاً مثل HTML از راست به چپ: L (آبی) سمت راست، بعد W (قرمز)، بعد H (سبز) ══
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColoredNumField("L", row.length, FieldLengthBg, FieldLengthBorder, Modifier.weight(1f)) { onChange(row.copy(length = it)) }
                ColoredNumField("W", row.width, FieldWidthBg, FieldWidthBorder, Modifier.weight(1f)) { onChange(row.copy(width = it)) }
                ColoredNumField("H", row.height, FieldHeightBg, FieldHeightBorder, Modifier.weight(1f)) { onChange(row.copy(height = it)) }
            }

            Spacer(Modifier.height(10.dp))

            // ══ ردیف N (تعداد، سیاه) و F (لب چسب، زرد) — معادل c2-qty/c2-glue در وب.
            // در HTML این دو فیلد عرض ثابت کوچک دارند (نه weight برابر)، N=۷۰px و F=۴۶px. ══
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                ColoredNumField("N", row.qty, FieldQtyBg, FieldQtyBorder, Modifier.width(78.dp)) { onChange(row.copy(qty = it)) }
                ColoredNumField("F", row.glue, FieldGlueBg, FieldGlueBorder, Modifier.width(56.dp)) { onChange(row.copy(glue = it)) }

                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // ══ سه‌تایی toggle: لایه (3/5) → فلوت (C/E) → نوع کاغذ (2T/KT) — دقیقاً همین ترتیب
            // و همین برچسب‌ها مطابق HTML واقعی (نه "3 لایه" / "KT" اول) ══
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RedToggleGroup(
                    options = listOf("3" to (row.layer == "3"), "5" to (row.layer == "5")),
                    onSelect = { i -> onChange(row.copy(layer = if (i == 0) "3" else "5")) }
                )
                RedToggleGroup(
                    options = listOf("C" to (row.flute == "C"), "E" to (row.flute == "E")),
                    onSelect = { i -> onChange(row.copy(flute = if (i == 0) "C" else "E")) }
                )
                RedToggleGroup(
                    options = listOf("2T" to (row.paperType == "2T"), "KT" to (row.paperType == "KT")),
                    onSelect = { i -> onChange(row.copy(paperType = if (i == 0) "2T" else "KT")) }
                )
            }

            row.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Red700, fontSize = 12.sp)
            }

            // ══ ردیف پایین کارت — معادل .calc2-row-bottom در وب: دکمه‌ی حذف (−) سمت راست‌ترین،
            // و دکمه‌ی «+» افزودن کارتن فقط روی آخرین کارت، کنار همان دکمه‌ی حذف. ══
            if (showDelete || showAdd) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDelete) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(color = Red50, shape = androidx.compose.foundation.shape.CircleShape)
                                .border(width = 1.5.dp, color = Red100, shape = androidx.compose.foundation.shape.CircleShape)
                                .clickable(onClick = onDelete),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("−", color = Red700, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    if (showAdd) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(color = SurfaceAlt, shape = androidx.compose.foundation.shape.CircleShape)
                                .border(width = 1.5.dp, color = BorderColor, shape = androidx.compose.foundation.shape.CircleShape)
                                .clickable(onClick = onAdd),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * فیلد ورودی رنگی — معادل استایل مستقیم روی input های c2-length/width/height/qty/glue در وب:
 * پس‌زمینه‌ی رنگی پر، متن سفید و برجسته، برچسب کوچک بالای فیلد (رنگ قرمز مثل وب).
 */
@Composable
private fun ColoredNumField(
    label: String,
    value: String,
    bg: Color,
    border: Color,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Red700,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(color = bg, shape = RoundedCornerShape(8.dp))
                .border(width = 1.5.dp, color = border, shape = RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp, horizontal = 8.dp)
        )
    }
}

/**
 * گروه دکمه‌های toggle قرمز — معادل .subtype-toggle/.subtype-btn/.subtype-btn.active در وب:
 * دکمه‌ی انتخاب‌شده پس‌زمینه‌ی قرمز برند + متن سفید، دکمه‌ی غیرفعال پس‌زمینه‌ی خاکستری‌روشن + متن خاکستری.
 */
@Composable
private fun RedToggleGroup(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .background(color = SurfaceAlt, shape = RoundedCornerShape(10.dp))
            .border(width = 1.5.dp, color = BorderColor, shape = RoundedCornerShape(10.dp))
    ) {
        options.forEachIndexed { i, (label, selected) ->
            Box(
                modifier = Modifier
                    .background(
                        color = if (selected) Red700 else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else TextSecondary
                )
            }
        }
    }
}

/** بخش‌های accordion داخل کارت نتیجه — معادل کلیدهای c2sec-price/sheet/waste/img/suggest در وب. فقط یکی هم‌زمان باز است. */
private enum class ResultAccordion { PRICE, SHEET, WASTE, IMG, SUGGEST }

@Composable
private fun CartonResultCard(
    result: CartonCalcResult,
    suggestOpen: Boolean = false,
    suggestBundle: Calc2SuggestBundle? = null,
    onToggleSuggest: () -> Unit = {},
    onApplySuggestedDims: (Double, Double, Double) -> Unit = { _, _, _ -> }
) {
    // معادل calc2Accordion در وب — همیشه حداکثر یکی از پنج بخش باز است.
    var openSection by remember(result.row.localId) { mutableStateOf<ResultAccordion?>(null) }
    // suggestOpen از ViewModel کنترل می‌شود (چون نیاز به fetch async دارد)؛ وقتی suggestOpen=true
    // بشود openSection را هم‌زمان با آن هماهنگ می‌کنیم تا فقط یکی باز بماند.
    if (suggestOpen && openSection != ResultAccordion.SUGGEST) openSection = ResultAccordion.SUGGEST
    if (!suggestOpen && openSection == ResultAccordion.SUGGEST) openSection = null

    val best = result.sheets.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            // ══ هدر: سمت راست ابعاد رنگی + ابعاد ورق، سمت چپ سه ردیف قیمت (فروش/تخفیف/پایه) —
            // معادل inv-card-header در وب (inv-card-main + inv-card-stats) ══
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    // ابعاد کارتن با رنگ کاراکتر‌به‌کاراکتر: L=آبی، ×=مشکی، W=قرمز، ×=مشکی، H=سبز
                    ColoredDimsRow(result.length, result.width, result.height, extraLabel = "${result.qty} کارتن")

                    Spacer(Modifier.height(6.dp))
                    if (best != null) {
                        ColoredDimsRow(
                            best.sheet.sh, best.sheet.sw, null,
                            extraLabel = "${best.sheetsNeed ?: "—"} ورق",
                            small = true
                        )
                    } else {
                        Text("ورق مناسبی در انبار پیدا نشد.", fontSize = 12.sp, color = TextMuted)
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    PriceLine("فروش", formatNum(result.finalPrice), GreenDark)
                    Spacer(Modifier.height(4.dp))
                    PriceLine("تخفیف", formatNum(result.finalPrice - result.scrapWastePricePerCarton), Color(0xFFF97316))
                    Spacer(Modifier.height(4.dp))
                    PriceLine("پایه", formatNum(result.basePriceWithWaste), Red700)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ══ ۵ دکمه‌ی accordion رنگی — معادل card-action-row در وب:
            // قیمت(سبز) / شیت(زرد) / پرت(زرد) / تصویر شیت(آبی) / پیشنهاد شیت(سبز) ══
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccordionButton("قیمت", Green) {
                    openSection = if (openSection == ResultAccordion.PRICE) null else ResultAccordion.PRICE
                }
                AccordionButton("شیت", Gold) {
                    openSection = if (openSection == ResultAccordion.SHEET) null else ResultAccordion.SHEET
                }
                AccordionButton("پرت", Gold) {
                    openSection = if (openSection == ResultAccordion.WASTE) null else ResultAccordion.WASTE
                }
                AccordionButton("🖼️ تصویر شیت", Blue, enabled = best != null) {
                    openSection = if (openSection == ResultAccordion.IMG) null else ResultAccordion.IMG
                }
                AccordionButton("📐 پیشنهاد شیت", Green) {
                    val opening = openSection != ResultAccordion.SUGGEST
                    openSection = if (opening) ResultAccordion.SUGGEST else null
                    if (opening != suggestOpen) onToggleSuggest()
                }
            }

            Spacer(Modifier.height(12.dp))

            // ══ دکمه‌ی سبز تمام‌عرض «کپی متن» — معادل btn-cart c2-sms-btn در وب ══
            FullWidthGreenButton(label = "📤 کپی متن و باز کردن تماس‌ها")

            // ══ بخش «قیمت» — معادل c2sec-price (tint-green) در وب:
            // سود هر کارتن (زرد) / سود کل (قرمز) / قیمت کل کارتن‌ها (سبز بزرگ) ══
            if (openSection == ResultAccordion.PRICE) {
                Spacer(Modifier.height(12.dp))
                TintedSection(GreenBg) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("سود هر کارتن", formatNum(result.profitPerUnit), Modifier.weight(1f), variant = StatVariant.Yellow)
                        StatBox("سود کل", formatNum(result.totalProfit), Modifier.weight(1f), variant = StatVariant.Red)
                    }
                    Spacer(Modifier.height(10.dp))
                    StatBox("قیمت کل کارتن‌ها", formatNum(result.totalPrice), Modifier.fillMaxWidth(), big = true, variant = StatVariant.Green)
                }
            }

            // ══ بخش «شیت» — معادل c2sec-sheet (tint-yellow) در وب: ابعاد بازشده + کارت هر ورق پیشنهادی ══
            if (openSection == ResultAccordion.SHEET) {
                Spacer(Modifier.height(12.dp))
                TintedSection(StatYellowBg) {
                    StatBox(
                        "ابعاد ورق بازشده (طول × عرض)",
                        "${dimX(result.totalLength, result.totalWidth)} سانتی‌متر",
                        Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    if (best == null) {
                        EmptyHint("ورق سازگاری در انبار پیدا نشد.")
                    } else {
                        SuggestionCard(highlighted = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏆 ${dimX(best.sheet.sh, best.sheet.sw)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    if (best.canFulfill) "✔ کافی" else "✘ ناکافی",
                                    color = if (best.canFulfill) GreenDark else Red700,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MiniStat("کارتن در هر ورق", "${best.perSheet}", Modifier.weight(1f))
                                MiniStat("ورق موجود/لازم", "${best.sheetsNeed ?: "—"}", Modifier.weight(1f))
                                MiniStat("حداکثر کارتن", "${best.maxBoxes}", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ══ بخش «پرت» — معادل c2sec-waste (tint-yellow) در وب: جدول متر مربع/قیمت پرت/قیمت ضایعات ══
            if (openSection == ResultAccordion.WASTE) {
                Spacer(Modifier.height(12.dp))
                TintedSection(StatYellowBg) {
                    if (best == null) {
                        EmptyHint("ورق سازگاری در انبار پیدا نشد.")
                    } else {
                        SuggestionCard(highlighted = true) {
                            MiniStat("متر مربع پرت (هر ورق)", formatNum(best.wasteArea / 10000.0), Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            MiniStat("قیمت ضایعات کل این کارتن", formatNum(result.wasteCostTotal), Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            MiniStat("درصد پرت", "${best.wastePercent}%", Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // ══ بخش «تصویر شیت» — معادل c2sec-img (tint-blue) در وب ══
            if (openSection == ResultAccordion.IMG && best != null) {
                Spacer(Modifier.height(12.dp))
                TintedSection(BlueBg) {
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
            }

            // ══ بخش «پیشنهاد شیت» — معادل c2sec-suggest (tint-green) در وب ══
            if (openSection == ResultAccordion.SUGGEST) {
                Spacer(Modifier.height(12.dp))
                TintedSection(GreenBg) {
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
}

/** ابعاد رنگی کاراکتر‌به‌کاراکتر — معادل بدون فاصله <bdi> رنگی در inv-card-meta وب: L آبی، × مشکی، W قرمز، × مشکی، H سبز (در صورت وجود). */
@Composable
private fun ColoredDimsRow(a: Double, b: Double, c: Double?, extraLabel: String, small: Boolean = false) {
    val fs = if (small) 12.5.sp else 15.sp
    val fw = FontWeight.Bold
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dimNum(a), color = Blue, fontWeight = fw, fontSize = fs)
        Text("×", color = TextPrimary, fontWeight = fw, fontSize = fs)
        Text(dimNum(b), color = Red700, fontWeight = fw, fontSize = fs)
        if (c != null) {
            Text("×", color = TextPrimary, fontWeight = fw, fontSize = fs)
            Text(dimNum(c), color = Green, fontWeight = fw, fontSize = fs)
        }
        Text("  ·  $extraLabel", color = TextPrimary, fontWeight = fw, fontSize = fs)
    }
}

private fun dimNum(n: Double): String = if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

/** یک ردیف قیمت گوشه‌ی بالا-چپ کارت — معادل .inv-stat در inv-card-stats وب («فروش/تخفیف/پایه»). */
@Composable
private fun PriceLine(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Blue)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/** دکمه‌ی رنگی accordion — معادل btn-opt-green/yellow/blue در وب. */
@Composable
private fun AccordionButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color = if (enabled) color else color.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** دکمه‌ی سبز تمام‌عرض — معادل btn-cart در وب. */
@Composable
private fun FullWidthGreenButton(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Green, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** بخش رنگی داخلی — معادل .calc2-section-tinted در وب. */
@Composable
private fun TintedSection(tint: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = tint, shape = RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(content = content)
    }
}

/** دکمه‌ی بیضی‌شکل با حاشیه — معادل .btn.btn-flat در وب (پس‌زمینه‌ی خنثی، متن تیره، بدون سایه). */
@Composable
private fun PillOutlineButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (enabled) SurfaceDeep else SurfaceAlt,
                shape = RoundedCornerShape(50)
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) TextSecondary else TextMuted
        )
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
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    variant: StatVariant = StatVariant.Green
) {
    val (bg, borderColor, valueColor) = when (variant) {
        StatVariant.Green -> Triple(GreenBg, StatGreenBorder, GreenDark)
        StatVariant.Yellow -> Triple(StatYellowBg, StatYellowBorder, Color(0xFF92400E))
        StatVariant.Red -> Triple(StatRedBg, StatRedBorder, Red700)
        StatVariant.Blue -> Triple(BlueBg, StatBlueBorder, BlueDark)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = if (big) 14.dp else 10.dp, horizontal = 10.dp)
                // معادل border-right:3px solid در وب — نوار رنگی سمت راست باکس
                .background(Color.Transparent),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = if (big) 17.sp else 14.sp, color = valueColor)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = TextMuted)
        }
    }
}

private enum class StatVariant { Green, Yellow, Red, Blue }

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
    // معادل کارت آبی‌کم‌رنگ پایین فاکتور در وب (فیلد نام مشتری + دکمه‌ی قرمز «ثبت فاکتور»)
    Card(
        colors = CardDefaults.cardColors(containerColor = BlueBg),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatBlueBorder.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = onCustomerNameChange,
                    placeholder = { Text("نام مشتری", color = TextMuted) },
                    singleLine = true,
                    enabled = !submitted,
                    isError = customerError != null,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Red700,
                        unfocusedBorderColor = Red700.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.weight(1.6f)
                )
                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .background(
                            color = if (submitted) StatGreenBorder else Red700,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .then(
                            if (!submitted && !isSubmitting) Modifier.clickable(onClick = onSubmit) else Modifier
                        )
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            submitted -> "✔ ثبت شد"
                            isSubmitting -> "در حال ثبت..."
                            else -> "ثبت فاکتور"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            if (customerError == null && customerName.isBlank() && !submitted) {
                Spacer(Modifier.height(8.dp))
                Text("لطفاً نام مشتری را وارد کنید.", fontSize = 12.sp, color = TextMuted)
            }
            customerError?.let {
                Spacer(Modifier.height(8.dp))
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
    // معادل کارت جمع‌کل در پایین نتایج وب (border-color:brand): بالا مبلغ‌کل(سبز)/سود‌کل(قرمز)
    // در یک ردیف، پایین ضایعات‌کل(زرد-نارنجی) تمام‌عرض. سپس در پایین‌ترین بخش، کارت آبی
    // c2sec-invoice-all (که در SubmitInvoiceCard جداگانه رندر می‌شود).
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Red700),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("مبلغ کل", formatNum(totals.totalPrice), Modifier.weight(1f), variant = StatVariant.Green)
                StatBox("سود کل", formatNum(totals.totalProfit), Modifier.weight(1f), variant = StatVariant.Red)
            }
            Spacer(Modifier.height(10.dp))
            StatBox("ضایعات کل", formatNum(totals.totalWaste), Modifier.fillMaxWidth(), variant = StatVariant.Yellow)
        }
    }
}

