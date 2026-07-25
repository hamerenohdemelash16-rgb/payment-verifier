package com.hana.paymentverifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.regex.Pattern

/**
 * Listens for incoming SMS. When a message comes from CBE or Telebirr,
 * it extracts the amount + sender name and pushes it to Firebase Realtime
 * Database so the shop dashboard can show it instantly.
 *
 * NOTE: The regex patterns below are a starting point based on common
 * CBE/Telebirr SMS formats. Once you have real sample messages, test them
 * against these patterns and adjust — bank SMS wording can vary slightly.
 *
 * NOTE: Firebase security rules require authentication to read/write, so
 * this receiver signs in with a fixed account before saving any payment.
 */
class SmsReceiver : BroadcastReceiver() {

    // Same account created in Firebase Console → Authentication → Users.
    // This lets the app satisfy the "auth != null && auth.uid === ..." rule.
    private val FIREBASE_EMAIL = "hamerenohdemelash16@gmail.com"
    private val FIREBASE_PASSWORD = "eyubaltena"

    // Entry point — Android calls this automatically whenever any SMS arrives.
    // We first check it's actually an SMS-received event, then loop through
    // every message in the intent (a single SMS can sometimes arrive as
    // multiple parts/messages bundled together).
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
                    ensureSignedInThenSave(source, amount, payerName, txnId, body)
                    showFullScreenAlert(context, source, amount, payerName)
                }
            }
        }
    }

    // Decides whether an incoming SMS is actually a payment confirmation,
    // not just any message from a bank/telecom number (e.g. balance checks,
    // promotions). We require BOTH a recognizable sender/provider AND
    // payment-related keywords in the body before treating it as a real transaction.
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

    // Makes sure we're authenticated with Firebase before writing. If already
    // signed in from a previous SMS, this skips straight to saving. Otherwise
    // it signs in first, then saves once that completes.
    private fun ensureSignedInThenSave(
        source: String,
        amount: String,
        payerName: String,
        txnId: String,
        rawSms: String
    ) {
        val auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            savePaymentToFirestore(source, amount, payerName, txnId, rawSms)
            return
        }

        auth.signInWithEmailAndPassword(FIREBASE_EMAIL, FIREBASE_PASSWORD)
            .addOnSuccessListener {
                Log.d("SmsReceiver", "Signed in to Firebase successfully")
                savePaymentToFirestore(source, amount, payerName, txnId, rawSms)
            }
            .addOnFailureListener { e ->
                Log.e("SmsReceiver", "Firebase sign-in failed, payment NOT saved", e)
            }
    }

    // Writes the parsed transaction to Firebase Realtime Database so the
    // web dashboard picks it up instantly via its live listener. Despite the
    // function name mentioning "Firestore", this actually uses Realtime
    // Database (FirebaseDatabase) — naming leftover from an earlier version.
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
