package com.rasticpack.app.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import com.rasticpack.app.data.repo.CustomerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** مقادیر فرم افزودن/ویرایش مشتری — هم برای «افزودن» و هم برای «ویرایش» استفاده می‌شود. */
data class CustomerFormState(
    val name: String = "",
    val company: String = "",
    val address: String = "",
    val phone: String = "",
    val locationPasteText: String = "",
    val lat: String = "",
    val lng: String = ""
) {
    val hasLocation: Boolean get() = lat.toDoubleOrNull() != null && lng.toDoubleOrNull() != null
}

data class CustomersUiState(
    val customers: List<CustomerEntity> = emptyList(),
    val searchQuery: String = "",
    val showAddPanel: Boolean = false,
    val addForm: CustomerFormState = CustomerFormState(),
    val addError: String? = null,
    val editingId: Int? = null,
    val editForm: CustomerFormState = CustomerFormState(),
    val editError: String? = null,
    // مودال فاکتورهای مشتری
    val invoicesModalCustomerId: Int? = null,
    val invoicesModalList: List<InvoiceWithItems> = emptyList()
)

class CustomersViewModel(private val db: AppDatabase) : ViewModel() {

    private val repo = CustomerRepository(db)

    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAll().collect { list ->
                _uiState.update { it.copy(customers = list) }
            }
        }
    }

    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }

    fun filteredCustomers(): List<CustomerEntity> {
        val q = _uiState.value.searchQuery.trim().lowercase()
        val list = _uiState.value.customers
        if (q.isBlank()) return list
        return list.filter { it.name.lowercase().contains(q) || it.company.lowercase().contains(q) }
    }

    // ══ نمایش فرم افزودن ══
    fun toggleAddPanel() {
        _uiState.update {
            if (it.showAddPanel) it.copy(showAddPanel = false)
            else it.copy(showAddPanel = true, addForm = CustomerFormState(), addError = null)
        }
    }
    fun closeAddPanel() = _uiState.update { it.copy(showAddPanel = false) }

    fun updateAddForm(transform: (CustomerFormState) -> CustomerFormState) =
        _uiState.update { it.copy(addForm = transform(it.addForm), addError = null) }

    fun applyLocationPasteToAdd(text: String) {
        updateAddForm { it.copy(locationPasteText = text) }
        val parsed = LocationParsing.parse(text) ?: return
        updateAddForm { it.copy(lat = parsed.lat.toString(), lng = parsed.lng.toString()) }
    }

    fun applyGpsToAdd(lat: Double, lng: Double) {
        updateAddForm { it.copy(lat = lat.toString(), lng = lng.toString()) }
    }

    fun clearLocationInAdd() {
        updateAddForm { it.copy(lat = "", lng = "", locationPasteText = "") }
    }

    fun submitAdd() {
        val f = _uiState.value.addForm
        viewModelScope.launch {
            val error = repo.add(
                name = f.name, company = f.company, address = f.address, phone = f.phone,
                lat = f.lat.toDoubleOrNull(), lng = f.lng.toDoubleOrNull(),
                locationLink = f.locationPasteText.trim().ifBlank { null }
            )
            if (error != null) {
                _uiState.update { it.copy(addError = error) }
            } else {
                _uiState.update { it.copy(showAddPanel = false, addForm = CustomerFormState(), addError = null) }
            }
        }
    }

    // ══ ویرایش مشتری ══
    fun startEdit(customer: CustomerEntity) {
        _uiState.update {
            it.copy(
                editingId = customer.id,
                editForm = CustomerFormState(
                    name = customer.name, company = customer.company, address = customer.address,
                    phone = customer.phone, locationPasteText = customer.locationLink ?: "",
                    lat = customer.lat?.toString() ?: "", lng = customer.lng?.toString() ?: ""
                ),
                editError = null
            )
        }
    }
    fun cancelEdit() = _uiState.update { it.copy(editingId = null, editError = null) }

    fun updateEditForm(transform: (CustomerFormState) -> CustomerFormState) =
        _uiState.update { it.copy(editForm = transform(it.editForm), editError = null) }

    fun applyLocationPasteToEdit(text: String) {
        updateEditForm { it.copy(locationPasteText = text) }
        val parsed = LocationParsing.parse(text) ?: return
        updateEditForm { it.copy(lat = parsed.lat.toString(), lng = parsed.lng.toString()) }
    }

    fun applyGpsToEdit(lat: Double, lng: Double) {
        updateEditForm { it.copy(lat = lat.toString(), lng = lng.toString()) }
    }

    fun clearLocationInEdit() {
        updateEditForm { it.copy(lat = "", lng = "", locationPasteText = "") }
    }

    fun submitEdit() {
        val id = _uiState.value.editingId ?: return
        val f = _uiState.value.editForm
        viewModelScope.launch {
            val error = repo.update(
                id = id, name = f.name, company = f.company, address = f.address, phone = f.phone,
                lat = f.lat.toDoubleOrNull(), lng = f.lng.toDoubleOrNull(),
                locationLink = f.locationPasteText.trim().ifBlank { null }
            )
            if (error != null) {
                _uiState.update { it.copy(editError = error) }
            } else {
                _uiState.update { it.copy(editingId = null, editError = null) }
            }
        }
    }

    fun deleteCustomer(id: Int) {
        viewModelScope.launch {
            repo.delete(id)
            _uiState.update { st ->
                if (st.editingId == id) st.copy(editingId = null) else st
            }
        }
    }

    // ══ مودال فاکتورهای مشتری ══
    fun openInvoicesModal(customerId: Int) {
        _uiState.update { it.copy(invoicesModalCustomerId = customerId, invoicesModalList = emptyList()) }
        viewModelScope.launch {
            repo.observeInvoicesForCustomer(customerId).collect { list ->
                if (_uiState.value.invoicesModalCustomerId == customerId) {
                    _uiState.update { it.copy(invoicesModalList = list.sortedByDescending { iw -> iw.invoice.dateIso }) }
                }
            }
        }
    }
    fun closeInvoicesModal() = _uiState.update { it.copy(invoicesModalCustomerId = null, invoicesModalList = emptyList()) }
}
