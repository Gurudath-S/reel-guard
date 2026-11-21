package com.you.reelguard.data;

/** Main-thread state for controls and actions. The repository worker reads active() for policy. */
final class EnforcementMode {
    private boolean enabled;
    private int pendingWrites;
    private volatile boolean active;
    private long revision;

    EnforcementMode(boolean enabled) {
        this.enabled = enabled;
        active = enabled;
    }

    boolean enabled() { return enabled; }
    boolean pending() { return pendingWrites > 0; }
    boolean active() { return active; }
    long revision() { return revision; }
    boolean permits(long ticket) { return active && ticket == revision; }

    void beginWrite() {
        pendingWrites++;
        updateActive();
    }

    void finishWrite(boolean savedEnabled) {
        enabled = savedEnabled;
        pendingWrites--;
        updateActive();
    }

    private void updateActive() {
        boolean next = enabled && !pending();
        if (next != active) {
            revision++;
            active = next;
        }
    }
}
