package com.rasticpack.app.ui.calc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.repo.CustomerRepository
import com.rasticpack.app.data.repo.InventoryRepository
import com.rasticpack.app.data.repo.InvoiceRepository
import com.rasticpack.app.data.repo.PricingRepository
import com.rasticpack.app.data.repo.PricingRepository.Companion.priceCategoryOf
import com.rasticpack.app.data.repo.PricingRepository.Companion.sheetPriceKey
import com.rasticpack.app.engine.CalculatorEngine
import com.rasticpack.app.engine.Grain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.round

/** وضعیت کامل صفحه‌ی «محاسبه کارتن» — یک UiState به‌جای چند remember جدا، برای سادگی مدیریت. */
data class Calc2UiState(
    val rows: List<CartonRowInput> = defaultCartonRows(),
    val profitPercentText: String = "20",
    val results: List<CartonCalcResult> = emptyList(),
    val totals: Calc2Totals? = null,
    val showResults: Boolean = false,
    val globalAlert: String? = null,
    val isCalculating: Boolean = false,
    // ══ ۳.۳ — پیشنهاد شیت/کارتن ══
    // کلید = localId ردیف. حضور کلید یعنی پنل پیشنهاد آن ردیف باز است؛
    // مقدار null یعنی «در حال محاسبه»، مقدار غیرنال یعنی نتیجه آماده است.
    val suggestOpenFor: Int? = null,
    val suggestResults: Map<Int, Calc2SuggestBundle> = emptyMap(),
    // ══ ۳.۵ — ثبت فاکتور ══ معادل c2inv-customer + c2inv-alert-all در وب
    val invoiceCustomerName: String = "",
    val invoiceCustomerError: String? = null,
    val invoiceSubmitAlert: String? = null,
    val isSubmittingInvoice: Boolean = false,
    val invoiceSubmitted: Boolean = false,   // معادل calc2InvoiceSubmitted — بعد از ثبت دکمه غیرفعال می‌شود
    val submittedInvoiceId: Int? = null
)

class Calc2ViewModel(private val db: AppDatabase) : ViewModel() {

    private val inventoryRepo = InventoryRepository(db)
    private val pricingRepo = PricingRepository(db)
    private val customerRepo = CustomerRepository(db)
    private val invoiceRepo = InvoiceRepository(db)

    private val _uiState = MutableStateFlow(Calc2UiState())
    val uiState: StateFlow<Calc2UiState> = _uiState.asStateFlow()

    private var nextLocalId = 3

    // ══ مدیریت ردیف‌ها — معادل addCartonRow2/removeCartonRow2/setCartonLayer... در وب ══

    fun addRow() {
        val newRow = CartonRowInput(localId = nextLocalId++)
        _uiState.update { it.copy(rows = it.rows + newRow) }
    }

    fun removeRow(localId: Int) {
        _uiState.update { st ->
            if (st.rows.size <= 1) st else st.copy(rows = st.rows.filter { it.localId != localId })
        }
    }

    fun updateRow(localId: Int, transform: (CartonRowInput) -> CartonRowInput) {
        _uiState.update { st ->
            st.copy(rows = st.rows.map { if (it.localId == localId) transform(it).copy(error = null) else it })
        }
    }

    fun updateProfitPercent(value: String) {
        _uiState.update { it.copy(profitPercentText = value) }
    }

    // ══ محاسبه — معادل دقیق runCalc2 در 4.html ══

