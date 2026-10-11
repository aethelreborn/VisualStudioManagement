package io.github.aethelreborn.vsm.integration.importers;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads the LiteBans SQLite database (spec 18.5). Types, original timestamps and staff names are
 * preserved; IP bans are mapped to {@code vsm.ipban}.
 */
public final class LiteBansImporter extends ImportSource {

    private final Path database;

    public LiteBansImporter(VisualStudioManagement plugin, Path database) {
        super(plugin);
        this.database = database;
    }

    @Override
    public String id() {
        return "litebans";
    }

    @Override
    protected List<Punishment> read() throws IOException {
        List<Punishment> out = new ArrayList<>();
        if (!java.nio.file.Files.exists(database)) {
            logger.warning("LiteBans database not found at " + database + ".");
            return out;
        }
        String url = "jdbc:sqlite:" + database.toAbsolutePath();
        try (var connection = java.sql.DriverManager.getConnection(url);
             var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT * FROM litebans_bans")) {
            while (rs.next()) {
                Punishment punishment = new Punishment();
                punishment.id("LB-" + rs.getLong("id"));
                punishment.targetUuid(uuid(rs, "uuid"));
                punishment.targetName(rs.getString("name"));
                punishment.staffUuid(uuid(rs, "staff_uuid"));
                punishment.staffName(rs.getString("staff"));
                punishment.type(mapType(rs.getString("type")));
                if (punishment.type() == null) {
                    punishment.type(PunishmentType.BAN);
                }
                punishment.targetIp(rs.getString("ip"));
                punishment.reason(rs.getString("reason"));
                long now = System.currentTimeMillis();
                punishment.issuedAt(parseTimestamp(rs.getString("started"), now));
                long until = parseTimestamp(rs.getString("executed"), -1L);
                punishment.expiresAt(until > 0L ? until : io.github.aethelreborn.vsm.util.TimeParser.PERMANENT);
                punishment.active("true".equalsIgnoreCase(rs.getString("active")));
                punishment.silent("true".equalsIgnoreCase(rs.getString("silent")));
                punishment.server(plugin.vsmConfig().serverName());
                out.add(punishment);
            }
        } catch (java.sql.SQLException ex) {
            throw new IOException("LiteBans database could not be read: " + ex.getMessage(), ex);
        }
        return out;
    }

    private static UUID uuid(java.sql.ResultSet rs, String column) {
        try {
            return parseUuid(rs.getString(column));
        } catch (java.sql.SQLException ex) {
            return null;
        }
    }
}