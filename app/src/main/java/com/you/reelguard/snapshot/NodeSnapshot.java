package com.you.reelguard.snapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NodeSnapshot {
    public static final class Bounds {
        public final int left, top, right, bottom;

        public Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public Bounds clip(Bounds other) {
            return new Bounds(Math.max(left, other.left), Math.max(top, other.top),
                    Math.min(right, other.right), Math.min(bottom, other.bottom));
        }

        public int height() { return Math.max(0, bottom - top); }
        public int width() { return Math.max(0, right - left); }
        public long area() { return (long) width() * height(); }
    }

    public final String id, className, text, description;
    public final boolean selected, scrollable, visible;
    public final Bounds bounds;
    public final List<NodeSnapshot> children;

    public NodeSnapshot(String id, String className, String text, String description,
                        boolean selected, boolean scrollable, boolean visible, Bounds bounds,
                        List<NodeSnapshot> children) {
        this.id = id;
        this.className = className;
        this.text = text;
        this.description = description;
        this.selected = selected;
        this.scrollable = scrollable;
        this.visible = visible;
        this.bounds = bounds;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    public List<NodeSnapshot> visibleSubtree(Bounds viewport) {
        ArrayDeque<NodeSnapshot> pending = new ArrayDeque<>();
        List<NodeSnapshot> result = new ArrayList<>();
        pending.push(this);
        while (!pending.isEmpty()) {
            NodeSnapshot node = pending.pop();
            if (node.visible && node.bounds.clip(viewport).area() > 0) result.add(node);
            for (int i = node.children.size() - 1; i >= 0; i--) pending.push(node.children.get(i));
        }
        return result;
    }
}
