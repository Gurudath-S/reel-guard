# CounterEngine and visit accounting

**Responsibility.** A single serialized plain-Java engine accepts ordered, verified observations. It emits absolute visit checkpoints, never calls Android or SQLite, and exposes the next time confirmation is needed. A visit is one continuous observation of an item on one surface with one eligibility and daily window.

| Method | Input and result |
|---|---|
| restore(window, recentCounted) | Start idle with the per-platform ordered history of up to 64 counted signatures in this window. Active timers and candidates are always new after reconnection. |
| observe(state, eligibility, sample, settings) → AccountingStep | Confirm the previous interval only if both ends support the same visit and inactivity has not expired. Return zero or more visit checkpoints, the proposed next engine state and next due elapsed time. A confirmed transition closes the old visit at its last verified sample and starts the new visit at the current sample. |
| confirm(frame, sample) | Reuse observe after re-reading the current screen on a scheduled wake-up. A timer firing alone earns no time. Use the dwell deadline, remaining minute budget, next rollover and periodic active checkpoint (1 s) to request the earliest useful confirmation. |
| close(reason), rollover(boundary) | Close at last verified time, cancel pending qualification. At a verified boundary use the split rule in [DailyWindow](07-day.md). A stale timer with an older visit/generation has no effect. |
| accept(step) | Advance committed state and dedupe history after the repository acknowledges the step. The same immutable pending checkpoint may be retried. |

## Transition and measurement rules

| Observation | Count and time result |
|---|---|
| OTHER, unavailable root, unsupported window, screen locked/off, PENDING author | Close the active visit at its last valid sample. No accrual through the gap. |
| New eligible target with signature | Allocate a unique visit ID and start with zero dwell. In dwell mode require a continuous confirmed interval of at least the configured threshold (default 2,000 ms). In every-swipe mode qualify the first observed distinct item immediately. A scroll callback itself never increments. |
| Same target and signature | Increase dwell by the verified elapsed difference once. On first qualification set counted to 1 if the signature is outside the platform's window history. Keep counted at 1 thereafter. |
| Recently counted signature revisited | Open a distinct visit to capture new dwell. Leave its counted flag at 0. Do not change the recency order of counted entries on an uncounted revisit. |
| Known target, eligible, signature absent | Permit time-only accrual between consecutive confirmed samples of the same surface/window and eligibility. Treat this as a time-only segment. Start a new identifiable visit when the signature arrives. Never synthesize a count from timestamps, author alone or a scroll. |
| EXEMPT | Log a separate visit with exempt dwell and counted 0. The quota receives zero counts and zero milliseconds. For same-item handling use the signature when available. |
| Scope, creator, window or configuration transition | Close on material changes to surface, app window, resolved creator/eligibility, signature, counting mode or dwell threshold. Preserve dedupe across surface changes within the platform. A limit-only edit causes a policy reevaluation without restarting qualification. |

The service stops accounting when blocking starts. For an already exhausted scope, evaluate entry before opening a new eligible visit. For a visit that crosses a limit, checkpoint its qualifying count and observed time before issuing the block. One count can qualify per visit and one time interval belongs to at most one surface. For performance, coalesce intermediate dwell writes to one per second, flushing early on count, transition, quota crossing, rollover or clean shutdown.

**Worked trace.** With two-second dwell and no limit, verify A at t=0, 1 and 2 s (count 1, dwell 2 s). Verify B at 2.4 and 2.8 s (0, 0.4 s). Verify A again at 3 and 5 s (0, 2 s). With target activity maintained throughout, the resulting eligible total is one reel and 4.4 seconds. Attribution of the two transition gaps is deliberately conservative. Every-swipe mode on the same trace yields two counts. For one target watched 30 s with repeated confirmations and continuing events, expect one count and 30 s. The inactivity cutoff also applies to a silent player.

**Acceptance.** Exercise exact threshold, 1 ms short, ten identities, one static item, A/B/A, eviction at entry 65, replayed checkpoint, delayed author, signature collision, time-only accounting, interrupted session and restart. Assert each persisted count, eligible/exempt milliseconds and dedupe state, in addition to the final total.
