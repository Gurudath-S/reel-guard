# Build and Android integration

**Deliverable.** A single installable app at repository root with namespace and application ID `com.you.reelguard`. Use platform Activities, XML views, one application process, and Java source/target compatibility 1.8. Run Gradle itself on JDK 17. Keep the existing plan and specs outside the app source sets.

| Dependency | Placement and purpose |
|---|---|
| Android Gradle Plugin 8.5.2, Gradle wrapper 8.7, Build Tools 34.0.0 | Pin the application plugin and wrapper. Set compile/target SDK 34, min SDK 29. Use Google Maven and Maven Central for dependencies and Gradle Plugin Portal for plugin resolution. This combination supports the plan's API level. [AGP compatibility and patch releases](https://developer.android.com/build/releases/agp-8-5-0-release-notes) |
| Platform SDK | Accessibility, WindowManager, Activities, Handler, notifications, preferences and SQLite. Use native java.time at this minimum SDK. |
| com.google.code.gson:gson:2.11.0 | App implementation dependency for selector assets and sanitized JSON fixtures. Use explicit field reads and schema validation. This also gives local tests the same parser. [Gson release](https://github.com/google/gson/releases/tag/gson-parent-2.11.0) |
| junit:junit:4.13.2 | Local test dependency only. Use fake clocks, a recording enforcer and in-memory port implementations. [JUnit release](https://github.com/junit-team/junit4/releases/tag/r4.13.2) |

## Files the implementer supplies

| Path under the repository | Responsibility |
|---|---|
| settings.gradle, build.gradle, app/build.gradle, gradle/wrapper and wrapper launchers | Declare repositories, plugin and app module, SDK/Java levels, dependencies and reproducible build entry points. Keep the local SDK path in local.properties or ANDROID_HOME. |
| app/src/main/AndroidManifest.xml | Export the MAIN/LAUNCHER MainActivity. Keep WhitelistActivity internal. Register ReelGuardService exported for system binding, protected by android.permission.BIND_ACCESSIBILITY_SERVICE on the service declaration. Supply the android.accessibilityservice.AccessibilityService intent action and android.accessibilityservice metadata referencing the service XML. [Android service declaration](https://developer.android.com/guide/topics/ui/accessibility/service) |
| app/src/main/res/xml/accessibility_config.xml | Request window-state/content and view-scroll events, generic feedback, 100 ms notification timeout, window-content retrieval, report-view-IDs flag and the two target package names. Add flagRetrieveInteractiveWindows for active-window identification and overlay geometry. Provide a localized description. [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo) |
| Manifest queries and permissions | Declare package visibility queries for com.instagram.android and com.google.android.youtube to read their versions. Declare POST_NOTIFICATIONS for selector-health alerts on API 33+. Overlay authority comes from the connected service. [Package visibility](https://developer.android.com/training/package-visibility/declaring) · [Notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) |
| app/src/main/java/com/you/reelguard, assets, res | Place service, snapshot, detect, engine, enforce, data and ui packages here. Store selectors in assets/selectors.json. Use strings, accessible theme colors and layouts for dashboard, whitelist, warning and block. |
| app/src/debug and app/src/test/resources/fixtures | DebugActivity, TreeDumper, capture controls and their manifest/resources belong to the debug variant. Keep regression fixtures on the host. Provide variant-specific navigation hooks so the release build resolves all references. |

## Setup and acceptance

1. Check java and javac report 17. Set ANDROID_HOME, install command-line tools under cmdline-tools/latest, accept SDK licenses and install platform-tools, platforms;android-34 and build-tools;34.0.0. Generate and retain the pinned wrapper.
2. Build with `gradlew.bat assembleDebug`. Confirm `adb devices` shows an authorized phone and inspect `adb shell getprop ro.build.version.sdk`. Install via `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Measure a second incremental build and install against the plan's approximate 20-second development target.
3. Open the app, open system Accessibility settings, enable ReelGuard and return. For a grayed-out toggle, inspect App info for the device's Allow restricted settings option. Check status and package/event-type logs on both apps. Record device-specific battery settings if reconnect fails after sleep.
4. Assemble the release variant as well. Inspect the merged manifest for both variants, including scope of exported components, and confirm only the debug APK exposes capture controls. The skeleton milestone passes when a clean install can reach settings and receive target events.
