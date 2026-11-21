# Prefs, WhitelistStore and EventLogDb

**Ownership.** Keep one application-scoped repository in the default process. Serialize setting edits, window resolution, usage writes and stats reads on a single executor. Publish immutable committed results to the service and Activities. Use SharedPreferences in private mode, Gson for structured preference values, and Android SQLiteOpenHelper, SQLiteDatabase, ContentValues and Cursor for the one events table. [Android SQLite guide](https://developer.android.com/training/data-storage/sqlite)

## Preferences and validation

| Logical key | Type, legal values and default |
|---|---|
| settings.v1 | One versioned object. scopeMode COMBINED or PER_SURFACE (default COMBINED). Store four cap pairs (combined and one per surface). Each count cap is absent or 1–100,000. Each time cap is absent or 1–1,440 whole minutes. All absent initially. |
| counting and warning fields within settings.v1 | countMode DWELL or EVERY_SWIPE (DWELL), dwellSeconds 1–60 (2), warnPercent 1–99 (80), resetMinute 0–1439 (240). Retain the inactive dwell value in swipe mode. |
| whitelist.v1 and recentCreators.v1 | A copied canonical string set and a newest-first JSON array of up to 20 distinct creator keys. Only WhitelistStore mutates these keys. |
| window.v1 | Persist active start/end instants, applied zone and reset minute. Recover on connect or UI read. A new window begins at the previous end per [DailyWindow](07-day.md). |
| warnings.v1 and health.v1 | Current window and delivered banner keys. Per-package diagnostic summary including profile/version, last reason, alert cooldown and acknowledgment. |

**Prefs operations.** readSettings returns an immutable validated snapshot and revision. saveSettings validates every active and retained field, serializes the whole object, writes on the repository executor and publishes the new revision after successful commit. Load defaults for absent optional fields. Preserve unrelated keys on edits. Invalid stored settings produce a visible configuration error and disable quota enforcement until repaired. Return write failures to the editor and keep its draft. Share the same revision notification path with whitelist edits.

## SQLite database v1

An events row is one visit segment in one window. Retries address the same visit. Use stable text enum codes (`ig`, `yt`, `reels_tab`, `feed_video`, `shorts`), 64-bit integer times in milliseconds and bound query parameters.

| Column(s) | Type and invariant |
|---|---|
| id, visit_id | INTEGER PRIMARY KEY for insertion order, TEXT UNIQUE NOT NULL for the generated segment UUID. Allocate the UUID once and retain across retries. |
| window_start_ms, ts, last_ts | INTEGER NOT NULL. Window key, wall time at segment start and wall time at last confirmation. Clip/split at reset. Use the window key for daily lookups. |
| app, surface, creator, signature | TEXT. App and surface required, enforce valid platform/surface pairs. Creator is the canonical platform key or NULL. Signature is hex digest or NULL. Store no raw caption or tree. |
| exempt, dwell_ms, counted | INTEGER NOT NULL. Boolean exempt, nonnegative verified milliseconds, boolean counted. Exempt forces counted zero. Counted requires a signature. A revisit may have dwell with counted zero. |
| revision | INTEGER NOT NULL, monotonically increasing within a visit. Ignore equal/older revisions. Writes contain absolute dwell and counted values. |

Index window_start_ms and surface for stats, and window_start_ms, app, counted and id for dedupe restoration. Do not enforce all-day uniqueness on signatures, since an evicted signature may qualify again.

| Repository operation | Implementation contract |
|---|---|
| applyStep(checkpoints, window) → Totals | In one DB transaction, update newer revisions or insert missing visits, then calculate the committed window totals. For a cross-window step include both sides in the same transaction. Keep already-committed values on retry. On failure roll back the entire batch. |
| readTotals(window) | Group by surface and exempt. Sum counted and dwell_ms for eligible rows. For exempt rows return number of segments and summed dwell, labeled as visits/time. Materialize zeroes for missing surfaces. Policy consumes only the eligible columns. |
| readRecentCounted(window, platform, capacity) | Read counted rows newest id first, retain the first occurrence of each signature until capacity is reached. Reverse to restore oldest-to-newest insertion order. Ignore uncounted visits. |
| loadDay(sample), subscribe(listener), unsubscribe(listener) | Resolve window then read totals, settings, service health and recent history from one serialized view. Deliver snapshots after commits. Unregister UI listeners on stop. Maintain a generation number to discard stale UI loads. |
| onCreate / onUpgrade | Create table and indexes transactionally. Use versioned forward migrations that preserve historical rows. Reject unsupported database versions with a visible storage error. |

**Restart and partial failure.** The events table is authoritative for usage. Do not mirror counts in preferences. A process kill can lose only uncheckpointed observations under normal write conditions. Never restore an open timer or fill the offline interval. A stored window may advance independently of a visit transaction. Replay pending writes by their own window keys, and resolve the current window afresh for UI/policy. Serialize retries before accepting later usage changes. On a database failure suspend accounting and enforcement, release the overlay, flag a storage error and retry on the next connection or a bounded delayed retry. Resume at a fresh observation after recovery. Do not charge the outage.

**Acceptance.** Reapply identical and out-of-order checkpoints, reopen the database after a count, and inject a transaction failure between two rollover segments. Verify zero duplicate counts, no partial segment pair, correct eligible/exempt totals, and identical dedupe before and after restart. Verify a failed settings save leaves the last committed quota in use.
