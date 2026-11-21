package com.you.reelguard.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.you.reelguard.detect.ContentIdentity;
import com.you.reelguard.detect.ContentIdentity.Eligibility;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.ScreenState;
import com.you.reelguard.engine.CounterEngine;
import com.you.reelguard.engine.DailyWindow;
import com.you.reelguard.engine.PolicyEngine;
import com.you.reelguard.engine.Settings;
import com.you.reelguard.engine.Totals;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class Repository {
    public static final class Dashboard {
        public final Settings settings;
        public final DailyWindow window;
        public final Totals totals;
        public final Set<String> whitelist;
        public final List<String> recent;
        public final String diagnostic;

        Dashboard(Settings settings, DailyWindow window, Totals totals, Set<String> whitelist,
                  List<String> recent, String diagnostic) {
            this.settings = settings;
            this.window = window;
            this.totals = totals;
            this.whitelist = whitelist;
            this.recent = Collections.unmodifiableList(new ArrayList<>(recent));
            this.diagnostic = diagnostic;
        }
    }

    public static final class Evaluation {
        public final PolicyEngine.Decision decision;
        public final boolean showWarning;
        public final long nextConfirmation;

        Evaluation(PolicyEngine.Decision decision, boolean showWarning, long nextConfirmation) {
            this.decision = decision;
            this.showWarning = showWarning;
            this.nextConfirmation = nextConfirmation;
        }
    }

    private static Repository instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Prefs prefs;
    private final EventLogDb db;
    private final WhitelistStore whitelist;
    private final Set<Runnable> observers = new HashSet<>();
    private final Set<Runnable> configurationObservers = new HashSet<>();
    private final EnumMap<Platform, String> health = new EnumMap<>(Platform.class);
    private final PolicyEngine policy = new PolicyEngine();
    private final CounterEngine counter = new CounterEngine();
    private long restoredWindow = Long.MIN_VALUE;

    private Repository(Context context) {
        prefs = new Prefs(context);
        db = new EventLogDb(context);
        whitelist = new WhitelistStore(prefs);
    }

    public static synchronized Repository get(Context context) {
        if (instance == null) instance = new Repository(context.getApplicationContext());
        return instance;
    }

    public void subscribe(Runnable observer) { observers.add(observer); }
    public void unsubscribe(Runnable observer) { observers.remove(observer); }
    public void subscribeConfiguration(Runnable observer) { configurationObservers.add(observer); }
    public void unsubscribeConfiguration(Runnable observer) { configurationObservers.remove(observer); }

    public void changed() {
        for (Runnable observer : new ArrayList<>(observers)) observer.run();
    }

    public void read(Consumer<Dashboard> success, Consumer<String> error) {
        worker.execute(() -> {
            try {
                Settings settings;
                String diagnostic = String.join("\n", health.values());
                try {
                    settings = prefs.settings();
                } catch (RuntimeException invalid) {
                    settings = Settings.defaults();
                    diagnostic = "Saved settings are invalid. Save new limits to repair them.\n" + diagnostic;
                }
                DailyWindow window = prefs.window(System.currentTimeMillis(), settings.resetMinute);
                Dashboard result = new Dashboard(settings, window, db.totals(window.start),
                        whitelist.members(), whitelist.recent(), diagnostic);
                main.post(() -> success.accept(result));
            } catch (RuntimeException failure) {
                report(failure);
                main.post(() -> error.accept("Could not load ReelGuard data."));
            }
        });
    }

    public void save(Settings settings, Consumer<Boolean> completed) {
        edit(() -> prefs.save(settings), completed);
    }

    public void setMember(String key, boolean enabled, Consumer<Boolean> completed) {
        edit(() -> whitelist.setMember(key, enabled), completed);
    }

    private void edit(Runnable edit, Consumer<Boolean> completed) {
        worker.execute(() -> {
            try {
                edit.run();
                main.post(() -> {
                    for (Runnable observer : new ArrayList<>(configurationObservers)) observer.run();
                    changed();
                    completed.accept(true);
                });
            } catch (RuntimeException failure) {
                report(failure);
                main.post(() -> completed.accept(false));
            }
        });
    }

    public void process(ScreenState state, int owner, long wall, long elapsed,
                        Consumer<Evaluation> success, Runnable failed) {
        worker.execute(() -> {
            try {
                Settings settings = prefs.settings();
                DailyWindow window = prefs.window(wall, settings.resetMinute);
                if (restoredWindow == Long.MIN_VALUE) {
                    counter.restore(window.start, db.recent(window.start, Platform.IG),
                            db.recent(window.start, Platform.YT));
                }
                restoredWindow = window.start;
                Eligibility eligibility = state.isTarget()
                        ? ContentIdentity.eligibility(state.platform, state.creator, whitelist.members())
                        : Eligibility.PENDING;
                if (state.isTarget()) whitelist.remember(state.creator);
                Totals totals = db.totals(window.start);
                PolicyEngine.Decision decision = policy.evaluate(state, eligibility, totals, settings, window);
                if (decision.action == PolicyEngine.Action.BLOCK) {
                    counter.close();
                } else {
                    List<CounterEngine.Checkpoint> changes = counter.observe(state, eligibility, owner, window,
                            wall, elapsed, settings);
                    if (!changes.isEmpty()) {
                        totals = db.apply(changes, window.start);
                        decision = policy.evaluate(state, eligibility, totals, settings, window);
                        main.post(this::changed);
                    }
                    if (decision.action == PolicyEngine.Action.BLOCK) counter.close();
                }
                boolean warn = decision.action == PolicyEngine.Action.WARN &&
                        !prefs.set("warned.v1").contains(decision.key);
                long nextConfirmation = Math.min(1000, counter.untilCount());
                if (state.isTarget() && eligibility == Eligibility.ELIGIBLE && decision.action != PolicyEngine.Action.BLOCK) {
                    int scope = settings.scope(state.surface);
                    long cap = settings.minutes(scope) * 60000L;
                    if (cap > 0) {
                        long used = totals.millis(scope);
                        long warning = PolicyEngine.warningAt(cap, settings.warnPercent);
                        long nextTimeThreshold = (used < warning ? warning : cap) - used;
                        nextConfirmation = Math.min(nextConfirmation, Math.max(1, nextTimeThreshold));
                    }
                }
                Evaluation result = new Evaluation(decision, warn, nextConfirmation);
                main.post(() -> success.accept(result));
            } catch (RuntimeException failure) {
                counter.close();
                restoredWindow = Long.MIN_VALUE;
                report(failure);
                main.post(failed);
            }
        });
    }

    public void closeSession() { worker.execute(counter::close); }

    public void markWarning(String key) {
        worker.execute(() -> {
            try {
                Set<String> shown = prefs.set("warned.v1");
                String dayPrefix = key.substring(0, key.indexOf(':') + 1);
                shown.removeIf(value -> !value.startsWith(dayPrefix));
                if (shown.add(key)) prefs.saveSet("warned.v1", shown);
            } catch (RuntimeException failure) {
                report(failure);
            }
        });
    }

    public void setHealth(Platform platform, String message, Consumer<Boolean> shouldNotify) {
        worker.execute(() -> {
            if (message == null) health.remove(platform);
            else health.put(platform, message);
            boolean due = false;
            if (message != null && shouldNotify != null) {
                try {
                    String key = "health.alert." + platform.key;
                    String saved = prefs.text(key);
                    long now = System.currentTimeMillis();
                    due = saved == null || now - Long.parseLong(saved) >= 86400000L;
                    if (due) prefs.put(key, String.valueOf(now));
                } catch (RuntimeException failure) {
                    report(failure);
                }
            }
            final boolean notify = due;
            main.post(() -> {
                changed();
                if (shouldNotify != null) shouldNotify.accept(notify);
            });
        });
    }

    private static void report(RuntimeException failure) {
        Log.w("ReelGuard", "Data operation failed: " + failure.getClass().getSimpleName());
    }
}
