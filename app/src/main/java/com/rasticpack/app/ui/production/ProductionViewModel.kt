package com.rasticpack.app.ui.production

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.domain.model.ProductionQueueItem
import com.rasticpack.app.domain.repository.ProductionQueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * معادل بخش «TAB — مراحل تولید» (زیرمرحله‌ی ۸.۱ — صف تولید + فرم ابعاد، بدون رسم SVG هنوز).
 * صف تولید: لیست productionQueue در وب (حداکثر ۳۰ مورد، معادل trimTo30 در DAO).
 * فرم ابعاد: معادل ورودی‌های sL/sW/bL/bW/bH/glue + دکمه‌ی محاسبه در وب.
 */
data class ProductionUiState(
    val queue: List<ProductionQueueItem> = emptyList(),
    // فرم ابعاد — معادل input های #sL #sW #bL #bW #bH #glue در وب
    val sL: String = "",
    val sW: String = "",
    val bL: String = "",
    val bW: String = "",
    val bH: String = "",
    val glue: String = "3.5",
    // معادل machine ('m' = برش, 'c' = چاک)
    val machine: String = "m",
    // نتیجه‌ی محاسبه (اگر محاسبه انجام شده)
    val result: BlankCalcResult? = null,
    val error: String? = null,
    // معادل مخفی‌شدن prod-dims-tab/prod-calc-btn وقتی یک آیتم از صف اعمال شده
    val dimsFormCollapsed: Boolean = false
)

/**
 * ══ مرحله ۳.۴ (نقشه معماری v2.9) — وصل‌شده به ProductionQueueRepository ══
 * قبلاً این ViewModel مستقیماً `ProductionQueueDao` را می‌گرفت؛ حالا از اینترفیس
 * دامنه (`domain.repository.ProductionQueueRepository`) استفاده می‌کند تا با الگوی
 * لایه‌بندی بقیه‌ی مراحل هماهنگ بماند. منطق داخل کلاس عیناً دست‌نخورده مانده —
 * فقط نوع داده‌ی صف از Entity به مدل خالص domain عوض شده.
 */
@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val productionQueueRepository: ProductionQueueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductionUiState())
    val uiState: StateFlow<ProductionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productionQueueRepository.observeAll().collect { list ->
                _uiState.update { it.copy(queue = list) }
            }
        }
    }

    fun updateSL(v: String) = _uiState.update { it.copy(sL = v) }
    fun updateSW(v: String) = _uiState.update { it.copy(sW = v) }
    fun updateBL(v: String) = _uiState.update { it.copy(bL = v) }
    fun updateBW(v: String) = _uiState.update { it.copy(bW = v) }
    fun updateBH(v: String) = _uiState.update { it.copy(bH = v) }
    fun updateGlue(v: String) = _uiState.update { it.copy(glue = v) }
    fun setMachine(m: String) {
        _uiState.update { it.copy(machine = m) }
        // معادل: اگر نتیجه از قبل نشان داده شده، با تعویض دستگاه دوباره محاسبه شود
        if (_uiState.value.result != null) calculate()
    }

    /** معادل تابع calculate() در وب (بخش عددی — بدون رسم SVG که به ۸.۲ موکول شده) */
    fun calculate() {
        val st = _uiState.value
        val sL = st.sL.toDoubleOrNull()
        val sW = st.sW.toDoubleOrNull()
        val bL = st.bL.toDoubleOrNull()
        val bW = st.bW.toDoubleOrNull()
        val bH = st.bH.toDoubleOrNull()
        if (sL == null || sW == null || bL == null || bW == null || bH == null ||
            sL <= 0 || sW <= 0 || bL <= 0 || bW <= 0 || bH <= 0
        ) {
            _uiState.update { it.copy(error = "لطفاً تمام ابعاد را وارد کنید.", result = null) }
            return
        }
        val glue = st.glue.toDoubleOrNull()
        val result = ProductionCalc.compute(sL, sW, bL, bW, bH, glue)
        _uiState.update { it.copy(result = result, error = null) }
    }

    /** معادل applyProductionItem در وب — مقادیر فرم را از یک رکورد صف پر می‌کند و محاسبه می‌کند */
    fun applyItem(item: ProductionQueueItem) {
        _uiState.update {
            it.copy(
                sL = fmtNum(item.sh), sW = fmtNum(item.sw),
                bL = fmtNum(item.length), bW = fmtNum(item.width), bH = fmtNum(item.height),
                glue = fmtNum(item.glue),
                dimsFormCollapsed = true
            )
        }
        calculate()
    }

    fun applyItemById(id: Int) {
        val item = _uiState.value.queue.find { it.id == id } ?: return
        applyItem(item)
    }

    fun expandDimsForm() = _uiState.update { it.copy(dimsFormCollapsed = false) }

    fun removeItem(id: Int) {
        val wasLastItem = _uiState.value.queue.size <= 1
        viewModelScope.launch {
            productionQueueRepository.deleteById(id)
        }
        // معادل بخش وب: اگر صف خالی شد، فرم ابعاد دوباره قابل‌مشاهده شود
        if (wasLastItem) {
            _uiState.update { it.copy(dimsFormCollapsed = false) }
        }
    }

    private fun fmtNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
