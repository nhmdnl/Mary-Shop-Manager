package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ReminderHelper {

    fun generateMessage(
        partyName: String,
        formattedBalance: String,
        shopName: String,
        shopPhone: String
    ): String {
        return "Hello $partyName, this is a friendly reminder from $shopName. " +
                "Your outstanding balance is $formattedBalance. " +
                "Kindly make payment via Mobile Money or Cash at your earliest convenience ($shopPhone). " +
                "Thank you for your business!"
    }

    fun openSms(context: Context, phone: String, message: String) {
        try {
            val cleanPhone = phone.trim()
            val uri = Uri.parse("smsto:${Uri.encode(cleanPhone)}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to generic share
            openShareChooser(context, message)
        }
    }

    fun openWhatsApp(context: Context, phone: String, message: String) {
        try {
            // Clean phone number for WhatsApp international format
            val cleanPhone = phone.replace(Regex("[^0-9]"), "")
            val uri = if (cleanPhone.isNotEmpty()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // If WhatsApp is not installed, open system chooser
            Toast.makeText(context, "WhatsApp not found. Opening other options...", Toast.LENGTH_SHORT).show()
            openShareChooser(context, message)
        }
    }

    fun openShareChooser(context: Context, message: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, message)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Send Payment Reminder").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open sharing options", Toast.LENGTH_SHORT).show()
        }
    }
}
