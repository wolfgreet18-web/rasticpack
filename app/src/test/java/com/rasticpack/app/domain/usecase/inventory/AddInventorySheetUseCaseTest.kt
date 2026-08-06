package com.rasticpack.app.domain.usecase.inventory

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.domain.model.InventorySheet
import com.rasticpack.app.domain.repository.InventorySheetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) ══
 * تست واحد (JVM) برای [AddInventorySheetUseCase] — طبق همان الگوی
 * [AddCustomerUseCaseTest]/[AddDriverUseCaseTest]. سناریوها معادل دقیق رفتار
 * addInventoryRow() در 4.html: ابعاد نامعتبر، موجودی منفی، ترکیب تکراری، مسیر موفق.
 */
class AddInventorySheetUseCaseTest {

    private class FakeInventorySheetRepository : InventorySheetRepository {
        private val state = MutableStateFlow<List<InventorySheet>>(emptyList())
        private var nextId = 1

        override fun observeAll(): Flow<List<InventorySheet>> = state
        override suspend fun getAll(): List<InventorySheet> = state.value
        override suspend fun getById(id: Int): InventorySheet? = state.value.find { it.id == id }
        override suspend fun getUniqueDims(): List<Pair<Double, Double>> =
            state.value.map { it.sh to it.sw }.distinct()

        override suspend fun insert(sheet: InventorySheet): InventorySheet {
            val withId = sheet.copy(id = nextId++)
            state.value = state.value + withId
            return withId
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
            return insert(InventorySheet(sw = sw, sh = sh, layer = layer, qty = 0, flute = flute, paperType = paperType))
        }
    }

    @Test
    fun `non-positive dimensions returns failure and inserts nothing`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = AddInventorySheetUseCase(repo)

        val result = useCase(sh = 0.0, sw = 100.0, layer = "3", qty = 10, flute = "C", paperType = "KT")

        assertTrue(result.isFailure)
        assertEquals(RasticError.InvalidSheetDimensions, result.errorOrNull())
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `negative quantity returns failure`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = AddInventorySheetUseCase(repo)

        val result = useCase(sh = 100.0, sw = 80.0, layer = "3", qty = -1, flute = "C", paperType = "KT")

        assertTrue(result.isFailure)
        assertEquals(RasticError.InvalidStockQuantity, result.errorOrNull())
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `duplicate combination returns failure`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = AddInventorySheetUseCase(repo)
        useCase(sh = 100.0, sw = 80.0, layer = "3", qty = 10, flute = "C", paperType = "KT")

        val result = useCase(sh = 100.0, sw = 80.0, layer = "3", qty = 5, flute = "C", paperType = "KT")

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is RasticError.DuplicateSheet)
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun `different flute or paperType is not treated as duplicate`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = AddInventorySheetUseCase(repo)
        useCase(sh = 100.0, sw = 80.0, layer = "3", qty = 10, flute = "C", paperType = "KT")

        val result = useCase(sh = 100.0, sw = 80.0, layer = "3", qty = 5, flute = "E", paperType = "KT")

        assertTrue(result.isSuccess)
        assertEquals(2, repo.getAll().size)
    }

    @Test
    fun `valid new sheet is inserted`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = AddInventorySheetUseCase(repo)

        val result = useCase(sh = 220.0, sw = 180.0, layer = "5", qty = 300, flute = "C", paperType = "2T")

        assertTrue(result.isSuccess)
        val sheet = result.getOrNull()!!
        assertEquals(220.0, sheet.sh, 0.0)
        assertEquals(180.0, sheet.sw, 0.0)
        assertEquals("5", sheet.layer)
        assertEquals(300, sheet.qty)
        assertEquals(1, repo.getAll().size)
    }
}
