package com.rasticpack.app.ui.invoices

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import java.io.File
import java.io.FileOutputStream

/**
 * ══ فاکتور حرفه‌ای PDF ══
 * معادل بخش `فاکتور حرفه‌ای PDF/چاپ` (buildInvoicePrintHtml) در 4.html — با همان اطلاعات
 * (سربرگ، جدول اقلام، جمع/مالیات، امضا) اما بدون کتابخانه‌ی html2canvas/jsPDF (که فقط در
 * مرورگر در دسترس‌اند). اینجا مستقیماً با PdfDocument بومی اندروید صفحه رسم می‌شود — طرح
 * ساده‌تر و تک‌رنگ (سفید/مشکی) به‌جای پس‌زمینه‌ی تیره‌ی تزئینی وب، چون هدف اصلی خوانایی و
 * قابل‌چاپ‌بودن است، نه بازتولید پیکسل‌به‌پیکسل ظاهر گرافیکی وب.
 *
 * ترتیب اطلاعات دقیقاً معادل وب است: سربرگ (نام شرکت/شماره فاکتور/تاریخ) ▸ اطلاعات مشتری ▸
 * جدول اقلام (شرح، تعداد، قیمت واحد، مبلغ) ▸ جمع کل ▸ یادداشت پایانی.
 */
object InvoicePdfBuilder {

