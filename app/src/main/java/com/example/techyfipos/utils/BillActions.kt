package com.example.techyfipos.utils

import com.example.techyfipos.R
import com.example.techyfipos.data.InvoiceSettingsData

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

const val WHATSAPP_PACKAGE = "com.whatsapp"
const val MESSENGER_PACKAGE = "com.facebook.orca"

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    val act = com.example.techyfipos.MainActivity.currentActivity?.get()
    if (act != null && !act.isFinishing && !act.isDestroyed) {
        return act
    }
    return null
}

/**
 * Opens system print dialog using a custom PrintDocumentAdapter that
 * sets the page size to exactly match the receipt image dimensions.
 * This eliminates extra white space on 80mm thermal roll printers.
 *
 * 80mm roll = ~226 points (1 point = 1/72 inch, 80mm ≈ 3.15 inch ≈ 226.77pt).
 * The page height is derived from the image aspect ratio so the printer
 * feeds only exactly enough paper for the content.
 */
fun printBillJpeg(context: Context, imageFile: File, jobName: String, paperRollWidthMm: Int = 80) {
    try {
        val activity = context.findActivity()
        val printManager = (activity?.getSystemService(Context.PRINT_SERVICE)
            ?: context.getSystemService(Context.PRINT_SERVICE)
            ?: context.applicationContext.getSystemService(Context.PRINT_SERVICE)) as? PrintManager

        if (printManager == null) {
            Toast.makeText(context, context.getString(R.string.print_service_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            Toast.makeText(context, context.getString(R.string.print_image_load_failed), Toast.LENGTH_SHORT).show()
            return
        }

        // 80mm in points: 80mm / 25.4 * 72 ≈ 226.77 → 3150 mils
        // 58mm in points: 58mm / 25.4 * 72 ≈ 164.41 → 2283 mils
        val pageWidthMils = if (paperRollWidthMm == 58) 2283 else 3150
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val pageHeightMils = (pageWidthMils * aspectRatio).toInt()

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                try {
                    // Convert mils to points for the PDF page: 1 point = 1/72 inch = 1000/72 mils
                    val pageWidthPt = (pageWidthMils / 1000.0 * 72.0).toInt()
                    val pageHeightPt = (pageHeightMils / 1000.0 * 72.0).toInt()

                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, 0).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // Scale bitmap to fill the entire page with no margins
                    val scaleX = pageWidthPt.toFloat() / bitmap.width.toFloat()
                    val scaleY = pageHeightPt.toFloat() / bitmap.height.toFloat()
                    val matrix = Matrix()
                    matrix.setScale(scaleX, scaleY)
                    canvas.drawBitmap(bitmap, matrix, null)

                    pdfDocument.finishPage(page)

                    destination?.let { fd ->
                        FileOutputStream(fd.fileDescriptor).use { out ->
                            pdfDocument.writeTo(out)
                        }
                    }
                    pdfDocument.close()
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                    e.printStackTrace()
                }
            }
        }

        // Build print attributes with custom page size matching receipt dimensions
        val customSize = PrintAttributes.MediaSize("RECEIPT_80MM", "80mm Receipt",
            pageWidthMils, pageHeightMils)
        val attrs = PrintAttributes.Builder()
            .setMediaSize(customSize)
            .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0)) // Zero margins
            .setResolution(PrintAttributes.Resolution("receipt", "Receipt", 203, 203)) // 203 DPI typical for thermal
            .build()

        printManager.print(jobName, adapter, attrs)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, context.getString(R.string.print_error_message, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
    }
}

fun pdfMediaSizeMils(widthMm: Float, heightMm: Float): Pair<Int, Int> =
    (widthMm.coerceAtLeast(1f) / 25.4f * 1000f).roundToInt() to
        (heightMm.coerceAtLeast(1f) / 25.4f * 1000f).roundToInt()

fun isPdfFile(file: File): Boolean {
    if (!file.isFile || file.length() < 5L) return false
    return try {
        FileInputStream(file).use { input ->
            val header = ByteArray(5)
            input.read(header) == header.size && header.contentEquals("%PDF-".toByteArray())
        }
    } catch (_: Exception) {
        false
    }
}

