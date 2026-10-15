package com.example.techyfipos.utils

import com.example.techyfipos.R
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.printer.PrintTiming
import com.example.techyfipos.printer.DirectPrintResult
import com.example.techyfipos.printer.EscPosPrinterDriver
import com.example.techyfipos.printer.LabelJob
import com.example.techyfipos.printer.PrinterTarget
import com.example.techyfipos.printer.ReceiptBitmapCache
import com.example.techyfipos.printer.ReceiptCutMode
import com.example.techyfipos.printer.TsplLabelPrinterDriver
import com.example.techyfipos.printer.isSamePrinter
import com.example.techyfipos.printer.isSamePrinterTarget
import com.example.techyfipos.printer.sendToBluetoothPrinter
import com.example.techyfipos.printer.sendToPrinter

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
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.util.Log
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
fun printBillJpeg(context: Context, imageFile: File, jobName: String, paperRollWidthMm: Int = 80): Boolean {
    try {
        val activity = context.findActivity()
        val printManager = (activity?.getSystemService(Context.PRINT_SERVICE)
            ?: context.getSystemService(Context.PRINT_SERVICE)
            ?: context.applicationContext.getSystemService(Context.PRINT_SERVICE)) as? PrintManager

        if (printManager == null) {
            Toast.makeText(context, context.getString(R.string.print_service_unavailable), Toast.LENGTH_SHORT).show()
            return false
        }
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Toast.makeText(context, context.getString(R.string.print_image_load_failed), Toast.LENGTH_SHORT).show()
            return false
        }

        // 80mm in points: 80mm / 25.4 * 72 ≈ 226.77 → 3150 mils
        // 58mm in points: 58mm / 25.4 * 72 ≈ 164.41 → 2283 mils
        val pageWidthMils = if (paperRollWidthMm == 58) 2283 else 3150
        val aspectRatio = bounds.outHeight.toFloat() / bounds.outWidth.toFloat()
        val pageHeightMils = (pageWidthMils * aspectRatio).toInt().coerceAtLeast(1)

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
                val output = destination
                if (output == null) {
                    callback?.onWriteFailed(context.getString(R.string.print_destination_unavailable))
                    return
                }
                Thread {
                    var bmp: Bitmap? = null
                    try {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            output.close()
                            return@Thread
                        }
                        bmp = BitmapFactory.decodeFile(imageFile.absolutePath)
                        if (bmp == null) {
                            callback?.onWriteFailed(context.getString(R.string.print_could_not_decode_receipt_image))
                            output.close()
                            return@Thread
                        }

                        val pageWidthPt = (pageWidthMils / 1000.0 * 72.0).toInt().coerceAtLeast(1)
                        val pageHeightPt = (pageHeightMils / 1000.0 * 72.0).toInt().coerceAtLeast(1)

                        val pdfDocument = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, 0).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas = page.canvas

                        // Scale bitmap to fill the entire page with no margins
                        val scaleX = pageWidthPt.toFloat() / bmp.width.toFloat()
                        val scaleY = pageHeightPt.toFloat() / bmp.height.toFloat()
                        val matrix = Matrix().apply { setScale(scaleX, scaleY) }
                        canvas.drawBitmap(bmp, matrix, null)

                        pdfDocument.finishPage(page)

                        FileOutputStream(output.fileDescriptor).use { out ->
                            pdfDocument.writeTo(out)
                        }
                        pdfDocument.close()
                        output.close()
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.localizedMessage)
                        try { output.close() } catch (_: Exception) {}
                        e.printStackTrace()
                    } finally {
                        bmp?.recycle()
                    }
                }.start()
            }
        }

        // Build print attributes with custom page size matching receipt dimensions
        val customSize = PrintAttributes.MediaSize("RECEIPT_${paperRollWidthMm}MM", "${if (paperRollWidthMm == 58) 58 else 80}mm Receipt",
            pageWidthMils, pageHeightMils)
        val attrs = PrintAttributes.Builder()
            .setMediaSize(customSize)
            .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0)) // Zero margins
            .setResolution(PrintAttributes.Resolution("receipt", "Receipt", 203, 203)) // 203 DPI typical for thermal
            .build()

        val printJob = printManager.print(jobName, adapter, attrs)
        return printJob != null
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, context.getString(R.string.print_error_message, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
        return false
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
                val pageCount = try {
                    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            renderer.pageCount
                        }
                    }
                } catch (e: Exception) {
                    PrintDocumentInfo.PAGE_COUNT_UNKNOWN
                }
                callback?.onLayoutFinished(
                    PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(pageCount)
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
                    callback?.onWriteFailed(context.getString(R.string.print_destination_unavailable))
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
                        callback?.onWriteFailed(context.getString(R.string.print_error_message, error.localizedMessage ?: ""))
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

sealed class FileSaveResult {
    data class PublicSaved(val path: String = "") : FileSaveResult()
    data class PrivateFallback(val path: String) : FileSaveResult()
    object Failed : FileSaveResult()

    val isSuccessful: Boolean get() = this is PublicSaved || this is PrivateFallback
}

/**
 * Copies the invoice JPEG into the public Pictures/Invoices or Downloads folder.
 * Returns FileSaveResult indicating whether saved publicly, fell back to private storage, or failed.
 */
fun saveBillJpegToDownloads(context: Context, imageFile: File): FileSaveResult {
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
                val copied = context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(imageFile).use { input -> input.copyTo(out) }
                }
                if (copied != null) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return FileSaveResult.PublicSaved("${Environment.DIRECTORY_PICTURES}/Invoices/$fileName")
                }
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
                val copied = context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(imageFile).use { input -> input.copyTo(out) }
                }
                if (copied != null) {
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return FileSaveResult.PublicSaved("${Environment.DIRECTORY_DOWNLOADS}/$fileName")
                }
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
        FileSaveResult.PublicSaved(outFile.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        // Final fallback: save in app's external files dir
        try {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val outFile = File(appDir, fileName)
            FileInputStream(imageFile).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            FileSaveResult.PrivateFallback(outFile.absolutePath)
        } catch (e2: Exception) {
            e2.printStackTrace()
            FileSaveResult.Failed
        }
    }
}

