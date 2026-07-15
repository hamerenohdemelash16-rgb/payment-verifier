package com.hana.paymentverifier

import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen green "payment received" alert.
 * Shows even if the phone is locked, so whoever is at the counter
 * sees it immediately without unlocking anything.
 */
class PaymentAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen + turn screen on
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_payment_alert)

        val source = intent.getStringExtra("source") ?: ""
        val amount = intent.getStringExtra("amount") ?: "0"
        val payerName = intent.getStringExtra("payerName") ?: "Unknown"

        findViewById<TextView>(R.id.amountText).text = "$amount ETB"
        findViewById<TextView>(R.id.detailsText).text = "Received via $source\nfrom $payerName"

        playAlertSound()

        findViewById<TextView>(R.id.dismissButton).setOnClickListener { finish() }
    }

    private fun playAlertSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, notification).play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
