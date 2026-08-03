package com.rasticpack.app.ui.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasticpack.app.engine.CalculatorEngine
import com.rasticpack.app.engine.Grain
import com.rasticpack.app.engine.SheetItem
import com.rasticpack.app.engine.SheetMatchResult

/** یک ردیف ورق قابل‌ویرایش در فرم تست (مقدار به‌صورت متن نگه داشته می‌شود تا تایپ آزاد باشد). */
private class SheetRowState(sh: String, sw: String, qty: String) {
    var sh by mutableStateOf(sh)
    var sw by mutableStateOf(sw)
    var qty by mutableStateOf(qty)
}

@Composable
fun EngineTestScreen(onBack: () -> Unit) {
    // ── ورودی‌های کارتن ──
    var length by remember { mutableStateOf("") }   // L
    var width by remember { mutableStateOf("") }    // W
    var height by remember { mutableStateOf("") }   // H
    var glue by remember { mutableStateOf("4") }    // F (لپ چسب)
    var qtyNeed by remember { mutableStateOf("") }  // N (تعداد لازم)
    var grainHorizontal by remember { mutableStateOf(true) }

    // ── ورودی‌های ورق‌های موجود (پیش‌فرض = همان دو ورق نمونه‌ای که در seed نسخه‌ی وب هست) ──
    val sheetRows = remember {
        mutableStateListOf(
            SheetRowState(sh = "280", sw = "240", qty = "300"),
            SheetRowState(sh = "280", sw = "220", qty = "300")
        )
    }

    var resultText by remember { mutableStateOf<String?>(null) }
    var resultList by remember { mutableStateOf<List<SheetMatchResult>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun fillSample(sampleLength: Double, sampleWidth: Double, sampleHeight: Double, sampleGlue: Double, sampleQty: Int) {
        length = numToInput(sampleLength)
        width = numToInput(sampleWidth)
        height = numToInput(sampleHeight)
        glue = numToInput(sampleGlue)
        qtyNeed = sampleQty.toString()
        resultText = null
        resultList = emptyList()
        errorText = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🧮 تست موتور محاسبه",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }

        Text(
            text = "این صفحه فقط برای تست خام موتور محاسبه است — ظاهرش با اپ نهایی فرقی ندارد چون هنوز UI اصلی ساخته نشده. " +
                "دو دکمه‌ی «نمونه ۱» و «نمونه ۲» دقیقاً همان اعداد calc2SampleDefaults نسخه‌ی وب را پر می‌کنند تا بتوانید " +
                "با محاسبه کارتن در 4.html مقایسه کنید.",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { fillSample(60.0, 40.0, 40.0, 4.0, 100) }) {
                Text("نمونه ۱ (۶۰×۴۰×۴۰)")
            }
            OutlinedButton(onClick = { fillSample(30.0, 20.0, 30.0, 2.0, 100) }) {
                Text("نمونه ۲ (۳۰×۲۰×۳۰)")
            }
        }

        SectionCard(title = "ابعاد کارتن") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("طول L", length, { length = it }, Modifier.weight(1f))
                NumField("عرض W", width, { width = it }, Modifier.weight(1f))
                NumField("ارتفاع H", height, { height = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("لپ چسب F", glue, { glue = it }, Modifier.weight(1f))
                NumField("تعداد لازم N", qtyNeed, { qtyNeed = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChipLike("↔ افقی", grainHorizontal) { grainHorizontal = true }
                FilterChipLike("↕ عمودی", !grainHorizontal) { grainHorizontal = false }
            }
        }

        SectionCard(title = "ورق‌های موجود در انبار (برای تست)") {
            sheetRows.forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumField("طول ورق", row.sh, { row.sh = it }, Modifier.weight(1f))
                    NumField("عرض ورق", row.sw, { row.sw = it }, Modifier.weight(1f))
                    NumField("موجودی", row.qty, { row.qty = it }, Modifier.weight(1f))
                    TextButton(onClick = { if (sheetRows.size > 1) sheetRows.removeAt(idx) }) {
                        Text("−", fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            TextButton(onClick = { sheetRows.add(SheetRowState("", "", "")) }) {
                Text("+ افزودن ورق")
            }
        }

        Button(
            onClick = {
                errorText = null
                resultText = null
                resultList = emptyList()
                try {
                    val l = length.toDouble()
                    val w = width.toDouble()
                    val h = height.toDouble()
                    val g = glue.toDoubleOrNull() ?: 4.0
                    val need = qtyNeed.toIntOrNull() ?: 0
                    if (l <= 0 || w <= 0 || h <= 0 || need <= 0) {
                        errorText = "ابعاد کارتن و تعداد لازم را کامل و درست وارد کنید."
                        return@Button
                    }
                    val grain = if (grainHorizontal) Grain.HORIZONTAL else Grain.VERTICAL
                    val (bw, bh) = CalculatorEngine.expandedCartonDims(l, w, h, g)

                    val inventory = sheetRows.mapIndexedNotNull { idx, row ->
                        val sh = row.sh.toDoubleOrNull() ?: return@mapIndexedNotNull null
                        val sw = row.sw.toDoubleOrNull() ?: return@mapIndexedNotNull null
                        val qty = row.qty.toIntOrNull() ?: 0
                        if (sh <= 0 || sw <= 0) return@mapIndexedNotNull null
                        SheetItem(id = idx, sw = sw, sh = sh, qty = qty)
                    }

                    val header = buildString {
                        appendLine("ابعاد بازشده (bw × bh) = ${fmt(bw)} × ${fmt(bh)} سانتی‌متر")
                        appendLine("جهت گوشت: ${if (grainHorizontal) "افقی" else "عمودی"}")
                    }
                    resultText = header
                    resultList = CalculatorEngine.matchSheets(
                        inventory = inventory,
                        bw = bw, bh = bh, need = need, grain = grain
                    )
                    if (resultList.isEmpty()) {
                        errorText = "هیچ ورقی برای این کارتن کافی نبود — ابعاد ورق‌ها یا موجودی را بررسی کنید."
                    }
                } catch (e: Exception) {
                    errorText = "ورودی نامعتبر: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("محاسبه", fontWeight = FontWeight.Bold)
        }

        errorText?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        resultText?.let { header ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(header, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        resultList.forEachIndexed { idx, r ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (idx == 0)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "${if (idx == 0) "🏆" else "▫️"} ورق ${fmt(r.sheet.sh)}×${fmt(r.sheet.sw)}  (موجودی ${r.sheet.qty})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Divider()
                    Spacer(Modifier.height(6.dp))
                    ResultLine("چیدمان مستقیم (ستون × ردیف)", "${r.layout.cols} × ${r.layout.rows}")
                    ResultLine("تعداد کارتن در هر ورق", r.perSheet.toString())
                    ResultLine("جهت صحیح / ناصحیح", "${r.layout.correct} / ${r.layout.wrong}")
                    ResultLine("تعداد ورق لازم", r.sheetsNeed?.toString() ?: "∞ (اصلاً جا نمی‌شود)")
                    ResultLine("کافی بودن موجودی", if (r.canFulfill) "✔ کافی" else "✘ ناکافی")
                    ResultLine("حداکثر کارتن با این موجودی", r.maxBoxes.toString())
                    ResultLine("کسری", r.shortage.toString())
                    ResultLine("درصد پرت", "${r.wastePercent}%")
                    ResultLine("پرت هر ورق (سانتی‌متر مربع)", fmt(r.wasteArea))
                    ResultLine("امتیاز رتبه‌بندی (score)", fmt(r.score))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label, fontSize = 13.sp) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, fontSize = 13.sp) }
    }
}

private fun fmt(n: Double): String {
    val rounded = Math.round(n * 100) / 100.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

private fun numToInput(n: Double): String =
    if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