fun saveBillPdfToDownloads(context: Context, pdfFile: File): FileSaveResult {
    if (!isPdfFile(pdfFile)) return FileSaveResult.Failed
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
                val copied = context.contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(pdfFile).use { input -> input.copyTo(output) }
                }
                if (copied != null) {
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return FileSaveResult.PublicSaved("${Environment.DIRECTORY_DOWNLOADS}/Invoices/$fileName")
                }
            }
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    return try {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val outFile = File(targetDir, fileName)
        FileInputStream(pdfFile).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        FileSaveResult.PrivateFallback(outFile.absolutePath)
    } catch (error: Exception) {
        error.printStackTrace()
        FileSaveResult.Failed
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
        clipData = ClipData.newUri(context.contentResolver, context.getString(R.string.clip_invoice_jpeg), imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (targetPackage != null) setPackage(targetPackage)
    }
    val target = context.findActivity() ?: context
    return try {
        val launchIntent = if (targetPackage == null) {
            Intent.createChooser(intent, context.getString(R.string.share_invoice_via_chooser)).apply {
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
        clipData = ClipData.newUri(context.contentResolver, context.getString(R.string.clip_invoice_pdf), pdfUri).apply {
            addItem(ClipData.Item(jpegUri))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_invoice_pdf_jpeg_chooser)).apply {
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
        clipData = ClipData.newUri(context.contentResolver, context.getString(R.string.clip_invoice_pdf), pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (targetPackage != null) setPackage(targetPackage)
    }
    return try {
        val launchIntent = if (targetPackage == null) {
            Intent.createChooser(intent, context.getString(R.string.share_invoice_pdf_via_chooser)).apply {
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
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_receipt_via_chooser)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        target.startActivity(chooser)
        true
    } catch (_: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_due_reminder), text))
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
        clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_customer_phone), normalizedPhone))
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
            clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_customer_phone), phone.trim()))
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

enum class RoutingDecision {
    DIRECT_OK,
    SHOW_ERROR,
    SYSTEM_DIALOG,
    SHOW_ERROR_THEN_SYSTEM_DIALOG
}

/**
 * Pure function determining the routing decision based on configured mode,
 * printer address, direct printing result, and partial success count.
 */
