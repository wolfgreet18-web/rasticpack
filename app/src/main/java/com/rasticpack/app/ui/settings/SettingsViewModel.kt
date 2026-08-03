package com.rasticpack.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.data.repo.BackupRepository
import com.rasticpack.app.data.repo.InventoryRepository
import com.rasticpack.app.data.repo.PricingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * زیرمرحله ۱۰.۱ — فقط دو بخش اول تب «تنظیمات» در وب: «⚖️ وزن ورق» و «🗑️ قیمت ضایعات»
 * (معادل sw-panel و wp-panel در 4.html). بقیه‌ی بخش‌های تنظیمات (متن پیامک، سربرگ فاکتور،
 * راننده‌ها، تنظیمات نمایشی، وضعیت ورق با نمودار، بکاپ) در زیرمراحل بعدی اضافه می‌شوند.
 *
 * منطق دقیقاً معادل وب:
 *   - sheetWeightLayer: کدام لایه (۳/۵) در حال حاضر روی تاگل انتخاب شده — فقط UI-state،
 *     در دیتابیس ذخیره نمی‌شود (در وب هم فقط یک متغیر سراسری موقت بود).
 *   - وزن هر لایه جدا ذخیره می‌شود: sheetWeights['3'] / sheetWeights['5'].
 *   - قیمت ضایعات مشترک بین هر دو لایه است (یک عدد واحد).
 */
data class SettingsUiState(
    val sheetWeightLayer: String = "3",
    val sheetWeight3: Double = 0.0,
    val sheetWeight5: Double = 0.0,
    val wastePrice: Double = 0.0,
    // ══ زیرمرحله ۱۰.۲ ══
    val smsTemplate: String = "",
    val invCompanyName: String = "رستیک پک",
    val invPhone: String = "",
    val invAddress: String = "",
    val invFooter: String = "با تشکر از خرید شما",
    val invCardNumber: String = "",
    val invShaba: String = "",
    val invAccountHolderName: String = "",
    // ══ زیرمرحله ۱۰.۴ — تنظیمات نمایشی (معادل displaySettings در وب) ══
    val dispFontScale: Int = 100,
    val dispZoom: Int = 85,
    val dispSoundEnabled: Boolean = true,
    // ══ زیرمرحله ۱۰.۴ — وضعیت موجودی ورق + آستانه‌ی هشدار (معادل status-panel در وب) ══
    val sheets: List<InventorySheetEntity> = emptyList(),
    val thresholds: Map<Int, Int> = emptyMap(),
    // کدام یک از ۸ بخش تاشو در حال حاضر باز است — فقط یکی در هر لحظه (معادل toggleSection در وب)
    val openSection: String? = null,
    val loaded: Boolean = false,
    // ══ زیرمرحله ۱۰.۵ — بکاپ/بازیابی/پاک‌کردن (معادل backup-panel در وب) ══
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val backupMessageIsError: Boolean = false
) {
    val currentSheetWeight: Double get() = if (sheetWeightLayer == "5") sheetWeight5 else sheetWeight3
}

/** بخش‌های تاشوی تب تنظیمات — معادل SETTINGS_PANEL_IDS در وب */
enum class SettingsSection {
    SHEET_WEIGHT, WASTE_PRICE, SHEET_STATUS, SMS_TEMPLATE, INVOICE_SETTINGS, DRIVERS, DISPLAY, BACKUP
}

class SettingsViewModel(db: AppDatabase) : ViewModel() {

