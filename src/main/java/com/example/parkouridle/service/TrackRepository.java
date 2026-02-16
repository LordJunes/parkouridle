package com.example.parkouridle.service;

import com.example.parkouridle.model.Track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrackRepository {

    private final ParkourDataStore dataStore;
    private final Map<Integer, Track> tracks = new LinkedHashMap<>();
    private int nextTrackId = 1;

    public TrackRepository(ParkourDataStore dataStore) {
        this.dataStore = dataStore;
        for (Track track : dataStore.loadTracks()) {
            tracks.put(track.id, track);
            nextTrackId = Math.max(nextTrackId, track.id + 1);
        }
    }

    public synchronized int nextId() {
        return nextTrackId++;
    }

    public synchronized void addTrack(Track track) {
        tracks.put(track.id, track);
        save();
    }

    public synchronized Track getTrack(int id) {
        return tracks.get(id);
    }

    public synchronized Collection<Track> allTracks() {
        return new ArrayList<>(tracks.values());
    }

    public synchronized List<Integer> allTrackIds() {
        List<Integer> ids = new ArrayList<>(tracks.keySet());
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    public synchronized void save() {
        dataStore.saveTracks(tracks.values());
    }
}
