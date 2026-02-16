package com.example.parkouridle.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Track {
    public int id;
    public TrackVector3 start;
    public List<TrackVector3> checkpoints = new ArrayList<>();
    public TrackVector3 finish;
    public Map<UUID, Long> leaderboard = new LinkedHashMap<>();

    public Track() {
    }

    public Track(int id, TrackVector3 start, List<TrackVector3> checkpoints, TrackVector3 finish) {
        this.id = id;
        this.start = start;
        this.checkpoints = checkpoints;
        this.finish = finish;
    }
}
