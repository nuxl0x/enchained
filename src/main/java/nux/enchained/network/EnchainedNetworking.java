package nux.enchained.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import nux.enchained.client.gui.EnchainedTeamsScreen;
import nux.enchained.teams.TeamData;
import nux.enchained.teams.TeamManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Networking for Enchained teams GUI <-> server TeamManager.
 */
public final class EnchainedNetworking {

    public static final String MODID = "enchained";

    // C2S
    public static final Identifier C2S_REQUEST_FULL_SYNC = new Identifier(MODID, "teams_request_full");
    public static final Identifier C2S_CREATE_TEAM       = new Identifier(MODID, "teams_create");
    public static final Identifier C2S_DISBAND_TEAM      = new Identifier(MODID, "teams_disband");
    public static final Identifier C2S_SET_LOCKED        = new Identifier(MODID, "teams_set_locked");
    public static final Identifier C2S_MOVE_PLAYER       = new Identifier(MODID, "teams_move_player");
    public static final Identifier C2S_REMOVE_PLAYER     = new Identifier(MODID, "teams_remove_player");
    public static final Identifier C2S_SET_LEADER        = new Identifier(MODID, "teams_set_leader");

    // S2C
    public static final Identifier S2C_FULL_SYNC         = new Identifier(MODID, "teams_full_sync");

    private EnchainedNetworking() {}

    // =========================
    // Public snapshot DTOs
    // =========================

    public static final class TeamSnapshot {
        public final String name;
        public final int color;      // 0xRRGGBB
        public final boolean locked;
        public final UUID leader;    // may be null
        public final List<UUID> members;

        public TeamSnapshot(String name, int color, boolean locked, UUID leader, List<UUID> members) {
            this.name = name;
            this.color = color;
            this.locked = locked;
            this.leader = leader;
            this.members = members;
        }
    }

    public static final class PlayerSnapshot {
        public final UUID uuid;
        public final String name;
        public final String teamName; // null if none

        public PlayerSnapshot(UUID uuid, String name, String teamName) {
            this.uuid = uuid;
            this.name = name;
            this.teamName = teamName;
        }
    }

    // =========================
    // Registration entry points
    // =========================

