package com.example.parkouridle.command;

import com.example.parkouridle.math.BigNumber;
import com.example.parkouridle.model.PlayerProgress;
import com.example.parkouridle.model.Track;
import com.example.parkouridle.service.ParkourManager;
import com.example.parkouridle.ui.ParkourUiService;
import com.example.parkouridle.util.BigNumberFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ParkourRootCommand extends AbstractPlayerCommand {
    private final ParkourUiService parkourUiService;

    public ParkourRootCommand(ParkourManager parkourManager, ParkourUiService parkourUiService) {
        super("parkour", "Parkour gameplay commands and menus");
        this.parkourUiService = parkourUiService;
        addAliases("p");

        addSubCommand(new MenuCommand(parkourUiService));
        addSubCommand(new ListCommand(parkourManager));
        addSubCommand(new TpCommand(parkourManager));
        addSubCommand(new LeaderboardCommand(parkourManager));
        addSubCommand(new AdminCommand(parkourManager));
        addSubCommand(new UpgradeCommand(parkourManager));
        addSubCommand(new UpgradeBuyCommand(parkourManager));
        addSubCommand(new VoidPointBuyCommand(parkourManager));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerMeta, World world) {
        parkourUiService.openMainMenu(store, playerRef, playerMeta);
    }

    private static final class MenuCommand extends AbstractPlayerCommand {
        private final ParkourUiService parkourUiService;

        private MenuCommand(ParkourUiService parkourUiService) {
            super("menu", "Open the Parkour main menu UI");
            this.parkourUiService = parkourUiService;
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerMeta, World world) {
            parkourUiService.openMainMenu(store, playerRef, playerMeta);
        }
    }

    private static final class ListCommand extends AbstractCommand {
        private final ParkourManager parkourManager;

        private ListCommand(ParkourManager parkourManager) {
            super("list", "List all registered parkour track IDs");
            this.parkourManager = parkourManager;
        }

        @Override
        protected java.util.concurrent.CompletableFuture<Void> execute(CommandContext context) {
            List<Integer> ids = parkourManager.listTrackIds();
            context.sendMessage(Message.raw(ids.isEmpty() ? "No tracks registered." : "Tracks: " + ids));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private static final class TpCommand extends AbstractPlayerCommand {
        private final ParkourManager parkourManager;
        private final RequiredArg<Integer> idArg;

        private TpCommand(ParkourManager parkourManager) {
            super("tp", "Teleport to start of a parkour track");
            this.parkourManager = parkourManager;
            this.idArg = withRequiredArg("id", "Track ID", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerMeta, World world) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Could not resolve player entity."));
                return;
            }
            parkourManager.teleportToTrackStart(player, idArg.get(context));
        }
    }

    private static final class LeaderboardCommand extends AbstractCommand {
        private final ParkourManager parkourManager;
        private final RequiredArg<Integer> idArg;

        private LeaderboardCommand(ParkourManager parkourManager) {
            super("leaderboard", "Show top 50 leaderboard for track ID");
            this.parkourManager = parkourManager;
            this.idArg = withRequiredArg("id", "Track ID", ArgTypes.INTEGER);
        }

        @Override
        protected java.util.concurrent.CompletableFuture<Void> execute(CommandContext context) {
            int id = idArg.get(context);
            Track track = parkourManager.getTrack(id);
            if (track == null) {
                context.sendMessage(Message.raw("Track not found: " + id));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            List<Map.Entry<UUID, Long>> top = parkourManager.getTopLeaderboard(id, 50);
            if (top.isEmpty()) {
                context.sendMessage(Message.raw("No leaderboard entries for track " + id));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            StringBuilder builder = new StringBuilder("Track ").append(id).append(" Top ").append(top.size()).append(":\n");
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<UUID, Long> row = top.get(i);
                builder.append(i + 1)
                    .append(". ")
                    .append(row.getKey())
                    .append(" -> ")
                    .append(BigNumberFormatter.formatMillis(row.getValue()))
                    .append("\n");
            }

            context.sendMessage(Message.raw(builder.toString()));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private static final class AdminCommand extends AbstractPlayerCommand {
        private final ParkourManager parkourManager;

        private AdminCommand(ParkourManager parkourManager) {
            super("admin", "Enter admin mode for parkour track registration");
            this.parkourManager = parkourManager;
            requirePermission("server.admin");
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerMeta, World world) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Could not resolve player entity."));
                return;
            }
            parkourManager.beginAdminRegistration(player);
            context.sendMessage(Message.raw("Admin registration mode enabled."));
        }
    }

    private static final class UpgradeCommand extends AbstractCommand {
        private final ParkourManager parkourManager;

        private UpgradeCommand(ParkourManager parkourManager) {
            super("upgrade", "Show your 6-tier parkour upgrades");
            this.parkourManager = parkourManager;
        }

        @Override
        protected java.util.concurrent.CompletableFuture<Void> execute(CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Player only command."));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            UUID uuid = context.sender().getUuid();
            PlayerProgress progress = parkourManager.getProgress(uuid);
            BigNumber next1 = parkourManager.getUpgradeService().costForOption(1, progress.option1);
            BigNumber next2 = parkourManager.getUpgradeService().costForOption(2, progress.option2);
            BigNumber next3 = parkourManager.getUpgradeService().costForOption(3, progress.option3);
            BigNumber next4 = parkourManager.getUpgradeService().costForOption(4, progress.option4);
            BigNumber next5 = parkourManager.getUpgradeService().costForOption(5, progress.option5);
            BigNumber next6 = parkourManager.getUpgradeService().costForOption(6, progress.option6);

            String msg = "Points=" + BigNumberFormatter.formatBigNumber(progress.points)
                + " | L1=" + progress.option1 + " (" + BigNumberFormatter.formatBigNumber(next1) + ")"
                + " | L2=" + progress.option2 + " (" + BigNumberFormatter.formatBigNumber(next2) + ")"
                + " | L3=" + progress.option3 + " (" + BigNumberFormatter.formatBigNumber(next3) + ")"
                + " | L4=" + progress.option4 + " (" + BigNumberFormatter.formatBigNumber(next4) + ")"
                + " | L5=" + progress.option5 + " (" + BigNumberFormatter.formatBigNumber(next5) + ")"
                + " | L6=" + progress.option6 + " (" + BigNumberFormatter.formatBigNumber(next6) + ")";

            context.sendMessage(Message.raw(msg));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private static final class UpgradeBuyCommand extends AbstractPlayerCommand {
        private final ParkourManager parkourManager;
        private final RequiredArg<Integer> optionArg;

        private UpgradeBuyCommand(ParkourManager parkourManager) {
            super("upgradebuy", "Buy one level of an upgrade option (1-6)");
            this.parkourManager = parkourManager;
            this.optionArg = withRequiredArg("option", "Upgrade option 1-6", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerMeta, World world) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Could not resolve player entity."));
                return;
            }

            int option = optionArg.get(context);
            if (option < 1 || option > 6) {
                context.sendMessage(Message.raw("Option must be between 1 and 6."));
                return;
            }

            parkourManager.buyUpgrade(player, option);
        }
    }

    private static final class VoidPointBuyCommand extends AbstractPlayerCommand {
        private final ParkourManager parkourManager;

        private VoidPointBuyCommand(ParkourManager parkourManager) {
            super("buyvp", "Buy one Void Point using Punkte");
            this.parkourManager = parkourManager;
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerMeta, World world) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Could not resolve player entity."));
                return;
            }
            parkourManager.buyVoidPoint(player);
        }
    }
}
