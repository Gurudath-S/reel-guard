package com.you.reelguard.detect;

public final class ScreenState {
    public enum Evidence { TARGET, KNOWN_OTHER, UNKNOWN, UNAVAILABLE }

    public final Platform platform;
    public final Surface surface;
    public final String creator;
    public final String signature;
    public final Evidence evidence;

    public ScreenState(Platform platform, Surface surface, String creator, String signature, Evidence evidence) {
        this.platform = platform;
        this.surface = surface;
        this.creator = creator;
        this.signature = signature;
        this.evidence = evidence;
    }

    public static ScreenState other(Platform platform, Evidence evidence) {
        return new ScreenState(platform, Surface.OTHER, null, null, evidence);
    }

    public boolean isTarget() {
        return evidence == Evidence.TARGET && surface != Surface.OTHER;
    }
}
