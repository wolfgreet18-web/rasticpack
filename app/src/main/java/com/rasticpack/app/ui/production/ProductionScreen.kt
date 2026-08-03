package com.rasticpack.app.ui.production

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.ProductionQueueItemEntity
import com.rasticpack.app.ui.invoices.JalaliDate
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary

/**
 * تب «مراحل تولید» — زیرمرحله‌ی ۸.۱: صف تولید + فرم ابعاد + محاسبه‌ی عددی.
 * رسم گرافیکی بلانک (svgBlank و بقیه‌ی توابع SVG وب) در زیرمرحله‌ی ۸.۲ اضافه می‌شود؛
 * فعلاً نتیجه‌ی محاسبه به‌صورت جدول ابعاد نمایش داده می‌شود.
 *
 * initialApplyId: اگر از تب فاکتورها با دکمه‌ی 🏭 وارد این صفحه شده باشیم، شناسه‌ی
 * رکورد صف تولیدی که باید بلافاصله در فرم اعمال شود (معادل applyProductionItem(firstId)
 * در انتهای sendAllToProduction وب).
 */
@Composable
fun ProductionScreen(onBack: () -> Unit, initialApplyId: Int? = null) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val viewModel = remember { ProductionViewModel(db) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialApplyId, state.queue) {
        if (initialApplyId != null && state.queue.any { it.id == initialApplyId }) {
            viewModel.applyItemById(initialApplyId)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏭 مراحل تولید", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = onBack) { Text("بازگشت") }
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (state.queue.isEmpty()) {
                    Text(
                        "هنوز کارتنی به مراحل تولید ارسال نشده — از تب «فاکتورها» با دکمه 🏭 ارسال کنید.",
                        fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Text("صف تولید (${JalaliDate.toFaDigits(state.queue.size)})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            }
            items(state.queue, key = { it.id }) { p ->
                ProductionQueueCard(
                    item = p,
                    onApply = { viewModel.applyItem(p) },
                    onRemove = { viewModel.removeItem(p.id) }
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            if (!state.dimsFormCollapsed) {
                item {
                    DimsFormCard(
                        state = state,
                        onSL = viewModel::updateSL, onSW = viewModel::updateSW,
                        onBL = viewModel::updateBL, onBW = viewModel::updateBW, onBH = viewModel::updateBH,
                        onGlue = viewModel::updateGlue,
                        onCalculate = viewModel::calculate
                    )
                }
            } else {
                item {
                    OutlinedButton(onClick = viewModel::expandDimsForm, modifier = Modifier.fillMaxWidth()) {
                        Text("✏️ نمایش/ویرایش دوباره‌ی ابعاد")
                    }
                }
            }

            item {
                MachineTabs(machine = state.machine, onSelect = viewModel::setMachine)
            }

            state.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            state.result?.let { r ->
                item {
                    BlankResultCard(r)
                }
            }
        }
    }
}

@Composable
private fun ProductionQueueCard(item: ProductionQueueItemEntity, onApply: () -> Unit, onRemove: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onApply() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.background(color = SurfaceAlt, shape = CircleShape).padding(8.dp)
            ) { Text("📦", fontSize = 14.sp) }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "${fmtDim(item.length)},${fmtDim(item.width)},${fmtDim(item.height)} سانتیمتر · ورق ${fmtDim(item.sh)}×${fmtDim(item.sw)} (${layerLabel(item.layer)})" +
                        if (item.customerName.isNotBlank()) " · ${item.customerName}" else "",
                    fontSize = 11.sp, color = TextSecondary
                )
            }
            IconButton(onClick = onRemove) { Text("🗑️", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun DimsFormCard(
    state: ProductionUiState,
    onSL: (String) -> Unit, onSW: (String) -> Unit,
    onBL: (String) -> Unit, onBW: (String) -> Unit, onBH: (String) -> Unit,
    onGlue: (String) -> Unit,
    onCalculate: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("ابعاد ورق خام (سانتی‌متر)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NumField("طول ورق (L)", state.sL, onSL, Modifier.weight(1f))
                NumField("عرض ورق (W)", state.sW, onSW, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Text("ابعاد کارتن نهایی (سانتی‌متر)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NumField("طول (L)", state.bL, onBL, Modifier.weight(1f))
                NumField("عرض (W)", state.bW, onBW, Modifier.weight(1f))
                NumField("ارتفاع (H)", state.bH, onBH, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Text("لپ چسب (سانتی‌متر)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            NumField("لب چسب (پیش‌فرض ۳.۵)", state.glue, onGlue, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth()) {
                Text("محاسبه")
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun MachineTabs(machine: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MachineTabButton("برش", machine == "m", Modifier.weight(1f)) { onSelect("m") }
        MachineTabButton("چاک", machine == "c", Modifier.weight(1f)) { onSelect("c") }
    }
}

@Composable
private fun MachineTabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) Color(0xFFFEF2F2) else SurfaceAlt,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Red700 else TextSecondary)
    }
}

@Composable
private fun BlankResultCard(r: BlankCalcResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            if (!r.fits) {
                Text(
                    "⚠️ از این شیت هیچ کارتنی درنمی‌آد. حداقل نیاز: ${fmtDim(r.blankLen)} × ${fmtDim(r.blankHt)} سانت",
                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
            }
            Text("نتیجه‌ی محاسبه", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            ResultRow("طول کل بازشده (L مضاعف + عرض + لپ چسب)", "${fmtDim(r.blankLen)} سانت")
            ResultRow("عرض کل بازشده (H + W)", "${fmtDim(r.blankHt)} سانت")
            ResultRow("مساحت هر بلانک", "${r.areaM2} متر مربع")
            ResultRow("لپ چسب", "${fmtDim(r.glue)} سانت")
            ResultRow("ارتفاع درپوش (W/2)", "${fmtDim(r.flapH)} سانت")
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            ResultRow("تعداد ردیف روی شیت خام", JalaliDate.toFaDigits(r.bestNL))
            ResultRow("تعداد ستون روی شیت خام", JalaliDate.toFaDigits(r.bestNW))
            ResultRow("جمع کارتن روی هر شیت", JalaliDate.toFaDigits(r.bestCount))

            if (r.fits) {
                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(12.dp))
                Text("🖼️ نقشه‌ی بلانک (خط تا آبی · خط برش قرمز)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                BlankSingleCanvas(r, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                BlankLegend()

                Spacer(Modifier.height(18.dp))
                Text("📐 چیدمان روی شیت خام", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                BlankSheetCutCanvas(r, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BlankLegend() {
    Column(modifier = Modifier.fillMaxWidth()) {
        LegendRow(Color(0xFFBBF7D0), "پنل طول (L)")
        LegendRow(Color(0xFFFED7AA), "پنل عرض (W)")
        LegendRow(Color(0xFFFBCFE8), "درپوش (H/2)")
        LegendRow(Color(0xFFFEF08A), "لپ چسب")
        LegendRow(Color(0xFF2563EB), "خط تا")
        LegendRow(Color(0xFFDC2626), "خط برش")
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 14.dp)
                .background(color, shape = RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun layerLabel(layer: String) = if (layer == "5") "پنج‌لایه" else "سه‌لایه"
private fun fmtDim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
