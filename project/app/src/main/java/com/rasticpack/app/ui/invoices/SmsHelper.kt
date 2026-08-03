package com.rasticpack.app.ui.invoices

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.VanDriverEntity

/**
 * ══ پیامک (ارسال با سیم‌کارت خود گوشی — از طریق اپ پیامک) ══
 * معادل دقیق بخش «پیامک» و «پیامک به راننده وانت» در 4.html.
 * هر تابع build*SmsBody متن را می‌سازد و buildSmsIntent همان Intent باز کردن اپ پیامک
 * را برمی‌گرداند (معادل buildSmsLink + window.location.href='sms:...' در وب).
 */
object SmsHelper {

    /** معادل sanitizePhone در وب */
    fun sanitizePhone(phone: String?): String =
        (phone ?: "").replace(Regex("[^\\d+]"), "")

    /** معادل neshanMapUrl در وب */
    fun neshanMapUrl(lat: Double, lng: Double): String =
        "https://neshan.org/maps?lat=$lat&lng=$lng"

    /** معادل customerLocationLink در وب */
    fun customerLocationLink(customer: CustomerEntity?): String? {
        if (customer?.lat != null && customer.lng != null) return neshanMapUrl(customer.lat, customer.lng)
        return customer?.locationLink
    }

    /** معادل fillSmsTemplate در وب — جایگزینی {نام}/{شرکت}/{تعداد}/{تاریخ} */
    fun fillSmsTemplate(tpl: String, name: String, company: String, qty: Int, dateLabel: String): String =
        tpl
            .replace("{نام}", name)
            .replace("{شرکت}", company)
            .replace("{تعداد}", qty.toString())
            .replace("{تاریخ}", dateLabel)

    /** معادل buildInvoiceItemsSmsBody در وب */
    fun buildInvoiceItemsSmsBody(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): String {
        val visibleItems = items.filter { it.qty > 0 }
        val lines = visibleItems.joinToString("\n  ______________\n") { it ->
            val dims = if (it.cartonLength > 0)
                "${fmtDimShort(it.cartonLength)}×${fmtDimShort(it.cartonWidth)}×${fmtDimShort(it.cartonHeight)}"
            else "—"
            val qty = if (it.cartonQty > 0) it.cartonQty else it.qty
            val unit = it.unitPrice?.let { u -> fmtNumInv(u) } ?: "—"
            val lineTotal = it.lineTotal?.let { t -> fmtNumInv(t) } ?: "—"
            val bundleTxt = bundleSummaryText(it)
            buildString {
                append("📦کارتن $dims\n")
                append("تعداد: $qty عدد")
                if (bundleTxt.isNotBlank()) append("\nبسته‌بندی: $bundleTxt")
                append("\nقیمت واحد:\n $unit ریال\nجمع: \n $lineTotal ریال")
            }
        }
        val grandTotal = visibleItems.sumOf { it.lineTotal ?: 0.0 }
        val hasPricing = visibleItems.any { it.lineTotal != null }
        val totalBundles = invoiceTotalBundles(visibleItems)
        val bundleLine = totalBundles?.let { "\nتعداد کل بسته: $it بسته" } ?: ""
        val totalLine = if (hasPricing) "\n________________\nمبلغ کل:\n${fmtNumInv(grandTotal)}ریال" else ""
        return lines + bundleLine + totalLine
    }

    /** معادل buildCardShabaSmsBody در وب */
    fun buildCardShabaSmsBody(cardNumber: String, shaba: String, accountHolderName: String): String {
        val lines = mutableListOf<String>()
        if (cardNumber.isNotBlank()) lines.add("شماره کارت: $cardNumber")
        if (shaba.isNotBlank()) lines.add("شماره شبا: $shaba")
        if (accountHolderName.isNotBlank()) lines.add("به نام: $accountHolderName")
        return lines.joinToString("\n")
    }

    /** معادل buildStoreAddressSmsBody در وب */
    fun buildStoreAddressSmsBody(address: String): String = address.trim()

