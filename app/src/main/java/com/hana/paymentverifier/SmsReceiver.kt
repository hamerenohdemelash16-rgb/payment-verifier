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
    val senderMatch = sender.contains("CBE", true) ||
            sender.contains("telebirr", true) ||
            sender.trim() == "127"
    val bodyMatch = body.contains("credited", true) ||
            body.contains("received", true) ||
            body.contains("deposit", true)
    val bodyNamesProvider = body.contains("telebirr", true) ||
            body.contains("CBE", true) ||
            body.contains("cbe.com.et", true)
    return (senderMatch || bodyNamesProvider) && bodyMatch
}

    // Matches things like "ETB 150.00", "Birr 150", "150.00 ETB"
    private fun extractAmount(body: String): String? {
        val pattern = Pattern.compile(
            "(?:ETB|Birr)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)|([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:ETB|Birr)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(body)
        return if (matcher.find()) {
            (matcher.group(1) ?: matcher.group(2))?.replace(",", "")
        } else null
    }

    // Telebirr: "...from MULUKEN BELAY(2519****3999)..."  -> name comes right before "("
// CBE:      "...from account 1**5595 (Tigist Wodajo Abebe)..." -> name is INSIDE the parentheses
private fun extractPayerName(body: String): String {
    // Try CBE format first (name inside parentheses, after "account")
    val cbePattern = Pattern.compile("from account\\s+\\S+\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE)
    val cbeMatcher = cbePattern.matcher(body)
    if (cbeMatcher.find()) {
        return cbeMatcher.group(1)?.trim() ?: "Unknown"
    }

    // Try Telebirr format (name right before an opening parenthesis)
    val telebirrPattern = Pattern.compile("from\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)*)\\s*\\(", Pattern.CASE_INSENSITIVE)
    val telebirrMatcher = telebirrPattern.matcher(body)
    if (telebirrMatcher.find()) {
        return telebirrMatcher.group(1)?.trim() ?: "Unknown"
    }

    return "Unknown"
}

    // Telebirr: "...Your transaction number is DGB8QPUBWO..."
// CBE: no explicit transaction number, but the receipt URL ends with a unique
// code (e.g. ".../v2-hfHCxzWhzPvPbLQUcKY0") which works as a reference
private fun extractTxnId(body: String): String {
    val telebirrPattern = Pattern.compile("transaction number is\\s+([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
    val telebirrMatcher = telebirrPattern.matcher(body)
    if (telebirrMatcher.find()) {
        return telebirrMatcher.group(1) ?: "N/A"
    }

    val cbePattern = Pattern.compile("cbe\\.com\\.et/(\\S+)", Pattern.CASE_INSENSITIVE)
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