    private val pricingRepo = PricingRepository(db)
    private val inventoryRepo = InventoryRepository(db)
    private val backupRepo = BackupRepository(db)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reload()
        }
    }

    private suspend fun reload() {
        val weights = pricingRepo.getSheetWeights()
        val waste = pricingRepo.getWastePrice()
        val sms = pricingRepo.getSmsTemplate()
        val inv = pricingRepo.getInvoiceSettings()
        val disp = pricingRepo.getDisplaySettings()
        val sheets = inventoryRepo.getAll()
        val thresholds = pricingRepo.getThresholds()
        _uiState.update {
            it.copy(
                sheetWeight3 = weights["3"] ?: 0.0,
                sheetWeight5 = weights["5"] ?: 0.0,
                wastePrice = waste,
                smsTemplate = sms,
                invCompanyName = inv.companyName,
                invPhone = inv.phone,
                invAddress = inv.address,
                invFooter = inv.footer,
                invCardNumber = inv.cardNumber,
                invShaba = inv.shaba,
                invAccountHolderName = inv.accountHolderName,
                dispFontScale = disp.fontScale,
                dispZoom = disp.zoom,
                dispSoundEnabled = disp.soundEnabled,
                sheets = sheets,
                thresholds = thresholds,
                loaded = true
            )
        }
    }

    /** معادل toggleSection در وب — فقط یکی از ۸ بخش در هر لحظه باز است */
    fun toggleSection(section: SettingsSection) {
        _uiState.update { it.copy(openSection = if (it.openSection == section.name) null else section.name) }
        if (section == SettingsSection.SHEET_STATUS) {
            viewModelScope.launch { refreshSheetsAndThresholds() }
        }
    }

    private suspend fun refreshSheetsAndThresholds() {
        val sheets = inventoryRepo.getAll()
        val thresholds = pricingRepo.getThresholds()
        _uiState.update { it.copy(sheets = sheets, thresholds = thresholds) }
    }

    /** معادل setSheetWeightLayer در وب — فقط تعویض تاگل، بدون تغییر مقدار ذخیره‌شده */
    fun setSheetWeightLayer(layer: String) {
        _uiState.update { it.copy(sheetWeightLayer = layer) }
    }

    /** معادل updateSheetWeight در وب — روی همان لایه‌ی فعلاً انتخاب‌شده ذخیره می‌شود */
    fun updateSheetWeight(value: Double) {
        val layer = _uiState.value.sheetWeightLayer
        _uiState.update {
            if (layer == "5") it.copy(sheetWeight5 = value) else it.copy(sheetWeight3 = value)
        }
        viewModelScope.launch {
            pricingRepo.updateSheetWeight(layer, value)
        }
    }

    /** معادل updateWastePrice در وب */
    fun updateWastePrice(value: Double) {
        _uiState.update { it.copy(wastePrice = value) }
        viewModelScope.launch {
            pricingRepo.updateWastePrice(value)
        }
    }

    // ══ زیرمرحله ۱۰.۲ — متن پیامک + تنظیمات سربرگ فاکتور ══
    // هرکدام معادل دقیق updateSmsTemplate(val) / updateInvoiceSetting(field,val) در وب:
    // بلافاصله هم UI-state و هم دیتابیس آپدیت می‌شوند (بدون دکمه‌ی «ذخیره» جدا).

    fun updateSmsTemplate(value: String) {
        _uiState.update { it.copy(smsTemplate = value) }
        viewModelScope.launch { pricingRepo.updateSmsTemplate(value) }
    }

    fun updateInvoiceCompanyName(value: String) {
        _uiState.update { it.copy(invCompanyName = value) }
        viewModelScope.launch { pricingRepo.updateInvoiceCompanyName(value) }
    }

    fun updateInvoicePhone(value: String) {
        _uiState.update { it.copy(invPhone = value) }
        viewModelScope.launch { pricingRepo.updateInvoicePhone(value) }
    }

    fun updateInvoiceAddress(value: String) {
        _uiState.update { it.copy(invAddress = value) }
        viewModelScope.launch { pricingRepo.updateInvoiceAddress(value) }
    }

    fun updateInvoiceFooter(value: String) {
        _uiState.update { it.copy(invFooter = value) }
        viewModelScope.launch { pricingRepo.updateInvoiceFooter(value) }
    }

    fun updateInvoiceCardNumber(value: String) {
        _uiState.update { it.copy(invCardNumber = value) }
        viewModelScope.launch { pricingRepo.updateInvoiceCardNumber(value) }
    }

    fun updateInvoiceShaba(value: String) {
        _uiState.update { it.copy(invShaba = value) }
        viewModelScope.launch { pricingRepo.updateInvoiceShaba(value) }
    }

    fun updateInvoiceAccountHolderName(value: String) {
        _uiState.update { it.copy(invAccountHolderName = value) }
        viewModelScope.launch { pricingRepo.updateInvoiceAccountHolderName(value) }
    }

    // ══ زیرمرحله ۱۰.۴ — تنظیمات نمایشی ══
    // معادل updateDisplayFontScale / updateDisplayZoom / toggleClickSound / resetDisplaySettings در وب.

    fun updateDisplayFontScale(value: Int) {
        _uiState.update { it.copy(dispFontScale = value) }
        viewModelScope.launch { pricingRepo.updateDisplayFontScale(value) }
    }

    fun updateDisplayZoom(value: Int) {
        _uiState.update { it.copy(dispZoom = value) }
        viewModelScope.launch { pricingRepo.updateDisplayZoom(value) }
    }

    fun toggleClickSound() {
        val newVal = !_uiState.value.dispSoundEnabled
        _uiState.update { it.copy(dispSoundEnabled = newVal) }
        viewModelScope.launch { pricingRepo.updateDisplaySoundEnabled(newVal) }
    }

    fun resetDisplaySettings() {
        _uiState.update { it.copy(dispFontScale = 100, dispZoom = 85, dispSoundEnabled = true) }
        viewModelScope.launch { pricingRepo.updateDisplaySettings(100, 85, true) }
    }

    // ══ زیرمرحله ۱۰.۴ — وضعیت موجودی ورق + آستانه‌ی هشدار ══
    // معادل updateSheetThreshold در وب.

    fun updateThreshold(sheetId: Int, rawValue: String) {
        val n = rawValue.toIntOrNull()
        _uiState.update { state ->
            val newThresholds = state.thresholds.toMutableMap()
            if (n == null || n < 0) newThresholds.remove(sheetId) else newThresholds[sheetId] = n
            state.copy(thresholds = newThresholds)
        }
        viewModelScope.launch { pricingRepo.updateThreshold(sheetId, if (n == null || n < 0) null else n) }
    }

    // ══ زیرمرحله ۱۰.۵ — بکاپ‌گیری / بازیابی / پاک‌کردن کامل ══
    // معادل doBackup / doRestore / clearAllData در وب. UI (SettingsScreen) مسئول باز کردن
    // Storage Access Framework (SAF) برای ذخیره/انتخاب فایل است؛ این ViewModel فقط محتوای
    // JSON را می‌سازد یا از رشته‌ی خوانده‌شده بازیابی می‌کند و پیام وضعیت را نگه می‌دارد —
    // معادل دقیق backupMsg(ok,text) در وب.

    fun suggestedBackupFileName(): String = backupRepo.suggestedFileName()

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null, backupMessageIsError = false) }
    }

    /** ساخت متن JSON بکاپ. onReady با محتوای فایل صدا زده می‌شود تا UI آن را در مسیر
     * انتخاب‌شده توسط کاربر (SAF) بنویسد — معادل دقیق doBackup/_doDownload در وب. */
    fun requestBackup(onReady: (fileName: String, content: String) -> Unit) {
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            try {
                val json = backupRepo.buildBackupJson()
                onReady(backupRepo.suggestedFileName(), json)
                _uiState.update {
                    it.copy(backupBusy = false, backupMessage = "فایل بکاپ با موفقیت ساخته شد.", backupMessageIsError = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        backupBusy = false,
                        backupMessage = "ذخیره ممکن نشد: ${e.message ?: "خطای ناشناس"}",
                        backupMessageIsError = true
                    )
                }
            }
        }
    }

    /** معادل doRestore در وب — jsonText محتوای فایل انتخاب‌شده توسط کاربر است. */
    fun restoreFromJson(jsonText: String) {
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            when (val result = backupRepo.restoreFromJson(jsonText)) {
                is com.rasticpack.app.data.repo.BackupRepository.RestoreResult.Success -> {
                    reload()
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupMessage = "بازیابی موفق — ${result.counts.sheets} ورق · " +
                                "${result.counts.customers} مشتری · ${result.counts.invoices} فاکتور",
                            backupMessageIsError = false
                        )
                    }
                }
                is com.rasticpack.app.data.repo.BackupRepository.RestoreResult.Error -> {
                    _uiState.update {
                        it.copy(backupBusy = false, backupMessage = result.message, backupMessageIsError = true)
                    }
                }
            }
        }
    }

    /** معادل clearAllData در وب — تأیید نهایی (دو تأییدیه) در UI انجام می‌شود، نه اینجا. */
    fun clearAllData() {
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            backupRepo.clearAllData()
            reload()
            _uiState.update {
                it.copy(backupBusy = false, backupMessage = "تمام اطلاعات پاک شد.", backupMessageIsError = false)
            }
        }
    }
}
