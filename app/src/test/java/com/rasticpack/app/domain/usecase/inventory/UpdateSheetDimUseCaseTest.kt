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
 * تست واحد (JVM) برای [UpdateSheetDimUseCase] — معادل دقیق updateDim() در 4.html:
 * مقدار غیرمثبت نادیده گرفته می‌شود، ترکیب تکراری با ورق دیگر رد می‌شود، مسیر موفق
 * فقط بعد سفارش‌شده را عوض می‌کند.
 */
class UpdateSheetDimUseCaseTest {

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

        override suspend fun decreaseQty(sheetId: Int, amount: Int) {}
        override suspend fun increaseQty(id: Int, amount: Int) {}

        override suspend fun findOrCreateSheet(
            sh: Double, sw: Double, layer: String, flute: String, paperType: String
        ): InventorySheet {
            state.value.find { it.sw == sw && it.sh == sh && it.layer == layer && it.flute == flute && it.paperType == paperType }
                ?.let { return it }
            return insert(InventorySheet(sw = sw, sh = sh, layer = layer, qty = 0, flute = flute, paperType = paperType))
        }
    }

    @Test
    fun `non-positive value is ignored without error`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = UpdateSheetDimUseCase(repo)
        val target = repo.insert(InventorySheet(sw = 80.0, sh = 100.0, layer = "3"))

        val result = useCase(target.id, "sh", -5.0)

        assertTrue(result.isSuccess)
        assertEquals(100.0, repo.getById(target.id)!!.sh, 0.0)
    }

    @Test
    fun `changing to a combination that duplicates another sheet returns failure`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = UpdateSheetDimUseCase(repo)
        repo.insert(InventorySheet(sw = 80.0, sh = 100.0, layer = "3"))
        val target = repo.insert(InventorySheet(sw = 90.0, sh = 100.0, layer = "3"))

        val result = useCase(target.id, "sw", 80.0)

        assertTrue(result.isFailure)
        assertEquals(RasticError.DuplicateSheetOnDimUpdate, result.errorOrNull())
        assertEquals(90.0, repo.getById(target.id)!!.sw, 0.0)
    }

    @Test
    fun `valid change updates only the requested dimension`() = runBlocking {
        val repo = FakeInventorySheetRepository()
        val useCase = UpdateSheetDimUseCase(repo)
        val target = repo.insert(InventorySheet(sw = 80.0, sh = 100.0, layer = "3"))

        val result = useCase(target.id, "sw", 85.0)

        assertTrue(result.isSuccess)
        val updated = repo.getById(target.id)!!
        assertEquals(85.0, updated.sw, 0.0)
        assertEquals(100.0, updated.sh, 0.0)
    }
}
