package io.github.aethelreborn.vsm.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Timestamp formatting shared by the audit log, join notices and staff info screens. */
public final class TimeUtil {

    private static final DateTimeFormatter LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    private TimeUtil() {
    }

    /** {@code 2026-10-11 19:55:00} as used by the daily audit log. */
    public static String formatTimestamp(long epochMillis) {
        return LOG.format(Instant.ofEpochMilli(epochMillis));
    }

    /** {@code 2026-10-11} used for log file names. */
    public static String formatDate(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    /** ISO-8601 as stored in {@code staff.yml} (spec 6.5). */
    public static String toIso(long epochMillis) {
        return epochMillis <= 0L ? "" : ISO.format(Instant.ofEpochMilli(epochMillis));
    }

    /** @return epoch millis, or {@code 0} when the text is empty or unparsable. */
    public static long fromIso(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(text.trim()).toEpochMilli();
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    /** Relative "3 days ago" style rendering. */
    public static String relative(long epochMillis, long now) {
        long delta = now - epochMillis;
        if (delta < 0L) {
            return "in the future";
        }
        return TimeParser.format(delta) + " ago";
    }
}