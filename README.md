# ReelGuard

ReelGuard tracks Instagram Reels and YouTube Shorts on Android. The dashboard shows today's counts and viewing time, supports combined or per-surface limits, and lets you exempt creators. See the [implementation specs](docs/specs/README.md).

## Build and install

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0. Set JAVA_HOME and ANDROID_HOME. From PowerShell at the project root, run:

1. `.\gradlew.bat testDebugUnitTest assembleDebug`
2. Connect an Android phone with USB debugging enabled and accept its authorization prompt. Run `adb devices -l` to confirm the connection.
3. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Open ReelGuard, choose **Accessibility settings**, and enable the service. The dashboard should show **Service connected**. Leave the limits blank for the first detection pass.

## First device pass

In the debug APK, open **Selector diagnostics** and arm a capture for Instagram or YouTube. Switch to the screen you want to inspect, then return to diagnostics for its classification and capture filename. Run `adb shell run-as com.you.reelguard ls files/fixtures` to list captures. Use `adb shell run-as com.you.reelguard cat files/fixtures/<filename>` to read a sanitized capture.

YouTube 21.45.82 (version code 1561310996) has a device-tested profile and sanitized Shorts and Home regression fixtures. On Android 14, three distinct Shorts counted, the three-reel cap exited Shorts to YouTube Home, and an ordinary video remained usable at the cap. Instagram 456.1.0.56.78 (version code 386215122) has a device-tested profile and sanitized Reels tab and Home feed regression fixtures. On Android 14, the Reels tab and Home feed video counted separately. At a two-reel Reels-only cap, opening the Reels tab returned to Home, and Messages and Profile were still accessible. Other app versions use provisional rules. See the [device acceptance matrix](docs/specs/15-verification.md).
