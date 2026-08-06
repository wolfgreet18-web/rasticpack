package com.rasticpack.app.domain.usecase.production

import com.rasticpack.app.domain.model.Invoice
import com.rasticpack.app.domain.model.InvoiceItem
import com.rasticpack.app.domain.model.InvoiceWithItemsModel
import com.rasticpack.app.domain.model.ProductionQueueItem
import com.rasticpack.app.domain.repository.InvoiceRepository
import com.rasticpack.app.domain.repository.ProductionQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══ مرحله ۳.۴ (نقشه معماری v2.9) ══
 * تست واحد برای SendToProductionUseCase — معادل دقیق sendAllToProduction در 4.html،
 * با تمرکز روی منطق sourceKey (جلوگیری از تکرار هنگام ارسال دوباره‌ی همان فاکتور).
 */
class SendToProductionUseCaseTest {

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

    private class FakeProductionQueueRepository(initial: List<ProductionQueueItem> = emptyList()) : ProductionQueueRepository {
        val state = MutableStateFlow(initial)
        var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

        override fun observeAll(): Flow<List<ProductionQueueItem>> = state
        override suspend fun getAllSourceKeys(): Set<String> = state.value.map { it.sourceKey }.toHashSet()
        override suspend fun findFirstByInvoicePrefix(invoiceId: Int): ProductionQueueItem? =
            state.value.filter { it.sourceKey.startsWith("$invoiceId-") }.minByOrNull { it.id }

        override suspend fun insert(item: ProductionQueueItem): Int {
            val id = nextId++
            state.value = state.value + item.copy(id = id)
            return id
        }

        override suspend fun deleteById(id: Int) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun trimTo30() {
            if (state.value.size > 30) {
                state.value = state.value.sortedByDescending { it.id }.take(30)
            }
        }
    }

    private fun sampleInvoice(id: Int = 1, itemQtys: List<Int> = listOf(4, 5)) = InvoiceWithItemsModel(
        Invoice(id = id, customerId = 1, customerName = "علی", dateIso = "2024-01-01T00:00:00Z", status = "draft", totalSheets = itemQtys.sum()),
        itemQtys.mapIndexed { idx, q ->
            InvoiceItem(id = idx + 1, invoiceId = id, sheetId = 1, sw = 100.0, sh = 100.0, layer = "3", qty = q, cartonName = "کارتن")
        }
    )

    @Test
    fun `invoice not found returns null success`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository()
        val queueRepo = FakeProductionQueueRepository()
        val useCase = SendToProductionUseCase(invoiceRepo, queueRepo)

        val result = useCase(999)
        assertTrue(result is com.rasticpack.app.core.result.RasticResult.Success)
        assertNull((result as com.rasticpack.app.core.result.RasticResult.Success).data)
    }

    @Test
    fun `sending adds all items with sourceKey and returns first new id`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val queueRepo = FakeProductionQueueRepository()
        val useCase = SendToProductionUseCase(invoiceRepo, queueRepo)

        val result = useCase(1)
        assertTrue(result is com.rasticpack.app.core.result.RasticResult.Success)
        val firstId = (result as com.rasticpack.app.core.result.RasticResult.Success).data
        assertNotNull(firstId)
        assertEquals(2, queueRepo.state.value.size)
        assertEquals(setOf("1-0", "1-1"), queueRepo.state.value.map { it.sourceKey }.toSet())
        assertTrue(invoiceRepo.getById(1)!!.invoice.sentToProduction)
    }

    @Test
    fun `sending again does not duplicate items still in queue`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val queueRepo = FakeProductionQueueRepository()
        val useCase = SendToProductionUseCase(invoiceRepo, queueRepo)

        useCase(1)
        val result = useCase(1) // ارسال دوباره — همه از قبل در صف‌اند

        assertTrue(result is com.rasticpack.app.core.result.RasticResult.Success)
        assertEquals(2, queueRepo.state.value.size) // بدون تکرار
        val firstId = (result as com.rasticpack.app.core.result.RasticResult.Success).data
        assertNotNull(firstId) // اولین رکورد موجود برگردانده می‌شود
    }

    @Test
    fun `re-sending after one item removed from queue re-adds only the missing item`() = runBlocking {
        val invoiceRepo = FakeInvoiceRepository(listOf(sampleInvoice()))
        val queueRepo = FakeProductionQueueRepository()
        val useCase = SendToProductionUseCase(invoiceRepo, queueRepo)

        useCase(1)
        val removedId = queueRepo.state.value.first { it.sourceKey == "1-0" }.id
        queueRepo.deleteById(removedId)
        assertEquals(1, queueRepo.state.value.size)

        useCase(1)
        assertEquals(2, queueRepo.state.value.size)
        assertEquals(setOf("1-0", "1-1"), queueRepo.state.value.map { it.sourceKey }.toSet())
    }
}
