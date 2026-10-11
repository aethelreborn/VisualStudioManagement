package io.github.aethelreborn.vsm.integration.importers;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads the AdvancedBan MySQL/SQLite database (spec 18.5). */
public final class AdvancedBanImporter extends ImportSource {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public AdvancedBanImporter(VisualStudioManagement plugin, String jdbcUrl, String user, String password) {
        super(plugin);
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    @Override
    public String id() {
        return "advancedban";
    }

    @Override
    protected List<Punishment> read() throws IOException {
        List<Punishment> out = new ArrayList<>();
        try (var connection = java.sql.DriverManager.getConnection(jdbcUrl, user, password);
             var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM punishments")) {
            while (rs.next()) {
                Punishment punishment = new Punishment();
                punishment.id("AB-" + safeString(rs, "id"));
                punishment.targetUuid(parseUuid(safeString(rs, "uuid")));
                punishment.targetName(safeString(rs, "name"));
                punishment.staffName(orDefault(safeString(rs, "operator"), "console"));
                punishment.type(mapType(safeString(rs, "type")));
                if (punishment.type() == null) {
                    punishment.type(PunishmentType.BAN);
                }
                punishment.targetIp(safeString(rs, "ip"));
                punishment.reason(orDefault(safeString(rs, "reason"), "Imported from AdvancedBan"));
                long now = System.currentTimeMillis();
                long issued = safeLong(rs, "datetime", now);
                long removed = safeLong(rs, "removed", -1L);
                punishment.issuedAt(issued);
                punishment.active(removed <= 0L);
                punishment.expiresAt(TimeParser.PERMANENT);
                punishment.server(plugin.vsmConfig().serverName());
                out.add(punishment);
            }
        } catch (SQLException ex) {
            throw new IOException("AdvancedBan database could not be read: " + ex.getMessage(), ex);
        }
        return out;
    }

    private static String safeString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            return null;
        }
    }

    private static long safeLong(ResultSet rs, String column, long fallback) {
        try {
            return rs.getLong(column);
        } catch (SQLException ex) {
            return fallback;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}