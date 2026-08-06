package com.rasticpack.app.ui.invoices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import com.rasticpack.app.data.entities.VanDriverEntity
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.Green
import com.rasticpack.app.ui.theme.Red600
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private val StatusColors = mapOf(
    "draft" to Color(0xFFDC2626),
    "partial" to Color(0xFFEA580C),
    "paid" to Color(0xFF16A34A),
    "debtor" to Color(0xFF7C3AED)
)

@Composable
fun InvoicesScreen(onBack: () -> Unit, onNavigateToProduction: (Int?) -> Unit = {}) {
    val viewModel: InvoicesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val list = viewModel.visibleList()
    val counts = viewModel.statusCounts()
    var pdfBusyId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🧾 فاکتورها", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }

        Spacer(Modifier.height(12.dp))

        // سوییچر ماه
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = SurfaceAlt, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.resetToToday() }) { Text("↻", fontSize = 16.sp) }
            IconButton(onClick = { viewModel.shiftMonth(-1) }) { Text("‹", fontWeight = FontWeight.Bold) }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(viewModel.monthLabel(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    if (list.isNotEmpty() || viewModel.baseFilteredList().isNotEmpty())
                        "${viewModel.baseFilteredList().size} فاکتور" else "فاکتوری ثبت نشده",
                    fontSize = 10.sp, color = TextMuted
                )
            }
            IconButton(onClick = { viewModel.shiftMonth(1) }) { Text("›", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(10.dp))

        // فیلتر وضعیت
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusFilterChip("پیش‌فاکتور", counts["draft"] ?: 0, "draft", state.statusFilter, viewModel::setStatusFilter, modifier = Modifier.weight(1f))
            StatusFilterChip("نیمه تسویه", counts["partial"] ?: 0, "partial", state.statusFilter, viewModel::setStatusFilter, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusFilterChip("تسویه‌شده", counts["paid"] ?: 0, "paid", state.statusFilter, viewModel::setStatusFilter, modifier = Modifier.weight(1f))
            StatusFilterChip("بدهکار", viewModel.debtorCount(), "debtor", state.statusFilter, viewModel::setStatusFilter, modifier = Modifier.weight(1f))
        }

        if (state.statusFilter == "debtor") {
            Spacer(Modifier.height(10.dp))
            DebtorSummaryCard(
                totalRemaining = viewModel.debtorTotalRemaining(),
                customerCount = viewModel.debtorCustomerCount()
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { viewModel.toggleSearch() }) { Text("🔍", fontSize = 15.sp) }
        }
        if (state.showSearch) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("جستجوی مشتری...", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }

        if (list.isEmpty()) {
            val filterLabel = when (state.statusFilter) {
                "debtor" -> "بدهکار"
                null -> null
                else -> INVOICE_STATUS_INFO[state.statusFilter]?.label
            }
            Text(
                if (filterLabel != null) "فاکتوری با وضعیت «$filterLabel» پیدا نشد." else "فاکتوری در این ماه ثبت نشده.",
                fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.invoice.id }) { iw ->
                    if (state.editingInvoiceId == iw.invoice.id) {
                        InvoiceEditCard(
                            iw = iw,
                            customerName = state.editCustomerName,
                            quantities = state.editQuantities,
                            error = state.editError,
                            onCustomerNameChange = viewModel::updateEditCustomerName,
                            onQtyChange = viewModel::updateEditQty,
                            onSave = viewModel::saveEdit,
                            onCancel = viewModel::cancelEdit,
                            onDelete = { viewModel.deleteInvoice(iw.invoice.id) }
                        )
                    } else {
                        InvoiceViewCard(
                            iw = iw,
                            statusLabel = statusLabelFor(viewModel, iw),
                            isDebtor = viewModel.isDebtor(iw),
                            total = viewModel.invoiceTotal(iw),
                            remaining = viewModel.invoiceRemaining(iw),
                            payPanelOpen = state.payPanelInvoiceId == iw.invoice.id,
                            payAmountText = state.payAmountText,
                            onToggleSent = { viewModel.toggleSent(iw.invoice.id, iw.invoice.sent) },
                            onToggleSettled = { viewModel.toggleSettled(iw.invoice.id, viewModel.statusOf(iw) == "paid") },
                            onOpenPay = {
                                val remaining = viewModel.invoiceRemaining(iw)
                                val prefill = if ((iw.invoice.paidAmount ?: 0.0) > 0) fmtNumInv(iw.invoice.paidAmount!!) else ""
                                viewModel.openPayPanel(iw.invoice.id, prefill)
                            },
                            onClosePay = viewModel::closePayPanel,
                            onPayAmountChange = viewModel::updatePayAmountText,
                            onSubmitPay = viewModel::submitPayment,
                            onFillRemaining = { viewModel.updatePayAmountText(fmtNumInv(viewModel.invoiceRemaining(iw))) },
                            onEdit = { viewModel.startEdit(iw) },
                            onSetBundle = { itemId, size -> viewModel.setBundleSize(iw.invoice.id, itemId, size) },
                            customerPhone = viewModel.customerFor(iw)?.phone.orEmpty(),
                            smsOptionsOpen = state.smsOptionsInvoiceId == iw.invoice.id,
                            onToggleSmsOptions = { viewModel.toggleSmsOptions(iw.invoice.id) },
                            onSendCardSms = {
                                val phone = SmsHelper.sanitizePhone(viewModel.customerFor(iw)?.phone)
                                if (phone.isNotBlank()) SmsHelper.startSms(context, phone, viewModel.cardShabaSmsBody())
                            },
                            onSendInvoiceSms = {
                                val phone = SmsHelper.sanitizePhone(viewModel.customerFor(iw)?.phone)
                                if (phone.isNotBlank()) SmsHelper.startSms(context, phone, viewModel.invoiceItemsSmsBody(iw))
                            },
                            vanOptionsOpen = state.vanOptionsInvoiceId == iw.invoice.id,
                            onToggleVanOptions = { viewModel.toggleVanOptions(iw.invoice.id) },
                            vanDrivers = state.vanDrivers,
                            vanDriverListOpen = state.vanDriverListInvoiceId == iw.invoice.id,
                            onToggleVanDriverList = { viewModel.toggleVanDriverList(iw.invoice.id) },
                            vanCustDriverListOpen = state.vanCustDriverListInvoiceId == iw.invoice.id,
                            onToggleVanCustDriverList = { viewModel.toggleVanCustDriverList(iw.invoice.id) },
                            onSendVanSmsToDriver = { driver ->
                                val phone = SmsHelper.sanitizePhone(driver.phone)
                                if (phone.isNotBlank()) SmsHelper.startSms(context, phone, viewModel.vanSmsBody(iw))
                            },
                            onSendVanInfoToCustomer = { driver ->
                                val phone = SmsHelper.sanitizePhone(viewModel.customerFor(iw)?.phone)
                                if (phone.isNotBlank()) SmsHelper.startSms(context, phone, viewModel.vanInfoForCustomerSmsBody(iw, driver))
                            },
                            onSendToProduction = {
                                coroutineScope.launch {
                                    val firstId = viewModel.sendToProduction(iw.invoice.id)
                                    onNavigateToProduction(firstId)
                                }
                            },
                            onSharePdf = {
                                pdfBusyId = iw.invoice.id
                                val settings = state.invoiceSettings
                                coroutineScope.launch {
                                    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        InvoicePdfBuilder.buildPdfFile(
                                            context, iw.invoice, iw.items, viewModel.customerFor(iw),
                                            InvoicePdfBuilder.InvoiceSettings(
                                                companyName = settings.companyName, phone = settings.phone,
                                                address = settings.address, footer = settings.footer
                                            )
                                        )
                                    }
                                    val uri = InvoicePdfBuilder.shareUriFor(context, file)
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, null))
                                    pdfBusyId = null
                                }
                            },
                            pdfBusy = pdfBusyId == iw.invoice.id
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabelFor(vm: InvoicesViewModel, iw: InvoiceWithItems): String {
    if (vm.isDebtor(iw)) return "بدهکار"
    return INVOICE_STATUS_INFO[vm.statusOf(iw)]?.label ?: "تسویه‌شده"
}

@Composable
private fun StatusFilterChip(
    label: String,
    count: Int,
    key: String,
    activeFilter: String?,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = StatusColors[key] ?: MaterialTheme.colorScheme.primary
    val active = activeFilter == key
    Box(
        modifier = modifier
            .background(color = if (active) color else Color.White, shape = RoundedCornerShape(10.dp))
            .clickable { onClick(key) }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 0) "$label (${JalaliDate.toFaDigits(count)})" else label,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = if (active) Color.White else color
        )
    }
}

