package io.github.julianm20.killstreaks;

import java.util.List;

/** One configured milestone: what to say, play and run when a streak hits {@link #streak()}. */
public final class Milestone {

    private final int streak;
    private final boolean announce;
    private final String announcement;
    private final String killerMessage;
    private final String sound;
    private final float volume;
    private final float pitch;
    private final List<String> commands;

    public Milestone(int streak, boolean announce, String announcement, String killerMessage,
                     String sound, float volume, float pitch, List<String> commands) {
        this.streak = streak;
        this.announce = announce;
        this.announcement = announcement;
        this.killerMessage = killerMessage;
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.commands = List.copyOf(commands);
    }

    public int streak() {
        return streak;
    }

    public boolean announce() {
        return announce;
    }

    public String announcement() {
        return announcement;
    }

    public String killerMessage() {
        return killerMessage;
    }

    public String sound() {
        return sound;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    /** Console commands run as rewards. Supports %player%, %streak%, %victim%. */
    public List<String> commands() {
        return commands;
    }
}
