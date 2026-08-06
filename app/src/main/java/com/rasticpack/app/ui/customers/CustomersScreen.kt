package com.rasticpack.app.ui.customers

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasticpack.app.domain.model.Customer
import com.rasticpack.app.data.entities.InvoiceWithItems
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary

@Composable
fun CustomersScreen(onBack: () -> Unit) {
    val viewModel: CustomersViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val list = viewModel.filteredCustomers()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("👤 مشتری‌ها", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("نام یا نام شرکت...", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.toggleAddPanel() }) {
                Text(if (state.showAddPanel) "✕" else "+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.showAddPanel) {
            CustomerFormCard(
                title = "👥 افزودن مشتری",
                form = state.addForm,
                error = state.addError,
                onNameChange = { v -> viewModel.updateAddForm { it.copy(name = v) } },
                onCompanyChange = { v -> viewModel.updateAddForm { it.copy(company = v) } },
                onAddressChange = { v -> viewModel.updateAddForm { it.copy(address = v) } },
                onPhoneChange = { v -> viewModel.updateAddForm { it.copy(phone = v) } },
                onLocationPasteChange = viewModel::applyLocationPasteToAdd,
                onLatChange = { v -> viewModel.updateAddForm { it.copy(lat = v) } },
                onLngChange = { v -> viewModel.updateAddForm { it.copy(lng = v) } },
                onCaptureGps = { lat, lng -> viewModel.applyGpsToAdd(lat, lng) },
                onClearLocation = viewModel::clearLocationInAdd,
                onSubmit = viewModel::submitAdd,
                submitLabel = "+ افزودن مشتری"
            )
            Spacer(Modifier.height(14.dp))
        }

        if (list.isEmpty()) {
            Text(
                if (state.customers.isEmpty()) "هیچ مشتری‌ای ثبت نشده." else "مشتری‌ای پیدا نشد.",
                fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { customer ->
                    if (state.editingId == customer.id) {
                        CustomerFormCard(
                            title = "✏️ ویرایش مشتری",
                            form = state.editForm,
                            error = state.editError,
                            onNameChange = { v -> viewModel.updateEditForm { it.copy(name = v) } },
                            onCompanyChange = { v -> viewModel.updateEditForm { it.copy(company = v) } },
                            onAddressChange = { v -> viewModel.updateEditForm { it.copy(address = v) } },
                            onPhoneChange = { v -> viewModel.updateEditForm { it.copy(phone = v) } },
                            onLocationPasteChange = viewModel::applyLocationPasteToEdit,
                            onLatChange = { v -> viewModel.updateEditForm { it.copy(lat = v) } },
                            onLngChange = { v -> viewModel.updateEditForm { it.copy(lng = v) } },
                            onCaptureGps = { lat, lng -> viewModel.applyGpsToEdit(lat, lng) },
                            onClearLocation = viewModel::clearLocationInEdit,
                            onSubmit = viewModel::submitEdit,
                            submitLabel = "💾 ذخیره",
                            onCancel = viewModel::cancelEdit,
                            onDelete = { viewModel.deleteCustomer(customer.id) }
                        )
                    } else {
                        CustomerViewCard(
                            customer = customer,
                            onEdit = { viewModel.startEdit(customer) },
                            onOpenInvoices = { viewModel.openInvoicesModal(customer.id) }
                        )
                    }
                }
            }
        }
    }

    state.invoicesModalCustomerId?.let { custId ->
        val customer = state.customers.find { it.id == custId }
        CustomerInvoicesDialog(
            customerName = customer?.name ?: "",
            invoices = state.invoicesModalList,
            onClose = viewModel::closeInvoicesModal
        )
    }
}

