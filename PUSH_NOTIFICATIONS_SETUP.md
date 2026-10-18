# Turning on Push Notifications (Firebase Cloud Messaging)

The code for push notifications is already built and shipped. Until you complete
these steps it stays **dormant** — the app and backend work exactly as before.
Everything here is **free** (Firebase's Spark plan), but it does require a Google
account and a credit-card-free Firebase project.

There are two credentials to produce, and two places they go:

| Credential | Where it comes from | Where it goes |
|---|---|---|
| `google-services.json` | Firebase console (Android app config) | `android/app/google-services.json` (replace the placeholder, commit) |
| Service account key JSON | Firebase console (service account) | Render env var `Fcm__ServiceAccountJson` |

---

## 1. Create a Firebase project (free, ~2 min)

1. Go to https://console.firebase.google.com and click **Add project**.
2. Name it (e.g. `zoomers`). You can **disable Google Analytics** — not needed.
3. Wait for it to finish, then open the project.

## 2. Add the Android app → get `google-services.json`

1. In the project, click the **Android** icon ("Add app").
2. **Android package name:** `com.zoomers.app` (must match exactly).
3. Nickname/SHA-1 are optional — skip them. Click **Register app**.
4. **Download `google-services.json`.**
5. Replace the placeholder file in the repo:
   - Put your downloaded file at **`android/app/google-services.json`** (overwrite
     the existing placeholder).
   - Commit and push it. (This file is app configuration, not a secret — it ships
     inside every APK anyway, so committing it is fine.)
6. You can skip the "add the SDK / init" steps Firebase shows next — the app
   already has all of that.

## 3. Create a service account key → set it on Render

The backend needs to authenticate to FCM to *send* pushes.

1. In the Firebase console: **⚙️ Project settings → Service accounts**.
2. Click **Generate new private key** → confirm. A JSON file downloads.
   **Keep this one secret** — it grants send access.
3. Open the Render dashboard for the `zoomers-api` service → **Environment**.
4. Add a new environment variable:
   - **Key:** `Fcm__ServiceAccountJson`
   - **Value:** paste the **entire contents** of the downloaded JSON file
     (the whole `{ ... }` blob, on one line is fine).
5. Save — Render redeploys automatically. On boot the backend detects the key and
   switches from the logging no-op to real FCM delivery.

## 4. Get a push-enabled build and test

1. Let CI build a fresh APK from the commit that includes your real
   `google-services.json` (GitHub → Actions → latest Android CI run → Artifacts).
2. Install it, open the app, sign in, and **allow notifications** when prompted.
3. From a second account (or a friend's install), send you a DM, like your post,
   comment, or follow you. A notification should arrive — even with the app closed.

### Respecting user preferences
Delivery honors each user's **Settings → notifications** toggles (likes, comments,
follows, messages). Turning one off stops those pushes server-side.

---

## Notes & troubleshooting

- **Nothing happens / no notifications:** confirm (a) your real `google-services.json`
  is committed (not the placeholder — the placeholder's `project_id` is
  `zoomers-placeholder`), (b) `Fcm__ServiceAccountJson` is set on Render and the
  service redeployed, (c) you allowed the notification permission on the device.
- **The placeholder** lets the project build and run before you set Firebase up;
  with it in place, the app simply never obtains a push token. No crashes.
- **Costs:** FCM is free with no quota that a project this size will hit.
- **Security:** never commit the *service account* key. Only `google-services.json`
  (the client config) belongs in the repo.