fun decidePrintRouting(
    mode: String,
    address: String,
    directResult: DirectPrintResult?,
    partialSuccessCount: Int = 0
): RoutingDecision {
    val trimmedAddress = address.trim()
    val normalizedMode = mode.trim().uppercase()
    return when (normalizedMode) {
        "SYSTEM" -> RoutingDecision.SYSTEM_DIALOG
        "BLUETOOTH", "USB" -> {
            if (trimmedAddress.isBlank() || directResult !is DirectPrintResult.Success) {
                RoutingDecision.SHOW_ERROR
            } else {
                RoutingDecision.DIRECT_OK
            }
        }
        else -> { // "AUTO"
            if (trimmedAddress.isBlank()) {
                RoutingDecision.SYSTEM_DIALOG
            } else if (directResult is DirectPrintResult.Success) {
                RoutingDecision.DIRECT_OK
            } else if (partialSuccessCount > 0) {
                // Do not fall back to system dialog if some items were already printed directly;
                // reprinting the entire batch in the system dialog causes duplicates.
                RoutingDecision.SHOW_ERROR
            } else {
                RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG
            }
        }
    }
}

/**
 * Displays a localized Toast message explaining why direct printing failed.
 */
suspend fun showDirectPrintToast(context: Context, result: DirectPrintResult) = withContext(Dispatchers.Main) {
    val message = when (result) {
        is DirectPrintResult.Success -> return@withContext
        is DirectPrintResult.NoAddress -> context.getString(R.string.direct_print_error_no_address)
        is DirectPrintResult.NoPermission -> context.getString(R.string.direct_print_error_no_permission)
        is DirectPrintResult.BluetoothOff -> context.getString(R.string.direct_print_error_bluetooth_off)
        is DirectPrintResult.DeviceNotPaired -> context.getString(R.string.direct_print_error_not_paired)
        is DirectPrintResult.UsbDeviceNotFound -> context.getString(R.string.direct_print_error_usb_device_not_found)
        is DirectPrintResult.UsbNoPermission -> context.getString(R.string.direct_print_error_usb_no_permission)
        is DirectPrintResult.UsbNoPrinterInterface -> context.getString(R.string.direct_print_error_usb_no_printer_interface)
        is DirectPrintResult.UsbNotSupported -> context.getString(R.string.direct_print_error_usb_not_supported)
        is DirectPrintResult.UsbOpenFailed -> context.getString(R.string.direct_print_error_usb_open_failed)
        is DirectPrintResult.UsbClaimInterfaceFailed -> context.getString(R.string.direct_print_error_usb_claim_failed)
        is DirectPrintResult.UsbTransferFailed -> context.getString(R.string.direct_print_error_usb_transfer_failed, result.code)
        is DirectPrintResult.UsbCommunicationError -> context.getString(R.string.direct_print_error_usb_comm, result.reason)
        is DirectPrintResult.Failed -> context.getString(R.string.direct_print_error_connection_failed, result.message)
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/**
 * Sends the ESC/POS drawer-kick pulse over Bluetooth SPP socket or USB bulk endpoint.
 */
@SuppressLint("MissingPermission")
suspend fun triggerCashDrawerKick(context: Context, target: PrinterTarget) = withContext(Dispatchers.IO) {
    suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    if (target is PrinterTarget.None || target.identifier.isBlank()) {
        toast(context.getString(R.string.cash_drawer_no_printer_paired))
        return@withContext
    }

    val result = sendToPrinter(context, target, listOf(getCashDrawerKickCommand()))
    withContext(Dispatchers.Main) {
        when (result) {
            is DirectPrintResult.Success -> toast(context.getString(R.string.cash_drawer_kick_sent))
            else -> showDirectPrintToast(context, result)
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun triggerCashDrawerKick(context: Context, printerAddress: String) {
    val target = when {
        printerAddress.trim().startsWith("usb:", ignoreCase = true) -> PrinterTarget.Usb(printerAddress.trim())
        printerAddress.isNotBlank() -> PrinterTarget.Bluetooth(printerAddress.trim())
        else -> PrinterTarget.None
    }
    triggerCashDrawerKick(context, target)
}

/**
 * Prints receipt bitmap directly via ESC/POS without launching system print dialog.
 */
suspend fun printReceiptDirect(
    context: Context,
    bitmap: Bitmap,
    target: PrinterTarget,
    paperRollWidthMm: Int = 80,
    autoKickDrawer: Boolean = false,
    darkness: Int = 8,
    cutMode: String = "PARTIAL",
    feedLines: Int = 4,
    cutCommandStyle: String = "GS_V"
): Boolean {
    val dots = if (paperRollWidthMm == 58) 384 else 576
    val res = EscPosPrinterDriver.printReceiptDirect(
        context = context,
        target = target,
        bitmap = bitmap,
        paperWidthDots = dots,
        autoKickDrawer = autoKickDrawer,
        darkness = darkness,
        cutMode = ReceiptCutMode.fromString(cutMode),
        feedLines = feedLines,
        cutCommandStyle = cutCommandStyle
    )
    return res is DirectPrintResult.Success
}

suspend fun printReceiptDirectBluetooth(
    context: Context,
    bitmap: Bitmap,
    printerAddress: String,
    paperRollWidthMm: Int = 80,
    autoKickDrawer: Boolean = false,
    darkness: Int = 8,
    cutMode: String = "PARTIAL",
    feedLines: Int = 4,
    cutCommandStyle: String = "GS_V"
): Boolean {
    val target = if (printerAddress.trim().startsWith("usb:", ignoreCase = true)) {
        PrinterTarget.Usb(printerAddress.trim())
    } else if (printerAddress.isNotBlank()) {
        PrinterTarget.Bluetooth(printerAddress.trim())
    } else {
        PrinterTarget.None
    }
    return printReceiptDirect(
        context = context,
        bitmap = bitmap,
        target = target,
        paperRollWidthMm = paperRollWidthMm,
        autoKickDrawer = autoKickDrawer,
        darkness = darkness,
        cutMode = cutMode,
        feedLines = feedLines,
        cutCommandStyle = cutCommandStyle
    )
}

/**
 * Unified entry point for printing receipt images.
 * Respects receiptPrintMode (BLUETOOTH, SYSTEM, AUTO) and provides graceful fallback.
 */
suspend fun printReceiptImage(
    context: Context,
    activity: Activity?,
    imageFile: File?,
    jobName: String,
    settings: InvoiceSettingsData,
    kickDrawer: Boolean,
    timing: PrintTiming? = null,
    cachedBitmap: Bitmap? = null,
    billForFallback: BillData? = null,
    receiptLanguage: String = "EN"
): Boolean {
    val effectiveTiming = timing ?: PrintTiming()
    val mode = settings.receiptPrintMode.trim().uppercase()
    val target = settings.resolvedReceiptPrinterTarget()

    val resolveFallbackFile: suspend () -> File? = {
        imageFile ?: if (billForFallback != null) {
            try {
                withContext(Dispatchers.IO) {
                    generateBillJpeg(context, billForFallback, receiptLanguage)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("BillActions", "Failed to generate bill fallback JPEG", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.print_could_not_decode_receipt_image), Toast.LENGTH_SHORT).show()
                }
                null
            }
        } else null
    }

    if (mode == "SYSTEM") {
        Log.d(
            "BillActions",
            "printReceiptImage: route=system, target=${target::class.simpleName}(${target.identifier}), cutMode=${settings.receiptCutMode}, cutStyle=${settings.receiptCutCommandStyle}, feedLines=${settings.receiptFeedLinesBeforeCut}, jobName=$jobName"
        )
        val fileToPrint = resolveFallbackFile()
        if (fileToPrint != null) {
            withContext(Dispatchers.Main) {
                printBillJpeg(activity ?: context, fileToPrint, jobName, settings.paperRollWidthMm)
            }
        }
        return false
    }

    Log.d(
        "BillActions",
        "printReceiptImage: route=direct, target=${target::class.simpleName}(${target.identifier}), cutMode=${settings.receiptCutMode}, cutStyle=${settings.receiptCutCommandStyle}, feedLines=${settings.receiptFeedLinesBeforeCut}, jobName=$jobName"
    )

    if (target is PrinterTarget.None || target.identifier.isBlank()) {
        val decision = decidePrintRouting(mode, "", DirectPrintResult.NoAddress)
        Log.d("BillActions", "printReceiptImage: directResult=NoAddress, decision=$decision")
        return when (decision) {
            RoutingDecision.SHOW_ERROR -> {
                showDirectPrintToast(context, DirectPrintResult.NoAddress)
                false
            }
            RoutingDecision.SYSTEM_DIALOG,
            RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG -> {
                val fileToPrint = resolveFallbackFile()
                if (fileToPrint != null) {
                    withContext(Dispatchers.Main) {
                        printBillJpeg(activity ?: context, fileToPrint, jobName, settings.paperRollWidthMm)
                    }
                }
                false
            }
            RoutingDecision.DIRECT_OK -> true
        }
    }

    val locallyDecoded = cachedBitmap == null && imageFile != null
    val bitmap = if (cachedBitmap != null && !cachedBitmap.isRecycled) {
        effectiveTiming.recordDecodeDone()
        cachedBitmap
    } else if (imageFile != null) {
        val decoded = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        }
        effectiveTiming.recordDecodeDone()
        decoded
    } else {
        effectiveTiming.recordDecodeDone()
        null
    }
    val directResult: DirectPrintResult = try {
        if (bitmap != null) {
            val paperDots = if (settings.paperRollWidthMm == 58) 384 else 576
            EscPosPrinterDriver.printReceiptDirect(
                context = context,
                target = target,
                bitmap = bitmap,
                paperWidthDots = paperDots,
                autoKickDrawer = kickDrawer,
                darkness = settings.receiptDarkness,
                cutMode = ReceiptCutMode.fromString(settings.receiptCutMode),
                feedLines = settings.receiptFeedLinesBeforeCut,
                cutCommandStyle = settings.receiptCutCommandStyle,
                timing = effectiveTiming
            )
        } else {
            DirectPrintResult.Failed(context.getString(R.string.print_could_not_decode_receipt_image))
        }
    } finally {
        if (locallyDecoded) {
            bitmap?.recycle()
        }
    }

    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        cleanOldPrintCacheFiles(context)
    }

    val partialSuccess = if (directResult is DirectPrintResult.UsbTransferFailed) 1 else 0
    val decision = decidePrintRouting(mode, target.identifier, directResult, partialSuccessCount = partialSuccess)
    Log.d("BillActions", "printReceiptImage: directResult=$directResult, decision=$decision")
    return when (decision) {
        RoutingDecision.DIRECT_OK -> true
        RoutingDecision.SHOW_ERROR -> {
            showDirectPrintToast(context, directResult)
            false
        }
        RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG -> {
            showDirectPrintToast(context, directResult)
            val fileToPrint = resolveFallbackFile()
            if (fileToPrint != null) {
                withContext(Dispatchers.Main) {
                    printBillJpeg(activity ?: context, fileToPrint, jobName, settings.paperRollWidthMm)
                }
            }
            false
        }
        RoutingDecision.SYSTEM_DIALOG -> {
            val fileToPrint = resolveFallbackFile()
            if (fileToPrint != null) {
                withContext(Dispatchers.Main) {
                    printBillJpeg(activity ?: context, fileToPrint, jobName, settings.paperRollWidthMm)
                }
            }
            false
        }
    }
}

/**
 * Executes a receipt cut test banner job using the given settings.
 * When [isLong] is true, prints a 1200 rows tall test receipt simulating a 10-item job.
 */
suspend fun executeReceiptCutTest(
    context: Context,
    settings: InvoiceSettingsData,
    cutMode: ReceiptCutMode = ReceiptCutMode.fromString(settings.receiptCutMode),
    feedLines: Int = settings.receiptFeedLinesBeforeCut,
    cutCommandStyle: String = settings.receiptCutCommandStyle,
    isLong: Boolean = false,
    timing: PrintTiming? = null
): DirectPrintResult {
    val target = settings.resolvedReceiptPrinterTarget()
    if (target is PrinterTarget.None || target.identifier.isBlank()) {
        return DirectPrintResult.NoAddress
    }
    val paperDots = if (settings.paperRollWidthMm == 58) 384 else 576
    return EscPosPrinterDriver.printCutTestDirect(
        context = context,
        target = target,
        paperWidthDots = paperDots,
        darkness = settings.receiptDarkness,
        cutMode = cutMode,
        feedLines = feedLines,
        cutCommandStyle = cutCommandStyle,
        isLong = isLong,
        timing = timing
    )
}

/**
 * Deletes print cache files (bills, reports, receipts, product_codes, loose PDFs/JPEGs)
 * that are older than [maxAgeMillis] (default 7 days).
 */
fun cleanOldPrintCacheFiles(cacheDir: File, maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L): Int {
    var deletedCount = 0
    try {
        val now = System.currentTimeMillis()
        val targetDirs = listOf(
            File(cacheDir, "bills"),
            File(cacheDir, "reports"),
            File(cacheDir, "receipts"),
            File(cacheDir, "product_codes")
        )
        for (dir in targetDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && (now - file.lastModified() > maxAgeMillis)) {
                        if (file.delete()) deletedCount++
                    }
                }
            }
        }
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.extension.equals("pdf", ignoreCase = true) || file.extension.equals("jpg", ignoreCase = true))) {
                if (now - file.lastModified() > maxAgeMillis) {
                    if (file.delete()) deletedCount++
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return deletedCount
}

fun cleanOldPrintCacheFiles(context: Context, maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L): Int =
    cleanOldPrintCacheFiles(context.cacheDir, maxAgeMillis)

enum class LabelPrintOutcome {
    SENT_DIRECT,
    OPENED_SYSTEM_DIALOG,
    FAILED;

    val isSuccess: Boolean get() = this != FAILED
}

/**
 * Unified entry point for printing label images.
 * Respects labelPrintMode (BLUETOOTH, SYSTEM, AUTO) and provides graceful fallback.
 */
suspend fun printLabelImages(
    context: Context,
    activity: Activity?,
    labels: List<Bitmap>,
    pdfFallback: File? = null,
    widthMm: Float,
    heightMm: Float,
    copies: Int,
    jobName: String,
    settings: InvoiceSettingsData,
    pdfFallbackBuilder: (suspend () -> File?)? = null
): LabelPrintOutcome {
    val mode = settings.labelPrintMode.trim().uppercase()
    val labelTarget = settings.resolvedLabelPrinterTarget()
    val receiptTarget = settings.resolvedReceiptPrinterTarget()

    val resolvePdf: suspend () -> File? = {
        pdfFallback ?: pdfFallbackBuilder?.invoke()
    }

    if (mode == "SYSTEM") {
        val file = resolvePdf()
        return if (file != null) {
            val opened = withContext(Dispatchers.Main) {
                printPdfDocument(activity ?: context, file, jobName, widthMm, heightMm)
            }
            if (opened) LabelPrintOutcome.OPENED_SYSTEM_DIALOG else LabelPrintOutcome.FAILED
        } else {
            LabelPrintOutcome.FAILED
        }
    }

    if (isSamePrinterTarget(labelTarget, receiptTarget)) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.direct_print_error_label_same_as_receipt),
                Toast.LENGTH_LONG
            ).show()
        }
        return LabelPrintOutcome.FAILED
    }

    if (labelTarget is PrinterTarget.None || labelTarget.identifier.isBlank()) {
        val decision = decidePrintRouting(mode, "", DirectPrintResult.NoAddress)
        return when (decision) {
            RoutingDecision.SHOW_ERROR -> {
                showDirectPrintToast(context, DirectPrintResult.NoAddress)
                LabelPrintOutcome.FAILED
            }
            RoutingDecision.SYSTEM_DIALOG,
            RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG -> {
                val file = resolvePdf()
                if (file != null) {
                    val opened = withContext(Dispatchers.Main) {
                        printPdfDocument(activity ?: context, file, jobName, widthMm, heightMm)
                    }
                    if (opened) LabelPrintOutcome.OPENED_SYSTEM_DIALOG else LabelPrintOutcome.FAILED
                } else {
                    LabelPrintOutcome.FAILED
                }
            }
            RoutingDecision.DIRECT_OK -> LabelPrintOutcome.SENT_DIRECT
        }
    }

    val jobs = labels.map { LabelJob(it, copies) }
    val batchResult = TsplLabelPrinterDriver.printLabelsBatch(
        context = context,
        target = labelTarget,
        jobs = jobs,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = settings.labelGapMm,
        darkness = settings.labelDarkness,
        dpi = settings.labelDpi,
        speed = settings.labelPrintSpeed,
        xOffsetDots = settings.labelXOffsetDots,
        yOffsetDots = settings.labelYOffsetDots,
        direction = settings.labelDirection,
        paperType = settings.labelPaperType
    )
    val directResult = batchResult.result

    if (batchResult.successfulCount > 0 && directResult !is DirectPrintResult.Success) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.batch_print_partial_failure, batchResult.successfulCount),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val decision = decidePrintRouting(mode, labelTarget.identifier, directResult, batchResult.successfulCount)
    return when (decision) {
        RoutingDecision.DIRECT_OK -> LabelPrintOutcome.SENT_DIRECT
        RoutingDecision.SHOW_ERROR -> {
            showDirectPrintToast(context, directResult)
            LabelPrintOutcome.FAILED
        }
        RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG -> {
            showDirectPrintToast(context, directResult)
            val file = resolvePdf()
            if (file != null) {
                val opened = withContext(Dispatchers.Main) {
                    printPdfDocument(activity ?: context, file, jobName, widthMm, heightMm)
                }
                if (opened) LabelPrintOutcome.OPENED_SYSTEM_DIALOG else LabelPrintOutcome.FAILED
            } else {
                LabelPrintOutcome.FAILED
            }
        }
        RoutingDecision.SYSTEM_DIALOG -> {
            val file = resolvePdf()
            if (file != null) {
                val opened = withContext(Dispatchers.Main) {
                    printPdfDocument(activity ?: context, file, jobName, widthMm, heightMm)
                }
                if (opened) LabelPrintOutcome.OPENED_SYSTEM_DIALOG else LabelPrintOutcome.FAILED
            } else {
                LabelPrintOutcome.FAILED
            }
        }
    }
}

