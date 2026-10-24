package com.techyfiposmeds.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a local file before letting the crash proceed, so a shop owner
 * can open Settings -> About -> Crash Logs and share the actual stack trace with support instead
 * of only ever seeing Android's generic "app has a bug" dialog.
 */
object CrashLogger {
    private const val DIR_NAME = "crash_logs"
    private const val MAX_LOGS = 10

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Logging must never be the reason the crash fails to propagate.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val fileStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$fileStamp.txt")

        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }

        val content = buildString {
            appendLine("Techyfi POS crash report")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("App version: $versionName")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stackTrace)
        }
        file.writeText(content)
        pruneOldLogs(dir)
    }

    private fun pruneOldLogs(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS)
            ?.forEach { it.delete() }
    }

    /** Most recent crash logs first. */
    fun listLogs(context: Context): List<File> {
        val dir = File(context.filesDir, DIR_NAME)
        return dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    /** First line of the stack trace after the header, i.e. the exception type + message. */
    fun summarize(file: File): String = try {
        file.readLines().drop(6).firstOrNull { it.isNotBlank() } ?: file.name
    } catch (_: Exception) {
        file.name
    }

    fun readLog(file: File): String = try {
        file.readText()
    } catch (e: Exception) {
        "Could not read log: ${e.message}"
    }

    fun deleteAllLogs(context: Context) {
        File(context.filesDir, DIR_NAME).listFiles()?.forEach { it.delete() }
    }
}
