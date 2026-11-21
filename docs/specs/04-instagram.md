# InstagramDetector

**Input and output.** Consume a COMPLETE frame for com.instagram.android, one selected profile, and the shared creator/signature operations. Emit exactly one IG_REELS_TAB, IG_FEED_VIDEO or OTHER per frame. Implement in plain Java using the catalog matcher and snapshot geometry.

| Method | Procedure |
|---|---|
| detect(frame, profile) | Check recognized foreground exclusions (DM, profile, search or covering modal) first. Test the tab/player rule, then the home-feed rule. If positive contexts conflict, return UNKNOWN. Return KNOWN_OTHER for a verified ordinary context and UNKNOWN for insufficient evidence. |
| matchReelsTab(root) | Require the visible reelsPager and reelsTabSelected roles (tab selected on the node or its fixture-verified selected ancestor). Bind one current item inside the pager using a current-item marker or dominant geometry. A nav icon alone is common on other screens. |
| dominantItem(items, viewport) | Use items from the evidenced pager or homeFeedList only. Exclude hidden and zero-area items. Rank by intersection area with the container clipped to the active window, including photo rows in the home-feed ranking. Require at least half of the smaller of item height and viewport height visible. Equal leading area or transition ambiguity yields UNKNOWN. |
| matchFeedVideo(root) | Require homeFeedList, select its dominant feedRow, then require a videoMarker or reelDescription in that row. Use positive photo markers for a known photo. An unrecognized dominant row is UNKNOWN. A small video above a dominant photo, preload outside the viewport or Reel label elsewhere cannot qualify. |
| readItem(item, surface) | Extract creatorHandle and caption (or verified stable item ID) inside the chosen item only. Pass to [identity](06-identity.md). Emit the surface even if identity is incomplete. Use the enclosing current item when player chrome and caption are siblings. |

**Fallback discovery.** If the app exposes a sparse player, capture selected tab, scrollable pager and item-scoped creator evidence together. A fallback can use independently verified equivalent signals for the pager and selected tab roles. A scroll event schedules inspection. The event by itself cannot identify a reel. Feed detection stays best effort when item boundaries or media type are unavailable.

**Examples to capture.** Start in the Reels tab, swipe to a second reel, then open the creator's profile. Expect two tab candidates then OTHER. Return home to a video between two photos. Expect only the dominant verified video to produce a feed candidate. Open a DM while already over quota. The DM fixture must produce OTHER with no navigation action. Include a feed reel opened full-screen outside the selected Reels tab as an ambiguous case unless discovery establishes a planned surface.

**Acceptance.** For each fixture assert surface, evidence class, chosen item, canonical creator, identity relationship and matched roles. Include mixed visible rows, neighbor author contamination, tab icon present/unselected, caption changes from player controls, missing creator, modal and a broken profile. Keep feed and tab accounting distinct even if the same post appears in both.
