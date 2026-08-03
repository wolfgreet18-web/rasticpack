package com.rasticpack.app.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import com.rasticpack.app.data.entities.VanDriverEntity
import com.rasticpack.app.data.repo.CustomerRepository
import com.rasticpack.app.data.repo.InvoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvoicesUiState(
    val allInvoices: List<InvoiceWithItems> = emptyList(),
    val selectedMonthSort: Int? = null,   // معادل selectedInvMonthSort
    val exactDateKey: String? = null,     // معادل selectedInvExactDate (yyyy-MM-dd)
    val statusFilter: String? = null,     // null | "draft" | "partial" | "paid" | "debtor"
    val searchQuery: String = "",
    val customerFilterId: Int? = null,
    val showSearch: Boolean = false,
    // پنل ویرایش
    val editingInvoiceId: Int? = null,
    val editQuantities: Map<Int, String> = emptyMap(), // itemId -> متن ورودی
    val editCustomerName: String = "",
    val editError: String? = null,
    // پنل پرداخت
    val payPanelInvoiceId: Int? = null,
    val payAmountText: String = "",
    // مرحله ۷ — پاپ‌آپ‌های پیامک/وانت روی هر کارت فاکتور (معادل sms-opts-/van-opts- در وب)
    val smsOptionsInvoiceId: Int? = null,
    val vanOptionsInvoiceId: Int? = null,
    val vanDriverListInvoiceId: Int? = null,
    val vanCustDriverListInvoiceId: Int? = null,
    val customersById: Map<Int, CustomerEntity> = emptyMap(),
    val vanDrivers: List<VanDriverEntity> = emptyList(),
    val invoiceSettings: InvoiceHeaderSettings = InvoiceHeaderSettings(),
    val smsTemplate: String = ""
)

/** معادل بخش invoiceSettings در وب — فقط فیلدهایی که برای پیامک/PDF فاکتور لازم است */
data class InvoiceHeaderSettings(
    val companyName: String = "رستیک پک",
    val phone: String = "",
    val address: String = "",
    val footer: String = "با تشکر از خرید شما",
    val cardNumber: String = "",
    val shaba: String = "",
    val accountHolderName: String = ""
)

class InvoicesViewModel(private val db: AppDatabase) : ViewModel() {

    private val repo = InvoiceRepository(db)
    private val customerRepo = CustomerRepository(db)

    private val _uiState = MutableStateFlow(InvoicesUiState())
    val uiState: StateFlow<InvoicesUiState> = _uiState.asStateFlow()

    init {
        val todaySort = JalaliDate.monthInfoFor(java.time.Instant.now().toString()).sortVal
        _uiState.update { it.copy(selectedMonthSort = todaySort) }
        viewModelScope.launch {
            repo.observeAllWithItems().collect { list ->
                _uiState.update { it.copy(allInvoices = list) }
            }
        }
        viewModelScope.launch {
            customerRepo.observeAll().collect { list ->
                _uiState.update { it.copy(customersById = list.associateBy { c -> c.id }) }
            }
        }
        viewModelScope.launch {
            db.vanDriverDao().observeAll().collect { list ->
                _uiState.update { it.copy(vanDrivers = list) }
            }
        }
        viewModelScope.launch {
            val settings = db.appSettingsDao().get()
            if (settings != null) {
                _uiState.update {
                    it.copy(
                        invoiceSettings = InvoiceHeaderSettings(
                            companyName = settings.invCompanyName,
                            phone = settings.invPhone,
                            address = settings.invAddress,
                            footer = settings.invFooter,
                            cardNumber = settings.invCardNumber,
                            shaba = settings.invShaba,
                            accountHolderName = settings.invAccountHolderName
                        ),
                        smsTemplate = settings.smsTemplate
                    )
                }
            }
        }
    }

    fun customerFor(iw: InvoiceWithItems): CustomerEntity? = _uiState.value.customersById[iw.invoice.customerId]

