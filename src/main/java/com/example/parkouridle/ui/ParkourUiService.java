package com.example.parkouridle.ui;

import com.example.parkouridle.service.ParkourManager;
import com.example.parkouridle.ui.page.ParkourMenuPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ParkourUiService {

    private final ParkourManager parkourManager;

    public ParkourUiService(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    public void openMainMenu(Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerMeta) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        player.getPageManager().openCustomPage(playerRef, store, new ParkourMenuPage(playerMeta, parkourManager));
    }
}
