package io.github.julianm20.hiddenteams;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A team.
 *
 * <p>The team's identity is its {@link #id()}, never its name. Names are cosmetic and
 * deliberately allowed to collide - if names had to be unique, "that name is taken"
 * would tell an outsider that a team by that name exists.
 *
 * <p>Members are stored as UUID -> last known name so teammates can see offline members.
 * Backed by concurrent collections because team chat is handled on Paper's async chat thread.
 */
public final class Team {

    private final UUID id;
    private final Map<UUID, String> members = new ConcurrentHashMap<>();
    private final long createdAt;
    private volatile String name;
    private volatile UUID owner;

    public Team(UUID id, String name, UUID owner, long createdAt) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public UUID owner() {
        return owner;
    }

    public void owner(UUID owner) {
        this.owner = owner;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public long createdAt() {
        return createdAt;
    }

    public Set<UUID> memberIds() {
        return Collections.unmodifiableSet(members.keySet());
    }

    public Map<UUID, String> members() {
        return Collections.unmodifiableMap(members);
    }

    public int size() {
        return members.size();
    }

    public boolean hasMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public String memberName(UUID uuid) {
        String stored = members.get(uuid);
        return (stored == null || stored.isBlank()) ? uuid.toString().substring(0, 8) : stored;
    }

    public void addMember(UUID uuid, String name) {
        members.put(uuid, name);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    /** Keeps the stored name fresh so offline members show up correctly after a rename. */
    public void touchName(UUID uuid, String name) {
        members.computeIfPresent(uuid, (key, old) -> name);
    }
}