    // ══ سوییچر ماه/روز ══
    fun shiftMonth(dir: Int) {
        _uiState.update { st ->
            val cur = st.selectedMonthSort ?: JalaliDate.monthInfoFor(java.time.Instant.now().toString()).sortVal
            // dir=-1 یعنی «ماه بعد» (جلوتر)، dir=1 یعنی «ماه قبل» — مطابق دکمه‌های وب
            val next = JalaliDate.addMonthsToSortVal(cur, if (dir == -1) 1 else -1)
            st.copy(selectedMonthSort = next, exactDateKey = null)
        }
    }

    fun resetToToday() {
        val todaySort = JalaliDate.monthInfoFor(java.time.Instant.now().toString()).sortVal
        _uiState.update { it.copy(selectedMonthSort = todaySort, exactDateKey = null) }
    }

    fun selectExactDate(dateKey: String) {
        _uiState.update {
            val info = JalaliDate.monthInfoFor(dateKey + "T00:00:00Z")
            it.copy(exactDateKey = dateKey, selectedMonthSort = info.sortVal)
        }
    }

    fun clearExactDate() = _uiState.update { it.copy(exactDateKey = null) }

    fun monthLabel(): String {
        val st = _uiState.value
        if (st.exactDateKey != null) return st.exactDateKey
        val sort = st.selectedMonthSort ?: return "—"
        val jy = sort / 100
        val jm = sort % 100
        return JalaliDate.toFaDigits(jy) + "/" + JalaliDate.toFaDigits(jm)
    }

    // ══ فیلترها ══
    fun setStatusFilter(status: String) = _uiState.update {
        it.copy(statusFilter = if (it.statusFilter == status) null else status)
    }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun toggleSearch() = _uiState.update {
        if (it.showSearch) it.copy(showSearch = false, searchQuery = "") else it.copy(showSearch = true)
    }
    fun setCustomerFilter(id: Int?) = _uiState.update { it.copy(customerFilterId = id) }

    /** معادل renderInvoicesList — فیلتر بر اساس ماه/روز/مشتری/جستجو (بدون فیلتر وضعیت — برای شمارش) */
    fun baseFilteredList(): List<InvoiceWithItems> {
        val st = _uiState.value
        var list = st.allInvoices
        list = if (st.exactDateKey != null) {
            list.filter { JalaliDate.dateOnlyKey(it.invoice.dateIso) == st.exactDateKey }
        } else if (st.selectedMonthSort != null) {
            list.filter { JalaliDate.monthInfoFor(it.invoice.dateIso).sortVal == st.selectedMonthSort }
        } else list
        if (st.customerFilterId != null) list = list.filter { it.invoice.customerId == st.customerFilterId }
        val q = st.searchQuery.trim().lowercase()
        if (q.isNotBlank()) list = list.filter { it.invoice.customerName.lowercase().contains(q) }
        return list
    }

    /** فیلتر نهایی با اعمال وضعیت — معادل انتهای renderInvoicesList */
    fun visibleList(): List<InvoiceWithItems> {
        val base = baseFilteredList()
        val filter = _uiState.value.statusFilter
        val filtered = when (filter) {
            null -> base
            "debtor" -> base.filter { repo.isDebtor(it.invoice) }
            else -> base.filter { repo.statusOf(it.invoice) == filter && !repo.isDebtor(it.invoice) }
        }
        return filtered.sortedByDescending { it.invoice.dateIso }
    }

    /** تعداد هر وضعیت روی دکمه‌های فیلتر — معادل renderInvStatusCounts */
    fun statusCounts(): Map<String, Int> {
        val base = baseFilteredList()
        val counts = mutableMapOf("draft" to 0, "partial" to 0, "paid" to 0)
        base.forEach { iw ->
            val k = repo.statusOf(iw.invoice)
            if (k != "paid" && repo.isDebtor(iw.invoice)) return@forEach
            counts[k] = (counts[k] ?: 0) + 1
        }
        return counts
    }
    fun debtorCount(): Int = baseFilteredList().count { repo.isDebtor(it.invoice) }
    fun debtorTotalRemaining(): Double =
        baseFilteredList().filter { repo.isDebtor(it.invoice) }
            .sumOf { repo.invoiceRemaining(it.invoice, it.items) }
    fun debtorCustomerCount(): Int =
        baseFilteredList().filter { repo.isDebtor(it.invoice) }
            .map { it.invoice.customerId }.distinct().size

