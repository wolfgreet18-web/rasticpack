package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.domain.model.Invoice
import com.rasticpack.app.domain.model.InvoiceItem
import com.rasticpack.app.domain.model.InvoiceWithItemsModel
import com.rasticpack.app.domain.model.InventorySheet
import com.rasticpack.app.domain.repository.InventorySheetRepository
import com.rasticpack.app.domain.repository.InvoiceRepository
import com.rasticpack.app.engine.CalculatorEngine
import com.rasticpack.app.engine.Grain
import com.rasticpack.app.engine.SheetItem
import com.rasticpack.app.ui.calc.CartonCalcResult
import com.rasticpack.app.ui.calc.CartonRowInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══ مرحله ۳.۳ بخش دوم (نقشه معماری v2.9) ══
 * تست واحد برای SubmitInvoiceUseCase (معادل submitCalc2Invoice در وب) و
 * EditInvoiceUseCase (معادل saveInvoiceEdit در وب، شامل BUG FIX جمع‌زدن نیاز
 * چند ردیف از یک ورق مشترک قبل از چک موجودی).
 */
class SubmitAndEditInvoiceUseCaseTest {

    private class FakeInvoiceRepository(initial: List<InvoiceWithItemsModel> = emptyList()) : InvoiceRepository {
        val state = MutableStateFlow(initial)

        override fun observeAllWithItems(): Flow<List<InvoiceWithItemsModel>> = state
        override fun observeByCustomer(customerId: Int): Flow<List<InvoiceWithItemsModel>> =
            MutableStateFlow(state.value.filter { it.invoice.customerId == customerId })

        override suspend fun getById(id: Int): InvoiceWithItemsModel? =
            state.value.find { it.invoice.id == id }

        override suspend fun deleteById(id: Int) {
            state.value = state.value.filterNot { it.invoice.id == id }
        }

        override suspend fun updateInvoice(invoice: Invoice) {
            state.value = state.value.map { if (it.invoice.id == invoice.id) it.copy(invoice = invoice) else it }
        }

        override suspend fun updateItem(item: InvoiceItem) {
            state.value = state.value.map { iw ->
                if (iw.invoice.id == item.invoiceId) {
                    iw.copy(items = iw.items.map { if (it.id == item.id) item else it })
                } else iw
            }
        }

        override suspend fun deleteItem(itemId: Int) {
            state.value = state.value.map { iw -> iw.copy(items = iw.items.filterNot { it.id == itemId }) }
        }

        override suspend fun insertWithItems(invoice: Invoice, items: List<InvoiceItem>): Int {
            val newId = (state.value.maxOfOrNull { it.invoice.id } ?: 0) + 1
            val newInvoice = invoice.copy(id = newId)
            val newItems = items.mapIndexed { idx, it -> it.copy(id = idx + 1, invoiceId = newId) }
            state.value = state.value + InvoiceWithItemsModel(newInvoice, newItems)
            return newId
        }
    }

    private class FakeInventorySheetRepository(initial: List<InventorySheet> = emptyList()) : InventorySheetRepository {
        val state = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<InventorySheet>> = state
        override suspend fun getAll(): List<InventorySheet> = state.value
        override suspend fun getById(id: Int): InventorySheet? = state.value.find { it.id == id }
        override suspend fun getUniqueDims(): List<Pair<Double, Double>> =
            state.value.map { it.sh to it.sw }.distinct()

        override suspend fun insert(sheet: InventorySheet): InventorySheet {
            state.value = state.value + sheet
            return sheet
        }

        override suspend fun update(sheet: InventorySheet) {
            state.value = state.value.map { if (it.id == sheet.id) sheet else it }
        }

        override suspend fun delete(id: Int) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun decreaseQty(sheetId: Int, amount: Int) {
            state.value = state.value.map { if (it.id == sheetId) it.copy(qty = it.qty - amount) else it }
        }

        override suspend fun increaseQty(id: Int, amount: Int) {
            state.value = state.value.map { if (it.id == id) it.copy(qty = it.qty + amount) else it }
        }

        override suspend fun findOrCreateSheet(sh: Double, sw: Double, layer: String, flute: String, paperType: String): InventorySheet {
            val existing = state.value.find { it.sh == sh && it.sw == sw && it.layer == layer && it.flute == flute && it.paperType == paperType }
            if (existing != null) return existing
            val created = InventorySheet(id = (state.value.maxOfOrNull { it.id } ?: 0) + 1, sh = sh, sw = sw, layer = layer, qty = 0, flute = flute, paperType = paperType)
            state.value = state.value + created
            return created
        }
    }

