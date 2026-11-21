package com.you.reelguard.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

public final class DailyWindow {
    public final long start;
    public final long end;
    public final String zone;
    public final int resetMinute;

    public DailyWindow(long start, long end, String zone, int resetMinute) {
        this.start = start;
        this.end = end;
        this.zone = zone;
        this.resetMinute = resetMinute;
    }

    public static DailyWindow containing(long now, ZoneId zone, int reset) {
        LocalDate date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (boundary(date, zone, reset) > now) date = date.minusDays(1);
        return new DailyWindow(boundary(date, zone, reset), boundary(date.plusDays(1), zone, reset),
                zone.getId(), reset);
    }

    public static DailyWindow advance(DailyWindow current, long now, ZoneId zone, int reset) {
        if (current == null) return containing(now, zone, reset);
        if (now < current.end) return current;
        DailyWindow candidate = containing(now, zone, reset);
        return new DailyWindow(Math.max(candidate.start, current.end), candidate.end, zone.getId(), reset);
    }

    private static long boundary(LocalDate date, ZoneId zone, int reset) {
        LocalDateTime local = date.atStartOfDay().plusMinutes(reset);
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) return zone.getRules().getTransition(local).getInstant().toEpochMilli();
        return local.toInstant(offsets.get(0)).toEpochMilli();
    }
}
