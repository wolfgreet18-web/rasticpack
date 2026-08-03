package com.rasticpack.app.data.repo

import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.AppSettingsEntity
import org.json.JSONObject

/**
 * دسترسی به sheetPrices / sheetWeights / wastePrice که در نسخه‌ی وب داخل کلید
 * 'rasticpack-data' با JSON.stringify ذخیره می‌شدند. اینجا همان کلیدگذاری وب
 * ("{layer}-{KT|2T|E}"، مثلاً "3-KT") داخل ستون‌های JSON جدول app_settings حفظ شده
 * تا منطق priceCategoryOf/sheetPriceKey از CalculatorEngine/UI بدون تغییر قابل استفاده باشد.
 *
 * توجه: این Repository فقط «خواندن» (برای مرحله ۳) را پوشش می‌دهد. نوشتن/ویرایش قیمت
 * از تب «ورق» (مرحله ۴) خواهد بود — همان‌جا متدهای update اضافه می‌شوند.
 */
class PricingRepository(private val db: AppDatabase) {

    companion object {
        val PRICE_CATEGORIES = listOf("KT", "2T", "E")

        /** معادل دقیق priceCategoryOf در 4.html */
        fun priceCategoryOf(paperType: String, flute: String): String {
            if (flute == "E") return "E"
            return if (paperType == "2T") "2T" else "KT"
        }

        /** معادل دقیق sheetPriceKey در 4.html */
        fun sheetPriceKey(layer: String, category: String): String = "$layer-$category"
    }

    private suspend fun getOrDefault(): AppSettingsEntity =
        db.appSettingsDao().get() ?: AppSettingsEntity(id = 0).also {
            db.appSettingsDao().upsert(it)
        }

    /** نقشه‌ی کامل قیمت هر متر مربع (جمع محصول+کرایه) — کلید = "{layer}-{category}" */
    suspend fun getSheetPrices(): Map<String, Double> {
        val settings = getOrDefault()
        val json = JSONObject(settings.sheetPricesJson)
        val map = mutableMapOf<String, Double>()
        json.keys().forEach { key -> map[key] = json.optDouble(key, 0.0) }
        return map
    }

    /** قیمت یک دسته‌ی مشخص (لایه+دسته) — یا ۰ اگر ثبت نشده باشد */
    suspend fun getPrice(layer: String, category: String): Double =
        getSheetPrices()[sheetPriceKey(layer, category)] ?: 0.0

    /** وزن هر ۱ متر مربع ورق (گرم) برای هر لایه — {"3": .., "5": ..} */
    suspend fun getSheetWeights(): Map<String, Double> {
        val settings = getOrDefault()
        val json = JSONObject(settings.sheetWeightsJson)
        return mapOf(
            "3" to json.optDouble("3", 0.0),
            "5" to json.optDouble("5", 0.0)
        )
    }

    suspend fun getWastePrice(): Double = getOrDefault().wastePrice

    /** درصد سود پیش‌فرض — در وب مقدار اولیه‌ی فیلد calc2-profit برابر ۲۰ است */
    suspend fun getDefaultProfitPercent(): Double = 20.0

    // ══ مرحله ۴ — نوشتن/ویرایش قیمت، وزن ورق، قیمت ضایعات، آستانه‌ی هشدار ══
    // معادل sheetPriceBreakdown / recalcSheetPriceTotal / sheetWeights / wastePrice / sheetThresholds در وب.

    /** تفکیک قیمت هر دسته (محصول + کرایه حمل) — کلید = "{layer}-{category}" */
    data class PriceBreakdown(val product: Double = 0.0, val freight: Double = 0.0) {
        val total: Double get() = product + freight
    }

    suspend fun getPriceBreakdowns(): Map<String, PriceBreakdown> {
        val json = JSONObject(getOrDefault().sheetPriceBreakdownJson)
        val map = mutableMapOf<String, PriceBreakdown>()
        json.keys().forEach { key ->
            val obj = json.optJSONObject(key)
            map[key] = PriceBreakdown(
                product = obj?.optDouble("product", 0.0) ?: 0.0,
                freight = obj?.optDouble("freight", 0.0) ?: 0.0
            )
        }
        return map
    }

    /**
     * ویرایش تفکیک قیمت یک دسته — هرکدام از product/freight که null بماند، مقدار قبلی‌اش
     * حفظ می‌شود. بعد از هر تغییر، sheetPrices[key] (قیمت کل) دوباره = product+freight
     * محاسبه و ذخیره می‌شود — معادل دقیق recalcSheetPriceTotal در وب.
     */
    suspend fun updatePriceBreakdown(layer: String, category: String, product: Double?, freight: Double?) {
        val key = sheetPriceKey(layer, category)
        val settings = getOrDefault()
        val breakdownJson = JSONObject(settings.sheetPriceBreakdownJson)
        val existing = breakdownJson.optJSONObject(key)
        val newProduct = product ?: existing?.optDouble("product", 0.0) ?: 0.0
        val newFreight = freight ?: existing?.optDouble("freight", 0.0) ?: 0.0
        breakdownJson.put(key, JSONObject().put("product", newProduct).put("freight", newFreight))

        val pricesJson = JSONObject(settings.sheetPricesJson)
        pricesJson.put(key, newProduct + newFreight)

        db.appSettingsDao().upsert(
            settings.copy(
                sheetPriceBreakdownJson = breakdownJson.toString(),
                sheetPricesJson = pricesJson.toString()
            )
        )
    }