    fun invoiceTotal(iw: InvoiceWithItems) = repo.invoiceTotal(iw.items)
    fun invoiceRemaining(iw: InvoiceWithItems) = repo.invoiceRemaining(iw.invoice, iw.items)
    fun statusOf(iw: InvoiceWithItems) = repo.statusOf(iw.invoice)
    fun isDebtor(iw: InvoiceWithItems) = repo.isDebtor(iw.invoice)

    // ══ عملیات لمسی — ارسال شد / تسویه شد ══
    fun toggleSent(invoiceId: Int, currentlySent: Boolean) {
        viewModelScope.launch { repo.setSent(invoiceId, !currentlySent) }
    }
    fun toggleSettled(invoiceId: Int, currentlyPaid: Boolean) {
        viewModelScope.launch {
            if (currentlyPaid) repo.unmarkSettled(invoiceId) else repo.markSettled(invoiceId)
        }
    }

    // ══ پنل پرداخت ══
    fun openPayPanel(invoiceId: Int, prefillText: String) {
        _uiState.update { it.copy(payPanelInvoiceId = invoiceId, payAmountText = prefillText) }
    }
    fun closePayPanel() = _uiState.update { it.copy(payPanelInvoiceId = null, payAmountText = "") }
    fun updatePayAmountText(v: String) = _uiState.update { it.copy(payAmountText = v) }
    fun submitPayment() {
        val id = _uiState.value.payPanelInvoiceId ?: return
        val amount = _uiState.value.payAmountText.replace(",", "").toDoubleOrNull()
        viewModelScope.launch {
            repo.submitPayment(id, amount)
            _uiState.update { it.copy(payPanelInvoiceId = null, payAmountText = "") }
        }
    }

    // ══ ویرایش فاکتور ══
    fun startEdit(iw: InvoiceWithItems) {
        _uiState.update {
            it.copy(
                editingInvoiceId = iw.invoice.id,
                editCustomerName = iw.invoice.customerName,
                editQuantities = iw.items.associate { item -> item.id to item.qty.toString() },
                editError = null
            )
        }
    }
    fun cancelEdit() = _uiState.update { it.copy(editingInvoiceId = null, editError = null) }
    fun updateEditCustomerName(v: String) = _uiState.update { it.copy(editCustomerName = v, editError = null) }
    fun updateEditQty(itemId: Int, v: String) = _uiState.update {
        it.copy(editQuantities = it.editQuantities + (itemId to v))
    }

    fun saveEdit() {
        val st = _uiState.value
        val invoiceId = st.editingInvoiceId ?: return
        viewModelScope.launch {
            val customer = customerRepo.findByName(st.editCustomerName)
            if (customer == null) {
                _uiState.update { it.copy(editError = "این مشتری ثبت نشده.") }
                return@launch
            }
            val newQuantities = st.editQuantities.mapValues { (_, v) -> v.toIntOrNull() ?: 0 }
            val error = repo.editInvoiceItemQuantities(invoiceId, newQuantities)
            if (error != null) {
                _uiState.update { it.copy(editError = error) }
                return@launch
            }
            // به‌روزرسانی نام مشتری فاکتور در صورت تغییر
            val iw = st.allInvoices.find { it.invoice.id == invoiceId }
            if (iw != null && (iw.invoice.customerId != customer.id)) {
                db.invoiceDao().updateInvoice(
                    iw.invoice.copy(customerId = customer.id, customerName = customer.name)
                )
            }
            _uiState.update { it.copy(editingInvoiceId = null, editError = null) }
        }
    }

