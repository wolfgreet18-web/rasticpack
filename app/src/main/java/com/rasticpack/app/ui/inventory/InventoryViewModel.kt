package com.rasticpack.app.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.data.repo.InventoryRepository
import com.rasticpack.app.data.repo.PricingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * معادل بخش‌های «TAB — موجودی ورق» و «کرایه حمل ورق» در 4.html.
 * فیلترها (KT/2T/E) برای هر لایه مستقل نگه داشته می‌شوند — دقیقاً مثل inventoryFilter در وب.
 */
data class InventoryUiState(
    val layerView: String = "3",
    val filter3: String? = null,
    val filter5: String? = null,
    val sheets: List<InventorySheetEntity> = emptyList(),
    val priceBreakdowns: Map<String, PricingRepository.PriceBreakdown> = emptyMap(),
    val newSh: String = "",
    val newSw: String = "",
    val newQty: String = "",
    val addError: String? = null,
    // popup تفکیک قیمت (محصول + کرایه) — priceEditKey غیرنال یعنی باز است
    val priceEditKey: String? = null,
    val priceEditProduct: String = "",
    val priceEditFreight: String = "",
    // ماشین‌حساب کرایه حمل (حالت دستی)
    val showFreightDialog: Boolean = false,
    val freightItems: List<FreightItemInput> = listOf(FreightItemInput(1), FreightItemInput(2)),
    val freightTotalText: String = "",
    val freightError: String? = null,
    val freightResult: FreightCalcResult? = null,
    val freightAppliedPriceKeys: Set<String> = emptySet(),
    val freightAppliedStockKeys: Set<String> = emptySet(),
    // حالت دستی/اتوماتیک — معادل truckFreightMode در وب
    val freightMode: String = "manual",
    // حالت اتوماتیک کرایه حمل (۴.۳) — معادل truckFreightAuto* در وب
    val autoLayer: String = "3",
    val autoOpenKey: String? = null,
    val autoDims: Map<String, AutoDimState> = emptyMap(),
    val autoTotalText: String = "",
    val autoError: String? = null,
    val autoResult: FreightCalcResult? = null,
    val autoAppliedPriceKeys: Set<String> = emptySet(),
    val autoAppliedStockKeys: Set<String> = emptySet()
)


class InventoryViewModel(private val db: AppDatabase) : ViewModel() {

