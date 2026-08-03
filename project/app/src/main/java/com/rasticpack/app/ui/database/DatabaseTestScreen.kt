package com.rasticpack.app.ui.database

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import kotlinx.coroutines.launch

/** اندازه‌ی هر صفحه از لیست فاکتورها — دقیقاً معادل PAGE_SIZE=50 در 4.html. */
private const val PAGE_SIZE = 50

/**
 * صفحه‌ی تست مرحله‌ی ۲ — دیتابیس (Room).
 * دو کار را تست می‌کند:
 *  ۱) تولید ۱۰٬۰۰۰ فاکتور نمونه (هرکدام با یک آیتم) در دیتابیس واقعی روی گوشی.
 *  ۲) نمایش صفحه‌بندی‌شده‌ی (۵۰ تایی) این فاکتورها در یک LazyColumn — معادل دقیق
 *     همان الگوی صفحه‌بندی که در renderInvoicesList نسخه‌ی وب برای جلوگیری از کندی
 *     با هزاران رکورد استفاده شده بود.
 */
@Composable
fun DatabaseTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var totalCount by remember { mutableIntStateOf(0) }
    var isSeeding by remember { mutableStateOf(false) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var seedMs by remember { mutableStateOf<Long?>(null) }

    val invoices = remember { mutableStateListOf<InvoiceWithItems>() }
    var loadedOffset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }

    fun refreshCount() {
        scope.launch { totalCount = db.invoiceDao().count() }
    }

    fun loadNextPage() {
        if (isLoadingPage || !hasMore) return
        isLoadingPage = true
        scope.launch {
            val page = db.invoiceDao().getPage(PAGE_SIZE, loadedOffset)
            invoices.addAll(page)
            loadedOffset += page.size
            hasMore = page.size == PAGE_SIZE
            isLoadingPage = false
        }
    }

    fun resetAndReload() {
        invoices.clear()
        loadedOffset = 0
        hasMore = true
        loadNextPage()
    }

    LaunchedEffect(Unit) {
        refreshCount()
        resetAndReload()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🗄️ تست دیتابیس (مرحله ۲)",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }

        Text(
            text = "هدف این صفحه اطمینان از این است که با هزاران فاکتور واقعی در دیتابیس، اسکرول لیست " +
                "همچنان نرم بماند — دقیقاً همان دغدغه‌ی کارایی (Performance) که در نسخه‌ی وب با " +
                "صفحه‌بندی ۵۰تایی (PAGE_SIZE) حل شده بود.",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تعداد فاکتورهای فعلی در دیتابیس: $totalCount", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                seedMs?.let {
                    Text("آخرین تولید ۱۰٬۰۰۰ فاکتور نمونه در $it میلی‌ثانیه انجام شد.", fontSize = 12.sp)
                }
                statusMsg?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !isSeeding,
                onClick = {
                    isSeeding = true
                    statusMsg = "در حال تولید ۱۰٬۰۰۰ فاکتور نمونه..."
                    scope.launch {
                        val elapsed = seedSampleInvoices(db, count = 10_000)
                        seedMs = elapsed
                        isSeeding = false
                        statusMsg = "تولید ۱۰٬۰۰۰ فاکتور نمونه تمام شد ✅"
                        refreshCount()
                        resetAndReload()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSeeding) "در حال تولید..." else "🚀 تولید ۱۰٬۰۰۰ فاکتور نمونه")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        db.invoiceDao().clearInvoices()
                        refreshCount()
                        resetAndReload()
                        statusMsg = "همه‌ی فاکتورهای تستی پاک شد."
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("🗑 پاک کردن همه فاکتورها") }
        }

        Text(
            text = "لیست فاکتورها (صفحه‌بندی‌شده — هر بار ۵۰ مورد):",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(invoices, key = { it.invoice.id }) { iw ->
                InvoiceRow(iw)
            }
            item {
                Spacer(Modifier.height(8.dp))
                if (isLoadingPage) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                } else if (hasMore) {
                    OutlinedButton(
                        onClick = { loadNextPage() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("نمایش ۵۰ فاکتور بعدی") }
                } else if (invoices.isNotEmpty()) {
                    Text(
                        "پایان لیست (${invoices.size} از $totalCount مورد نمایش داده شد).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun InvoiceRow(iw: InvoiceWithItems) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("#${iw.invoice.id} — ${iw.invoice.customerName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(iw.invoice.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "${iw.items.size} قلم · مجموع ${iw.invoice.totalSheets} برگ",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * تولید ۱۰٬۰۰۰ فاکتور نمونه (هرکدام یک آیتم) — برای تست کارایی دیتابیس روی گوشی واقعی.
 * ابتدا یک مشتری و یک ورق نمونه (اگر خالی باشند) ساخته می‌شوند تا فاکتورها منبع معتبر
 * داشته باشند. کل عملیات داخل یک تراکنش (transaction) انجام می‌شود تا سریع باشد
 * (بدون این‌که هزاران commit جدا روی دیسک بنویسد).
 *
 * خروجی: مدت زمان اجرا به میلی‌ثانیه (برای نمایش روی صفحه).
 */
private suspend fun seedSampleInvoices(db: AppDatabase, count: Int): Long {
    val start = System.currentTimeMillis()
    // فرمت ISO 8601 ساده (بدون وابستگی به java.time که نیاز به API 26+ دارد — minSdk این پروژه ۲۴ است)
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val nowMs = System.currentTimeMillis()
    db.withTransaction {
        val customerId = db.customerDao().let { dao ->
            dao.getFirstOrNull()?.id ?: dao.insert(
                CustomerEntity(name = "مشتری تستی", company = "شرکت تستی", phone = "09120000000")
            ).toInt()
        }
        val sheet = db.inventoryDao().getAll().firstOrNull()
            ?: run {
                val newId = db.inventoryDao().insert(
                    InventorySheetEntity(sw = 240.0, sh = 280.0, layer = "3", qty = 100_000)
                ).toInt()
                InventorySheetEntity(id = newId, sw = 240.0, sh = 280.0, layer = "3", qty = 100_000)
            }

        for (i in 1..count) {
            // هر فاکتور یک دقیقه عقب‌تر از قبلی — تا آخرین فاکتور نزدیک «الان» باشد (مثل داده‌ی واقعی)
            val dateMs = nowMs - (count - i).toLong() * 60_000L
            val invoiceId = db.invoiceDao().insertInvoice(
                InvoiceEntity(
                    customerId = customerId,
                    customerName = "مشتری تستی",
                    dateIso = isoFormat.format(Date(dateMs)),
                    status = if (i % 3 == 0) "paid" else if (i % 3 == 1) "partial" else "draft",
                    totalSheets = 10
                )
            ).toInt()
            db.invoiceDao().insertItems(
                listOf(
                    InvoiceItemEntity(
                        invoiceId = invoiceId,
                        sheetId = sheet.id,
                        sw = sheet.sw,
                        sh = sheet.sh,
                        layer = sheet.layer,
                        qty = 10,
                        cartonName = "کارتن نمونه",
                        cartonLength = 60.0,
                        cartonWidth = 40.0,
                        cartonHeight = 40.0,
                        glue = 4.0,
                        cartonQty = 100,
                        unitPrice = 250000.0,
                        lineTotal = 25_000_000.0,
                        itemProfit = 5_000_000.0
                    )
                )
            )
        }
    }
    return System.currentTimeMillis() - start
}

