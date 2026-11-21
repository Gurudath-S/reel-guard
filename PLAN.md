# Reel/Shorts Limiter: End-to-End Plan

## 0. Key design decisions

| Decision | Choice | Why |
|---|---|---|
| Screen viewing mechanism | **AccessibilityService** | It's the only practical way to read another app's UI tree on a non-rooted phone. It sees view IDs, text and content descriptions, with no screen recording needed. |
| Blocking mechanism | Full-screen **accessibility overlay** plus `GLOBAL_ACTION_BACK` or `HOME` | The overlay needs no extra permission, and it covers the reel so scrolling is impossible. |
| Language | **Java, plain Activities + XML views** | You already have Java. This skips Kotlin, Compose, Room and kapt/ksp, which keeps builds fast and the footprint small. |
| IDE | **None required** (VS Code or any editor, plus Gradle CLI) | Android Studio is several GB. The command-line toolchain is under 1 GB. |
| Storage | SharedPreferences for settings, one small SQLite table for the event log | No annotation processors needed. |
| Target | minSdk 29, compileSdk/targetSdk 34 (Android 14) | Matches your phone. |

The riskiest part is detection: Instagram and YouTube view IDs are not a public API and change between versions. So the plan front-loads a discovery spike (Phase 1) and isolates all selectors in one config file.

---

## 1. Local environment setup and checks

**Install and verify, in this order (PowerShell):**

1. **JDK 17.** Android Gradle Plugin 8.x needs 17, and an older Java will fail.
   ```
   java -version        # want 17.x
   javac -version
   ```
   If yours is 8 or 11, install Temurin 17 and point `JAVA_HOME` to it.
2. **Android command-line tools.** Check whether your existing SDK has `cmdline-tools\latest\bin\sdkmanager.bat`. If not, download "command line tools only" and place them in `...\Android\Sdk\cmdline-tools\latest\`. The folder must be named `latest`.
3. **Environment variables:**
   ```
   ANDROID_HOME = C:\Users\<you>\AppData\Local\Android\Sdk   (or your path)
   PATH += %ANDROID_HOME%\platform-tools; %ANDROID_HOME%\cmdline-tools\latest\bin
   ```
4. **SDK packages (minimal):**
   ```
   sdkmanager --licenses
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
   Skip emulators, system images and NDK.
5. **Gradle:** download the Gradle 8.x zip once, run `gradle wrapper` in the project, then use `gradlew` from then on. You can delete the zip afterwards.
6. **Phone:**
   - Settings → About phone → tap Build number 7 times.
   - Developer options → enable USB debugging, and optionally Wireless debugging.
   - Plug in and accept the RSA prompt.
   ```
   adb devices          # must show "device", not "unauthorized"/"offline"
   adb shell getprop ro.build.version.sdk    # expect 34
   ```
   If the device doesn't appear, install your phone maker's USB driver or the Google USB driver.
