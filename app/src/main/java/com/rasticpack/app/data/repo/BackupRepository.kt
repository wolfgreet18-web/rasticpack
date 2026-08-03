package com.rasticpack.app.data.repo

import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.AppSettingsEntity
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.ProductionQueueItemEntity
import com.rasticpack.app.data.entities.VanDriverEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * زیرمرحله ۱۰.۵ — بکاپ‌گیری (خروجی JSON) / بازیابی / پاک‌کردن کامل اطلاعات.
 * معادل دقیق doBackup / doRestore / clearAllData در 4.html، با یک تفاوت ساختاری:
 * در وب همه‌چیز (inventory, customers, invoices, ...) در یک آبجکت واحد JSON.stringify
 * می‌شد چون همه در حافظه (آرایه‌های جاوااسکریپت) بودند؛ اینجا همان ساختار JSON دقیقاً
 * بازتولید می‌شود (همان کلیدها: app/v/at/inventory/customers/invoices/...) اما هر
 * بخش از جدول Room مربوط به خودش خوانده/نوشته می‌شود.
 *
 * فرمت خروجی intentionally سازگار با فایل بکاپ نسخه‌ی وب نگه داشته نمی‌شود مو‌به‌مو
 * (چون ساختار invoices آنجا invoice+items تودرتو بود و اینجا دو جدول جدا هستند) —
 * ولی این Repository همیشه فایلی می‌سازد که خودش هم بتواند بخواند (self-consistent)،
 * و اگر فایل ورودی ساختار نسخه‌ی وب (invoices تودرتو با items داخلش) را داشته باشد
 * هم قابل‌شناسایی و migrate است، تا بکاپ‌های گرفته‌شده از نسخه‌ی وب هم قابل‌بازیابی باشند.
 */
class BackupRepository(private val db: AppDatabase) {

    companion object {
        const val APP_TAG = "rasticpack"
        const val BACKUP_VERSION = 1
    }

    data class RestoreCounts(val sheets: Int, val customers: Int, val invoices: Int)

    sealed class RestoreResult {
        data class Success(val counts: RestoreCounts) : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    /** ساخت نام فایل پیشنهادی — معادل الگوی 'rasticpack-YYYY-MM-DD.json' در وب */
    fun suggestedFileName(): String {
        val today = java.time.LocalDate.now().toString() // YYYY-MM-DD
        return "rasticpack-$today.json"
    }

