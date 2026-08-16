package io.github.julianm20.killstreaks;

import java.util.UUID;

/** Persistent per-player streak record. */
public final class PlayerStreak {

    private final UUID uuid;
    private String name;
    private int current;
    private int best;
    private int kills;
    private int deaths;

    public PlayerStreak(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public PlayerStreak(UUID uuid, String name, int current, int best, int kills, int deaths) {
        this.uuid = uuid;
        this.name = name;
        this.current = current;
        this.best = best;
        this.kills = kills;
        this.deaths = deaths;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    public void name(String name) {
        this.name = name;
    }

    public int current() {
        return current;
    }

    public int best() {
        return best;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    /** Increments the streak by one kill and returns the new streak value. */
    public int addKill() {
        kills++;
        current++;
        if (current > best) {
            best = current;
        }
        return current;
    }

    /** Counts a kill toward lifetime stats without advancing the streak (anti-farm). */
    public void addKillOnly() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    /** Resets the current streak and returns the streak value that was lost. */
    public int resetStreak() {
        int lost = current;
        current = 0;
        return lost;
    }

    /** Admin override. Also bumps the personal best if the new value is higher. */
    public void setCurrent(int value) {
        this.current = Math.max(0, value);
        if (this.current > best) {
            best = this.current;
        }
    }

    public void setBest(int value) {
        this.best = Math.max(0, value);
    }
}