    /** معادل buildVanItemLines در وب */
    private fun buildVanItemLines(items: List<InvoiceItemEntity>): String =
        items.filter { it.qty > 0 }.joinToString("\n") { it ->
            val dims = if (it.cartonLength > 0)
                "${fmtDimShort(it.cartonLength)}×${fmtDimShort(it.cartonWidth)}×${fmtDimShort(it.cartonHeight)}"
            else "—"
            val qty = if (it.cartonQty > 0) it.cartonQty else it.qty
            val bundleTxt = bundleSummaryText(it)
            buildString {
                append("📦 $dims\n")
                append("تعداد: $qty عدد")
                if (bundleTxt.isNotBlank()) append("\n($bundleTxt)")
            }
        }

    /** معادل buildVanSmsBody در وب — اطلاعات کامل بار برای راننده وانت */
    fun buildVanSmsBody(invoice: InvoiceEntity, items: List<InvoiceItemEntity>, customer: CustomerEntity?): String {
        val visibleItems = items.filter { it.qty > 0 }
        val totalCartons = visibleItems.sumOf { if (it.cartonQty > 0) it.cartonQty else it.qty }
        val lines = mutableListOf<String>()
        lines.add(buildVanItemLines(visibleItems))
        lines.add("تعداد کل کارتن: ")
        lines.add("$totalCartons عدد")
        invoiceTotalBundles(visibleItems)?.let {
            lines.add("تعداد کل بسته: ")
            lines.add("$it بسته")
        }
        lines.add("ـــــــــــــــــــــــــــــــــ")
        lines.add("نام مشتری: ${invoice.customerName}")
        if (!customer?.company.isNullOrBlank()) lines.add("نام شرکت: ${customer!!.company}")
        if (!customer?.phone.isNullOrBlank()) lines.add("شماره تماس مشتری: ${customer!!.phone}")
        if (!customer?.address.isNullOrBlank()) lines.add("آدرس: ${customer!!.address}")
        customerLocationLink(customer)?.let { lines.add("لوکیشن: $it") }
        return lines.joinToString("\n")
    }

    /** معادل buildVanInfoForCustomerSmsBody در وب — پیامک به مشتری با اطلاعات راننده */
    fun buildVanInfoForCustomerSmsBody(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        driver: VanDriverEntity
    ): String {
        val visibleItems = items.filter { it.qty > 0 }
        val totalCartons = visibleItems.sumOf { if (it.cartonQty > 0) it.cartonQty else it.qty }
        val lines = mutableListOf<String>()
        lines.add("🛻 ${invoice.customerName} عزیز، بار شما ($totalCartons عدد کارتن) با وانت زیر ارسال می‌شود:")
        lines.add("راننده: ${driver.name}")
        if (driver.phone.isNotBlank()) lines.add("شماره تماس راننده: ${driver.phone}")
        if (driver.plate.isNotBlank()) lines.add("شماره پلاک: ${driver.plate}")
        lines.add("")
        lines.add(buildVanItemLines(visibleItems))
        lines.add("")
        lines.add("تعداد کل کارتن: $totalCartons عدد")
        invoiceTotalBundles(visibleItems)?.let { lines.add("تعداد کل بسته: $it بسته") }
        return lines.joinToString("\n")
    }

    /** معادل buildSmsLink + window.location.href='sms:...' در وب — باز کردن اپ پیامک با متن پرشده */
    fun smsIntent(phone: String, body: String): Intent {
        val uri = "sms:$phone".toUri()
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
        }
    }

    /** باز کردن اپ پیامک برای تماس بدون شماره‌ی مشخص (معادل window.location.href='tel:' نبود؛
     * اینجا مستقیم از قابلیت انتخاب مخاطب استاندارد اندروید برای اپ پیامک استفاده می‌کنیم) */
    fun startSms(context: Context, phone: String, body: String) {
        try {
            context.startActivity(smsIntent(phone, body))
        } catch (e: Exception) {
            // اگر اپ پیامک در دسترس نبود، بی‌صدا نادیده گرفته می‌شود؛ صفحه می‌تواند Toast نشان دهد
        }
    }

    /** اشتراک‌گذاری متن ساده از طریق Share Sheet اندروید — معادل shareInvoiceText در وب */
    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun fmtDimShort(v: Double): String = fmtDimShortPublic(v)
    fun fmtDimShortPublic(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    private fun fmtNumInv(n: Double): String =
        java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(Math.round(n))
}
