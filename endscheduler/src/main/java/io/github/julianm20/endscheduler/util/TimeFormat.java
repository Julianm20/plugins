package io.github.julianm20.endscheduler.util;

/** Human-readable durations: "2d 5h 13m", "13m 4s", "9s". */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String describe(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0s";
        }

        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            out.append(hours).append("h ");
        }
        // Once we are into days, seconds are noise.
        if (days > 0) {
            out.append(minutes).append("m");
            return out.toString().trim();
        }
        if (hours > 0 || minutes > 0) {
            out.append(minutes).append("m ");
        }
        out.append(seconds).append("s");
        return out.toString().trim();
    }
}
