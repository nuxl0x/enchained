package nux.enchained.teams;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single team: name, color, leader, members, lock state,
 * and pending join requests.
 */
public class TeamData {

    private final String name;
    private final int color;         // 0xRRGGBB
    private UUID leader;             // can be null
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> joinRequests = new HashSet<>();
    private boolean locked;
    private boolean friendlyFireDisabled = false;

    public TeamData(String name, UUID leader, boolean locked, int color) {
        this.name = name;
        this.leader = leader;
        this.locked = locked;
        this.color = color;

        // Auto-add leader as member if provided.
        if (leader != null) {
            this.members.add(leader);
        }
    }

    // ------------------------
    // Basic data
    // ------------------------

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
        if (leader != null) {
            this.members.add(leader);
        }
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isFriendlyFireDisabled() {
        return friendlyFireDisabled;
    }

    public void setFriendlyFireDisabled(boolean disabled) {
        this.friendlyFireDisabled = disabled;
    }

    // ------------------------
    // Members
    // ------------------------

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean addMember(UUID uuid) {
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    // ------------------------
    // Join Requests
    // ------------------------

    /**
     * Players who have requested to join this team but are not yet members.
     */
    public Set<UUID> getJoinRequests() {
        return joinRequests;
    }

    public boolean addJoinRequest(UUID uuid) {
        return joinRequests.add(uuid);
    }

    public boolean removeJoinRequest(UUID uuid) {
        return joinRequests.remove(uuid);
    }

    public boolean hasJoinRequest(UUID uuid) {
        return joinRequests.contains(uuid);
    }
}