fun printPdfDocument(
    context: Context,
    pdfFile: File,
    jobName: String,
    widthMm: Float? = null,
    heightMm: Float? = null
): Boolean {
    try {
        val activity = context.findActivity()
        val printManager = (activity?.getSystemService(Context.PRINT_SERVICE)
            ?: context.getSystemService(Context.PRINT_SERVICE)
            ?: context.applicationContext.getSystemService(Context.PRINT_SERVICE)) as? PrintManager

        if (printManager == null) {
            Toast.makeText(context, context.getString(R.string.print_service_unavailable), Toast.LENGTH_SHORT).show()
            return false
        }
        if (!isPdfFile(pdfFile)) {
            Toast.makeText(context, context.getString(R.string.print_invalid_pdf), Toast.LENGTH_SHORT).show()
            return false
        }

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                callback?.onLayoutFinished(
                    PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build(),
                    true
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                val output = destination
                if (output == null) {
                    callback?.onWriteFailed("Print destination is unavailable")
                    return
                }
                Thread {
                    try {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            output.close()
                            return@Thread
                        }
                        FileInputStream(pdfFile).use { input ->
                            ParcelFileDescriptor.AutoCloseOutputStream(output).use { target ->
                                input.copyTo(target)
                            }
                        }
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                        } else {
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        }
                    } catch (error: Exception) {
                        callback?.onWriteFailed(error.localizedMessage ?: "Unable to write print document")
                    }
                }.start()
            }
        }

        val attributes = PrintAttributes.Builder()
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .apply {
                if (widthMm != null && heightMm != null) {
                    val (widthMils, heightMils) = pdfMediaSizeMils(widthMm, heightMm)
                    setMediaSize(
                        PrintAttributes.MediaSize(
                            "TECHYFI_LABEL_${widthMils}X${heightMils}",
                            "${widthMm.toCleanMillimetres()} × ${heightMm.toCleanMillimetres()} mm Label",
                            widthMils,
                            heightMils
                        )
                    )
                    setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    setResolution(PrintAttributes.Resolution("label_203dpi", "203 dpi", 203, 203))
                }
            }
            .build()
        printManager.print(jobName, adapter, attributes)
        return true
    } catch (error: Exception) {
        error.printStackTrace()
        Toast.makeText(context, context.getString(R.string.toast_print_error_fmt, error.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
        return false
    }
}

private fun Float.toCleanMillimetres(): String =
    if (this % 1f == 0f) toInt().toString() else toString()

fun printBillPdf(context: Context, pdfFile: File, jobName: String): Boolean =
    printPdfDocument(context, pdfFile, jobName)

/**
 * Copies the invoice JPEG into the public Pictures/Invoices or Downloads folder. Returns true on success.
 */
fun saveBillJpegToDownloads(context: Context, imageFile: File): Boolean {
    val fileName = if (imageFile.nameWithoutExtension.contains("repayment", ignoreCase = true)) {
        "Repayment_Receipt_${System.currentTimeMillis()}.jpg"
    } else {
        "Invoice_${imageFile.nameWithoutExtension}_${System.currentTimeMillis()}.jpg"
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Try MediaStore Images first
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Invoices")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(imageFile).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try MediaStore Downloads as fallback
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(imageFile).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fallback for pre-Q or direct file write
    return try {
        val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Invoices")
        if (!targetDir.exists()) targetDir.mkdirs()
        val outFile = File(targetDir, fileName)
        FileInputStream(imageFile).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        // Final fallback: save in app's external files dir
        try {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val outFile = File(appDir, fileName)
            FileInputStream(imageFile).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            true
        } catch (e2: Exception) {
            e2.printStackTrace()
            false
        }
    }
}

fun saveBillPdfToDownloads(context: Context, pdfFile: File): Boolean {
    if (!isPdfFile(pdfFile)) return false
    val fileName = "Invoice_${pdfFile.nameWithoutExtension}_${System.currentTimeMillis()}.pdf"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Invoices")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(pdfFile).use { input -> input.copyTo(output) }
                } ?: return false
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return true
            }
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    return try {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        FileInputStream(pdfFile).use { input ->
            FileOutputStream(File(targetDir, fileName)).use { output -> input.copyTo(output) }
        }
        true
    } catch (error: Exception) {
        error.printStackTrace()
        false
    }
}

/** Puts the invoice JPEG onto the system clipboard as a file Uri. */
fun copyImageUriToClipboard(context: Context, imageUri: Uri, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newUri(context.contentResolver, label, imageUri)
    clipboard.setPrimaryClip(clip)
}

fun copyPdfUriToClipboard(context: Context, pdfUri: Uri, label: String) = copyImageUriToClipboard(context, pdfUri, label)

