package com.hana.paymentverifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val sender = sms.originatingAddress ?: ""
            val body = sms.messageBody ?: ""

            Log.d("SmsReceiver", "SMS from $sender: $body")

            if (isPaymentSms(sender, body)) {
                val amount = extractAmount(body)
                val payerName = extractPayerName(body)
                val txnId = extractTxnId(body)
                val source = if (sender.contains("CBE", true) || body.contains("CBE", true) || body.contains("cbe.com.et", true)) "CBE" else "Telebirr"

                if (amount != null) {
                    savePaymentToFirestore(source, amount, payerName, txnId, body)
                    showFullScreenAlert(context, source, amount, payerName)
                }
            }
        }
    }

    private fun isPaymentSms(sender: String, body: String): Boolean {
        val normalizedSender = sender.replace(" ", "")
        val senderMatch = sender.contains("CBE", true) ||
                normalizedSender.contains("telebirr", true) ||
                sender.trim() == "127"

        val hasAmountPattern = Pattern.compile("\\d[\\d,]*\\.\\d{2}").matcher(body).find()

        val bodyNamesProvider = body.contains("telebirr", true) ||
                body.contains("Tele Birr", true) ||
                body.contains("CBE", true) ||
                body.contains("cbe.com.et", true) ||
                body.contains("ቴሌብር") ||
                body.contains("ተቀብለዋል")

        return (senderMatch || bodyNamesProvider) && hasAmountPattern
    }

    private fun extractAmount(body: String): String? {
        val pattern = Pattern.compile(
            "(?:ETB|Birr)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)|([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:ETB|Birr|ብር)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(body)
        return if (matcher.find()) {
            (matcher.group(1) ?: matcher.group(2))?.replace(",", "")
        } else null
    }

    private fun extractPayerName(body: String): String {
        // CBE format
        val cbePattern = Pattern.compile("from account\\s+\\S+\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE)
        val cbeMatcher = cbePattern.matcher(body)
        if (cbeMatcher.find()) {
            return cbeMatcher.group(1)?.trim() ?: "Unknown"
        }

        // Telebirr English format
        val telebirrPattern = Pattern.compile("from\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)*)\\s*\\(", Pattern.CASE_INSENSITIVE)
        val telebirrMatcher = telebirrPattern.matcher(body)
        if (telebirrMatcher.find()) {
            return telebirrMatcher.group(1)?.trim() ?: "Unknown"
        }

        // Telebirr Amharic format: "ከ [Name]([Phone])" -> e.g., "ከ Hana Leykun(2519****8747)"
        val telebirrAmharicPattern = Pattern.compile("ከ\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)*)\\s*\\(")
        val telebirrAmharicMatcher = telebirrAmharicPattern.matcher(body)
        if (telebirrAmharicMatcher.find()) {
            return telebirrAmharicMatcher.group(1)?.trim() ?: "Unknown"
        }

        return "Unknown"
    }

    private fun extractTxnId(body: String): String {
        val telebirrPattern = Pattern.compile("transaction number is\\s+([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
        val telebirrMatcher = telebirrPattern.matcher(body)
        if (telebirrMatcher.find()) {
            return telebirrMatcher.group(1) ?: "N/A"
        }

        // Telebirr Amharic format: "ቁጥርዎ [TxnId]" -> e.g., "ቁጥርዎ DH25G9856V"
        val telebirrAmharicPattern = Pattern.compile("ቁጥርዎ\\s+([A-Za-z0-9]+)")
        val telebirrAmharicMatcher = telebirrAmharicPattern.matcher(body)
        if (telebirrAmharicMatcher.find()) {
            return telebirrAmharicMatcher.group(1) ?: "N/A"
        }

        val cbePattern = Pattern.compile("cbe\\.com\\.et(?::\\d+)?/(\\S+)", Pattern.CASE_INSENSITIVE)
        val cbeMatcher = cbePattern.matcher(body)
        if (cbeMatcher.find()) {
            return cbeMatcher.group(1) ?: "N/A"
        }

        return "N/A"
    }

    private fun savePaymentToFirestore(
        source: String,
        amount: String,
        payerName: String,
        txnId: String,
        rawSms: String
    ) {
        val db = FirebaseDatabase.getInstance().getReference("transactions")
        val payment = hashMapOf(
            "source" to source,
            "amount" to amount,
            "payerName" to payerName,
            "txnId" to txnId,
            "rawSms" to rawSms,
            "timestamp" to System.currentTimeMillis(),
            "verified" to true
        )
        db.push().setValue(payment)
            .addOnSuccessListener { Log.d("SmsReceiver", "Payment saved: $amount from $payerName") }
            .addOnFailureListener { e -> Log.e("SmsReceiver", "Failed to save payment", e) }
    }

    private fun showFullScreenAlert(context: Context, source: String, amount: String, payerName: String) {
        val alertIntent = Intent(context, PaymentAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("source", source)
            putExtra("amount", amount)
            putExtra("payerName", payerName)
        }
        context.startActivity(alertIntent)
    }
}
