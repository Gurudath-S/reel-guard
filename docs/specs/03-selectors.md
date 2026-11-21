# Versioned selector catalog and detection contracts

**Owner.** The detect package owns a pure Java SelectorCatalog, matcher and ScreenDetector interface. An Android adapter obtains package longVersionCode through PackageManager and asset bytes through AssetManager. Load the catalog once per service connection. Refresh version selection after package replacement or the next connection. Use Gson, java.util.regex.Pattern and immutable collections.

## Asset schema for selectors.json

| Field | Shape and validation |
|---|---|
| schemaVersion, packages | Integer 1 and an object keyed by the two exact Android package names. A missing package disables detection for that platform and sets a diagnostics reason. |
| profiles | Per package, an array of named profiles with inclusive minVersionCode and maxVersionCode (longs), supported locale tags for language-dependent patterns, and roles. Start with the tested single version. Ranges must be ordered and disjoint. Expand only after regression evidence. |
| fallback | One package-specific profile containing roles with independently verified generic evidence, including localized patterns where tested. Use on unknown/missing versions or as a second complete rule set when the version profile is inconclusive. |
| roles | Object keyed by the semantic names in the two detector sheets. Each role contains ordered alternative clauses. Within a clause all specified constraints must hold on one node. Alternatives are tried in asset order. Scoping, parent/child relationships and precedence belong to the detector. |
| clause | Optional exact qualified viewId and className, optional selected/scrollable booleans, optional textPattern or descriptionPattern. Require at least one identifying constraint. Pattern matching uses a precompiled regex search on the bounded field. Extraction clauses declare a capture group (or group zero for a full-field value) and source field. Reject invalid group references. |
| profile evidence | Each profile identifies its fixture filenames and tested locale(s) in the discovery notes or test manifest. Keep changing IDs, labels and language-specific string handling in the asset. |

## Methods and result types

| Operation | Contract |
|---|---|
| SelectorCatalog.load(reader) | Validate package, version bounds, unique names, allowed roles and clause fields. Compile patterns once, reject unbounded nested repetition. Isolate a malformed profile and report its path. Unsupported schema disables the catalog. |
| profilesFor(package, versionCode, locale) | Return the exact range then package fallback, skipping unavailable profiles. Use language-neutral ID clauses for any locale and only applicable localized patterns (exact tag, then language). Never combine half a positive rule from each profile. |
| find(role, scope, viewport) / extract(role, scope, viewport) | Return visible matching nodes in document order, or a unique extracted value. Multiple different plausible authors inside the selected item yield unresolved identity. Return the role names used for diagnostics. |
| ScreenDetector.detect(frame, profile) → DetectionResult | Return ScreenState with platform, surface, nullable creatorId and nullable contentSignature. Add evidence class TARGET, KNOWN_OTHER, UNKNOWN or UNAVAILABLE, profile name and matched role names. Surface OTHER covers the last three evidence classes. Only TARGET can start usage. |
| DetectorRouter.classify(frame) | Dispatch by verified root package. Accept a complete profile's TARGET or KNOWN_OTHER. Try fallback for UNKNOWN. If still unknown, allow navigation and submit a health sample. UNAVAILABLE, ambiguous ties and incomplete frames have distinct diagnostic reasons. |

**Use case.** An app update renames a pager. Verify a fallback against a positive fixture and the nearest confusing negative. If the remaining evidence also matches a home preview, return UNKNOWN and collect new fixtures. A version mismatch alone does not establish a broken selector.

**Acceptance.** Exercise inclusive range endpoints, gaps, unknown version, wrong package, missing role, malformed pattern, locale mismatch, fallback success and invalid asset schema. A localized label outside the active item never supplies that item's identity.
