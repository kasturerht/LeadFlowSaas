package com.nexaleads.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nexaleads.app.data.model.Lead
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

📞 *Contact:* +91 98347 83503
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
            Toast.makeText(context, "WhatsApp is not installed or failed to launch.", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateOrderMessage(
        customerName: String,
        products: String,
        address: String,
        paymentMode: String,
        includeAddress: Boolean,
        includePaymentLink: Boolean,
        includeDispatchNote: Boolean,
        includeSupportPhone: Boolean,
        originalTotal: String = "",
        discountAmount: String = "",
        customPaymentLink: String = "upi://pay?pa=merchant@icici&pn=Order%20Payment",
        language: String = "English",
        supportNumber: String = "+91 98347 83503"
    ): String {
        val cleanName = customerName.trim()
        val isGenericName = cleanName.isBlank() || 
                            cleanName.equals("Customer", ignoreCase = true) || 
                            cleanName.equals("Client", ignoreCase = true) || 
                            cleanName.equals("Lead", ignoreCase = true)
        
        val isCod = paymentMode.equals("COD", ignoreCase = true) || paymentMode.contains("Cash", ignoreCase = true)
        val isPendingPayment = !isCod && (
            paymentMode.contains("Link Requested", ignoreCase = true) || 
            paymentMode.contains("Pending", ignoreCase = true) ||
            (paymentMode.contains("Prepaid", ignoreCase = true) && includePaymentLink)
        )
        val showPaymentLink = !isCod && (includePaymentLink || isPendingPayment)

        val formattedProducts = if (products.isNotBlank()) {
            products.split(", ").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") { "• $it" }
        } else {
            "• [No products specified]"
        }

        val sb = StringBuilder()
        val lang = language.trim().lowercase()

        when {
            lang == "marathi" || lang == "मराठी" -> {
                if (isGenericName) {
                    sb.append("नमस्कार! 👋\n\n")
                } else {
                    sb.append("नमस्कार *").append(cleanName).append("* जी! 👋\n\n")
                }

                if (isPendingPayment) {
                    sb.append("तुमच्या ऑर्डरबद्दल मनःपूर्वक धन्यवाद! ✨\n")
                    sb.append("आम्हाला तुमची ऑर्डर विनंती प्राप्त झाली आहे.\n\n")
                } else {
                    sb.append("तुमच्या ऑर्डरबद्दल मनःपूर्वक धन्यवाद! ✨\n")
                    sb.append("तुमची ऑर्डर *यशस्वीरित्या कन्फर्म* झाली आहे.\n\n")
                }

                sb.append("━━━━━━━━━━━━━━━━━━\n")
                sb.append("🛒 *ऑर्डर तपशील (ORDER SUMMARY)*\n\n")
                sb.append(formattedProducts).append("\n\n")
                
                val origVal = originalTotal.toIntOrNull() ?: 0
                val discVal = discountAmount.toIntOrNull() ?: 0
                val finalVal = origVal - discVal
                
                if (discVal > 0 && finalVal > 0) {
                    val percentOff = (discVal * 100) / origVal
                    sb.append("🎁 *स्पेशल ऑफर!* 🎁\n")
                    sb.append("❌ मूळ किंमत: ~₹$origVal~\n")
                    sb.append("✅ *तुम्हाला मिळालेली किंमत: ₹$finalVal*\n")
                    sb.append("━━━━━━━━━━━━━━━━━━\n")
                    sb.append("🎉 *जबरदस्त! तुमचे थेट ₹$discVal ($percentOff% OFF) वाचले* 🎉\n")
                } else if (origVal > 0 && discVal == 0) {
                    sb.append("💰 *एकूण रक्कम:* ₹$origVal\n")
                } else if (origVal > 0 && discVal < 0) {
                    sb.append("💰 *एकूण रक्कम:* ₹$finalVal\n")
                }
                
                sb.append("━━━━━━━━━━━━━━━━━━\n\n")

                if (showPaymentLink) {
                    sb.append("💳 *पेमेंट स्थिती:* प्रलंबित (Payment Required)\n\n")
                    sb.append("ऑर्डर कन्फर्म करण्यासाठी आणि पार्सल त्वरित पाठवण्यासाठी कृपया खालील लिंकवरून सुरक्षित पेमेंट करा:\n\n")
                    sb.append("🔗 *पेमेंट लिंक (Pay Here):*\n").append(customPaymentLink).append("\n")
                    sb.append("_(GPay / PhonePe / Paytm / UPI सर्व उपलब्ध)_\n\n")
                    sb.append("✅ *टीप:* पेमेंट पूर्ण झाल्यावर कृपया स्क्रीनशॉट इथे पाठवा!\n\n")
                } else {
                    val displayMode = when {
                        paymentMode.equals("COD", ignoreCase = true) -> "कॅश ऑन डिलिव्हरी (COD)"
                        paymentMode.isNotBlank() -> paymentMode
                        else -> "कॅश ऑन डिलिव्हरी (COD)"
                    }
                    sb.append("💳 *पेमेंट पद्धत:* ").append(displayMode).append("\n\n")
                }

                if (includeAddress) {
                    sb.append("📍 *डिलिव्हरी पत्ता (Delivery Address):*\n")
                    if (address.isNotBlank()) {
                        sb.append(address.trim()).append("\n\n")
                        sb.append("⚠️ _कृपया पिन कोड तपासा आणि पत्ता बरोबर असल्यास *'CONFIRM'* असा रिप्लाय करा._\n\n")
                    } else {
                        sb.append("⚠️ _पार्सल पाठवण्यासाठी आम्हाला तुमचा संपूर्ण पत्ता आणि पिन कोड हवा आहे! कृपया इथे रिप्लाय करून पत्ता पाठवा._\n\n")
                    }
                }

                if (includeDispatchNote) {
                    sb.append("🚚 *डिलिव्हरी (Dispatch):* २४ तासांत एक्सप्रेस कुरिअरद्वारे रवाना होईल\n\n")
                }

                if (includeSupportPhone) {
                    sb.append("📞 *हेल्पलाईन (Support):* ").append(supportNumber).append("\n\n")
                }

                sb.append("आमच्यावर विश्वास ठेवल्याबद्दल धन्यवाद! 🙏")
            }

            lang == "hindi" || lang == "हिंदी" -> {
                if (isGenericName) {
                    sb.append("नमस्ते! 👋\n\n")
                } else {
                    sb.append("नमस्ते *").append(cleanName).append("* जी! 👋\n\n")
                }

                if (isPendingPayment) {
                    sb.append("आपके ऑर्डर के लिए धन्यवाद! ✨\n")
                    sb.append("हमें आपका ऑर्डर अनुरोध प्राप्त हो गया है।\n\n")
                } else {
                    sb.append("आपके ऑर्डर के लिए धन्यवाद! ✨\n")
                    sb.append("आपका ऑर्डर *सफलतापूर्वक कन्फर्म* हो गया है।\n\n")
                }

                sb.append("━━━━━━━━━━━━━━━━━━\n")
                sb.append("🛒 *ऑर्डर विवरण (ORDER SUMMARY)*\n\n")
                sb.append(formattedProducts).append("\n\n")
                
                val origVal = originalTotal.toIntOrNull() ?: 0
                val discVal = discountAmount.toIntOrNull() ?: 0
                val finalVal = origVal - discVal
                
                if (discVal > 0 && finalVal > 0) {
                    val percentOff = (discVal * 100) / origVal
                    sb.append("🎁 *स्पेशल ऑफर!* 🎁\n")
                    sb.append("❌ मूल कीमत: ~₹$origVal~\n")
                    sb.append("✅ *आपको मिली कीमत: ₹$finalVal*\n")
                    sb.append("━━━━━━━━━━━━━━━━━━\n")
                    sb.append("🎉 *शानदार! आपके सीधे ₹$discVal ($percentOff% OFF) बच गए* 🎉\n")
                } else if (origVal > 0 && discVal == 0) {
                    sb.append("💰 *कुल राशि:* ₹$origVal\n")
                } else if (origVal > 0 && discVal < 0) {
                    sb.append("💰 *कुल राशि:* ₹$finalVal\n")
                }
                
                sb.append("━━━━━━━━━━━━━━━━━━\n\n")

                if (showPaymentLink) {
                    sb.append("💳 *पेमेंट स्थिति:* लंबित (Payment Required)\n\n")
                    sb.append("ऑर्डर कन्फर्म करने और पार्सल तुरंत भेजने के लिए कृपया नीचे दिए गए लिंक से सुरक्षित पेमेंट करें:\n\n")
                    sb.append("🔗 *पेमेंट लिंक (Pay Here):*\n").append(customPaymentLink).append("\n")
                    sb.append("_(GPay / PhonePe / Paytm / UPI सभी उपलब्ध)_\n\n")
                    sb.append("✅ *नोट:* पेमेंट पूरा होने के बाद कृपया स्क्रीनशॉट यहाँ भेजें!\n\n")
                } else {
                    val displayMode = when {
                        paymentMode.equals("COD", ignoreCase = true) -> "कैश ऑन डिलीवरी (COD)"
                        paymentMode.isNotBlank() -> paymentMode
                        else -> "कैश ऑन डिलीवरी (COD)"
                    }
                    sb.append("💳 *पेमेंट मोड:* ").append(displayMode).append("\n\n")
                }

                if (includeAddress) {
                    sb.append("📍 *डिलीवरी पता (Delivery Address):*\n")
                    if (address.isNotBlank()) {
                        sb.append(address.trim()).append("\n\n")
                        sb.append("⚠️ _कृपया पिन कोड जांचें और पता सही होने पर *'CONFIRM'* रिप्लाई करें।_\n\n")
                    } else {
                        sb.append("⚠️ _पार्सल भेजने के लिए हमें आपका पूरा पता और पिन कोड चाहिए! कृपया यहाँ रिप्लाई करके अपना पता भेजें।_\n\n")
                    }
                }

                if (includeDispatchNote) {
                    sb.append("🚚 *डिलीवरी (Dispatch):* 24 घंटे के भीतर एक्सप्रेस कूरियर द्वारा भेजा जाएगा\n\n")
                }

                if (includeSupportPhone) {
                    sb.append("📞 *हेल्पलाइन (Support):* ").append(supportNumber).append("\n\n")
                }

                sb.append("हमसे जुड़ने के लिए धन्यवाद! 🙏")
            }

            else -> {
                // English Default
                if (isGenericName) {
                    sb.append("Hi there! 👋\n\n")
                } else {
                    sb.append("Hi *").append(cleanName).append("*! 👋\n\n")
                }

                if (isPendingPayment) {
                    sb.append("Thank you for your order! ✨\n")
                    sb.append("We have received your order request.\n\n")
                } else {
                    sb.append("Thank you for your order! ✨\n")
                    sb.append("Your order has been *successfully confirmed*.\n\n")
                }

                sb.append("━━━━━━━━━━━━━━━━━━\n")
                sb.append("🛒 *ORDER SUMMARY*\n\n")
                sb.append(formattedProducts).append("\n\n")
                
                val origVal = originalTotal.toIntOrNull() ?: 0
                val discVal = discountAmount.toIntOrNull() ?: 0
                val finalVal = origVal - discVal
                
                if (discVal > 0 && finalVal > 0) {
                    val percentOff = (discVal * 100) / origVal
                    sb.append("🎁 *SPECIAL OFFER APPLIED!* 🎁\n")
                    sb.append("❌ Regular Price: ~₹$origVal~\n")
                    sb.append("✅ *Your Special Price: ₹$finalVal*\n")
                    sb.append("━━━━━━━━━━━━━━━━━━\n")
                    sb.append("🎉 *CONGRATULATIONS! YOU SAVED ₹$discVal ($percentOff% OFF)* 🎉\n")
                } else if (origVal > 0 && discVal == 0) {
                    sb.append("💰 *Total Value:* ₹$origVal\n")
                } else if (origVal > 0 && discVal < 0) {
                    sb.append("💰 *Total Value:* ₹$finalVal\n")
                }
                
                sb.append("━━━━━━━━━━━━━━━━━━\n\n")

                if (showPaymentLink) {
                    sb.append("💳 *Payment Status:* Pending (Action Required)\n\n")
                    sb.append("To confirm instant dispatch for your order, please complete your secure payment via the link below:\n\n")
                    sb.append("🔗 *Secure Pay Link:*\n").append(customPaymentLink).append("\n")
                    sb.append("_(Supports GPay / PhonePe / Paytm / UPI)_\n\n")
                    sb.append("✅ *Note:* Please share a payment screenshot here once done for immediate dispatch!\n\n")
                } else {
                    val displayMode = when {
                        paymentMode.equals("COD", ignoreCase = true) -> "Cash on Delivery (COD)"
                        paymentMode.isNotBlank() -> paymentMode
                        else -> "Cash on Delivery (COD)"
                    }
                    sb.append("💳 *Payment Mode:* ").append(displayMode).append("\n\n")
                }

                if (includeAddress) {
                    sb.append("📍 *Delivery Address:*\n")
                    if (address.isNotBlank()) {
                        sb.append(address.trim()).append("\n\n")
                        sb.append("⚠️ _Please check your PIN code and reply *'CONFIRM'* if correct._\n\n")
                    } else {
                        sb.append("⚠️ _We need your full shipping address & PIN code! Please reply with your delivery address here to dispatch your order._\n\n")
                    }
                }

                if (includeDispatchNote) {
                    sb.append("🚚 *Dispatch:* Within 24 hours via Express Courier\n\n")
                }

                if (includeSupportPhone) {
                    sb.append("📞 *Support Helpline:* ").append(supportNumber).append("\n\n")
                }

                sb.append("Thank you for choosing us! 🙏")
            }
        }

        return sb.toString().trim()
    }

    fun sendOrderConfirmation(
        context: Context,
        phone: String,
        customerName: String,
        products: String,
        address: String,
        paymentMode: String,
        includeAddress: Boolean,
        includePaymentLink: Boolean,
        includeDispatchNote: Boolean,
        includeSupportPhone: Boolean,
        originalTotal: String = "",
        discountAmount: String = "",
        customPaymentLink: String = "upi://pay?pa=merchant@icici&pn=Order%20Payment",
        language: String = "English",
        supportNumber: String = "+91 98347 83503"
    ) {
        val cleanDigits = phone.filter { it.isDigit() }
        if (cleanDigits.length < 10) {
            Toast.makeText(context, "Invalid phone number: Must have at least 10 digits.", Toast.LENGTH_SHORT).show()
            return
        }
        val waPhone = "91" + cleanDigits.takeLast(10)
        val messageText = generateOrderMessage(
            customerName, products, address, paymentMode,
            includeAddress, includePaymentLink, includeDispatchNote, includeSupportPhone,
            originalTotal, discountAmount, customPaymentLink, language, supportNumber
        )
        
        val waPackage = getWhatsAppPackage(context)
        try {
            val encodedMsg = URLEncoder.encode(messageText, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$waPhone&text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                if (waPackage != null) {
                    setPackage(waPackage)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "WhatsApp is not installed or failed to launch.", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateDispositionMessage(
        status: String,
        customerName: String,
        productName: String = "",
        language: String = "English"
    ): String {
        val cleanName = customerName.trim()
        val isGenericName = cleanName.isBlank() || 
                            cleanName.equals("Customer", ignoreCase = true) || 
                            cleanName.equals("Client", ignoreCase = true) || 
                            cleanName.equals("Lead", ignoreCase = true)
        
        val lang = language.trim().lowercase()
        val sb = StringBuilder()

        when (status) {
            "No answer", "Busy", "Not reachable" -> {
                when {
                    lang == "marathi" || lang == "मराठी" -> {
                        if (isGenericName) sb.append("नमस्कार! 👋\n\n") else sb.append("नमस्कार *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("आम्ही तुम्हाला Finesse Overseas कडून संपर्क करण्याचा प्रयत्न केला, परंतु होऊ शकला नाही.\n\n")
                        sb.append("कृपया तुम्हाला सोयीस्कर अशी वेळ सांगा, म्हणजे आम्ही तुम्हाला पुन्हा कॉल करू शकू. 🙏")
                    }
                    lang == "hindi" || lang == "हिंदी" -> {
                        if (isGenericName) sb.append("नमस्ते! 👋\n\n") else sb.append("नमस्ते *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("हमने आपको Finesse Overseas से संपर्क करने का प्रयास किया, लेकिन बात नहीं हो पाई।\n\n")
                        sb.append("कृपया अपना सुविधाजनक समय बताएं, ताकि हम आपको फिर से कॉल कर सकें। 🙏")
                    }
                    else -> {
                        if (isGenericName) sb.append("Hi there! 👋\n\n") else sb.append("Hi *").append(cleanName).append("*! 👋\n\n")
                        sb.append("We tried calling you from Finesse Overseas but couldn't reach you.\n\n")
                        sb.append("Please let us know a good time to connect so we can call you back. 🙏")
                    }
                }
            }
            "Product Enquiry", "Demo Scheduled" -> {
                when {
                    lang == "marathi" || lang == "मराठी" -> {
                        if (isGenericName) sb.append("नमस्कार! 👋\n\n") else sb.append("नमस्कार *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("तुमच्या चौकशीबद्दल मनःपूर्वक धन्यवाद! ✨\n\n")
                        if (productName.isNotBlank()) sb.append("तुम्ही *").append(productName).append("* बद्दल माहिती विचारली होती.\n")
                        sb.append("अधिक माहितीसाठी तुम्ही कधीही संपर्क करू शकता. लवकरच आमचा प्रतिनिधी तुम्हाला सविस्तर माहिती देईल. 🙏")
                    }
                    lang == "hindi" || lang == "हिंदी" -> {
                        if (isGenericName) sb.append("नमस्ते! 👋\n\n") else sb.append("नमस्ते *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("आपकी पूछताछ के लिए धन्यवाद! ✨\n\n")
                        if (productName.isNotBlank()) sb.append("आपने *").append(productName).append("* के बारे में जानकारी माँगी थी।\n")
                        sb.append("अधिक जानकारी के लिए आप कभी भी संपर्क कर सकते हैं। जल्द ही हमारा प्रतिनिधि आपको विस्तृत जानकारी देगा। 🙏")
                    }
                    else -> {
                        if (isGenericName) sb.append("Hi there! 👋\n\n") else sb.append("Hi *").append(cleanName).append("*! 👋\n\n")
                        sb.append("Thank you for your inquiry! ✨\n\n")
                        if (productName.isNotBlank()) sb.append("Regarding your interest in *").append(productName).append("*.\n")
                        sb.append("Feel free to reach out for more details. Our representative will share complete information with you shortly. 🙏")
                    }
                }
            }
            "Follow-up", "Call Back" -> {
                when {
                    lang == "marathi" || lang == "मराठी" -> {
                        if (isGenericName) sb.append("नमस्कार! 👋\n\n") else sb.append("नमस्कार *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("आपल्या मागील चर्चेनुसार, मी फक्त फॉलोअप घेत आहे.\n\n")
                        sb.append("काही शंका असल्यास किंवा पुढील प्रक्रियेसाठी कृपया संपर्क करा. 🙏")
                    }
                    lang == "hindi" || lang == "हिंदी" -> {
                        if (isGenericName) sb.append("नमस्ते! 👋\n\n") else sb.append("नमस्ते *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("हमारी पिछली चर्चा के अनुसार, मैं बस फॉलो-अप ले रहा हूँ।\n\n")
                        sb.append("यदि आपका कोई सवाल है या आप आगे बढ़ना चाहते हैं, तो कृपया संपर्क करें। 🙏")
                    }
                    else -> {
                        if (isGenericName) sb.append("Hi there! 👋\n\n") else sb.append("Hi *").append(cleanName).append("*! 👋\n\n")
                        sb.append("As per our last discussion, I am just following up.\n\n")
                        sb.append("Let us know if you have any questions or if you're ready to proceed. 🙏")
                    }
                }
            }
            "Not Interested", "Wrong Number" -> {
                when {
                    lang == "marathi" || lang == "मराठी" -> {
                        if (isGenericName) sb.append("नमस्कार! 👋\n\n") else sb.append("नमस्कार *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("वेळ दिल्याबद्दल धन्यवाद!\n\n")
                        sb.append("भविष्यात कधीही काही मदत लागल्यास आम्हाला नक्की संपर्क करा. तुमचा दिवस शुभ जावो! 🙏")
                    }
                    lang == "hindi" || lang == "हिंदी" -> {
                        if (isGenericName) sb.append("नमस्ते! 👋\n\n") else sb.append("नमस्ते *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("अपना समय देने के लिए धन्यवाद!\n\n")
                        sb.append("भविष्य में कभी भी हमारी सेवाओं की आवश्यकता हो, तो बेझिझक संपर्क करें। आपका दिन शुभ हो! 🙏")
                    }
                    else -> {
                        if (isGenericName) sb.append("Hi there! 👋\n\n") else sb.append("Hi *").append(cleanName).append("*! 👋\n\n")
                        sb.append("Thank you for your time!\n\n")
                        sb.append("Feel free to reach out to us anytime in the future if your requirements change. Have a great day! 🙏")
                    }
                }
            }
            else -> {
                when {
                    lang == "marathi" || lang == "मराठी" -> {
                        if (isGenericName) sb.append("नमस्कार! 👋\n\n") else sb.append("नमस्कार *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("आमच्याशी संपर्क साधल्याबद्दल धन्यवाद! काही शंका असल्यास कृपया रिप्लाय करा. 🙏")
                    }
                    lang == "hindi" || lang == "हिंदी" -> {
                        if (isGenericName) sb.append("नमस्ते! 👋\n\n") else sb.append("नमस्ते *").append(cleanName).append("* जी! 👋\n\n")
                        sb.append("हमसे जुड़ने के लिए धन्यवाद! किसी भी जानकारी के लिए कृपया रिप्लाई करें। 🙏")
                    }
                    else -> {
                        if (isGenericName) sb.append("Hi there! 👋\n\n") else sb.append("Hi *").append(cleanName).append("*! 👋\n\n")
                        sb.append("Thank you for connecting with us! Please reply if you need any assistance. 🙏")
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    fun sendDispositionWhatsApp(
        context: Context,
        phone: String,
        status: String,
        customerName: String,
        productName: String = "",
        language: String = "English"
    ) {
        val cleanDigits = phone.filter { it.isDigit() }
        if (cleanDigits.length < 10) {
            Toast.makeText(context, "Invalid phone number.", Toast.LENGTH_SHORT).show()
            return
        }
        val waPhone = "91" + cleanDigits.takeLast(10)
        
        val messageText = generateDispositionMessage(status, customerName, productName, language)
        val waPackage = getWhatsAppPackage(context)
        
        try {
            val encodedMsg = URLEncoder.encode(messageText, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$waPhone&text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                if (waPackage != null) {
                    setPackage(waPackage)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "WhatsApp is not installed or failed to launch.", Toast.LENGTH_SHORT).show()
        }
    }
}

