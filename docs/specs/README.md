# ReelGuard implementation specifications

Use these sheets to implement the personal Android app described in [PLAN.md](../../PLAN.md). They define one Gradle app module with Java packages for the components below. Names in method tables are proposed Java contracts. Screen examples and thresholds are acceptance inputs. Device selector values must come from the discovery spike.

| Build step | Component and deliverable | Depends on |
|---|---|---|
| 1 | [Build and Android integration](01-build.md) defines the toolchain, APK, manifest, resources and service registration. | Android SDK and a phone |
| 2 | [Snapshot and discovery](02-snapshot.md) defines device capture, the immutable tree and fixture format. | Build setup for live capture. ADB discovery can start independently. |
| 3 | [Selector catalog](03-selectors.md) defines the asset schema, version matching, evidence and fallback rules. | Captured trees |
| 4 | [Instagram detector](04-instagram.md) separates the Reels tab, home-feed video, and ordinary Instagram. | Snapshot, catalog, identity |
| 4 | [YouTube detector](05-youtube.md) separates the active Short, previews, and long videos. | Snapshot, catalog, identity |
| 4 | [Content identity and whitelist](06-identity.md) defines signatures, creator matching and quick add. | Catalog extraction roles, preferences |
| 5 | [Daily window](07-day.md) assigns usage to reset periods and handles clock changes. | Java time |
| 5 | [Counter engine](08-counter.md) owns visits, verified dwell, count qualification and deduplication. | Screen state, identity, daily window |
| 5 | [Policy engine](09-policy.md) resolves quota scope, thresholds and enforcement eligibility. | Daily totals, settings, creator eligibility |
| 6 | [Preferences and event database](10-data.md) defines settings, durable usage, queries and restart recovery. | Identity, window, counter and policy contracts |
| 6 | [Accessibility service](11-service.md) wires capture, scheduling, storage, policy and lifecycle together. | Modules above |
| 6 | [Overlay enforcer](12-overlay.md) owns banners, blocking, Back, Home and cleanup. | Service and policy |
| 7 | [Activities and settings](13-ui.md) defines the dashboard, editor, whitelist and status workflows. | Preferences, database, service status |
| 8 | [Diagnostics and selector health](14-diagnostics.md) defines local alerts, debug capture and release boundaries. | Snapshot, catalog, service and UI |
| All | [Verification and device acceptance](15-verification.md) maps fixtures, timing tests and device scenarios to pass conditions. | Use the relevant rows at each step |
| Feature | [Universal limits switch](16-universal-limits-switch.md) defines the dashboard switch, Quick Settings tile, and continued accounting while limits are off. | Preferences, service, policy and UI |

## Follow one reel through the system

1. Instagram emits a scroll event. The service coalesces it, verifies the active package and window, then copies the visible hierarchy. The detector identifies the active row, extracts its author and caption, and returns one screen observation.
2. The service resolves the creator against the current whitelist and evaluates today's committed usage. If that scope is already exhausted, it can block as soon as the observation establishes eligibility. Otherwise the counter opens a visit and schedules the next confirmation.
3. After two verified seconds, the counter proposes a counted visit. One database transaction checkpoints dwell and the count. Policy evaluates the resulting totals before the service dispatches a banner or block. The dashboard reads the same rows.
4. A swipe changes the signature. A revisit inside the dedupe history adds eligible viewing time but no second count. A whitelisted visit records exempt time separately. A photo feed, DM or long video closes the visit and releases enforcement.

## Decisions left open by the plan

| Topic | Baseline for implementation |
|---|---|
| First launch | Track usage with quotas disabled until the user saves a positive limit. Default to combined scope, two-second dwell, 80% warning and 04:00 device-local reset. |
| Meaning of minutes | Charge verified eligible time even for a short flick or revisit. Count qualification and time qualification are independent. |
| Missing author | If the platform has whitelist entries, defer enforcement and charging while the handle is unresolved. If its whitelist is empty, a verified target can accrue time and trigger enforcement even when the handle is unavailable. |
| Content identity | Keep the most recent 64 counted identities per platform per daily window, shared across that platform's surfaces. Older evicted items can count again. |
| Changes and overrides | Saved quota changes take effect immediately. Whitelist changes apply prospectively. The universal limits switch controls enforcement across all surfaces. The optional 30-second limit-increase delay has a separate acceptance case in the UI sheet. |
| Timings and heuristics | The 300 ms sampling interval, three-second inactivity cutoff, feed geometry and health-alert thresholds are starting values. Record fixture or device evidence when tuning them. |

The device-dependent deliverables are the selector profiles, sanitized fixtures, and device measurements. Record the installed app version, locale and observed evidence with each result so another implementer can reproduce it.