    /** آستانه‌ی هشدار هر ورق (کلید = id ورق) — معادل sheetThresholds در وب */
    suspend fun getThresholds(): Map<Int, Int> {
        val json = JSONObject(getOrDefault().sheetThresholdsJson)
        val map = mutableMapOf<Int, Int>()
        json.keys().forEach { k -> k.toIntOrNull()?.let { map[it] = json.optInt(k, 0) } }
        return map
    }

    suspend fun updateThreshold(sheetId: Int, value: Int?) {
        val settings = getOrDefault()
        val json = JSONObject(settings.sheetThresholdsJson)
        if (value == null) json.remove(sheetId.toString()) else json.put(sheetId.toString(), value)
        db.appSettingsDao().upsert(settings.copy(sheetThresholdsJson = json.toString()))
    }

    suspend fun updateSheetWeight(layer: String, value: Double) {
        val settings = getOrDefault()
        val json = JSONObject(settings.sheetWeightsJson)
        json.put(layer, value)
        db.appSettingsDao().upsert(settings.copy(sheetWeightsJson = json.toString()))
    }

    suspend fun updateWastePrice(value: Double) {
        db.appSettingsDao().upsert(getOrDefault().copy(wastePrice = value))
    }

    // ══ زیرمرحله ۱۰.۲ — متن پیامک + تنظیمات سربرگ فاکتور ══
    // معادل smsTemplate و invoiceSettings در وب. جمع‌شده در یک data class تا خواندن/نوشتن
    // یک‌جا و ساده باشد (هر دو در همان جدول app_settings تک‌ردیفه ذخیره می‌شوند).

    data class InvoiceSettings(
        val companyName: String = "رستیک پک",
        val phone: String = "",
        val address: String = "",
        val footer: String = "با تشکر از خرید شما",
        val cardNumber: String = "",
        val shaba: String = "",
        val accountHolderName: String = ""
    )

    suspend fun getSmsTemplate(): String = getOrDefault().smsTemplate

    suspend fun updateSmsTemplate(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(smsTemplate = value))
    }

    suspend fun getInvoiceSettings(): InvoiceSettings {
        val s = getOrDefault()
        return InvoiceSettings(
            companyName = s.invCompanyName,
            phone = s.invPhone,
            address = s.invAddress,
            footer = s.invFooter,
            cardNumber = s.invCardNumber,
            shaba = s.invShaba,
            accountHolderName = s.invAccountHolderName
        )
    }

    /** معادل updateInvoiceSetting(field,val) در وب — فقط یک فیلد را عوض می‌کند */
    suspend fun updateInvoiceCompanyName(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(invCompanyName = value))
    }
    suspend fun updateInvoicePhone(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(invPhone = value))
    }
    suspend fun updateInvoiceAddress(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(invAddress = value))
    }
    suspend fun updateInvoiceFooter(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(invFooter = value))
    }
    suspend fun updateInvoiceCardNumber(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(invCardNumber = value))
    }
    suspend fun updateInvoiceShaba(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(invShaba = value))
    }
    suspend fun updateInvoiceAccountHolderName(value: String) {
        db.appSettingsDao().upsert(getOrDefault().copy(invAccountHolderName = value))
    }

    // ══ زیرمرحله ۱۰.۴ — تنظیمات نمایشی (اندازه فونت/زوم/صدای کلیک) ══
    // معادل displaySettings در وب — dispFontScale/dispZoom/dispSoundEnabled در همان جدول app_settings.

    data class DisplaySettings(
        val fontScale: Int = 100,
        val zoom: Int = 85,
        val soundEnabled: Boolean = true
    )

    suspend fun getDisplaySettings(): DisplaySettings {
        val s = getOrDefault()
        return DisplaySettings(s.dispFontScale, s.dispZoom, s.dispSoundEnabled)
    }

    suspend fun updateDisplayFontScale(value: Int) {
        db.appSettingsDao().upsert(getOrDefault().copy(dispFontScale = value))
    }

    suspend fun updateDisplayZoom(value: Int) {
        db.appSettingsDao().upsert(getOrDefault().copy(dispZoom = value))
    }

    suspend fun updateDisplaySoundEnabled(value: Boolean) {
        db.appSettingsDao().upsert(getOrDefault().copy(dispSoundEnabled = value))
    }

    suspend fun updateDisplaySettings(fontScale: Int, zoom: Int, soundEnabled: Boolean) {
        db.appSettingsDao().upsert(
            getOrDefault().copy(dispFontScale = fontScale, dispZoom = zoom, dispSoundEnabled = soundEnabled)
        )
    }
}

