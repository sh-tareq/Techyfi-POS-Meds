package com.example.techyfipos.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SigningCertificateHelper {
    fun sha1Fingerprint(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()
            } ?: return "Unavailable"
            MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
                .joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "Unavailable"
        }
    }
}
