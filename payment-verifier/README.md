# 📱 Payment Verifier — Real-Time Mobile Money Confirmation for Small Shops

## The Problem

Small shops in Ethiopia commonly accept payments via **Telebirr** and **CBE Birr**, sent
to the shop owner's personal bank/mobile-money account. But the account owner isn't
always physically present at the shop. When a customer pays, the shopkeeper has no way
to instantly confirm the money arrived — they have to **call the account owner**, who
then has to check their phone and call back. This delay:

- Frustrates customers waiting at the counter
- Creates an opening for scammers to fake "payment sent" screenshots
- Depends entirely on someone being reachable by phone at that exact moment

## The Solution

This project automatically detects payment confirmation SMS (from CBE/Telebirr) the
moment they arrive, and displays them **instantly** on a screen at the shop counter —
no phone calls, no waiting.

```
Customer pays via Telebirr/CBE
        │
        ▼
SMS arrives on the account's phone
        │
        ▼
Android app reads the SMS, extracts amount + sender
        │
        ▼
Pushes it to Firebase Firestore (free tier — a few KB of data per transaction)
        │
        ▼
Shop counter tablet/webpage shows a big green "✅ Payment Received" card instantly
```

## Why this doesn't cost anything

- Reading incoming SMS on a phone is free — it's built into Android
- The only network use is a tiny amount of data sent to Firebase, roughly
  **10–20 KB per transaction**. Even a busy shop (~50 payments/day) uses under
  **1 MB/day**, or about **25–30 MB/month** — far less than one YouTube video.
- Firebase's free tier covers this usage many times over for a small shop.

## Project Structure

```
payment-verifier/
├── app/                        Android app (installs on the payment account's phone)
│   └── src/main/
│       ├── java/com/hana/paymentverifier/
│       │   ├── SmsReceiver.kt         Listens for SMS, parses amount/sender, pushes to Firebase
│       │   ├── PaymentAlertActivity.kt Full-screen alert shown on the phone itself
│       │   └── MainActivity.kt        Simple status screen + permission request
│       ├── res/layout/                UI layouts
│       └── AndroidManifest.xml        Permissions + component registration
├── dashboard/
│   └── index.html              Shop counter webpage — shows live incoming payments
└── README.md
```

## Setup Guide

### 1. Create a free Firebase project
1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create a new project
2. Enable **Realtime Database** (Build → Realtime Database → Create Database → start in
   test mode). This is used instead of Firestore because it works on Firebase's free
   **Spark** plan — no billing account required.
3. Add an **Android app** to the project (package name: `com.hana.paymentverifier`) — download
   the generated `google-services.json` and place it in the `app/` folder
4. Add a **Web app** to the same project — copy the `firebaseConfig` object it gives you.
   Also copy the **databaseURL** shown at the top of the Realtime Database page
   (looks like `https://YOUR_PROJECT-default-rtdb.firebaseio.com`) — you'll need both.

### 2. Configure the dashboard
Open `dashboard/index.html` and replace the placeholder `firebaseConfig` object with the
real one from your Firebase web app setup, making sure to include the `databaseURL` field.

### 3. Build the Android app
1. Open the `app/` folder in **Android Studio**
2. Make sure `google-services.json` is in place
3. Build → Generate Signed/Debug APK
4. Install the APK on the phone that holds the SIM/account receiving payments
5. Open the app once and grant SMS permission — after that it runs in the background

### 4. Open the dashboard on the shop counter
Open `dashboard/index.html` in any browser (Chrome on a tablet works great) and leave
it open. New payments will appear automatically with a sound alert.

## Important: Adjust the SMS parsing to real message formats

The regex patterns in `SmsReceiver.kt` (`extractAmount`, `extractPayerName`, `extractTxnId`)
are based on common CBE/Telebirr SMS formats, but exact wording can vary. Before relying
on this in a real shop:

1. Collect a few real payment confirmation SMS texts (mask any sensitive numbers)
2. Test them against the regex patterns
3. Adjust the patterns in `SmsReceiver.kt` until they reliably extract the right amount and sender

## Status / Roadmap

- [x] SMS detection and parsing
- [x] Real-time push to Firebase
- [x] Live dashboard with alerts
- [ ] Offline local log (SQLite) as a backup if internet is briefly down
- [ ] Daily summary / total collected per day
- [ ] Support for additional providers (M-Pesa, other banks)

## License

MIT — free to use, modify, and learn from.
