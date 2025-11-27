package nux.enchained.teams;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

/**
 * Stores all team data for a world and persists it to disk.
 *
 * Saved as data/enchained_teams.dat inside the world folder.
 */
public class TeamManager extends PersistentState {

    public static final String SAVE_NAME = "enchained_teams";

    private final Map<String, TeamData> teamsByName = new HashMap<>();
    private final Map<UUID, String> teamByPlayer = new HashMap<>();

    public TeamManager() {
    }

    // ---------- Static accessors ----------

    /**
     * Get the TeamManager for the server (using the overworld's PersistentStateManager).
     */
    public static TeamManager get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld is null – cannot access TeamManager");
        }
        return get(overworld);
    }

    /**
     * Get the TeamManager for a specific ServerWorld.
     */
    public static TeamManager get(ServerWorld world) {
        PersistentStateManager stateManager = world.getPersistentStateManager();
        return stateManager.getOrCreate(TeamManager::fromNbt, TeamManager::new, SAVE_NAME);
    }

    private static TeamManager fromNbt(NbtCompound nbt) {
        TeamManager manager = new TeamManager();
        manager.readNbt(nbt);
        return manager;
    }

    // ---------- Persistence ----------

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList teamList = new NbtList();

        for (TeamData team : teamsByName.values()) {
            NbtCompound t = new NbtCompound();
            t.putString("Name", team.getName());
            t.putBoolean("Locked", team.isLocked());

            UUID leader = team.getLeader();
            t.putString("Leader", leader != null ? leader.toString() : "");

            // Store color
            t.putInt("Color", team.getColor());

            NbtList membersList = new NbtList();
            for (UUID member : team.getMembers()) {
                membersList.add(NbtString.of(member.toString()));
            }
            t.put("Members", membersList);

            teamList.add(t);
        }

        nbt.put("Teams", teamList);
        return nbt;
    }

    private void readNbt(NbtCompound nbt) {
        teamsByName.clear();
        teamByPlayer.clear();

        NbtList teamList = nbt.getList("Teams", NbtElement.COMPOUND_TYPE);
        for (NbtElement elem : teamList) {
            if (!(elem instanceof NbtCompound teamTag)) continue;

            String name = teamTag.getString("Name");
            boolean locked = teamTag.getBoolean("Locked");

            String leaderStr = teamTag.getString("Leader");
            UUID leader = leaderStr.isEmpty() ? null : UUID.fromString(leaderStr);

            // Color is optional for backwards-compatibility
            int color = teamTag.contains("Color", NbtElement.INT_TYPE)
                    ? teamTag.getInt("Color")
                    : 0xFFFFFF;

            TeamData team = new TeamData(name, leader, locked, color);

            NbtList membersList = teamTag.getList("Members", NbtElement.STRING_TYPE);
            for (NbtElement mElem : membersList) {
                String uuidStr = mElem.asString();
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    team.addMember(uuid);
                    teamByPlayer.put(uuid, name.toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }

            teamsByName.put(name.toLowerCase(Locale.ROOT), team);
        }
    }

    // ---------- Query methods ----------

    public Optional<TeamData> getTeamByName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(teamsByName.get(name.toLowerCase(Locale.ROOT)));
    }

    public Optional<TeamData> getTeamOfPlayer(UUID playerId) {
        String teamNameKey = teamByPlayer.get(playerId);
        if (teamNameKey == null) return Optional.empty();
        return Optional.ofNullable(teamsByName.get(teamNameKey));
    }

    public boolean isInTeam(UUID playerId) {
        return teamByPlayer.containsKey(playerId);
    }

    public Collection<TeamData> getAllTeams() {
        return Collections.unmodifiableCollection(teamsByName.values());
    }

    // ---------- Modification methods ----------

    /**
     * Create a new team. If leader is null, the team starts empty.
     */
    public boolean createTeam(String name, UUID leader, int color) {
        String key = name.toLowerCase(Locale.ROOT);
        if (teamsByName.containsKey(key)) {
            return false;
        }

        TeamData team = new TeamData(name, leader, false, color);
        teamsByName.put(key, team);

        if (leader != null) {
            // Ensure the player mapping points to this team
            teamByPlayer.put(leader, key);
        }

        markDirty();
        return true;
    }

    public boolean disbandTeam(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        TeamData team = teamsByName.remove(key);
        if (team == null) {
            return false;
        }

        for (UUID member : team.getMembers()) {
            teamByPlayer.remove(member);
        }

        markDirty();
        return true;
    }

    /**
     * Add a player to a team. Does NOT automatically remove them from an old team.
     * Use movePlayerToTeam for "move" semantics.
     */
    public boolean addPlayerToTeam(String teamName, UUID playerId) {
        TeamData team = teamsByName.get(teamName.toLowerCase(Locale.ROOT));
        if (team == null) return false;

        if (!team.addMember(playerId)) {
            return false;
        }

        teamByPlayer.put(playerId, teamName.toLowerCase(Locale.ROOT));
        markDirty();
        return true;
    }

    /**
     * Remove a player from whatever team they are in.
     * NOTE: Teams are NOT deleted when empty anymore – they can remain empty.
     */
    public boolean removePlayerFromTeam(UUID playerId) {
        String teamKey = teamByPlayer.remove(playerId);
        if (teamKey == null) {
            return false;
        }

        TeamData team = teamsByName.get(teamKey);
        if (team == null) {
            markDirty();
            return false;
        }

        boolean removed = team.removeMember(playerId);

        if (playerId.equals(team.getLeader())) {
            team.setLeader(null);
        }

        markDirty();
        return removed;
    }

    /**
     * Move player to a new team: remove from old, then add to target.
     */
    public boolean movePlayerToTeam(String targetTeamName, UUID playerId) {
        removePlayerFromTeam(playerId);
        return addPlayerToTeam(targetTeamName, playerId);
    }

    public boolean setLeader(String teamName, UUID newLeader) {
        String key = teamName.toLowerCase(Locale.ROOT);
        TeamData team = teamsByName.get(key);
        if (team == null) return false;

        team.setLeader(newLeader);
        if (newLeader != null) {
            team.addMember(newLeader);
            teamByPlayer.put(newLeader, key);
        }

        markDirty();
        return true;
    }

    public boolean setLocked(String teamName, boolean locked) {
        TeamData team = teamsByName.get(teamName.toLowerCase(Locale.ROOT));
        if (team == null) return false;

        team.setLocked(locked);
        markDirty();
        return true;
    }

    public boolean isLocked(String teamName) {
        TeamData team = teamsByName.get(teamName.toLowerCase(Locale.ROOT));
        return team != null && team.isLocked();
    }

    public static String getSavePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT)
                .resolve("data")
                .resolve(SAVE_NAME + ".dat")
                .toString();
    }
}