    private val inventoryRepo = InventoryRepository(db)
    private val pricingRepo = PricingRepository(db)

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private var freightNextLocalId = 3

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val sheets = inventoryRepo.getAll()
            val breakdowns = pricingRepo.getPriceBreakdowns()
            _uiState.update { it.copy(sheets = sheets, priceBreakdowns = breakdowns) }
        }
    }

    // ══ سوییچ لایه/فیلتر — معادل setInventoryLayerView/setInventoryFilter در وب ══

    fun setLayerView(layer: String) = _uiState.update { it.copy(layerView = layer) }

    fun filterFor(layer: String): String? =
        if (layer == "3") _uiState.value.filter3 else _uiState.value.filter5

    fun setFilter(layer: String, value: String) {
        _uiState.update { st ->
            if (layer == "3") {
                st.copy(filter3 = if (st.filter3 == value) null else value)
            } else {
                st.copy(filter5 = if (st.filter5 == value) null else value)
            }
        }
    }

    /** ورق‌های همین لایه+فیلتر، مرتب — معادل renderInventorySection در وب */
    fun sheetsFor(layer: String, filter: String?): List<InventorySheetEntity> {
        if (filter == null) return emptyList()
        return _uiState.value.sheets.filter { s ->
            (s.layer.ifBlank { "3" }) == layer && when (filter) {
                "KT" -> (s.paperType.ifBlank { "2T" }) == "KT"
                "2T" -> (s.paperType.ifBlank { "2T" }) == "2T"
                "E" -> (s.flute.ifBlank { "C" }) == "E"
                else -> false
            }
        }
    }

    // ══ افزودن/ویرایش/حذف ورق ══

    fun updateNewSh(v: String) = _uiState.update { it.copy(newSh = v, addError = null) }
    fun updateNewSw(v: String) = _uiState.update { it.copy(newSw = v, addError = null) }
    fun updateNewQty(v: String) = _uiState.update { it.copy(newQty = v, addError = null) }

    /** معادل paperFluteFromFilter در وب — از فیلتر فعال، paperType/flute استخراج می‌شود */
    private fun paperFluteFromFilter(filter: String): Pair<String, String> = when (filter) {
        "2T" -> "2T" to "C"
        "E" -> "KT" to "E"
        else -> "KT" to "C" // KT
    }

    fun addSheet(layer: String) {
        val filter = filterFor(layer) ?: return
        val st = _uiState.value
        val sh = st.newSh.toDoubleOrNull() ?: 0.0
        val sw = st.newSw.toDoubleOrNull() ?: 0.0
        val qty = st.newQty.toIntOrNull() ?: 0
        val (paperType, flute) = paperFluteFromFilter(filter)
        viewModelScope.launch {
            val error = inventoryRepo.addSheet(sh, sw, layer, qty, flute, paperType)
            if (error != null) {
                _uiState.update { it.copy(addError = error) }
            } else {
                _uiState.update { it.copy(newSh = "", newSw = "", newQty = "", addError = null) }
                refresh()
            }
        }
    }

    fun updateQty(id: Int, value: String) {
        val qty = value.toIntOrNull() ?: 0
        viewModelScope.launch {
            inventoryRepo.updateQty(id, qty)
            refresh()
        }
    }

    fun updateDim(id: Int, field: String, value: String) {
        val v = value.toDoubleOrNull() ?: return
        viewModelScope.launch {
            inventoryRepo.updateDim(id, field, v)
            refresh()
        }
    }

    fun deleteSheet(id: Int) {
        viewModelScope.launch {
            inventoryRepo.delete(id)
            pricingRepo.updateThreshold(id, null)
            refresh()
        }
    }

    // ══ ویرایش قیمت (تفکیک محصول/کرایه) — معادل togglePriceBreakdown/updatePriceBreakdownField در وب ══

    fun openPriceEdit(layer: String, category: String) {
        val key = PricingRepository.sheetPriceKey(layer, category)
        val cur = _uiState.value
        if (cur.priceEditKey == key) {
            _uiState.update { it.copy(priceEditKey = null) }
            return
        }
        val b = cur.priceBreakdowns[key] ?: PricingRepository.PriceBreakdown()
        _uiState.update {
            it.copy(
                priceEditKey = key,
                priceEditProduct = if (b.product > 0) fmtNum(b.product) else "",
                priceEditFreight = if (b.freight > 0) fmtNum(b.freight) else ""
            )
        }
    }

    fun updatePriceEditProduct(v: String) = _uiState.update { it.copy(priceEditProduct = v) }
    fun updatePriceEditFreight(v: String) = _uiState.update { it.copy(priceEditFreight = v) }

    fun savePriceEdit(layer: String, category: String) {
        val st = _uiState.value
        val product = parseAmount(st.priceEditProduct) ?: 0.0
        val freight = parseAmount(st.priceEditFreight) ?: 0.0
        viewModelScope.launch {
            pricingRepo.updatePriceBreakdown(layer, category, product, freight)
            _uiState.update { it.copy(priceEditKey = null) }
            refresh()
        }
    }

    // ══ ماشین‌حساب کرایه حمل بار ماشین (حالت دستی) — معادل calcTruckFreight/applyTruckFreight* در وب ══

    fun openFreightDialog() = _uiState.update {
        it.copy(
            showFreightDialog = true,
            freightMode = "manual",
            freightItems = listOf(FreightItemInput(1), FreightItemInput(2)),
            freightTotalText = "", freightResult = null, freightError = null,
            freightAppliedPriceKeys = emptySet(), freightAppliedStockKeys = emptySet(),
            autoLayer = "3", autoOpenKey = null, autoDims = emptyMap(),
            autoTotalText = "", autoError = null, autoResult = null,
            autoAppliedPriceKeys = emptySet(), autoAppliedStockKeys = emptySet()
        )
    }
    fun closeFreightDialog() = _uiState.update { it.copy(showFreightDialog = false) }

    fun setFreightMode(mode: String) = _uiState.update { it.copy(freightMode = mode) }


    fun addFreightItem() {
        val id = ++freightNextLocalId
        _uiState.update { it.copy(freightItems = it.freightItems + FreightItemInput(id)) }
    }

    fun removeFreightItem(localId: Int) {
        _uiState.update { st ->
            if (st.freightItems.size <= 1) st
            else st.copy(freightItems = st.freightItems.filter { it.localId != localId })
        }
    }

    fun updateFreightItem(localId: Int, transform: (FreightItemInput) -> FreightItemInput) {
        _uiState.update { st ->
            st.copy(
                freightItems = st.freightItems.map { if (it.localId == localId) transform(it) else it },
                freightResult = null, freightAppliedPriceKeys = emptySet(), freightAppliedStockKeys = emptySet()
            )
        }
    }

    fun updateFreightTotal(v: String) = _uiState.update {
        it.copy(freightTotalText = v, freightResult = null, freightAppliedPriceKeys = emptySet(), freightAppliedStockKeys = emptySet())
    }

    private fun categoryToPaperFlute(cat: String): Pair<String, String> = when (cat) {
        "2T" -> "2T" to "C"
        "E" -> "KT" to "E"
        else -> "KT" to "C"
    }

    fun calcFreight() {
        val st = _uiState.value
        val total = parseAmount(st.freightTotalText)
        if (total == null || total <= 0) {
            _uiState.update { it.copy(freightError = "مبلغ کل کرایه این بار را وارد کنید.", freightResult = null) }
            return
        }

        data class Valid(val sh: Double, val sw: Double, val qty: Int, val layer: String, val category: String)
        val valid = st.freightItems.mapNotNull { item ->
            val sh = item.sh.toDoubleOrNull() ?: return@mapNotNull null
            val sw = item.sw.toDoubleOrNull() ?: return@mapNotNull null
            val qty = item.qty.toIntOrNull() ?: return@mapNotNull null
            if (sh <= 0 || sw <= 0 || qty <= 0) return@mapNotNull null
            Valid(sh, sw, qty, item.layer, item.category)
        }
        if (valid.isEmpty()) {
            _uiState.update { it.copy(freightError = "حداقل یک ردیف را با طول، عرض و تعداد معتبر پر کنید.", freightResult = null) }
            return
        }

        val totalArea = valid.sumOf { (it.sh * it.sw / 10000.0) * it.qty }
        if (totalArea <= 0) {
            _uiState.update { it.copy(freightError = "مساحت کل صفر است.", freightResult = null) }
            return
        }
        val freightPerM2 = total / totalArea

        viewModelScope.launch {
            val breakdowns = pricingRepo.getPriceBreakdowns()
            val byKey = linkedMapOf<String, MutableList<Valid>>()
            valid.forEach { v ->
                val key = PricingRepository.sheetPriceKey(v.layer, v.category)
                byKey.getOrPut(key) { mutableListOf() }.add(v)
            }
            val groups = byKey.map { (key, items) ->
                val shareCost = items.sumOf { (it.sh * it.sw / 10000.0) * it.qty * freightPerM2 }
                val b = breakdowns[key] ?: PricingRepository.PriceBreakdown()
                val dimsLabel = items.joinToString("، ") { "${fmtDim(it.sh)}×${fmtDim(it.sw)} (${it.qty} برگ)" }
                val rows = items.map {
                    val (paperType, flute) = categoryToPaperFlute(it.category)
                    FreightRow(sh = it.sh, sw = it.sw, qty = it.qty, layer = it.layer, flute = flute, paperType = paperType)
                }
                FreightGroupResult(
                    priceKey = key,
                    layer = items.first().layer,
                    category = items.first().category,
                    dimsLabel = dimsLabel,
                    shareCost = shareCost,
                    oldPrice = b.total,
                    newPrice = b.product + freightPerM2,
                    rows = rows
                )
            }
            _uiState.update {
                it.copy(
                    freightResult = FreightCalcResult(freightPerM2, totalArea, groups),
                    freightError = null,
                    freightAppliedPriceKeys = emptySet(),
                    freightAppliedStockKeys = emptySet()
                )
            }
        }
    }

    /** معادل applyTruckFreightCategory در وب — کرایه‌ی جدید جایگزین کرایه‌ی قبلی همین دسته می‌شود */
    fun applyFreightToPrice(priceKey: String) {
        val result = _uiState.value.freightResult ?: return
        if (_uiState.value.freightAppliedPriceKeys.contains(priceKey)) return
        viewModelScope.launch {
            val breakdowns = pricingRepo.getPriceBreakdowns()
            val b = breakdowns[priceKey] ?: PricingRepository.PriceBreakdown()
            val parts = priceKey.split("-", limit = 2)
            if (parts.size == 2) {
                pricingRepo.updatePriceBreakdown(parts[0], parts[1], b.product, result.freightPerM2)
            }
            _uiState.update { it.copy(freightAppliedPriceKeys = it.freightAppliedPriceKeys + priceKey) }
            refresh()
        }
    }

    /** معادل applyTruckFreightStock در وب — افزودن ورق‌های این دسته به موجودی با تعداد واردشده */
    fun applyFreightToStock(priceKey: String) {
        val result = _uiState.value.freightResult ?: return
        if (_uiState.value.freightAppliedStockKeys.contains(priceKey)) return
        val group = result.groups.find { it.priceKey == priceKey } ?: return
        viewModelScope.launch {
            group.rows.forEach { r ->
                val sheet = inventoryRepo.findOrCreateSheet(r.sh, r.sw, r.layer, r.flute, r.paperType)
                inventoryRepo.increaseQty(sheet.id, r.qty)
            }
            _uiState.update { it.copy(freightAppliedStockKeys = it.freightAppliedStockKeys + priceKey) }
            refresh()
        }
    }

    // ══ حالت اتوماتیک کرایه حمل (۴.۳) — معادل بخش «کرایه حمل — حالت اتوماتیک» در وب ══

    fun setAutoLayer(layer: String) = _uiState.update { it.copy(autoLayer = layer, autoOpenKey = null) }

    /** باز/بسته کردن پنل یک ابعاد با کلیک ساده — معادل toggleTruckFreightAutoDim در وب */
    fun toggleAutoDimOpen(sh: Double, sw: Double) {
        val key = autoDimKey(_uiState.value.autoLayer, sh, sw)
        _uiState.update { it.copy(autoOpenKey = if (it.autoOpenKey == key) null else key) }
    }

    /** نگه‌داشتن کوتاه روی یک ابعاد ← انتخاب/لغو انتخاب (قرمز/سبز) — معادل toggleTruckFreightAutoSelect در وب */
    fun toggleAutoDimSelect(sh: Double, sw: Double) {
        val key = autoDimKey(_uiState.value.autoLayer, sh, sw)
        _uiState.update { st ->
            val cur = st.autoDims[key] ?: AutoDimState()
            st.copy(autoDims = st.autoDims + (key to cur.copy(selected = !cur.selected)))
        }
    }

    fun updateAutoDimQty(sh: Double, sw: Double, qty: String) {
        val key = autoDimKey(_uiState.value.autoLayer, sh, sw)
        _uiState.update { st ->
            val cur = st.autoDims[key] ?: AutoDimState()
            st.copy(autoDims = st.autoDims + (key to cur.copy(qty = qty)))
        }
    }

    fun setAutoDimCategory(sh: Double, sw: Double, category: String) {
        val key = autoDimKey(_uiState.value.autoLayer, sh, sw)
        _uiState.update { st ->
            val cur = st.autoDims[key] ?: AutoDimState()
            val newCat = if (cur.category == category) null else category
            st.copy(autoDims = st.autoDims + (key to cur.copy(category = newCat)))
        }
    }

    fun updateAutoTotal(v: String) = _uiState.update {
        it.copy(autoTotalText = v, autoResult = null, autoAppliedPriceKeys = emptySet(), autoAppliedStockKeys = emptySet())
    }

    /** معادل calcTruckFreightAuto در وب — بر اساس ابعادهای «انتخاب‌شده» (selected=true) محاسبه می‌کند */
    fun calcAutoFreight() {
        val st = _uiState.value
        val total = parseAmount(st.autoTotalText)
        if (total == null || total <= 0) {
            _uiState.update { it.copy(autoError = "مبلغ کل کرایه این بار را وارد کنید.", autoResult = null) }
            return
        }

        data class Valid(val sh: Double, val sw: Double, val qty: Int, val layer: String, val category: String)
        val valid = mutableListOf<Valid>()
        st.autoDims.forEach { (key, d) ->
            if (!d.selected) return@forEach
            val parts = key.split("-")
            if (parts.size != 3) return@forEach
            val layer = parts[0]
            val sh = parts[1].toDoubleOrNull() ?: return@forEach
            val sw = parts[2].toDoubleOrNull() ?: return@forEach
            val qty = d.qty.toIntOrNull() ?: return@forEach
            val cat = d.category ?: return@forEach
            if (sh > 0 && sw > 0 && qty > 0) valid.add(Valid(sh, sw, qty, layer, cat))
        }
        if (valid.isEmpty()) {
            _uiState.update {
                it.copy(
                    autoError = "حداقل یک ابعاد را کمی نگه دارید تا انتخاب شود، سپس تعداد و دسته آن را کامل وارد کنید.",
                    autoResult = null
                )
            }
            return
        }

        val totalArea = valid.sumOf { (it.sh * it.sw / 10000.0) * it.qty }
        if (totalArea <= 0) {
            _uiState.update { it.copy(autoError = "مساحت کل صفر است.", autoResult = null) }
            return
        }
        val freightPerM2 = total / totalArea

        viewModelScope.launch {
            val breakdowns = pricingRepo.getPriceBreakdowns()
            val byKey = linkedMapOf<String, MutableList<Valid>>()
            valid.forEach { v ->
                val key = PricingRepository.sheetPriceKey(v.layer, v.category)
                byKey.getOrPut(key) { mutableListOf() }.add(v)
            }
            val groups = byKey.map { (key, items) ->
                val shareCost = items.sumOf { (it.sh * it.sw / 10000.0) * it.qty * freightPerM2 }
                val b = breakdowns[key] ?: PricingRepository.PriceBreakdown()
                val dimsLabel = items.joinToString("، ") { "${fmtDim(it.sh)}×${fmtDim(it.sw)} (${it.qty} برگ)" }
                val rows = items.map {
                    val (paperType, flute) = categoryToPaperFlute(it.category)
                    FreightRow(sh = it.sh, sw = it.sw, qty = it.qty, layer = it.layer, flute = flute, paperType = paperType)
                }
                FreightGroupResult(
                    priceKey = key,
                    layer = items.first().layer,
                    category = items.first().category,
                    dimsLabel = dimsLabel,
                    shareCost = shareCost,
                    oldPrice = b.total,
                    newPrice = b.product + freightPerM2,
                    rows = rows
                )
            }
            _uiState.update {
                it.copy(
                    autoResult = FreightCalcResult(freightPerM2, totalArea, groups),
                    autoError = null,
                    autoAppliedPriceKeys = emptySet(),
                    autoAppliedStockKeys = emptySet()
                )
            }
        }
    }

    /** معادل applyTruckFreightAutoCategory در وب */
    fun applyAutoFreightToPrice(priceKey: String) {
        val result = _uiState.value.autoResult ?: return
        if (_uiState.value.autoAppliedPriceKeys.contains(priceKey)) return
        viewModelScope.launch {
            val breakdowns = pricingRepo.getPriceBreakdowns()
            val b = breakdowns[priceKey] ?: PricingRepository.PriceBreakdown()
            val parts = priceKey.split("-", limit = 2)
            if (parts.size == 2) {
                pricingRepo.updatePriceBreakdown(parts[0], parts[1], b.product, result.freightPerM2)
            }
            _uiState.update { it.copy(autoAppliedPriceKeys = it.autoAppliedPriceKeys + priceKey) }
            refresh()
        }
    }

    /** معادل applyTruckFreightAutoStock در وب */
    fun applyAutoFreightToStock(priceKey: String) {
        val result = _uiState.value.autoResult ?: return
        if (_uiState.value.autoAppliedStockKeys.contains(priceKey)) return
        val group = result.groups.find { it.priceKey == priceKey } ?: return
        viewModelScope.launch {
            group.rows.forEach { r ->
                val sheet = inventoryRepo.findOrCreateSheet(r.sh, r.sw, r.layer, r.flute, r.paperType)
                inventoryRepo.increaseQty(sheet.id, r.qty)
            }
            _uiState.update { it.copy(autoAppliedStockKeys = it.autoAppliedStockKeys + priceKey) }
            refresh()
        }
    }

    companion object {
        fun layerLabel(layer: String) = if (layer == "5") "پنج‌لایه" else "سه‌لایه"

        fun fmtNum(n: Double): String =
            java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(Math.round(n))

        fun fmtDim(n: Double): String =
            if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

        private fun parseAmount(s: String): Double? {
            if (s.isBlank()) return null
            return s.replace(",", "").toDoubleOrNull()
        }
    }
}
