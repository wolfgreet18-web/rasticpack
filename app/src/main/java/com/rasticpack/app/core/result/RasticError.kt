package com.rasticpack.app.core.result

/**
 * ══ مرحله ۰.۴ — الگوی یکدست مدیریت خطا (فقط قرارداد؛ هنوز جایی استفاده نمی‌شود) ══
 *
 * چرا این وجود دارد: در وضعیت فعلی، هر Repository برای خطا یا `String?` برمی‌گرداند
 * (مثل `CustomerRepository.add(...)`) یا یک `sealed class` محلیِ مخصوص خودش می‌سازد
 * (مثل `InvoiceRepository.SubmitResult`). این یعنی هر ViewModel باید جداگانه یاد بگیرد
 * چطور خطای هر Repository را بخواند، و متن پیام‌های فارسی در همان لحظه‌ی return پراکنده‌اند.
 *
 * از فاز الف (مرحله ۲ به بعد)، هر UseCase به‌جای این الگوهای پراکنده، دقیقاً یکی از
 * زیرکلاس‌های همین `RasticError` را برمی‌گرداند (پیچیده‌شده در `RasticResult.Failure`)،
 * و متن دقیق فارسی هر خطا فقط در یک‌جا (`toUserMessage()`) نگه داشته می‌شود — نه در
 * ده‌ها نقطه‌ی پراکنده‌ی return.
 *
 * تک‌تک این پیام‌ها *کلمه‌به‌کلمه* از پیام‌های فعلی موجود در Repository ها (که خودشان
 * کلمه‌به‌کلمه از 4.html کپی شده بودند) گرفته شده‌اند — طبق قانون «مطابقت کامل با 4.html»
 * در پرامپت معرفی پروژه. این فایل هیچ پیام جدیدی اختراع نمی‌کند.
 */
sealed class RasticError {

    // ══ مشتری‌ها — معادل CustomerRepository ══
    data object CustomerNameBlank : RasticError()
    data object CustomerNameDuplicate : RasticError()
    data class CustomerNotFound(val nameQuery: String? = null) : RasticError()

    // ══ راننده وانت‌ها — معادل DriverRepository ══
    data object DriverNameBlank : RasticError()
    /** افزودن راننده جدید با نامی که از قبل هست — معادل addDriver در وب. */
    data object DriverNameDuplicateOnAdd : RasticError()
    /** ویرایش راننده به نامی که راننده‌ی دیگری از قبل دارد — معادل saveDriverEdit در وب. */
    data object DriverNameDuplicateOnEdit : RasticError()
    data object DriverNotFound : RasticError()

    // ══ موجودی ورق — معادل InventoryRepository ══
    data object InvalidSheetDimensions : RasticError()
    data object InvalidStockQuantity : RasticError()
    data class DuplicateSheet(val sh: Double, val sw: Double, val layerLabel: String, val flute: String, val paperType: String) : RasticError()
    /** تغییر ابعاد یک ورق موجود به مقداری که با ورق دیگری در همان بخش یکسان می‌شود — معادل updateDim در وب. */
    data object DuplicateSheetOnDimUpdate : RasticError()

    // ══ فاکتور — معادل InvoiceRepository.SubmitResult و بقیه‌ی عملیات فاکتور ══
    data class InsufficientStock(val sheetLabel: String?) : RasticError()
    data object NoValidSheetsForInvoice : RasticError()
    /** فاکتوری با این شناسه پیدا نشد — معادل نگهبان‌های `if(!inv) return` پراکنده در وب. */
    data object InvoiceNotFound : RasticError()

    // ══ محاسبه کارتن — معادل runCalc2/calculate در وب ══
    data object InvalidCartonDimensions : RasticError()
    data class MissingSheetPrice(val layerLabel: String, val category: String) : RasticError()

    // ══ خطای عمومی/غیرمنتظره (fallback) ══
    data class Unknown(val message: String? = null) : RasticError()

    /**
     * متن دقیق فارسی هر خطا — کلمه‌به‌کلمه معادل نسخه‌ی وب. این تنها نقطه‌ای است که
     * پیام کاربر از روی نوع خطا ساخته می‌شود؛ UI هرگز خودش رشته نمی‌سازد.
     */
    fun toUserMessage(): String = when (this) {
        CustomerNameBlank -> "نام مشتری را وارد کنید."
        CustomerNameDuplicate -> "مشتری با این نام قبلاً ثبت شده."
        is CustomerNotFound -> "این مشتری ثبت نشده."

        DriverNameBlank -> "نام راننده را وارد کنید."
        DriverNameDuplicateOnAdd -> "راننده‌ای با این نام قبلاً ثبت شده."
        DriverNameDuplicateOnEdit -> "راننده دیگری با این نام قبلاً ثبت شده."
        DriverNotFound -> "راننده پیدا نشد."

        InvalidSheetDimensions -> "طول و عرض ورق را کامل وارد کنید."
        InvalidStockQuantity -> "موجودی را وارد کنید."
        is DuplicateSheet ->
            "ورق ${fmt(sh)}×${fmt(sw)} ($layerLabel · فلوت $flute · $paperType) قبلاً ثبت شده."
        DuplicateSheetOnDimUpdate -> "ورقی با همین ابعاد در همین بخش وجود دارد."

        is InsufficientStock -> "موجودی ورق${sheetLabel?.let { " $it" } ?: ""} کافی نیست."
        NoValidSheetsForInvoice -> "هیچ ورق معتبری برای صدور سند پیدا نشد."
        InvoiceNotFound -> "فاکتور پیدا نشد."

        InvalidCartonDimensions -> "ابعاد و تعداد این کارتن را کامل وارد کنید."
        is MissingSheetPrice -> "قیمت ورق $layerLabel · دسته $category ثبت نشده — از تب «ورق» وارد کنید."

        is Unknown -> message ?: "خطای ناشناخته‌ای رخ داد."
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
