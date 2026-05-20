# OmniRead Play Store Launch Checklist

## Domain Setup

Buy a domain and point these hosts before production:

- `api.<domain>` -> OmniRead backend over HTTPS
- `www.<domain>` or `<domain>` -> public website/legal pages
- `admin.<domain>` -> admin panel, protected from public signups

Production Android release builds must use:

```text
OMNIREAD_API_BASE=https://api.<domain>
```

The release Gradle build intentionally fails if this is still `api.yourdomain.com` or the temporary IP.

## Required Public URLs

Publish these before Play review:

- Privacy policy: `https://www.<domain>/privacy`
- Account deletion: `https://www.<domain>/delete-account`
- Support/contact: `support@<domain>` or a contact page
- AdMob app-ads.txt: `https://www.<domain>/app-ads.txt`

The backend already has `DELETE /v1/auth/account`, which schedules account deletion with a 30-day purge window. The public deletion page should explain how users delete in-app and how to contact support.

## Play Console App Content

Complete these in Play Console:

- App access: provide reviewer login or guest-flow instructions.
- Ads: yes, OmniRead contains ads.
- Advertising ID: yes, used for advertising/analytics via AdMob/Google ads services.
- Content rating: answer as a reading/entertainment app; dark romance/horror/violence themes may push rating above Teen depending on catalog.
- Target audience: Teen+ / not child-directed.
- Data safety: declare account info, app activity/reading activity, purchases, user content/comments, device identifiers, diagnostics if collected by SDKs, and data sharing with Google/AdMob/Firebase/payment processors.
- Data deletion: provide the account deletion URL.
- Financial features: declare in-app purchases/subscriptions if coin packs or VIP are enabled.

## Current Release Permissions

Expected release permissions after manifest merge:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `com.android.vending.BILLING`
- `android.permission.AD_ID`
- `com.google.android.gms.permission.AD_ID`
- `android.permission.ACCESS_ADSERVICES_AD_ID`
- `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
- `android.permission.ACCESS_ADSERVICES_TOPICS`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.WAKE_LOCK`
- `com.google.android.c2dm.permission.RECEIVE`
- `com.omniread.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`

Not currently requested: camera, location, microphone, contacts, SMS, call log, storage, foreground service.

Push infrastructure is present through Firebase Messaging. Before production, add an explicit in-app notification opt-in flow for Android 13+ and explain that notifications are for story updates, rewards, account/activity alerts, and reading reminders.

## Play Products

Create these products in Play Console before enabling purchases:

- In-app products: `coins_50`, `coins_150`, `coins_350`, `coins_800`, `coins_2000`
- Subscription: `vip_monthly`

Digital coins/VIP must use Google Play Billing on Android.

## AdMob

After the Play listing exists:

- Link AdMob app store details to the Play listing.
- Update rewarded SSV callback to `https://api.<domain>/v1/payments/ad-reward/admob-callback`.
- Publish `app-ads.txt` on the website domain.
- Keep debug builds on Google demo ad IDs; do not click live ads during testing.

## Release Build

Use Android App Bundle for Play:

```bash
cd android
OMNIREAD_API_BASE=https://api.<domain> ./gradlew :app:bundleRelease
```

Then upload `android/app/build/outputs/bundle/release/app-release.aab` through Play Console.

For new personal developer accounts, expect closed testing with 12 testers for 14 days before production access.
