package com.rasticpack.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.domain.model.Driver
import com.rasticpack.app.domain.repository.DriverRepository
import com.rasticpack.app.domain.usecase.settings.AddDriverUseCase
import com.rasticpack.app.domain.usecase.settings.DeleteDriverUseCase
import com.rasticpack.app.domain.usecase.settings.UpdateDriverUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** مقادیر فرم افزودن/ویرایش راننده — معادل فیلدهای new-driver-name/phone/plate در وب */
data class DriverFormState(
    val name: String = "",
    val phone: String = "",
    val plate: String = ""
)

data class DriversUiState(
    val drivers: List<Driver> = emptyList(),
    val showAddPanel: Boolean = false,
    val addForm: DriverFormState = DriverFormState(),
    val addError: String? = null,
    val editingId: Int? = null,
    val editForm: DriverFormState = DriverFormState(),
    val editError: String? = null
)

/**
 * معادل بخش «🚛 راننده وانت‌ها» (drivers-panel) در 4.html — همان الگوی
 * CustomersViewModel (مرحله ۵) اما ساده‌تر، چون راننده موقعیت مکانی یا مودال فاکتور ندارد.
 *
 * ══ مرحله ۲ (نقشه معماری v2.5) — اولین ViewModel وصل‌شده به لایه‌ی domain ══
 * قبلاً این کلاس مستقیماً `data.repo.DriverRepository` (که هم دسترسی داده و هم
 * اعتبارسنجی را با هم داشت) تزریق می‌گرفت. حالا فقط سه UseCase (`AddDriverUseCase`,
 * `UpdateDriverUseCase`, `DeleteDriverUseCase`) و اینترفیس خواندنی
 * `domain.repository.DriverRepository` (برای `observeAll()`) تزریق می‌گیرد؛ منطق
 * اعتبارسنجی/پیام خطا کاملاً به لایه‌ی domain منتقل شده — این ViewModel دیگر
 * هیچ قانون کسب‌وکاری (نام تکراری/خالی) را خودش نمی‌داند، فقط خروجی
 * `RasticResult` را به پیام قابل‌نمایش (`RasticError.toUserMessage()`) تبدیل می‌کند.
 * رفتار قابل‌مشاهده در UI (پیام‌های خطا، جریان کار) عیناً همان قبلی است.
 */
@HiltViewModel
class DriversViewModel @Inject constructor(
    private val repo: DriverRepository,
    private val addDriverUseCase: AddDriverUseCase,
    private val updateDriverUseCase: UpdateDriverUseCase,
    private val deleteDriverUseCase: DeleteDriverUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriversUiState())
    val uiState: StateFlow<DriversUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAll().collect { list ->
                _uiState.update { it.copy(drivers = list) }
            }
        }
    }

    // ══ نمایش فرم افزودن ══
    fun toggleAddPanel() {
        _uiState.update {
            if (it.showAddPanel) it.copy(showAddPanel = false)
            else it.copy(showAddPanel = true, addForm = DriverFormState(), addError = null)
        }
    }

    fun updateAddForm(transform: (DriverFormState) -> DriverFormState) =
        _uiState.update { it.copy(addForm = transform(it.addForm), addError = null) }

    fun submitAdd() {
        val f = _uiState.value.addForm
        viewModelScope.launch {
            val result = addDriverUseCase(name = f.name, phone = f.phone, plate = f.plate)
            result.onFailure { error ->
                _uiState.update { it.copy(addError = error.toUserMessage()) }
            }.onSuccess {
                _uiState.update { it.copy(showAddPanel = false, addForm = DriverFormState(), addError = null) }
            }
        }
    }

    // ══ ویرایش راننده ══
    fun startEdit(driver: Driver) {
        _uiState.update {
            it.copy(
                editingId = driver.id,
                editForm = DriverFormState(name = driver.name, phone = driver.phone, plate = driver.plate),
                editError = null
            )
        }
    }

    fun cancelEdit() = _uiState.update { it.copy(editingId = null, editError = null) }

    fun updateEditForm(transform: (DriverFormState) -> DriverFormState) =
        _uiState.update { it.copy(editForm = transform(it.editForm), editError = null) }

    fun submitEdit() {
        val id = _uiState.value.editingId ?: return
        val f = _uiState.value.editForm
        viewModelScope.launch {
            val result = updateDriverUseCase(id = id, name = f.name, phone = f.phone, plate = f.plate)
            result.onFailure { error ->
                _uiState.update { it.copy(editError = error.toUserMessage()) }
            }.onSuccess {
                _uiState.update { it.copy(editingId = null, editError = null) }
            }
        }
    }

    fun deleteDriver(id: Int) {
        viewModelScope.launch {
            deleteDriverUseCase(id)
            _uiState.update { st -> if (st.editingId == id) st.copy(editingId = null) else st }
        }
    }
}
