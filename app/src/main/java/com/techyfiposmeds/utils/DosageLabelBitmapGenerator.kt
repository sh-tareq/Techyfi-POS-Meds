package com.techyfiposmeds.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates high-contrast 203 DPI monochrome/thermal bitmaps for 50mm x 30mm adhesive
 * dosage labels attached to medicine envelopes, strips, or boxes in Bangladesh pharmacies.
 */
object DosageLabelBitmapGenerator {

    // Standard 50mm x 30mm at 203 DPI (8 dots/mm)
    const val LABEL_WIDTH_DOTS = 384
    const val LABEL_HEIGHT_DOTS = 240

    fun generateDosageLabelBitmap(
        context: Context,
        storeName: String,
        storePhone: String,
        patientName: String,
        medicineName: String,
        genericName: String? = null,
        dosageSchedule: String,
        foodTiming: String? = null,
        batchNumber: String? = null,
        expiryDate: String? = null,
        dateMillis: Long = System.currentTimeMillis()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(LABEL_WIDTH_DOTS, LABEL_HEIGHT_DOTS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean white background
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }

        // Outer border with 4px margin
        val outerRect = RectF(4f, 4f, LABEL_WIDTH_DOTS - 4f, LABEL_HEIGHT_DOTS - 4f)
        canvas.drawRoundRect(outerRect, 6f, 6f, borderPaint)

        // Header: Pharmacy Name & Hotline
        val storePaint = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val safeStoreName = storeName.ifBlank { "Techyfi Medicine Corner" }
        canvas.drawText(safeStoreName.take(28), LABEL_WIDTH_DOTS / 2f, 22f, storePaint)

        val phonePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val safePhone = if (storePhone.isNotBlank()) "হটলাইন: $storePhone" else "লাইসেন্সপ্রাপ্ত ফার্মেসি"
        canvas.drawText(safePhone, LABEL_WIDTH_DOTS / 2f, 35f, phonePaint)

        // Divider
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawLine(10f, 40f, LABEL_WIDTH_DOTS - 10f, 40f, linePaint)

        // Patient & Date Row
        val patientPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val safePatient = if (patientName.isNotBlank() && patientName != "Patient") "রোগী: $patientName" else "রোগী: নিয়মিত সেবাগ্রহীতা"
        canvas.drawText(safePatient.take(22), 12f, 55f, patientPaint)

        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(dateMillis))
        val datePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateFmt, LABEL_WIDTH_DOTS - 12f, 55f, datePaint)

        // Medicine Name & Generic
        val medPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(medicineName.take(30), 12f, 74f, medPaint)

        if (!genericName.isNullOrBlank()) {
            val genPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            canvas.drawText("(${genericName.take(34)})", 12f, 87f, genPaint)
        }

        // 🩺 High-Contrast Bengali Dosage Schedule Box
        val dosageBoxTop = if (genericName.isNullOrBlank()) 84f else 95f
        val dosageBoxBottom = dosageBoxTop + 85f
        val dosageBoxRect = RectF(10f, dosageBoxTop, LABEL_WIDTH_DOTS - 10f, dosageBoxBottom)

        // Dark filled background for dosage box to make it pop
        val boxBgPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(dosageBoxRect, 8f, 8f, boxBgPaint)

        val dosageTitlePaint = Paint().apply {
            color = Color.LTGRAY
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("• সেবনবিধি / Dosage Instruction •", LABEL_WIDTH_DOTS / 2f, dosageBoxTop + 18f, dosageTitlePaint)

        val dosageTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val safeDosage = dosageSchedule.ifBlank { "১ + ০ + ১" }
        canvas.drawText(safeDosage, LABEL_WIDTH_DOTS / 2f, dosageBoxTop + 47f, dosageTextPaint)

        val timingPaint = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val safeTiming = foodTiming ?: if (safeDosage.contains("খাওয়ার")) "" else "খাওয়ার পর ভরা পেটে"
        if (safeTiming.isNotBlank()) {
            canvas.drawText("👉 $safeTiming", LABEL_WIDTH_DOTS / 2f, dosageBoxTop + 69f, timingPaint)
        }

        // Footer Row: Batch & Expiry
        val footerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val batchStr = if (!batchNumber.isNullOrBlank()) "ব্যাচ: $batchNumber" else "মানসম্মত ওষুধ"
        val expiryStr = if (!expiryDate.isNullOrBlank()) "মেয়াদ: $expiryDate" else ""
        val footerText = listOf(batchStr, expiryStr).filter { it.isNotBlank() }.joinToString(" • ")
        canvas.drawText(footerText, 12f, LABEL_HEIGHT_DOTS - 14f, footerPaint)

        val watermarkPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Techyfi Pharma", LABEL_WIDTH_DOTS - 12f, LABEL_HEIGHT_DOTS - 14f, watermarkPaint)

        return bitmap
    }
}