@Composable
private fun DebtorSummaryCard(totalRemaining: Double, customerCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7C3AED)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${fmtNumInv(totalRemaining)} ریال", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .background(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("${JalaliDate.toFaDigits(customerCount)} مشتری", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InvoiceViewCard(
    iw: InvoiceWithItems,
    statusLabel: String,
    isDebtor: Boolean,
    total: Double,
    remaining: Double,
    payPanelOpen: Boolean,
    payAmountText: String,
    onToggleSent: () -> Unit,
    onToggleSettled: () -> Unit,
    onOpenPay: () -> Unit,
    onClosePay: () -> Unit,
    onPayAmountChange: (String) -> Unit,
    onSubmitPay: () -> Unit,
    onFillRemaining: () -> Unit,
    onEdit: () -> Unit,
    onSetBundle: (Int, Int?) -> Unit,
    // ══ مرحله ۷ — پیامک/وانت/PDF ══
    customerPhone: String,
    smsOptionsOpen: Boolean,
    onToggleSmsOptions: () -> Unit,
    onSendCardSms: () -> Unit,
    onSendInvoiceSms: () -> Unit,
    vanOptionsOpen: Boolean,
    onToggleVanOptions: () -> Unit,
    vanDrivers: List<VanDriverEntity>,
    vanDriverListOpen: Boolean,
    onToggleVanDriverList: () -> Unit,
    vanCustDriverListOpen: Boolean,
    onToggleVanCustDriverList: () -> Unit,
    onSendVanSmsToDriver: (VanDriverEntity) -> Unit,
    onSendVanInfoToCustomer: (VanDriverEntity) -> Unit,
    onSharePdf: () -> Unit,
    pdfBusy: Boolean,
    // ══ مرحله ۸ — ارسال به صف تولید (معادل دکمه‌ی 🏭 در وب) ══
    onSendToProduction: () -> Unit
) {
    val borderColor = when {
        isDebtor -> StatusColors["debtor"]!!
        else -> StatusColors[iw.invoice.status.ifBlank { "paid" }] ?: StatusColors["paid"]!!
    }
    val isPaid = iw.invoice.status == "paid"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.background(color = borderColor, shape = CircleShape).padding(8.dp)
                ) { Text("🧾", fontSize = 14.sp) }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(iw.invoice.customerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        JalaliDate.formatDateTimeShort(iw.invoice.dateIso) + if (iw.invoice.editedAtIso != null) " · ویرایش‌شده" else "",
                        fontSize = 11.sp, color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${JalaliDate.toFaDigits(iw.invoice.totalSheets)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("کل ورق", fontSize = 9.sp, color = TextMuted)
                }
                Spacer(Modifier.width(6.dp))
                // معادل دکمه‌ی 🏭 در وب — ارسال به صف تولید (با نشان ✔ کوچک اگر قبلاً ارسال شده)
                Box(
                    modifier = Modifier
                        .clickable { onSendToProduction() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🏭", fontSize = 20.sp)
                        if (iw.invoice.sentToProduction) {
                            Text("✔", fontSize = 9.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier.background(color = borderColor.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = borderColor)
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = BorderColor)
            Spacer(Modifier.height(10.dp))

            iw.items.filter { it.qty > 0 }.forEachIndexed { idx, item ->
                InvoiceItemRow(index = idx + 1, item = item, onSetBundle = { size -> onSetBundle(item.id, size) })
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("✏️ ویرایش", fontSize = 11.sp) }
                OutlinedButton(onClick = onOpenPay, modifier = Modifier.weight(1f)) { Text("💰 مبلغ", fontSize = 11.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onToggleSent,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (iw.invoice.sent) Green else SurfaceAlt,
                        contentColor = if (iw.invoice.sent) Color.White else TextSecondary
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("ارسال شد", fontSize = 11.sp) }
                Button(
                    onClick = onToggleSettled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaid) Green else SurfaceAlt,
                        contentColor = if (isPaid) Color.White else TextSecondary
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("تسویه شد", fontSize = 11.sp) }
            }

            // ══ مرحله ۷ — پیامک / وانت / اشتراک‌گذاری PDF ══
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleSmsOptions) { Text("💬", fontSize = 20.sp) }
                IconButton(onClick = onToggleVanOptions) { Text("🛻", fontSize = 20.sp) }
                IconButton(onClick = onSharePdf, enabled = !pdfBusy) {
                    Text(if (pdfBusy) "⏳" else "🔗", fontSize = 20.sp)
                }
            }

            if (smsOptionsOpen) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (customerPhone.isNotBlank()) {
                        OutlinedButton(onClick = onSendCardSms, modifier = Modifier.weight(1f)) {
                            Text("💳 شماره کارت", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = onSendInvoiceSms, modifier = Modifier.weight(1f)) {
                            Text("🧾 فاکتور", fontSize = 11.sp)
                        }
                    } else {
                        Text(
                            "برای این مشتری شماره تماسی ثبت نشده — از تب «مشتری‌ها» شماره را اضافه کنید.",
                            fontSize = 10.sp, color = TextMuted, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (vanOptionsOpen) {
                Spacer(Modifier.height(6.dp))
                if (customerPhone.isBlank()) {
                    Text(
                        "برای این مشتری شماره تماسی ثبت نشده — از تب «مشتری‌ها» شماره را اضافه کنید.",
                        fontSize = 10.sp, color = TextMuted
                    )
                } else if (vanDrivers.isEmpty()) {
                    Text(
                        "راننده‌ای ثبت نشده — از تب «تنظیمات ▸ راننده وانت‌ها» اضافه کنید.",
                        fontSize = 10.sp, color = TextMuted
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = onToggleVanCustDriverList, modifier = Modifier.weight(1f)) {
                            Text("👤 اطلاع به مشتری", fontSize = 11.sp)
                        }
                        if (vanDrivers.size == 1) {
                            OutlinedButton(
                                onClick = { onSendVanSmsToDriver(vanDrivers[0]) },
                                modifier = Modifier.weight(1f)
                            ) { Text("🛻 ${vanDrivers[0].name}", fontSize = 11.sp) }
                        } else {
                            OutlinedButton(onClick = onToggleVanDriverList, modifier = Modifier.weight(1f)) {
                                Text("🛻 انتخاب راننده", fontSize = 11.sp)
                            }
                        }
                    }
                    if (vanDriverListOpen && vanDrivers.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        vanDrivers.forEach { d ->
                            OutlinedButton(
                                onClick = { onSendVanSmsToDriver(d) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) { Text("🚛 ${d.name}", fontSize = 11.sp) }
                        }
                    }
                    if (vanCustDriverListOpen) {
                        Spacer(Modifier.height(6.dp))
                        vanDrivers.forEach { d ->
                            OutlinedButton(
                                onClick = { onSendVanInfoToCustomer(d) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) { Text("👤 ${d.name}", fontSize = 11.sp) }
                        }
                    }
                }
            }

            if (payPanelOpen) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        StatRow("مبلغی که مشتری باید پرداخت کند", "${fmtNumInv(total)} ریال", Red700)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onFillRemaining() },
                        ) { StatRow("مانده (لمس برای پرکردن)", "${fmtNumInv(remaining)} ریال", Color(0xFF92400E)) }
                        Spacer(Modifier.height(6.dp))
                        StatRow("مبلغ واریزی مشتری تاکنون", "${fmtNumInv(iw.invoice.paidAmount ?: 0.0)} ریال", Color(0xFF166534))
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = payAmountText,
                            onValueChange = onPayAmountChange,
                            label = { Text("ثبت/ویرایش مبلغ واریزی جدید (ریال)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onSubmitPay, modifier = Modifier.weight(1f)) { Text("💾 ثبت واریزی", fontSize = 12.sp) }
                            OutlinedButton(onClick = onClosePay, modifier = Modifier.weight(1f)) { Text("بستن", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = TextMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun InvoiceItemRow(index: Int, item: InvoiceItemEntity, onSetBundle: (Int?) -> Unit) {
    val bundleSummary = bundleSummaryText(item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = SurfaceAlt, shape = RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            "$index. ${item.cartonName.ifBlank { "کارتن" }} ${fmtDimShort(item.cartonLength)}×${fmtDimShort(item.cartonWidth)}×${fmtDimShort(item.cartonHeight)}",
            fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        Text(
            "${fmtDimShort(item.sh)}×${fmtDimShort(item.sw)} · ${layerLabelInv(item.layer)} · ${item.qty} برگ · ${item.cartonQty} عدد",
            fontSize = 11.sp, color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BUNDLE_SIZE_OPTIONS.forEach { size ->
                val selected = item.bundleSize == size
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSetBundle(if (selected) null else size) }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$size", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else TextSecondary
                    )
                }
            }
        }
        if (bundleSummary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(bundleSummary, fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InvoiceEditCard(
    iw: InvoiceWithItems,
    customerName: String,
    quantities: Map<Int, String>,
    error: String?,
    onCustomerNameChange: (String) -> Unit,
    onQtyChange: (Int, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Red600),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("✏️ ویرایش فاکتور", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = customerName, onValueChange = onCustomerNameChange,
                label = { Text("نام مشتری", fontSize = 11.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            iw.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${item.cartonName.ifBlank { fmtDimShort(item.sh) + "×" + fmtDimShort(item.sw) }} (${layerLabelInv(item.layer)})",
                        fontSize = 11.sp, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quantities[item.id] ?: item.qty.toString(),
                        onValueChange = { onQtyChange(item.id, it) },
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Red700)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("💾 ذخیره") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("✖ انصراف") }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Red700),
                modifier = Modifier.fillMaxWidth()
            ) { Text("🗑 حذف فاکتور") }
        }
    }
}
