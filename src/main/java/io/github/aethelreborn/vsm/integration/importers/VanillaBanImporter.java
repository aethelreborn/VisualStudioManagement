package io.github.aethelreborn.vsm.integration.importers;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.util.Json;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads {@code banned-players.json} and {@code banned-ips.json} (spec 18.5). */
public final class VanillaBanImporter extends ImportSource {

    private final Path serverRoot;

    public VanillaBanImporter(VisualStudioManagement plugin, Path serverRoot) {
        super(plugin);
        this.serverRoot = serverRoot;
    }

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    protected List<Punishment> read() throws IOException {
        List<Punishment> out = new ArrayList<>();
        out.addAll(readAccounts());
        out.addAll(readIps());
        return out;
    }

    private List<Punishment> readAccounts() throws IOException {
        List<Punishment> out = new ArrayList<>();
        Path file = serverRoot.resolve("banned-players.json");
        if (!Files.exists(file)) {
            return out;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        long now = System.currentTimeMillis();
        for (String entry : objects(json)) {
            String name = stringValue(entry, "name");
            String uuidText = stringValue(entry, "uuid");
            String created = stringValue(entry, "created");
            String expires = stringValue(entry, "expires");
            String source = stringValue(entry, "source");
            Punishment punishment = new Punishment();
            punishment.id("VB-A-" + (name == null ? out.size() : name));
            punishment.targetName(name);
            punishment.targetUuid(parseUuid(uuidText));
            punishment.staffName("console");
            punishment.type(PunishmentType.BAN);
            punishment.reason(source == null ? "Imported from vanilla" : source);
            punishment.issuedAt(isoToMillis(created, now));
            punishment.expiresAt(isoToMillis(expires, TimeParser.PERMANENT));
            punishment.active(punishment.expiresAt() == TimeParser.PERMANENT
                    || punishment.expiresAt() > System.currentTimeMillis());
            punishment.server(plugin.vsmConfig().serverName());
            out.add(punishment);
        }
        return out;
    }

    private List<Punishment> readIps() throws IOException {
        List<Punishment> out = new ArrayList<>();
        Path file = serverRoot.resolve("banned-ips.json");
        if (!Files.exists(file)) {
            return out;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        long now = System.currentTimeMillis();
        for (String entry : objects(json)) {
            String ip = stringValue(entry, "ip");
            String created = stringValue(entry, "created");
            String expires = stringValue(entry, "expires");
            String source = stringValue(entry, "source");
            Punishment punishment = new Punishment();
            punishment.id("VB-I-" + (ip == null ? out.size() : ip.replace('.', '-')));
            punishment.targetIp(plugin.ipService().store(ip));
            punishment.staffName("console");
            punishment.type(PunishmentType.IP_BAN);
            punishment.reason(source == null ? "Imported from vanilla" : source);
            punishment.issuedAt(isoToMillis(created, now));
            punishment.expiresAt(isoToMillis(expires, TimeParser.PERMANENT));
            punishment.active(punishment.expiresAt() == TimeParser.PERMANENT
                    || punishment.expiresAt() > System.currentTimeMillis());
            punishment.server(plugin.vsmConfig().serverName());
            out.add(punishment);
        }
        return out;
    }

    /** Extracts each top level {@code {...}} object without needing a JSON library. */
    private static List<String> objects(String json) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static String stringValue(String json, String key) {
        String needle = "\"" + key + "\"";
        int index = json.indexOf(needle);
        if (index < 0) {
            return null;
        }
        int colon = json.indexOf(':', index + needle.length());
        if (colon < 0) {
            return null;
        }
        int open = json.indexOf('"', colon + 1);
        if (open < 0) {
            return null;
        }
        int close = json.indexOf('"', open + 1);
        return close < 0 ? null : json.substring(open + 1, close);
    }

    private static long isoToMillis(String iso, long fallback) {
        if (iso == null || iso.isBlank()) {
            return fallback;
        }
        long parsed = io.github.aethelreborn.vsm.util.TimeUtil.fromIso(iso);
        return parsed > 0L ? parsed : fallback;
    }

    /** Kept so the JSON helper is referenced and future embeds can reuse it. */
    public String quote(String value) {
        return Json.quote(value);
    }
}