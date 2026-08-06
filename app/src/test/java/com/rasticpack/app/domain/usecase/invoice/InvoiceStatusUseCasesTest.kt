package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.domain.model.Invoice
import com.rasticpack.app.domain.model.InvoiceItem
import com.rasticpack.app.domain.model.InvoiceWithItemsModel
import com.rasticpack.app.domain.model.InventorySheet
import com.rasticpack.app.domain.repository.InventorySheetRepository
import com.rasticpack.app.domain.repository.InvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══ مرحله ۳.۳ (نقشه معماری) ══
 * تست واحد (JVM) برای UseCase های ساده‌ی مربوط به فاکتور (بدون وابستگی به
 * موتور محاسبه‌ی تب calc2): MarkInvoiceSentUseCase, MarkInvoiceSettledUseCase,
 * SubmitInvoicePaymentUseCase, SetInvoiceBundleSizeUseCase, DeleteInvoiceUseCase.
 * سناریوها معادل دقیق رفتار markInvoiceSent/markInvoiceSettled/submitInvoicePayment/
 * bundleClick/deleteInvoice در 4.html.
 */
class InvoiceStatusUseCasesTest {

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
            state.value = state.value.map {
                if (it.invoice.id == invoice.id) it.copy(invoice = invoice) else it
            }
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
        private val state = MutableStateFlow(initial)

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
            val s = state.value.find { it.id == sheetId } ?: return
            update(s.copy(qty = (s.qty - amount).coerceAtLeast(0)))
        }

        override suspend fun increaseQty(id: Int, amount: Int) {
            val s = state.value.find { it.id == id } ?: return
            update(s.copy(qty = s.qty + amount))
        }

        override suspend fun findOrCreateSheet(
            sh: Double, sw: Double, layer: String, flute: String, paperType: String
        ): InventorySheet {
            state.value.find { it.sw == sw && it.sh == sh && it.layer == layer && it.flute == flute && it.paperType == paperType }
                ?.let { return it }
            val created = InventorySheet(id = state.value.size + 1, sw = sw, sh = sh, layer = layer, qty = 0, flute = flute, paperType = paperType)
            return insert(created)
        }
    }

    private fun sampleInvoice(id: Int = 1, status: String = "draft", sent: Boolean = false, paidAmount: Double? = null) =
        InvoiceWithItemsModel(
            invoice = Invoice(id = id, customerId = 1, customerName = "علی", dateIso = "2026-01-01T00:00:00Z", status = status, sent = sent, paidAmount = paidAmount),
            items = listOf(
                InvoiceItem(id = 1, invoiceId = id, sheetId = 10, sw = 80.0, sh = 100.0, layer = "3", qty = 5, lineTotal = 100_000.0)
            )
        )

    // ══ MarkInvoiceSentUseCase ══

    @Test
    fun `mark sent sets sent flag true`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val useCase = MarkInvoiceSentUseCase(repo)

        val result = useCase(1, true)

        assertTrue(result.isSuccess)
        assertTrue(repo.getById(1)!!.invoice.sent)
    }

    @Test
    fun `mark sent on missing invoice returns InvoiceNotFound`() = runBlocking {
        val repo = FakeInvoiceRepository()
        val useCase = MarkInvoiceSentUseCase(repo)

        val result = useCase(999, true)

        assertTrue(result.isFailure)
        assertEquals(RasticError.InvoiceNotFound, result.errorOrNull())
    }

    // ══ MarkInvoiceSettledUseCase ══

    @Test
    fun `settle sets status paid and paidAmount to total`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val useCase = MarkInvoiceSettledUseCase(repo)

        useCase.settle(1)

        val inv = repo.getById(1)!!.invoice
        assertEquals("paid", inv.status)
        assertEquals(100_000.0, inv.paidAmount)
    }

    @Test
    fun `unsettle reverts to draft only if currently paid`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice(status = "paid", paidAmount = 100_000.0)))
        val useCase = MarkInvoiceSettledUseCase(repo)

        useCase.unsettle(1)

        val inv = repo.getById(1)!!.invoice
        assertEquals("draft", inv.status)
        assertNull(inv.paidAmount)
    }

    @Test
    fun `unsettle does nothing if not paid`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice(status = "partial", paidAmount = 50_000.0)))
        val useCase = MarkInvoiceSettledUseCase(repo)

        useCase.unsettle(1)

        val inv = repo.getById(1)!!.invoice
        assertEquals("partial", inv.status)
        assertEquals(50_000.0, inv.paidAmount)
    }

    // ══ SubmitInvoicePaymentUseCase ══

    @Test
    fun `payment of zero or null resets to draft`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice(status = "partial", paidAmount = 50_000.0)))
        val useCase = SubmitInvoicePaymentUseCase(repo)

        useCase(1, null)

        val inv = repo.getById(1)!!.invoice
        assertEquals("draft", inv.status)
        assertNull(inv.paidAmount)
    }

    @Test
    fun `payment covering full amount marks paid`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val useCase = SubmitInvoicePaymentUseCase(repo)

        useCase(1, 150_000.0) // بیشتر از کل — باید paidAmount به مبلغ کل محدود شود

        val inv = repo.getById(1)!!.invoice
        assertEquals("paid", inv.status)
        assertEquals(100_000.0, inv.paidAmount)
    }

    @Test
    fun `partial payment marks partial with exact amount`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val useCase = SubmitInvoicePaymentUseCase(repo)

        useCase(1, 40_000.0)

        val inv = repo.getById(1)!!.invoice
        assertEquals("partial", inv.status)
        assertEquals(40_000.0, inv.paidAmount)
    }

    // ══ SetInvoiceBundleSizeUseCase ══

    @Test
    fun `setting bundle size stores it on item`() = runBlocking {
        val repo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val useCase = SetInvoiceBundleSizeUseCase(repo)

        useCase(invoiceId = 1, itemId = 1, size = 15)

        assertEquals(15, repo.getById(1)!!.items.first().bundleSize)
    }

    @Test
    fun `passing null clears bundle size (toggle decision made by caller)`() = runBlocking {
        // مرحله ۳.۳ بخش دوم: تصمیم toggle در UI/ViewModel گرفته می‌شود (دقیقاً مثل
        // bundleClick در وب)، نه در خودِ UseCase — پس اینجا فقط رفتار "size=null یعنی پاک‌شدن" تست می‌شود.
        val repo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val useCase = SetInvoiceBundleSizeUseCase(repo)

        useCase(1, 1, 15)
        useCase(1, 1, null)

        assertNull(repo.getById(1)!!.items.first().bundleSize)
    }

    // ══ DeleteInvoiceUseCase ══

    @Test
    fun `deleting invoice restores stock and removes invoice`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val inventoryRepo = FakeInventorySheetRepository(
            listOf(InventorySheet(id = 10, sw = 80.0, sh = 100.0, layer = "3", qty = 20, flute = "C", paperType = "KT"))
        )
        val useCase = DeleteInvoiceUseCase(invoiceRepo, inventoryRepo)

        val result = useCase(1)

        assertTrue(result.isSuccess)
        assertNull(invoiceRepo.getById(1))
        assertEquals(25, inventoryRepo.getById(10)!!.qty) // 20 + 5 (qty مصرف‌شده)
    }

    @Test
    fun `deleting missing invoice returns InvoiceNotFound`() = runBlocking {
        val useCase = DeleteInvoiceUseCase(FakeInvoiceRepository(), FakeInventorySheetRepository())

        val result = useCase(999)

        assertTrue(result.isFailure)
        assertEquals(RasticError.InvoiceNotFound, result.errorOrNull())
    }
}
