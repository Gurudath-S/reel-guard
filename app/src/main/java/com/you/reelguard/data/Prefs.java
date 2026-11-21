package com.you.reelguard.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.you.reelguard.engine.DailyWindow;
import com.you.reelguard.engine.Settings;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    private static final String ENFORCEMENT_ENABLED = "enforcement_enabled.v1";
    private final SharedPreferences storage;
    private final Gson gson = new Gson();

    Prefs(Context context) { storage = context.getSharedPreferences("reelguard", Context.MODE_PRIVATE); }

    Settings settings() {
        String saved = storage.getString("settings.v1", null);
        if (saved == null) return Settings.defaults();
        JsonObject object = gson.fromJson(saved, JsonObject.class);
        Settings decoded = gson.fromJson(object, Settings.class);
        int[] counts = new int[4];
        int[] minutes = new int[4];
        for (int i = 0; i < 4; i++) {
            counts[i] = decoded.count(i);
            minutes[i] = decoded.minutes(i);
        }
        return new Settings(decoded.separate, decoded.everySwipe, decoded.dwellSeconds,
                decoded.warnPercent, decoded.resetMinute, counts, minutes);
    }

    void save(Settings settings) { put("settings.v1", gson.toJson(settings)); }

    boolean enforcementEnabled() { return storage.getBoolean(ENFORCEMENT_ENABLED, true); }

    void saveEnforcementEnabled(boolean enabled, boolean previous) {
        if (storage.edit().putBoolean(ENFORCEMENT_ENABLED, enabled).commit()) return;
        // A failed commit can still change the in-memory preference.
        storage.edit().putBoolean(ENFORCEMENT_ENABLED, previous).commit();
        throw new IllegalStateException("Limits preference write failed");
    }

    DailyWindow window(long now, int reset) {
        String saved = storage.getString("window.v1", null);
        DailyWindow decoded = gson.fromJson(saved, DailyWindow.class);
        if (decoded != null && decoded.start >= decoded.end) throw new IllegalStateException("Invalid daily window");
        DailyWindow window = DailyWindow.advance(decoded, now, ZoneId.systemDefault(), reset);
        if (window != decoded) put("window.v1", gson.toJson(window));
        return window;
    }

    String text(String key) { return storage.getString(key, null); }
    void put(String key, String value) {
        if (!storage.edit().putString(key, value).commit()) throw new IllegalStateException("Preferences write failed");
    }

    Set<String> set(String key) {
        return new HashSet<>(storage.getStringSet(key, Collections.emptySet()));
    }

    void saveSet(String key, Set<String> values) {
        if (!storage.edit().putStringSet(key, new HashSet<>(values)).commit())
            throw new IllegalStateException("Preferences write failed");
    }
}
