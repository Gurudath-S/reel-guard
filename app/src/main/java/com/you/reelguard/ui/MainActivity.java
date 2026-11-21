package com.you.reelguard.ui;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import com.you.reelguard.R;
import com.you.reelguard.data.Repository;
import com.you.reelguard.debug.DebugTools;
import com.you.reelguard.detect.Surface;
import com.you.reelguard.service.ReelGuardService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;

public final class MainActivity extends Activity {
    private final int[] countIds = {R.id.counts_0, R.id.counts_1, R.id.counts_2, R.id.counts_3};
    private final int[] minuteIds = {R.id.minutes_0, R.id.minutes_1, R.id.minutes_2, R.id.minutes_3};
    private final int[] groupIds = {R.id.cap_group_0, R.id.cap_group_1, R.id.cap_group_2, R.id.cap_group_3};
    private final Runnable refresh = this::load;
    private Repository repository;
    private Repository.Dashboard dashboard;
    private int resetMinute = 240;
    private boolean started;
    private boolean initialized;
    private boolean saving;
    private long request;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.main_activity);
        repository = Repository.get(this);
        if (saved != null) {
            resetMinute = saved.getInt("reset", 240);
            initialized = saved.getBoolean("initialized");
        }
        findViewById(R.id.enable).setOnClickListener(v -> openSettings());
        findViewById(R.id.whitelist).setOnClickListener(v -> startActivity(new Intent(this, WhitelistActivity.class)));
        findViewById(R.id.notifications).setOnClickListener(v -> enableNotifications());
        findViewById(R.id.reset).setOnClickListener(v -> pickReset());
        findViewById(R.id.save).setOnClickListener(v -> save());
        findViewById(R.id.cancel).setOnClickListener(v -> {
            if (dashboard != null) populate(dashboard.settings);
            text(R.id.form_result).setText("");
        });
        toggle(R.id.separate).setOnCheckedChangeListener((view, checked) -> renderScope());
        toggle(R.id.swipe).setOnCheckedChangeListener((view, checked) -> renderScope());
        DebugTools.bind(this, findViewById(R.id.debug));
        renderScope();
        renderReset();
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        repository.subscribe(refresh);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    @Override protected void onStop() {
        started = false;
        repository.unsubscribe(refresh);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("reset", resetMinute);
        out.putBoolean("initialized", initialized);
    }

    private void load() {
        renderStatus();
        findViewById(R.id.notifications).setVisibility(Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ? View.VISIBLE : View.GONE);
        long token = ++request;
        repository.read(data -> {
            if (!started || token != request) return;
            dashboard = data;
            if (!initialized) populate(data.settings);
            StringBuilder usage = new StringBuilder("Today  ·  resets at ")
                    .append(DateFormat.getTimeFormat(this).format(new Date(data.window.end)))
                    .append("\nAll surfaces   ").append(usage(data, 0, !data.settings.separate));
            for (Surface surface : Surface.values()) {
                if (surface == Surface.OTHER) continue;
                usage.append("\n").append(surface.label).append("   ")
                        .append(usage(data, surface.ordinal() + 1, data.settings.separate));
            }
            usage.append("\nWhitelisted   ").append(data.totals.exemptVisits(0)).append(" visits  ·  ")
                    .append(duration(data.totals.exemptMillis(0)));
            text(R.id.usage).setText(usage);
            text(R.id.diagnostic).setText(data.diagnostic);
            text(R.id.diagnostic).setVisibility(data.diagnostic.trim().isEmpty() ? View.GONE : View.VISIBLE);
        }, error -> {
            if (!started || token != request) return;
            text(R.id.diagnostic).setVisibility(View.VISIBLE);
            text(R.id.diagnostic).setText(error);
        });
    }

    private String usage(Repository.Dashboard data, int scope, boolean showCap) {
        int countCap = showCap ? data.settings.count(scope) : 0;
        int timeCap = showCap ? data.settings.minutes(scope) : 0;
        return data.totals.count(scope) + (countCap > 0 ? " / " + countCap : "") + " reels  ·  " +
                duration(data.totals.millis(scope)) + (timeCap > 0 ? " / " + timeCap + "m" : "") +
                (showCap && countCap == 0 && timeCap == 0 ? "  ·  No limit" : "");
    }

    private String duration(long millis) {
        return (millis / 60000) + "m " + ((millis / 1000) % 60) + "s";
    }

    private void renderStatus() {
        ComponentName service = new ComponentName(this, ReelGuardService.class);
        boolean enabled = false;
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (service.equals(ComponentName.unflattenFromString(info.getId()))) enabled = true;
        }
        text(R.id.status).setText(ReelGuardService.isConnected() ? "Service connected" :
                enabled ? "Service enabled · waiting for connection" : "Service disabled");
    }

    private void openSettings() {
        new android.app.AlertDialog.Builder(this)
                .setMessage("ReelGuard reads visible Instagram and YouTube content to recognize short videos. At your limit it can go Back and offer a button to return Home.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Open settings", (dialog, item) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (android.content.ActivityNotFoundException unavailable) {
                        text(R.id.status).setText("Open Settings → Accessibility → Installed apps → ReelGuard.");
                    }
                }).show();
    }

    private void enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 12) load();
    }

    private void populate(com.you.reelguard.engine.Settings settings) {
        for (int i = 0; i < 4; i++) {
            field(countIds[i]).setText(settings.count(i) == 0 ? "" : String.valueOf(settings.count(i)));
            field(minuteIds[i]).setText(settings.minutes(i) == 0 ? "" : String.valueOf(settings.minutes(i)));
        }
        toggle(R.id.separate).setChecked(settings.separate);
        toggle(R.id.swipe).setChecked(settings.everySwipe);
        field(R.id.dwell).setText(String.valueOf(settings.dwellSeconds));
        field(R.id.warning_percent).setText(String.valueOf(settings.warnPercent));
        resetMinute = settings.resetMinute;
        renderReset();
        renderScope();
        initialized = true;
    }

    private void renderScope() {
        boolean separate = toggle(R.id.separate).isChecked();
        for (int i = 0; i < 4; i++) findViewById(groupIds[i]).setVisibility((i > 0) == separate ? View.VISIBLE : View.GONE);
        field(R.id.dwell).setEnabled(!toggle(R.id.swipe).isChecked());
    }

    private void renderReset() {
        Button reset = findViewById(R.id.reset);
        reset.setText("Daily reset  ·  " + LocalTime.of(resetMinute / 60, resetMinute % 60)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
    }

    private void pickReset() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            resetMinute = hour * 60 + minute;
            renderReset();
        }, resetMinute / 60, resetMinute % 60, DateFormat.is24HourFormat(this)).show();
    }

    private int number(int id, int max, boolean optional) {
        EditText input = field(id);
        String value = input.getText().toString().trim();
        if (optional && value.isEmpty()) {
            input.setError(null);
            return 0;
        }
        try {
            int number = Integer.parseInt(value);
            if (number >= 1 && number <= max) {
                input.setError(null);
                return number;
            }
        } catch (NumberFormatException invalid) {
            // Use the same field error for malformed and out-of-range values.
        }
        input.setError("Enter a whole number from 1 to " + max);
        throw new IllegalArgumentException();
    }

    private void save() {
        if (saving || dashboard == null) return;
        try {
            boolean separate = toggle(R.id.separate).isChecked();
            int[] counts = new int[4];
            int[] minutes = new int[4];
            for (int i = 0; i < 4; i++) {
                if ((i > 0) == separate) {
                    counts[i] = number(countIds[i], 100000, true);
                    minutes[i] = number(minuteIds[i], 1440, true);
                } else {
                    counts[i] = dashboard.settings.count(i);
                    minutes[i] = dashboard.settings.minutes(i);
                }
            }
            boolean swipe = toggle(R.id.swipe).isChecked();
            int dwell = swipe ? dashboard.settings.dwellSeconds : number(R.id.dwell, 60, false);
            com.you.reelguard.engine.Settings next = new com.you.reelguard.engine.Settings(separate, swipe, dwell,
                    number(R.id.warning_percent, 99, false), resetMinute, counts, minutes);
            saving = true;
            repository.save(next, success -> {
                saving = false;
                if (isDestroyed()) return;
                if (success) populate(next);
                text(R.id.form_result).setText(success ? "Saved. A reset time change applies after the current window." : "Could not save settings.");
            });
        } catch (IllegalArgumentException invalid) {
            text(R.id.form_result).setText("Check the fields marked above.");
        }
    }

    private TextView text(int id) { return findViewById(id); }
    private Switch toggle(int id) { return findViewById(id); }
    private EditText field(int id) { return findViewById(id); }
}
