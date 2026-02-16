package com.example.parkouridle.service;

import com.example.parkouridle.math.BigNumber;
import com.example.parkouridle.model.PlayerProgress;
import com.example.parkouridle.model.Track;
import com.example.parkouridle.model.TrackVector3;
import com.example.parkouridle.util.BigNumberFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ParkourManager {

    private static final String GREEN_WOOL_ITEM = "hytale:green_wool";
    private static final String BLUE_WOOL_ITEM = "hytale:blue_wool";
    private static final String RED_WOOL_ITEM = "hytale:red_wool";

    private static final String RESET_ITEM = "hytale:green_wool";
    private static final String CHECKPOINT_ITEM = "hytale:blue_wool";
    private static final String QUIT_ITEM = "hytale:red_wool";

    private final TrackRepository trackRepository;
    private final PlayerProgressRepository progressRepository;
    private final UpgradeService upgradeService;

    private final Map<UUID, AdminSession> adminSessions = new HashMap<>();
    private final Map<UUID, ActiveRun> activeRuns = new HashMap<>();
    private final Map<UUID, ScheduledFuture<?>> idleTasks = new HashMap<>();

    private ScheduledFuture<?> scanTask;

    public ParkourManager(
        TrackRepository trackRepository,
        PlayerProgressRepository progressRepository,
        UpgradeService upgradeService
    ) {
        this.trackRepository = trackRepository;
        this.progressRepository = progressRepository;
        this.upgradeService = upgradeService;
    }

    public void start() {
        if (scanTask != null) {
            return;
        }

        scanTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
            this::tickPlayers,
            1,
            200,
            TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel(false);
            scanTask = null;
        }
        for (ScheduledFuture<?> task : idleTasks.values()) {
            task.cancel(false);
        }
        idleTasks.clear();
        progressRepository.save();
        trackRepository.save();
    }

    public void beginAdminRegistration(Player player) {
        adminSessions.put(player.getUuid(), new AdminSession());
        giveAdminSetupItems(player);
        send(player, "Admin setup: place Green Wool for Start.");
    }

    public boolean isAdminSession(UUID uuid) {
        return adminSessions.containsKey(uuid);
    }

    public void handlePlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        String itemId = normalizeItemId(event.getItemInHand() == null ? null : event.getItemInHand().getItemId());

        if (adminSessions.containsKey(player.getUuid()) && event.getTargetBlock() != null) {
            handleAdminPlacement(player, event.getTargetBlock(), itemId);
            return;
        }

        ActiveRun run = activeRuns.get(player.getUuid());
        if (run == null) {
            return;
        }

        if (isLike(itemId, RESET_ITEM)) {
            teleportPlayer(player, run.track.start);
            send(player, "Reset to start.");
            return;
        }

        if (isLike(itemId, CHECKPOINT_ITEM) && run.lastCheckpoint != null) {
            teleportPlayer(player, run.lastCheckpoint);
            send(player, "Teleported to checkpoint.");
            return;
        }

        if (isLike(itemId, QUIT_ITEM)) {
            endRun(player, false, 0L);
            send(player, "Run cancelled.");
        }
    }

    public void handlePlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef ref = event.getPlayerRef();
        UUID uuid = ref.getUuid();
        activeRuns.remove(uuid);
        adminSessions.remove(uuid);
    }

    public Track getTrack(int id) {
        return trackRepository.getTrack(id);
    }

    public List<Integer> listTrackIds() {
        return trackRepository.allTrackIds();
    }

    public List<Map.Entry<UUID, Long>> getTopLeaderboard(int id, int limit) {
        Track track = trackRepository.getTrack(id);
        if (track == null) {
            return List.of();
        }

        return track.leaderboard.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(limit)
            .toList();
    }

    public PlayerProgress getProgress(UUID uuid) {
        return progressRepository.getOrCreate(uuid);
    }

    public UpgradeService getUpgradeService() {
        return upgradeService;
    }

    public void buyUpgrade(Player player, int option) {
        PlayerProgress progress = progressRepository.getOrCreate(player.getUuid());
        int level = getLevel(progress, option);
        BigNumber cost = upgradeService.costForOption(option, level);

        if (progress.points.compareTo(cost) < 0) {
            send(player, "Not enough Punkte. Need: " + BigNumberFormatter.formatBigNumber(cost));
            return;
        }

        progress.points = progress.points.subtract(cost);
        incrementLevel(progress, option);
        progressRepository.save();

        send(player, "Upgrade " + option + " purchased. New level: " + getLevel(progress, option));
    }

    public void buyVoidPoint(Player player) {
        PlayerProgress progress = progressRepository.getOrCreate(player.getUuid());
        BigNumber cost = upgradeService.purchaseVoidPointCost(progress.currentVp);

        if (progress.points.compareTo(cost) < 0) {
            send(player, "Not enough Punkte for VP. Need: " + BigNumberFormatter.formatBigNumber(cost));
            return;
        }

        progress.points = progress.points.subtract(cost);
        progress.currentVp++;
        progressRepository.save();

        send(player, "Purchased 1 Void Point. Total VP: " + progress.currentVp);
    }

    public void teleportToTrackStart(Player player, int id) {
        Track track = trackRepository.getTrack(id);
        if (track == null) {
            send(player, "Track not found: " + id);
            return;
        }
        teleportPlayer(player, track.start);
    }

    private void handleAdminPlacement(Player player, Vector3i target, String itemId) {
        AdminSession session = adminSessions.get(player.getUuid());
        if (session == null) {
            return;
        }

        TrackVector3 placed = TrackVector3.fromVector3i(target);

        switch (session.stage) {
            case WAIT_START -> {
                if (!isLike(itemId, GREEN_WOOL_ITEM)) {
                    send(player, "Place Green Wool for Start.");
                    return;
                }
                session.start = placed;
                session.stage = AdminStage.WAIT_CHECKPOINT_OR_FINISH;
                send(player, "Start saved. Place Blue Wool checkpoints, then Red Wool finish.");
            }
            case WAIT_CHECKPOINT_OR_FINISH -> {
                if (isLike(itemId, BLUE_WOOL_ITEM)) {
                    session.checkpoints.add(placed);
                    send(player, "Checkpoint #" + session.checkpoints.size() + " saved.");
                    return;
                }
                if (!isLike(itemId, RED_WOOL_ITEM)) {
                    send(player, "Place Blue Wool for checkpoint or Red Wool for finish.");
                    return;
                }

                if (session.start == null) {
                    send(player, "Missing start block.");
                    return;
                }

                Track track = new Track(trackRepository.nextId(), session.start, new ArrayList<>(session.checkpoints), placed);
                trackRepository.addTrack(track);
                adminSessions.remove(player.getUuid());
                send(player, "Track saved as ID " + track.id + ".");
            }
        }
    }

    private void tickPlayers() {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return;
            }

            for (World world : universe.getWorlds().values()) {
                for (Player player : world.getPlayers()) {
                    tickPlayer(player);
                }
            }
        } catch (Exception ignored) {
            // Keep scheduler alive if one player/world fails this cycle.
        }
    }

    private void tickPlayer(Player player) {
        TransformComponent transform = player.getTransformComponent();
        if (transform == null) {
            return;
        }

        Vector3d pos = transform.getPosition();
        int bx = (int) Math.floor(pos.getX());
        int by = (int) Math.floor(pos.getY()) - 1;
        int bz = (int) Math.floor(pos.getZ());
        TrackVector3 block = new TrackVector3(bx, by, bz);

        ActiveRun run = activeRuns.get(player.getUuid());

        if (run == null) {
            Track startTrack = findTrackAtStart(block);
            if (startTrack != null) {
                startRun(player, startTrack);
            }
            return;
        }

        ensureRunItems(player);

        if (isTrackCheckpoint(run.track, block)) {
            run.lastCheckpoint = block;
        }

        if (Objects.equals(run.track.finish, block)) {
            long elapsed = System.currentTimeMillis() - run.startedAtMillis;
            endRun(player, true, elapsed);
        }
    }

    private void startRun(Player player, Track track) {
        ActiveRun run = new ActiveRun(track, System.currentTimeMillis());
        run.lastCheckpoint = track.start;
        activeRuns.put(player.getUuid(), run);

        ensureRunItems(player);
        send(player, "Parkour mode started on Track " + track.id + ".");
    }

    private void endRun(Player player, boolean finished, long elapsedMillis) {
        ActiveRun run = activeRuns.remove(player.getUuid());
        clearRunItems(player);

        if (!finished || run == null) {
            return;
        }

        PlayerProgress progress = progressRepository.getOrCreate(player.getUuid());
        long oldPb = progress.personalBestByTrack.getOrDefault(run.track.id, Long.MAX_VALUE);
        boolean newPb = elapsedMillis < oldPb;

        if (newPb) {
            progress.personalBestByTrack.put(run.track.id, elapsedMillis);
            run.track.leaderboard.put(player.getUuid(), elapsedMillis);
            trackRepository.save();
            startIdleTask(player.getUuid(), elapsedMillis);
            send(player, "New PB: " + BigNumberFormatter.formatMillis(elapsedMillis));
        } else {
            send(player, "Finished in " + BigNumberFormatter.formatMillis(elapsedMillis));
        }

        BigNumber gain = upgradeService.computeIdlePointsPerTick(progress);
        progress.points = progress.points.add(gain);
        progressRepository.save();
    }

    private void startIdleTask(UUID uuid, long pbMillis) {
        ScheduledFuture<?> oldTask = idleTasks.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel(false);
        }

        long seconds = Math.max(1L, pbMillis / 1000L);
        ScheduledFuture<?> newTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            PlayerProgress progress = progressRepository.getOrCreate(uuid);
            BigNumber gain = upgradeService.computeIdlePointsPerTick(progress);
            progress.points = progress.points.add(gain);
            progressRepository.save();
        }, seconds, seconds, TimeUnit.SECONDS);

        idleTasks.put(uuid, newTask);
    }

    private void teleportPlayer(Player player, TrackVector3 location) {
        Ref<?> ref = player.getReference();
        if (ref == null) {
            return;
        }

        Vector3f rotation = player.getTransformComponent() == null ? Vector3f.ZERO : player.getTransformComponent().getRotation();
        player.getWorld()
            .getEntityStore()
            .getStore()
            .addComponent(player.getReference(), Teleport.getComponentType(), Teleport.createForPlayer(location.toCenterVector3d(), rotation));
    }

    private void ensureRunItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        inventory.getHotbar().setItemStackForSlot((short) 0, new ItemStack(RESET_ITEM, 1));
        inventory.getHotbar().setItemStackForSlot((short) 1, new ItemStack(CHECKPOINT_ITEM, 1));
        inventory.getHotbar().setItemStackForSlot((short) 2, new ItemStack(QUIT_ITEM, 1));
        player.sendInventory();
    }

    private void clearRunItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot((short) 0, ItemStack.EMPTY);
        inventory.getHotbar().setItemStackForSlot((short) 1, ItemStack.EMPTY);
        inventory.getHotbar().setItemStackForSlot((short) 2, ItemStack.EMPTY);
        player.sendInventory();
    }

    private void giveAdminSetupItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot((short) 6, new ItemStack(GREEN_WOOL_ITEM, 64));
        inventory.getHotbar().setItemStackForSlot((short) 7, new ItemStack(BLUE_WOOL_ITEM, 64));
        inventory.getHotbar().setItemStackForSlot((short) 8, new ItemStack(RED_WOOL_ITEM, 64));
        player.sendInventory();
    }

    private Track findTrackAtStart(TrackVector3 block) {
        for (Track track : trackRepository.allTracks()) {
            if (Objects.equals(track.start, block)) {
                return track;
            }
        }
        return null;
    }

    private boolean isTrackCheckpoint(Track track, TrackVector3 block) {
        for (TrackVector3 checkpoint : track.checkpoints) {
            if (Objects.equals(checkpoint, block)) {
                return true;
            }
        }
        return false;
    }

    private static void send(Player player, String text) {
        player.sendMessage(Message.raw("[Parkour] " + text));
    }

    private static boolean isLike(String value, String expected) {
        String normalizedExpected = normalizeItemId(expected);
        return value != null && (value.equals(normalizedExpected) || value.endsWith(normalizedExpected));
    }

    private static String normalizeItemId(String itemId) {
        return itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
    }

    private static int getLevel(PlayerProgress progress, int option) {
        return switch (option) {
            case 1 -> progress.option1;
            case 2 -> progress.option2;
            case 3 -> progress.option3;
            case 4 -> progress.option4;
            case 5 -> progress.option5;
            case 6 -> progress.option6;
            default -> 0;
        };
    }

    private static void incrementLevel(PlayerProgress progress, int option) {
        switch (option) {
            case 1 -> progress.option1++;
            case 2 -> progress.option2++;
            case 3 -> progress.option3++;
            case 4 -> progress.option4++;
            case 5 -> progress.option5++;
            case 6 -> progress.option6++;
            default -> {
            }
        }
    }

    private enum AdminStage {
        WAIT_START,
        WAIT_CHECKPOINT_OR_FINISH
    }

    private static final class AdminSession {
        private AdminStage stage = AdminStage.WAIT_START;
        private TrackVector3 start;
        private final List<TrackVector3> checkpoints = new ArrayList<>();
    }

    private static final class ActiveRun {
        private final Track track;
        private final long startedAtMillis;
        private TrackVector3 lastCheckpoint;

        private ActiveRun(Track track, long startedAtMillis) {
            this.track = track;
            this.startedAtMillis = startedAtMillis;
        }
    }
}