    // ══ بکاپ‌گیری — خروجی JSON کامل ══
    suspend fun buildBackupJson(): String {
        val inventory = db.inventoryDao().getAll()
        val customers = db.customerDao().getAll()
        val invoicesWithItems = db.invoiceDao().observeAllWithItems()
        val invoiceList = firstOf(invoicesWithItems)
        val drivers = firstOf(db.vanDriverDao().observeAll())
        val production = firstOf(db.productionQueueDao().observeAll())
        val settings = db.appSettingsDao().get() ?: AppSettingsEntity(id = 0)

        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("v", BACKUP_VERSION)
        root.put("at", Instant.now().toString())

        root.put("inventory", JSONArray().apply {
            inventory.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("sw", s.sw); put("sh", s.sh)
                    put("layer", s.layer); put("qty", s.qty)
                    put("flute", s.flute); put("paperType", s.paperType)
                })
            }
        })

        root.put("customers", JSONArray().apply {
            customers.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("company", c.company)
                    put("address", c.address); put("phone", c.phone)
                    put("lat", c.lat ?: JSONObject.NULL)
                    put("lng", c.lng ?: JSONObject.NULL)
                    put("locationLink", c.locationLink ?: JSONObject.NULL)
                })
            }
        })

        root.put("invoices", JSONArray().apply {
            invoiceList.forEach { iw ->
                val inv = iw.invoice
                put(JSONObject().apply {
                    put("id", inv.id)
                    put("customerId", inv.customerId)
                    put("customerName", inv.customerName)
                    put("date", inv.dateIso)
                    put("status", inv.status)
                    put("totalSheets", inv.totalSheets)
                    put("sent", inv.sent)
                    put("sentToProduction", inv.sentToProduction)
                    put("paidAmount", inv.paidAmount ?: JSONObject.NULL)
                    put("editedAt", inv.editedAtIso ?: JSONObject.NULL)
                    put("items", JSONArray().apply {
                        iw.items.forEach { it2 ->
                            put(JSONObject().apply {
                                put("sheetId", it2.sheetId); put("sw", it2.sw); put("sh", it2.sh)
                                put("layer", it2.layer); put("qty", it2.qty)
                                put("cartonName", it2.cartonName)
                                put("cartonLength", it2.cartonLength)
                                put("cartonWidth", it2.cartonWidth)
                                put("cartonHeight", it2.cartonHeight)
                                put("glue", it2.glue)
                                put("cartonQty", it2.cartonQty)
                                put("unitPrice", it2.unitPrice ?: JSONObject.NULL)
                                put("lineTotal", it2.lineTotal ?: JSONObject.NULL)
                                put("itemProfit", it2.itemProfit ?: JSONObject.NULL)
                                put("stockAfter", it2.stockAfter ?: JSONObject.NULL)
                                put("bundleSize", it2.bundleSize ?: JSONObject.NULL)
                            })
                        }
                    })
                })
            }
        })

        root.put("vanDrivers", JSONArray().apply {
            drivers.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("name", d.name); put("phone", d.phone); put("plate", d.plate)
                })
            }
        })

        root.put("productionQueue", JSONArray().apply {
            production.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("sourceKey", p.sourceKey); put("name", p.name)
                    put("length", p.length); put("width", p.width); put("height", p.height)
                    put("glue", p.glue); put("sh", p.sh); put("sw", p.sw); put("layer", p.layer)
                    put("customerName", p.customerName); put("sentAt", p.sentAtIso)
                })
            }
        })

        root.put("sheetPrices", JSONObject(settings.sheetPricesJson))
        root.put("sheetPriceBreakdown", JSONObject(settings.sheetPriceBreakdownJson))
        root.put("sheetWeights", JSONObject(settings.sheetWeightsJson))
        root.put("wastePrice", settings.wastePrice)
        root.put("sheetThresholds", JSONObject(settings.sheetThresholdsJson))
        root.put("smsTemplate", settings.smsTemplate)
        root.put("invoiceSettings", JSONObject().apply {
            put("companyName", settings.invCompanyName)
            put("phone", settings.invPhone)
            put("address", settings.invAddress)
            put("footer", settings.invFooter)
            put("cardNumber", settings.invCardNumber)
            put("shaba", settings.invShaba)
            put("accountHolderName", settings.invAccountHolderName)
        })
        root.put("displaySettings", JSONObject().apply {
            put("fontScale", settings.dispFontScale)
            put("zoom", settings.dispZoom)
            put("soundEnabled", settings.dispSoundEnabled)
        })

        return root.toString(2)
    }

    // ══ بازیابی — جایگزینی کامل داده‌های فعلی با محتوای فایل ══
    suspend fun restoreFromJson(jsonText: String): RestoreResult {
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return RestoreResult.Error("فایل پشتیبان خراب یا نامعتبر است: JSON نامعتبر")
        }

        val appTag = root.optString("app", "")
        if (appTag.isNotBlank() && appTag != APP_TAG) {
            return RestoreResult.Error("این فایل متعلق به برنامه دیگری است.")
        }

        val hasInventory = root.has("inventory") && root.opt("inventory") is JSONArray
        val hasCustomers = root.has("customers") && root.opt("customers") is JSONArray
        val hasInvoices = root.has("invoices") && root.opt("invoices") is JSONArray
        if (!hasInventory && !hasCustomers && !hasInvoices) {
            return RestoreResult.Error("ساختار فایل پشتیبان ناقص یا خراب است.")
        }

        return try {
            // ۱) پاک کردن کامل جدول‌های فعلی
            db.invoiceDao().clearItems()
            db.invoiceDao().clearInvoices()
            db.customerDao().clear()
            db.inventoryDao().clear()
            db.vanDriverDao().clear()
            db.productionQueueDao().clear()

            // ۲) درج موجودی
            val inventoryArr = root.optJSONArray("inventory") ?: JSONArray()
            for (i in 0 until inventoryArr.length()) {
                val o = inventoryArr.getJSONObject(i)
                db.inventoryDao().insert(
                    InventorySheetEntity(
                        id = o.optInt("id", 0),
                        sw = o.optDouble("sw", 0.0),
                        sh = o.optDouble("sh", 0.0),
                        layer = o.optString("layer", "3"),
                        qty = o.optInt("qty", 0),
                        flute = o.optString("flute", "C"),
                        paperType = o.optString("paperType", "2T")
                    )
                )
            }

            // ۳) درج مشتریان
            val customersArr = root.optJSONArray("customers") ?: JSONArray()
            for (i in 0 until customersArr.length()) {
                val o = customersArr.getJSONObject(i)
                db.customerDao().insert(
                    CustomerEntity(
                        id = o.optInt("id", 0),
                        name = o.optString("name", ""),
                        company = o.optString("company", ""),
                        address = o.optString("address", ""),
                        phone = o.optString("phone", ""),
                        lat = if (o.isNull("lat")) null else o.optDouble("lat"),
                        lng = if (o.isNull("lng")) null else o.optDouble("lng"),
                        locationLink = if (o.isNull("locationLink")) null else o.optString("locationLink")
                    )
                )
            }

            // ۴) درج فاکتورها + آیتم‌ها (ساختار وب: هر فاکتور آرایه‌ی items تودرتو دارد)
            val invoicesArr = root.optJSONArray("invoices") ?: JSONArray()
            for (i in 0 until invoicesArr.length()) {
                val o = invoicesArr.getJSONObject(i)
                val invoiceId = o.optInt("id", 0)
                db.invoiceDao().insertInvoice(
                    InvoiceEntity(
                        id = invoiceId,
                        customerId = o.optInt("customerId", 0),
                        customerName = o.optString("customerName", ""),
                        dateIso = o.optString("date", Instant.now().toString()),
                        status = o.optString("status", "draft"),
                        totalSheets = o.optInt("totalSheets", 0),
                        sent = o.optBoolean("sent", false),
                        sentToProduction = o.optBoolean("sentToProduction", false),
                        paidAmount = if (o.isNull("paidAmount")) null else o.optDouble("paidAmount"),
                        editedAtIso = if (o.isNull("editedAt")) null else o.optString("editedAt")
                    )
                )
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<InvoiceItemEntity>()
                for (j in 0 until itemsArr.length()) {
                    val it2 = itemsArr.getJSONObject(j)
                    items.add(
                        InvoiceItemEntity(
                            invoiceId = invoiceId,
                            sheetId = it2.optInt("sheetId", 0),
                            sw = it2.optDouble("sw", 0.0),
                            sh = it2.optDouble("sh", 0.0),
                            layer = it2.optString("layer", "3"),
                            qty = it2.optInt("qty", 0),
                            cartonName = it2.optString("cartonName", ""),
                            cartonLength = it2.optDouble("cartonLength", 0.0),
                            cartonWidth = it2.optDouble("cartonWidth", 0.0),
                            cartonHeight = it2.optDouble("cartonHeight", 0.0),
                            glue = it2.optDouble("glue", 0.0),
                            cartonQty = it2.optInt("cartonQty", 0),
                            unitPrice = if (it2.isNull("unitPrice")) null else it2.optDouble("unitPrice"),
                            lineTotal = if (it2.isNull("lineTotal")) null else it2.optDouble("lineTotal"),
                            itemProfit = if (it2.isNull("itemProfit")) null else it2.optDouble("itemProfit"),
                            stockAfter = if (it2.isNull("stockAfter")) null else it2.optInt("stockAfter"),
                            bundleSize = if (it2.isNull("bundleSize")) null else it2.optInt("bundleSize")
                        )
                    )
                }
                if (items.isNotEmpty()) db.invoiceDao().insertItems(items)
            }

            // ۵) درج رانندگان
            val driversArr = root.optJSONArray("vanDrivers") ?: JSONArray()
            for (i in 0 until driversArr.length()) {
                val o = driversArr.getJSONObject(i)
                db.vanDriverDao().insert(
                    VanDriverEntity(
                        id = o.optInt("id", 0),
                        name = o.optString("name", ""),
                        phone = o.optString("phone", ""),
                        plate = o.optString("plate", "")
                    )
                )
            }

            // ۶) درج صف تولید
            val prodArr = root.optJSONArray("productionQueue") ?: JSONArray()
            for (i in 0 until prodArr.length()) {
                val o = prodArr.getJSONObject(i)
                db.productionQueueDao().insert(
                    ProductionQueueItemEntity(
                        id = o.optInt("id", 0),
                        sourceKey = o.optString("sourceKey", ""),
                        name = o.optString("name", ""),
                        length = o.optDouble("length", 0.0),
                        width = o.optDouble("width", 0.0),
                        height = o.optDouble("height", 0.0),
                        glue = o.optDouble("glue", 4.0),
                        sh = o.optDouble("sh", 0.0),
                        sw = o.optDouble("sw", 0.0),
                        layer = o.optString("layer", "3"),
                        customerName = o.optString("customerName", ""),
                        sentAtIso = o.optString("sentAt", Instant.now().toString())
                    )
                )
            }

            // ۷) تنظیمات (app_settings تک‌ردیفه)
            val invSettings = root.optJSONObject("invoiceSettings") ?: JSONObject()
            val dispSettings = root.optJSONObject("displaySettings") ?: JSONObject()
            db.appSettingsDao().upsert(
                AppSettingsEntity(
                    id = 0,
                    sheetPricesJson = (root.optJSONObject("sheetPrices") ?: JSONObject()).toString(),
                    sheetPriceBreakdownJson = (root.optJSONObject("sheetPriceBreakdown") ?: JSONObject()).toString(),
                    sheetWeightsJson = (root.optJSONObject("sheetWeights") ?: JSONObject().put("3", 0).put("5", 0)).toString(),
                    wastePrice = root.optDouble("wastePrice", 0.0),
                    sheetThresholdsJson = (root.optJSONObject("sheetThresholds") ?: JSONObject()).toString(),
                    smsTemplate = root.optString("smsTemplate", ""),
                    invCompanyName = invSettings.optString("companyName", "رستیک پک"),
                    invPhone = invSettings.optString("phone", ""),
                    invAddress = invSettings.optString("address", ""),
                    invFooter = invSettings.optString("footer", "با تشکر از خرید شما"),
                    invCardNumber = invSettings.optString("cardNumber", ""),
                    invShaba = invSettings.optString("shaba", ""),
                    invAccountHolderName = invSettings.optString("accountHolderName", ""),
                    dispFontScale = dispSettings.optInt("fontScale", 100),
                    dispZoom = dispSettings.optInt("zoom", 85),
                    dispSoundEnabled = dispSettings.optBoolean("soundEnabled", true)
                )
            )

            RestoreResult.Success(
                RestoreCounts(
                    sheets = inventoryArr.length(),
                    customers = customersArr.length(),
                    invoices = invoicesArr.length()
                )
            )
        } catch (e: Exception) {
            RestoreResult.Error("فایل پشتیبان خراب یا نامعتبر است: ${e.message ?: "خطای ناشناس"}")
        }
    }

    /** معادل clearAllData در وب — پاک کردن کامل تمام اطلاعات (بدون بازگشت) */
    suspend fun clearAllData() {
        db.invoiceDao().clearItems()
        db.invoiceDao().clearInvoices()
        db.customerDao().clear()
        db.inventoryDao().clear()
        db.vanDriverDao().clear()
        db.productionQueueDao().clear()
        db.appSettingsDao().upsert(AppSettingsEntity(id = 0))
    }

    private suspend fun <T> firstOf(flow: kotlinx.coroutines.flow.Flow<T>): T =
        flow.first()
}
