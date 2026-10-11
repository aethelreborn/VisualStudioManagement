package io.github.aethelreborn.vsm.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Duration parsing and formatting for every punishment / temporary state.
 *
 * <p>Supported units: {@code s}, {@code m}, {@code h}, {@code d}, {@code w}, {@code mo} (30 days) and
 * {@code y} (365 days). Combined forms such as {@code 1d12h} or {@code 2w3d} are supported.
 * {@link #PERMANENT} ({@code -1}) is returned for {@code perm}, {@code permanent} and {@code forever}.
 */
public final class TimeParser {

    public static final long PERMANENT = -1L;

    private static final long UNIT_YEAR = 365L * 24L * 60L * 60L * 1000L;
    private static final long UNIT_MONTH = 30L * 24L * 60L * 60L * 1000L;
    private static final long UNIT_WEEK = 7L * 24L * 60L * 60L * 1000L;
    private static final long UNIT_DAY = 24L * 60L * 60L * 1000L;
    private static final long UNIT_HOUR = 60L * 60L * 1000L;
    private static final long UNIT_MINUTE = 60L * 1000L;
    private static final long UNIT_SECOND = 1000L;

    private TimeParser() {
    }

    /** @return {@code true} when the token means "no expiry". */
    public static boolean isPermanentToken(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        return t.equals("perm") || t.equals("permanent") || t.equals("forever") || t.equals("-1");
    }

    /**
     * Parses a duration into milliseconds.
     *
     * @return the duration in milliseconds, or {@link #PERMANENT} for a permanent token
     * @throws IllegalArgumentException when the input is empty, malformed, zero, negative or overflows
     */
    public static long parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("duration is null");
        }
        String raw = input.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("duration is empty");
        }
        if (isPermanentToken(raw)) {
            return PERMANENT;
        }

        long total = 0L;
        boolean matched = false;
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                number.append(c);
                continue;
            }
            if (c == ' ' || c == '_') {
                continue;
            }
            if (number.length() == 0) {
                throw new IllegalArgumentException("no number before unit '" + c + "' in '" + input + "'");
            }
            long value;
            try {
                value = Long.parseLong(number.toString());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("number too large in '" + input + "'", ex);
            }
            number.setLength(0);
            matched = true;

            String unit = unitAt(raw, i);
            long unitMs = unitMillis(unit);
            long add = Math.multiplyExact(value, unitMs);
            total = Math.addExact(total, add);
            i += unit.length() - 1;
        }

        if (number.length() > 0) {
            throw new IllegalArgumentException("trailing number without unit in '" + input + "'");
        }
        if (!matched) {
            throw new IllegalArgumentException("no duration found in '" + input + "'");
        }
        if (total <= 0L) {
            throw new IllegalArgumentException("duration must be greater than zero in '" + input + "'");
        }
        return total;
    }

    /** Parses a duration, returning {@code fallback} instead of throwing. */
    public static long parseOr(String input, long fallback) {
        try {
            return parse(input);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static String unitAt(String raw, int index) {
        if (raw.startsWith("mo", index)) {
            return "mo";
        }
        if (raw.startsWith("ms", index)) {
            throw new IllegalArgumentException("milliseconds are not supported: '" + raw + "'");
        }
        char c = raw.charAt(index);
        return String.valueOf(c);
    }

    private static long unitMillis(String unit) {
        return switch (unit) {
            case "s" -> UNIT_SECOND;
            case "m" -> UNIT_MINUTE;
            case "h" -> UNIT_HOUR;
            case "d" -> UNIT_DAY;
            case "w" -> UNIT_WEEK;
            case "mo" -> UNIT_MONTH;
            case "y" -> UNIT_YEAR;
            default -> throw new IllegalArgumentException("unknown duration unit '" + unit + "'");
        };
    }

    /** @return {@code true} when the duration is well formed. */
    public static boolean valid(String input) {
        try {
            return parse(input) != PERMANENT || isPermanentToken(input);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Human readable rendering such as {@code 1 day, 12 hours}. */
    public static String format(long millis) {
        if (millis == PERMANENT) {
            return "permanent";
        }
        if (millis < 0L) {
            return "unknown";
        }
        if (millis == 0L) {
            return "0 seconds";
        }
        long[] units = {UNIT_YEAR, UNIT_MONTH, UNIT_WEEK, UNIT_DAY, UNIT_HOUR, UNIT_MINUTE, UNIT_SECOND};
        String[] names = {"year", "month", "week", "day", "hour", "minute", "second"};
        StringBuilder out = new StringBuilder();
        long remaining = millis;
        for (int i = 0; i < units.length; i++) {
            long value = remaining / units[i];
            if (value <= 0L) {
                continue;
            }
            remaining -= value * units[i];
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(value).append(' ').append(names[i]);
            if (value != 1L) {
                out.append('s');
            }
        }
        return out.toString();
    }

    /** Short rendering such as {@code 30m}, used inside broadcasts. */
    public static String formatShort(long millis) {
        if (millis == PERMANENT) {
            return "permanent";
        }
        if (millis <= 0L) {
            return "0s";
        }
        if (millis % UNIT_YEAR == 0L) {
            return (millis / UNIT_YEAR) + "y";
        }
        if (millis % UNIT_MONTH == 0L) {
            return (millis / UNIT_MONTH) + "mo";
        }
        if (millis % UNIT_WEEK == 0L) {
            return (millis / UNIT_WEEK) + "w";
        }
        if (millis % UNIT_DAY == 0L) {
            return (millis / UNIT_DAY) + "d";
        }
        if (millis % UNIT_HOUR == 0L) {
            return (millis / UNIT_HOUR) + "h";
        }
        if (millis % UNIT_MINUTE == 0L) {
            return (millis / UNIT_MINUTE) + "m";
        }
        return (millis / UNIT_SECOND) + "s";
    }

    /** Remaining time rendered for a punishment that has not expired yet. */
    public static String formatRemaining(long expiresAt, long now) {
        if (expiresAt == PERMANENT) {
            return "Never";
        }
        long remaining = expiresAt - now;
        if (remaining <= 0L) {
            return "Expired";
        }
        return format(remaining);
    }

    public static long secondsToMillis(long seconds) {
        return TimeUnit.SECONDS.toMillis(seconds);
    }

    public static long minutesToMillis(long minutes) {
        return TimeUnit.MINUTES.toMillis(minutes);
    }
}