package com.you.reelguard.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.you.reelguard.R;
import com.you.reelguard.data.Repository;
import com.you.reelguard.detect.ContentIdentity;
import com.you.reelguard.detect.Platform;
import java.util.ArrayList;
import java.util.Collections;

public final class WhitelistActivity extends Activity {
    private Repository repository;
    private Repository.Dashboard dashboard;
    private long request;
    private boolean started;
    private final Runnable refresh = this::load;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.whitelist_activity);
        repository = Repository.get(this);
        Spinner platform = findViewById(R.id.platform);
        ArrayAdapter<String> options = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{Platform.IG.label, Platform.YT.label});
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        platform.setAdapter(options);
        findViewById(R.id.add).setOnClickListener(v -> {
            EditText field = findViewById(R.id.handle);
            Platform app = Platform.values()[platform.getSelectedItemPosition()];
            String key = ContentIdentity.creatorKey(app, field.getText().toString());
            if (key == null) field.setError("Enter a " + app.label + " handle");
            else edit(key, true, field);
        });
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        repository.subscribe(refresh);
        load();
    }

    @Override protected void onStop() {
        started = false;
        repository.unsubscribe(refresh);
        super.onStop();
    }

    private void load() {
        long token = ++request;
        repository.read(data -> {
            if (!started || token != request) return;
            dashboard = data;
            LinearLayout members = findViewById(R.id.members);
            LinearLayout recent = findViewById(R.id.recent);
            members.removeAllViews();
            recent.removeAllViews();
            ArrayList<String> saved = new ArrayList<>(data.whitelist);
            Collections.sort(saved);
            for (String key : saved) row(members, "Remove " + key, true, () -> edit(key, false, null));
            for (String key : data.recent) {
                boolean member = data.whitelist.contains(key);
                row(recent, (member ? "Added " : "Add ") + key, !member, () -> edit(key, true, null));
            }
            if (saved.isEmpty()) empty(members, "No saved creators.");
            if (data.recent.isEmpty()) empty(recent, "Creators appear here as ReelGuard recognizes them.");
        }, error -> {
            if (started && token == request) status(error);
        });
    }

    private void edit(String key, boolean add, EditText clear) {
        if (add && dashboard != null && dashboard.whitelist.contains(key)) {
            status("Already added.");
            return;
        }
        repository.setMember(key, add, saved -> {
            if (!started) return;
            status(saved ? (add ? "Creator added." : "Creator removed.") : "Could not save whitelist.");
            if (saved && clear != null) clear.setText("");
        });
    }

    private void row(LinearLayout list, String label, boolean enabled, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setEnabled(enabled);
        button.setOnClickListener(v -> action.run());
        list.addView(button);
    }

    private void empty(LinearLayout list, String message) {
        TextView label = new TextView(this);
        label.setText(message);
        list.addView(label);
    }

    private void status(String message) {
        TextView label = findViewById(R.id.whitelist_status);
        label.setText(message);
    }
}