/** Generic share-sheet with the invoice JPEG attached. */
fun shareBillJpeg(context: Context, imageUri: Uri, targetPackage: String? = null): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        clipData = ClipData.newUri(context.contentResolver, "Invoice JPEG", imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (targetPackage != null) setPackage(targetPackage)
    }
    val target = context.findActivity() ?: context
    return try {
        val launchIntent = if (targetPackage == null) {
            Intent.createChooser(intent, "Share Invoice Via").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            intent
        }
        target.startActivity(launchIntent)
        true
    } catch (e: ActivityNotFoundException) {
        val appName = when (targetPackage) {
            WHATSAPP_PACKAGE -> "WhatsApp"
            MESSENGER_PACKAGE -> "Messenger"
            else -> "the selected app"
        }
        Toast.makeText(context, context.getString(R.string.app_not_installed_error, appName), Toast.LENGTH_SHORT).show()
        false
    }
}

fun shareBillPdfAndJpeg(context: Context, pdfUri: Uri, jpegUri: Uri): Boolean {
    val target = context.findActivity() ?: context
    val streams = arrayListOf(pdfUri, jpegUri)
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
        clipData = ClipData.newUri(context.contentResolver, "Invoice PDF", pdfUri).apply {
            addItem(ClipData.Item(jpegUri))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        val chooser = Intent.createChooser(intent, "Share Invoice PDF + JPEG").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        target.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.pdf_app_not_installed), Toast.LENGTH_SHORT).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(context, context.getString(R.string.invoice_share_blocked), Toast.LENGTH_SHORT).show()
        false
    }
}

fun shareBillPdf(context: Context, pdfUri: Uri, targetPackage: String? = null): Boolean {
    val target = context.findActivity() ?: context
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        clipData = ClipData.newUri(context.contentResolver, "Invoice PDF", pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (targetPackage != null) setPackage(targetPackage)
    }
    return try {
        val launchIntent = if (targetPackage == null) {
            Intent.createChooser(intent, "Share Invoice PDF Via").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            intent
        }
        target.startActivity(launchIntent)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.pdf_app_not_installed), Toast.LENGTH_SHORT).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(context, context.getString(R.string.invoice_share_blocked), Toast.LENGTH_SHORT).show()
        false
    }
}

fun shareReceiptAsText(context: Context, text: String): Boolean {
    val target = context.findActivity() ?: context
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        val chooser = Intent.createChooser(intent, "Share Receipt Via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        target.startActivity(chooser)
        true
    } catch (_: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Due Reminder", text))
        Toast.makeText(context, context.getString(R.string.message_copied_clipboard), Toast.LENGTH_SHORT).show()
        false
    }
}

/** Opens the system dialer, or copies the phone number when no dialer is available. */
fun openPhoneDialer(context: Context, phone: String): Boolean {
    val normalizedPhone = phone.trim()
    if (normalizedPhone.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.no_phone_number_available), Toast.LENGTH_SHORT).show()
        return false
    }

    val target = context.findActivity() ?: context
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", normalizedPhone, null)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        target.startActivity(intent)
        true
    } catch (_: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Customer phone", normalizedPhone))
        Toast.makeText(context, context.getString(R.string.no_dialer_found_phone_copied), Toast.LENGTH_LONG).show()
        false
    }
}

/** Opens an addressed SMS reminder and falls back to the general share sheet. */
fun openPaymentReminder(context: Context, phone: String, message: String): Boolean {
    val normalizedPhone = phone.trim()
    if (normalizedPhone.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.no_phone_number_available), Toast.LENGTH_SHORT).show()
        return false
    }

    val target = context.findActivity() ?: context
    // 1. Try smsto: URI
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$normalizedPhone")).apply {
        putExtra("sms_body", message)
        putExtra(Intent.EXTRA_TEXT, message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        target.startActivity(intent)
        true
    } catch (_: Exception) {
        // 2. Try sms: URI with body parameter
        try {
            val smsUri = Uri.parse("sms:$normalizedPhone?body=${Uri.encode(message)}")
            val viewIntent = Intent(Intent.ACTION_VIEW, smsUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            target.startActivity(viewIntent)
            true
        } catch (_: Exception) {
            shareReceiptAsText(context, message)
        }
    }
}

