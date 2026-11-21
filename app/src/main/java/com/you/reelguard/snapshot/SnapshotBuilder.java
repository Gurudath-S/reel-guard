package com.you.reelguard.snapshot;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotBuilder {
    private static final int MAX_NODES = 600;
    private static final int MAX_DEPTH = 32;
    private int visited;
    private boolean incomplete;

    public NodeSnapshot capture(AccessibilityNodeInfo root) {
        visited = 0;
        incomplete = false;
        NodeSnapshot tree = copy(root, 0);
        return incomplete ? null : tree;
    }

    private NodeSnapshot copy(AccessibilityNodeInfo node, int depth) {
        if (node == null || ++visited > MAX_NODES || depth > MAX_DEPTH) {
            incomplete = true;
            return null;
        }
        try {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            List<NodeSnapshot> children = new ArrayList<>();
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount && !incomplete; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                try {
                    NodeSnapshot copied = copy(child, depth + 1);
                    if (copied != null) children.add(copied);
                } finally {
                    recycle(child);
                }
            }
            boolean redact = node.isEditable() || node.isPassword();
            return new NodeSnapshot(node.getViewIdResourceName(), string(node.getClassName()),
                    redact ? null : string(node.getText()), redact ? null : string(node.getContentDescription()),
                    node.isSelected(), node.isScrollable(), node.isVisibleToUser(),
                    new Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom), children);
        } catch (IllegalStateException stale) {
            incomplete = true;
            return null;
        }
    }

    private static String string(CharSequence value) {
        if (value == null || value.length() == 0) return null;
        String text = value.toString();
        int length = text.codePointCount(0, text.length());
        return length <= 512 ? text : text.substring(0, text.offsetByCodePoints(0, 512));
    }

    @SuppressWarnings("deprecation")
    public static void recycle(AccessibilityNodeInfo node) {
        if (node != null && Build.VERSION.SDK_INT < 33) node.recycle();
    }
}
