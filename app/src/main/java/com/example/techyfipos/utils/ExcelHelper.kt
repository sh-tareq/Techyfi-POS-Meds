package com.example.techyfipos.utils

import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.CustomerDue
import com.example.techyfipos.data.db.DueCharge
import com.example.techyfipos.data.db.DuePayment
import com.example.techyfipos.data.db.Expense
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.db.Sale
import com.example.techyfipos.data.db.SaleWithItems
import com.example.techyfipos.data.db.Return
import com.example.techyfipos.data.db.ReturnItem
import com.example.techyfipos.data.db.ScheduledDueReminder
import com.example.techyfipos.data.db.Supplier
import com.example.techyfipos.ui.viewmodel.calculateDiscountValue
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelHelper {

    init {
        try {
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    data class ImportResult(
        val successCount: Int,
        val failCount: Int,
        val failures: List<String>,
        val importedProducts: List<Product>,
        val presentFields: Set<String> = emptySet()
    )

    fun isValidEan13WithLeadingZero(twelveDigits: String): Boolean {
        if (twelveDigits.length != 12 || !twelveDigits.all { it.isDigit() }) return false
        val full13 = "0$twelveDigits"
        var sum = 0
        for (i in 0 until 12) {
            val digit = full13[i] - '0'
            val weight = if (i % 2 == 0) 1 else 3
            sum += digit * weight
        }
        val check = (10 - (sum % 10)) % 10
        return check == (full13[12] - '0')
    }

    fun preserveLeadingZeros(raw: String): String {
        val trimmed = raw.trim()
        // 10-digit number starting with '1' is a Bangladeshi mobile number (01X...) with leading 0 dropped by Excel
        if (trimmed.length == 10 && trimmed.startsWith("1") && trimmed.all { it.isDigit() }) {
            return "0$trimmed"
        }
        // 12-digit number that validates as an EAN-13 when prepended with '0'
        if (trimmed.length == 12 && trimmed.all { it.isDigit() } && isValidEan13WithLeadingZero(trimmed)) {
            return "0$trimmed"
        }
        return trimmed
    }

    @Suppress("DEPRECATION")
    private fun getCellStringValue(cell: Cell?, isBarcode: Boolean = false): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            Cell.CELL_TYPE_STRING -> {
                val str = cell.stringCellValue.trim()
                if (isBarcode) preserveLeadingZeros(str) else str
            }
            Cell.CELL_TYPE_NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.dateCellValue
                    SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
                } else {
                    val numValue = cell.numericCellValue
                    val raw = if (numValue == numValue.toLong().toDouble()) {
                        numValue.toLong().toString()
                    } else {
                        numValue.toString()
                    }
                    if (isBarcode) preserveLeadingZeros(raw) else raw
                }
            }
            Cell.CELL_TYPE_BOOLEAN -> cell.booleanCellValue.toString()
            Cell.CELL_TYPE_FORMULA -> {
                try {
                    val str = cell.stringCellValue.trim()
                    if (isBarcode) preserveLeadingZeros(str) else str
                } catch (e: Exception) {
                    try {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            val date = cell.dateCellValue
                            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
                        } else {
                            val numValue = cell.numericCellValue
                            val raw = if (numValue == numValue.toLong().toDouble()) {
                                numValue.toLong().toString()
                            } else {
                                numValue.toString()
                            }
                            if (isBarcode) preserveLeadingZeros(raw) else raw
                        }
                    } catch (ex: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }

    private fun parseDouble(valStr: String): Double? {
        val converted = toEnglishDigits(valStr)
        val cleaned = converted.replace("৳", "").replace(",", "").replace(" ", "").trim()
        return cleaned.toDoubleOrNull()
    }

    private fun parseInt(valStr: String): Int? {
        val converted = toEnglishDigits(valStr)
        val cleaned = converted.replace(",", "").replace(" ", "").trim()
        return cleaned.toDoubleOrNull()?.toInt() ?: cleaned.toIntOrNull()
    }

    // Normalized headers mapping to fields
    private fun mapHeaderToField(header: String): String? {
        val h = header.lowercase(Locale.getDefault()).replace(" ", "").trim()
        return when {
            h in listOf("barcode", "code", "productcode", "sku", "বারকোড", "পণ্যকোড", "কোড", "পণ্যআইডি") -> "barcode"
            h in listOf("name", "productname", "title", "নাম", "পণ্যেরনাম") -> "name"
            h in listOf("category", "ক্যাটাগরি", "বিভাগ") -> "category"
            h in listOf("purchaseprice", "buyingprice", "costprice", "purchase", "ক্রয়মূল্য", "ক্রয়মূল্য", "কেনাদাম") -> "purchasePrice"
            h in listOf("sellingprice", "saleprice", "price", "বিক্রয়মূল্য", "বিক্রয়মূল্য", "বিক্রিরদাম") -> "sellingPrice"
            h in listOf("stock", "quantity", "stockqty", "qty", "স্টক", "পরিমাণ", "স্টকেরপরিমাণ") -> "stockQty"
            h in listOf("reorderlevel", "reorder", "minstock", "রিঅর্ডারলেভেল", "সর্বনিম্নস্টক") -> "reorderLevel"
            h in listOf("unit", "uom", "ইউনিট") -> "unit"
            h in listOf("wholesaleprice", "wholesale", "পাইকারিমূল্য") -> "wholesalePrice"
            h in listOf("mrp", "mrpprice", "সর্বোচ্চখুচরামূল্য") -> "mrpPrice"
            h in listOf("discountedprice", "discountprice", "offerprice", "ছাড়মূল্য") -> "discountedPrice"
            h in listOf("mfgorpkgtype", "datetype", "mfgpkgtype") -> "mfgOrPkgType"
            h in listOf("mfgorpkgdate", "manufacturingdate", "packagingdate", "mfgdate") -> "mfgOrPkgDate"
            h in listOf("expirydate", "expirationdate", "expdate", "মেয়াদউত্তীর্ণেরতারিখ") -> "expiryDate"
            h in listOf("imageuri", "image", "ছবি") -> "imageUri"
            else -> null
        }
    }

    fun generateProductsTemplate(outputStream: OutputStream) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Product Import Template")

        // Header style
        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.getIndex()
            fontHeightInPoints = 11.toShort()
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
            setAlignment(HorizontalAlignment.CENTER)
            setVerticalAlignment(VerticalAlignment.CENTER)
        }

        // Data row style
        val numberStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }
        val intStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0")
        }

        val headers = arrayOf(
            "Barcode", "Product Name", "Category", "Purchase Price", "Selling Price", "Stock Qty",
            "Reorder Level", "Unit", "Wholesale Price", "MRP Price", "Discounted Price",
            "Mfg or Pkg Type", "Mfg or Pkg Date", "Expiry Date"
        )
        val headerRow = sheet.createRow(0)
        headerRow.heightInPoints = 26f

        for (i in headers.indices) {
            val cell = headerRow.createCell(i)
            cell.setCellValue(headers[i])
            cell.cellStyle = headerStyle
            sheet.setColumnWidth(i, 22 * 256)
        }

        // Comprehensive sample product data
        val sampleProducts = listOf(
            arrayOf<Any>("8901234567891", "Pran Potato Crackers 25g", "Snacks", 12.00, 15.00, 100, 20),
            arrayOf<Any>("8901234567892", "Mojo Cola 500ml", "Beverages", 25.00, 30.00, 60, 15),
            arrayOf<Any>("8901234567893", "Ruchi Chanachur 150g", "Snacks", 35.00, 45.00, 40, 10),
            arrayOf<Any>("8901234567894", "Aarong Dairy Milk 1L", "Dairy", 80.00, 95.00, 30, 5),
            arrayOf<Any>("8901234567895", "Lux Body Soap 100g", "Personal Care", 50.00, 60.00, 50, 10),
            arrayOf<Any>("", "Organic Brown Sugar 1kg", "Grocery", 110.00, 130.00, 25, 5),
            arrayOf<Any>("8901234567897", "Radhuni Turmeric Powder 200g", "Grocery", 65.00, 78.00, 45, 8),
            arrayOf<Any>("8901234567898", "Teer Soyabean Oil 1L", "Grocery", 160.00, 175.00, 50, 10)
        )

        for ((idx, item) in sampleProducts.withIndex()) {
            val r = sheet.createRow(idx + 1)
            r.heightInPoints = 20f

            // Barcode
            r.createCell(0).setCellValue(item[0] as String)
            // Name
            r.createCell(1).setCellValue(item[1] as String)
            // Category
            r.createCell(2).setCellValue(item[2] as String)
            
            // Purchase Price
            val c3 = r.createCell(3)
            c3.setCellValue(item[3] as Double)
            c3.cellStyle = numberStyle

            // Selling Price
            val c4 = r.createCell(4)
            c4.setCellValue(item[4] as Double)
            c4.cellStyle = numberStyle

            // Stock Qty
            val c5 = r.createCell(5)
            c5.setCellValue((item[5] as Int).toDouble())
            c5.cellStyle = intStyle

            // Reorder Level
            val c6 = r.createCell(6)
            c6.setCellValue((item[6] as Int).toDouble())
            c6.cellStyle = intStyle
        }

        workbook.write(outputStream)
        workbook.close()
    }

    fun exportProducts(products: List<Product>, outputStream: OutputStream) {
        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Products")

            // Header style
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
                setFillPattern(FillPatternType.SOLID_FOREGROUND)
                setFont(headerFont)
            }

            val headers = arrayOf(
                "Barcode", "Product Name", "Category", "Purchase Price", "Selling Price", "Stock Qty",
                "Reorder Level", "Unit", "Wholesale Price", "MRP Price", "Discounted Price",
                "Mfg or Pkg Type", "Mfg or Pkg Date", "Expiry Date", "Image URI"
            )
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
                sheet.setColumnWidth(i, 20 * 256)
            }

            var rowIdx = 1
            for (p in products) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(p.barcode)
                row.createCell(1).setCellValue(p.name)
                row.createCell(2).setCellValue(p.category)
                row.createCell(3).setCellValue(p.purchasePrice)
                row.createCell(4).setCellValue(p.sellingPrice)
                row.createCell(5).setCellValue(p.stockQty.toDouble())
                row.createCell(6).setCellValue(p.reorderLevel.toDouble())
                row.createCell(7).setCellValue(p.unit)
                row.createCell(8).setCellValue(p.wholesalePrice)
                row.createCell(9).setCellValue(p.mrpPrice)
                row.createCell(10).setCellValue(p.discountedPrice)
                row.createCell(11).setCellValue(p.mfgOrPkgType ?: "")
                row.createCell(12).setCellValue(p.mfgOrPkgDate ?: "")
                row.createCell(13).setCellValue(p.expiryDate ?: "")
                row.createCell(14).setCellValue(p.imageUri ?: "")
            }

            workbook.write(outputStream)
        } finally {
            try { workbook.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    fun importProducts(inputStream: InputStream): ImportResult {
        val importedList = mutableListOf<Product>()
        val failures = mutableListOf<String>()
        var successCount = 0
        var failCount = 0
        var presentFields: Set<String> = emptySet()

        try {
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            if (sheet == null || sheet.physicalNumberOfRows == 0) {
                return ImportResult(0, 1, listOf("The selected Excel file is empty."), emptyList())
            }

            val headerRow = sheet.getRow(0)
            if (headerRow == null) {
                return ImportResult(0, 1, listOf("No header row found in Excel."), emptyList())
            }

            val colMap = mutableMapOf<String, Int>() // Field -> Col Index
            for (cellIdx in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(cellIdx)
                val headerVal = getCellStringValue(cell)
                val field = mapHeaderToField(headerVal)
                if (field != null) {
                    colMap[field] = cellIdx
                }
            }
            presentFields = colMap.keys.toSet()

            // Verify minimum required fields
            val requiredFields = listOf("barcode", "name", "sellingPrice")
            val missing = requiredFields.filter { it !in colMap }
            if (missing.isNotEmpty()) {
                return ImportResult(
                    successCount = 0,
                    failCount = 1,
                    failures = listOf("Missing required columns: ${missing.joinToString(", ")}"),
                    importedProducts = emptyList()
                )
            }

            for (rowIdx in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                
                // Skip completely empty rows
                var isRowEmpty = true
                for (c in 0 until row.lastCellNum) {
                    if (getCellStringValue(row.getCell(c)).isNotEmpty()) {
                        isRowEmpty = false
                        break
                    }
                }
                if (isRowEmpty) continue

                try {
                    val rawBarcode = colMap["barcode"]?.let { getCellStringValue(row.getCell(it), isBarcode = true) } ?: ""
                    val barcode = toEnglishDigits(rawBarcode).trim().lowercase()
                    val name = colMap["name"]?.let { getCellStringValue(row.getCell(it)) } ?: ""
                    
                    if (barcode.isBlank() || name.isBlank()) {
                        failCount++
                        failures.add("Row ${rowIdx + 1}: Barcode and Name cannot be blank.")
                        continue
                    }

                    val categoryStr = colMap["category"]?.let { getCellStringValue(row.getCell(it)).trim() }.orEmpty()
                    val category = categoryStr
                    
                    val pPriceStr = colMap["purchasePrice"]?.let { getCellStringValue(row.getCell(it)) }.orEmpty()
                    val parsedPPrice = if (pPriceStr.isBlank()) null else parseDouble(pPriceStr)
                    if (pPriceStr.isNotBlank() && (parsedPPrice == null || parsedPPrice < 0.0)) {
                        failCount++
                        failures.add("Row ${rowIdx + 1}: Invalid purchase price '$pPriceStr'.")
                        continue
                    }
                    val purchasePrice = if (pPriceStr.isBlank()) -1.0 else (parsedPPrice ?: 0.0).coerceAtLeast(0.0)

                    val sPriceStr = colMap["sellingPrice"]?.let { getCellStringValue(row.getCell(it)) } ?: "0.0"
                    val sellingPrice = parseDouble(sPriceStr)
                    if (sellingPrice == null || sellingPrice < 0.0) {
                        failCount++
                        failures.add("Row ${rowIdx + 1}: Invalid selling price '$sPriceStr'. Must be zero or greater.")
                        continue
                    }

                    val stockStr = colMap["stockQty"]?.let { getCellStringValue(row.getCell(it)) }.orEmpty()
                    val parsedStock = if (stockStr.isBlank()) null else parseDouble(stockStr)
                    if (stockStr.isNotBlank() && (parsedStock == null || parsedStock < 0.0)) {
                        failCount++
                        failures.add("Row ${rowIdx + 1}: Invalid stock quantity '$stockStr'.")
                        continue
                    }
                    val stockQty = if (stockStr.isBlank()) -1.0 else (parsedStock ?: 0.0).coerceAtLeast(0.0)

                    val reorderStr = colMap["reorderLevel"]?.let { getCellStringValue(row.getCell(it)).trim() }.orEmpty()
                    val reorderLevel = if (reorderStr.isBlank()) -1 else (parseInt(reorderStr) ?: 0).coerceAtLeast(0)

                    fun optionalText(field: String): String? = colMap[field]
                        ?.let { getCellStringValue(row.getCell(it)).trim().takeIf(String::isNotBlank) }
                    fun optionalDouble(field: String): Double {
                        val s = colMap[field]?.let { getCellStringValue(row.getCell(it)).trim() }.orEmpty()
                        if (s.isBlank()) return -1.0
                        return parseDouble(s)?.coerceAtLeast(0.0) ?: 0.0
                    }

                    val expiryVal = optionalText("expiryDate")
                    if (expiryVal != null && parseExpiryDateToMillis(toEnglishDigits(expiryVal)) == null) {
                        failCount++
                        failures.add("Row ${rowIdx + 1}: Invalid expiry date '$expiryVal'. Expected dd/MM/yyyy.")
                        continue
                    }

                    val mrpVal = optionalDouble("mrpPrice")
                    val effectiveSellingPrice = if (mrpVal > 0.0) sellingPrice.coerceAtMost(mrpVal) else sellingPrice

                    importedList.add(
                        Product(
                            barcode = barcode,
                            name = name,
                            category = category,
                            purchasePrice = purchasePrice,
                            sellingPrice = effectiveSellingPrice,
                            stockQty = stockQty,
                            reorderLevel = reorderLevel,
                            imageUri = optionalText("imageUri"),
                            mfgOrPkgType = optionalText("mfgOrPkgType") ?: "Mfg",
                            mfgOrPkgDate = optionalText("mfgOrPkgDate"),
                            expiryDate = expiryVal,
                            unit = optionalText("unit") ?: "Pcs",
                            wholesalePrice = optionalDouble("wholesalePrice"),
                            mrpPrice = mrpVal,
                            discountedPrice = optionalDouble("discountedPrice")
                        )
                    )
                    successCount++
                } catch (e: Exception) {
                    failCount++
                    failures.add("Row ${rowIdx + 1}: ${e.message}")
                }
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(0, 1, listOf("Failed to parse Excel file: ${e.localizedMessage}"), emptyList())
        }

        return ImportResult(successCount, failCount, failures, importedList, presentFields)
    }

    fun exportSales(
        salesList: List<SaleWithItems>,
        purchasePrices: Map<String, Double>,
        returns: List<Return>,
        returnItems: List<ReturnItem>,
        outputStream: OutputStream,
        canViewPnl: Boolean = true
    ) {
        val workbook = SXSSFWorkbook(100)
        try {
            val sheet = workbook.createSheet("Sales Report")

            // Styles
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
                setFillPattern(FillPatternType.SOLID_FOREGROUND)
                setFont(headerFont)
            }

            val summaryFont = workbook.createFont().apply {
                bold = true
            }
            val summaryStyle = workbook.createCellStyle().apply {
                setFont(summaryFont)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
                setFillPattern(FillPatternType.SOLID_FOREGROUND)
            }

            val headers = if (canViewPnl) {
                arrayOf(
                    "Date", "Invoice ID", "Items Detail", "Payment Method",
                    "Subtotal", "Discount", "Grand Total", "Returns", "Net Total", "Amount Paid",
                    "Due Amount", "Profit", "Customer"
                )
            } else {
                arrayOf(
                    "Date", "Invoice ID", "Items Detail", "Payment Method",
                    "Subtotal", "Discount", "Grand Total", "Returns", "Net Total", "Amount Paid",
                    "Due Amount", "Customer"
                )
            }
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
                sheet.setColumnWidth(i, 20 * 256)
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            var rowIdx = 1
            var totalSalesSum = 0.0
            var totalProfitSum = 0.0

            salesList.chunked(500).forEach { batch ->
                for (swi in batch) {
                    val row = sheet.createRow(rowIdx++)
                    val saleDate = dateFormat.format(Date(swi.sale.timestamp))
                    
                    // Format item details e.g., "Soap x 2, Bread x 1"
                    val itemDetails = swi.items.joinToString(", ") { "${it.productName} x ${it.quantity}" }
                    
                    // Historical cost comes from the immutable sale line. A live product lookup is only
                    // used for old rows created before purchasePriceAtSale existed.
                    var purchaseCost = 0.0
                    for (item in swi.items) {
                        val cost = item.purchasePriceAtSale ?: purchasePrices[item.productBarcode]
                            ?: com.example.techyfipos.ui.viewmodel.fallbackPurchasePrice(item.productBarcode, item.price)
                        purchaseCost += cost * item.quantity
                    }
                    val saleReturns = returns.filter { it.saleId == swi.sale.id }
                    val returnedAmount = saleReturns.sumOf { it.refundAmount }
                    val returnIds = saleReturns.mapTo(hashSetOf()) { it.id }
                    val returnedCost = returnItems.asSequence()
                        .filter { it.returnId in returnIds }
                        .sumOf { returned ->
                            val sold = swi.items.firstOrNull { it.productBarcode == returned.productBarcode }
                            val cost = sold?.purchasePriceAtSale ?: purchasePrices[returned.productBarcode]
                                ?: com.example.techyfipos.ui.viewmodel.fallbackPurchasePrice(returned.productBarcode, returned.price)
                            cost * returned.quantity
                        }
                    val netTax = if (swi.sale.isTaxInclusive && swi.sale.taxAmount > 0.0) swi.sale.taxAmount else 0.0
                    val netGrandTotal = (swi.sale.grandTotal - netTax).coerceAtLeast(0.0)
                    val returnedTax = if (swi.sale.isTaxInclusive && swi.sale.grandTotal > 0.0 && netTax > 0.0) {
                        returnedAmount * (netTax / swi.sale.grandTotal)
                    } else 0.0
                    val netReturnedAmount = (returnedAmount - returnedTax).coerceAtLeast(0.0)
                    val profit = (netGrandTotal - purchaseCost) - (netReturnedAmount - returnedCost)
                    val netTotal = (swi.sale.grandTotal - returnedAmount).coerceAtLeast(0.0)
                    
                    row.createCell(0).setCellValue(saleDate)
                    row.createCell(1).setCellValue(swi.sale.id.toString())
                    row.createCell(2).setCellValue(itemDetails)
                    val paymentDetail = if (swi.sale.paymentMethod == "Split" && !swi.sale.splitPaymentJson.isNullOrBlank()) {
                        try {
                            val values = JSONArray(swi.sale.splitPaymentJson)
                            (0 until values.length()).joinToString(" + ") { index ->
                                val value = values.getJSONObject(index)
                                "${value.optString("method", "Other")}: ${value.optDouble("amount", 0.0)}"
                            }
                        } catch (_: Exception) { "Split" }
                    } else swi.sale.paymentMethod
                    row.createCell(3).setCellValue(paymentDetail)
                    row.createCell(4).setCellValue(swi.sale.subtotal)
                    row.createCell(5).setCellValue(
                        calculateDiscountValue(swi.sale.subtotal, swi.sale.discountAmount, swi.sale.isPercentageDiscount)
                    )
                    row.createCell(6).setCellValue(swi.sale.grandTotal)
                    row.createCell(7).setCellValue(returnedAmount)
                    row.createCell(8).setCellValue(netTotal)
                    row.createCell(9).setCellValue(swi.sale.amountPaid)

                    val originalDue = when {
                        swi.sale.paymentMethod == "Due" -> {
                            (swi.sale.grandTotal - swi.sale.amountPaid).coerceAtLeast(0.0)
                                .takeIf { it > 0.0 } ?: swi.sale.grandTotal
                        }
                        swi.sale.paymentMethod == "Split" && !swi.sale.splitPaymentJson.isNullOrBlank() -> try {
                            val values = JSONArray(swi.sale.splitPaymentJson)
                            (0 until values.length()).sumOf { index ->
                                values.getJSONObject(index).takeIf {
                                    it.optString("method", "").equals("Due", ignoreCase = true)
                                }?.optDouble("amount", 0.0) ?: 0.0
                            }
                        } catch (_: Exception) { 0.0 }
                        else -> 0.0
                    }
                    val due = (originalDue - saleReturns.sumOf { it.dueAdjustmentAmount }).coerceAtLeast(0.0)
                    row.createCell(10).setCellValue(due)
                    val custColIdx: Int
                    if (canViewPnl) {
                        row.createCell(11).setCellValue(profit)
                        custColIdx = 12
                    } else {
                        custColIdx = 11
                    }

                    val custInfo = if (swi.sale.customerName != null) "${swi.sale.customerName} (${swi.sale.customerPhone ?: ""})" else ""
                    row.createCell(custColIdx).setCellValue(custInfo)

                    totalSalesSum += netTotal
                    if (canViewPnl) {
                        totalProfitSum += profit
                    }
                }
            }

            // Empty row separator
            rowIdx++

            // Summary row
            val summaryRow = sheet.createRow(rowIdx)
            for (i in headers.indices) {
                summaryRow.createCell(i).cellStyle = summaryStyle
            }
            summaryRow.getCell(0).setCellValue("TOTAL SUMMARY")
            summaryRow.getCell(8).setCellValue(totalSalesSum)
            if (canViewPnl) {
                summaryRow.getCell(11).setCellValue(totalProfitSum)
            }

            workbook.write(outputStream)
        } finally {
            try {
                workbook.dispose()
            } catch (_: Exception) {}
            try {
                workbook.close()
            } catch (_: Exception) {}
        }
    }

    fun exportDues(duesList: List<CustomerDue>, outputStream: OutputStream) {
        exportComprehensiveDues(
            duesList = duesList,
            outputStream = outputStream
        )
    }

    fun exportComprehensiveDues(
        duesList: List<CustomerDue>,
        paymentsList: List<DuePayment> = emptyList(),
        remindersList: List<ScheduledDueReminder> = emptyList(),
        salesList: List<Sale> = emptyList(),
        returnsList: List<Return> = emptyList(),
        chargesList: List<DueCharge> = emptyList(),
        outputStream: OutputStream
    ) {
        val workbook = SXSSFWorkbook(100)
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.getIndex()
            fontHeightInPoints = 11.toShort()
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
            setAlignment(HorizontalAlignment.CENTER)
            setVerticalAlignment(VerticalAlignment.CENTER)
        }

        val tealHeaderStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.TEAL.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
            setAlignment(HorizontalAlignment.CENTER)
            setVerticalAlignment(VerticalAlignment.CENTER)
        }

        val greenHeaderStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREEN.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
            setAlignment(HorizontalAlignment.CENTER)
            setVerticalAlignment(VerticalAlignment.CENTER)
        }

        val summaryFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 11.toShort()
        }
        val summaryStyle = workbook.createCellStyle().apply {
            setFont(summaryFont)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
        }

        val currencyStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }
        val summaryCurrencyStyle = workbook.createCellStyle().apply {
            setFont(summaryFont)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }

        // ==========================================
        // SHEET 1: Customer Dues & Accounts Summary
        // ==========================================
        val sheet1 = workbook.createSheet("Dues & Credit Summary")
        val headers1 = arrayOf(
            "Sl #",
            "Customer Name",
            "Customer Phone",
            "Account Status",
            "Outstanding Due (৳)",
            "Credit Limit (৳)",
            "Available Credit (৳)",
            "Total Repaid (৳)",
            "Last Payment Date",
            "Last Payment (৳)",
            "Last Updated"
        )
        val hRow1 = sheet1.createRow(0)
        hRow1.heightInPoints = 26f
        for (i in headers1.indices) {
            val cell = hRow1.createCell(i)
            cell.setCellValue(headers1[i])
            cell.cellStyle = headerStyle
            sheet1.setColumnWidth(i, if (i == 1) 26 * 256 else if (i == 2) 18 * 256 else 20 * 256)
        }

        val paymentsByPhone = paymentsList.groupBy { it.customerPhone }
        var rowIdx1 = 1
        var totalOutstandingSum = 0.0
        var totalCreditLimitSum = 0.0
        var totalRepaidSum = 0.0

        for ((index, due) in duesList.sortedByDescending { it.outstandingAmount }.withIndex()) {
            val row = sheet1.createRow(rowIdx1++)
            val custPayments = paymentsByPhone[due.customerPhone] ?: emptyList()
            val totalRepaid = custPayments.sumOf { it.amountPaid }
            val lastPayment = custPayments.maxByOrNull { it.timestamp }
            val availableCredit = if (due.creditLimit > 0) (due.creditLimit - due.outstandingAmount).coerceAtLeast(0.0) else 0.0
            val status = when {
                due.outstandingAmount > due.creditLimit && due.creditLimit > 0 -> "Credit Limit Exceeded"
                due.outstandingAmount > 0 -> "Active Due"
                due.outstandingAmount < 0 -> "Credit Balance (Advance)"
                else -> "Settled / Cleared"
            }

            row.createCell(0).setCellValue((index + 1).toDouble())
            row.createCell(1).setCellValue(due.customerName)
            row.createCell(2).setCellValue(due.customerPhone)
            row.createCell(3).setCellValue(status)

            val cDue = row.createCell(4)
            cDue.setCellValue(due.outstandingAmount)
            cDue.cellStyle = currencyStyle

            val cLimit = row.createCell(5)
            cLimit.setCellValue(due.creditLimit)
            cLimit.cellStyle = currencyStyle

            val cAvail = row.createCell(6)
            cAvail.setCellValue(availableCredit)
            cAvail.cellStyle = currencyStyle

            val cRepaid = row.createCell(7)
            cRepaid.setCellValue(totalRepaid)
            cRepaid.cellStyle = currencyStyle

            row.createCell(8).setCellValue(if (lastPayment != null) dateFormat.format(Date(lastPayment.timestamp)) else "-")

            val cLastPayAmt = row.createCell(9)
            cLastPayAmt.setCellValue(lastPayment?.amountPaid ?: 0.0)
            cLastPayAmt.cellStyle = currencyStyle

            row.createCell(10).setCellValue(if (due.updatedAt > 0) dateFormat.format(Date(due.updatedAt)) else "-")

            totalOutstandingSum += due.outstandingAmount
            totalCreditLimitSum += due.creditLimit
            totalRepaidSum += totalRepaid
        }

        // Summary Row Sheet 1
        val sumRow1 = sheet1.createRow(rowIdx1)
        for (i in headers1.indices) sumRow1.createCell(i).cellStyle = summaryStyle
        sumRow1.getCell(0).setCellValue("TOTAL SUMMARY")
        val s4 = sumRow1.getCell(4); s4.setCellValue(totalOutstandingSum); s4.cellStyle = summaryCurrencyStyle
        val s5 = sumRow1.getCell(5); s5.setCellValue(totalCreditLimitSum); s5.cellStyle = summaryCurrencyStyle
        val s7 = sumRow1.getCell(7); s7.setCellValue(totalRepaidSum); s7.cellStyle = summaryCurrencyStyle

        // ==========================================
        // SHEET 2: Repayment History Ledger
        // ==========================================
        val sheet2 = workbook.createSheet("Repayment Ledger")
        val headers2 = arrayOf(
            "Sl #",
            "Payment Receipt #",
            "Payment Date & Time",
            "Customer Name",
            "Customer Phone",
            "Amount Paid (৳)",
            "Payment Method",
            "Reference / Notes"
        )
        val hRow2 = sheet2.createRow(0)
        hRow2.heightInPoints = 26f
        for (i in headers2.indices) {
            val cell = hRow2.createCell(i)
            cell.setCellValue(headers2[i])
            cell.cellStyle = greenHeaderStyle
            sheet2.setColumnWidth(i, if (i == 3) 24 * 256 else 20 * 256)
        }

        val custNameMap = duesList.associate { it.customerPhone to it.customerName }
        var rowIdx2 = 1
        var totalCollectedSum = 0.0
        for ((index, p) in paymentsList.sortedByDescending { it.timestamp }.withIndex()) {
            val row = sheet2.createRow(rowIdx2++)
            val name = custNameMap[p.customerPhone] ?: p.customerPhone
            row.createCell(0).setCellValue((index + 1).toDouble())
            row.createCell(1).setCellValue("#PAY-${p.id}")
            row.createCell(2).setCellValue(dateFormat.format(Date(p.timestamp)))
            row.createCell(3).setCellValue(name)
            row.createCell(4).setCellValue(p.customerPhone)

            val cAmt = row.createCell(5)
            cAmt.setCellValue(p.amountPaid)
            cAmt.cellStyle = currencyStyle

            row.createCell(6).setCellValue(p.paymentMethod)
            row.createCell(7).setCellValue(p.referenceNote ?: "")

            totalCollectedSum += p.amountPaid
        }

        // Summary Row Sheet 2
        val sumRow2 = sheet2.createRow(rowIdx2)
        for (i in headers2.indices) sumRow2.createCell(i).cellStyle = summaryStyle
        sumRow2.getCell(0).setCellValue("TOTAL REPAYMENTS COLLECTED")
        val s25 = sumRow2.getCell(5); s25.setCellValue(totalCollectedSum); s25.cellStyle = summaryCurrencyStyle

        // ==========================================
        // SHEET 3: Credit Sales & Invoices
        // ==========================================
        val sheet3 = workbook.createSheet("Credit Sales & Invoices")
        val headers3 = arrayOf(
            "Sl #",
            "Invoice #",
            "Date & Time",
            "Customer Name",
            "Customer Phone",
            "Grand Total (৳)",
            "Amount Paid (৳)",
            "Due Incurred (৳)",
            "Payment Method"
        )
        val hRow3 = sheet3.createRow(0)
        hRow3.heightInPoints = 26f
        for (i in headers3.indices) {
            val cell = hRow3.createCell(i)
            cell.setCellValue(headers3[i])
            cell.cellStyle = tealHeaderStyle
            sheet3.setColumnWidth(i, 20 * 256)
        }

        val creditSales = salesList.filter { s ->
            val due = (s.grandTotal - s.amountPaid).coerceAtLeast(0.0)
            s.paymentMethod.equals("Due", ignoreCase = true) || due > 0.001
        }.sortedByDescending { it.timestamp }

        var rowIdx3 = 1
        var totalCreditDueIncurred = 0.0
        var totalCreditSalesGrand = 0.0
        for ((index, s) in creditSales.withIndex()) {
            val row = sheet3.createRow(rowIdx3++)
            val dueAmount = if (s.paymentMethod.equals("Due", ignoreCase = true)) s.grandTotal else (s.grandTotal - s.amountPaid).coerceAtLeast(0.0)
            val name = s.customerName ?: (custNameMap[s.customerPhone ?: ""] ?: (s.customerPhone ?: "Walk-in"))

            row.createCell(0).setCellValue((index + 1).toDouble())
            row.createCell(1).setCellValue(String.format(Locale.US, "#INV-%05d", s.id))
            row.createCell(2).setCellValue(dateFormat.format(Date(s.timestamp)))
            row.createCell(3).setCellValue(name)
            row.createCell(4).setCellValue(s.customerPhone ?: "")

            val cGrand = row.createCell(5)
            cGrand.setCellValue(s.grandTotal)
            cGrand.cellStyle = currencyStyle

            val cPaid = row.createCell(6)
            cPaid.setCellValue(s.amountPaid)
            cPaid.cellStyle = currencyStyle

            val cDue = row.createCell(7)
            cDue.setCellValue(dueAmount)
            cDue.cellStyle = currencyStyle

            row.createCell(8).setCellValue(s.paymentMethod)

            totalCreditSalesGrand += s.grandTotal
            totalCreditDueIncurred += dueAmount
        }

        // Summary Row Sheet 3
        val sumRow3 = sheet3.createRow(rowIdx3)
        for (i in headers3.indices) sumRow3.createCell(i).cellStyle = summaryStyle
        sumRow3.getCell(0).setCellValue("TOTAL CREDIT INVOICES")
        val s35 = sumRow3.getCell(5); s35.setCellValue(totalCreditSalesGrand); s35.cellStyle = summaryCurrencyStyle
        val s37 = sumRow3.getCell(7); s37.setCellValue(totalCreditDueIncurred); s37.cellStyle = summaryCurrencyStyle

        // ==========================================
        // SHEET 4: Scheduled Reminders & Activity
        // ==========================================
        if (remindersList.isNotEmpty()) {
            val sheet4 = workbook.createSheet("Scheduled Reminders")
            val headers4 = arrayOf(
                "Sl #",
                "Customer Name",
                "Customer Phone",
                "Due Amount (৳)",
                "Scheduled Reminder Date",
                "Channel",
                "Frequency / Type"
            )
            val hRow4 = sheet4.createRow(0)
            hRow4.heightInPoints = 26f
            for (i in headers4.indices) {
                val cell = hRow4.createCell(i)
                cell.setCellValue(headers4[i])
                cell.cellStyle = headerStyle
                sheet4.setColumnWidth(i, 20 * 256)
            }

            var rowIdx4 = 1
            for ((index, rem) in remindersList.sortedBy { it.fireAt }.withIndex()) {
                val row = sheet4.createRow(rowIdx4++)
                val name = custNameMap[rem.customerPhone] ?: rem.customerName

                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue(name)
                row.createCell(2).setCellValue(rem.customerPhone)

                val cDue = row.createCell(3)
                cDue.setCellValue(rem.dueAmount)
                cDue.cellStyle = currencyStyle

                row.createCell(4).setCellValue(dateFormat.format(Date(rem.fireAt)))
                row.createCell(5).setCellValue(rem.channel)
                row.createCell(6).setCellValue(if (rem.isRecurring) "Daily Recurring" else "One-Time")
            }
        }

        // Sheet 5: Due Charges & Adjustments (if any)
        if (chargesList.isNotEmpty()) {
            val sheet5 = workbook.createSheet("Due Charges & Adjustments")
            val headers5 = arrayOf(
                "#", "Charge ID", "Customer Name", "Phone", "Amount Added (৳)", "Date", "Reason", "Note"
            )
            val hRow5 = sheet5.createRow(0)
            hRow5.heightInPoints = 26f
            for (i in headers5.indices) {
                val cell = hRow5.createCell(i)
                cell.setCellValue(headers5[i])
                cell.cellStyle = headerStyle
                sheet5.setColumnWidth(i, 20 * 256)
            }
            var rowIdx5 = 1
            for ((index, chg) in chargesList.sortedByDescending { it.timestamp }.withIndex()) {
                val row = sheet5.createRow(rowIdx5++)
                val name = custNameMap[chg.customerPhone] ?: ""
                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue("#CHG-${chg.id}")
                row.createCell(2).setCellValue(name)
                row.createCell(3).setCellValue(chg.customerPhone)
                val cAmt = row.createCell(4)
                cAmt.setCellValue(chg.amount)
                cAmt.cellStyle = currencyStyle
                row.createCell(5).setCellValue(dateFormat.format(Date(chg.timestamp)))
                row.createCell(6).setCellValue(chg.reason)
                row.createCell(7).setCellValue(chg.note ?: "")
            }
        }

            workbook.write(outputStream)
        } finally {
            try { workbook.dispose() } catch (_: Exception) {}
            try { workbook.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    fun exportCustomerStatementExcel(
        customer: CustomerDue,
        paymentsList: List<DuePayment>,
        salesList: List<Sale> = emptyList(),
        dueChargesList: List<DueCharge> = emptyList(),
        outputStream: OutputStream
    ) {
        val workbook = SXSSFWorkbook(100)
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val titleFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14.toShort()
            color = IndexedColors.DARK_BLUE.getIndex()
        }
        val titleStyle = workbook.createCellStyle().apply { setFont(titleFont) }

        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.getIndex()
            fontHeightInPoints = 11.toShort()
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
            setAlignment(HorizontalAlignment.CENTER)
            setVerticalAlignment(VerticalAlignment.CENTER)
        }
        val greenHeaderStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREEN.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
            setAlignment(HorizontalAlignment.CENTER)
            setVerticalAlignment(VerticalAlignment.CENTER)
        }

        val boldFont = workbook.createFont().apply { bold = true }
        val boldStyle = workbook.createCellStyle().apply { setFont(boldFont) }

        val summaryStyle = workbook.createCellStyle().apply {
            setFont(boldFont)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
        }
        val currencyStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }
        val summaryCurrencyStyle = workbook.createCellStyle().apply {
            setFont(boldFont)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }

        val sheet = workbook.createSheet("Customer Statement")

        // Customer Header Card
        val r0 = sheet.createRow(0)
        r0.createCell(0).apply { setCellValue("CUSTOMER KHATA STATEMENT"); cellStyle = titleStyle }

        val r1 = sheet.createRow(2)
        r1.createCell(0).apply { setCellValue("Customer Name:"); cellStyle = boldStyle }
        r1.createCell(1).setCellValue(customer.customerName)
        r1.createCell(3).apply { setCellValue("Outstanding Due:"); cellStyle = boldStyle }
        val cD1 = r1.createCell(4); cD1.setCellValue(customer.outstandingAmount); cD1.cellStyle = currencyStyle

        val r2 = sheet.createRow(3)
        r2.createCell(0).apply { setCellValue("Phone Number:"); cellStyle = boldStyle }
        r2.createCell(1).setCellValue(customer.customerPhone)
        r2.createCell(3).apply { setCellValue("Credit Limit:"); cellStyle = boldStyle }
        val cD2 = r2.createCell(4); cD2.setCellValue(customer.creditLimit); cD2.cellStyle = currencyStyle

        val r3 = sheet.createRow(4)
        r3.createCell(0).apply { setCellValue("Generated At:"); cellStyle = boldStyle }
        r3.createCell(1).setCellValue(dateFormat.format(Date()))
        r3.createCell(3).apply { setCellValue("Total Repaid:"); cellStyle = boldStyle }
        val cD3 = r3.createCell(4); cD3.setCellValue(paymentsList.sumOf { it.amountPaid }); cD3.cellStyle = currencyStyle

        // Section 1: Payment History Ledger
        val pHeaderRow = sheet.createRow(7)
        pHeaderRow.heightInPoints = 24f
        val pHeaders = arrayOf("Receipt #", "Payment Date", "Amount Paid (৳)", "Payment Method", "Reference Note")
        for (i in pHeaders.indices) {
            val cell = pHeaderRow.createCell(i)
            cell.setCellValue(pHeaders[i])
            cell.cellStyle = greenHeaderStyle
            sheet.setColumnWidth(i, 22 * 256)
        }

        var rowIdx = 8
        var totalPaid = 0.0
        for (p in paymentsList.sortedByDescending { it.timestamp }) {
            val r = sheet.createRow(rowIdx++)
            r.createCell(0).setCellValue("#PAY-${p.id}")
            r.createCell(1).setCellValue(dateFormat.format(Date(p.timestamp)))
            val c = r.createCell(2); c.setCellValue(p.amountPaid); c.cellStyle = currencyStyle
            r.createCell(3).setCellValue(p.paymentMethod)
            r.createCell(4).setCellValue(p.referenceNote ?: "")
            totalPaid += p.amountPaid
        }

        val pSumRow = sheet.createRow(rowIdx++)
        for (i in pHeaders.indices) pSumRow.createCell(i).cellStyle = summaryStyle
        pSumRow.getCell(0).setCellValue("TOTAL PAYMENTS")
        val psc = pSumRow.getCell(2); psc.setCellValue(totalPaid); psc.cellStyle = summaryCurrencyStyle

        // Section 2: Linked Invoices (if any)
        val custSales = salesList.filter { it.customerPhone == customer.customerPhone }
        if (custSales.isNotEmpty()) {
            rowIdx += 2
            val sHeaderRow = sheet.createRow(rowIdx++)
            sHeaderRow.heightInPoints = 24f
            val sHeaders = arrayOf("Invoice #", "Sale Date", "Grand Total (৳)", "Paid (৳)", "Due Incurred (৳)")
            for (i in sHeaders.indices) {
                val cell = sHeaderRow.createCell(i)
                cell.setCellValue(sHeaders[i])
                cell.cellStyle = headerStyle
            }

            var totalSalesAmt = 0.0
            var totalSalesDue = 0.0
            for (s in custSales.sortedByDescending { it.timestamp }) {
                val r = sheet.createRow(rowIdx++)
                val due = if (s.paymentMethod.equals("Due", ignoreCase = true)) s.grandTotal else (s.grandTotal - s.amountPaid).coerceAtLeast(0.0)
                r.createCell(0).setCellValue(String.format(Locale.US, "#INV-%05d", s.id))
                r.createCell(1).setCellValue(dateFormat.format(Date(s.timestamp)))
                val cG = r.createCell(2); cG.setCellValue(s.grandTotal); cG.cellStyle = currencyStyle
                val cP = r.createCell(3); cP.setCellValue(s.amountPaid); cP.cellStyle = currencyStyle
                val cD = r.createCell(4); cD.setCellValue(due); cD.cellStyle = currencyStyle
                totalSalesAmt += s.grandTotal
                totalSalesDue += due
            }

            val sSumRow = sheet.createRow(rowIdx++)
            for (i in sHeaders.indices) sSumRow.createCell(i).cellStyle = summaryStyle
            sSumRow.getCell(0).setCellValue("TOTAL INVOICES")
            val sc2 = sSumRow.getCell(2); sc2.setCellValue(totalSalesAmt); sc2.cellStyle = summaryCurrencyStyle
            val sc4 = sSumRow.getCell(4); sc4.setCellValue(totalSalesDue); sc4.cellStyle = summaryCurrencyStyle
        }

        // Section 3: Due Charges / Debits (if any)
        val custCharges = dueChargesList.filter { it.customerPhone == customer.customerPhone }
        if (custCharges.isNotEmpty()) {
            rowIdx += 2
            val cHeaderRow = sheet.createRow(rowIdx++)
            cHeaderRow.heightInPoints = 24f
            val cHeaders = arrayOf("Charge #", "Date", "Amount Added (৳)", "Reason", "Note")
            for (i in cHeaders.indices) {
                val cell = cHeaderRow.createCell(i)
                cell.setCellValue(cHeaders[i])
                cell.cellStyle = headerStyle
                sheet.setColumnWidth(i, 22 * 256)
            }

            var totalChargesAmt = 0.0
            for (c in custCharges.sortedByDescending { it.timestamp }) {
                val r = sheet.createRow(rowIdx++)
                r.createCell(0).setCellValue("#CHG-${c.id}")
                r.createCell(1).setCellValue(dateFormat.format(Date(c.timestamp)))
                val cAmt = r.createCell(2); cAmt.setCellValue(c.amount); cAmt.cellStyle = currencyStyle
                r.createCell(3).setCellValue(c.reason)
                r.createCell(4).setCellValue(c.note ?: "")
                totalChargesAmt += c.amount
            }

            val cSumRow = sheet.createRow(rowIdx)
            for (i in cHeaders.indices) cSumRow.createCell(i).cellStyle = summaryStyle
            cSumRow.getCell(0).setCellValue("TOTAL CHARGES")
            val sc2 = cSumRow.getCell(2); sc2.setCellValue(totalChargesAmt); sc2.cellStyle = summaryCurrencyStyle
        }

            workbook.write(outputStream)
        } finally {
            try { workbook.dispose() } catch (_: Exception) {}
            try { workbook.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    fun exportCustomers(customersList: List<Customer>, outputStream: OutputStream) {
        val workbook = SXSSFWorkbook(100)
        try {
            val sheet = workbook.createSheet("Customers Directory")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.getIndex()
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
        }

        val summaryFont = workbook.createFont().apply {
            bold = true
        }
        val summaryStyle = workbook.createCellStyle().apply {
            setFont(summaryFont)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
        }

        val headers = arrayOf(
            "Phone Number",
            "Customer Name",
            "Category Tag",
            "Loyalty Tier",
            "Loyalty Points",
            "Total Orders",
            "Lifetime Spend",
            "Outstanding Due",
            "Credit Limit",
            "Address",
            "Email",
            "Notes",
            "Last Visit / Activity"
        )
        val headerRow = sheet.createRow(0)
        for (i in headers.indices) {
            val cell = headerRow.createCell(i)
            cell.setCellValue(headers[i])
            cell.cellStyle = headerStyle
            sheet.setColumnWidth(i, 20 * 256)
        }

        var rowIdx = 1
        var totalLifetimeSpendSum = 0.0
        var totalDuesSum = 0.0
        var totalOrdersSum = 0

        for (c in customersList) {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(c.phone)
            row.createCell(1).setCellValue(c.name)
            row.createCell(2).setCellValue(c.tag)
            row.createCell(3).setCellValue(c.loyaltyTier)
            row.createCell(4).setCellValue(c.loyaltyPoints.toDouble())
            row.createCell(5).setCellValue(c.totalOrders.toDouble())
            row.createCell(6).setCellValue(c.lifetimeSpent)
            row.createCell(7).setCellValue(c.totalDue)
            row.createCell(8).setCellValue(c.creditLimit)
            row.createCell(9).setCellValue(c.address ?: "")
            row.createCell(10).setCellValue(c.email ?: "")
            row.createCell(11).setCellValue(c.notes ?: "")
            row.createCell(12).setCellValue(if (c.updatedAt > 0) dateFormat.format(Date(c.updatedAt)) else "")

            totalLifetimeSpendSum += c.lifetimeSpent
            totalDuesSum += c.totalDue
            totalOrdersSum += c.totalOrders
        }

        rowIdx++
        val summaryRow = sheet.createRow(rowIdx)
        for (i in headers.indices) {
            summaryRow.createCell(i).cellStyle = summaryStyle
        }
        summaryRow.getCell(0).setCellValue("TOTAL SUMMARY")
        summaryRow.getCell(5).setCellValue(totalOrdersSum.toDouble())
        summaryRow.getCell(6).setCellValue(totalLifetimeSpendSum)
        summaryRow.getCell(7).setCellValue(totalDuesSum)

            workbook.write(outputStream)
        } finally {
            try { workbook.dispose() } catch (_: Exception) {}
            try { workbook.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    fun exportSuppliers(suppliersList: List<Supplier>, outputStream: OutputStream) {
        val workbook = SXSSFWorkbook(100)
        try {
            val sheet = workbook.createSheet("Suppliers Directory")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
                setFillPattern(FillPatternType.SOLID_FOREGROUND)
                setFont(headerFont)
            }

            val summaryFont = workbook.createFont().apply {
                bold = true
            }
            val summaryStyle = workbook.createCellStyle().apply {
                setFont(summaryFont)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
                setFillPattern(FillPatternType.SOLID_FOREGROUND)
            }

            val headers = arrayOf(
                "Supplier ID",
                "Company Name",
                "Contact Person",
                "Phone Number",
                "Address",
                "Total Purchased",
                "Total Due",
                "Notes",
                "Last Updated"
            )
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
                sheet.setColumnWidth(i, 20 * 256)
            }

            var rowIdx = 1
            var totalPurchasedSum = 0.0
            var totalDuesSum = 0.0

            for (s in suppliersList) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(s.id.toDouble())
                row.createCell(1).setCellValue(s.companyName)
                row.createCell(2).setCellValue(s.name)
                row.createCell(3).setCellValue(s.phone)
                row.createCell(4).setCellValue(s.address ?: "")
                row.createCell(5).setCellValue(s.totalPurchased)
                row.createCell(6).setCellValue(s.totalDue)
                row.createCell(7).setCellValue(s.notes ?: "")
                row.createCell(8).setCellValue(if (s.updatedAt > 0) dateFormat.format(Date(s.updatedAt)) else "")

                totalPurchasedSum += s.totalPurchased
                totalDuesSum += s.totalDue
            }

            rowIdx++
            val summaryRow = sheet.createRow(rowIdx)
            for (i in headers.indices) {
                summaryRow.createCell(i).cellStyle = summaryStyle
            }
            summaryRow.getCell(0).setCellValue("TOTAL SUMMARY")
            summaryRow.getCell(5).setCellValue(totalPurchasedSum)
            summaryRow.getCell(6).setCellValue(totalDuesSum)

            workbook.write(outputStream)
        } finally {
            try { workbook.dispose() } catch (_: Exception) {}
            try { workbook.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    fun exportExpenses(expensesList: List<Expense>, outputStream: OutputStream) {
        val workbook = SXSSFWorkbook(100)
        try {
            val sheet = workbook.createSheet("Expenses & Cash In")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.getIndex()
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
        }

        val summaryFont = workbook.createFont().apply {
            bold = true
        }
        val summaryStyle = workbook.createCellStyle().apply {
            setFont(summaryFont)
        }

        val headers = arrayOf("ID", "Date & Time", "Type", "Title", "Category", "Payment Source", "Amount (৳)", "Cashier", "Note")
        val headerRow = sheet.createRow(0)
        for (i in headers.indices) {
            val cell = headerRow.createCell(i)
            cell.setCellValue(headers[i])
            cell.cellStyle = headerStyle
            sheet.setColumnWidth(i, if (i == 1 || i == 3 || i == 8) 24 * 256 else 16 * 256)
        }

        var rowIdx = 1
        var totalExpenseSum = 0.0
        var totalCashInSum = 0.0

        for (exp in expensesList) {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(exp.id.toDouble())
            row.createCell(1).setCellValue(dateFormat.format(Date(exp.timestamp)))
            row.createCell(2).setCellValue(if (exp.type == "CASH_IN") "Cash In" else "Expense")
            row.createCell(3).setCellValue(exp.title)
            row.createCell(4).setCellValue(exp.category)
            row.createCell(5).setCellValue(exp.paymentSource)
            row.createCell(6).setCellValue(exp.amount)
            row.createCell(7).setCellValue(exp.cashierName)
            row.createCell(8).setCellValue(exp.note ?: "")

            if (exp.type == "CASH_IN") {
                totalCashInSum += exp.amount
            } else {
                totalExpenseSum += exp.amount
            }
        }

        rowIdx++
        val summaryRowExp = sheet.createRow(rowIdx++)
        for (i in headers.indices) summaryRowExp.createCell(i).cellStyle = summaryStyle
        summaryRowExp.getCell(0).setCellValue("TOTAL EXPENSES")
        summaryRowExp.getCell(6).setCellValue(totalExpenseSum)

        val summaryRowCashIn = sheet.createRow(rowIdx)
        for (i in headers.indices) summaryRowCashIn.createCell(i).cellStyle = summaryStyle
        summaryRowCashIn.getCell(0).setCellValue("TOTAL CASH IN")
        summaryRowCashIn.getCell(6).setCellValue(totalCashInSum)

            workbook.write(outputStream)
        } finally {
            try { workbook.dispose() } catch (_: Exception) {}
            try { workbook.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    fun exportSalesReport(
        report: com.example.techyfipos.ui.viewmodel.SalesReportData,
        dateRangeLabel: String,
        outputStream: OutputStream,
        canViewPnl: Boolean = true
    ) {
        val workbook = SXSSFWorkbook(100)
        try {
            val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.getIndex()
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
            setFont(headerFont)
        }
        val boldFont = workbook.createFont().apply { bold = true }
        val boldStyle = workbook.createCellStyle().apply { setFont(boldFont) }

        val titleFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14.toShort()
            color = IndexedColors.DARK_BLUE.getIndex()
        }
        val titleStyle = workbook.createCellStyle().apply { setFont(titleFont) }

        // Sheet 1: Executive Summary
        val summarySheet = workbook.createSheet("Executive Summary")
        val titleRow = summarySheet.createRow(0)
        titleRow.createCell(0).apply {
            setCellValue("TECHYFI POS - SALES & FINANCIAL REPORT")
            cellStyle = titleStyle
        }
        val dateRow = summarySheet.createRow(1)
        dateRow.createCell(0).setCellValue("Report Period: $dateRangeLabel")
        dateRow.getCell(0).cellStyle = boldStyle

        val kpiItems = mutableListOf<Pair<String, Double>>()
        kpiItems.add("Gross Revenue (৳)" to report.totalRevenue)
        if (canViewPnl) {
            kpiItems.add("Gross Profit (৳)" to report.totalProfit)
        }
        kpiItems.add("Total Expenses (৳)" to report.totalExpenses)
        if (canViewPnl) {
            kpiItems.add("Net Profit (৳)" to report.netProfit)
        }
        kpiItems.add("Completed Transactions" to report.transactionCount.toDouble())
        kpiItems.add("Total Units Sold" to report.itemsSold)
        kpiItems.add("Total Discounts Given (৳)" to report.totalDiscount)
        kpiItems.add("Total Tax / VAT Collected (৳)" to report.totalTax)
        kpiItems.add("Total Refunds Issued (৳)" to report.totalRefunds)

        val kpiHeaderRow = summarySheet.createRow(3)
        kpiHeaderRow.createCell(0).apply { setCellValue("Financial Metric"); cellStyle = headerStyle }
        kpiHeaderRow.createCell(1).apply { setCellValue("Value"); cellStyle = headerStyle }
        summarySheet.setColumnWidth(0, 32 * 256)
        summarySheet.setColumnWidth(1, 20 * 256)

        var sRowIdx = 4
        for ((metric, value) in kpiItems) {
            val r = summarySheet.createRow(sRowIdx++)
            r.createCell(0).setCellValue(metric)
            r.createCell(1).setCellValue(value)
        }

        // Sheet 2: Top Products
        val productsSheet = workbook.createSheet("Top Selling Products")
        val prodHeaders = arrayOf("Rank", "Barcode", "Product Name", "Quantity Sold", "Revenue (৳)")
        val prodHeaderRow = productsSheet.createRow(0)
        for (i in prodHeaders.indices) {
            val cell = prodHeaderRow.createCell(i)
            cell.setCellValue(prodHeaders[i])
            cell.cellStyle = headerStyle
            productsSheet.setColumnWidth(i, if (i == 2) 30 * 256 else 18 * 256)
        }
        var pRowIdx = 1
        for ((index, prod) in report.topProducts.withIndex()) {
            val r = productsSheet.createRow(pRowIdx++)
            r.createCell(0).setCellValue((index + 1).toDouble())
            r.createCell(1).setCellValue(prod.barcode)
            r.createCell(2).setCellValue(prod.name)
            r.createCell(3).setCellValue(prod.quantity)
            r.createCell(4).setCellValue(prod.revenue)
        }

        // Sheet 3: Payment Mix
        val paymentSheet = workbook.createSheet("Payment Mix")
        val payHeaders = arrayOf("Payment Method", "Transactions", "Amount (৳)", "Share (%)")
        val payHeaderRow = paymentSheet.createRow(0)
        for (i in payHeaders.indices) {
            val cell = payHeaderRow.createCell(i)
            cell.setCellValue(payHeaders[i])
            cell.cellStyle = headerStyle
            paymentSheet.setColumnWidth(i, 20 * 256)
        }
        var payRowIdx = 1
        val totalPayAmount = if (report.totalRevenue > 0.0) report.totalRevenue else 1.0
        for (pay in report.paymentBreakdown) {
            val r = paymentSheet.createRow(payRowIdx++)
            r.createCell(0).setCellValue(pay.method)
            r.createCell(1).setCellValue(pay.transactionCount.toDouble())
            r.createCell(2).setCellValue(pay.amount)
            val share = (pay.amount / totalPayAmount) * 100.0
            r.createCell(3).setCellValue(String.format(Locale.US, "%.1f%%", share))
        }

        // Sheet 4: Daily Breakdown
        if (report.dailyBreakdown.isNotEmpty()) {
            val dailySheet = workbook.createSheet("Daily Breakdown")
            val dailyHeaders = if (canViewPnl) {
                arrayOf("Date", "Revenue (৳)", "Profit (৳)", "Transactions")
            } else {
                arrayOf("Date", "Revenue (৳)", "Transactions")
            }
            val dailyHeaderRow = dailySheet.createRow(0)
            for (i in dailyHeaders.indices) {
                val cell = dailyHeaderRow.createCell(i)
                cell.setCellValue(dailyHeaders[i])
                cell.cellStyle = headerStyle
                dailySheet.setColumnWidth(i, 20 * 256)
            }
            val dFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
            var dRowIdx = 1
            for (d in report.dailyBreakdown) {
                val r = dailySheet.createRow(dRowIdx++)
                r.createCell(0).setCellValue(dFormat.format(Date(d.timestamp)))
                r.createCell(1).setCellValue(d.revenue)
                if (canViewPnl) {
                    r.createCell(2).setCellValue(d.profit)
                    r.createCell(3).setCellValue(d.transactionCount.toDouble())
                } else {
                    r.createCell(2).setCellValue(d.transactionCount.toDouble())
                }
            }
        }

            workbook.write(outputStream)
        } finally {
            try { workbook.dispose() } catch (_: Exception) {}
            try { workbook.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }
}


