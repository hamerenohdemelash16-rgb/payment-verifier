# TrustPay

**Real-time mobile money payment verification for small merchants.**

## The Problem

In Ethiopia, most small merchants accept payments through Telebirr and CBE Birr —
mobile money services where a customer "pays" by sending an SMS confirmation,
often just showing the merchant their phone screen. This creates a serious
trust gap: fake or edited screenshots are a common scam, and merchants have no
fast, reliable way to confirm a payment actually landed before handing over
goods or change. For small shop owners — like my family's food shop — this
means real financial loss from scams that a simple verification system could
prevent.

## The Solution

TrustPay listens for incoming payment SMS messages directly on the merchant's
phone, parses the real transaction data (sender, amount, payer name), and
pushes it instantly to a live, secured web dashboard — so a merchant can check
in real time whether a payment genuinely came through, without relying on a
screenshot.

## How It Works

- **Android app (Kotlin)** — `SmsReceiver.kt` listens for incoming SMS,
  detects and parses payment messages from known senders:
  - Telebirr: recognized via short code `127`
  - CBE Birr: payer name extracted from parentheses in the message body
- **Firebase Realtime Database** — parsed transactions are pushed live,
  protected by Firebase Authentication (only authorized accounts can read/write)
- **Web dashboard** — hosted live via GitHub Pages, protected by a password
  gate. Groups transactions by day, splits totals by Telebirr vs. CBE, and
  shows a combined daily total at a glance

## Tech Stack

- Kotlin (Android, SMS parsing)
- Firebase Realtime Database + Firebase Authentication
- HTML/JS web dashboard, hosted on GitHub Pages

## Security

Early versions had two issues that have since been fixed:
- A Firebase config file was accidentally committed — removed, and Firebase
  rules were locked down to require authentication
- The dashboard now requires a password before showing any transaction data

## Status / What's Next

Core pipeline (SMS → Firebase → live dashboard) is working end to end and
publicly hosted. Next: real-world testing at the actual shop, and refining
edge cases in SMS parsing.

## Why It Matters

This isn't hypothetical — it's built for a real problem I saw firsthand at
my family's shop. It's a small, focused fix to a trust gap that costs real
merchants real money every day.