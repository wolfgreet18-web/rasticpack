package com.rasticpack.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * معادل بخش‌های کوچک/سراسری تنظیمات نسخه‌ی وب که آنجا در window.storage با کلید
 * 'rasticpack-data' ذخیره می‌شدند (به‌جز inventory/invoices/customers که هرکدام جدول
 * جدای خودشان را دارند). این یک جدول تک‌ردیفه است (id همیشه ۰).
 *
 * نقشه‌های کلید-مقدار (sheetPrices، sheetPriceBreakdown، sheetWeights، sheetThresholds)
 * به‌صورت رشته‌ی JSON ذخیره می‌شوند — دقیقاً همان کلیدگذاری وب («layer-category»، مثلاً
 * "3-KT" یا "5-E") را حفظ می‌کنند تا منطق priceCategoryOf/sheetPriceKey بدون تغییر منتقل شود.
 *
 * توجه: بر خلاف وب، اینجا دیگر نیازی به nextId/nextCustId/... نیست — چون Room با
 * autoGenerate خودش شناسه‌ی یکتا می‌سازد.
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,

    // JSON: Map<String, Double> — کلید = "{layer}-{KT|2T|E}" (مثلاً "3-KT")
    val sheetPricesJson: String = "{}",
    // JSON: Map<String, {product, freight}>
    val sheetPriceBreakdownJson: String = "{}",
    // JSON: {"3": Double, "5": Double}
    val sheetWeightsJson: String = "{\"3\":0,\"5\":0}",
    val wastePrice: Double = 0.0,
    // JSON: Map<String(sheetId), Int(threshold)>
    val sheetThresholdsJson: String = "{}",

    val smsTemplate: String = "",

    // تنظیمات سربرگ فاکتور
    val invCompanyName: String = "رستیک پک",
    val invPhone: String = "",
    val invAddress: String = "",
    val invFooter: String = "با تشکر از خرید شما",
    val invCardNumber: String = "",
    val invShaba: String = "",
    val invAccountHolderName: String = "",

    // تنظیمات نمایشی
    val dispFontScale: Int = 100,
    val dispZoom: Int = 85,
    val dispSoundEnabled: Boolean = true
)