/**
 * Smart print function for sales receipts that routes through printReceiptImage.
 */
suspend fun printReceiptWithBluetoothFallback(
    context: Context,
    activity: Activity?,
    bill: BillData,
    settings: InvoiceSettingsData,
    receiptLanguage: String = "EN",
    kickDrawer: Boolean = false,
    timing: PrintTiming? = null
): Boolean {
    val effectiveTiming = timing ?: PrintTiming()

    // The in-memory bitmap is only useful for direct ESC/POS printing. With the system print dialog or no
    // configured printer it would be rendered for nothing and the JPEG rendered again, so skip it.
    val goesDirect = settings.receiptPrintMode.trim().uppercase() != "SYSTEM" &&
        settings.resolvedReceiptPrinterTarget().isConfigured
    if (!goesDirect) {
        return printReceiptImage(
            context = context,
            activity = activity,
            imageFile = null,
            jobName = context.getString(R.string.print_job_invoice_fmt, bill.saleId.toString()),
            settings = settings,
            kickDrawer = kickDrawer,
            timing = effectiveTiming,
            cachedBitmap = null,
            billForFallback = bill,
            receiptLanguage = receiptLanguage
        )
    }

    val bitmapStartMs = System.currentTimeMillis()
    val paperWidth = settings.paperRollWidthMm
    val cached = ReceiptBitmapCache.get(bill.saleId, receiptLanguage, paperWidth)
    val bitmapToPrint: Bitmap = if (cached != null) {
        effectiveTiming.recordGenerateDone()
        Log.d("BillActions", "PrintTiming: receipt bitmap cache hit (0 ms)")
        cached
    } else {
        val bmp = try {
            withContext(Dispatchers.IO) {
                generateBillBitmap(context, bill, receiptLanguage)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("BillActions", "Failed to generate bill bitmap", e)
            return false
        }
        ReceiptBitmapCache.put(bill.saleId, receiptLanguage, paperWidth, bmp)
        effectiveTiming.recordGenerateDone()
        Log.d("BillActions", "PrintTiming: receipt bitmap generated in ${System.currentTimeMillis() - bitmapStartMs} ms")
        bmp
    }

    return printReceiptImage(
        context = context,
        activity = activity,
        imageFile = null,
        jobName = context.getString(R.string.print_job_invoice_fmt, bill.saleId.toString()),
        settings = settings,
        kickDrawer = kickDrawer,
        timing = effectiveTiming,
        cachedBitmap = bitmapToPrint,
        billForFallback = bill,
        receiptLanguage = receiptLanguage
    )
}

/**
 * Smart print function for Store Credit Voucher slip that routes through printReceiptImage.
 */
suspend fun printStoreCreditVoucherSlip(
    context: Context,
    voucherFile: File,
    voucherCode: String,
    settings: InvoiceSettingsData
): Boolean {
    return printReceiptImage(
        context = context,
        activity = context.findActivity(),
        imageFile = voucherFile,
        jobName = context.getString(R.string.print_job_voucher_fmt, voucherCode),
        settings = settings,
        kickDrawer = false
    )
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
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.store_credit_voucher_subject_fmt, voucherCode))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.store_credit_voucher_text_fmt, voucherCode, formatTaka(voucherAmount, isBangla = false)))
        clipData = ClipData.newUri(context.contentResolver, context.getString(R.string.clip_voucher_slip), uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val target = context.findActivity() ?: context
    return try {
        val chooser = Intent.createChooser(intent, context.getString(R.string.store_credit_share_voucher_via_chooser)).apply {
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

