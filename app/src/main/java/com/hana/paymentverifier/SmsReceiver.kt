package com.hana.paymentverifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.util.regex.Pattern

/**
 * Listens for incoming SMS. When a message comes from CBE or Telebirr,
 * it extracts the amount + sender name and pushes it to Firebase Firestore
 * so the shop dashboard can show it instantly.
 *
 * NOTE: The regex patterns below are a starting point based on common
 * CBE/Telebirr SMS formats. Once you have real sample messages, test them
 * against these patterns and adjust — bank SMS wording can vary slightly.
 */
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
        val normalizedSender = sender.replace(" ", "") // handles "Tele Birr" vs "telebirr"
        val senderMatch = sender.contains("CBE", true) ||
                normalizedSender.contains("telebirr", true) ||
                sender.trim() == "127" // Telebirr sends from this short code, not a name
        val bodyMatch = body.contains("credited", true) ||
                body.contains("received", true) ||
                body.contains("deposit", true) ||
                body.contains("ተቀብለዋል") // Amharic: "received"
        // Fallback: even if the sender ID is an unrecognized short code, the message
        // body itself usually names the provider (e.g. "Thanks for Banking with CBE",
        // "using telebirr") — catch those cases too
        val bodyNamesProvider = body.contains("telebirr", true) ||
                body.contains("CBE", true) ||
                body.contains("cbe.com.et", true) ||
                body.contains("ቴሌብር") // Amharic: "Telebirr"

        return (senderMatch || bodyNamesProvider) && bodyMatch
    }

    // Matches things like "ETB 150.00", "Birr 150", "150.00 ETB", or Amharic "150.00 ብር"
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

    // Telebirr (English): "...from MULUKEN BELAY(2519****3999)..." -> name comes right before "("
    // Telebirr (Amharic): "...ከ Hana Leykun(2519****8747)..." -> same idea, "ከ" means "from"
    // CBE (with name):    "...from account 1**5595 (Tigist Wodajo Abebe)..." -> name is INSIDE parentheses
    // CBE (no name):      "...has been credited with ETB 25800.00..." -> no payer name in the message at all
    private fun extractPayerName(body: String): String {
        // Try CBE format first (name inside parentheses, after "account")
        val cbePattern = Pattern.compile("from account\\s+\\S+\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE)
        val cbeMatcher = cbePattern.matcher(body)
        if (cbeMatcher.find()) {
            return cbeMatcher.group(1)?.trim() ?: "Unknown"
        }

        // Try Telebirr English format (name right before an opening parenthesis)
        val telebirrPattern = Pattern.compile("from\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)*)\\s*\\(", Pattern.CASE_INSENSITIVE)
        val telebirrMatcher = telebirrPattern.matcher(body)
        if (telebirrMatcher.find()) {
            return telebirrMatcher.group(1)?.trim() ?: "Unknown"
        }

        // Try Telebirr Amharic format: "ከ NAME("
        val telebirrAmharicPattern = Pattern.compile("ከ\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)*)\\s*\\(")
        val telebirrAmharicMatcher = telebirrAmharicPattern.matcher(body)
        if (telebirrAmharicMatcher.find()) {
            return telebirrAmharicMatcher.group(1)?.trim() ?: "Unknown"
        }

        // Some CBE messages don't include the payer's name at all — nothing to extract
        return "Unknown"
    }

    // Telebirr (English): "...Your transaction number is DGB8QPUBWO..."
    // Telebirr (Amharic): "...የሂሳብ እንቅስቃሴ ቁጥርዎ DH28G8TP82 ነዉ..." -> "ቁጥርዎ" means "your number"
    // CBE: no explicit transaction number in the text, but the receipt URL ends with
    // a unique code (e.g. ".../v2-hfHCxzWhzPvPbLQUcKY0" or ".../BranchReceipt/FT26...")
    private fun extractTxnId(body: String): String {
        val telebirrPattern = Pattern.compile("transaction number is\\s+([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
        val telebirrMatcher = telebirrPattern.matcher(body)
        if (telebirrMatcher.find()) {
            return telebirrMatcher.group(1) ?: "N/A"
        }

        val telebirrAmharicPattern = Pattern.compile("ቁጥርዎ\\s+([A-Za-z0-9]+)")
        val telebirrAmharicMatcher = telebirrAmharicPattern.matcher(body)
        if (telebirrAmharicMatcher.find()) {
            return telebirrAmharicMatcher.group(1) ?: "N/A"
        }

        // Allow for an optional ":port" in the URL (e.g. cbe.com.et:100/...)
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
        // push() creates a unique auto-generated key for each new payment
        db.push().setValue(payment)
            .addOnSuccessListener { Log.d("SmsReceiver", "Payment saved: $amount from $payerName") }
            .addOnFailureListener { e -> Log.e("SmsReceiver", "Failed to save payment", e) }
    }

    // Shows a big full-screen alert directly on this phone too (useful if this
    // IS the shop's counter phone — see README "Option A" setup)
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
