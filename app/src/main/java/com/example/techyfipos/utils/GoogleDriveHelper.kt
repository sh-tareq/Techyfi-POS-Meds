package com.example.techyfipos.utils

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GoogleDriveHelper {

    private const val OPEN_ID_SCOPE = "openid"
    private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
    private const val USER_INFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"

    val requestedScopes: List<Scope> = listOf(
        Scope(GoogleDriveBackupManager.DRIVE_APPDATA_SCOPE),
        Scope(OPEN_ID_SCOPE),
        Scope(EMAIL_SCOPE)
    )

    data class DriveConnection(
        val accountId: String,
        val email: String,
        val accessToken: String
    )

    data class DriveBackupFile(
        val id: String,
        val name: String,
        val size: Long,
        val createdTime: Long,
        val isAutoBackup: Boolean = false,
        val formattedDate: String = "",
        val formattedSize: String = ""
    )

    fun getAuthorizationClient(context: Context): AuthorizationClient =
        Identity.getAuthorizationClient(context)

    fun buildAuthorizationRequest(accountEmail: String? = null): AuthorizationRequest {
        val builder = AuthorizationRequest.builder().setRequestedScopes(requestedScopes)
        accountEmail?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.setAccount(Account(it, "com.google"))
        }
        return builder.build()
    }

    suspend fun resolveConnection(result: AuthorizationResult): DriveConnection = withContext(Dispatchers.IO) {
        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: error("Google did not return a Drive access token")
        val connection = URL(USER_INFO_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "Google account lookup failed ($responseCode)" }
            val identity = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val accountId = identity.optString("sub").takeIf { it.isNotBlank() }
                ?: error("Google account identity is unavailable")
            val email = identity.optString("email").takeIf { it.isNotBlank() }
                ?: error("Google account email is unavailable")
            DriveConnection(accountId = accountId, email = email, accessToken = token)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun authorizeWithoutUi(context: Context, accountEmail: String? = null): DriveConnection? =
        withContext(Dispatchers.IO) {
            val result = Tasks.await(
                getAuthorizationClient(context).authorize(buildAuthorizationRequest(accountEmail))
            )
            if (result.hasResolution()) null else resolveConnection(result)
        }

    suspend fun revoke(context: Context, accountEmail: String) = withContext(Dispatchers.IO) {
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(accountEmail, "com.google"))
            .setScopes(requestedScopes)
            .build()
        Tasks.await(getAuthorizationClient(context).revokeAccess(request))
    }

    fun getDriveService(connection: DriveConnection): Drive =
        GoogleDriveBackupManager.getDriveService(connection.accessToken)

    suspend fun uploadBackup(
        driveService: Drive,
        localFile: File,
        isAutoBackup: Boolean = false,
        retentionCount: Int = 7
    ): String? = withContext(Dispatchers.IO) {
        GoogleDriveBackupManager.uploadEncryptedBackup(
            driveService = driveService,
            localEncryptedFile = localFile,
            isAutoBackup = isAutoBackup,
            retentionCount = retentionCount
        ).fileId
    }

    suspend fun listBackups(driveService: Drive): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        GoogleDriveBackupManager.listBackups(driveService).map { record ->
            DriveBackupFile(
                id = record.fileId,
                name = record.fileName,
                size = record.fileSize,
                createdTime = record.createdTimestamp,
                isAutoBackup = record.isAutoBackup,
                formattedDate = record.formattedDate,
                formattedSize = record.formattedSize
            )
        }
    }

    suspend fun downloadBackup(driveService: Drive, fileId: String, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            GoogleDriveBackupManager.downloadBackup(driveService, fileId, outputFile)
        }

    suspend fun deleteBackup(driveService: Drive, fileId: String): Boolean = withContext(Dispatchers.IO) {
        GoogleDriveBackupManager.deleteBackup(driveService, fileId)
    }

    suspend fun testDriveConnection(context: Context, accountEmail: String? = null): Result<DriveConnection> =
        withContext(Dispatchers.IO) {
            try {
                val conn = authorizeWithoutUi(context, accountEmail)
                if (conn == null) {
                    Result.failure(Exception("Google Drive authentication requires user sign-in or authorization."))
                } else {
                    val service = getDriveService(conn)
                    service.about().get().setFields("user").execute()
                    Result.success(conn)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
