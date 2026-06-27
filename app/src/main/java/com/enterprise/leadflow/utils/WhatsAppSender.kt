package com.enterprise.leadflow.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.enterprise.leadflow.data.model.Lead
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

object WhatsAppSender {

    private fun getWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val w4b = "com.whatsapp.w4b"
        val wa = "com.whatsapp"

        return try {
            pm.getPackageInfo(w4b, PackageManager.GET_META_DATA)
            w4b
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                pm.getPackageInfo(wa, PackageManager.GET_META_DATA)
                wa
            } catch (e2: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    private fun copyAssetToCache(context: Context, assetName: String): File? {
        return try {
            val cacheDir = File(context.cacheDir, "templates")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val outFile = File(cacheDir, assetName)
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun sendTemplates(
        context: Context,
        lead: Lead,
        sendText: Boolean,
        sendImage: Boolean,
        sendPdf: Boolean
    ) {
        val waPackage = getWhatsAppPackage(context)
        if (waPackage == null) {
            Toast.makeText(context, "WhatsApp is not installed on this device.", Toast.LENGTH_LONG).show()
            return
        }

        val waPhone = "91" + PhoneUtils.sanitizePhoneNumber(lead.phone).takeLast(10)
        
        val messageText = if (sendText) {
            """
Hello *${lead.name}*, 👋

Thank you for connecting with *Finesse Overseas Education*, Kolhapur's trusted consultancy with over 25 years of expertise in Study Abroad guidance. 🎓✈️

We would love to invite you to our office to discuss your career and higher education plans in detail.

📍 *Visit Our Office:*
_Samarth Sakshi Plaza, First Floor,_
_Above Sohala Showroom, Beside Lenskart,_
_4th Lane, Rajarampuri, Kolhapur, Maharashtra (416008)_

🕒 *Office Hours:* 
Monday – Saturday: 10:30 AM to 6:30 PM

📞 *Contact:* +91 98500 69600
📧 *Email:* finesseoverseaseducation@gmail.com
🌐 *Website:* www.finesseoverseas.com

Please let us know what time works best for you so we can schedule an appointment. We look forward to meeting you! ✨

Best Regards,
*Finesse Overseas Education Team, Kolhapur.*
            """.trimIndent()
        } else {
            ""
        }

        val uris = ArrayList<Uri>()
        if (sendImage) {
            val imgFile = copyAssetToCache(context, "visiting_card.png")
            if (imgFile != null) uris.add(getUriForFile(context, imgFile))
        }
        if (sendPdf) {
            val pdfFile = copyAssetToCache(context, "brochure.pdf")
            if (pdfFile != null) uris.add(getUriForFile(context, pdfFile))
        }

        try {
            val intent = if (uris.isEmpty()) {
                // Text only
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$waPhone&text=${URLEncoder.encode(messageText, "UTF-8")}")
                    setPackage(waPackage)
                }
            } else if (uris.size == 1) {
                // Single Media (Image or PDF)
                Intent(Intent.ACTION_SEND).apply {
                    type = if (sendImage) "image/*" else "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    putExtra("jid", "$waPhone@s.whatsapp.net") // Direct to chat in ACTION_SEND
                    if (messageText.isNotEmpty()) {
                        putExtra(Intent.EXTRA_TEXT, messageText)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage(waPackage)
                }
            } else {
                // Multiple Media (Image AND PDF)
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra("jid", "$waPhone@s.whatsapp.net")
                    if (messageText.isNotEmpty()) {
                        putExtra(Intent.EXTRA_TEXT, messageText)
                        // In case WhatsApp drops it for */*, we can copy to clipboard for convenience
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Template Text", messageText))
                        Toast.makeText(context, "Text copied to clipboard just in case!", Toast.LENGTH_SHORT).show()
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage(waPackage)
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to launch WhatsApp.", Toast.LENGTH_SHORT).show()
        }
    }
}