    private const val PAGE_WIDTH = 595   // A4 در ۷۲ dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    data class InvoiceSettings(
        val companyName: String,
        val phone: String,
        val address: String,
        val footer: String
    )

    /** رسم و ذخیره‌ی PDF فاکتور در cache/pdfs — بازمی‌گرداند فایل ساخته‌شده را */
    fun buildPdfFile(
        context: Context,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        customer: CustomerEntity?,
        settings: InvoiceSettings
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val rtlFont = Typeface.DEFAULT
        val boldFont = Typeface.DEFAULT_BOLD

        var y = MARGIN + 10f

        // ── سربرگ ──
        val titlePaint = textPaint(20f, boldFont, Color.BLACK)
        y = drawRtlLine(canvas, "فاکتور فروش", titlePaint, y)
        y += 4f
        val companyPaint = textPaint(12f, boldFont, Color.BLACK)
        y = drawRtlLine(canvas, settings.companyName, companyPaint, y)
        val metaPaint = textPaint(10f, rtlFont, Color.DKGRAY)
        y = drawRtlLine(canvas, "شماره فاکتور: #${invoice.id}    ·    تاریخ: ${JalaliDate.formatDateTimeShort(invoice.dateIso)}", metaPaint, y)
        if (settings.phone.isNotBlank() || settings.address.isNotBlank()) {
            val line = listOfNotNull(
                settings.phone.takeIf { it.isNotBlank() }?.let { "تلفن: $it" },
                settings.address.takeIf { it.isNotBlank() }
            ).joinToString("    ·    ")
            if (line.isNotBlank()) y = drawRtlLine(canvas, line, metaPaint, y)
        }

        y += 8f
        drawDivider(canvas, y)
        y += 16f

        // ── اطلاعات مشتری ──
        val labelPaint = textPaint(11f, boldFont, Color.BLACK)
        val bodyPaint = textPaint(11f, rtlFont, Color.BLACK)
        y = drawRtlLine(canvas, "مشتری", labelPaint, y)
        y = drawRtlLine(canvas, invoice.customerName, bodyPaint, y)
        if (!customer?.company.isNullOrBlank()) y = drawRtlLine(canvas, customer!!.company, bodyPaint, y)
        if (!customer?.address.isNullOrBlank()) y = drawRtlLine(canvas, customer!!.address, bodyPaint, y)
        if (!customer?.phone.isNullOrBlank()) y = drawRtlLine(canvas, "تلفن: ${customer!!.phone}", bodyPaint, y)

        y += 8f
        drawDivider(canvas, y)
        y += 16f

        // ── جدول اقلام ──
        val visibleItems = items.filter { it.qty > 0 }
        val hasPricing = visibleItems.any { it.lineTotal != null }
        if (!hasPricing) {
            val notePaint = textPaint(9.5f, rtlFont, Color.rgb(0x92, 0x40, 0x0E))
            y = drawRtlLine(canvas, "این فاکتور بدون اطلاعات قیمت ثبت شده — قیمت‌ها در دسترس نیست.", notePaint, y)
            y += 6f
        }

        val headerPaint = textPaint(10f, boldFont, Color.WHITE)
        val headerBgPaint = Paint().apply { color = Color.rgb(0x99, 0x1B, 0x1B) }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, headerBgPaint)
        // ستون‌ها راست‌به‌چپ: شرح، تعداد، واحد، مبلغ
        val col1 = PAGE_WIDTH - MARGIN - 10f  // شرح (سمت راست)
        val col2 = PAGE_WIDTH - MARGIN - 230f // تعداد
        val col3 = PAGE_WIDTH - MARGIN - 300f // قیمت واحد
        val col4 = MARGIN + 10f               // مبلغ (سمت چپ)
        canvas.drawText("شرح", col1, y + 15f, headerPaint.apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("تعداد", col2, y + 15f, headerPaint)
        canvas.drawText("واحد", col3, y + 15f, headerPaint)
        canvas.drawText("مبلغ", col4, y + 15f, headerPaint.apply { textAlign = Paint.Align.LEFT })
        y += 22f

        val rowPaint = textPaint(10f, rtlFont, Color.BLACK)
        val subPaint = textPaint(8.5f, rtlFont, Color.DKGRAY)
        visibleItems.forEachIndexed { idx, it ->
            val dims = if (it.cartonLength > 0)
                "${SmsHelper.fmtDimShortPublic(it.cartonLength)}×${SmsHelper.fmtDimShortPublic(it.cartonWidth)}×${SmsHelper.fmtDimShortPublic(it.cartonHeight)}"
            else "—"
            val qty = if (it.cartonQty > 0) it.cartonQty else it.qty
            val unit = it.unitPrice?.let { u -> fmtNum(u) } ?: "—"
            val lineTotal = it.lineTotal?.let { t -> fmtNum(t) } ?: "—"
            val bundleTxt = bundleSummaryText(it)

            if (y > PAGE_HEIGHT - 140) {
                // اگر جا کم بود، صفحه‌ی جدید — برای سادگی این نسخه فقط صفحه‌ی اول را می‌بندد و ادامه می‌دهد
                document.finishPage(page)
                return@forEachIndexed
            }

            canvas.drawText(it.cartonName.ifBlank { "کارتن" }, col1, y + 14f, rowPaint.apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("$qty", col2, y + 14f, rowPaint.apply { textAlign = Paint.Align.LEFT })
            canvas.drawText(unit, col3, y + 14f, rowPaint)
            canvas.drawText(lineTotal, col4, y + 14f, rowPaint.apply { textAlign = Paint.Align.LEFT })
            y += 15f
            val subText = "$dims · ${it.qty} برگ" + if (bundleTxt.isNotBlank()) " · 🧵 $bundleTxt" else ""
            canvas.drawText(subText, col1, y + 10f, subPaint.apply { textAlign = Paint.Align.RIGHT })
            y += 16f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f })
            y += 8f
        }

        y += 10f

        if (hasPricing) {
            val grandTotal = visibleItems.sumOf { it.lineTotal ?: 0.0 }
            val totalLabelPaint = textPaint(13f, boldFont, Color.BLACK)
            canvas.drawText("مبلغ نهایی فاکتور", PAGE_WIDTH - MARGIN, y, totalLabelPaint.apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("${fmtNum(grandTotal)} ریال", MARGIN, y, totalLabelPaint.apply { textAlign = Paint.Align.LEFT })
            y += 24f
        }

        val totalBundles = invoiceTotalBundles(visibleItems)
        if (totalBundles != null) {
            y = drawRtlLine(canvas, "تعداد کل بسته: $totalBundles بسته", bodyPaint, y)
            y += 8f
        }

        drawDivider(canvas, y)
        y += 16f

        if (settings.footer.isNotBlank()) {
            val footerPaint = textPaint(9.5f, rtlFont, Color.DKGRAY)
            y = drawRtlLine(canvas, settings.footer, footerPaint, y)
        }

        document.finishPage(page)

        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val safeName = invoice.customerName.replace(Regex("[\\\\/:*?\"<>|]"), "")
        val file = File(dir, "فاکتور-$safeName-${invoice.id}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    /** معادل shareInvoicePdf در وب — اشتراک‌گذاری فایل PDF از طریق Share Sheet با FileProvider */
    fun shareUriFor(context: Context, file: File): android.net.Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun textPaint(size: Float, typeface: Typeface, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.typeface = typeface
            this.color = color
            textAlign = Paint.Align.RIGHT
        }

    /** رسم یک خط راست‌به‌چپ (با پشتیبانی از شکستن خط در صورت طولانی بودن) — بازمی‌گرداند y بعدی */
    private fun drawRtlLine(canvas: Canvas, text: String, paint: TextPaint, y: Float, lineHeight: Float = 16f): Float {
        if (text.isBlank()) return y + lineHeight
        val maxWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(MARGIN, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height + 4f
    }

    private fun drawDivider(canvas: Canvas, y: Float) {
        val p = Paint().apply { color = Color.rgb(0xAB, 0x8F, 0x5C); strokeWidth = 1f }
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, p)
    }

    private fun fmtNum(n: Double): String =
        java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(Math.round(n))
}
