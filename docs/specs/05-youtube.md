# YouTubeDetector

**Input and output.** Consume a COMPLETE frame for com.google.android.youtube and a resolved selector profile. Return YT_SHORTS for an active Shorts player. YouTube home, Shorts shelves, results, channels and ordinary watch pages must stay usable at every quota level. Dependencies are the shared Java matcher, geometry helpers and [identity module](06-identity.md).

| Method | Procedure |
|---|---|
| detect(frame, profile) | Exclude covering modals and verified ordinary watch contexts. Look for shortsPlayer plus a fixture-confirmed current Short item or equivalent in-player evidence. Recognize ordinary contexts using homeShelf, searchResults, channelPage or longWatchPage roles. Return UNKNOWN for conflicting player contexts or insufficient evidence. |
| findActiveShort(player, viewport) | Resolve currentShort by selected/current role if exposed. Otherwise select one dominant visible item inside the pager with the geometry and tie rules from [the Instagram sheet](04-instagram.md). Bind the active item and skip preloaded siblings. |
| extractChannel(item) | Read a channelHandle text/description extraction from the item or its fixture-verified associated controls. Normalize to the YouTube key. A display name without evidence of a handle is unresolved. |
| describeShort(item, channel) | Use stableShortId if proven available or the normalized channel and caption/title prefix. Exclude subscriber count, playback time and button state. Hand unresolved identity to the counter as absent. |

**Selector discovery.** Inspect candidate reel-related resource IDs in both player and home shelf dumps. Record exact, version-tested values. A Shorts navigation label, thumbnail, vertical aspect ratio or ID family shared with previews is insufficient. Verify entering from the home shelf and from the Shorts tab. The active player can be recognized independently of its launch path.

**Acceptance.** Two different Shorts with the same author and different titles produce different signatures. Repeated samples of one Short preserve identity. A handle visible only on an adjacent item cannot exempt the current Short. The same title on a home card returns OTHER. A recognized player with delayed channel text remains a target with unresolved author until the next verified capture. Test both unknown selectors and generic fallback against the long-video negative fixture.
