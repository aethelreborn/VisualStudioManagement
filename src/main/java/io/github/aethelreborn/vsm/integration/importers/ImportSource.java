package io.github.aethelreborn.vsm.integration.importers;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Base class for the importers (spec 18.5). Every importer runs off the main thread, supports
 * {@code --dry-run} and logs a summary.
 */
public abstract class ImportSource {

    protected final VisualStudioManagement plugin;
    protected final Logger logger;

    protected ImportSource(VisualStudioManagement plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Identifier used by {@code /vsm import <id>}. */
    public abstract String id();

    /** Reads the source and returns the punishments it would create. */
    protected abstract List<Punishment> read() throws IOException;

    /** @return a human readable summary for the log. */
    public String run(boolean dryRun) {
        List<Punishment> punishments;
        try {
            punishments = read();
        } catch (IOException | RuntimeException ex) {
            logger.severe("Import from " + id() + " failed: " + ex.getMessage());
            return "Import from " + id() + " failed: " + ex.getMessage();
        }
        int created = 0;
        int skipped = 0;
        for (Punishment punishment : punishments) {
            if (punishment.id() == null || punishment.id().isBlank()) {
                punishment.id(plugin.vsmConfig().idPrefix() + "-IMP" + created);
            }
            if (plugin.punishmentCache().byId(punishment.id()).isPresent()) {
                skipped++;
                continue;
            }
            if (dryRun) {
                created++;
                continue;
            }
            plugin.punishmentService().store(punishment);
            created++;
        }
        String summary = "Imported %d punishment(s) from %s (%d skipped, dry-run: %s)"
                .formatted(created, id(), skipped, dryRun);
        logger.info(summary);
        return summary;
    }

    /** Reads a UTF-8 text file from a plugin data folder. */
    protected static List<String> readLines(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    protected static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    protected static long parseTimestamp(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Maps a foreign reason string onto one of our types. */
    protected static PunishmentType mapType(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toLowerCase(Locale.ROOT);
        if (text.contains("ipban") || text.contains("ip_ban") || text.contains("ipban")) {
            return text.contains("temp") ? PunishmentType.TEMP_IP_BAN : PunishmentType.IP_BAN;
        }
        if (text.contains("ban")) {
            return text.contains("temp") ? PunishmentType.TEMP_BAN : PunishmentType.BAN;
        }
        if (text.contains("mute")) {
            return text.contains("shadow") ? PunishmentType.SHADOW_MUTE
                    : text.contains("temp") || text.contains("tempmute") ? PunishmentType.TEMP_MUTE
                    : PunishmentType.MUTE;
        }
        if (text.contains("warn")) {
            return text.contains("temp") ? PunishmentType.TEMP_WARN : PunishmentType.WARN;
        }
        if (text.contains("kick")) {
            return PunishmentType.KICK;
        }
        return null;
    }

    /** Splits a CSV line honouring quoted fields. */
    protected static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        out.add(field.toString().trim());
        return out;
    }
}