package com.you.reelguard.engine;

public final class Totals {
    private final long[][] values = new long[4][4];

    public void add(int surface, boolean exempt, long count, long millis) {
        int column = exempt ? 2 : 0;
        values[0][column] += count;
        values[0][column + 1] += millis;
        values[surface][column] += count;
        values[surface][column + 1] += millis;
    }

    public long count(int scope) { return values[scope][0]; }
    public long millis(int scope) { return values[scope][1]; }
    public long exemptVisits(int scope) { return values[scope][2]; }
    public long exemptMillis(int scope) { return values[scope][3]; }
}
