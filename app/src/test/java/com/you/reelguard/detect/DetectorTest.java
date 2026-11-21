package com.you.reelguard.detect;

import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.Reader;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DetectorTest {
    private final Bounds frame = new Bounds(0, 0, 400, 800);
    private DetectorRouter router;

    @Before public void loadAsset() throws Exception {
        Path file = Paths.get("src/main/assets/selectors.json");
        if (!Files.exists(file)) file = Paths.get("app").resolve(file);
        try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            router = new DetectorRouter(SelectorCatalog.load(input));
        }
    }

    @Test public void reelsSelectionAndItemScope() {
        NodeSnapshot current = ig("clips_video_container", frame, null, null, false,
                ig("clips_author_username", frame, "@Sample", null, false),
                ig("clips_caption", frame, "A reel", null, false));
        NodeSnapshot pager = ig("clips_viewer_view_pager", frame, null, null, false, current);
        ScreenState selected = classify(Platform.IG, ig("root", frame, null, null, false, pager,
                ig("clips_tab", frame, null, "Reels", true),
                ig("clips_author_username", frame, "@Other", null, false)));
        assertEquals(Surface.IG_REELS_TAB, selected.surface);
        assertEquals("ig:sample", selected.creator);
        assertNotNull(selected.signature);
        assertEquals(Surface.OTHER, classify(Platform.IG, pager).surface);
    }

    @Test public void feedChecksDominantRow() {
        NodeSnapshot video = ig("row_feed_media_container", new Bounds(0, 550, 400, 1100), null, null, false,
                ig("row_feed_video_media", new Bounds(0, 550, 400, 1100), null, "Reel by author", false));
        NodeSnapshot photo = ig("row_feed_photo", new Bounds(0, 0, 400, 550), null, null, false);
        NodeSnapshot list = new NodeSnapshot("com.instagram.android:id/refreshable_recycler_view", null,
                null, null, false, true, true, frame, Arrays.asList(photo, video));
        NodeSnapshot homeTab = ig("feed_tab", frame, null, "Home", true);
        assertEquals(ScreenState.Evidence.KNOWN_OTHER,
                classify(Platform.IG, ig("root", frame, null, null, false, homeTab, list)).evidence);
        NodeSnapshot visible = ig("row_feed_media_container", frame, null, null, false,
                ig("row_feed_video_media", frame, null, "Reel by author", false));
        NodeSnapshot feed = new NodeSnapshot("com.instagram.android:id/refreshable_recycler_view", null,
                null, null, false, true, true, frame, Arrays.asList(visible));
        assertEquals(Surface.OTHER, classify(Platform.IG, feed).surface);
        assertEquals(Surface.IG_FEED_VIDEO,
                classify(Platform.IG, ig("root", frame, null, null, false, homeTab, feed)).surface);
    }

    @Test public void shortsRequiresPlayerAndRejectsLongWatch() {
        NodeSnapshot handle = node(Platform.YT, "handle", frame, "@Example", null, false);
        NodeSnapshot title = node(Platform.YT, "reel_title", frame, "Example title", null, false);
        NodeSnapshot video = node(Platform.YT, "reel_watch_player", frame, null, null, false);
        NodeSnapshot item = node(Platform.YT, "reel_player_page_container", frame, null, null, false, video, handle, title);
        NodeSnapshot shorts = node(Platform.YT, "reel_watch_fragment_container", frame, null, null, false, item);
        assertEquals(Surface.OTHER, classify(Platform.YT, item).surface);
        ScreenState state = classify(Platform.YT, shorts);
        assertEquals(Surface.YT_SHORTS, state.surface);
        assertEquals("yt:@example", state.creator);
        assertNotNull(state.signature);
        NodeSnapshot normal = node(Platform.YT, "watch_player", frame, null, null, false, shorts);
        assertEquals(ScreenState.Evidence.KNOWN_OTHER, classify(Platform.YT, normal).evidence);
    }

    @Test public void classifiesCapturedYouTubeVersion() throws Exception {
        ScreenState shorts = classifyFixture(Platform.YT, "yt-21.45.82-shorts.json");
        assertEquals(Surface.YT_SHORTS, shorts.surface);
        assertEquals("yt:@sample_4", shorts.creator);
        assertNotNull(shorts.signature);
        ScreenState home = classifyFixture(Platform.YT, "yt-21.45.82-home.json");
        assertEquals(Surface.OTHER, home.surface);
        assertEquals(ScreenState.Evidence.KNOWN_OTHER, home.evidence);
    }

    @Test public void classifiesCapturedInstagramHomeFeedVideo() throws Exception {
        ScreenState video = classifyFixture(Platform.IG, "ig-456.1.0.56.78-home-feed-video.json");
        assertEquals(Surface.IG_FEED_VIDEO, video.surface);
        assertEquals("ig:sample_14", video.creator);
        assertNotNull(video.signature);
    }
    @Test public void resolvesCapturedInstagramReelIdentity() throws Exception {
        ScreenState reel = classifyFixture(Platform.IG, "ig-456.1.0.56.78-reels.json");
        assertEquals(Surface.IG_REELS_TAB, reel.surface);
        assertTrue(reel.creator.startsWith("ig:sample_"));
        assertNotNull(reel.signature);
    }
    private ScreenState classifyFixture(Platform platform, String name) throws Exception {
        try (java.io.InputStream stream = getClass().getResourceAsStream("/fixtures/" + name);
             Reader reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonObject json = gson.fromJson(reader, com.google.gson.JsonObject.class);
            return router.classify(platform, json.get("versionCode").getAsLong(),
                    gson.fromJson(json.get("root"), NodeSnapshot.class),
                    gson.fromJson(json.get("windowBounds"), Bounds.class),
                    json.get("locale").getAsString());
        }
    }
    @Test public void missingRootSuspends() {
        assertEquals(ScreenState.Evidence.UNAVAILABLE, classify(Platform.IG, null).evidence);
    }

    private ScreenState classify(Platform platform, NodeSnapshot tree) {
        return router.classify(platform, -1, tree, frame, "en-US");
    }

    private NodeSnapshot ig(String id, Bounds bounds, String text, String desc,
                            boolean selected, NodeSnapshot... children) {
        return node(Platform.IG, id, bounds, text, desc, selected, children);
    }

    private NodeSnapshot node(Platform platform, String id, Bounds bounds, String text, String desc,
                              boolean selected, NodeSnapshot... children) {
        return new NodeSnapshot(platform.packageName + ":id/" + id, null,
                text, desc, selected, false, true, bounds, Arrays.asList(children));
    }
}