    fun deleteInvoice(invoiceId: Int) {
        viewModelScope.launch {
            repo.deleteInvoice(invoiceId)
            _uiState.update { if (it.editingInvoiceId == invoiceId) it.copy(editingInvoiceId = null) else it }
        }
    }

    // ══ بسته‌بندی (تسمه) ══
    /** معادل sendAllToProduction در وب — به صف تولید اضافه می‌کند و شناسه‌ی اولین
     * رکورد مرتبط را برمی‌گرداند (برای اعمال خودکار در فرم محاسبه‌ی تب تولید). */
    suspend fun sendToProduction(invoiceId: Int): Int? = repo.sendAllToProduction(invoiceId)

    fun setBundleSize(invoiceId: Int, itemId: Int, size: Int?) {
        viewModelScope.launch { repo.setBundleSize(invoiceId, itemId, size) }
    }

    // ══ مرحله ۷ — پاپ‌آپ‌های پیامک/وانت (معادل closeAllInvoicePopups/toggleSmsOptions/toggleVanOptions در وب) ══
    private fun closeAllPopups(st: InvoicesUiState) = st.copy(
        smsOptionsInvoiceId = null, vanOptionsInvoiceId = null,
        vanDriverListInvoiceId = null, vanCustDriverListInvoiceId = null
    )

    fun toggleSmsOptions(invoiceId: Int) = _uiState.update { st ->
        val opening = st.smsOptionsInvoiceId != invoiceId
        closeAllPopups(st).copy(smsOptionsInvoiceId = if (opening) invoiceId else null)
    }

    fun toggleVanOptions(invoiceId: Int) = _uiState.update { st ->
        val opening = st.vanOptionsInvoiceId != invoiceId
        closeAllPopups(st).copy(vanOptionsInvoiceId = if (opening) invoiceId else null)
    }

    fun toggleVanDriverList(invoiceId: Int) = _uiState.update { st ->
        val opening = st.vanDriverListInvoiceId != invoiceId
        st.copy(
            vanDriverListInvoiceId = if (opening) invoiceId else null,
            vanCustDriverListInvoiceId = null
        )
    }

    fun toggleVanCustDriverList(invoiceId: Int) = _uiState.update { st ->
        val opening = st.vanCustDriverListInvoiceId != invoiceId
        st.copy(
            vanCustDriverListInvoiceId = if (opening) invoiceId else null,
            vanDriverListInvoiceId = null
        )
    }

    // ══ مرحله ۷ — متن‌های پیامک آماده برای هر فاکتور (معادل buildInvoiceItemsSmsBody/... در وب) ══
    fun invoiceItemsSmsBody(iw: InvoiceWithItems): String =
        SmsHelper.buildInvoiceItemsSmsBody(iw.invoice, iw.items)

    fun cardShabaSmsBody(): String {
        val s = _uiState.value.invoiceSettings
        return SmsHelper.buildCardShabaSmsBody(s.cardNumber, s.shaba, s.accountHolderName)
    }

    fun storeAddressSmsBody(): String = SmsHelper.buildStoreAddressSmsBody(_uiState.value.invoiceSettings.address)

    fun vanSmsBody(iw: InvoiceWithItems): String =
        SmsHelper.buildVanSmsBody(iw.invoice, iw.items, customerFor(iw))

    fun vanInfoForCustomerSmsBody(iw: InvoiceWithItems, driver: VanDriverEntity): String =
        SmsHelper.buildVanInfoForCustomerSmsBody(iw.invoice, iw.items, driver)

    /** معادل sendInvoiceSms در وب — متن قالب پیامک با جایگزینی متغیرها */
    fun templatedSmsBody(iw: InvoiceWithItems): String {
        val st = _uiState.value
        val customer = customerFor(iw)
        return SmsHelper.fillSmsTemplate(
            st.smsTemplate, iw.invoice.customerName, customer?.company ?: "",
            iw.invoice.totalSheets, JalaliDate.formatDateTimeShort(iw.invoice.dateIso)
        )
    }
}
