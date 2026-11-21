package com.you.reelguard.engine;

import com.you.reelguard.detect.ContentIdentity;
import com.you.reelguard.detect.ContentIdentity.Eligibility;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.ScreenState;
import com.you.reelguard.detect.Surface;
import com.you.reelguard.engine.CounterEngine.Checkpoint;
import com.you.reelguard.engine.PolicyEngine.Action;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UsageTest {
    private final CounterEngine engine = new CounterEngine();
    private final Settings defaults = Settings.defaults();
    private final DailyWindow day = new DailyWindow(0, 100000, "UTC", 0);

    @Test public void thresholdAndRevisitSeparateCountFromTime() {
        assertFalse(only(sample("A", Eligibility.ELIGIBLE, 0)).counted);
        assertFalse(only(sample("A", Eligibility.ELIGIBLE, 1999)).counted);
        assertTrue(only(sample("A", Eligibility.ELIGIBLE, 2000)).counted);
        assertFalse(only(sample("B", Eligibility.ELIGIBLE, 2400)).counted);
        assertEquals(400, only(sample("B", Eligibility.ELIGIBLE, 2800)).dwell);
        Checkpoint replay = only(sample("A", Eligibility.ELIGIBLE, 3000));
        assertFalse(replay.counted);
        Checkpoint later = only(sample("A", Eligibility.ELIGIBLE, 5000));
        assertFalse(later.counted);
        assertEquals(2000, later.dwell);
        assertEquals(replay.id, later.id);
    }

    @Test public void ignoredGapAndMissingIdentity() {
        sample("A", Eligibility.ELIGIBLE, 0);
        assertTrue(sample("A", Eligibility.PENDING, 2000).isEmpty());
        assertEquals(0, only(sample("A", Eligibility.EXEMPT, 3000)).dwell);
        Checkpoint exempt = only(sample("A", Eligibility.EXEMPT, 5000));
        assertTrue(exempt.exempt);
        assertFalse(exempt.counted);
        assertEquals(2000, exempt.dwell);
        sample(null, Eligibility.ELIGIBLE, 5500);
        Checkpoint timeOnly = only(sample(null, Eligibility.ELIGIBLE, 7500));
        assertFalse(timeOnly.counted);
        assertEquals(2000, timeOnly.dwell);
        assertEquals(0, only(sample(null, Eligibility.ELIGIBLE, 11000)).dwell);
    }

    @Test public void everySwipeRestoreAndCrossSurfaceDedupe() {
        Settings swipe = new Settings(false, true, 2, 80, 240, new int[4], new int[4]);
        engine.restore(day.start, Collections.singletonList("A"), Collections.emptyList());
        Checkpoint seen = only(engine.observe(item("A", Surface.IG_FEED_VIDEO), Eligibility.ELIGIBLE,
                4, day, 10, 10, swipe));
        assertFalse(seen.counted);
        assertTrue(only(engine.observe(item("B", Surface.IG_FEED_VIDEO), Eligibility.ELIGIBLE,
                4, day, 11, 11, swipe)).counted);
    }

    @Test public void rolloverCommitsBothSegments() {
        DailyWindow next = new DailyWindow(day.end, day.end + 100000, "UTC", 0);
        sample("A", Eligibility.ELIGIBLE, 98000);
        sample("A", Eligibility.ELIGIBLE, 99000);
        List<Checkpoint> split = engine.observe(item("A", Surface.IG_REELS_TAB), Eligibility.ELIGIBLE,
                4, next, 102000, 102000, defaults);
        assertEquals(2, split.size());
        assertEquals(2000, split.get(0).dwell);
        assertEquals(2000, split.get(1).dwell);
        assertEquals(0, split.get(0).window);
        assertEquals(day.end, split.get(1).window);
        assertTrue(split.get(0).counted);
        assertTrue(split.get(1).counted);
    }

    @Test public void dailyBoundaryAndDst() {
        ZoneId zone = ZoneId.of("America/New_York");
        long instant = Instant.parse("2024-03-10T07:00:00Z").toEpochMilli();
        DailyWindow gap = DailyWindow.containing(instant, zone, 150);
        assertEquals(instant, gap.start);
        long overlap = Instant.parse("2024-11-03T05:30:00Z").toEpochMilli();
        DailyWindow fall = DailyWindow.containing(overlap, zone, 90);
        assertEquals(overlap, fall.start);
        assertSame(fall, DailyWindow.advance(fall, overlap - 120000, ZoneId.of("UTC"), 240));
        DailyWindow edited = DailyWindow.advance(fall, fall.end, ZoneId.of("UTC"), 240);
        assertEquals(fall.end, edited.start);
    }

    @Test public void warningAndBlockingFollowActiveScope() {
        PolicyEngine policy = new PolicyEngine();
        ScreenState reel = item("A", Surface.IG_REELS_TAB);
        Settings settings = new Settings(false, false, 2, 80, 240, new int[]{10, 1, 0, 0},
                new int[]{1, 0, 0, 0});
        Totals totals = new Totals();
        totals.add(2, false, 7, 47999);
        assertEquals(Action.ALLOW, policy.evaluate(reel, Eligibility.ELIGIBLE, totals, settings, day).action);
        totals.add(2, false, 1, 1);
        assertEquals(Action.WARN, policy.evaluate(reel, Eligibility.ELIGIBLE, totals, settings, day).action);
        totals.add(2, false, 0, 12000);
        assertEquals(Action.BLOCK, policy.evaluate(reel, Eligibility.ELIGIBLE, totals, settings, day).action);
        assertEquals(Action.ALLOW, policy.evaluate(reel, Eligibility.EXEMPT, totals, settings, day).action);
        Settings separate = new Settings(true, false, 2, 80, 240, new int[]{10, 1, 0, 0}, new int[4]);
        assertEquals(Action.ALLOW, policy.evaluate(reel, Eligibility.ELIGIBLE, totals, separate, day).action);
        assertEquals(Action.ALLOW, policy.evaluate(ScreenState.other(Platform.YT, ScreenState.Evidence.KNOWN_OTHER),
                Eligibility.ELIGIBLE, totals, settings, day).action);
    }

    @Test public void signatureNormalizesCaptionWhitespace() {
        assertEquals(ContentIdentity.signature(Platform.YT, null, "yt:@author", "One two"),
                ContentIdentity.signature(Platform.YT, null, "yt:@author", " One\u00a0\n two "));
    }
    @Test public void whitelistIsPlatformScoped() {
        assertEquals("ig:khanacademy", ContentIdentity.creatorKey(Platform.IG, " @KhanAcademy "));
        assertEquals("yt:@école", ContentIdentity.creatorKey(Platform.YT, "@E\u0301cole"));
        assertNull(ContentIdentity.creatorKey(Platform.IG, "https://instagram.com/khanacademy"));
        java.util.Set<String> members = Collections.singleton("yt:@khanacademy");
        assertEquals(Eligibility.PENDING, ContentIdentity.eligibility(Platform.YT, null, members));
        assertEquals(Eligibility.ELIGIBLE, ContentIdentity.eligibility(Platform.IG, null, members));
        assertEquals(Eligibility.EXEMPT, ContentIdentity.eligibility(Platform.YT, "yt:@khanacademy", members));
    }

    private List<Checkpoint> sample(String id, Eligibility eligibility, long time) {
        return engine.observe(item(id, Surface.IG_REELS_TAB), eligibility, 4, day, time, time, defaults);
    }

    private ScreenState item(String id, Surface surface) {
        return new ScreenState(Platform.IG, surface, "ig:author", id, ScreenState.Evidence.TARGET);
    }

    private Checkpoint only(List<Checkpoint> changed) {
        assertEquals(1, changed.size());
        return changed.get(0);
    }
}
