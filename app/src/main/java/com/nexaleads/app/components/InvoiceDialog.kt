package com.nexaleads.app.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.utils.InvoiceGenerator
import kotlinx.coroutines.launch

@Composable
fun InvoiceDialog(
    lead: Lead,
    supportNumber: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isGenerating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        containerColor = Color.White,
        title = {
            Text(
                "Send Invoice to Client",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    "You have successfully verified the payment for ${lead.name}. Would you like to generate and send a premium PDF tax invoice to them via WhatsApp?",
                    color = Color(0xFF475569),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Order Total: ₹${(lead.originalTotalValue.toIntOrNull() ?: 0) - (lead.discountAmount.toIntOrNull() ?: 0)}", fontWeight = FontWeight.Bold)
                        Text("Status: PAID - Verified", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isGenerating = true
                    // Launch in coroutine to avoid ANR during PDF creation
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            val pdfUri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                InvoiceGenerator.generateInvoicePdf(context, lead, supportNumber)
                            }
                            if (pdfUri != null) {
                                sendPdfViaWhatsApp(context, pdfUri, lead.phone)
                            } else {
                                Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isGenerating = false
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2)),
                shape = RoundedCornerShape(12.dp),
                enabled = !isGenerating
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Text("Generate & Send", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isGenerating
            ) {
                Text("Skip", color = Color(0xFF94A3B8))
            }
        }
    )
}

private fun sendPdfViaWhatsApp(context: Context, pdfUri: Uri, phone: String) {
    // Attempt to open the specific chat directly
    val formattedPhone = phone.replace("+", "").replace(" ", "")
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        putExtra("jid", "$formattedPhone@s.whatsapp.net") // Force specific chat
        setPackage("com.whatsapp")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        // Fallback to WhatsApp Business
        intent.setPackage("com.whatsapp.w4b")
        try {
            context.startActivity(intent)
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
