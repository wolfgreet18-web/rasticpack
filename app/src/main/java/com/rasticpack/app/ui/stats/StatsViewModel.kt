package com.rasticpack.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.InvoiceWithItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val invoices: List<InvoiceWithItems> = emptyList(),
    // معادل باز/بسته بودن هر نمودار (arrow) در وب — پیش‌فرض بسته
    val turnoverChartOpen: Boolean = false,
    val profitChartOpen: Boolean = false,
    val countChartOpen: Boolean = false
)

class StatsViewModel(private val db: AppDatabase) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            db.invoiceDao().observeAllWithItems().collect { list ->
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
