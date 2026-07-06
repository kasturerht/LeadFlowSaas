package com.nexaleads.app.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.nexaleads.app.data.model.Lead
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceGenerator {

    fun generateInvoicePdf(context: Context, lead: Lead, supportNumber: String): Uri? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val titlePaint = Paint()

        // Header Background
        paint.color = Color.parseColor("#8A2BE2") // ModernViolet
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 120f, paint)

        // Title
        titlePaint.color = Color.WHITE
        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.textSize = 32f
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TAX INVOICE", pageWidth / 2f, 60f, titlePaint)

        titlePaint.textSize = 14f
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("NexaLeads - Premium Products", pageWidth / 2f, 90f, titlePaint)

        // Reset Paint
        paint.color = Color.BLACK
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // Invoice Meta
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val currentDate = dateFormat.format(Date())
        val invoiceNumber = "INV-${lead.id.takeLast(6).uppercase()}"

        canvas.drawText("Invoice No: $invoiceNumber", 40f, 160f, paint)
        canvas.drawText("Date: $currentDate", 40f, 180f, paint)

        // Customer Details
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Billed To:", 40f, 220f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Name: ${lead.name}", 40f, 240f, paint)
        canvas.drawText("Phone: ${lead.phone}", 40f, 260f, paint)
        
        val fullAddress = listOf(lead.address, lead.city, lead.pincode).filter { it.isNotBlank() }.joinToString(", ")
        if (fullAddress.isNotBlank()) {
            canvas.drawText("Address: $fullAddress", 40f, 280f, paint)
        }

        // Table Header Background
        paint.color = Color.parseColor("#F3F4F6")
        canvas.drawRect(40f, 320f, pageWidth - 40f, 350f, paint)

        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Item / Product", 50f, 340f, paint)
        canvas.drawText("Qty", 350f, 340f, paint)
        canvas.drawText("Amount", 450f, 340f, paint)

        // Products List
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        var currentY = 380f
        
        val products = lead.product.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (products.isEmpty()) {
            canvas.drawText("Multiple Products", 50f, currentY, paint)
            canvas.drawText("1", 350f, currentY, paint)
            canvas.drawText("₹${lead.originalTotalValue}", 450f, currentY, paint)
            currentY += 30f
        } else {
            for (product in products) {
                val pName = product.substringBefore("(").trim()
                val qtyStr = product.substringAfter("(", "").substringBefore("x)").trim()
                val qty = qtyStr.toIntOrNull() ?: 1
                canvas.drawText(pName, 50f, currentY, paint)
                canvas.drawText(qty.toString(), 350f, currentY, paint)
                canvas.drawText("-", 450f, currentY, paint) // Detailed breakdown not always accurate due to combos
                currentY += 30f
            }
        }

        // Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, currentY + 10f, pageWidth - 40f, currentY + 10f, paint)
        
        // Summary
        paint.color = Color.BLACK
        currentY += 40f
        
        val origVal = lead.originalTotalValue.toIntOrNull() ?: 0
        val discVal = lead.discountAmount.toIntOrNull() ?: 0
        val finalVal = origVal - discVal

        paint.textAlign = Paint.Align.RIGHT
        if (origVal > 0) {
            canvas.drawText("Subtotal: ₹$origVal", pageWidth - 50f, currentY, paint)
            currentY += 25f
        }
        
        if (discVal > 0) {
            paint.color = Color.parseColor("#10B981") // Green
            canvas.drawText("Discount: -₹$discVal", pageWidth - 50f, currentY, paint)
            currentY += 25f
            paint.color = Color.BLACK
        }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        canvas.drawText("Total Amount: ₹$finalVal", pageWidth - 50f, currentY, paint)
        currentY += 20f

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.DKGRAY
        canvas.drawText("(Inclusive of all taxes)", pageWidth - 50f, currentY, paint)

        // Payment Status
        currentY += 50f
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        paint.color = Color.parseColor("#10B981")
        canvas.drawText("PAYMENT STATUS: PAID (Verified)", 40f, currentY, paint)

        // Footer
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Helpline / Support: $supportNumber", pageWidth / 2f, pageHeight - 80f, paint)
        canvas.drawText("Thank you for your business!", pageWidth / 2f, pageHeight - 60f, paint)

        pdfDocument.finishPage(page)

        // Save to cache dir
        try {
            val invoiceDir = File(context.cacheDir, "invoices")
            if (!invoiceDir.exists()) {
                invoiceDir.mkdirs()
            }
            // Always overwrite the same temp file to save storage
            val file = File(invoiceDir, "temp_invoice.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
        }
        return null
    }
}
