# PolicyEngine and threshold decisions

**Interface.** A pure Java PolicyEngine takes ScreenState, eligibility, today's committed per-surface totals, Settings and DailyWindow. Return Decision containing action (ALLOW, WARN, BLOCK), scope key, triggering measure(s), observed values, configured caps and reset instant. Use long integer counts and milliseconds throughout.

| Method | Rules |
|---|---|
| resolveScope(surface, settings) | Combined mode sums eligible usage across all three surfaces. Separate mode selects only the active surface. Maintain inactive-mode settings for later editing. Disabled cap contributes no threshold. |
| evaluate(screen, eligibility, totals, settings, window) | Immediately ALLOW ordinary/unknown/unavailable screens and EXEMPT or PENDING items. For an eligible target evaluate each enabled count/time cap. At or above any cap return BLOCK. Otherwise at or above any warning threshold return WARN. Else ALLOW. Evaluate entry with existing totals, then again after an accounting step. |
| warningAt(cap, percent), remaining(scope) | For counts use ceiling of cap × percent / 100. For minutes convert the cap to milliseconds first and compare integer milliseconds. Record all triggering measures, with count first for display ties. A cap of one can reach BLOCK without any prior WARN. |
| nextDeadline(totals, activeVisit, settings) | For a running eligible target return elapsed time remaining until the next enabled time warning or time block. Subtract verified dwell pending checkpoint exactly once. Return no deadline for inactive measures. Combine with counter scheduling in the service. |

**Banner delivery.** WARN describes a level. Display one banner per window, scope and configured threshold configuration, when the user is on an eligible target. Persist the last-delivered warning keys in preferences. BLOCK always preempts WARN. Separate scopes can warn independently. A changed limit or warning percent creates a fresh configuration key. Discard old-window keys at rollover.

| Situation | Expected decision |
|---|---|
| Combined count limit 10, warning 80%, counts 7, 8, 10 | ALLOW, WARN once for the configuration, BLOCK. After Back, ordinary feed is ALLOW at 10. Next eligible Short is BLOCK on entry, without waiting for dwell. |
| Combined count 10 and time 5 min, count 2 and time 300,000 ms | BLOCK on time. Verified eligible dwell from revisits contributes. |
| Separate IG feed limit 3 reached, Shorts cap disabled | IG feed videos block. Shorts remain usable. A combined total cannot exhaust the independent Reels tab. |
| All caps disabled | Track eligible usage and display stats. Always ALLOW. |
| At limit, whitelisted author or unresolved with platform whitelist | ALLOW. Reevaluate immediately when the author or membership resolves. |
| Lower limit below today's usage / raise exhausted limit | Use current totals with saved settings. On a verified target, apply the new decision and dismiss a stale overlay if eligible usage is now below the limit. |

**Acceptance.** Verify each boundary one unit below, exactly at and above the threshold, including a three-reel cap with an 80% warning. Test simultaneous thresholds, separate/combined switching mid-day, warning replay after service recreation, and the first observed item in a fresh window. A BLOCK is valid for its verified target only.
