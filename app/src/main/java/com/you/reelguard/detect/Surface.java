package com.you.reelguard.detect;

public enum Surface {
    IG_REELS_TAB("reels_tab", "Instagram Reels tab"),
    IG_FEED_VIDEO("feed_video", "Instagram feed videos"),
    YT_SHORTS("shorts", "YouTube Shorts"),
    OTHER("other", "Other");

    public final String key;
    public final String label;

    Surface(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
