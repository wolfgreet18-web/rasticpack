package com.rasticpack.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.data.repo.PricingRepository
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.DangerRed
import com.rasticpack.app.ui.theme.GoldLight
import com.rasticpack.app.ui.theme.Green
import com.rasticpack.app.ui.theme.GreenBg
import com.rasticpack.app.ui.theme.GreenDark
import com.rasticpack.app.ui.theme.Red100
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary

private val CATEGORIES = listOf("KT", "2T", "E")

@Composable
fun InventoryScreen(onBack: () -> Unit) {
    val viewModel: InventoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    val layer = state.layerView
    val filter = viewModel.filterFor(layer)
    val sheets = viewModel.sheetsFor(layer, filter)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📋 موجودی ورق", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f)) {
                ChipToggle("سه‌لایه", layer == "3") { viewModel.setLayerView("3") }
                Spacer(Modifier.width(6.dp))
                ChipToggle("پنج‌لایه", layer == "5") { viewModel.setLayerView("5") }
            }
            IconButton(onClick = { viewModel.openFreightDialog() }) {
                Text("🚚", fontSize = 22.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            CATEGORIES.forEach { cat ->
                ChipToggle(cat, filter == cat) { viewModel.setFilter(layer, cat) }
                Spacer(Modifier.width(6.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        if (filter == null) {
            Text(
                "برای مشاهده ورق‌ها، یکی از فیلترهای KT / 2T / E را انتخاب کنید",
                fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            PriceBox(
                layer = layer, category = filter, state = state,
                onToggle = { viewModel.openPriceEdit(layer, filter) },
                onProductChange = viewModel::updatePriceEditProduct,
                onFreightChange = viewModel::updatePriceEditFreight,
                onSave = { viewModel.savePriceEdit(layer, filter) }
            )

            Spacer(Modifier.height(14.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sheets.isEmpty()) {
                    item {
                        Text("هیچ ورقی در این بخش ثبت نشده", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
                    }
                } else {
                    items(sheets, key = { it.id }) { sheet ->
                        SheetRow(
                            sheet = sheet,
                            onQtyChange = { viewModel.updateQty(sheet.id, it) },
                            onDimChange = { field, v -> viewModel.updateDim(sheet.id, field, v) },
                            onDelete = { viewModel.deleteSheet(sheet.id) }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(10.dp))
                    AddSheetRow(
                        sh = state.newSh, sw = state.newSw, qty = state.newQty,
                        onShChange = viewModel::updateNewSh, onSwChange = viewModel::updateNewSw, onQtyChange = viewModel::updateNewQty,
                        layerLabel = InventoryViewModel.layerLabel(layer),
                        onAdd = { viewModel.addSheet(layer) }
                    )
                    state.addError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, color = Red700)
                    }
                }
            }
        }
    }

    if (state.showFreightDialog) {
        TruckFreightDialog(state = state, viewModel = viewModel)
    }
}

@Composable
private fun PriceBox(
    layer: String,
    category: String,
    state: InventoryUiState,
    onToggle: () -> Unit,
    onProductChange: (String) -> Unit,
    onFreightChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val key = "$layer-$category"
    val price = state.priceBreakdowns[key]?.total ?: 0.0
    val editing = state.priceEditKey == key

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (price > 0) InventoryViewModel.fmtNum(price) else "—",
                fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary
            )
            Text(
                "قیمت کل هر متر مربع — ${InventoryViewModel.layerLabel(layer)} · دسته $category",
                fontSize = 11.sp, color = TextMuted
            )
        }
    }

    if (editing) {
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.priceEditProduct,
                        onValueChange = onProductChange,
                        label = { Text("💰 قیمت محصول", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = state.priceEditFreight,
                        onValueChange = onFreightChange,
                        label = { Text("🚚 کرایه حمل", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("💾 ذخیره قیمت") }
            }
        }
    }
}

@Composable
private fun SheetRow(
    sheet: InventorySheetEntity,
    onQtyChange: (String) -> Unit,
    onDimChange: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    val (bg, border) = when {
        sheet.qty == 0 -> Red100 to DangerRed
        sheet.qty < 50 -> GoldLight to Color(0xFFD97706)
        else -> GreenBg to Green
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallEditableNumber(value = sheet.sh, modifier = Modifier.weight(1f)) { onDimChange("sh", it) }
            SmallEditableNumber(value = sheet.sw, modifier = Modifier.weight(1f)) { onDimChange("sw", it) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(color = bg, shape = RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                OutlinedTextField(
                    value = sheet.qty.toString(),
                    onValueChange = onQtyChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onDelete) { Text("🗑", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun SmallEditableNumber(value: Double, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = InventoryViewModel.fmtDim(value),
        onValueChange = onChange,
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun AddSheetRow(
    sh: String, sw: String, qty: String,
    onShChange: (String) -> Unit, onSwChange: (String) -> Unit, onQtyChange: (String) -> Unit,
    layerLabel: String,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sh, onValueChange = onShChange, label = { Text("L", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = sw, onValueChange = onSwChange, label = { Text("W", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = qty, onValueChange = onQtyChange, label = { Text("N", fontSize = 11.sp) }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ افزودن $layerLabel") }
        }
    }
}

@Composable
private fun ChipToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

/**
 * پنجره‌ی ماشین‌حساب «کرایه حمل بار ماشین» (حالت دستی) — معادل truck-freight-modal + حالت
 * manual در وب. هر ردیف: طول/عرض/تعداد + دسته (لایه، KT/2T/E). بعد از محاسبه، برای هر
 * دسته‌ی قیمتی می‌توان کرایه را روی قیمت اعمال کرد و/یا ورق‌ها را با تعداد واردشده به موجودی افزود.
 */
@Composable
private fun TruckFreightDialog(state: InventoryUiState, viewModel: InventoryViewModel) {
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    Dialog(onDismissRequest = { viewModel.closeFreightDialog() }) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.heightIn(max = maxDialogHeight)) {
                // ── هدر ثابت: عنوان + دکمه بستن + تاب دستی/اتوماتیک — خارج از اسکرول (flex-shrink:0 معادل وب) ──
                Column(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚚 کرایه حمل بار ماشین", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        TextButton(onClick = { viewModel.closeFreightDialog() }) { Text("✕") }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipToggle("دستی", state.freightMode == "manual") { viewModel.setFreightMode("manual") }
                        ChipToggle("اتوماتیک", state.freightMode == "auto") { viewModel.setFreightMode("auto") }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── بخش اسکرول‌شونده: محتوای حالت دستی/اتوماتیک (خودشان LazyColumn با weight(1f) دارند) ──
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    if (state.freightMode == "manual") {
                        ManualFreightContent(state = state, viewModel = viewModel)
                    } else {
                        AutoFreightContent(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualFreightContent(state: InventoryUiState, viewModel: InventoryViewModel) {
    Text(
        "برای هر نوع ورقی که این ماشین آورده، طول×عرض، لایه و دسته (KT/2T/E) را وارد کن و تعدادش را بزن — " +
            "بعد مبلغ کل کرایه‌ی این بار را وارد کن. کرایه‌ی هر متر مربع خودش محاسبه می‌شود.",
        fontSize = 11.sp, color = TextSecondary, lineHeight = 17.sp
    )

    Spacer(Modifier.height(12.dp))

    LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.freightItems, key = { it.localId }) { item ->
            FreightItemCard(
                item = item,
                showDelete = state.freightItems.size > 1,
                uniqueDims = state.uniqueDims,
                onChange = { updated -> viewModel.updateFreightItem(item.localId) { updated } },
                onRemove = { viewModel.removeFreightItem(item.localId) },
                onTogglePreset = { viewModel.togglePresetList(item.localId) },
                onSelectPreset = { sh, sw -> viewModel.selectPreset(item.localId, sh, sw) }
            )
        }

        item {
            OutlinedButton(onClick = { viewModel.addFreightItem() }, modifier = Modifier.fillMaxWidth()) {
                Text("+ افزودن نوع ورق")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.freightTotalText,
                onValueChange = { viewModel.updateFreightTotal(it) },
                label = { Text("مبلغ کل کرایه این بار (ریال)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            state.freightError?.let {
                Spacer(Modifier.height(6.dp))
                FreightErrorBanner(it)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { viewModel.calcFreight() }, modifier = Modifier.fillMaxWidth()) {
                Text("🚚 محاسبه کرایه هر متر مربع")
            }

            state.freightResult?.let { result ->
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = GoldLight),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(InventoryViewModel.fmtNum(result.freightPerM2), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "کرایه هر متر مربع — بر اساس کل بار (${"%.2f".format(result.totalArea)} متر مربع)",
                            fontSize = 10.sp, color = TextMuted
                        )
                    }
                }

                result.groups.forEach { g ->
                    Spacer(Modifier.height(10.dp))
                    FreightGroupCard(
                        group = g,
                        layerLabel = InventoryViewModel.layerLabel(g.layer),
                        priceApplied = state.freightAppliedPriceKeys.contains(g.priceKey),
                        stockApplied = state.freightAppliedStockKeys.contains(g.priceKey),
                        onApplyPrice = { viewModel.applyFreightToPrice(g.priceKey) },
                        onApplyStock = { viewModel.applyFreightToStock(g.priceKey) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * حالت اتوماتیک کرایه حمل (۴.۳) — معادل truck-freight-auto در وب.
 * لیست ابعاد آماده برای لایه‌ی انتخاب‌شده؛ کلیک ساده پنل تعداد/دسته را باز می‌کند،
 * نگه‌داشتن کوتاه (long-press) آن ابعاد را برای محاسبه‌ی کرایه «انتخاب» می‌کند (سبز می‌شود).
 */
@Composable
private fun AutoFreightContent(state: InventoryUiState, viewModel: InventoryViewModel) {
    Text(
        "یکی از ابعادهای آماده را نگه دارید تا انتخاب شود (سبز می‌شود) — دوباره نگه دارید تا لغو شود. " +
            "برای ابعادهای انتخاب‌شده تعداد و دسته (KT/2T/E) را وارد کنید، سپس مبلغ کل کرایه را بزنید.",
        fontSize = 11.sp, color = TextSecondary, lineHeight = 17.sp
    )

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipToggle("سه‌لایه", state.autoLayer == "3") { viewModel.setAutoLayer("3") }
        ChipToggle("پنج‌لایه", state.autoLayer == "5") { viewModel.setAutoLayer("5") }
    }
    Spacer(Modifier.height(10.dp))

    LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TRUCK_FREIGHT_AUTO_DIMS_LIST, key = { "${state.autoLayer}-${it.first}-${it.second}" }) { (sh, sw) ->
            val key = autoDimKey(state.autoLayer, sh, sw)
            val dimState = state.autoDims[key] ?: AutoDimState()
            val open = state.autoOpenKey == key || dimState.selected
            AutoDimCard(
                sh = sh, sw = sw,
                selected = dimState.selected,
                open = open,
                qty = dimState.qty,
                category = dimState.category,
                onClick = { viewModel.toggleAutoDimOpen(sh, sw) },
                onLongPress = { viewModel.toggleAutoDimSelect(sh, sw) },
                onQtyChange = { viewModel.updateAutoDimQty(sh, sw, it) },
                onCategoryChange = { viewModel.setAutoDimCategory(sh, sw, it) }
            )
        }

        item {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.autoTotalText,
                onValueChange = { viewModel.updateAutoTotal(it) },
                label = { Text("مبلغ کل کرایه این بار (ریال)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            state.autoError?.let {
                Spacer(Modifier.height(6.dp))
                FreightErrorBanner(it)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { viewModel.calcAutoFreight() }, modifier = Modifier.fillMaxWidth()) {
                Text("🚚 محاسبه کرایه هر متر مربع")
            }

            state.autoResult?.let { result ->
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = GoldLight),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(InventoryViewModel.fmtNum(result.freightPerM2), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "کرایه هر متر مربع — بر اساس کل بار (${"%.2f".format(result.totalArea)} متر مربع)",
                            fontSize = 10.sp, color = TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                FreightAutoChart(result)

                result.groups.forEach { g ->
                    Spacer(Modifier.height(10.dp))
                    FreightGroupCard(
                        group = g,
                        layerLabel = InventoryViewModel.layerLabel(g.layer),
                        priceApplied = state.autoAppliedPriceKeys.contains(g.priceKey),
                        stockApplied = state.autoAppliedStockKeys.contains(g.priceKey),
                        onApplyPrice = { viewModel.applyAutoFreightToPrice(g.priceKey) },
                        onApplyStock = { viewModel.applyAutoFreightToStock(g.priceKey) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutoDimCard(
    sh: Double, sw: Double,
    selected: Boolean, open: Boolean,
    qty: String, category: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onQtyChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    val shLabel = InventoryViewModel.fmtDim(sh)
    val swLabel = InventoryViewModel.fmtDim(sw)
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (selected) Green else if (open) Red100 else SurfaceAlt,
                    shape = RoundedCornerShape(10.dp)
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$shLabel×$swLabel",
                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = qty, onValueChange = onQtyChange,
                        label = { Text("تعداد", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    CATEGORIES.forEach { cat ->
                        ChipToggle(cat, category == cat) { onCategoryChange(cat) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreightItemCard(
    item: FreightItemInput,
    showDelete: Boolean,
    uniqueDims: List<Pair<Double, Double>>,
    onChange: (FreightItemInput) -> Unit,
    onRemove: () -> Unit,
    onTogglePreset: () -> Unit,
    onSelectPreset: (Double, Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            // ── دکمه‌ی «انتخاب ابعاد آماده» — معادل truckFreightPresetListHtml در وب ──
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (item.presetOpen) 180f else 0f, label = "presetArrow"
            )
            OutlinedButton(onClick = onTogglePreset, modifier = Modifier.fillMaxWidth()) {
                Text(
                    buildString {
                        append("📐 انتخاب ابعاد آماده")
                        if (item.sh.isNotBlank() && item.sw.isNotBlank()) {
                            append(" (${item.sh}×${item.sw})")
                        }
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "▾", fontSize = 11.sp,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }

            if (item.presetOpen) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (uniqueDims.isEmpty()) {
                        Text(
                            "هنوز ورقی در موجودی ثبت نشده — از تب «ورق» اضافه کنید.",
                            fontSize = 11.sp, color = TextMuted
                        )
                    } else {
                        val curSh = item.sh.toDoubleOrNull()
                        val curSw = item.sw.toDoubleOrNull()
                        uniqueDims.forEach { (sh, sw) ->
                            val selected = curSh == sh && curSw == sw
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(9.dp)
                                    )
                                    .border(
                                        1.5.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else BorderColor,
                                        RoundedCornerShape(9.dp)
                                    )
                                    .clickable { onSelectPreset(sh, sw) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${InventoryViewModel.fmtDim(sh)}×${InventoryViewModel.fmtDim(sw)}",
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (selected) {
                                    Text("✔", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                ChipToggle("۳ لایه", item.layer == "3") { onChange(item.copy(layer = "3")) }
                ChipToggle("۵ لایه", item.layer == "5") { onChange(item.copy(layer = "5")) }
                // خط جداکننده‌ی عمودی باریک بین دو گروه لایه/دسته — معادل دو subtype-toggle مجزا در وب
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                CATEGORIES.forEach { cat ->
                    ChipToggle(cat, item.category == cat) { onChange(item.copy(category = cat)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = BorderColor
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = item.sh, onValueChange = { onChange(item.copy(sh = it)) }, label = { Text("طول", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = item.sw, onValueChange = { onChange(item.copy(sw = it)) }, label = { Text("عرض", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = item.qty, onValueChange = { onChange(item.copy(qty = it)) }, label = { Text("تعداد", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                if (showDelete) {
                    IconButton(onClick = onRemove) { Text("−", fontSize = 18.sp, color = Red700) }
                }
            }
        }
    }
}

@Composable
/**
 * نمودار میله‌ای افقی سهم هر ابعاد (نه هر گروه قیمتی) از کرایه‌ی کل — معادل
 * renderTruckFreightAutoChart در وب. برخلاف FreightGroupCard که بر مبنای priceKey
 * گروه‌بندی می‌کند، این نمودار روی سطح تک‌ردیف (هر ترکیب ابعاد+دسته‌ی انتخاب‌شده) کار می‌کند.
 */
@Composable
private fun FreightAutoChart(result: FreightCalcResult) {
    val rows = result.groups.flatMap { it.rows }
    if (rows.isEmpty()) return
    val maxShare = rows.maxOf { it.shareCost }.let { if (it <= 0) 1.0 else it }

    Column(Modifier.fillMaxWidth()) {
        Text("📊 سهم هر ابعاد از کرایه کل", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(SurfaceAlt, RoundedCornerShape(10.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            rows.forEach { row ->
                val heightDp = (4 + (row.shareCost / maxShare) * 106).dp
                Column(
                    modifier = Modifier.width(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(InventoryViewModel.fmtNum(row.shareCost), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightDp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    val category = PricingRepository.priceCategoryOf(row.paperType, row.flute)
                    Text(
                        "${InventoryViewModel.fmtDim(row.sh)}×${InventoryViewModel.fmtDim(row.sw)}",
                        fontSize = 10.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(category, fontSize = 10.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/**
 * باکس هشدار قرمزکم‌رنگ برای freightError/autoError — معادل کلاس alert-danger در وب
 * (پس‌زمینه‌ی صورتی کم‌رنگ + حاشیه + آیکون ⚠️ کنار متن، به‌جای یک خط متن قرمز ساده).
 */
@Composable
private fun FreightErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), // معادل var(--red-50)
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(11.dp, 9.dp), verticalAlignment = Alignment.Top) {
            Text("⚠️", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(message, fontSize = 12.sp, color = Red700)
        }
    }
}

@Composable
private fun FreightGroupCard(
    group: FreightGroupResult,
    layerLabel: String,
    priceApplied: Boolean,
    stockApplied: Boolean,
    onApplyPrice: () -> Unit,
    onApplyStock: () -> Unit
) {
    Card(
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("$layerLabel · دسته ${group.category}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(group.dimsLabel, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniValue("قیمت فعلی", InventoryViewModel.fmtNum(group.oldPrice), Modifier.weight(1f))
                MiniValue("قیمت پس از جایگزینی کرایه", InventoryViewModel.fmtNum(group.newPrice), Modifier.weight(1f), highlight = true)
            }
            Spacer(Modifier.height(6.dp))
            Text("سهم این نوع از کرایه کل: ${InventoryViewModel.fmtNum(group.shareCost)} ریال", fontSize = 10.sp, color = TextMuted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApplyPrice, enabled = !priceApplied, modifier = Modifier.weight(1f)) {
                    Text(if (priceApplied) "✔ کرایه اعمال شد" else "🚚 اعمال کرایه", fontSize = 12.sp)
                }
                Button(onClick = onApplyStock, enabled = !stockApplied, modifier = Modifier.weight(1f)) {
                    Text(if (stockApplied) "✔ به موجودی اضافه شد" else "📦 افزودن به موجودی", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniValue(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (highlight) GreenDark else MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 10.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
