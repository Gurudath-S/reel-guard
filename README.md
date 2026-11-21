# ReelGuard

ReelGuard tracks Instagram Reels and YouTube Shorts on Android. The dashboard shows today's counts and viewing time, supports combined or per-surface limits, and lets you exempt creators. See the [implementation specs](docs/specs/README.md).

## Turn limits on or off

Use **ReelGuard limits** near the top of the dashboard. When limits are off, Instagram Reels, Instagram feed videos, and YouTube Shorts scroll freely and usage still counts toward the daily quota while the accessibility service is connected. Turn limits on again manually to enforce the saved caps against today's totals.

To control limits from another app, open the Quick Settings editor and drag the **ReelGuard limits** tile into your visible tiles. The dashboard and tile share the same setting, and either control can change it while the accessibility service is disconnected.

## Build and install

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0. Set JAVA_HOME and ANDROID_HOME. From PowerShell at the project root, run:

1. `.\gradlew.bat testDebugUnitTest assembleDebug`
2. Connect an Android phone with USB debugging enabled and accept its authorization prompt. Run `adb devices -l` to confirm the connection.
3. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Open ReelGuard, choose **Accessibility settings**, and enable the service. The dashboard should show **Service connected**. Leave the limits blank for the first detection pass.

## GitHub releases

Each push to `main`, including a merged pull request, runs the release unit tests, builds and signs an APK, and publishes it as `reelguard.apk`. Download it from [GitHub Releases](https://github.com/Gurudath-S/reel-guard/releases/latest). Each new run uses a `build-N` tag and increments the Android version code.

For an on-demand release, open [Release APK](https://github.com/Gurudath-S/reel-guard/actions/workflows/release.yml) in GitHub Actions, select **Run workflow**, and choose `main`.

The workflow reads the PKCS#12 keystore with alias `reelguard` from the `REELGUARD_KEYSTORE_BASE64` Actions secret and its password from `REELGUARD_KEYSTORE_PASSWORD`. Keep a backup of both for future updates. Android requires the same signing key to install an update over an existing app. Moving from the local debug APK to the GitHub release requires a fresh install.

## First device pass

In the debug APK, open **Selector diagnostics** and arm a capture for Instagram or YouTube. Switch to the screen you want to inspect, then return to diagnostics for its classification and capture filename. Run `adb shell run-as com.you.reelguard ls files/fixtures` to list captures. Use `adb shell run-as com.you.reelguard cat files/fixtures/<filename>` to read a sanitized capture.

YouTube 21.45.82 (version code 1561310996) has a device-tested profile and sanitized Shorts and Home regression fixtures. On Android 14, three distinct Shorts counted, the three-reel cap exited Shorts to YouTube Home, and an ordinary video remained usable at the cap. Instagram 456.1.0.56.78 (version code 386215122) has a device-tested profile and sanitized Reels tab and Home feed regression fixtures. On Android 14, the Reels tab and Home feed video counted separately. At a two-reel Reels-only cap, opening the Reels tab returned to Home, and Messages and Profile were still accessible. Other app versions use provisional rules. See the [device acceptance matrix](docs/specs/15-verification.md).
