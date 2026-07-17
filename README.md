# ZYShorts — Android App Project

This is a small Android app that wraps your Hugging Face Space
(`https://huggingface.co/spaces/Himanshujena564/Newtrail`) into a real,
installable app — its own icon, its own splash screen, no browser address
bar. It talks to your live Space, so your web version and app version stay
in sync automatically (same backend, same code).

You don't need Android Studio or any install on your laptop. The APK is
built for you in the cloud by GitHub Actions.

## Step 1 — Create a free GitHub account
Go to https://github.com and sign up (skip if you already have one).

## Step 2 — Create a new repository
1. Click the **+** icon (top right) → **New repository**
2. Name it `ZYShorts` (or anything you like)
3. Keep it **Public** or **Private**, either works
4. Click **Create repository** (leave it empty — don't add a README here)

## Step 3 — Upload these files
1. Unzip the file I gave you (`ZYShorts.zip`) on your laptop — no installer needed, just double-click / right-click → Extract, built into Windows and Mac
2. On your new GitHub repo page, click **"uploading an existing file"**
3. Drag the **entire contents** of the unzipped `ZYShorts` folder into the browser window (all folders and files, keeping the same structure: `app/`, `.github/`, `build.gradle`, etc.)
4. Scroll down, click **Commit changes**

⚠️ Important: make sure the `.github` folder (with the workflow file inside)
actually uploaded — GitHub sometimes hides dot-folders in the drag-and-drop
preview, but as long as you dragged the whole extracted folder in, it will
be included.

## Step 4 — Let it build
1. Go to the **Actions** tab of your repo
2. You should see a workflow run called "Build ZYShorts APK" already running (it starts automatically after upload)
3. Wait 2–4 minutes for it to finish (green checkmark ✅)

## Step 5 — Download your APK
1. Click on the finished workflow run
2. Scroll down to **Artifacts**
3. Click **ZYShorts-app** to download a zip containing `app-debug.apk`
4. Transfer that `.apk` to your Android phone (email it to yourself, Google Drive, USB, WhatsApp — any method) and tap it to install

Your phone may show a "unknown sources" warning the first time — that's
normal for any app not from the Play Store. Just allow it for this install.

## Updating the app later
If you change your Hugging Face Space and want the app to point elsewhere,
edit this one line in `app/src/main/java/com/zyshorts/app/MainActivity.kt`:

```kotlin
private val appUrl = "https://himanshujena564-newtrail.hf.space"
```

Commit the change on GitHub, and Actions will automatically rebuild a new
APK for you.

## Notes
- This build is a **debug APK** — perfectly installable and testable on any
  Android phone. If you ever want to publish to the Google Play Store, that
  requires a signed **release** build and a Play Developer account
  (one-time $25 fee) — a separate step I can help with when you're ready.
- Your Space needs to be awake/reachable for the app to load, same as
  visiting it in a browser.