    fun runCalculation() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCalculating = true, globalAlert = null, showResults = false,
                    suggestOpenFor = null, suggestResults = emptyMap(),
                    invoiceSubmitted = false, submittedInvoiceId = null,
                    invoiceSubmitAlert = null, invoiceCustomerError = null
                )
            }

            val grain = Grain.HORIZONTAL
            val profitPct = _uiState.value.profitPercentText.toDoubleOrNull() ?: 0.0

            // ۱) اعتبارسنجی هر ردیف — ابعاد و تعداد باید مثبت باشند
            var hasError = false
            val validatedRows = _uiState.value.rows.mapIndexed { idx, row ->
                val l = row.length.toDoubleOrNull()
                val w = row.width.toDoubleOrNull()
                val h = row.height.toDoubleOrNull()
                val q = row.qty.toIntOrNull()
                if (l == null || l <= 0 || w == null || w <= 0 || h == null || h <= 0 || q == null || q <= 0) {
                    hasError = true
                    row.copy(error = "ابعاد و تعداد این کارتن را کامل وارد کنید.")
                } else {
                    row.copy(error = null)
                }
            }
            _uiState.update { it.copy(rows = validatedRows) }
            if (hasError) {
                _uiState.update { it.copy(isCalculating = false) }
                return@launch
            }

            val inventory = inventoryRepo.getAllAsSheetItems()
            val sheetPrices = pricingRepo.getSheetPrices()
            val sheetWeights = pricingRepo.getSheetWeights()
            val wastePrice = pricingRepo.getWastePrice()

            // ۲) بررسی این‌که برای هر ردیف قیمت ورق مربوطه ثبت شده باشد
            var priceMissing = false
            val rowsAfterPriceCheck = validatedRows.map { row ->
                val category = priceCategoryOf(row.paperType, row.flute)
                val priceKey = sheetPriceKey(row.layer, category)
                val price = sheetPrices[priceKey] ?: 0.0
                if (price <= 0.0) {
                    priceMissing = true
                    row.copy(error = "قیمت ورق ${layerLabel(row.layer)} · دسته $category ثبت نشده — از تب «ورق» وارد کنید.")
                } else row
            }
            _uiState.update { it.copy(rows = rowsAfterPriceCheck) }
            if (priceMissing) {
                _uiState.update { it.copy(isCalculating = false) }
                return@launch
            }

            val alert = if (inventory.isEmpty())
                "هیچ ورقی در موجودی ثبت نشده — برای محاسبه ضایعات ابتدا ورق اضافه کنید."
            else null

            var totalAllCartons = 0.0
            var totalProfitAll = 0.0

            val results = rowsAfterPriceCheck.map { row ->
                val length = row.length.toDouble()
                val width = row.width.toDouble()
                val height = row.height.toDouble()
                val qty = row.qty.toInt()
                val glueRaw = row.glue.toDoubleOrNull() ?: 0.0
                val glue = if (glueRaw > 0) glueRaw else 4.0

                val category = priceCategoryOf(row.paperType, row.flute)
                val pricePerM2 = sheetPrices[sheetPriceKey(row.layer, category)] ?: 0.0

                val (totalWidth, totalLength) = CalculatorEngine.expandedCartonDims(length, width, height, glue)
                val areaM2 = (totalLength * totalWidth) / 10000.0
                val basePrice = areaM2 * pricePerM2

                val sheets = if (inventory.isNotEmpty())
                    CalculatorEngine.matchSheets(inventory, totalWidth, totalLength, qty, grain, row.layer, row.flute, row.paperType)
                        .take(1)
                else emptyList()
                val best = sheets.firstOrNull()

                val wasteCostPerUnit = if (best != null && best.perSheet > 0)
                    ((best.wasteArea / 10000.0) * pricePerM2) / best.perSheet
                else 0.0
                val wasteCostTotal = wasteCostPerUnit * qty
                val basePriceWithWaste = basePrice + wasteCostPerUnit
                val finalPrice = basePriceWithWaste * (1 + profitPct / 100.0)
                val profitPerUnit = finalPrice - basePriceWithWaste
                val totalProfit = profitPerUnit * qty
                val totalPrice = finalPrice * qty

                totalAllCartons += totalPrice
                totalProfitAll += totalProfit

                CartonCalcResult(
                    row = row,
                    name = "کارتن",
                    length = length, width = width, height = height, qty = qty, glue = glue,
                    totalLength = totalLength, totalWidth = totalWidth,
                    areaM2 = areaM2, basePrice = basePrice,
                    wasteCostPerUnit = wasteCostPerUnit, wasteCostTotal = wasteCostTotal,
                    basePriceWithWaste = basePriceWithWaste, finalPrice = finalPrice,
                    profitPerUnit = profitPerUnit, totalProfit = totalProfit, totalPrice = totalPrice,
                    sheets = sheets
                )
            }

            // ۳) محاسبه‌ی جداگانه‌ی قیمت ضایعات (اسقاط) — معادل بخش دوم runCalc2 در وب
            var totalWasteCostAll = 0.0
            results.forEach { r ->
                val best = r.sheets.firstOrNull()
                var costForThis = 0.0
                if (best != null) {
                    val sheetWeightG = sheetWeights[r.row.layer] ?: 0.0
                    val areaTotal = best.totalWasteArea / 10000.0
                    val scrapKgTotal = areaTotal * (sheetWeightG / 1000.0)
                    costForThis = scrapKgTotal * wastePrice
                    totalWasteCostAll += costForThis
                }
                r.scrapWastePricePerCarton = if (r.qty > 0) costForThis / r.qty else 0.0
            }

            _uiState.update {
                it.copy(
                    results = results,
                    totals = Calc2Totals(totalAllCartons, totalProfitAll, totalWasteCostAll),
                    showResults = true,
                    globalAlert = alert,
                    isCalculating = false
                )
            }
        }
    }

    // ══ ۳.۳ — پیشنهاد ابعاد شیت پرت‌صفر + پیشنهاد ابعاد کارتن نزدیک از روی موجودی ══
    // معادل دقیق calc2Accordion(rowId,'suggest') + renderCalc2SuggestSheet در وب.

    /** باز/بسته کردن آکاردئون «📐 پیشنهاد شیت» برای یک ردیف نتیجه — فقط یکی در هر لحظه باز می‌ماند. */
    fun toggleSuggest(rowLocalId: Int) {
        val current = _uiState.value
        if (current.suggestOpenFor == rowLocalId) {
            _uiState.update { it.copy(suggestOpenFor = null) }
            return
        }
        _uiState.update { it.copy(suggestOpenFor = rowLocalId) }
        // اگر قبلاً برای همین ردیف محاسبه نشده (یا نتیجه‌ی قدیمی مربوط به محاسبه‌ی قبلی است)، دوباره بساز
        if (!current.suggestResults.containsKey(rowLocalId)) {
            computeSuggestFor(rowLocalId)
        }
    }

    private fun computeSuggestFor(rowLocalId: Int) {
        val result = _uiState.value.results.find { it.row.localId == rowLocalId } ?: return
        viewModelScope.launch {
            val inventory = inventoryRepo.getAllAsSheetItems()
            val bw = result.totalWidth
            val bh = result.totalLength
            val layer = result.row.layer
            val flute = result.row.flute
            val paperType = result.row.paperType

            val zeroWasteOptions = CalculatorEngine.computeZeroWasteSheetSuggestions(bw, bh)
            val existingClose = CalculatorEngine.findClosestExistingSheets(inventory, bw, bh, layer)
            val cartonSuggestions = CalculatorEngine.suggestCartonDimsFromInventory(
                inventory, result.length, result.width, result.height, result.glue, layer, flute, paperType
            )
            val category = priceCategoryOf(paperType, flute)
            val pricePerM2 = pricingRepo.getSheetPrices()[sheetPriceKey(layer, category)] ?: 0.0
            val singleBest = result.sheets.firstOrNull()
            val twoPiece = CalculatorEngine.computeTwoPieceSuggestion(
                inventory = inventory,
                length = result.length, width = result.width, height = result.height,
                glue = result.glue, qty = result.qty,
                layer = layer, flute = flute, paperType = paperType,
                pricePerM2 = pricePerM2,
                singleBest = singleBest,
                basePriceWithWaste = result.basePriceWithWaste
            )

            val bundle = Calc2SuggestBundle(
                zeroWasteOptions = zeroWasteOptions,
                existingClose = existingClose,
                cartonSuggestions = cartonSuggestions,
                twoPiece = twoPiece
            )
            _uiState.update { it.copy(suggestResults = it.suggestResults + (rowLocalId to bundle)) }
        }
    }

    /** اعمال ابعاد پیشنهادی روی فرم همان کارتن + محاسبه‌ی مجدد خودکار — معادل applyCalc2SuggestedDims در وب. */
    fun applySuggestedDims(rowLocalId: Int, length: Double, width: Double, height: Double) {
        updateRow(rowLocalId) {
            it.copy(
                length = formatDimForInput(length),
                width = formatDimForInput(width),
                height = formatDimForInput(height)
            )
        }
        // نتیجه‌ی پیشنهاد قدیمی این ردیف دیگر معتبر نیست — با محاسبه‌ی بعدی از نو ساخته می‌شود
        _uiState.update { it.copy(suggestResults = it.suggestResults - rowLocalId, suggestOpenFor = null) }
        runCalculation()
    }

    private fun formatDimForInput(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    // ══ ۳.۵ — ثبت فاکتور ══ معادل دقیق submitCalc2Invoice در 4.html

    fun updateInvoiceCustomerName(value: String) {
        _uiState.update { it.copy(invoiceCustomerName = value, invoiceCustomerError = null) }
    }

    fun submitInvoice() {
        val st = _uiState.value
        if (st.invoiceSubmitted || st.isSubmittingInvoice) return

        _uiState.update { it.copy(invoiceSubmitAlert = null, invoiceCustomerError = null) }

        val nameInput = st.invoiceCustomerName.trim()
        if (nameInput.isEmpty()) {
            _uiState.update { it.copy(invoiceCustomerError = "لطفاً نام مشتری را وارد کنید.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingInvoice = true) }
            val customer = customerRepo.findByName(nameInput)
            if (customer == null) {
                _uiState.update {
                    it.copy(
                        isSubmittingInvoice = false,
                        invoiceCustomerError = "این مشتری ثبت نشده — از «مشتری‌ها» اضافه کنید."
                    )
                }
                return@launch
            }

            when (val result = invoiceRepo.submitInvoiceFromCalc2(customer.id, customer.name, st.results)) {
                is InvoiceRepository.SubmitResult.Error -> {
                    _uiState.update {
                        it.copy(isSubmittingInvoice = false, invoiceSubmitAlert = result.message)
                    }
                }
                is InvoiceRepository.SubmitResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingInvoice = false,
                            invoiceSubmitted = true,
                            submittedInvoiceId = result.invoiceId,
                            invoiceSubmitAlert = null
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun layerLabel(layer: String) = if (layer == "5") "پنج‌لایه" else "سه‌لایه"
    }
}
