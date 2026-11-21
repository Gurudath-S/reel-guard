# Discovery, NodeSnapshot and SnapshotBuilder

**Consumer contract.** The Android boundary returns an immutable Java tree, its coordinate frame and a capture status. Detectors and fixture tests consume exactly this representation. Never retain a live accessibility object in a detector, timer, Activity or cache. [AccessibilityNodeInfo fields and lifecycle](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)

## Discovery on the target phone

1. Record package name, long version code and version name using package information or ADB dumpsys package. Record device API, locale, orientation and window size. Open each screen in [the fixture inventory](15-verification.md). Wait for the content to settle.
2. Run `adb shell uiautomator dump /sdcard/rg_capture.xml` and `adb pull /sdcard/rg_capture.xml` in a private working directory. Pull each capture before reusing the name. Capture the same item during playback, a different item by the same author, and a scroll transition. Compare rows, selected tabs, bounds and changing text. Verify candidates against live service captures when the skeleton exists.
3. Convert the hierarchy using a host-side fixture importer based on JDK XML parsing and Gson. Disable external entities and DTD processing. Map resource-id, content-desc, text, class, selected, scrollable, bounds, package and ordered children. Use captured visibility if available. For XML without that attribute, mark visibility as inferred from non-empty intersecting bounds and document that inference. Compare to a live capture before promoting a rule.
4. Replace real handles and captions with consistent synthetic values and remove unrelated private text. Preserve selector-relevant label structure, language, hierarchy, coordinates, ID and state. Review each fixture and its expected output. Record which pairs must share or differ in signature. Store only sanitized fixtures in test resources.

## Data model and operations

| Contract | Required behavior |
|---|---|
| SnapshotFrame | schemaVersion 1, packageName, windowId, windowBounds in screen pixels, versionCode if known, locale tag, capture status, optional root, captured wall time and elapsed time. Host fixtures use deterministic times and include expected outcomes separately from detector input. |
| NodeSnapshot | Nullable resource ID, class, text and content description. Include selected, scrollable, visible, bounds in screen coordinates and ordered children. An empty string becomes absent. Use plain immutable rectangles and defensive copies. Ignore editable/password field contents. |
| SnapshotBuilder.capture(activeWindow, stamp) → SnapshotFrame | Verify the focused target application and readable root. Obtain each node's values once, copy strings (cap each at 512 Unicode code points), intersect geometry during matching. Finish on the capture worker. Recycle owned nodes/windows on API 29–32 in cleanup paths. API 33+ discontinued pooling. |
| copyTree(root, budget) → tree and completeness | Iteratively walk children in document order. Initial limits are depth 32 and 600 visited nodes. Stop at the limits and mark TRUNCATED. A stale child/read failure marks the capture incomplete. A missing root returns UNAVAILABLE. Only COMPLETE captures may drive classification or charging in the baseline. |
| visibleArea(node, viewport), descendants(scope) | Calculate nonnegative rectangle intersection area with wide integers. Expose only nodes in the nominated subtree. Use visibility and bounds together. Cache paths or indices only inside a single immutable frame. |
| FixtureReader.read(input) → frame and expectations | Validate schema, geometry, required fields and documented inference. Report fixture path and field on error. The reader uses Java and Gson on the host. |

**Acceptance.** Cover an empty root, invalid bounds, null child, very deep/wide trees and a recycled/stale node. A missing or incomplete capture suspends tracking and cannot cause a block. A full fixture round-trip preserves selected states, child order, Unicode handles and window offsets. Inspect truncation rates on real target screens before setting the final traversal budget.
