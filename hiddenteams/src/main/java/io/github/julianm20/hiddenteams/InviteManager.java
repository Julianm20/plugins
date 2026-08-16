package io.github.julianm20.hiddenteams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending invitations, in memory only.
 *
 * <p>Invites expire, so there is nothing worth persisting - and not persisting them means
 * an invite can never outlive the team it pointed at.
 */
public final class InviteManager {

    /** invitee -> inviter -> invite */
    private final Map<UUID, Map<UUID, Invite>> pending = new ConcurrentHashMap<>();

    public record Invite(UUID teamId, UUID inviter, String inviterName, long expiresAt) {
        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    public void add(UUID invitee, UUID inviter, String inviterName, UUID teamId, long expiryMillis) {
        pending.computeIfAbsent(invitee, key -> new ConcurrentHashMap<>())
                .put(inviter, new Invite(teamId, inviter, inviterName,
                        System.currentTimeMillis() + expiryMillis));
    }

    /** Live invites for a player, expired entries pruned. */
    public List<Invite> forPlayer(UUID invitee) {
        Map<UUID, Invite> byInviter = pending.get(invitee);
        if (byInviter == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        byInviter.values().removeIf(invite -> invite.expired(now));
        if (byInviter.isEmpty()) {
            pending.remove(invitee);
            return List.of();
        }
        return new ArrayList<>(byInviter.values());
    }

    /** The single live invite from a specific inviter, or null. */
    public Invite find(UUID invitee, UUID inviter) {
        Map<UUID, Invite> byInviter = pending.get(invitee);
        if (byInviter == null) {
            return null;
        }
        Invite invite = byInviter.get(inviter);
        if (invite == null) {
            return null;
        }
        if (invite.expired(System.currentTimeMillis())) {
            byInviter.remove(inviter);
            return null;
        }
        return invite;
    }

    public boolean hasPending(UUID invitee, UUID inviter) {
        return find(invitee, inviter) != null;
    }

    public void remove(UUID invitee, UUID inviter) {
        Map<UUID, Invite> byInviter = pending.get(invitee);
        if (byInviter != null) {
            byInviter.remove(inviter);
            if (byInviter.isEmpty()) {
                pending.remove(invitee);
            }
        }
    }

    public void clearFor(UUID invitee) {
        pending.remove(invitee);
    }

    /** Drops every invite pointing at a team, e.g. when it is disbanded. */
    public void clearForTeam(UUID teamId) {
        for (Map.Entry<UUID, Map<UUID, Invite>> entry : pending.entrySet()) {
            entry.getValue().values().removeIf(invite -> invite.teamId().equals(teamId));
            if (entry.getValue().isEmpty()) {
                pending.remove(entry.getKey());
            }
        }
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Invite>> entry : pending.entrySet()) {
            entry.getValue().values().removeIf(invite -> invite.expired(now));
            if (entry.getValue().isEmpty()) {
                pending.remove(entry.getKey());
            }
        }
    }
}
