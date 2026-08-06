package com.rasticpack.app.domain.usecase.customer

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.domain.model.Customer
import com.rasticpack.app.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) ══
 * تست واحد (JVM، بدون نیاز به دستگاه/امولاتور) برای [AddCustomerUseCase] — طبق
 * همان الگوی [AddDriverUseCaseTest] در مرحله ۲: یک [FakeCustomerRepository]
 * در-حافظه، بدون Room واقعی.
 *
 * سه سناریو دقیقاً معادل رفتار addCustomer() در 4.html:
 *   ۱) نام خالی → خطا، هیچ رکوردی درج نمی‌شود.
 *   ۲) نام تکراری (بدون حساسیت به بزرگ/کوچکی و فاصله‌ی اضافه) → خطا.
 *   ۳) مسیر موفق → رکورد درج می‌شود و مقدار trim‌شده برمی‌گردد.
 */
class AddCustomerUseCaseTest {

    private class FakeCustomerRepository : CustomerRepository {
        private val state = MutableStateFlow<List<Customer>>(emptyList())
        private var nextId = 1
        val renamedInvoiceCalls = mutableListOf<Pair<Int, String>>()

        override fun observeAll(): Flow<List<Customer>> = state

        override suspend fun getAll(): List<Customer> = state.value

        override suspend fun findById(id: Int): Customer? = state.value.find { it.id == id }

        override suspend fun findByName(name: String): Customer? {
            val n = name.trim().lowercase()
            return state.value.find { it.name.trim().lowercase() == n }
        }

        override suspend fun insert(customer: Customer): Customer {
            val withId = customer.copy(id = nextId++)
            state.value = state.value + withId
            return withId
        }

        override suspend fun update(customer: Customer) {
            state.value = state.value.map { if (it.id == customer.id) customer else it }
        }

        override suspend fun delete(id: Int) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun renameCustomerOnInvoices(customerId: Int, newName: String) {
            renamedInvoiceCalls.add(customerId to newName)
        }
    }

    @Test
    fun `blank name returns failure and inserts nothing`() = runBlocking {
        val repo = FakeCustomerRepository()
        val useCase = AddCustomerUseCase(repo)

        val result = useCase(
            name = "   ", company = "", address = "", phone = "",
            lat = null, lng = null, locationLink = null
        )

        assertTrue(result.isFailure)
        assertEquals(RasticError.CustomerNameBlank, result.errorOrNull())
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `duplicate name (case and spacing insensitive) returns failure`() = runBlocking {
        val repo = FakeCustomerRepository()
        val useCase = AddCustomerUseCase(repo)
        useCase(name = "علی رضایی", company = "", address = "", phone = "", lat = null, lng = null, locationLink = null)

        val result = useCase(
            name = "  علی رضایی  ", company = "شرکت جدید", address = "", phone = "",
            lat = null, lng = null, locationLink = null
        )

        assertTrue(result.isFailure)
        assertEquals(RasticError.CustomerNameDuplicate, result.errorOrNull())
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun `valid new customer is inserted and trimmed`() = runBlocking {
        val repo = FakeCustomerRepository()
        val useCase = AddCustomerUseCase(repo)

        val result = useCase(
            name = "  رضا کریمی  ", company = " شرکت ما ", address = "", phone = " 0912111 ",
            lat = 36.3, lng = 59.5, locationLink = "https://neshan.org/x"
        )

        assertTrue(result.isSuccess)
        val customer = result.getOrNull()!!
        assertEquals("رضا کریمی", customer.name)
        assertEquals("شرکت ما", customer.company)
        assertEquals("0912111", customer.phone)
        assertEquals(1, repo.getAll().size)
    }
}
