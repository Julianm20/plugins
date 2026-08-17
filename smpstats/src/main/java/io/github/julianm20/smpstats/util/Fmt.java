package io.github.julianm20.smpstats.util;

import io.github.julianm20.smpstats.api.StatFormat;

import java.util.Locale;

/** Renders raw stored numbers into what a player sees. */
public final class Fmt {

    private Fmt() {
    }

    public static String value(long raw, StatFormat format) {
        return switch (format) {
            case NUMBER -> count(raw);
            case DURATION_TICKS -> duration(raw / 20L);
            case DURATION_SECONDS -> duration(raw);
            case DISTANCE_CM -> distance(raw);
            case DAMAGE_TENTHS -> String.format(Locale.ROOT, "%,.1f HP", raw / 10.0D);
            case MONEY -> "$" + count(raw);
            case TEXT -> String.valueOf(raw);
        };
    }

    public static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String ratio(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** Centimetres to a readable distance: "1.5 km", "420 m". */
    public static String distance(long centimetres) {
        double metres = centimetres / 100.0D;
        if (metres >= 1000.0D) {
            return String.format(Locale.ROOT, "%,.1f km", metres / 1000.0D);
        }
        return String.format(Locale.ROOT, "%,.0f m", metres);
    }

    /** Seconds to "4d 7h", "1h 30m", "45s". */
    public static String duration(long totalSeconds) {
        if (totalSeconds <= 0L) {
            return "0m";
        }
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m";
        }
        return totalSeconds + "s";
    }

    /** "netherite_sword" -> "Netherite Sword". */
    public static String prettyMaterial(String material) {
        if (material == null || material.isBlank()) {
            return "";
        }
        String[] words = material.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
