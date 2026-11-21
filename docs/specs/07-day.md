# DailyWindow and time sources

**Contract.** Use java.time.Instant, LocalDate, LocalTime, ZoneId and ZonedDateTime for calendar rules. Supply elapsed time separately through a ClockSource backed by SystemClock.elapsedRealtime on Android. Use paired wall/elapsed samples in tests. Persist wall instants and verified durations. Elapsed timestamps are valid only in the current process/boot session. [Android clock semantics](https://developer.android.com/reference/android/os/SystemClock)

| Method or value | Behavior |
|---|---|
| ClockSource.sample() → TimeSample | Capture wall instant, monotonic elapsed milliseconds and current device zone together. A fake provides explicit values and simulated wall jumps. |
| DailyWindow.containing(instant, zone, resetMinute) | Resolve the local reset on today's calendar date. If it lies after the instant, use the previous local date. Resolve the next local date's reset separately. Return startInclusive and endExclusive as epoch milliseconds, zone ID and applied reset minute. The start instant is the database window key. |
| resolveBoundary(localDate, reset, zone) | For a nonexistent local reset, use the first valid instant after the DST gap. For an ambiguous local time, choose the earlier occurrence. Use ZoneRules valid offsets/transition to implement this explicitly. |
| advance(storedWindow, sample, desiredReset) | First launch uses containing. While before the stored end, retain the stored window, including after a backward wall-clock adjustment. At the end, start a successor exactly at the old end. Its end is the first reset strictly after that instant in the desired zone/time. Advance to the interval containing now if multiple days elapsed. Persist the selected window before opening new visit rows. |
| splitInterval(previousSample, currentSample, windowEnd) | Split a confirmed continuous interval at a crossed boundary. For stable clocks map elapsed proportionally to wall time, round once and assign the remainder to the second segment. For a wall/elapsed delta discrepancy over one second, close at the last confirmed sample, advance the calendar and start a fresh observation. No clock jump creates extra dwell. |

**Reset edits and travel.** Freeze the current end, show it in the editor, and apply the latest requested zone/reset to the successor. This can create one shorter or longer transition window. Use actual instants as keys even when successive windows start on the same local date. Keep old rows assigned to their original keys. UI and service use one serialized repository operation to resolve the current window.

**At rollover.** Flush the old segment and open a new one for continuing verified use. Clear per-window dedupe and warning latches. Reload the new totals before enforcement. Start a fresh dwell qualification in the new window. The current reel can qualify once in each window. If the app is idle, resolve the window lazily on the next dashboard read or service event.

**Acceptance.** At a 04:00 reset, 03:59:59.999 belongs to the earlier window and 04:00:00.000 to the next. A four-second confirmed visit centered on reset contributes 2,000 ms to each. Cover DST gap/overlap, zone change, reset edit, a missed week, reboot, and a wall clock set backwards. Reopening the dashboard before the old end always reads the same window as the service.
