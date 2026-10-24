package com.techyfiposmeds.ui.viewmodel

import com.techyfiposmeds.data.DataRepository
import com.techyfiposmeds.data.db.CustomerLoyalty
import com.techyfiposmeds.data.db.Expense
import com.techyfiposmeds.utils.LoyaltyProgramSettings
import com.techyfiposmeds.utils.RepositoryErrorBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class LoyaltyExpenseDuesCrashTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testR20_03_loyaltyUpsertUnauthorizedEmitsToErrorBusAndCallbackFails() = runTest(testDispatcher) {
        val repository = mock(DataRepository::class.java)
        `when`(repository.getLoyaltySettings()).thenReturn(LoyaltyProgramSettings())
        `when`(repository.allLoyaltyMembers).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allLoyaltyTransactions).thenReturn(MutableStateFlow(emptyList()))

        val expectedError = SecurityException("Unauthorized: missing LOYALTY_MANAGE")
        doAnswer { throw expectedError }
            .`when`(repository).upsertLoyalty(any(CustomerLoyalty::class.java) ?: CustomerLoyalty(phone = "017", name = "Name", tier = "Regular"))

        val viewModel = LoyaltyViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        var callbackResult: Result<Unit>? = null
        var emittedError: Throwable? = null
        val collectJob = launch {
            emittedError = RepositoryErrorBus.errors.first()
        }

        viewModel.upsertMember("01700000000", "Rahim") {
            callbackResult = it
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull("Error bus must receive exception from loyalty upsert", emittedError)
        assertEquals(expectedError.message, emittedError?.message)
        assertTrue("Callback must receive failure result", callbackResult?.isFailure == true)

        collectJob.cancel()
    }

    @Test
    fun testR20_03_expenseAddUnauthorizedEmitsToErrorBusAndCallbackFails() = runTest(testDispatcher) {
        val repository = mock(DataRepository::class.java)
        `when`(repository.allExpenses).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.registerShiftState).thenReturn(MutableStateFlow(null))
        `when`(repository.allSales).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allReturns).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allDuePayments).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allSupplierPayments).thenReturn(MutableStateFlow(emptyList()))

        val expectedError = SecurityException("Unauthorized: missing EXPENSES_MANAGE")
        doAnswer { throw expectedError }
            .`when`(repository).insertExpense(any(Expense::class.java) ?: Expense(title = "T", category = "C", type = "EXPENSE", amount = 10.0, paymentSource = "Cash", timestamp = 0L))

        val viewModel = ExpenseViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        var callbackResult: Result<Unit>? = null
        var emittedError: Throwable? = null
        val collectJob = launch {
            emittedError = RepositoryErrorBus.errors.first()
        }

        viewModel.addExpense("Tea", "Office", "EXPENSE", 50.0) {
            callbackResult = it
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull("Error bus must receive exception from addExpense", emittedError)
        assertEquals(expectedError.message, emittedError?.message)
        assertTrue("Callback must receive failure result", callbackResult?.isFailure == true)

        collectJob.cancel()
    }

    @Test
    fun testR20_03_deleteCustomerDueUnauthorizedEmitsToErrorBusAndCallbackFails() = runTest(testDispatcher) {
        val repository = mock(DataRepository::class.java)
        `when`(repository.allOutstandingDues).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allDues).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allDuePayments).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allScheduledDueReminders).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allDueReminderLogs).thenReturn(MutableStateFlow(emptyList()))

        val expectedError = IllegalStateException("Cannot delete customer due ledger with positive balance")
        doAnswer { throw expectedError }
            .`when`(repository).deleteCustomerDue(any(String::class.java) ?: "")

        val viewModel = DuesViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        var callbackResult: Result<Unit>? = null
        var emittedError: Throwable? = null
        val collectJob = launch {
            emittedError = RepositoryErrorBus.errors.first()
        }

        viewModel.deleteCustomerDue("01700000000") {
            callbackResult = it
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull("Error bus must receive exception from deleteCustomerDue", emittedError)
        assertEquals(expectedError.message, emittedError?.message)
        assertTrue("Callback must receive failure result", callbackResult?.isFailure == true)

        collectJob.cancel()
    }
}