    /** یک CartonCalcResult ساده که دقیقاً یک ورق را با qty لازم مصرف می‌کند — با استفاده‌ی واقعی از CalculatorEngine.matchSheets */
    private fun buildResult(sheet: InventorySheet, cartonQty: Int): CartonCalcResult {
        val row = CartonRowInput(localId = 1, layer = sheet.layer, flute = sheet.flute, paperType = sheet.paperType)
        val length = 30.0; val width = 20.0; val height = 20.0; val glue = 4.0
        val (bw, bh) = CalculatorEngine.expandedCartonDims(length, width, height, glue)
        val sheetItem = SheetItem(sheet.id, sheet.sw, sheet.sh, sheet.layer, sheet.qty, sheet.flute, sheet.paperType)
        val matches = CalculatorEngine.matchSheets(listOf(sheetItem), bw, bh, cartonQty, Grain.HORIZONTAL, sheet.layer, sheet.flute, sheet.paperType)
        return CartonCalcResult(
            row = row, name = "کارتن", length = length, width = width, height = height, qty = cartonQty, glue = glue,
            totalLength = bh, totalWidth = bw, areaM2 = 0.0, basePrice = 0.0, wasteCostPerUnit = 0.0, wasteCostTotal = 0.0,
            basePriceWithWaste = 1000.0, finalPrice = 1200.0, profitPerUnit = 200.0, totalProfit = 200.0 * cartonQty,
            totalPrice = 1200.0 * cartonQty, sheets = matches
        )
    }

    // ══ SubmitInvoiceUseCase ══

    @Test
    fun `submitting with no valid sheets fails`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository()
        val inventoryRepo = FakeInventorySheetRepository()
        val useCase = SubmitInvoiceUseCase(invoiceRepo, inventoryRepo)

        // ورقی که اصلاً جا نمی‌دهد (خیلی کوچک) → sheets خالی می‌ماند
        val tinySheet = InventorySheet(id = 1, sh = 5.0, sw = 5.0, layer = "3", qty = 10, flute = "C", paperType = "KT")
        inventoryRepo.state.value = listOf(tinySheet)
        val result = buildResult(tinySheet, cartonQty = 5)

