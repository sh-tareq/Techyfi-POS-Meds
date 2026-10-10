package com.example.techyfipos.ui.viewmodel

import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.utils.RepositoryErrorBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelCrashTest {
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
    fun testR20_01_saveInvoiceSettingsDataWithoutPermissionCatchesErrorAndEmitsToBus() = runTest(testDispatcher) {
        val repository = mock(DataRepository::class.java)
            `when`(repository.languageFlow).thenReturn(flowOf("en"))
            `when`(repository.themeModeFlow).thenReturn(flowOf("SYSTEM"))
            `when`(repository.uiLayoutModeFlow).thenReturn(flowOf("AUTO"))
            `when`(repository.defaultCostPercentageFlow).thenReturn(flowOf(100.0))
            `when`(repository.shopNameFlow).thenReturn(flowOf("My Shop"))
            `when`(repository.shopAddressFlow).thenReturn(flowOf(""))
            `when`(repository.shopPhoneFlow).thenReturn(flowOf(""))
            `when`(repository.shopLogoUriFlow).thenReturn(flowOf(""))
            `when`(repository.facebookUrlFlow).thenReturn(flowOf(""))
            `when`(repository.qrCodeInfoFlow).thenReturn(flowOf(""))
            `when`(repository.cashierNameFlow).thenReturn(flowOf("Cashier"))
            `when`(repository.invoiceSettingsDataFlow).thenReturn(flowOf(InvoiceSettingsData()))
            `when`(repository.quickSalePresetsFlow).thenReturn(flowOf(emptyList()))
            `when`(repository.dueReminderTemplatesFlow).thenReturn(flowOf(emptyList()))
            `when`(repository.autoBackupFlow).thenReturn(flowOf(false))
            `when`(repository.localAutoBackupFlow).thenReturn(flowOf(false))
            `when`(repository.autoBackupFrequencyFlow).thenReturn(flowOf("DAILY"))
            `when`(repository.driveAutoBackupFrequencyFlow).thenReturn(flowOf("DAILY"))
            `when`(repository.lastLocalBackupTimestampFlow).thenReturn(flowOf(0L))
            `when`(repository.lastDriveBackupTimestampFlow).thenReturn(flowOf(0L))
            `when`(repository.driveRetentionCountFlow).thenReturn(flowOf(7))
            `when`(repository.driveNetworkPolicyFlow).thenReturn(flowOf("WIFI_ONLY"))
            `when`(repository.driveBackupTimeFlow).thenReturn(flowOf("02:00"))

            val expectedError = SecurityException("Unauthorized: missing SYSTEM_SHOP_SETTINGS")
            doAnswer { throw expectedError }
                .`when`(repository).saveInvoiceSettingsData(any(InvoiceSettingsData::class.java) ?: InvoiceSettingsData())

            val viewModel = SettingsViewModel(repository)

            var emittedError: Throwable? = null
            val collectJob = launch {
                emittedError = RepositoryErrorBus.errors.first()
            }

            viewModel.saveInvoiceSettingsData(InvoiceSettingsData())
            testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Error must be caught and broadcasted without throwing", emittedError != null)
        assertEquals(expectedError.message, emittedError?.message)
        collectJob.cancel()
    }
}
