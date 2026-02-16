package com.example.parkouridle;

import com.example.parkouridle.command.ParkourRootCommand;
import com.example.parkouridle.service.ParkourDataStore;
import com.example.parkouridle.service.ParkourManager;
import com.example.parkouridle.service.PlayerProgressRepository;
import com.example.parkouridle.service.TrackRepository;
import com.example.parkouridle.service.UpgradeService;
import com.example.parkouridle.ui.ParkourUiService;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * ParkourIdle - A Hytale server plugin.
 *
 * @author Minecraft_CEO
 * @version 1.0.0
 */
public class ParkourIdlePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static ParkourIdlePlugin instance;

    private ParkourManager parkourManager;

    public ParkourIdlePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static ParkourIdlePlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[ParkourIdle] Setting up...");

        ParkourDataStore dataStore = new ParkourDataStore(getDataDirectory());
        TrackRepository trackRepository = new TrackRepository(dataStore);
        PlayerProgressRepository progressRepository = new PlayerProgressRepository(dataStore);
        UpgradeService upgradeService = new UpgradeService();

        this.parkourManager = new ParkourManager(trackRepository, progressRepository, upgradeService);
        ParkourUiService uiService = new ParkourUiService(parkourManager);

        getCommandRegistry().registerCommand(new ParkourRootCommand(parkourManager, uiService));

        getEventRegistry().registerGlobal(PlayerInteractEvent.class, parkourManager::handlePlayerInteract);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, parkourManager::handlePlayerDisconnect);

        LOGGER.at(Level.INFO).log("[ParkourIdle] Setup complete!");
    }

    @Override
    protected void start() {
        if (parkourManager != null) {
            parkourManager.start();
        }
        LOGGER.at(Level.INFO).log("[ParkourIdle] Started!");
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[ParkourIdle] Shutting down...");
        if (parkourManager != null) {
            parkourManager.stop();
            parkourManager = null;
        }
        instance = null;
    }
}
