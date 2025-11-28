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

    private final Map<String, TeamData> teamsByName = new HashMap<>(); // key = lowercase name
    private final Map<UUID, String> teamByPlayer = new HashMap<>();    // player -> team key

    public TeamManager() {
    }

    // ------------------------
    // Static accessors
    // ------------------------

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

    // ------------------------
    // Persistence
    // ------------------------

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

            // Members
            NbtList membersList = new NbtList();
            for (UUID member : team.getMembers()) {
                membersList.add(NbtString.of(member.toString()));
            }
            t.put("Members", membersList);

            // Join requests
            NbtList joinReqList = new NbtList();
            for (UUID req : team.getJoinRequests()) {
                joinReqList.add(NbtString.of(req.toString()));
            }
            t.put("JoinRequests", joinReqList);

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

            int color = teamTag.contains("Color", NbtElement.INT_TYPE)
                    ? teamTag.getInt("Color")
                    : 0xFFFFFF;

            TeamData team = new TeamData(name, leader, locked, color);

            // Members
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

            // Join requests (optional)
            if (teamTag.contains("JoinRequests", NbtElement.LIST_TYPE)) {
                NbtList joinReqList = teamTag.getList("JoinRequests", NbtElement.STRING_TYPE);
                for (NbtElement rElem : joinReqList) {
                    String uuidStr = rElem.asString();
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        team.addJoinRequest(uuid);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            teamsByName.put(name.toLowerCase(Locale.ROOT), team);
        }
    }

    // ------------------------
    // Query methods
    // ------------------------

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

    // ------------------------
    // Modification methods
    // ------------------------

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

        // Clear player -> team mapping
        for (UUID member : team.getMembers()) {
            teamByPlayer.remove(member);
        }

        // Join requests don’t map into teamByPlayer, so nothing else to clear
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
     * Teams are NOT deleted when empty – they can remain empty.
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

        // They might also have had a pending join request (we can safely clear)
        team.removeJoinRequest(playerId);

        markDirty();
        return removed;
    }

    /**
     * Move player to a new team: remove from old, then add to target.
     */
    public boolean movePlayerToTeam(String targetTeamName, UUID playerId) {
        removePlayerFromTeam(playerId);

        String key = targetTeamName.toLowerCase(Locale.ROOT);
        TeamData team = teamsByName.get(key);
        if (team != null) {
            // Clear any join request on this team for that player
            team.removeJoinRequest(playerId);
        }

        boolean ok = addPlayerToTeam(targetTeamName, playerId);
        if (ok) {
            markDirty();
        }
        return ok;
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

    // ------------------------
    // Join Request methods
    // ------------------------

    /**
     * Player asks to join a team.
     * Fails if:
     *  - team doesn't exist
     *  - team is locked
     *  - player is already in any team
     *  - player already requested this team
     */
    public boolean addJoinRequest(String teamName, UUID playerId) {
        if (teamName == null || playerId == null) return false;
        String key = teamName.toLowerCase(Locale.ROOT);
        TeamData team = teamsByName.get(key);
        if (team == null) return false;

        // Don't allow join requests on locked teams
        if (team.isLocked()) return false;

        // Already in a team? They should not be able to request.
        if (isInTeam(playerId)) return false;

        // Already requested this team?
        if (team.hasJoinRequest(playerId)) return false;

        boolean added = team.addJoinRequest(playerId);
        if (added) {
            markDirty();
        }
        return added;
    }

    /**
     * Remove a join request from a team (accept or reject).
     */
    public boolean removeJoinRequest(String teamName, UUID playerId) {
        if (teamName == null || playerId == null) return false;
        String key = teamName.toLowerCase(Locale.ROOT);
        TeamData team = teamsByName.get(key);
        if (team == null) return false;

        boolean removed = team.removeJoinRequest(playerId);
        if (removed) {
            markDirty();
        }
        return removed;
    }

    // ------------------------
    // Role / rule helpers
    // ------------------------

    /**
     * Whether the specified player is the leader of the given team.
     */
    public boolean isLeader(String teamName, UUID playerId) {
        if (teamName == null || playerId == null) return false;
        String key = teamName.toLowerCase(Locale.ROOT);
        TeamData team = teamsByName.get(key);
        if (team == null) return false;
        UUID leader = team.getLeader();
        return leader != null && leader.equals(playerId);
    }

    /**
     * Whether a player is allowed to leave their current team.
     * Current rule: if the team is locked, nobody can leave.
     */
    public boolean canLeave(UUID playerId) {
        if (playerId == null) return false;

        Optional<TeamData> opt = getTeamOfPlayer(playerId);
        if (opt.isEmpty()) {
            // Not in a team – nothing to leave; treat as allowed
            return true;
        }

        TeamData team = opt.get();
        return !team.isLocked();
    }

    // ------------------------
    // Misc
    // ------------------------

    public static String getSavePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT)
                .resolve("data")
                .resolve(SAVE_NAME + ".dat")
                .toString();
    }
}