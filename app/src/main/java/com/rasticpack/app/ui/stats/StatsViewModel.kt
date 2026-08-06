package com.rasticpack.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.dao.InvoiceDao
import com.rasticpack.app.data.entities.InvoiceWithItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val invoices: List<InvoiceWithItems> = emptyList(),
    // معادل باز/بسته بودن هر نمودار (arrow) در وب — پیش‌فرض بسته
    val turnoverChartOpen: Boolean = false,
    val profitChartOpen: Boolean = false,
    val countChartOpen: Boolean = false
)

/**
 * ══ مرحله ۰.۳ — وصل‌شده به Hilt ══
 * این ViewModel به‌جای Repository مستقیماً از `InvoiceDao` استفاده می‌کرد؛ چون این DAO در
 * `DatabaseModule` تأمین شده، مستقیماً تزریق می‌شود. منطق داخل کلاس دست‌نخورده مانده.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(private val invoiceDao: InvoiceDao) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            invoiceDao.observeAllWithItems().collect { list ->
                _uiState.update { it.copy(invoices = list) }
            }
        }
    }

    fun setPeriod(p: StatsPeriod) = _uiState.update { it.copy(period = p) }

    fun toggleTurnoverChart() = _uiState.update { it.copy(turnoverChartOpen = !it.turnoverChartOpen) }
    fun toggleProfitChart() = _uiState.update { it.copy(profitChartOpen = !it.profitChartOpen) }
    fun toggleCountChart() = _uiState.update { it.copy(countChartOpen = !it.countChartOpen) }

    fun data(): List<StatsPoint> {
        val st = _uiState.value
        return StatsEngine.computeData(st.period, st.invoices)
    }

    fun leaders(): StatsEngine.Leaders {
        val st = _uiState.value
        return StatsEngine.computeLeaders(st.period, st.invoices)
    }

    fun hasAnyInvoices(): Boolean = _uiState.value.invoices.isNotEmpty()
}
