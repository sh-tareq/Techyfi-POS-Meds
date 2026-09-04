package com.example.techyfipos.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleDriveAuthorizationIntegrationTest {

    @Test
    fun backupAuthorizationUsesOnlyAppDataAndIdentityScopes() {
        val scopeUris = GoogleDriveHelper.requestedScopes.map { it.scopeUri }

        assertTrue(scopeUris.contains(GoogleDriveBackupManager.DRIVE_APPDATA_SCOPE))
        assertTrue(scopeUris.contains("openid"))
        assertTrue(scopeUris.contains("https://www.googleapis.com/auth/userinfo.email"))
        assertFalse(scopeUris.contains("https://www.googleapis.com/auth/drive"))
        assertFalse(scopeUris.contains("https://www.googleapis.com/auth/drive.file"))
        assertEquals(3, scopeUris.distinct().size)
    }
}
