package io.github.julianm20.endscheduler;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns an {@code open-time} config value into a concrete instant.
 *
 * <p>Two accepted forms:
 * <ul>
 *   <li>{@code "SUNDAY 18:00"} - the next occurrence of that weekday and time</li>
 *   <li>{@code "2026-08-23 18:00"} - one exact date and time</li>
 * </ul>
 *
 * <p>The resolved instant is stored in state.yml, so a restart never re-resolves it and
 * never pushes the opening a week into the future.
 */
public final class OpenTimeParser {

    private static final Pattern ABSOLUTE =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}$");
    private static final DateTimeFormatter ABSOLUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private OpenTimeParser() {
    }

    /**
     * @param spec the raw config value
     * @param zone the configured timezone
     * @param from the moment to resolve relative to (for the weekday form)
     * @return the instant the End should open
     * @throws IllegalArgumentException if the spec cannot be understood
     */
    public static Instant resolve(String spec, ZoneId zone, Instant from) {
        String trimmed = spec == null ? "" : spec.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("open-time is empty");
        }

        if (ABSOLUTE.matcher(trimmed).matches()) {
            // Accept both "2026-08-23 18:00" and "2026-08-23T18:00".
            LocalDateTime dateTime = LocalDateTime.parse(trimmed.replace('T', ' '), ABSOLUTE_FORMAT);
            return dateTime.atZone(zone).toInstant();
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected 'DAY HH:mm' or 'yyyy-MM-dd HH:mm'");
        }

        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown day of week '" + parts[0] + "'");
        }

        LocalTime time;
        try {
            time = LocalTime.parse(parts[1]);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("unknown time '" + parts[1] + "', expected HH:mm");
        }

        ZonedDateTime now = from.atZone(zone);
        ZonedDateTime candidate = now.with(TemporalAdjusters.nextOrSame(day))
                .with(time)
                .withSecond(0)
                .withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toInstant();
    }
}