    public static void registerServerReceivers() {
        // Request full data (any player)
        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_FULL_SYNC,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> sendFullSyncTo(player))
        );

        // Admin-only actions below

        // CREATE TEAM
        ServerPlayNetworking.registerGlobalReceiver(C2S_CREATE_TEAM,
                (server, player, handler, buf, responseSender) -> {
                    final String name = buf.readString(64);
                    final int color   = buf.readInt();
                    server.execute(() -> handleCreateTeamC2S(server, player, name, color));
                });

        // DISBAND TEAM
        ServerPlayNetworking.registerGlobalReceiver(C2S_DISBAND_TEAM,
                (server, player, handler, buf, responseSender) -> {
                    final String name = buf.readString(64);
                    server.execute(() -> handleDisbandTeamC2S(server, player, name));
                });

        // SET LOCKED
        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_LOCKED,
                (server, player, handler, buf, responseSender) -> {
                    final String name   = buf.readString(64);
                    final boolean lock  = buf.readBoolean();
                    server.execute(() -> handleSetLockedC2S(server, player, name, lock));
                });

        // MOVE PLAYER TO TEAM
        ServerPlayNetworking.registerGlobalReceiver(C2S_MOVE_PLAYER,
                (server, player, handler, buf, responseSender) -> {
                    final String teamName = buf.readString(64);
                    final UUID targetId   = buf.readUuid();
                    server.execute(() -> handleMovePlayerC2S(server, player, teamName, targetId));
                });

        // REMOVE PLAYER FROM TEAM (set to no team)
        ServerPlayNetworking.registerGlobalReceiver(C2S_REMOVE_PLAYER,
                (server, player, handler, buf, responseSender) -> {
                    final UUID targetId = buf.readUuid();
                    server.execute(() -> handleRemovePlayerC2S(server, player, targetId));
                });

        // SET LEADER
        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_LEADER,
                (server, player, handler, buf, responseSender) -> {
                    final String teamName = buf.readString(64);
                    final UUID newLeader  = buf.readUuid();
                    server.execute(() -> handleSetLeaderC2S(server, player, teamName, newLeader));
                });
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_FULL_SYNC,
                (client, handler, buf, responseSender) -> {
                    List<TeamSnapshot> teams = readTeamSnapshots(buf);
                    List<PlayerSnapshot> players = readPlayerSnapshots(buf);

                    client.execute(() -> {
                        if (client.currentScreen instanceof EnchainedTeamsScreen screen) {
                            screen.applyFullSync(teams, players);
                        }
                    });
                });
    }

    // =========================
    // Client send helpers
    // =========================

    public static void requestFullSync() {
        ClientPlayNetworking.send(C2S_REQUEST_FULL_SYNC, PacketByteBufs.empty());
    }

    public static void sendCreateTeam(String name, int color) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        buf.writeInt(color);
        ClientPlayNetworking.send(C2S_CREATE_TEAM, buf);
    }

    public static void sendDisbandTeam(String name) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        ClientPlayNetworking.send(C2S_DISBAND_TEAM, buf);
    }

    public static void sendSetLocked(String name, boolean locked) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        buf.writeBoolean(locked);
        ClientPlayNetworking.send(C2S_SET_LOCKED, buf);
    }

    public static void sendMovePlayerToTeam(String teamName, UUID playerId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(teamName);
        buf.writeUuid(playerId);
        ClientPlayNetworking.send(C2S_MOVE_PLAYER, buf);
    }

    public static void sendRemovePlayerFromTeam(UUID playerId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(playerId);
        ClientPlayNetworking.send(C2S_REMOVE_PLAYER, buf);
    }

    public static void sendSetLeader(String teamName, UUID leader) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(teamName);
        buf.writeUuid(leader);
        ClientPlayNetworking.send(C2S_SET_LEADER, buf);
    }

    // =========================
    // Server handlers
    // =========================

    private static boolean isAdmin(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2);
    }

    private static void handleCreateTeamC2S(MinecraftServer server,
                                            ServerPlayerEntity sender,
                                            String name,
                                            int color) {
        if (!isAdmin(sender)) return;

        TeamManager manager = TeamManager.get(server);

        // Pass `null` for leader so the team starts empty.
        boolean ok = manager.createTeam(name, null, color);
        if (!ok) {
            sender.sendMessage(Text.literal("[Enchained] A team with that name already exists."), false);
            return;
        }

        // Broadcast updated team state to everyone
        sendFullSyncToAll(server);
    }

    private static void handleDisbandTeamC2S(MinecraftServer server,
                                             ServerPlayerEntity sender,
                                             String name) {
        if (!isAdmin(sender)) return;

        TeamManager manager = TeamManager.get(server);
        manager.disbandTeam(name);

        sendFullSyncToAll(server);
    }

    private static void handleSetLockedC2S(MinecraftServer server,
                                           ServerPlayerEntity sender,
                                           String name,
                                           boolean locked) {
        if (!isAdmin(sender)) return;

        TeamManager manager = TeamManager.get(server);
        manager.setLocked(name, locked);

        sendFullSyncToAll(server);
    }

    private static void handleMovePlayerC2S(MinecraftServer server,
                                            ServerPlayerEntity sender,
                                            String targetTeamName,
                                            UUID targetPlayer) {
        if (!isAdmin(sender)) return;

        TeamManager manager = TeamManager.get(server);
        manager.movePlayerToTeam(targetTeamName, targetPlayer);

        sendFullSyncToAll(server);
    }

    private static void handleRemovePlayerC2S(MinecraftServer server,
                                              ServerPlayerEntity sender,
                                              UUID targetPlayer) {
        if (!isAdmin(sender)) return;

        TeamManager manager = TeamManager.get(server);
        manager.removePlayerFromTeam(targetPlayer); // this sets them to "no team"

        sendFullSyncToAll(server);
    }

    private static void handleSetLeaderC2S(MinecraftServer server,
                                           ServerPlayerEntity sender,
                                           String teamName,
                                           UUID newLeader) {
        if (!isAdmin(sender)) return;

        TeamManager manager = TeamManager.get(server);
        manager.setLeader(teamName, newLeader);

        sendFullSyncToAll(server);
    }

    // =========================
    // Full sync building
    // =========================

    public static void sendFullSyncTo(ServerPlayerEntity target) {
        MinecraftServer server = target.getServer();
        if (server == null) return;

        TeamManager manager = TeamManager.get(server);

        // Build team snapshots
        List<TeamSnapshot> teams = new ArrayList<>();
        for (TeamData t : manager.getAllTeams()) {
            List<UUID> members = new ArrayList<>(t.getMembers());
            teams.add(new TeamSnapshot(
                    t.getName(),
                    t.getColor(),
                    t.isLocked(),
                    t.getLeader(),
                    members
            ));
        }

        // Build player snapshots (online players only)
        List<PlayerSnapshot> players = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            String teamName = manager.getTeamOfPlayer(id).map(TeamData::getName).orElse(null);
            players.add(new PlayerSnapshot(id, player.getGameProfile().getName(), teamName));
        }

        PacketByteBuf buf = PacketByteBufs.create();
        writeTeamSnapshots(buf, teams);
        writePlayerSnapshots(buf, players);

        ServerPlayNetworking.send(target, S2C_FULL_SYNC, buf);
    }

    /**
     * Broadcast a full sync to every online player.
     */
    public static void sendFullSyncToAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendFullSyncTo(player);
        }
    }

    // =========================
    // Serialization helpers
    // =========================

    private static void writeTeamSnapshots(PacketByteBuf buf, List<TeamSnapshot> teams) {
        buf.writeVarInt(teams.size());
        for (TeamSnapshot t : teams) {
            buf.writeString(t.name);
            buf.writeInt(t.color);
            buf.writeBoolean(t.locked);

            buf.writeBoolean(t.leader != null);
            if (t.leader != null) {
                buf.writeUuid(t.leader);
            }

            buf.writeVarInt(t.members.size());
            for (UUID m : t.members) {
                buf.writeUuid(m);
            }
        }
    }

    private static List<TeamSnapshot> readTeamSnapshots(PacketByteBuf buf) {
        int count = buf.readVarInt();
        List<TeamSnapshot> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buf.readString(64);
            int color = buf.readInt();
            boolean locked = buf.readBoolean();

            UUID leader = null;
            boolean hasLeader = buf.readBoolean();
            if (hasLeader) {
                leader = buf.readUuid();
            }

            int mCount = buf.readVarInt();
            List<UUID> members = new ArrayList<>(mCount);
            for (int j = 0; j < mCount; j++) {
                members.add(buf.readUuid());
            }

            list.add(new TeamSnapshot(name, color, locked, leader, members));
        }
        return list;
    }

    private static void writePlayerSnapshots(PacketByteBuf buf, List<PlayerSnapshot> players) {
        buf.writeVarInt(players.size());
        for (PlayerSnapshot p : players) {
            buf.writeUuid(p.uuid);
            buf.writeString(p.name);
            buf.writeBoolean(p.teamName != null);
            if (p.teamName != null) {
                buf.writeString(p.teamName);
            }
        }
    }

    private static List<PlayerSnapshot> readPlayerSnapshots(PacketByteBuf buf) {
        int count = buf.readVarInt();
        List<PlayerSnapshot> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUuid();
            String name = buf.readString(64);
            String teamName = null;
            boolean hasTeam = buf.readBoolean();
            if (hasTeam) {
                teamName = buf.readString(64);
            }
            list.add(new PlayerSnapshot(uuid, name, teamName));
        }
        return list;
    }
}