/** Opens WhatsApp chat with prefilled payment reminder message. */
fun openWhatsAppReminder(context: Context, phone: String, message: String): Boolean {
    val rawDigits = phone.replace(Regex("[^0-9+]"), "")
    if (rawDigits.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.no_phone_number_available), Toast.LENGTH_SHORT).show()
        return false
    }
    val international = when {
        rawDigits.startsWith("+") -> rawDigits.substring(1)
        rawDigits.startsWith("880") -> rawDigits
        rawDigits.startsWith("0") -> "88$rawDigits"
        else -> "880$rawDigits"
    }
    val target = context.findActivity() ?: context
    // 1. Try whatsapp://send URI
    return try {
        val directUri = Uri.parse("whatsapp://send?phone=$international&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, directUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        target.startActivity(intent)
        true
    } catch (_: Exception) {
        // 2. Try https://api.whatsapp.com/send Web API URI (works in browser & WhatsApp)
        try {
            val webUri = Uri.parse("https://api.whatsapp.com/send?phone=$international&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            target.startActivity(intent)
            true
        } catch (_: Exception) {
            // 3. Fallback to standard share sheet or clipboard
            shareReceiptAsText(context, message)
        }
    }
}

/** Opens WhatsApp chat directly for a customer phone with resilient 3-tier fallback. */
fun openWhatsAppChat(context: Context, phone: String): Boolean {
    val rawDigits = phone.replace(Regex("[^0-9+]"), "")
    if (rawDigits.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.no_phone_number_available), Toast.LENGTH_SHORT).show()
        return false
    }
    val international = when {
        rawDigits.startsWith("+") -> rawDigits.substring(1)
        rawDigits.startsWith("880") -> rawDigits
        rawDigits.startsWith("0") -> "88$rawDigits"
        else -> "880$rawDigits"
    }
    val target = context.findActivity() ?: context
    // 1. Try whatsapp://send URI
    return try {
        val directUri = Uri.parse("whatsapp://send?phone=$international")
        val intent = Intent(Intent.ACTION_VIEW, directUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        target.startActivity(intent)
        true
    } catch (_: Exception) {
        // 2. Try https://api.whatsapp.com/send Web API URI (works in browser & WhatsApp)
        try {
            val webUri = Uri.parse("https://api.whatsapp.com/send?phone=$international")
            val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            target.startActivity(intent)
            true
        } catch (_: Exception) {
            // 3. Fallback to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Customer Phone", phone.trim()))
            Toast.makeText(context, context.getString(R.string.toast_phone_copied_whatsapp_unavailable), Toast.LENGTH_SHORT).show()
            false
        }
    }
}


/**
 * Standard ESC/POS Cash Drawer Kick pulse command:
 * ESC p m t1 t2 = [0x1B, 0x70, 0x00, 0x19, 0xFA]
 * (Connector Pin 2, 50ms pulse, 500ms off)
 */
fun getCashDrawerKickCommand(): ByteArray = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

private const val SERIAL_PORT_PROFILE_UUID = "00001101-0000-1000-8000-00805F9B34FB"

/**
 * Sends the ESC/POS drawer-kick pulse over a Bluetooth SPP socket to [printerAddress], the MAC
 * address of an already-paired thermal printer (paired via Android's own Bluetooth settings).
 * Must run off the main thread — RFCOMM connect/write are blocking calls.
 */
@SuppressLint("MissingPermission")
suspend fun triggerCashDrawerKick(context: Context, printerAddress: String) = withContext(Dispatchers.IO) {
    suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    if (printerAddress.isBlank()) {
        toast("⚠️ No cash drawer printer paired. Set one in Settings.")
        return@withContext
    }

    val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!hasPermission) {
        toast("⚠️ Bluetooth permission required to open the cash drawer")
        return@withContext
    }

    try {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
        val device = adapter?.bondedDevices?.firstOrNull { it.address == printerAddress }
        if (adapter == null || device == null) {
            toast("⚠️ Paired printer not found. Re-select it in Settings.")
            return@withContext
        }
        adapter.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(java.util.UUID.fromString(SERIAL_PORT_PROFILE_UUID))
        socket.use {
            it.connect()
            it.outputStream.write(getCashDrawerKickCommand())
            it.outputStream.flush()
        }
        toast("⚡ Cash drawer kick sent")
    } catch (e: Exception) {
        android.util.Log.e("CashDrawerKick", "Failed to send drawer kick to $printerAddress", e)
        toast("⚠️ Could not reach the cash drawer printer")
    }
}

/**
 * Prints receipt bitmap directly via Bluetooth ESC/POS without launching system print dialog.
 * If direct printing fails or printer is not configured, it can fall back to standard system print.
 */
suspend fun printReceiptDirectBluetooth(
    context: Context,
    bitmap: Bitmap,
    printerAddress: String,
    paperRollWidthMm: Int = 80,
    autoKickDrawer: Boolean = false
): Boolean {
    val dots = if (paperRollWidthMm == 58) 384 else 576
    return com.example.techyfipos.printer.EscPosPrinterDriver.printReceiptDirect(
        context = context,
        printerAddress = printerAddress,
        bitmap = bitmap,
        paperWidthDots = dots,
        autoKickDrawer = autoKickDrawer
    )
}

