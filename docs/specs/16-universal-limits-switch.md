# Universal limits switch

The **ReelGuard limits** switch applies to Instagram Reels, Instagram feed videos, and YouTube Shorts. The dashboard and Android Quick Settings tile control the same saved setting. Usage continues to count while limits are off and the accessibility service is connected.

| Mode | Behavior |
|---|---|
| On | Enforce the saved combined or per-surface quota, including warning thresholds and creator exemptions. Evaluate the latest totals on fresh foreground evidence. |
| Off | Track qualifying counts, verified dwell, and exempt usage. Clear existing quota overlays and suppress quota warnings, shields, Back, and Home actions. |
| Accessibility service disconnected | Allow preference changes from either control. Show the dashboard's independent service status. Apply the saved mode when Android reconnects the service. |

The setting defaults to on for a new or upgraded installation. An explicitly saved off state survives a daily reset, process restart, service reconnect, and reboot. Limits resume when the user turns them on. The quota window rolls over normally, and same-day usage accumulated while off affects the cap immediately upon resuming. A daily cap of 10 can therefore show 14 / 10.

## Controls and persistence

- Place the native dashboard switch above usage, with a status message when limits are off or the accessibility service is disconnected. Keep it independent of the cap editor's Save settings and Reset draft buttons, and retain normal draft restoration on rotation.
- Register a toggleable Android tile with the same label, a monochrome icon, and the system tile-binding permission. Refresh from the repository while listening. The user adds it through the Quick Settings editor. Removing the tile leaves the saved mode in place. [Android Quick Settings tile guide](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- Store `enforcement_enabled.v1` as a Boolean in the existing preferences, independent of `settings.v1` and the usage database. The repository provides a lightweight mode read, change notifications on the main thread, an explicit dashboard setter, and a tile toggle that reads and inverts the last successful value on the serialized worker.
- Display the last confirmed mode until a write succeeds. A failed write restores that mode and reports “Couldn’t change ReelGuard limits. Try again.” Preference reads and changes work independently of dashboard usage queries.

## Accounting and enforcement

1. Apply the mode in both policy evaluations around the counter checkpoint. While off, policy returns ALLOW even when the scope is exhausted, so the counter keeps processing eligible and exempt visits. Continue normal count confirmations and daily-window scheduling. Skip quota-specific time deadlines while off.
2. Suspend quota actions on the main thread when a mode write starts. Clear any warning or shield immediately and cancel its expiry callback. Keep usage and deduplication history. When the last queued write completes, reconcile the saved mode.
3. Tag captures with both the foreground revision and the mode revision. Validate both revisions and window ownership before applying quota actions. Recheck before Back, shield attachment, and the shield's Home action. Refresh the shield's validator when a later verified decision confirms the same block episode.
4. Request a fresh observation when the mode changes during an active session. After inactivity, wait for a supported target event. A foreground Quick Settings shade is a system window and cannot authorize an action. A Back action already carried out by Android cannot be reversed by the switch.
5. Preserve the warning key and record delivery only after the banner attaches. Crossing a warning threshold while off leaves the warning due. A previously delivered warning stays recorded for its original scope and daily window.

## Verification

Run `gradlew.bat test assembleDebug assembleRelease`. Host coverage includes allowing and recording above-cap usage across all surfaces, preserving warning identity, ordering overlapping writes, and rejecting delayed actions after off–on transitions or a failed write. Use the Android device checks for System UI behavior, overlay input, and persistence. [Verification and device acceptance](15-verification.md)
