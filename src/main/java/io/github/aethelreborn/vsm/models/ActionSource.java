package io.github.aethelreborn.vsm.models;

import java.util.Locale;

/** Where a staff action came from; recorded on every audit entry (spec 12). */
public enum ActionSource {
    COMMAND,
    GUI,
    CONSOLE,
    API,
    AUTOMOD;

    public static ActionSource fromString(String raw) {
        if (raw == null) {
            return COMMAND;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return COMMAND;
        }
    }
}