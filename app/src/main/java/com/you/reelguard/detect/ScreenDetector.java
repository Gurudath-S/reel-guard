package com.you.reelguard.detect;

import com.you.reelguard.detect.SelectorCatalog.Profile;
import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface ScreenDetector {
    ScreenState detect(NodeSnapshot root, Bounds bounds, Profile profile, String locale);

    static NodeSnapshot dominant(List<NodeSnapshot> rows, Bounds viewport) {
        Set<NodeSnapshot> nested = new HashSet<>();
        for (NodeSnapshot row : rows) {
            for (NodeSnapshot child : row.children) nested.addAll(child.visibleSubtree(viewport));
        }
        NodeSnapshot winner = null;
        long best = 0;
        for (NodeSnapshot row : rows) {
            if (nested.contains(row)) continue;
            Bounds visible = row.bounds.clip(viewport);
            if ((long) visible.height() * 2 < Math.min(row.bounds.height(), viewport.height())) continue;
            long area = visible.area();
            if (area > best) {
                winner = row;
                best = area;
            } else if (area == best) {
                winner = null;
            }
        }
        return winner;
    }

    static ScreenState item(Platform platform, Surface surface, NodeSnapshot node, Bounds viewport,
                            Profile profile, String locale) {
        String creator = ContentIdentity.creatorKey(platform, profile.extract("creator", node, viewport, locale));
        String id = profile.extract("stableId", node, viewport, locale);
        String caption = profile.extract("caption", node, viewport, locale);
        return new ScreenState(platform, surface, creator,
                ContentIdentity.signature(platform, id, creator, caption), ScreenState.Evidence.TARGET);
    }
}
