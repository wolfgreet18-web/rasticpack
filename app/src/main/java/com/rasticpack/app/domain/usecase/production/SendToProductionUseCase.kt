package com.rasticpack.app.domain.usecase.production

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.ProductionQueueItem
import com.rasticpack.app.domain.repository.InvoiceRepository
import com.rasticpack.app.domain.repository.ProductionQueueRepository
import java.time.Instant
import javax.inject.Inject

/**
 * ══ مرحله ۳.۴ (نقشه معماری v2.9) — SendToProductionUseCase ══
 * معادل دقیق sendAllToProduction در 4.html و منطق موجود در data/repo/InvoiceRepository
 * قدیمی (sendAllToProduction):
 *
 * BUG FIX (هم‌راستا با وب): قبلاً هر فاکتور فقط یک‌بار حق داشت به صف تولید اضافه شود؛
 * کلیک‌های بعدی فقط به تب تولید می‌بردند. اگر کاربر یک آیتم را از صف حذف می‌کرد، دیگر
 * راهی برای اضافه‌کردن دوباره‌ی همان آیتم نبود. الان: دکمه همیشه فعال است. هر آیتم
 * فاکتور یک sourceKey پایدار ("{invoiceId}-{itemIndex}") دارد که روی رکورد صف تولید
 * هم ذخیره می‌شود؛ با هر فراخوانی، فقط آیتم‌هایی که sourceKey‌شان همین الان در صف
 * نیست (چون حذف شده یا هنوز اضافه نشده) دوباره اضافه می‌شوند — آیتم‌های موجود در صف
 * دست‌نخورده می‌مانند (بدون تکرار).
 *
 * خروجی موفق: شناسه‌ی اولین رکورد صف تولید مربوط به این فاکتور — یا رکورد تازه‌اضافه‌شده،
 * یا اگر چیزی تازه اضافه نشد (همه از قبل در صف بودند)، اولین رکورد موجود مربوط به همین
 * فاکتور — برای apply خودکار به فرم محاسبه، معادل applyProductionItem(firstId) در وب.
 * اگر فاکتور پیدا نشود یا هیچ آیتم معتبری (qty>0) نداشته باشد، null موفق برمی‌گردد
 * (نه خطا — دقیقاً مثل وب که در این حالت سکوت می‌کند و کاری انجام نمی‌دهد).
 */
class SendToProductionUseCase @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val productionQueueRepository: ProductionQueueRepository
) {
    suspend operator fun invoke(invoiceId: Int): RasticResult<Int?> {
        val iw = invoiceRepository.getById(invoiceId) ?: return RasticResult.success(null)
        if (iw.items.none { it.qty > 0 }) return RasticResult.success(null)

        val existingKeys = productionQueueRepository.getAllSourceKeys()
        var firstNewId: Int? = null
        val nowIso = Instant.now().toString()

        iw.items.forEachIndexed { idx, item ->
            if (item.qty <= 0) return@forEachIndexed
            val sourceKey = "$invoiceId-$idx"
            if (existingKeys.contains(sourceKey)) return@forEachIndexed
            val record = ProductionQueueItem(
                sourceKey = sourceKey,
                name = item.cartonName.ifBlank { "کارتن" },
                length = item.cartonLength, width = item.cartonWidth, height = item.cartonHeight,
                glue = if (item.glue > 0) item.glue else 4.0,
                sh = item.sh, sw = item.sw, layer = item.layer,
                customerName = iw.invoice.customerName,
                sentAtIso = nowIso
            )
            val newId = productionQueueRepository.insert(record)
            if (firstNewId == null) firstNewId = newId
        }

        // معادل if(productionQueue.length>30) productionQueue.length=30;
        productionQueueRepository.trimTo30()

        invoiceRepository.updateInvoice(iw.invoice.copy(sentToProduction = true))

        if (firstNewId != null) return RasticResult.success(firstNewId)

        // چیزی تازه اضافه نشد (همه از قبل در صف بودند) — اولین رکورد موجود مربوط به
        // همین فاکتور را برای apply خودکار برگردان.
        val existing = productionQueueRepository.findFirstByInvoicePrefix(invoiceId)
        return RasticResult.success(existing?.id)
    }
}
