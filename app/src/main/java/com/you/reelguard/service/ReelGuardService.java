package com.you.reelguard.service;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.you.reelguard.data.Repository;
import com.you.reelguard.debug.DebugTools;
import com.you.reelguard.detect.DetectorRouter;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.ScreenState;
import com.you.reelguard.detect.SelectorCatalog;
import com.you.reelguard.enforce.OverlayEnforcer;
import com.you.reelguard.engine.PolicyEngine.Action;
import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import com.you.reelguard.snapshot.SnapshotBuilder;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReelGuardService extends AccessibilityService {
    private static final long INACTIVITY_MS = 3000;
    private static final long DEBOUNCE_MS = 300;
    private static boolean connected;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable captureTask = this::beginCapture;
    private final Runnable inactivity = this::endSession;
    private final Runnable configuration = this::onConfigurationEdited;

    private Repository repository;
    private ExecutorService captureWorker;
    private OverlayEnforcer enforcer;
    private HealthMonitor health;
    private DetectorRouter router;
    private volatile long revision;
    private long lastEvent = -1;
    private long scheduledAt = Long.MAX_VALUE;
    private boolean inFlight;
    private long followUp = Long.MAX_VALUE;
    private boolean registered;
    private Platform lastPlatform;
    private int lastEventWindow = -1;

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) endSession();
            else {
                repository.changed();
                configuration.run();
            }
        }
    };

    public static boolean isConnected() { return connected; }

    private void onConfigurationEdited() {
        revision++;
        if (lastEvent >= 0) schedule(0);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (captureWorker != null) return;
        repository = Repository.get(this);
        enforcer = new OverlayEnforcer(this, main);
        health = new HealthMonitor(this, repository);
        captureWorker = Executors.newSingleThreadExecutor();
        captureWorker.execute(this::loadCatalog);
        repository.subscribeConfiguration(configuration);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, filter);
        registered = true;
        connected = true;
        repository.changed();
        lastEvent = SystemClock.elapsedRealtime();
        main.postDelayed(inactivity, INACTIVITY_MS);
        schedule(0);
    }

    private void loadCatalog() {
        try (Reader input = new InputStreamReader(getAssets().open("selectors.json"), StandardCharsets.UTF_8)) {
            router = new DetectorRouter(SelectorCatalog.load(input));
        } catch (Exception invalid) {
            router = new DetectorRouter(null);
            Log.w("ReelGuard", "Selector catalog could not be loaded");
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (captureWorker == null) return;
        Platform platform = Platform.fromPackage(event.getPackageName());
        if (platform == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;
        int window = event.getWindowId();
        boolean transition = (lastPlatform != null && platform != lastPlatform) ||
                (lastEventWindow >= 0 && window >= 0 && window != lastEventWindow);
        if (transition) {
            repository.closeSession();
            enforcer.clear();
            health.leave();
        }
        if (transition || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_SCROLLED) revision++;
        lastPlatform = platform;
        lastEventWindow = window;
        lastEvent = SystemClock.elapsedRealtime();
        main.removeCallbacks(inactivity);
        main.postDelayed(inactivity, INACTIVITY_MS);
        schedule(transition || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ? 0 : DEBOUNCE_MS);
    }

    private void schedule(long delay) {
        if (captureWorker == null || lastEvent < 0) return;
        if (inFlight) {
            followUp = Math.min(followUp, delay);
            return;
        }
        long due = SystemClock.uptimeMillis() + delay;
        if (due >= scheduledAt) return;
        scheduledAt = due;
        main.removeCallbacks(captureTask);
        main.postAtTime(captureTask, due);
    }

    private void beginCapture() {
        scheduledAt = Long.MAX_VALUE;
        if (captureWorker == null || lastEvent < 0) return;
        if (SystemClock.elapsedRealtime() - lastEvent >= INACTIVITY_MS) {
            endSession();
            return;
        }
        inFlight = true;
        long ticket = revision;
        captureWorker.execute(() -> {
            Observation found;
            try {
                found = inspect();
            } catch (RuntimeException failure) {
                Log.w("ReelGuard", "Window inspection failed: " + failure.getClass().getSimpleName());
                found = null;
            }
            Observation observation = found;
            main.post(() -> accept(ticket, observation));
        });
    }

    private Observation inspect() {
        Owner owner = activeOwner();
        if (owner == null) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            if (root == null || root.getWindowId() != owner.id ||
                    Platform.fromPackage(root.getPackageName()) != owner.platform) return null;
            NodeSnapshot tree = new SnapshotBuilder().capture(root);
            long version = -1;
            try {
                version = getPackageManager().getPackageInfo(owner.platform.packageName, 0).getLongVersionCode();
            } catch (PackageManager.NameNotFoundException ignored) {
                // A package update can temporarily remove its version metadata.
            }
            String locale = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
            ScreenState state = router.classify(owner.platform, version, tree, owner.bounds, locale);
            DebugTools.capture(this, owner.platform, version, owner.id, owner.bounds, locale, tree, state);
            return new Observation(owner, state, version);
        } finally {
            SnapshotBuilder.recycle(root);
        }
    }

    private void accept(long ticket, Observation observation) {
        if (captureWorker == null) return;
        if (ticket != revision || lastEvent < 0) {
            finishCapture(0);
            return;
        }
        if (observation == null || !owns(observation.owner)) {
            repository.closeSession();
            enforcer.clear();
            health.leave();
            finishCapture(Long.MAX_VALUE);
            return;
        }
        health.record(observation.state, observation.version, observation.elapsed);
        repository.process(observation.state, observation.owner.id, observation.wall, observation.elapsed,
                evaluation -> {
                    if (captureWorker == null) return;
                    if (ticket != revision || lastEvent < 0 || !owns(observation.owner)) {
                        enforcer.clear();
                        finishCapture(0);
                        return;
                    }
                    if (evaluation.decision.action == Action.BLOCK) {
                        final Owner owner = observation.owner;
                        enforcer.block(evaluation.decision, owner.id, owner.bounds, () -> owns(owner),
                                () -> schedule(0));
                    } else {
                        enforcer.allow();
                        if (evaluation.showWarning && enforcer.warn(evaluation.decision, observation.owner.bounds))
                            repository.markWarning(evaluation.decision.key);
                    }
                    long age = SystemClock.elapsedRealtime() - observation.elapsed;
                    long next = enforcer.isBlocked() ? DEBOUNCE_MS : Math.max(1, evaluation.nextConfirmation - age);
                    if (observation.state.isTarget()) {
                        next = Math.min(next, Math.max(1, evaluation.decision.reset - System.currentTimeMillis()));
                    } else if (observation.state.evidence != ScreenState.Evidence.UNKNOWN) {
                        next = Long.MAX_VALUE;
                    }
                    finishCapture(next);
                }, () -> {
                    if (enforcer != null) enforcer.clear();
                    finishCapture(1000);
                });
    }

    private void finishCapture(long next) {
        inFlight = false;
        long delay = Math.min(next, followUp);
        followUp = Long.MAX_VALUE;
        if (delay != Long.MAX_VALUE) schedule(delay);
    }

    private boolean owns(Owner expected) {
        Owner current = activeOwner();
        return current != null && current.id == expected.id && current.platform == expected.platform &&
                current.bounds.left == expected.bounds.left && current.bounds.top == expected.bounds.top &&
                current.bounds.right == expected.bounds.right && current.bounds.bottom == expected.bounds.bottom;
    }

    private Owner activeOwner() {
        if (!getSystemService(PowerManager.class).isInteractive() ||
                getSystemService(KeyguardManager.class).isKeyguardLocked()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        Platform platform;
        int id;
        try {
            platform = Platform.fromPackage(root.getPackageName());
            id = root.getWindowId();
        } finally {
            SnapshotBuilder.recycle(root);
        }
        if (platform == null) return null;
        List<AccessibilityWindowInfo> windows = getWindows();
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getId() != id || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION ||
                        !window.isFocused()) continue;
                if (Build.VERSION.SDK_INT >= 33 && window.getDisplayId() != android.view.Display.DEFAULT_DISPLAY) return null;
                Rect rect = new Rect();
                window.getBoundsInScreen(rect);
                if (!rect.isEmpty()) return new Owner(platform, id, new Bounds(rect.left, rect.top, rect.right, rect.bottom));
            }
            return null;
        } finally {
            if (Build.VERSION.SDK_INT < 33) for (AccessibilityWindowInfo window : windows) recycleWindow(window);
        }
    }

    @SuppressWarnings("deprecation")
    private static void recycleWindow(AccessibilityWindowInfo window) { window.recycle(); }

    private void endSession() {
        revision++;
        lastEvent = -1;
        lastEventWindow = -1;
        lastPlatform = null;
        scheduledAt = Long.MAX_VALUE;
        followUp = Long.MAX_VALUE;
        main.removeCallbacks(inactivity);
        main.removeCallbacks(captureTask);
        if (repository != null) repository.closeSession();
        if (enforcer != null) enforcer.clear();
        if (health != null) health.leave();
    }

    @Override public void onInterrupt() { endSession(); }
    @Override public boolean onUnbind(Intent intent) { disconnect(); return super.onUnbind(intent); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }

    private void disconnect() {
        endSession();
        if (captureWorker != null) captureWorker.shutdownNow();
        captureWorker = null;
        DebugTools.cancel();
        if (registered) unregisterReceiver(changes);
        registered = false;
        inFlight = false;
        connected = false;
        if (repository != null) {
            repository.unsubscribeConfiguration(configuration);
            repository.changed();
        }
    }

    private static final class Owner {
        final Platform platform;
        final int id;
        final Bounds bounds;
        Owner(Platform platform, int id, Bounds bounds) {
            this.platform = platform;
            this.id = id;
            this.bounds = bounds;
        }
    }

    private static final class Observation {
        final Owner owner;
        final ScreenState state;
        final long version;
        final long wall = System.currentTimeMillis();
        final long elapsed = SystemClock.elapsedRealtime();

        Observation(Owner owner, ScreenState state, long version) {
            this.owner = owner;
            this.state = state;
            this.version = version;
        }
    }
}