        val outcome = useCase(customerId = 1, customerName = "علی", results = listOf(result))
        assertTrue(outcome is com.rasticpack.app.core.result.RasticResult.Failure)
        assertEquals(RasticError.NoValidSheetsForInvoice, (outcome as com.rasticpack.app.core.result.RasticResult.Failure).error)
    }

    @Test
    fun `submitting with insufficient stock fails and does not mutate inventory`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository()
        val inventoryRepo = FakeInventorySheetRepository()
        val useCase = SubmitInvoiceUseCase(invoiceRepo, inventoryRepo)

        // ورق بزرگ‌کافی برای چیدمان اما موجودی خیلی کم (qty=1) در برابر نیاز بالا
        val sheet = InventorySheet(id = 1, sh = 100.0, sw = 100.0, layer = "3", qty = 1, flute = "C", paperType = "KT")
        inventoryRepo.state.value = listOf(sheet)
        val result = buildResult(sheet, cartonQty = 500) // نیاز به ورق بیشتر از موجودی

        val outcome = useCase(customerId = 1, customerName = "علی", results = listOf(result))
        assertTrue(outcome is com.rasticpack.app.core.result.RasticResult.Failure)
        assertTrue((outcome as com.rasticpack.app.core.result.RasticResult.Failure).error is RasticError.InsufficientStock)
        // موجودی نباید تغییر کرده باشد چون کل عملیات رد شد
        assertEquals(1, inventoryRepo.state.value.first().qty)
    }

    @Test
    fun `successful submit decreases inventory and creates invoice`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository()
        val inventoryRepo = FakeInventorySheetRepository()
        val useCase = SubmitInvoiceUseCase(invoiceRepo, inventoryRepo)

        val sheet = InventorySheet(id = 1, sh = 100.0, sw = 100.0, layer = "3", qty = 50, flute = "C", paperType = "KT")
        inventoryRepo.state.value = listOf(sheet)
        val result = buildResult(sheet, cartonQty = 10)

        val outcome = useCase(customerId = 1, customerName = "علی", results = listOf(result))
        assertTrue(outcome is com.rasticpack.app.core.result.RasticResult.Success)
        val invoiceId = (outcome as com.rasticpack.app.core.result.RasticResult.Success).data
        val stored = invoiceRepo.getById(invoiceId)
        assertEquals(1, stored?.items?.size)
        assertTrue(inventoryRepo.state.value.first().qty < 50)
    }

    // ══ EditInvoiceUseCase ══

    @Test
    fun `editing invoice not found fails`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository()
        val inventoryRepo = FakeInventorySheetRepository()
        val useCase = EditInvoiceUseCase(invoiceRepo, inventoryRepo)

        val outcome = useCase(invoiceId = 99, customerId = 1, customerName = "علی", newQuantities = emptyMap())
        assertTrue(outcome is com.rasticpack.app.core.result.RasticResult.Failure)
        assertEquals(RasticError.InvoiceNotFound, (outcome as com.rasticpack.app.core.result.RasticResult.Failure).error)
    }

    @Test
    fun `editing sums need across two items from same sheet before checking stock`() = runBlocking {
        // BUG FIX در وب: دو ردیف از یک ورق مشترک باید قبل از چک، جمع زده شوند —
        // نه این‌که هرکدام به‌تنهایی (اشتباهاً) در برابر کل موجودی چک شوند.
        val sheet = InventorySheet(id = 1, sh = 100.0, sw = 100.0, layer = "3", qty = 10, flute = "C", paperType = "KT")
        val inventoryRepo = FakeInventorySheetRepository(listOf(sheet))
        val invoice = Invoice(id = 1, customerId = 1, customerName = "علی", dateIso = "2024-01-01T00:00:00Z", status = "draft", totalSheets = 8)
        val item1 = InvoiceItem(id = 1, invoiceId = 1, sheetId = 1, sw = 100.0, sh = 100.0, layer = "3", qty = 4)
        val item2 = InvoiceItem(id = 2, invoiceId = 1, sheetId = 1, sw = 100.0, sh = 100.0, layer = "3", qty = 4)
        // موجودی فعلی=10 ؛ این فاکتور قبلاً ۸ برگ مصرف کرده بود، پس موجودی واقعی انبار قبل از فاکتور ۱۸ بود.
        val invoiceRepo = FakeInvoiceRepository(listOf(InvoiceWithItemsModel(invoice, listOf(item1, item2))))
        val useCase = EditInvoiceUseCase(invoiceRepo, inventoryRepo)

        // ویرایش: هر دو ردیف را به ۷ برگ افزایش بده → نیاز جدید کل = ۱۴.
        // بعد از برگشت ۸ برگ قبلی، موجودی موقت = ۱۸ که برای ۱۴ کافی است.
        val outcome = useCase(invoiceId = 1, customerId = 1, customerName = "علی", newQuantities = mapOf(1 to 7, 2 to 7))
        assertTrue(outcome is com.rasticpack.app.core.result.RasticResult.Success)
        assertEquals(18 - 14, inventoryRepo.state.value.first().qty)
        val stored = invoiceRepo.getById(1)!!
        assertEquals(setOf(7, 7), stored.items.map { it.qty }.toSet())
    }

    @Test
    fun `editing an item down to zero removes it`() = runBlocking {
        val sheet = InventorySheet(id = 1, sh = 100.0, sw = 100.0, layer = "3", qty = 10, flute = "C", paperType = "KT")
        val inventoryRepo = FakeInventorySheetRepository(listOf(sheet))
        val invoice = Invoice(id = 1, customerId = 1, customerName = "علی", dateIso = "2024-01-01T00:00:00Z", status = "draft", totalSheets = 4)
        val item = InvoiceItem(id = 1, invoiceId = 1, sheetId = 1, sw = 100.0, sh = 100.0, layer = "3", qty = 4)
        val invoiceRepo = FakeInvoiceRepository(listOf(InvoiceWithItemsModel(invoice, listOf(item))))
        val useCase = EditInvoiceUseCase(invoiceRepo, inventoryRepo)

        val outcome = useCase(invoiceId = 1, customerId = 1, customerName = "علی", newQuantities = mapOf(1 to 0))
        assertTrue(outcome is com.rasticpack.app.core.result.RasticResult.Success)
        assertTrue(invoiceRepo.getById(1)!!.items.isEmpty())
        assertEquals(14, inventoryRepo.state.value.first().qty) // ۱۰ + ۴ برگشتی، هیچ‌چیز کسر نشد
    }
}
