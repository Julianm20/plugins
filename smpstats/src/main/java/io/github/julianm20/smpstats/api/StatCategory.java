package io.github.julianm20.smpstats.api;

/** Which section of the /stats profile a statistic appears under. */
public enum StatCategory {

    /** Fighting other players. */
    COMBAT("Combat"),

    /** Everything else you do in the world. */
    GAMEPLAY("Gameplay"),

    /** Fed in by other plugins - bounties, KOTH, wars, contracts. */
    SERVER("Server");

    private final String displayName;

    StatCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
