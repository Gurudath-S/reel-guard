package com.you.reelguard.enforce;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import com.you.reelguard.R;
import com.you.reelguard.engine.PolicyEngine.Decision;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.util.Date;
import java.util.function.BooleanSupplier;

public final class OverlayEnforcer {
    private final AccessibilityService service;
    private final Handler handler;
    private final WindowManager manager;
    private View shown;
    private boolean blocked;
    private int attemptedOwner = -1;
    private final Runnable expireBanner = this::clearWarning;

    public OverlayEnforcer(AccessibilityService service, Handler handler) {
        this.service = service;
        this.handler = handler;
        manager = service.getSystemService(WindowManager.class);
    }

    public boolean isBlocked() { return blocked; }

    public boolean warn(Decision decision, Bounds bounds) {
        if (blocked) return false;
        clear();
        TextView banner = (TextView) LayoutInflater.from(service).inflate(R.layout.warning, null);
        banner.setText(message(decision));
        if (!attach(banner, bounds, false)) return false;
        handler.postDelayed(expireBanner, 2000);
        return true;
    }

    public void block(Decision decision, int windowId, Bounds bounds,
                      BooleanSupplier validOwner, Runnable reconcile) {
        if (attemptedOwner == windowId) return;
        clear();
        if (!validOwner.getAsBoolean()) return;
        attemptedOwner = windowId;
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        if (!validOwner.getAsBoolean()) {
            reconcile.run();
            return;
        }
        View shield = LayoutInflater.from(service).inflate(R.layout.block, null);
        shield.setBackgroundColor(Color.rgb(17, 28, 24));
        shield.setClickable(true);
        TextView reason = shield.findViewById(R.id.block_reason);
        reason.setText(message(decision) + "\nResets at " +
                DateFormat.getTimeFormat(service).format(new Date(decision.reset)));
        Button close = shield.findViewById(R.id.close_app);
        close.setOnClickListener(view -> {
            if (validOwner.getAsBoolean() && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) clear();
            reconcile.run();
        });
        blocked = attach(shield, bounds, true);
        reconcile.run();
    }

    public void allow() {
        if (blocked || attemptedOwner >= 0) clear();
    }

    private boolean attach(View view, Bounds bounds, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(bounds.width(),
                touchable ? bounds.height() : WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = bounds.left;
        params.y = bounds.top;
        try {
            manager.addView(view, params);
            shown = view;
            return true;
        } catch (RuntimeException failure) {
            Log.w("ReelGuard", "Overlay attach failed: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private void clearWarning() {
        if (!blocked) clear();
    }

    public void clear() {
        handler.removeCallbacks(expireBanner);
        if (shown != null) {
            try {
                manager.removeViewImmediate(shown);
            } catch (IllegalArgumentException ignored) {
                // Android can remove the window when the service disconnects.
            }
        }
        shown = null;
        attemptedOwner = -1;
        blocked = false;
    }

    private static String message(Decision decision) {
        if (!decision.time) return decision.used + " of " + decision.cap + " reels used";
        return decision.used / 1000 + " of " + decision.cap / 1000 + " seconds used";
    }
}