@Composable
private fun CustomerFormCard(
    title: String,
    form: CustomerFormState,
    error: String?,
    onNameChange: (String) -> Unit,
    onCompanyChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onLocationPasteChange: (String) -> Unit,
    onLatChange: (String) -> Unit,
    onLngChange: (String) -> Unit,
    onCaptureGps: (Double, Double) -> Unit,
    onClearLocation: () -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String,
    onCancel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var gpsError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLocationPermissionLauncher(
        onGranted = {
            val loc = getLastKnownLocation(context)
            if (loc != null) onCaptureGps(loc.latitude, loc.longitude)
            else gpsError = "موقعیت در دسترس نیست — GPS گوشی را روشن کنید و دوباره امتحان کنید."
        },
        onDenied = { gpsError = "اجازه دسترسی به موقعیت رد شده — از تنظیمات گوشی اجازه بدهید." }
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = form.name, onValueChange = onNameChange,
                label = { Text("نام مشتری", fontSize = 12.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.company, onValueChange = onCompanyChange,
                label = { Text("نام شرکت", fontSize = 12.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.address, onValueChange = onAddressChange,
                label = { Text("آدرس", fontSize = 12.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.phone, onValueChange = onPhoneChange,
                label = { Text("شماره تماس", fontSize = 12.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("موقعیت مکانی (اختیاری)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = form.locationPasteText,
                onValueChange = onLocationPasteChange,
                placeholder = { Text("لینک/متن اشتراک‌گذاری نشان را اینجا پیست کنید", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "در اپ نشان، روی موقعیت مشتری لمس طولانی کنید ← اشتراک‌گذاری ← کپی لینک، سپس همین‌جا پیست کنید — خودش مختصات را پیدا می‌کند.",
                fontSize = 10.sp, color = TextMuted, lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    gpsError = null
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val loc = getLastKnownLocation(context)
                        if (loc != null) onCaptureGps(loc.latitude, loc.longitude)
                        else gpsError = "موقعیت در دسترس نیست — GPS گوشی را روشن کنید و دوباره امتحان کنید."
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("📍 دریافت موقعیت فعلی (GPS)") }

            gpsError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 11.sp, color = Red700)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (form.hasLocation) "✅ موقعیت ثبت شد (${form.lat}, ${form.lng})" else "موقعیتی ثبت نشده",
                fontSize = 11.sp, color = TextMuted
            )

            if (form.hasLocation) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onClearLocation) { Text("✖ حذف موقعیت", fontSize = 12.sp, color = Red700) }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = form.lat, onValueChange = onLatChange,
                    label = { Text("عرض جغرافیایی (lat)", fontSize = 10.sp) }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = form.lng, onValueChange = onLngChange,
                    label = { Text("طول جغرافیایی (lng)", fontSize = 10.sp) }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Red700)
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onSubmit, modifier = Modifier.weight(1f)) { Text(submitLabel) }
                if (onCancel != null) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("✖ انصراف") }
                }
            }
            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDelete,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Red700),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🗑 حذف مشتری") }
            }
        }
    }
}

@Composable
private fun CustomerViewCard(
    customer: Customer,
    onEdit: () -> Unit,
    onOpenInvoices: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(color = SurfaceAlt, shape = CircleShape)
                        .padding(8.dp)
                ) { Text("👤", fontSize = 16.sp) }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        customer.name + if (customer.company.isNotBlank()) " — ${customer.company}" else "",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Text(
                        (customer.phone.ifBlank { "بدون شماره" }) +
                            if (customer.address.isNotBlank()) " · ${customer.address}" else "",
                        fontSize = 12.sp, color = TextSecondary
                    )
                }
            }

            if (customer.lat != null && customer.lng != null) {
                Spacer(Modifier.height(10.dp))
                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        val url = LocationParsing.neshanMapUrl(customer.lat, customer.lng)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🗺️ باز کردن در نشان / مسیریابی") }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("📍 موقعیت مکانی ثبت نشده", fontSize = 11.sp, color = TextMuted)
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = BorderColor)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onOpenInvoices) { Text("🧾 فاکتور", fontSize = 12.sp) }
                IconButton(onClick = onEdit) { Text("✏️", fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun CustomerInvoicesDialog(
    customerName: String,
    invoices: List<InvoiceWithItems>,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧾 فاکتورهای $customerName", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = onClose) { Text("✕", fontSize = 16.sp) }
                }
                Spacer(Modifier.height(10.dp))
                if (invoices.isEmpty()) {
                    Text("سندی ثبت نشده.", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(invoices, key = { it.invoice.id }) { iw ->
                            InvoiceMiniRow(iw)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceMiniRow(iw: InvoiceWithItems) {
    val statusLabel = when (iw.invoice.status) {
        "draft" -> "پیش‌فاکتور"
        "partial" -> "نیمه تسویه"
        else -> "تسویه‌شده"
    }
    val dateText = remember(iw.invoice.dateIso) { formatDateFa(iw.invoice.dateIso) }
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("فاکتور #${iw.invoice.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(statusLabel, fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text("$dateText · ${iw.invoice.totalSheets} برگ ورق", fontSize = 11.sp, color = TextMuted)
        }
    }
}

private fun formatDateFa(iso: String): String {
    return try {
        iso.substring(0, 10)
    } catch (e: Exception) {
        iso
    }
}

@Composable
private fun rememberLocationPermissionLauncher(
    onGranted: () -> Unit,
    onDenied: () -> Unit
) = androidx.activity.compose.rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { results ->
    val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) onGranted() else onDenied()
}

private fun getLastKnownLocation(context: android.content.Context): Location? {
    return try {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            val loc = lm.getLastKnownLocation(provider) ?: continue
            if (best == null || loc.accuracy < best!!.accuracy) best = loc
        }
        best
    } catch (e: SecurityException) {
        null
    }
}

