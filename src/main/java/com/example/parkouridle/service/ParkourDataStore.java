package com.example.parkouridle.service;

import com.example.parkouridle.math.BigNumber;
import com.example.parkouridle.model.PlayerProgress;
import com.example.parkouridle.model.Track;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ParkourDataStore {

    private static final Type TRACK_LIST_TYPE = new TypeToken<List<Track>>() {}.getType();
    private static final Type PLAYER_MAP_TYPE = new TypeToken<Map<UUID, PlayerProgress>>() {}.getType();

    private final Path tracksFile;
    private final Path playersFile;
    private final Gson gson;

    public ParkourDataStore(Path dataDir) {
        this.tracksFile = dataDir.resolve("tracks.json");
        this.playersFile = dataDir.resolve("players.json");
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(BigNumber.class, new BigNumberAdapter())
            .create();
    }

    public synchronized List<Track> loadTracks() {
        if (!Files.exists(tracksFile)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(tracksFile, StandardCharsets.UTF_8)) {
            List<Track> tracks = gson.fromJson(reader, TRACK_LIST_TYPE);
            return tracks == null ? new ArrayList<>() : tracks;
        } catch (IOException e) {
            throw new RuntimeException("Could not load tracks.json", e);
        }
    }

    public synchronized void saveTracks(Collection<Track> tracks) {
        ensureParent(tracksFile);
        try (Writer writer = Files.newBufferedWriter(tracksFile, StandardCharsets.UTF_8)) {
            gson.toJson(tracks, TRACK_LIST_TYPE, writer);
        } catch (IOException e) {
            throw new RuntimeException("Could not save tracks.json", e);
        }
    }

    public synchronized Map<UUID, PlayerProgress> loadPlayers() {
        if (!Files.exists(playersFile)) {
            return new HashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(playersFile, StandardCharsets.UTF_8)) {
            Map<UUID, PlayerProgress> players = gson.fromJson(reader, PLAYER_MAP_TYPE);
            return players == null ? new HashMap<>() : players;
        } catch (IOException e) {
            throw new RuntimeException("Could not load players.json", e);
        }
    }

    public synchronized void savePlayers(Map<UUID, PlayerProgress> players) {
        ensureParent(playersFile);
        try (Writer writer = Files.newBufferedWriter(playersFile, StandardCharsets.UTF_8)) {
            gson.toJson(players, PLAYER_MAP_TYPE, writer);
        } catch (IOException e) {
            throw new RuntimeException("Could not save players.json", e);
        }
    }

    private static void ensureParent(Path file) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Could not create data directory", e);
        }
    }

    private static final class BigNumberAdapter implements JsonSerializer<BigNumber>, JsonDeserializer<BigNumber> {
        @Override
        public JsonElement serialize(BigNumber src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.add("mag", new JsonPrimitive(src.mag()));
            obj.add("layer", new JsonPrimitive(src.layer()));
            return obj;
        }

        @Override
        public BigNumber deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            double mag = obj.get("mag").getAsDouble();
            long layer = obj.get("layer").getAsLong();
            return BigNumber.ofLayer(mag, layer);
        }
    }
}
