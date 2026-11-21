package com.you.reelguard.data;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class WhitelistStore {
    private static final String MEMBERS = "whitelist.v1";
    private static final String RECENT = "recentCreators.v1";
    private final Prefs prefs;
    private final Gson gson = new Gson();

    WhitelistStore(Prefs prefs) { this.prefs = prefs; }

    Set<String> members() { return Collections.unmodifiableSet(prefs.set(MEMBERS)); }

    void setMember(String key, boolean member) {
        Set<String> values = prefs.set(MEMBERS);
        if (member ? values.add(key) : values.remove(key)) prefs.saveSet(MEMBERS, values);
    }

    List<String> recent() {
        String[] values = gson.fromJson(prefs.text(RECENT), String[].class);
        return values == null ? Collections.emptyList() : Arrays.asList(values);
    }

    void remember(String key) {
        if (key == null) return;
        List<String> next = new ArrayList<>(recent());
        if (!next.isEmpty() && key.equals(next.get(0))) return;
        next.remove(key);
        next.add(0, key);
        prefs.put(RECENT, gson.toJson(next.subList(0, Math.min(20, next.size()))));
    }
}
