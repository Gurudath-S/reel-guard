package com.you.reelguard.debug;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.ScreenState;
import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;

public final class DebugTools {
    private DebugTools() {}
    public static void bind(Activity activity, View button) { button.setVisibility(View.GONE); }
    public static void cancel() {}
    public static void capture(Context context, Platform platform, long version, int windowId,
                               Bounds bounds, String locale, NodeSnapshot tree, ScreenState result) {}
}
