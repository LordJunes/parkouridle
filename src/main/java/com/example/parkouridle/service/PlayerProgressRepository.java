package com.example.parkouridle.service;

import com.example.parkouridle.model.PlayerProgress;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProgressRepository {

    private final ParkourDataStore dataStore;
    private final Map<UUID, PlayerProgress> players;

    public PlayerProgressRepository(ParkourDataStore dataStore) {
        this.dataStore = dataStore;
        this.players = new HashMap<>(dataStore.loadPlayers());
    }

    public synchronized PlayerProgress getOrCreate(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerProgress::new);
    }

    public synchronized void save() {
        dataStore.savePlayers(players);
    }
}
