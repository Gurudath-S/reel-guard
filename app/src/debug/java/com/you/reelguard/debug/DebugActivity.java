package com.you.reelguard.debug;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.you.reelguard.detect.Platform;
import com.you.reelguard.service.ReelGuardService;

public final class DebugActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        status = new TextView(this);
        layout.addView(status);
        for (Platform platform : Platform.values()) {
            Button action = new Button(this);
            action.setText("Capture next " + platform.label + " screen");
            action.setOnClickListener(view -> {
                if (ReelGuardService.isConnected()) DebugTools.arm(platform);
                render();
            });
            layout.addView(action);
        }
        setContentView(layout);
    }

    @Override protected void onResume() { super.onResume(); render(); }

    private void render() {
        status.setText(ReelGuardService.isConnected() ? DebugTools.status() :
                "Enable the ReelGuard accessibility service before capturing.");
    }
}
