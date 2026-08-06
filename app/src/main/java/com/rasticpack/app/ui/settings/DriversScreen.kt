package com.rasticpack.app.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasticpack.app.domain.model.Driver
import com.rasticpack.app.ui.theme.BorderColor
import com.rasticpack.app.ui.theme.Red700
import com.rasticpack.app.ui.theme.SurfaceAlt
import com.rasticpack.app.ui.theme.TextMuted
import com.rasticpack.app.ui.theme.TextSecondary

/**
 * زیرمرحله ۱۰.۳ — معادل drivers-panel در 4.html («🚛 راننده وانت‌ها»).
 * افزودن/ویرایش/حذف راننده، دقیقاً به همان الگوی تب «مشتری‌ها» (مرحله ۵) اما بدون
 * موقعیت مکانی/GPS و بدون مودال فاکتور — چون راننده در وب هیچ‌کدام از این‌ها را ندارد.
 * دکمه‌ی «📱 پیامک» گوشی را به اپ پیامک با شماره‌ی راننده هدایت می‌کند (بدون متن
 * از پیش پر شده در این مرحله — چون متن دقیق «برای بارگیری وانت تشریف بیاورید» مربوط
 * به ارسال از خودِ فاکتور است که در مرحله ۷ ساخته شده؛ این دکمه فقط برای راحتی تماس/پیامک
 * سریع با راننده از همین صفحه‌ی تنظیمات است).
 */
@Composable
fun DriversScreen(onBack: () -> Unit) {
    val viewModel: DriversViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🚛 راننده وانت‌ها", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
        }

        Spacer(Modifier.height(14.dp))

        DriverFormCard(
            title = "🚛 افزودن راننده",
            form = state.addForm,
            error = state.addError,
            onNameChange = { v -> viewModel.updateAddForm { it.copy(name = v) } },
            onPhoneChange = { v -> viewModel.updateAddForm { it.copy(phone = v) } },
            onPlateChange = { v -> viewModel.updateAddForm { it.copy(plate = v) } },
            onSubmit = viewModel::submitAdd,
            submitLabel = "+ افزودن راننده"
        )

        Spacer(Modifier.height(16.dp))

        if (state.drivers.isEmpty()) {
            Text(
                "هیچ راننده‌ای ثبت نشده.",
                fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.drivers, key = { it.id }) { driver ->
                    if (state.editingId == driver.id) {
                        DriverFormCard(
                            title = "✏️ ویرایش راننده",
                            form = state.editForm,
                            error = state.editError,
                            onNameChange = { v -> viewModel.updateEditForm { it.copy(name = v) } },
                            onPhoneChange = { v -> viewModel.updateEditForm { it.copy(phone = v) } },
                            onPlateChange = { v -> viewModel.updateEditForm { it.copy(plate = v) } },
                            onSubmit = viewModel::submitEdit,
                            submitLabel = "💾 ذخیره",
                            onCancel = viewModel::cancelEdit,
                            onDelete = { viewModel.deleteDriver(driver.id) }
                        )
                    } else {
                        DriverViewCard(
                            driver = driver,
                            onEdit = { viewModel.startEdit(driver) },
                            onDelete = { viewModel.deleteDriver(driver.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverFormCard(
    title: String,
    form: DriverFormState,
    error: String?,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPlateChange: (String) -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String,
    onCancel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text("نام راننده") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = form.phone,
                onValueChange = onPhoneChange,
                label = { Text("شماره تماس") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = form.plate,
                onValueChange = onPlateChange,
                label = { Text("شماره پلاک (اختیاری)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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
                    colors = ButtonDefaults.buttonColors(containerColor = Red700),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🗑 حذف راننده") }
            }
        }
    }
}

@Composable
private fun DriverViewCard(
    driver: Driver,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
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
                ) { Text("🚛", fontSize = 16.sp) }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        (driver.phone.ifBlank { "بدون شماره" }) +
                            if (driver.plate.isNotBlank()) " · پلاک ${driver.plate}" else "",
                        fontSize = 12.sp, color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = BorderColor)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (driver.phone.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_SENDTO,
                                android.net.Uri.parse("smsto:${driver.phone}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("📱 پیامک", fontSize = 12.sp) }
                } else {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text("📱 پیامک", fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) { Text("✏️ ویرایش", fontSize = 12.sp) }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Red700),
                    modifier = Modifier.weight(1f)
                ) { Text("🗑 حذف", fontSize = 12.sp) }
            }
        }
    }
}
