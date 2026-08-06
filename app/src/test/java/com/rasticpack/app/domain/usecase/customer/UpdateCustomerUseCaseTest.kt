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
 * تست واحد برای [UpdateCustomerUseCase] — طبق همان الگوی [AddCustomerUseCaseTest].
 * سناریوی اضافه نسبت به Driver: وقتی نام واقعاً تغییر می‌کند، باید
 * `renameCustomerOnInvoices` دقیقاً یک‌بار صدا زده شود (معادل بخش «if(nameChanged)»
 * در `saveCustomerEdit()` وب)؛ اگر نام تغییر نکرده باشد، اصلاً نباید صدا زده شود.
 */
class UpdateCustomerUseCaseTest {

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

    private suspend fun seedOne(repo: FakeCustomerRepository, name: String): Customer =
        repo.insert(Customer(name = name))

    @Test
    fun `customer not found returns failure`() = runBlocking {
        val repo = FakeCustomerRepository()
        val useCase = UpdateCustomerUseCase(repo)

        val result = useCase(
            id = 999, name = "نام جدید", company = "", address = "", phone = "",
            lat = null, lng = null, locationLink = null
        )

        assertTrue(result.isFailure)
        assertEquals(RasticError.CustomerNotFound(), result.errorOrNull())
    }

    @Test
    fun `renaming to a name taken by another customer returns failure`() = runBlocking {
        val repo = FakeCustomerRepository()
        val useCase = UpdateCustomerUseCase(repo)
        seedOne(repo, "علی رضایی")
        val target = seedOne(repo, "محمد احمدی")

        val result = useCase(
            id = target.id, name = "علی رضایی", company = "", address = "", phone = "",
            lat = null, lng = null, locationLink = null
        )

        assertTrue(result.isFailure)
        assertEquals(RasticError.CustomerNameDuplicate, result.errorOrNull())
    }

    @Test
    fun `changing name propagates rename to invoices exactly once`() = runBlocking {
        val repo = FakeCustomerRepository()
        val useCase = UpdateCustomerUseCase(repo)
        val target = seedOne(repo, "نام قدیم")

        val result = useCase(
            id = target.id, name = "نام جدید", company = "شرکت", address = "", phone = "",
            lat = null, lng = null, locationLink = null
        )

        assertTrue(result.isSuccess)
        assertEquals("نام جدید", result.getOrNull()!!.name)
        assertEquals(listOf(target.id to "نام جدید"), repo.renamedInvoiceCalls)
    }

    @Test
    fun `keeping the same name does not trigger invoice rename`() = runBlocking {
        val repo = FakeCustomerRepository()
        val useCase = UpdateCustomerUseCase(repo)
        val target = seedOne(repo, "همان نام")

        val result = useCase(
            id = target.id, name = "همان نام", company = "شرکت به‌روزشده", address = "", phone = "",
            lat = null, lng = null, locationLink = null
        )

        assertTrue(result.isSuccess)
        assertEquals("شرکت به‌روزشده", result.getOrNull()!!.company)
        assertTrue(repo.renamedInvoiceCalls.isEmpty())
    }
}
