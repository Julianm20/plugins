package io.github.julianm20.smpstats.api;

/**
 * How a raw stored number is rendered.
 *
 * <p>The odd-looking units come from vanilla Minecraft, which stores playtime in ticks,
 * distances in centimetres and damage in tenths of a heart. Storing the raw value and
 * formatting on display keeps our numbers identical to vanilla's.
 */
public enum StatFormat {

    /** Plain count: 1234 -> "1,234". */
    NUMBER,

    /** Vanilla ticks: 1728000 -> "1d 0h". */
    DURATION_TICKS,

    /** Seconds: 5400 -> "1h 30m". */
    DURATION_SECONDS,

    /** Vanilla centimetres: 150000 -> "1.5 km". */
    DISTANCE_CM,

    /** Vanilla tenths of a heart: 4210 -> "421 HP". */
    DAMAGE_TENTHS,

    /** Currency: 42500 -> "$42,500". */
    MONEY,

    /** Raw text passthrough, for things like a weapon name stored elsewhere. */
    TEXT
}
