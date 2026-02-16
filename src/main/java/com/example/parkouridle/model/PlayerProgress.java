package com.example.parkouridle.model;

import com.example.parkouridle.math.BigNumber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProgress {
    public UUID uuid;
    public BigNumber points = BigNumber.ZERO;
    public int currentVp;
    public int option1;
    public int option2;
    public int option3;
    public int option4;
    public int option5;
    public int option6;
    public Map<Integer, Long> personalBestByTrack = new HashMap<>();

    public PlayerProgress() {
    }

    public PlayerProgress(UUID uuid) {
        this.uuid = uuid;
    }
}
