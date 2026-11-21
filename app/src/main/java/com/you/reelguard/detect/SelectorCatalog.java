package com.you.reelguard.detect;

import com.google.gson.Gson;
import com.you.reelguard.snapshot.NodeSnapshot;
import com.you.reelguard.snapshot.NodeSnapshot.Bounds;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SelectorCatalog {
    private int schemaVersion;
    private Map<String, PackageRules> packages;

    private static final class PackageRules {
        List<Profile> profiles;
        Profile fallback;
    }

    public static final class Profile {
        String name;
        long minVersionCode, maxVersionCode;
        List<String> locales;
        Map<String, List<Clause>> roles;

        private void prepare() {
            if (name == null || name.isEmpty() || roles == null) throw new IllegalArgumentException("Invalid profile");
            for (List<Clause> list : roles.values()) for (Clause clause : list) clause.prepare();
        }

        private boolean supports(String locale) {
            if (locales == null || locales.isEmpty()) return true;
            int separator = locale.indexOf('-');
            return locales.contains(locale) || (separator >= 0 && locales.contains(locale.substring(0, separator)));
        }

        public List<NodeSnapshot> find(String role, NodeSnapshot root, Bounds viewport, String locale) {
            List<Clause> clauses = roles.get(role);
            if (clauses == null) return Collections.emptyList();
            List<NodeSnapshot> matched = new ArrayList<>();
            for (NodeSnapshot node : root.visibleSubtree(viewport)) {
                for (Clause clause : clauses) {
                    if (clause.match(node, supports(locale))) {
                        matched.add(node);
                        break;
                    }
                }
            }
            return matched;
        }

        public String extract(String role, NodeSnapshot root, Bounds viewport, String locale) {
            List<Clause> clauses = roles.get(role);
            if (clauses == null) return null;
            Set<String> found = new HashSet<>();
            for (NodeSnapshot node : root.visibleSubtree(viewport)) {
                for (Clause clause : clauses) {
                    if (!clause.match(node, supports(locale))) continue;
                    String value = clause.value(node);
                    if (value != null && !value.trim().isEmpty()) found.add(value.trim());
                    break;
                }
            }
            return found.size() == 1 ? found.iterator().next() : null;
        }
    }

    private static final class Clause {
        String viewId, className, textPattern, descriptionPattern, source;
        Boolean selected, scrollable;
        int group;
        transient Pattern text, description;

        void prepare() {
            text = compile(textPattern);
            description = compile(descriptionPattern);
            if (viewId == null && className == null && text == null && description == null)
                throw new IllegalArgumentException("Selector needs identifying evidence");
            if (source != null && !source.equals("text") && !source.equals("description"))
                throw new IllegalArgumentException("Invalid extraction source");
            Pattern extraction = "description".equals(source) ? description : text;
            if (group < 0 || (group > 0 && (extraction == null || group > extraction.matcher("").groupCount())))
                throw new IllegalArgumentException("Invalid extraction group");
        }

        private static Pattern compile(String expression) {
            if (expression == null) return null;
            if (expression.length() > 256 || Pattern.compile("\\([^)]*[*+][^)]*\\)[*+]").matcher(expression).find())
                throw new IllegalArgumentException("Selector pattern exceeds complexity budget");
            return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }

        boolean match(NodeSnapshot node, boolean localeSupported) {
            return (viewId == null || viewId.equals(node.id)) &&
                    (className == null || className.equals(node.className)) &&
                    (selected == null || selected == node.selected) &&
                    (scrollable == null || scrollable == node.scrollable) &&
                    (localeSupported || (text == null && description == null)) &&
                    matches(text, node.text) && matches(description, node.description);
        }

        private boolean matches(Pattern pattern, String value) {
            return pattern == null || (value != null && pattern.matcher(value).find());
        }

        String value(NodeSnapshot node) {
            boolean useDescription = "description".equals(source);
            String value = useDescription ? node.description : node.text;
            Pattern pattern = useDescription ? description : text;
            if (pattern == null || value == null) return value;
            Matcher matcher = pattern.matcher(value);
            return matcher.find() ? matcher.group(group) : null;
        }
    }

    public static SelectorCatalog load(Reader reader) {
        SelectorCatalog catalog = new Gson().fromJson(reader, SelectorCatalog.class);
        if (catalog == null || catalog.schemaVersion != 1 || catalog.packages == null)
            throw new IllegalArgumentException("Unsupported selector schema");
        for (PackageRules rules : catalog.packages.values()) {
            if (rules == null) continue;
            if (rules.profiles != null) {
                long previous = -1;
                for (java.util.Iterator<Profile> it = rules.profiles.iterator(); it.hasNext();) {
                    Profile profile = it.next();
                    if (!valid(profile) || profile.minVersionCode <= previous ||
                            profile.minVersionCode > profile.maxVersionCode) it.remove();
                    else previous = profile.maxVersionCode;
                }
            }
            if (!valid(rules.fallback)) rules.fallback = null;
        }
        return catalog;
    }

    private static boolean valid(Profile profile) {
        if (profile == null) return false;
        try {
            profile.prepare();
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    public List<Profile> profilesFor(Platform platform, long version) {
        List<Profile> profiles = new ArrayList<>(2);
        PackageRules rules = packages.get(platform.packageName);
        if (rules != null && rules.profiles != null) {
            for (Profile profile : rules.profiles) {
                if (version >= profile.minVersionCode && version <= profile.maxVersionCode) {
                    profiles.add(profile);
                    break;
                }
            }
        }
        if (rules != null && rules.fallback != null) profiles.add(rules.fallback);
        return profiles;
    }
}
