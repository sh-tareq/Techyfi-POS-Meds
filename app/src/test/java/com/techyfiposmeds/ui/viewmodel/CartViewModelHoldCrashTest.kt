package com.techyfiposmeds.ui.viewmodel

import com.techyfiposmeds.data.DataRepository
import com.techyfiposmeds.data.InvoiceSettingsData
import com.techyfiposmeds.data.db.HeldCart
import com.techyfiposmeds.data.db.Product
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
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelHoldCrashTest {
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
    fun testR20_02_holdCurrentCartFailureRollsBackStateAndEmitsToErrorBus() = runTest(testDispatcher) {
        val repository = mock(DataRepository::class.java)
        `when`(repository.cashierNameFlow).thenReturn(flowOf("Test Cashier"))
        `when`(repository.loyaltySettingsFlow).thenReturn(MutableStateFlow(LoyaltyProgramSettings()))
        `when`(repository.allHeldCarts).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.heldCartCount).thenReturn(MutableStateFlow(0))
        `when`(repository.invoiceSettingsDataFlow).thenReturn(MutableStateFlow(InvoiceSettingsData()))
        `when`(repository.getCartSnapshot()).thenReturn("")

        val expectedError = SecurityException("Unauthorized: missing SALES_HOLD_CART")
        doAnswer { throw expectedError }
            .`when`(repository).insertHeldCart(any(HeldCart::class.java) ?: HeldCart(title = "test", itemsJson = "{}"))

        val viewModel = CartViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val product = Product(
            barcode = "SKU-HOLD-1",
            name = "Test Item",
            category = "General",
            purchasePrice = 10.0,
            sellingPrice = 20.0,
            discountedPrice = 18.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        viewModel.addToCart(product, 2.0)
        viewModel.setCustomerName("Rahim")
        viewModel.setCustomerPhone("01711223344")
        viewModel.setDiscountAmount(15.0)

        var emittedError: Throwable? = null
        val collectJob = launch {
            emittedError = RepositoryErrorBus.errors.first()
        }

        viewModel.holdCurrentCart(title = "Hold Table 1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull("Error bus must receive exception from holdCurrentCart", emittedError)
        assertEquals(expectedError.message, emittedError?.message)
        assertEquals(1, viewModel.cartItems.value.size)
        assertEquals("SKU-HOLD-1", viewModel.cartItems.value[0].product.barcode)
        assertEquals("Rahim", viewModel.customerName.value)
        assertEquals("01711223344", viewModel.customerPhone.value)
        assertEquals(15.0, viewModel.discountAmount.value, 0.001)

        collectJob.cancel()
    }
}
