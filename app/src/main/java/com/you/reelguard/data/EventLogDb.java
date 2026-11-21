package com.you.reelguard.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.Surface;
import com.you.reelguard.engine.CounterEngine.Checkpoint;
import com.you.reelguard.engine.Totals;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class EventLogDb extends SQLiteOpenHelper {
    EventLogDb(Context context) {
        super(context, "usage.db", null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY, visit_id TEXT NOT NULL UNIQUE, " +
                "window_start_ms INTEGER NOT NULL, ts INTEGER NOT NULL, last_ts INTEGER NOT NULL, " +
                "app TEXT NOT NULL, surface TEXT NOT NULL, creator TEXT, signature TEXT, " +
                "exempt INTEGER NOT NULL CHECK(exempt IN (0,1)), dwell_ms INTEGER NOT NULL CHECK(dwell_ms >= 0), " +
                "counted INTEGER NOT NULL CHECK(counted IN (0,1)), revision INTEGER NOT NULL, " +
                "CHECK(counted = 0 OR (exempt = 0 AND signature IS NOT NULL)), " +
                "CHECK((app = 'ig' AND surface IN ('reels_tab','feed_video')) OR (app = 'yt' AND surface = 'shorts')))");
        db.execSQL("CREATE INDEX events_window ON events(window_start_ms, surface)");
        db.execSQL("CREATE INDEX events_recent ON events(window_start_ms, app, counted, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported usage database migration");
    }

    Totals apply(List<Checkpoint> checkpoints, long window) {
        if (checkpoints.isEmpty()) return totals(window);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Checkpoint checkpoint : checkpoints) apply(db, checkpoint);
            Totals result = totals(window);
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    private void apply(SQLiteDatabase db, Checkpoint value) {
        ContentValues row = new ContentValues();
        row.put("visit_id", value.id);
        row.put("window_start_ms", value.window);
        row.put("ts", value.start);
        row.put("last_ts", value.last);
        row.put("app", value.state.platform.key);
        row.put("surface", value.state.surface.key);
        row.put("creator", value.state.creator);
        row.put("signature", value.state.signature);
        row.put("exempt", value.exempt ? 1 : 0);
        row.put("dwell_ms", value.dwell);
        row.put("counted", value.counted ? 1 : 0);
        row.put("revision", value.revision);
        String[] id = {value.id};
        if (db.update("events", row, "visit_id=? AND revision<?",
                new String[]{value.id, String.valueOf(value.revision)}) == 0 &&
                DatabaseUtils.queryNumEntries(db, "events", "visit_id=?", id) == 0) {
            db.insertOrThrow("events", null, row);
        }
    }

    Totals totals(long window) {
        Totals result = new Totals();
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT surface, exempt, SUM(counted), SUM(dwell_ms), COUNT(*) " +
                        "FROM events WHERE window_start_ms=? GROUP BY surface, exempt",
                new String[]{String.valueOf(window)})) {
            while (rows.moveToNext()) {
                boolean exempt = rows.getInt(1) != 0;
                for (Surface surface : Surface.values()) {
                    if (surface.key.equals(rows.getString(0)))
                        result.add(surface.ordinal() + 1, exempt, rows.getLong(exempt ? 4 : 2), rows.getLong(3));
                }
            }
        }
        return result;
    }

    List<String> recent(long window, Platform platform) {
        LinkedHashSet<String> signatures = new LinkedHashSet<>();
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT signature FROM events WHERE window_start_ms=? AND app=? AND counted=1 ORDER BY id DESC",
                new String[]{String.valueOf(window), platform.key})) {
            while (signatures.size() < 64 && rows.moveToNext()) signatures.add(rows.getString(0));
        }
        List<String> ordered = new ArrayList<>(signatures);
        Collections.reverse(ordered);
        return ordered;
    }
}
