package com.you.reelguard.engine;

import com.you.reelguard.detect.ContentIdentity.Eligibility;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.ScreenState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CounterEngine {
    public static final class Checkpoint {
        public final String id;
        public final ScreenState state;
        public final long window, start, last, dwell, revision;
        public final boolean exempt, counted;

        private Checkpoint(Visit visit) {
            id = visit.id;
            state = visit.state;
            window = visit.window;
            start = visit.firstWall;
            last = visit.wall;
            dwell = visit.dwell;
            revision = ++visit.revision;
            exempt = visit.eligibility == Eligibility.EXEMPT;
            counted = visit.counted;
        }
    }

    private static final class Visit {
        final String id = UUID.randomUUID().toString();
        final ScreenState state;
        final Eligibility eligibility;
        final int windowId;
        final long window, firstWall;
        final boolean swipe;
        final int dwellSeconds;
        long wall, elapsed, dwell, revision;
        boolean counted;

        Visit(ScreenState state, Eligibility eligibility, int windowId, long window,
              long wall, long elapsed, Settings settings) {
            this.state = state;
            this.eligibility = eligibility;
            this.windowId = windowId;
            this.window = window;
            this.firstWall = wall;
            this.wall = wall;
            this.elapsed = elapsed;
            this.swipe = settings.everySwipe;
            this.dwellSeconds = settings.dwellSeconds;
        }

        boolean same(ScreenState next, Eligibility nextEligibility, int owner, Settings settings) {
            return state.platform == next.platform && state.surface == next.surface &&
                    Objects.equals(state.creator, next.creator) && Objects.equals(state.signature, next.signature) &&
                    eligibility == nextEligibility && windowId == owner && swipe == settings.everySwipe &&
                    dwellSeconds == settings.dwellSeconds;
        }
    }

    private final EnumMap<Platform, LinkedHashSet<String>> dedupe = new EnumMap<>(Platform.class);
    private long day = Long.MIN_VALUE;
    private Visit active;

    public CounterEngine() {
        for (Platform platform : Platform.values()) dedupe.put(platform, new LinkedHashSet<>());
    }

    public void restore(long start, List<String> instagram, List<String> youtube) {
        close();
        resetHistory(start);
        dedupe.get(Platform.IG).addAll(instagram);
        dedupe.get(Platform.YT).addAll(youtube);
    }

    public void close() { active = null; }

    public long untilCount() {
        if (active == null || active.counted || active.state.signature == null ||
                active.eligibility != Eligibility.ELIGIBLE || active.swipe ||
                dedupe.get(active.state.platform).contains(active.state.signature)) return Long.MAX_VALUE;
        return Math.max(1, active.dwellSeconds * 1000L - active.dwell);
    }

    public List<Checkpoint> observe(ScreenState state, Eligibility eligibility, int owner, DailyWindow window,
                                    long wall, long elapsed, Settings settings) {
        List<Checkpoint> result = new ArrayList<>(2);
        boolean same = state.isTarget() && eligibility != Eligibility.PENDING && active != null &&
                active.same(state, eligibility, owner, settings);
        long elapsedGap = active == null ? 0 : elapsed - active.elapsed;
        long wallGap = active == null ? 0 : wall - active.wall;
        same &= elapsedGap >= 0 && elapsedGap <= 3000 && wallGap >= 0 &&
                Math.abs(wallGap - elapsedGap) <= 1000;
        long carry = 0;
        if (day != window.start) {
            if (same && active.wall < window.start && wall >= window.start && wallGap > 0) {
                long elapsedBefore = Math.round((double) elapsedGap * (window.start - active.wall) / wallGap);
                carry = elapsedGap - elapsedBefore;
                active.dwell += elapsedBefore;
                active.wall = window.start;
                qualify(active);
                result.add(new Checkpoint(active));
            }
            resetHistory(window.start);
            close();
            same = false;
        }
        if (!state.isTarget() || eligibility == Eligibility.PENDING) {
            close();
            return result;
        }
        if (!same) {
            active = new Visit(state, eligibility, owner, window.start,
                    carry == 0 ? wall : window.start, elapsed, settings);
            active.dwell = carry;
        } else if (elapsedGap == 0) {
            return Collections.unmodifiableList(result);
        } else {
            active.dwell += elapsedGap;
        }
        active.wall = wall;
        active.elapsed = elapsed;
        qualify(active);
        result.add(new Checkpoint(active));
        return Collections.unmodifiableList(result);
    }

    private void resetHistory(long start) {
        day = start;
        for (LinkedHashSet<String> signatures : dedupe.values()) signatures.clear();
    }

    private void qualify(Visit visit) {
        if (visit.counted || visit.eligibility != Eligibility.ELIGIBLE || visit.state.signature == null ||
                (!visit.swipe && visit.dwell < visit.dwellSeconds * 1000L)) return;
        LinkedHashSet<String> signatures = dedupe.get(visit.state.platform);
        if (!signatures.add(visit.state.signature)) return;
        visit.counted = true;
        if (signatures.size() > 64) signatures.remove(signatures.iterator().next());
    }
}
