package com.rasticpack.app.domain.usecase.settings

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.domain.model.Driver
import com.rasticpack.app.domain.repository.DriverRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) ══
 * تست واحد (JVM، بدون نیاز به دستگاه/امولاتور) برای [AddDriverUseCase] — طبق قانون
 * مرحله ۲ که می‌گوید هر UseCase تازه‌ساخته‌شده همراه با تست خودش نوشته می‌شود.
 * از یک [FakeDriverRepository] در-حافظه استفاده می‌کند، نه Room واقعی — پس این تست
 * در چند میلی‌ثانیه اجرا می‌شود و به `androidTest`/امولاتور وابسته نیست.
 *
 * سه سناریو دقیقاً معادل رفتار addDriver() در 4.html:
 *   ۱) نام خالی → خطا، هیچ رکوردی درج نمی‌شود.
 *   ۲) نام تکراری (بدون حساسیت به بزرگ/کوچکی و فاصله‌ی اضافه) → خطا.
 *   ۳) مسیر موفق → رکورد درج می‌شود و مقدار trim‌شده برمی‌گردد.
 */
class AddDriverUseCaseTest {

    private class FakeDriverRepository : DriverRepository {
        private val state = MutableStateFlow<List<Driver>>(emptyList())
        private var nextId = 1

        override fun observeAll(): Flow<List<Driver>> = state

        override suspend fun getAll(): List<Driver> = state.value

        override suspend fun findById(id: Int): Driver? = state.value.find { it.id == id }

        override suspend fun insert(driver: Driver) {
            val withId = driver.copy(id = nextId++)
            state.value = state.value + withId
        }

        override suspend fun update(driver: Driver) {
            state.value = state.value.map { if (it.id == driver.id) driver else it }
        }

        override suspend fun delete(id: Int) {
            state.value = state.value.filterNot { it.id == id }
        }
    }

    @Test
    fun `blank name returns failure and inserts nothing`() = runBlocking {
        val repo = FakeDriverRepository()
        val useCase = AddDriverUseCase(repo)

        val result = useCase(name = "   ", phone = "0912", plate = "")

        assertTrue(result.isFailure)
        assertEquals(RasticError.DriverNameBlank, result.errorOrNull())
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `duplicate name (case and spacing insensitive) returns failure`() = runBlocking {
        val repo = FakeDriverRepository()
        val useCase = AddDriverUseCase(repo)
        useCase(name = "حسین محمدی", phone = "", plate = "")

        val result = useCase(name = "  حسین محمدی  ", phone = "0912", plate = "")

        assertTrue(result.isFailure)
        assertEquals(RasticError.DriverNameDuplicateOnAdd, result.errorOrNull())
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun `valid new driver is inserted and trimmed`() = runBlocking {
        val repo = FakeDriverRepository()
        val useCase = AddDriverUseCase(repo)

        val result = useCase(name = "  رضا کریمی  ", phone = " 0936111 ", plate = "")

        assertTrue(result.isSuccess)
        val driver = result.getOrNull()!!
        assertEquals("رضا کریمی", driver.name)
        assertEquals("0936111", driver.phone)
        assertEquals(1, repo.getAll().size)
    }
}
