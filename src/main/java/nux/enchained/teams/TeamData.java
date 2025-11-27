package nux.enchained.teams;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single team: name, color, leader, members, lock state.
 */
public class TeamData {

    private final String name;
    private final int color;         // 0xRRGGBB
    private UUID leader;             // can be null
    private final Set<UUID> members = new HashSet<>();
    private boolean locked;

    public TeamData(String name, UUID leader, boolean locked, int color) {
        this.name = name;
        this.leader = leader;
        this.locked = locked;
        this.color = color;

        // We still auto-add leader as member *if* provided.
        if (leader != null) {
            this.members.add(leader);
        }
    }

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
}