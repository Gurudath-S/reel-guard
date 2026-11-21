package com.you.reelguard.engine;

import com.you.reelguard.detect.ContentIdentity.Eligibility;
import com.you.reelguard.detect.ScreenState;

public final class PolicyEngine {
    public enum Action { ALLOW, WARN, BLOCK }

    public static final class Decision {
        public final Action action;
        public final String key;
        public final long used;
        public final long cap;
        public final boolean time;
        public final long reset;

        private Decision(Action action, String key, long used, long cap, boolean time, long reset) {
            this.action = action;
            this.key = key;
            this.used = used;
            this.cap = cap;
            this.time = time;
            this.reset = reset;
        }
    }

    public Decision evaluate(ScreenState state, Eligibility eligibility, Totals totals,
                             Settings settings, DailyWindow window) {
        if (!state.isTarget() || eligibility != Eligibility.ELIGIBLE)
            return new Decision(Action.ALLOW, "", 0, 0, false, window.end);
        int scope = settings.scope(state.surface);
        long countCap = settings.count(scope);
        long timeCap = settings.minutes(scope) * 60000L;
        String key = window.start + ":" + scope + ":" + countCap + ":" + timeCap + ":" + settings.warnPercent;
        long count = totals.count(scope);
        long time = totals.millis(scope);
        if (countCap > 0 && count >= countCap) return new Decision(Action.BLOCK, key, count, countCap, false, window.end);
        if (timeCap > 0 && time >= timeCap) return new Decision(Action.BLOCK, key, time, timeCap, true, window.end);
        if (countCap > 0 && count >= warningAt(countCap, settings.warnPercent))
            return new Decision(Action.WARN, key, count, countCap, false, window.end);
        if (timeCap > 0 && time >= warningAt(timeCap, settings.warnPercent))
            return new Decision(Action.WARN, key, time, timeCap, true, window.end);
        return new Decision(Action.ALLOW, key, 0, 0, false, window.end);
    }

    public static long warningAt(long cap, int percent) {
        return (cap * percent + 99) / 100;
    }
}
