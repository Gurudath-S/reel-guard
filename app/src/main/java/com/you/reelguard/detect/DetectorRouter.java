package com.you.reelguard.detect;

import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;

public final class DetectorRouter {
    private final SelectorCatalog catalog;
    private final ScreenDetector instagram = new InstagramDetector();
    private final ScreenDetector youtube = new YouTubeDetector();

    public DetectorRouter(SelectorCatalog catalog) { this.catalog = catalog; }

    public ScreenState classify(Platform platform, long version, NodeSnapshot root, Bounds bounds, String locale) {
        if (root == null) return ScreenState.other(platform, ScreenState.Evidence.UNAVAILABLE);
        if (catalog == null) return ScreenState.other(platform, ScreenState.Evidence.UNKNOWN);
        ScreenDetector detector = platform == Platform.IG ? instagram : youtube;
        for (SelectorCatalog.Profile profile : catalog.profilesFor(platform, version)) {
            ScreenState result = detector.detect(root, bounds, profile, locale);
            if (result.evidence != ScreenState.Evidence.UNKNOWN) return result;
        }
        return ScreenState.other(platform, ScreenState.Evidence.UNKNOWN);
    }
}
