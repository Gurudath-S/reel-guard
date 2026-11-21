package com.you.reelguard.service;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import com.you.reelguard.R;
import com.you.reelguard.data.Repository;

public final class LimitsTileService extends TileService {
    private final Runnable refresh = this::render;
    private Repository repository;

    @Override public void onCreate() {
        super.onCreate();
        repository = Repository.get(this);
    }

    @Override public void onStartListening() {
        super.onStartListening();
        repository.subscribeEnforcement(refresh);
        render();
    }

    @Override public void onStopListening() {
        repository.unsubscribeEnforcement(refresh);
        super.onStopListening();
    }

    @Override public void onDestroy() {
        repository.unsubscribeEnforcement(refresh);
        super.onDestroy();
    }

    @Override public void onClick() {
        super.onClick();
        repository.toggleEnforcement(success -> {
            if (!success) Toast.makeText(getApplicationContext(), R.string.enforcement_error, Toast.LENGTH_SHORT).show();
        });
    }

    private void render() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(repository.enforcementEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