/**
 * Smart print function that first attempts direct ESC/POS Bluetooth printing
 * if a Bluetooth printer address is configured in InvoiceSettings.
 * If not configured or direct printing fails, falls back gracefully to standard Android OS print spooler.
 */
suspend fun printReceiptWithBluetoothFallback(
    context: Context,
    activity: Activity?,
    bill: BillData,
    settings: InvoiceSettingsData,
    receiptLanguage: String = "EN"
): Boolean {
    val printerAddress = settings.directBluetoothPrinterAddress.trim()
    if (printerAddress.isNotBlank()) {
        try {
            val jpegFile = kotlinx.coroutines.withContext(Dispatchers.IO) {
                generateBillJpeg(context, bill, receiptLanguage)
            }
            val bitmap = BitmapFactory.decodeFile(jpegFile.absolutePath)
            if (bitmap != null) {
                val printed = printReceiptDirectBluetooth(
                    context = context,
                    bitmap = bitmap,
                    printerAddress = printerAddress,
                    paperRollWidthMm = settings.paperRollWidthMm,
                    autoKickDrawer = settings.cashDrawerKickEnabled
                )
                if (printed) {
                    return true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BillActions", "Direct Bluetooth print failed, falling back to OS print dialog", e)
        }
    }
    // Fallback to standard PDF print dialog
    val pdfFile = kotlinx.coroutines.withContext(Dispatchers.IO) {
        generateBillPdf(context, bill, receiptLanguage = receiptLanguage)
    }
    return printBillPdf(activity ?: context, pdfFile, "Invoice #${bill.saleId}")
}

/**
 * Smart print function for Store Credit Voucher slip.
 * Attempts direct ESC/POS Bluetooth printing if configured, otherwise falls back to system print.
 */
suspend fun printStoreCreditVoucherSlip(
    context: Context,
    voucherFile: File,
    voucherCode: String,
    settings: InvoiceSettingsData
): Boolean {
    val printerAddress = settings.directBluetoothPrinterAddress.trim()
    if (printerAddress.isNotBlank()) {
        try {
            val bitmap = BitmapFactory.decodeFile(voucherFile.absolutePath)
            if (bitmap != null) {
                val printed = printReceiptDirectBluetooth(
                    context = context,
                    bitmap = bitmap,
                    printerAddress = printerAddress,
                    paperRollWidthMm = settings.paperRollWidthMm,
                    autoKickDrawer = false // Do not kick drawer for voucher slip
                )
                if (printed) return true
            }
        } catch (e: Exception) {
            android.util.Log.w("BillActions", "Direct Bluetooth print of voucher slip failed, falling back to OS print", e)
        }
    }
    // Fallback to system print dialog
    printBillJpeg(context, voucherFile, "Voucher-$voucherCode", paperRollWidthMm = settings.paperRollWidthMm)
    return true
}

/**
 * Shares the Store Credit Voucher slip image via Android system share sheet.
 */
fun shareStoreCreditVoucherSlip(
    context: Context,
    voucherFile: File,
    voucherCode: String,
    voucherAmount: Double
): Boolean {
    val uri = getBillImageUri(context, voucherFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Store Credit Voucher $voucherCode")
        putExtra(Intent.EXTRA_TEXT, "Store Credit Voucher: $voucherCode\nAmount: ${formatTaka(voucherAmount)}")
        clipData = ClipData.newUri(context.contentResolver, "Voucher Slip", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val target = context.findActivity() ?: context
    return try {
        val chooser = Intent.createChooser(intent, "Share Store Credit Voucher Via").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        target.startActivity(chooser)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, context.getString(R.string.toast_could_not_open_share_menu), Toast.LENGTH_SHORT).show()
        false
    }
}

/**
 * Shares Store Credit Voucher details via WhatsApp.
 */
fun shareStoreCreditVoucherWhatsApp(
    context: Context,
    phone: String,
    message: String
): Boolean {
    return openWhatsAppReminder(context, phone, message)
}

/**
 * Shares Store Credit Voucher details via SMS or system text share sheet.
 */
fun shareStoreCreditVoucherText(
    context: Context,
    phone: String?,
    message: String
): Boolean {
    return if (!phone.isNullOrBlank()) {
        openPaymentReminder(context, phone, message)
    } else {
        shareReceiptAsText(context, message)
    }
}

