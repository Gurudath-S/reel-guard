# Verification and device acceptance

Record host/device, date, APK variant, Android API, target version codes, locale, settings, observed totals and result for each device run. These are implementation acceptance criteria. Collect the device evidence and test fixtures during implementation.

## Host verification

Place tests under app/src/test/java and sanitized JSON under app/src/test/resources/fixtures. Use JUnit 4, the real pure-Java catalog/detectors/engines, a fake clock and recording adapters. Use real Android for framework binding, SQLite durability and overlay touch behavior. Run `gradlew.bat testDebugUnitTest` for a focused pass and `gradlew.bat test assembleDebug assembleRelease` for the complete host/build check. [Android local unit tests](https://developer.android.com/training/testing/local-tests)

| Suite | Inputs and assertions |
|---|---|
| Fixture inventory and classification | Capture IG selected Reels tab, current and next item, mixed feed with dominant video, dominant photo, photo-only feed, DM and profile. Capture active YT Shorts, home with Shorts shelf and ordinary long video. Include a delayed handle, adjacent creator, scrolled-back item, unreadable root, sparse player and changed selector. Annotate surface/evidence, creator, signature equality groups and profile. Assert fallback behavior and no cross-row leakage. |
| Accounting and eligibility | Ten distinct two-second visits → 10. One verified 30 s visit → 1. A/B/A inside the ring → 2. A 1,999 ms flick → 0 counts with eligible time. Every-swipe mode counts first confirmed item. IG and YT whitelist matches → zero eligible count/time with exempt rows. Exercise mixed eligibility and an unresolved signature. |
| Policy | At count 7/10 ALLOW, 8/10 WARN, 10/10 BLOCK. For 1 min, 47,999 ms ALLOW, 48,000 WARN, 60,000 BLOCK. Repeat for combined and separate scopes including disabled cap and simultaneous count/time limits. Test immediate reentry and permitted ordinary screens. |
| Window and storage contract | Simulate 03:59:58 through 04:00:02, DST transitions, backward clock, reboot and reset change. Assert ledger segment keys, exact milliseconds, count qualification and no old warning carryover. Replay duplicate revisions and restore the last 64 identities in order. |
| Pipeline races | Arrange delayed capture, stale overlay decision, switched package, changed whitelist and outstanding write. Assert generation cancellation, one Back per episode, no offline dwell, and durable usage for already-earned counts. Burst content changes and assert bounded scheduling. |

## Android 14 device runs

Start from recorded settings and a known daily total. Use a fresh test install or a fresh window for independent quota scenarios. Install with ADB, enable the service and confirm its connection in the dashboard. Use `adb logcat -s ReelGuard` and `adb shell dumpsys accessibility` for supporting evidence.

| Plan case | Procedure and pass condition |
|---|---|
| 1. Count block | Set combined count 3 and watch three distinct nonmember Reels beyond dwell. Observe committed count 3 and one block episode. |
| 2. Feed attribution | Watch a dominant home-feed video between two photos. Repeat with a second distinct video. Only feed_video increments. The photo stays usable. |
| 3. Instagram exemption | Whitelist a known IG handle and watch it with the cap already reached. Verify that exempt time advances, eligible totals stay fixed and the shield stays hidden. |
| 4. YouTube exemption | Repeat with a YT handle and a different IG handle with the same base spelling. Only the exact platform match is exempt. |
| 5. Ordinary use | At cap, open DMs, profile, IG photo feed, YouTube home and a long video. No active overlay or unsolicited Back on any screen. |
| 6. Reentry | Reopen a blocked surface three times. Measure each actionable event to Back/shield, distinguishing creator resolution from processing latency. Each confirmed eligible entry re-blocks without another count. |
| 7. Leave / force-stop | Navigate Home mid-candidate. After 3 s idle, return and check no background dwell. Force-stop ReelGuard, reopen the launcher, check system service state and re-enable if needed. Verify committed usage and dedupe survive. |
| 8. Reboot | Reboot with service previously enabled, unlock and check system rebind, dashboard status, totals and dedupe. Record vendor-specific failures and test any battery-setting workaround separately. |
| 9. Reset | Set an upcoming reset in a new test window or run across the displayed effective boundary. Verify old and new segments, fresh counters and release of a blocked surface. |
| 10. Display configurations | Rotate, use dark mode and split screen. Change focused pane while blocked. Check readable controls, owner-bounded touch interception, no stuck shield and no duplicate count. |
| 11. Selector update | Install an available changed target version or use a debug build with an intentionally mismatched profile and fallback. Generate genuinely unknown target frames through the health threshold. Verify nonblocking behavior, one notice and dashboard recovery when matching selectors return. Record which method was used. |

**Additional device checks.** Exercise a minutes-only cap with one long eligible reel and a mixed cap with revisits. Check warning delivery once, Home button failure handling where reproducible, notification permission denied, invalid settings, delayed author, disabled service, signed-out targets and missing target app. Kill the process after a committed count and confirm restart does not invent time. Inspect debug vs release APK contents. If optional friction ships, verify early confirmation fails, 30 s confirmation succeeds and leaving cancels.

## Universal limits switch device runs

Run on Android 14 with installed Instagram and YouTube versions recognized by the app. Check tile discovery, state announcements, and dashboard synchronization on API 29 as well. [Universal limits switch specification](16-universal-limits-switch.md)

| Scenario | Pass condition |
|---|---|
| Continue counting | Turn limits off at a combined cap of 3. Watch 5 qualifying items across the three surfaces. Each surface scrolls freely and the dashboard shows 5 / 3. Turn limits on and verify that the next confirmed eligible item blocks before it adds another count. |
| Time caps and scope | Cross a minutes-only limit while off. Verify dwell continues above the cap. Re-enable with only one surface exhausted and confirm that only that surface blocks. Include an Instagram feed video, an exempt creator, and an ordinary YouTube video. |
| Warning and shield | Cross the warning threshold while off, resume below the cap, and see one warning. Pause and resume again and confirm the warning stays recorded. Disable from the tile while a shield is active and verify that touches reach the app when the shade closes. Enable with the shade open and confirm that Back waits for fresh eligible foreground evidence. |
| Two controls | Add the tile through Quick Settings. Leave an unsaved cap draft, toggle from the tile, return to the dashboard, and save. Compare both controls and verify the draft survived. Test rapid taps, rotation, TalkBack, a disconnected accessibility service, and removing and re-adding the tile. |
| Persistence | Upgrade with existing history and verify limits default to on. Turn them off, cross the daily reset, restart the process, reboot, and reconnect the service. Verify the setting stays off and new observations use the new quota window. |

**Handoff evidence.** Keep sanitized fixture names, version-specific rule observations, host results, API 34 device outcomes and measured event-to-overlay latency together. Record false positives, missed feed rows, silent-player timeouts, truncated trees and background capture activity during the plan's week of daily use. An unverified selector remains a discovery item, including when a similar version happened to work.
