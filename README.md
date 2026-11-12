# TikDown

Android app (Kotlin + Jetpack Compose) that takes a TikTok link, calls the API, and shows every download option the API returns (video without watermark, MP3 audio, images if any). Downloads are saved to `Downloads/TikDown/` on the device via the system DownloadManager.

## Features
- Splash, Home, History, My Videos and Settings screens
- Paste a link, scan a QR code, or share a link from the TikTok app
- Every option the API returns (video without watermark, MP3, images)
- Download progress dialog with cancel and time remaining
- My Videos: filters, search, built-in player, open with, share, copy link, open folder, delete
- Settings: default format, auto download, save to gallery, dark mode, notifications, clear cache

## Change the API
Edit `API_URL` in `app/build.gradle.kts`.

## Build the debug APK

### With GitHub Actions
1. Create a GitHub repo and push this folder:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/TikDown.git
   git push -u origin main
   ```
2. Open the **Actions** tab > **Build Debug APK** > latest run.
3. Download the `TikDown-debug-apk` artifact, unzip, and install `app-debug.apk`.

You can also trigger it manually: Actions > Build Debug APK > **Run workflow**.

### Locally
Open the folder in Android Studio and press Run, or run `gradle assembleDebug` (Gradle 8.7, JDK 17).
Output: `app/build/outputs/apk/debug/app-debug.apk`.
