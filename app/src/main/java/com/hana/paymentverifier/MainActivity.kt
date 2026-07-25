package com.hana.paymentverifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Simple status screen. This app doesn't need a complicated UI —
 * it just needs to run in the background and have SMS permission.
 * Open this once after install to grant permission, then you can
 * leave the app alone; the SmsReceiver keeps working automatically.
 */
class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestSmsPermissionIfNeeded()
        updateStatus()
    }

    // Android requires the user to explicitly grant SMS permissions at runtime
    // (not just in the manifest) for privacy reasons. This checks if we already
    // have permission, and if not, shows the system permission dialog.
    private fun requestSmsPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                SMS_PERMISSION_CODE
            )
        }
    }

    // Called automatically by Android after the user responds to the
    // permission dialog (whether they tap Allow or Deny). We refresh the
    // status text either way so the screen reflects the current state.
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
    }

    // Updates the on-screen status message so the shop owner can glance at
    // the phone and immediately know if the app is working or needs attention.
    private fun updateStatus() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        findViewById<TextView>(R.id.statusText).text =
            if (granted) "✅ Running — watching for CBE/Telebirr payment SMS"
            else "⚠️ SMS permission needed — please grant it to continue"
    }
}
