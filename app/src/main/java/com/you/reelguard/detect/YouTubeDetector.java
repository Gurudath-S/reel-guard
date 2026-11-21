package com.you.reelguard.detect;

import com.you.reelguard.detect.ScreenState.Evidence;
import com.you.reelguard.detect.SelectorCatalog.Profile;
import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class YouTubeDetector implements ScreenDetector {
    @Override
    public ScreenState detect(NodeSnapshot root, Bounds bounds, Profile profile, String locale) {
        if (!profile.find("exclude", root, bounds, locale).isEmpty())
            return ScreenState.other(Platform.YT, Evidence.KNOWN_OTHER);
        NodeSnapshot player = ScreenDetector.dominant(profile.find("shortsPlayer", root, bounds, locale), bounds);
        if (player != null) {
            Bounds viewport = player.bounds.clip(bounds);
            NodeSnapshot current = ScreenDetector.dominant(profile.find("currentShort", player, viewport, locale), viewport);
            if (current != null) return describeShort(current, viewport, profile, locale);
        }
        if (!profile.find("ordinary", root, bounds, locale).isEmpty())
            return ScreenState.other(Platform.YT, Evidence.KNOWN_OTHER);
        return ScreenState.other(Platform.YT, Evidence.UNKNOWN);
    }

    private ScreenState describeShort(NodeSnapshot item, Bounds viewport, Profile profile, String locale) {
        ScreenState state = ScreenDetector.item(Platform.YT, Surface.YT_SHORTS, item, viewport, profile, locale);
        if (state.signature != null || state.creator == null) return state;
        String caption = extractCaptionSibling(item, viewport, profile, locale);
        return new ScreenState(state.platform, state.surface, state.creator,
                ContentIdentity.signature(state.platform, null, state.creator, caption), state.evidence);
    }

    private String extractCaptionSibling(NodeSnapshot item, Bounds viewport, Profile profile, String locale) {
        List<NodeSnapshot> authors = profile.find("creator", item, viewport, locale);
        if (authors.size() != 1) return null;
        NodeSnapshot author = authors.get(0);
        Set<NodeSnapshot> candidates = new HashSet<>(profile.find("captionSibling", item, viewport, locale));
        for (NodeSnapshot parent : item.visibleSubtree(viewport)) {
            if (!parent.children.contains(author)) continue;
            NodeSnapshot caption = null;
            for (NodeSnapshot child : parent.children) {
                if (!candidates.contains(child) || !belowHandle(child.bounds, author.bounds)) continue;
                if (caption == null || child.bounds.top < caption.bounds.top ||
                        (child.bounds.top == caption.bounds.top && child.bounds.width() > caption.bounds.width())) {
                    caption = child;
                }
            }
            return caption == null ? null : caption.description;
        }
        return null;
    }

    private boolean belowHandle(Bounds row, Bounds handle) {
        int gap = row.top - handle.bottom;
        return gap >= 0 && gap <= handle.height() && row.left <= handle.left && row.right > handle.left;
    }
}
