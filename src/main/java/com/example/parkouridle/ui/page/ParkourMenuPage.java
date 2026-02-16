package com.example.parkouridle.ui.page;

import com.example.parkouridle.math.BigNumber;
import com.example.parkouridle.model.PlayerProgress;
import com.example.parkouridle.model.Track;
import com.example.parkouridle.service.ParkourManager;
import com.example.parkouridle.util.BigNumberFormatter;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ParkourMenuPage extends InteractiveCustomUIPage<ParkourMenuPage.UiEventData> {

    private static final String BASE_LAYOUT = "Pages/CommandListPage.ui";
    private static final String BUTTON_LAYOUT = "Pages/BasicTextButton.ui";

    private final ParkourManager parkourManager;

    private Screen screen = Screen.MAIN;
    private int selectedTrackId = -1;

    public ParkourMenuPage(PlayerRef playerRef, ParkourManager parkourManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, UiEventData.CODEC);
        this.parkourManager = parkourManager;
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(BASE_LAYOUT);

        commands.set("#SearchInput.Visible", false);
        commands.set("#SendToChatButton.Visible", false);
        commands.set("#VariantsSection.Visible", false);
        commands.set("#SubcommandSection.Visible", false);
        commands.set("#AliasesSection.Visible", false);
        commands.set("#PermissionSection.Visible", false);
        commands.set("#ParametersSection.Visible", false);
        commands.set("#ArgumentTypesSection.Visible", false);
        commands.set("#BackButton.Visible", screen != Screen.MAIN);

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BackButton",
            new EventData().append(UiEventData.KEY_ACTION, "BACK")
        );

        commands.clear("#CommandList");

        switch (screen) {
            case MAIN -> buildMain(commands, events, store, playerRef);
            case TRACKS -> buildTrackList(commands, events);
            case TRACK_ACTIONS -> buildTrackActions(commands, events);
            case LEADERBOARD -> buildLeaderboard(commands, events);
            case UPGRADES -> buildUpgrades(commands, events);
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, UiEventData data) {
        if (data == null || data.action == null) {
            return;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        String action = data.action;

        if ("BACK".equals(action)) {
            if (screen == Screen.TRACK_ACTIONS) {
                screen = Screen.TRACKS;
            } else if (screen == Screen.LEADERBOARD) {
                screen = Screen.TRACK_ACTIONS;
            } else if (screen == Screen.UPGRADES || screen == Screen.TRACKS) {
                screen = Screen.MAIN;
            }
            rebuild();
            return;
        }

        if ("OPEN_TRACKS".equals(action)) {
            screen = Screen.TRACKS;
            rebuild();
            return;
        }

        if ("OPEN_UPGRADES".equals(action)) {
            screen = Screen.UPGRADES;
            rebuild();
            return;
        }

        if ("OPEN_ADMIN".equals(action)) {
            if (!player.hasPermission("server.admin")) {
                player.sendMessage(Message.raw("[Parkour] You do not have permission to use admin setup."));
                return;
            }
            parkourManager.beginAdminRegistration(player);
            close();
            return;
        }

        if ("BUY_VP".equals(action)) {
            parkourManager.buyVoidPoint(player);
            rebuild();
            return;
        }

        if ("CLOSE".equals(action)) {
            close();
            return;
        }

        if (action.startsWith("TRACK:")) {
            selectedTrackId = parseIntAfterPrefix(action, "TRACK:");
            if (selectedTrackId > 0) {
                screen = Screen.TRACK_ACTIONS;
                rebuild();
            }
            return;
        }

        if (action.startsWith("TP:")) {
            int trackId = parseIntAfterPrefix(action, "TP:");
            if (trackId > 0) {
                parkourManager.teleportToTrackStart(player, trackId);
            }
            return;
        }

        if (action.startsWith("LB:")) {
            int trackId = parseIntAfterPrefix(action, "LB:");
            if (trackId > 0) {
                selectedTrackId = trackId;
                screen = Screen.LEADERBOARD;
                rebuild();
            }
            return;
        }

        if (action.startsWith("UPGRADE:")) {
            int option = parseIntAfterPrefix(action, "UPGRADE:");
            if (option >= 1 && option <= 6) {
                parkourManager.buyUpgrade(player, option);
                rebuild();
            }
        }
    }

    private void buildMain(UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store, Ref<EntityStore> playerRef) {
        commands.set("#CommandName.TextSpans", Message.raw("Parkour Idle"));
        commands.set("#CommandDescription.TextSpans", Message.raw("Choose what you want to do."));

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            PlayerProgress progress = parkourManager.getProgress(player.getUuid());
            commands.set("#CommandUsageLabel.TextSpans", Message.raw("Punkte: " + BigNumberFormatter.formatBigNumber(progress.points)));
        } else {
            commands.set("#CommandUsageLabel.TextSpans", Message.raw("Punkte: unknown"));
        }

        addMenuButton(commands, events, 0, "Tracks", "OPEN_TRACKS");
        addMenuButton(commands, events, 1, "Upgrades", "OPEN_UPGRADES");
        addMenuButton(commands, events, 2, "Buy Void Point", "BUY_VP");

        int closeIndex = 3;
        if (player != null && player.hasPermission("server.admin")) {
            addMenuButton(commands, events, 3, "Admin Setup", "OPEN_ADMIN");
            closeIndex = 4;
        }

        addMenuButton(commands, events, closeIndex, "Close", "CLOSE");
    }

    private void buildTrackList(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#CommandName.TextSpans", Message.raw("Tracks"));
        commands.set("#CommandDescription.TextSpans", Message.raw("Select a track to teleport or view leaderboard."));
        commands.set("#CommandUsageLabel.TextSpans", Message.raw("Registered tracks: " + parkourManager.listTrackIds().size()));

        List<Integer> ids = new ArrayList<>(parkourManager.listTrackIds());
        if (ids.isEmpty()) {
            addMenuButton(commands, events, 0, "No tracks registered yet.", "BACK");
            return;
        }

        int index = 0;
        for (int id : ids) {
            Track track = parkourManager.getTrack(id);
            String label = "Track #" + id;
            if (track != null) {
                label += " | CP: " + track.checkpoints.size();
            }
            addMenuButton(commands, events, index++, label, "TRACK:" + id);
        }
    }

    private void buildTrackActions(UICommandBuilder commands, UIEventBuilder events) {
        Track track = parkourManager.getTrack(selectedTrackId);
        if (track == null) {
            screen = Screen.TRACKS;
            rebuild();
            return;
        }

        commands.set("#CommandName.TextSpans", Message.raw("Track #" + selectedTrackId));
        commands.set("#CommandDescription.TextSpans", Message.raw("Start: " + vec(track.start) + " | Finish: " + vec(track.finish)));
        commands.set("#CommandUsageLabel.TextSpans", Message.raw("Choose an action."));

        addMenuButton(commands, events, 0, "Teleport to Start", "TP:" + selectedTrackId);
        addMenuButton(commands, events, 1, "View Leaderboard", "LB:" + selectedTrackId);
        addMenuButton(commands, events, 2, "Back to Tracks", "BACK");
    }

    private void buildLeaderboard(UICommandBuilder commands, UIEventBuilder events) {
        Track track = parkourManager.getTrack(selectedTrackId);
        if (track == null) {
            screen = Screen.TRACKS;
            rebuild();
            return;
        }

        commands.set("#CommandName.TextSpans", Message.raw("Leaderboard: Track #" + selectedTrackId));
        commands.set("#CommandDescription.TextSpans", Message.raw("Top 50 best times."));

        List<Map.Entry<UUID, Long>> top = parkourManager.getTopLeaderboard(selectedTrackId, 50);
        commands.set("#CommandUsageLabel.TextSpans", Message.raw("Entries: " + top.size()));

        addMenuButton(commands, events, 0, "Teleport to Start", "TP:" + selectedTrackId);
        addMenuButton(commands, events, 1, "Back", "BACK");

        if (top.isEmpty()) {
            addStaticButton(commands, 2, "No times yet.");
            return;
        }

        int index = 2;
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<UUID, Long> row = top.get(i);
            String line = (i + 1) + ". " + shortUuid(row.getKey()) + " - " + BigNumberFormatter.formatMillis(row.getValue());
            addStaticButton(commands, index++, line);
        }
    }

    private void buildUpgrades(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#CommandName.TextSpans", Message.raw("Upgrades"));

        PlayerProgress progress = parkourManager.getProgress(playerRef.getUuid());
        commands.set("#CommandDescription.TextSpans", Message.raw("Buy upgrades and scale your Punkte production."));
        commands.set("#CommandUsageLabel.TextSpans", Message.raw("Punkte: " + BigNumberFormatter.formatBigNumber(progress.points) + " | VP: " + progress.currentVp));

        for (int option = 1; option <= 6; option++) {
            int level = switch (option) {
                case 1 -> progress.option1;
                case 2 -> progress.option2;
                case 3 -> progress.option3;
                case 4 -> progress.option4;
                case 5 -> progress.option5;
                case 6 -> progress.option6;
                default -> 0;
            };

            BigNumber cost = parkourManager.getUpgradeService().costForOption(option, level);
            String label = "Option " + option + " | L" + level + " | Cost " + BigNumberFormatter.formatBigNumber(cost);
            addMenuButton(commands, events, option - 1, label, "UPGRADE:" + option);
        }

        addMenuButton(commands, events, 6, "Buy 1 Void Point", "BUY_VP");
        addMenuButton(commands, events, 7, "Back", "BACK");
    }

    private void addMenuButton(UICommandBuilder commands, UIEventBuilder events, int index, String label, String action) {
        String buttonPath = "#CommandList[" + index + "]";
        commands.append("#CommandList", BUTTON_LAYOUT);
        commands.set(buttonPath + ".TextSpans", Message.raw(label));
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            buttonPath,
            new EventData().append(UiEventData.KEY_ACTION, action),
            false
        );
    }

    private void addStaticButton(UICommandBuilder commands, int index, String label) {
        commands.append("#CommandList", BUTTON_LAYOUT);
        commands.set("#CommandList[" + index + "].TextSpans", Message.raw(label));
    }

    private static int parseIntAfterPrefix(String text, String prefix) {
        try {
            return Integer.parseInt(text.substring(prefix.length()));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String vec(com.example.parkouridle.model.TrackVector3 vec) {
        if (vec == null) {
            return "?";
        }
        return vec.x + "," + vec.y + "," + vec.z;
    }

    private static String shortUuid(UUID uuid) {
        String s = uuid.toString();
        return s.substring(0, 8);
    }

    private enum Screen {
        MAIN,
        TRACKS,
        TRACK_ACTIONS,
        LEADERBOARD,
        UPGRADES
    }

    public static final class UiEventData {
        public static final String KEY_ACTION = "Action";
        public static final BuilderCodec<UiEventData> CODEC = BuilderCodec
            .builder(UiEventData.class, UiEventData::new)
            .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .build();

        public String action;
    }
}
