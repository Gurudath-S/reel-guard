package com.you.reelguard.detect;

import com.you.reelguard.detect.ScreenState.Evidence;
import com.you.reelguard.detect.SelectorCatalog.Profile;
import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.util.ArrayList;
import java.util.List;

public final class InstagramDetector implements ScreenDetector {
    @Override
    public ScreenState detect(NodeSnapshot root, Bounds bounds, Profile profile, String locale) {
        if (!profile.find("exclude", root, bounds, locale).isEmpty())
            return ScreenState.other(Platform.IG, Evidence.KNOWN_OTHER);
        NodeSnapshot pager = ScreenDetector.dominant(profile.find("reelsPager", root, bounds, locale), bounds);
        boolean reels = pager != null && tabSelected("reelsTabSelected", root, bounds, profile, locale);
        NodeSnapshot feed = findHomeFeed(root, bounds, profile, locale, reels);
        if (reels && feed != null) return ScreenState.other(Platform.IG, Evidence.UNKNOWN);
        if (reels) {
            Bounds viewport = pager.bounds.clip(bounds);
            NodeSnapshot current = ScreenDetector.dominant(profile.find("reelsItem", pager, viewport, locale), viewport);
            if (current != null) return ScreenDetector.item(Platform.IG, Surface.IG_REELS_TAB, current, viewport, profile, locale);
        }
        if (feed != null) return classifyFeed(feed, bounds, profile, locale);
        if (tabSelected("directTab", root, bounds, profile, locale) ||
                tabSelected("profileTab", root, bounds, profile, locale) ||
                tabSelected("searchTab", root, bounds, profile, locale))
            return ScreenState.other(Platform.IG, Evidence.KNOWN_OTHER);
        return ScreenState.other(Platform.IG, Evidence.UNKNOWN);
    }

    private boolean tabSelected(String role, NodeSnapshot root, Bounds bounds, Profile profile, String locale) {
        for (NodeSnapshot tab : profile.find(role, root, bounds, locale)) {
            if (tab.selected || !profile.find("selectedTabIcon", tab, bounds, locale).isEmpty()) return true;
        }
        return false;
    }

    private NodeSnapshot findHomeFeed(NodeSnapshot root, Bounds bounds, Profile profile, String locale, boolean reels) {
        NodeSnapshot list = ScreenDetector.dominant(profile.find("homeFeedList", root, bounds, locale), bounds);
        if (list == null) return null;
        if (tabSelected("homeTabSelected", root, bounds, profile, locale)) return list;

        // The Home tab can omit its selected icon from the service's accessibility tree.
        if (reels || profile.find("homeTabSelected", root, bounds, locale).isEmpty() ||
                tabSelected("directTab", root, bounds, profile, locale) ||
                tabSelected("profileTab", root, bounds, profile, locale) ||
                tabSelected("searchTab", root, bounds, profile, locale)) return null;
        return profile.find("homeFeedEvidence", list, list.bounds.clip(bounds), locale).isEmpty() ? null : list;
    }

    private ScreenState classifyFeed(NodeSnapshot feed, Bounds bounds, Profile profile, String locale) {
        Bounds viewport = feed.bounds.clip(bounds);
        List<NodeSnapshot> media = profile.find("feedMedia", feed, viewport, locale);
        if (!media.isEmpty()) return classifyGroupedFeed(feed, media, viewport, profile, locale);
        NodeSnapshot row = ScreenDetector.dominant(profile.find("feedRow", feed, viewport, locale), viewport);
        if (row != null && !profile.find("videoMarker", row, viewport, locale).isEmpty())
            return ScreenDetector.item(Platform.IG, Surface.IG_FEED_VIDEO, row, viewport, profile, locale);
        if (row != null && !profile.find("photoMarker", row, viewport, locale).isEmpty())
            return ScreenState.other(Platform.IG, Evidence.KNOWN_OTHER);
        return ScreenState.other(Platform.IG, Evidence.UNKNOWN);
    }

    private ScreenState classifyGroupedFeed(NodeSnapshot feed, List<NodeSnapshot> media,
                                            Bounds viewport, Profile profile, String locale) {
        // Ignore media inside shelves and other nested lists. A feed post is a direct list group.
        List<NodeSnapshot> groups = new ArrayList<>();
        for (NodeSnapshot child : feed.children) {
            for (NodeSnapshot candidate : media) {
                if (child == candidate || child.children.contains(candidate)) {
                    groups.add(child);
                    break;
                }
            }
        }
        NodeSnapshot row = ScreenDetector.dominant(groups, viewport);
        if (row == null) return ScreenState.other(Platform.IG, Evidence.UNKNOWN);
        if (profile.find("videoMarker", row, viewport, locale).isEmpty())
            return ScreenState.other(Platform.IG, Evidence.KNOWN_OTHER);

        String creator = ContentIdentity.creatorKey(Platform.IG, profile.extract("feedCreator", row, viewport, locale));
        String caption = null;
        int footerIndex = feed.children.indexOf(row) + 1;
        if (footerIndex < feed.children.size()) {
            // Instagram lays out the controls and caption in the immediate next recycler item.
            NodeSnapshot footer = feed.children.get(footerIndex);
            if (!profile.find("feedPostActions", footer, viewport, locale).isEmpty() &&
                    profile.find("feedMedia", footer, viewport, locale).isEmpty()) {
                for (NodeSnapshot line : profile.find("feedCaption", footer, viewport, locale)) {
                    String copy = line.text == null ? "" : line.text.trim();
                    for (NodeSnapshot author : profile.find("feedCaptionAuthor", line, viewport, locale)) {
                        String handle = author.description;
                        String footerCreator = ContentIdentity.creatorKey(Platform.IG, handle);
                        if (footerCreator == null || (creator != null && !creator.equals(footerCreator))) continue;
                        if (!copy.startsWith(handle + " ") && !copy.startsWith(handle + "\n")) continue;
                        creator = footerCreator;
                        caption = copy.substring(handle.length()).trim();
                        break;
                    }
                    if (caption != null) break;
                }
            }
        }
        return new ScreenState(Platform.IG, Surface.IG_FEED_VIDEO, creator,
                ContentIdentity.signature(Platform.IG, null, creator, caption), Evidence.TARGET);
    }
}
