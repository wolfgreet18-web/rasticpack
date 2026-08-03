package com.rasticpack.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.VanDriverEntity
import com.rasticpack.app.data.repo.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** مقادیر فرم افزودن/ویرایش راننده — معادل فیلدهای new-driver-name/phone/plate در وب */
data class DriverFormState(
    val name: String = "",
    val phone: String = "",
    val plate: String = ""
)

data class DriversUiState(
    val drivers: List<VanDriverEntity> = emptyList(),
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
 */
class DriversViewModel(db: AppDatabase) : ViewModel() {

    private val repo = DriverRepository(db)

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
            val error = repo.add(name = f.name, phone = f.phone, plate = f.plate)
            if (error != null) {
                _uiState.update { it.copy(addError = error) }
            } else {
                _uiState.update { it.copy(showAddPanel = false, addForm = DriverFormState(), addError = null) }
            }
        }
    }

    // ══ ویرایش راننده ══
    fun startEdit(driver: VanDriverEntity) {
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
            val error = repo.update(id = id, name = f.name, phone = f.phone, plate = f.plate)
            if (error != null) {
                _uiState.update { it.copy(editError = error) }
            } else {
                _uiState.update { it.copy(editingId = null, editError = null) }
            }
        }
    }

    fun deleteDriver(id: Int) {
        viewModelScope.launch {
            repo.delete(id)
            _uiState.update { st -> if (st.editingId == id) st.copy(editingId = null) else st }
        }
    }
}
