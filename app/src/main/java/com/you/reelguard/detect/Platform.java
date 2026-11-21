package com.you.reelguard.detect;

public enum Platform {
    IG("ig", "com.instagram.android", "Instagram"),
    YT("yt", "com.google.android.youtube", "YouTube");

    public final String key;
    public final String packageName;
    public final String label;

    Platform(String key, String packageName, String label) {
        this.key = key;
        this.packageName = packageName;
        this.label = label;
    }

    public static Platform fromPackage(CharSequence name) {
        for (Platform platform : values()) {
            if (platform.packageName.contentEquals(name == null ? "" : name)) return platform;
        }
        return null;
    }
}
