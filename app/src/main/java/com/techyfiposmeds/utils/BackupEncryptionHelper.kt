package com.techyfiposmeds.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise Authenticated AES-256-GCM Encryption Helper for Techyfi POS Backups.
 * 
 * Provides:
 * - AES-256-GCM authenticated encryption with 128-bit authentication tag.
 * - PBKDF2WithHmacSHA256 (100,000 iterations) key derivation from Google Account ID + static app salt.
 * - 12-byte random cryptographic IV prepended to the ciphertext.
 * - SHA-256 checksum calculation for file integrity verification.
 */
object BackupEncryptionHelper {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    // Legacy static salt, kept only to decrypt backups created before per-file random salts
    // were introduced. A fixed salt defeats PBKDF2's purpose against offline brute force, since
    // every install derives the same key from the same (guessable) Google Account ID passphrase.
    private val LEGACY_APP_SALT = byteArrayOf(
        0x54, 0x65, 0x63, 0x68, 0x79, 0x66, 0x69, 0x50,
        0x4F, 0x53, 0x5F, 0x42, 0x61, 0x63, 0x6B, 0x75,
        0x70, 0x5F, 0x53, 0x65, 0x63, 0x75, 0x72, 0x65
    )

    /**
     * Derives a 256-bit AES key from a passphrase (e.g. Google Account ID or custom owner secret)
     */
    fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, ALGORITHM)
    }

    /**
     * Encrypts a source file into a destination file using AES-256-GCM. A fresh random 16-byte
     * salt and 12-byte IV are generated per call and prepended to the output file (salt first,
     * then IV), so the derived key differs for every backup rather than reusing one fixed salt.
     */
    suspend fun encryptFile(
        inputFile: File,
        outputFile: File,
        passphrase: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val salt = ByteArray(SALT_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
            val secretKey = deriveKey(passphrase, salt)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply {
                SecureRandom().nextBytes(this)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

            FileOutputStream(outputFile).use { fos ->
                fos.write(salt)
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(inputFile).use { fis ->
                        fis.copyTo(cos)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            false
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted file. Tries the current format (16-byte random salt +
     * 12-byte IV header) first, then falls back to the legacy fixed-salt format (12-byte IV only)
     * so backups created before per-file salts were introduced can still be restored.
     */
    suspend fun decryptFile(
        inputFile: File,
        outputFile: File,
        passphrase: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (decryptWithFormat(inputFile, outputFile, passphrase, embeddedSalt = true)) {
            return@withContext true
        }
        decryptWithFormat(inputFile, outputFile, passphrase, embeddedSalt = false)
    }

    private fun decryptWithFormat(
        inputFile: File,
        outputFile: File,
        passphrase: String,
        embeddedSalt: Boolean
    ): Boolean {
        return try {
            FileInputStream(inputFile).use { fis ->
                val salt = if (embeddedSalt) {
                    ByteArray(SALT_LENGTH_BYTES).also {
                        if (fis.read(it) != SALT_LENGTH_BYTES) {
                            throw IllegalStateException("Invalid encrypted file: salt header is missing or corrupted.")
                        }
                    }
                } else {
                    LEGACY_APP_SALT
                }
                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                if (fis.read(iv) != GCM_IV_LENGTH_BYTES) {
                    throw IllegalStateException("Invalid encrypted file: IV header is missing or corrupted.")
                }

                val secretKey = deriveKey(passphrase, salt)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(outputFile).use { fos ->
                        cis.copyTo(fos)
                    }
                }
            }
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        }
    }

    /**
     * Calculates the SHA-256 hex digest of a file for integrity verification.
     */
    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
