package com.you.reelguard.engine;

import com.you.reelguard.detect.Surface;
import java.util.Arrays;

public final class Settings {
    public final boolean separate;
    public final boolean everySwipe;
    public final int dwellSeconds;
    public final int warnPercent;
    public final int resetMinute;
    private final int[] counts;
    private final int[] minutes;

    public Settings(boolean separate, boolean everySwipe, int dwellSeconds, int warnPercent,
                    int resetMinute, int[] counts, int[] minutes) {
        if (dwellSeconds < 1 || dwellSeconds > 60 || warnPercent < 1 || warnPercent > 99 ||
                resetMinute < 0 || resetMinute >= 1440) throw new IllegalArgumentException("Invalid settings");
        this.separate = separate;
        this.everySwipe = everySwipe;
        this.dwellSeconds = dwellSeconds;
        this.warnPercent = warnPercent;
        this.resetMinute = resetMinute;
        this.counts = validate(counts, 100000);
        this.minutes = validate(minutes, 1440);
    }

    private static int[] validate(int[] values, int max) {
        if (values.length != 4 || Arrays.stream(values).anyMatch(value -> value < 0 || value > max))
            throw new IllegalArgumentException("Invalid limit");
        return values.clone();
    }

    public static Settings defaults() {
        return new Settings(false, false, 2, 80, 240, new int[4], new int[4]);
    }

    public int scope(Surface surface) { return separate ? surface.ordinal() + 1 : 0; }
    public int count(int scope) { return counts[scope]; }
    public int minutes(int scope) { return minutes[scope]; }
}
