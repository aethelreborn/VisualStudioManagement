package io.github.aethelreborn.vsm.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** UUID helpers, including the offline-mode UUID algorithm used by servers without Mojang auth. */
public final class UuidUtil {

    private static final String OFFLINE_PREFIX = "OfflinePlayer:";

    private UuidUtil() {
    }

    /** The UUID a player would receive on an offline-mode server. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes((OFFLINE_PREFIX + name).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isOfflineUuid(UUID uuid, String name) {
        return uuid != null && name != null && uuid.equals(offlineUuid(name));
    }

    /** @return {@code true} when the text parses as a UUID. */
    public static boolean isUuid(String input) {
        if (input == null) {
            return false;
        }
        try {
            UUID.fromString(input.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static UUID parseOrNull(String input) {
        if (!isUuid(input)) {
            return null;
        }
        return UUID.fromString(input.trim());
    }

    /** Trims and lower-cases a player name the way Mojang profiles are stored. */
    public static String normaliseName(String name) {
        return name == null ? null : name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}