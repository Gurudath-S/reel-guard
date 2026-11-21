package com.you.reelguard.debug;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import com.google.gson.GsonBuilder;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.detect.ScreenState;
import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DebugTools {
    private static Platform armed;
    private static long expires;
    private static String message = "Arm a capture and switch to the target app within 60 seconds.";
    private static volatile String lastObservation = "";
    private static final List<String> SAFE_LABELS = Arrays.asList(
            "Home", "Reels", "Shorts", "Search", "Comments", "Like", "Share", "Subscribe", "Profile");

    private DebugTools() {}

    public static void bind(Activity activity, View button) {
        button.setOnClickListener(view -> activity.startActivity(new Intent(activity, DebugActivity.class)));
    }

    static synchronized void arm(Platform platform) {
        armed = platform;
        expires = SystemClock.elapsedRealtime() + 60000;
        message = "Armed for " + platform.label + ". Switch to the screen you want to capture.";
    }

    public static synchronized void cancel() {
        if (armed != null) message = "Capture canceled when the service disconnected.";
        armed = null;
    }

    static synchronized String status() {
        if (armed != null && SystemClock.elapsedRealtime() >= expires) {
            armed = null;
            message = "Capture expired.";
        }
        return lastObservation + (lastObservation.isEmpty() ? "" : "\n\n") + message;
    }

    public static void capture(Context context, Platform platform, long version, int windowId,
                               Bounds bounds, String locale, NodeSnapshot tree, ScreenState result) {
        lastObservation = "Last screen  ·  " + platform.label + " " + version + "\n" +
                result.surface + "  ·  " + result.evidence +
                (result.creator != null ? "  ·  creator resolved" : "") +
                (result.signature != null ? "  ·  signature resolved" : "");
        synchronized (DebugTools.class) {
            if (tree == null || armed != platform || SystemClock.elapsedRealtime() >= expires) return;
            armed = null;
        }
        File folder = new File(context.getFilesDir(), "fixtures");
        String name = platform.key + "-" + UUID.randomUUID() + ".json";
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Cannot create fixture directory");
            File file = new File(folder, name);
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("schemaVersion", 1);
                frame.put("packageName", platform.packageName);
                frame.put("versionCode", version);
                frame.put("windowId", windowId);
                frame.put("windowBounds", bounds);
                frame.put("locale", locale);
                frame.put("status", "COMPLETE");
                frame.put("surface", result.surface);
                frame.put("evidence", result.evidence);
                frame.put("root", sanitize(tree, new HashMap<>()));
                new GsonBuilder().setPrettyPrinting().create().toJson(frame, out);
            }
            synchronized (DebugTools.class) { message = "Saved files/fixtures/" + name; }
        } catch (java.io.IOException | RuntimeException error) {
            synchronized (DebugTools.class) { message = "Could not save the capture."; }
        }
    }

    private static NodeSnapshot sanitize(NodeSnapshot node, Map<String, String> replacements) {
        List<NodeSnapshot> children = new ArrayList<>();
        for (NodeSnapshot child : node.children) children.add(sanitize(child, replacements));
        String id = node.id != null && node.id.matches("(com\\.instagram\\.android|com\\.google\\.android\\.youtube|android):id/[a-zA-Z0-9_]+")
                ? node.id : null;
        return new NodeSnapshot(id, node.className, redact(node.text, replacements),
                redact(node.description, replacements), node.selected, node.scrollable, node.visible, node.bounds, children);
    }

    private static String redact(String value, Map<String, String> replacements) {
        if (value == null || SAFE_LABELS.contains(value)) return value;
        if (value.startsWith("Reel by ")) return "Reel by " + placeholder(value.substring(8), replacements);
        if (value.startsWith("@") && !value.contains(" ")) return "@" + placeholder(value, replacements);
        return placeholder(value, replacements);
    }

    private static String placeholder(String value, Map<String, String> replacements) {
        return replacements.computeIfAbsent(value, ignored -> "sample_" + (replacements.size() + 1));
    }
}
