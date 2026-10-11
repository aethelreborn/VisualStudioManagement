package io.github.aethelreborn.vsm.models;

import java.util.Locale;

/** Punishment kinds handled by the plugin (spec 5). */
public enum PunishmentType {
    WARN(true, false),
    TEMP_WARN(true, false),
    MUTE(true, true),
    TEMP_MUTE(true, true),
    SHADOW_MUTE(true, true),
    IP_MUTE(false, true),
    TEMP_IP_MUTE(false, true),
    KICK(false, false),
    BAN(true, false),
    TEMP_BAN(true, false),
    IP_BAN(false, false),
    TEMP_IP_BAN(false, false),
    NAME_BAN(false, false);

    private final boolean targetUuid;
    private final boolean blocksChat;

    PunishmentType(boolean targetUuid, boolean blocksChat) {
        this.targetUuid = targetUuid;
        this.blocksChat = blocksChat;
    }

    /** {@code false} for the pure IP punishments, which have no account to attach. */
    public boolean usesTargetUuid() {
        return targetUuid;
    }

    public boolean blocksChat() {
        return blocksChat;
    }

    public boolean isIp() {
        return this == IP_BAN || this == TEMP_IP_BAN || this == IP_MUTE || this == TEMP_IP_MUTE;
    }

    public boolean isTemporary() {
        return this == TEMP_WARN || this == TEMP_MUTE || this == TEMP_IP_MUTE
                || this == TEMP_BAN || this == TEMP_IP_BAN;
    }

    public boolean isBan() {
        return this == BAN || this == TEMP_BAN || this == IP_BAN || this == TEMP_IP_BAN || this == NAME_BAN;
    }

    public boolean isMute() {
        return this == MUTE || this == TEMP_MUTE || this == SHADOW_MUTE || this == IP_MUTE || this == TEMP_IP_MUTE;
    }

    public boolean isWarn() {
        return this == WARN || this == TEMP_WARN;
    }

    public boolean isWarning() {
        return isWarn();
    }

    /** Config key under {@code punishments.max-durations}. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the equivalent permanent type, or the type itself when already permanent. */
    public PunishmentType permanentVariant() {
        return switch (this) {
            case TEMP_WARN -> WARN;
            case TEMP_MUTE -> MUTE;
            case TEMP_IP_MUTE -> IP_MUTE;
            case TEMP_BAN -> BAN;
            case TEMP_IP_BAN -> IP_BAN;
            default -> this;
        };
    }

    /** @return the equivalent temporary type where one exists. */
    public PunishmentType temporaryVariant() {
        return switch (this) {
            case WARN -> TEMP_WARN;
            case MUTE, SHADOW_MUTE -> TEMP_MUTE;
            case IP_MUTE -> TEMP_IP_MUTE;
            case BAN -> TEMP_BAN;
            case IP_BAN -> TEMP_IP_BAN;
            default -> this;
        };
    }

    /** @return the punishment type parsed from a config / database string. */
    public static PunishmentType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String text = normalise(raw);
        for (PunishmentType type : values()) {
            if (normalise(type.name()).equals(text)) {
                return type;
            }
        }
        return null;
    }

    /** Strips separators so {@code ipban}, {@code ip-ban} and {@code IP_BAN} all match. */
    private static String normalise(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c != '-' && c != '_' && c != ' ') {
                out.append(c);
            }
        }
        return out.toString();
    }
}