7. **Smoke test:** build a blank "Hello" app with `gradlew assembleDebug`, then run `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Don't move on until this loop works. Install via adb, not by copying the APK to the phone, because Android 13+ puts sideloaded accessibility apps under "restricted settings" (see Phase 3).

**Gotchas to check early:** the first Gradle build downloads dependencies, so expect a few minutes. Also confirm that a one-line change rebuilds and reinstalls in under about 20 seconds.

---

## 2. Project layout

```
reelguard/
├─ app/src/main/
│  ├─ AndroidManifest.xml
│  ├─ assets/selectors.json            # all view IDs / patterns per app version
│  ├─ res/xml/accessibility_config.xml
│  └─ java/com/you/reelguard/
│     ├─ service/ReelGuardService.java      # AccessibilityService entry
│     ├─ snapshot/NodeSnapshot.java         # plain-Java copy of a UI node tree
│     ├─ snapshot/SnapshotBuilder.java      # AccessibilityNodeInfo → NodeSnapshot
│     ├─ detect/ScreenDetector.java         # interface
│     ├─ detect/InstagramDetector.java
│     ├─ detect/YouTubeDetector.java
│     ├─ detect/ScreenState.java            # app, Surface, creatorId, signature
│     ├─ engine/CounterEngine.java          # dedupe, dwell, counting
│     ├─ engine/PolicyEngine.java           # ALLOW / WARN / BLOCK
│     ├─ engine/DailyWindow.java            # reset time, rollover
│     ├─ enforce/OverlayEnforcer.java       # warning toast + block screen
│     ├─ data/Prefs.java, EventLogDb.java, WhitelistStore.java
│     ├─ ui/MainActivity.java               # today's stats, limit, status
│     ├─ ui/WhitelistActivity.java
│     └─ debug/TreeDumper.java              # logs/saves the UI tree (debug only)
└─ app/src/test/                           # JUnit, runs on laptop, no phone
   └─ resources/fixtures/*.json            # captured UI trees from your phone
```

**The most important structural rule:** the detectors, counter and policy engine work only on `NodeSnapshot` and plain Java objects, never on `AccessibilityNodeInfo`. That lets you test nearly all the logic on your laptop with `gradlew test` using trees captured from your real phone.

---

## 3. Module design

### 3.1 ReelGuardService (AccessibilityService)
- Config: `canRetrieveWindowContent=true`, `flagReportViewIds`, event types `typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled`, `packageNames="com.instagram.android,com.google.android.youtube"`, `notificationTimeout=100`.
- Limiting to those two packages saves battery, but you then get no events when you leave the apps. Handle that with an inactivity timer: if no events arrive for about 3 s, close the current "viewing session".
- Debounce content-changed events (about 250–400 ms) before taking a snapshot. These events fire constantly during video playback.

### 3.2 Detectors: classifying the surface
`ScreenState { app, surface, creatorId, contentSignature }`

Surfaces:
- `IG_REELS_TAB`: full-screen reels pager.
- `IG_FEED_VIDEO`: a video/reel-like post inside the home feed. This is the case you specifically want tracked.
- `YT_SHORTS`
- `OTHER`

How each is detected (**to be confirmed in the Phase 1 spike, not assumed**):
- **Reels tab:** the full-screen reels pager container is present and the bottom-nav "Reels" tab reports `selected`. Combine two signals so one renamed ID doesn't break it.
- **Feed video:** the home feed list is present, and a visible feed row contains a video or "Reel" marker, usually a content description such as "Reel by …" or a video player view. A reel in the feed gets counted as `IG_FEED_VIDEO`, never as `IG_REELS_TAB`.
- **Shorts:** the Shorts player container is present (the `reel_*` family of IDs in the YouTube app).
- **Creator:** read the author or channel node (Instagram username, YouTube `@handle`), lowercase and trim it.
- **Signature:** `hash(creatorId + caption text prefix)`, used to tell one reel from the next.

All IDs and patterns go in `selectors.json`, keyed by app version range, with generic fallbacks (text and content-description regexes). If you're in the app and *no* known selector matches, the app shows a "selectors may be outdated" notification instead of silently counting nothing.

### 3.3 CounterEngine: what counts as "one reel watched"
- A new `contentSignature` on a reel surface starts a candidate.
- It counts if it stays visible for at least N seconds (default 2 s, configurable). This stops fast flicks from inflating the count while still counting anything you actually looked at. Alternatively, count every swipe. Keep it as a setting.
- Dedupe: the same signature is not counted twice, and scrolling back up to a reel doesn't recount it. Keep a short ring buffer of recent signatures.
- Counters are kept per surface (`reels_tab`, `feed_video`, `shorts`), plus dwell seconds per surface.
- Whitelisted creators are logged separately and **not** counted toward the limit.

### 3.4 PolicyEngine
Inputs: today's counts and limit settings. Outputs `ALLOW`, `WARN` (at about 80% by default), or `BLOCK`.
- Limit configuration: one combined limit or separate limits per surface, in reel count, minutes, or both.
- Daily reset at a configurable time (for example 04:00, so late-night scrolling falls in the same "day").
- Once blocked, any new reel surface during the same day is blocked immediately on entry.

### 3.5 OverlayEnforcer
- **WARN:** a banner or toast overlay, "8/10 reels used."
- **BLOCK:** perform `GLOBAL_ACTION_BACK` to leave the reel surface, then show a full-screen opaque overlay (an accessibility overlay window, which needs no "draw over apps" permission) with the message and a "Close app" button that sends you HOME.
- If you reopen the Reels tab while blocked, the overlay returns within a fraction of a second.
- It only blocks reel surfaces. Normal Instagram (DMs, profile, feed without videos) and normal YouTube stay usable.
- Optional self-control features: a 30-second delay before an override or a limit increase, or no override at all.

### 3.6 Whitelist
- Entries: `platform + creatorId` (for example `ig:khanacademy`, `yt:@3blue1brown`).
- The whitelist screen has a text field with add and remove, and a "Quick add from last seen creators" list. The engine logs the last 20 creator IDs it saw, so you can tap to whitelist instead of typing.
- Matching is case-insensitive and exact on the handle.
- Limitation: the creator name only appears after the reel renders. The engine therefore waits for the debounce window before deciding whether a reel counts, so a whitelisted reel won't flash a block.

### 3.7 Storage and UI
- SharedPreferences hold limits, thresholds and the whitelist.
- An `events` SQLite table (`ts, app, surface, creator, signature, dwell_ms, counted`) powers daily stats and debugging.
- MainActivity shows service status (with a button that opens the accessibility settings), today's counts per surface, the limit editor, and links to the whitelist and a debug screen.

---

## 4. Build phases and tests

### Phase 1: Discovery spike (do this first, about half a day)
Goal: learn what Instagram and YouTube actually expose on *your* installed versions.
1. Open Instagram's Reels tab, then run:
   ```
   adb shell uiautomator dump /sdcard/ig_reels.xml
   adb pull /sdcard/ig_reels.xml
   ```
2. Repeat for: Instagram home feed with a reel visible, Instagram home feed with only photos, YouTube Shorts, and YouTube home.
3. Compare the dumps. Look for IDs, content descriptions, selected states and the creator node that **differ** between the surfaces.
4. Write down the selectors and save sanitized dumps as test fixtures.

**Exit criteria:** you can name a rule that separates Reels tab from feed video from Shorts from other, and a way to read the creator handle.

If a dump shows the player area as mostly empty nodes, fall back on bottom-nav selected state, scroll events, and creator text. Even so, the feed-video case may be the least reliable, so the plan treats it as best effort with heuristics you can tune.

### Phase 2: Skeleton app
Blank app, manifest, accessibility service that only logs the foreground package and event types via `adb logcat -s ReelGuard`. Verify events arrive.

### Phase 3: Enable the service on Android 14
- Settings → Accessibility → Installed apps → ReelGuard → On.
- If the toggle is greyed out ("Restricted setting"), go to App info → ⋮ → **Allow restricted settings**, then retry. Installing via adb usually avoids this, but check.
- Exclude the app from battery optimization, or the OS may kill the service.

### Phase 4: Snapshot + detectors with fixtures
Implement `NodeSnapshot`, `SnapshotBuilder`, and the detectors. Write JUnit tests that load each fixture and assert the surface and creator. Run `gradlew test`, with no phone needed.

### Phase 5: Counter + Policy engines (pure logic)
Unit-test with scripted event sequences:
- ten distinct reels count as 10
- one reel for 30 s counts as 1
- scrolling up and back down doesn't recount
- a flick under the dwell threshold doesn't count
- a whitelisted creator doesn't count
- WARN fires at 80% and BLOCK at 100%
- the daily rollover resets the counts

### Phase 6: Live wiring and overlay
Hook the service to the engines and enforcer, then run the on-device scenarios below.

### Phase 7: UI, whitelist, settings, stats
### Phase 8: Hardening (battery, edge cases, selector-health alert)

---

## 5. On-device test matrix (manual, your Android 14 phone)

| # | Scenario | Expected |
|---|---|---|
| 1 | Set limit to 3, scroll Reels tab | Counter reaches 3, then overlay blocks |
| 2 | Scroll the home feed past reels with the limit at 3 | `feed_video` counter increments, Reels-tab counter doesn't |
| 3 | Whitelisted IG creator's reel in Reels tab | Logged, not counted |
| 4 | Whitelisted YouTube channel's Short | Logged, not counted |
| 5 | Reach the limit, then use IG DMs, profile and YouTube long videos | Still usable |
| 6 | Reach the limit, then reopen the Reels tab | Immediate re-block |
| 7 | Leave and return mid-session, then force-stop the app | Session closes on timeout, counts persist |
| 8 | Reboot the phone | Service comes back, counts persist |
| 9 | Pass the daily reset time | Counters reset |
| 10 | Rotate, dark mode, split-screen | No crashes, no false counts |
| 11 | Update Instagram or YouTube | If detection breaks, the "selectors outdated" alert fires |

Useful commands during testing:
```
adb logcat -s ReelGuard
adb shell dumpsys accessibility | findstr ReelGuard
adb shell am force-stop com.you.reelguard
```

---

## 6. Known risks

1. **UI changes after app updates** break selectors. Mitigation: the selector file, fallbacks, the health alert, and fixture-based regression tests when you capture a new dump.
2. **Feed-video classification** is the fuzziest requirement. Expect to tune heuristics over a few days of real use.
3. **Service gets killed** by aggressive vendor battery managers. Mitigation: the battery exemption, plus a status check on app open.
4. **Easy to disable.** You can turn off the accessibility toggle at any time. Consider a "30-second friction" screen before changes, or accept this as a personal-use trade-off.
5. **Performance.** Walking a large UI tree on every event is expensive. Mitigation: debouncing, package filtering, shallow traversal, and caching the matched node.

---

## 7. Suggested order and effort

1. Environment and smoke test (1–2 hours)
2. Discovery spike (3–5 hours)
3. Skeleton + service (1–2 hours)
4. Detectors + tests (1 day)
5. Engines + tests (0.5–1 day)
6. Overlay + live wiring (1 day)
7. UI, whitelist, settings (1 day)
8. Hardening and a week of daily use (ongoing)