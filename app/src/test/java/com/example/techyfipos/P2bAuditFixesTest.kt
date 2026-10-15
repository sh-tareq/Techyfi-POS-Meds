package com.example.techyfipos

import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.ui.viewmodel.isLoyaltyPointsExpiringSoon
import com.example.techyfipos.utils.AutoBackupWorker
import com.example.techyfipos.utils.BillData
import com.example.techyfipos.utils.buildBillReceiptText
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ExecutionException

class P2bAuditFixesTest {

    @Test
    fun testR22_16_offlineExceptionUnwrapping() {
        // Direct IOException -> network error
        val ioException = IOException("Network connection reset")
        assertTrue(AutoBackupWorker.isNetworkOrTimeoutException(ioException))

        // ExecutionException wrapping IOException -> network error
        val wrappedIo = ExecutionException("Task execution failed", IOException("Connection timeout"))
        assertTrue(AutoBackupWorker.isNetworkOrTimeoutException(wrappedIo))

        // ExecutionException wrapping ApiException NETWORK_ERROR -> network error
        val apiNetwork = ApiException(Status(CommonStatusCodes.NETWORK_ERROR, "Network error"))
        val wrappedApiNetwork = ExecutionException("Task failed", apiNetwork)
        assertTrue(AutoBackupWorker.isNetworkOrTimeoutException(wrappedApiNetwork))

        // ExecutionException wrapping ApiException TIMEOUT -> network error
        val apiTimeout = ApiException(Status(CommonStatusCodes.TIMEOUT, "Timeout"))
        val wrappedApiTimeout = ExecutionException("Task failed", apiTimeout)
        assertTrue(AutoBackupWorker.isNetworkOrTimeoutException(wrappedApiTimeout))

        // ExecutionException wrapping ApiException with other status (e.g., auth failure) -> not network error
        val apiAuth = ApiException(Status(CommonStatusCodes.SIGN_IN_REQUIRED, "User interaction needed"))
        val wrappedApiAuth = ExecutionException("Task failed", apiAuth)
        assertFalse(AutoBackupWorker.isNetworkOrTimeoutException(wrappedApiAuth))

        // Other non-network exception
        val illegalState = IllegalStateException("Something else went wrong")
        assertFalse(AutoBackupWorker.isNetworkOrTimeoutException(illegalState))
    }

    @Test
    fun testR22_17_autoBackupWorkerRetryLogic() {
        val dayMs = 24 * 60 * 60 * 1000L
        val chainStart = 100 * dayMs
        val oldBackup = 50 * dayMs

        // Case 1: Attempt 0, local is requested and due -> should run
        val shouldRunAttempt0 = AutoBackupWorker.shouldRunLocalBackup(
            localRequested = true,
            runAttemptCount = 0,
            lastLocalBackupTimestamp = oldBackup,
            chainStartTime = chainStart,
            respectDueTime = true,
            now = chainStart,
            frequency = "DAILY"
        )
        assertTrue(shouldRunAttempt0)

        // Case 2: Attempt 0 failed local export, retried at Attempt 1 -> should run local backup
        val shouldRunAttempt1Failed = AutoBackupWorker.shouldRunLocalBackup(
            localRequested = true,
            runAttemptCount = 1,
            lastLocalBackupTimestamp = oldBackup, // Not updated because attempt 0 failed
            chainStartTime = chainStart,
            respectDueTime = true,
            now = chainStart + 15 * 60 * 1000L,
            frequency = "DAILY"
        )
        assertTrue(shouldRunAttempt1Failed)

        // Case 3: Attempt 0 succeeded on local backup, but Drive failed -> Attempt 1 should skip local
        val localFinishedTime = chainStart + 5000L
        val shouldRunAttempt1Succeeded = AutoBackupWorker.shouldRunLocalBackup(
            localRequested = true,
            runAttemptCount = 1,
            lastLocalBackupTimestamp = localFinishedTime, // Succeeded in attempt 0
            chainStartTime = chainStart,
            respectDueTime = true,
            now = chainStart + 15 * 60 * 1000L,
            frequency = "DAILY"
        )
        assertFalse(shouldRunAttempt1Succeeded)
    }

    @Test
    fun testR22_21_loyaltyPointsExpiringSoon() {
        val dayMs = 24 * 60 * 60 * 1000L
        val now = 400 * dayMs
        val expiryDays = 365

        // Zero balance -> never expiring soon
        assertFalse(isLoyaltyPointsExpiringSoon(pointsBalance = 0, updatedAt = 50 * dayMs, now = now, expiryDays = expiryDays))

        // Age 350 days (updated at Day 50): expires in 15 days (within 30 days window) -> true
        assertTrue(isLoyaltyPointsExpiringSoon(pointsBalance = 100, updatedAt = 50 * dayMs, now = now, expiryDays = expiryDays))

        // Age 380 days (updated at Day 20): already expired 15 days ago -> false
        assertFalse(isLoyaltyPointsExpiringSoon(pointsBalance = 100, updatedAt = 20 * dayMs, now = now, expiryDays = expiryDays))

        // Age 300 days (updated at Day 100): expires in 65 days (outside 30 days window) -> false
        assertFalse(isLoyaltyPointsExpiringSoon(pointsBalance = 100, updatedAt = 100 * dayMs, now = now, expiryDays = expiryDays))
    }

    @Test
    fun testR22_21_websiteUrlInReceipt() {
        val settings = InvoiceSettingsData(
            storeName = "Test Shop",
            websiteUrl = "www.techyfipos.com"
        )
        val bill = BillData(
            saleId = 101L,
            timestampMillis = 1700000000000L,
            shopName = "Test Shop",
            shopAddress = "",
            shopPhone = "",
            items = emptyList(),
            subtotal = 100.0,
            discountValue = 0.0,
            grandTotal = 100.0,
            paymentMethod = "Cash",
            customerName = "Walk-in",
            customerPhone = "",
            amountPaid = 100.0,
            changeAmount = 0.0,
            settingsData = settings
        )

        val receiptText = buildBillReceiptText(bill)
        assertTrue(receiptText.contains("www.techyfipos.com"))

        // Blank websiteUrl does not appear
        val billNoWeb = bill.copy(settingsData = settings.copy(websiteUrl = ""))
        val receiptNoWeb = buildBillReceiptText(billNoWeb)
        assertFalse(receiptNoWeb.contains("www.techyfipos.com"))
    }
}
