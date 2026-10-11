package io.github.aethelreborn.vsm.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Versioned schema migrations (spec 15.2). The version lives in {@code schema_version} so upgrades never
 * require manual SQL, and every step is idempotent.
 */
public final class Migrations {

    /** Current schema version; bump this whenever a new migration is appended. */
    public static final int CURRENT_VERSION = 1;

    private Migrations() {
    }

    /** SQL dialect differences between SQLite and MySQL (spec 15.2). */
    public record Dialect(boolean autoIncrement, boolean partialIndex) {

        public static Dialect sqlite() {
            return new Dialect(false, false);
        }

        public static Dialect mysql() {
            return new Dialect(true, true);
        }
    }

    /**
     * Creates every table and index for a fresh database and records the schema version.
     * Safe to call on an existing database.
     */
    public static void applyBaseline(java.sql.Connection connection, Dialect dialect, Logger logger)
            throws java.sql.SQLException {
        try (java.sql.Statement statement = connection.createStatement()) {
            for (String sql : baselineStatements(dialect)) {
                statement.executeUpdate(sql);
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            try (java.sql.ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + CURRENT_VERSION + ")");
                }
            }
        } catch (java.sql.SQLException ex) {
            logger.severe("Could not create the database schema: " + ex.getMessage());
            throw ex;
        }
    }

    private static List<String> baselineStatements(Dialect dialect) {
        List<String> statements = new ArrayList<>();
        statements.add("""
                CREATE TABLE IF NOT EXISTS staff (
                  uuid TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  rank TEXT NOT NULL,
                  added_by TEXT,
                  added_at INTEGER,
                  last_seen INTEGER,
                  pin_hash TEXT,
                  locked_ip_hash TEXT
                )""");
        statements.add("""
                CREATE TABLE IF NOT EXISTS punishments (
                  id TEXT PRIMARY KEY,
                  target_uuid TEXT,
                  target_name TEXT,
                  target_ip TEXT,
                  staff_uuid TEXT,
                  staff_name TEXT,
                  type TEXT NOT NULL,
                  reason TEXT,
                  issued_at INTEGER NOT NULL,
                  expires_at INTEGER,
                  active INTEGER NOT NULL,
                  removed_by TEXT,
                  removed_at INTEGER,
                  removal_reason TEXT,
                  server TEXT,
                  silent INTEGER DEFAULT 0,
                  evidence TEXT,
                  template_id TEXT,
                  ladder_step INTEGER,
                  parent_id TEXT,
                  scope TEXT DEFAULT ''
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_pun_target ON punishments(target_uuid, active)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_pun_ip ON punishments(target_ip, active)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_pun_staff ON punishments(staff_uuid)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_pun_exp ON punishments(expires_at)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_pun_parent ON punishments(parent_id)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS ip_history (
                  uuid TEXT NOT NULL,
                  ip TEXT NOT NULL,
                  first_seen INTEGER NOT NULL,
                  last_seen INTEGER NOT NULL,
                  times_seen INTEGER NOT NULL DEFAULT 1,
                  PRIMARY KEY (uuid, ip)
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_ip_lookup ON ip_history(ip)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS ip_allow (
                  uuid TEXT PRIMARY KEY,
                  added_by TEXT,
                  added_at INTEGER
                )""");
        statements.add("""
                CREATE TABLE IF NOT EXISTS notes (
                  id TEXT PRIMARY KEY,
                  target_uuid TEXT NOT NULL,
                  target_name TEXT,
                  author_uuid TEXT,
                  author_name TEXT,
                  text TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  pinned INTEGER DEFAULT 0
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_notes_target ON notes(target_uuid)");
        String reportsId = dialect.autoIncrement() ? "id INTEGER PRIMARY KEY AUTO_INCREMENT" : "id INTEGER PRIMARY KEY";
        statements.add("""
                CREATE TABLE IF NOT EXISTS reports (
                  %s,
                  reporter_uuid TEXT NOT NULL,
                  reporter_name TEXT NOT NULL,
                  target_uuid TEXT NOT NULL,
                  target_name TEXT NOT NULL,
                  reason TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  status TEXT NOT NULL,
                  priority TEXT DEFAULT 'NORMAL',
                  assigned_staff TEXT,
                  note TEXT,
                  evidence TEXT,
                  claimed_at INTEGER,
                  closed_at INTEGER
                )""".formatted(reportsId));
        statements.add("CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status)");
        statements.add("CREATE INDEX IF NOT EXISTS idx_reports_target ON reports(target_uuid)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                  id TEXT PRIMARY KEY,
                  staff_uuid TEXT,
                  staff_name TEXT,
                  action TEXT NOT NULL,
                  target_uuid TEXT,
                  target_name TEXT,
                  details TEXT,
                  source TEXT,
                  timestamp INTEGER NOT NULL,
                  prev_hash TEXT,
                  hash TEXT
                )""");
        statements.add("CREATE INDEX IF NOT EXISTS idx_audit_staff ON audit_logs(staff_uuid, timestamp)");
        statements.add("""
                CREATE TABLE IF NOT EXISTS saved_locations (
                  player TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  world TEXT,
                  x REAL,
                  y REAL,
                  z REAL,
                  yaw REAL,
                  pitch REAL,
                  saved_at INTEGER,
                  cause TEXT,
                  PRIMARY KEY (player, kind)
                )""");
        statements.add("""
                CREATE TABLE IF NOT EXISTS name_history (
                  uuid TEXT,
                  name TEXT,
                  first_seen INTEGER,
                  PRIMARY KEY (uuid, name)
                )""");
        statements.add("""
                CREATE TABLE IF NOT EXISTS watchlist (
                  uuid TEXT PRIMARY KEY,
                  name TEXT,
                  added_by TEXT,
                  reason TEXT,
                  added_at INTEGER
                )""");
        statements.add("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        return statements;
    }

    /** Reads the stored schema version, creating the table when it is missing. */
    public static int readVersion(java.sql.Connection connection) throws java.sql.SQLException {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            try (java.sql.ResultSet rs = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /** Writes the current version marker. */
    public static void writeVersion(java.sql.Connection connection) throws java.sql.SQLException {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_version");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + CURRENT_VERSION + ")");
        }
    }

    /** Removes inactive punishment rows older than the cutoff. */
    public static int prunePunishments(java.sql.Connection connection, long cutoff, Logger logger)
            throws java.sql.SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM punishments WHERE active = 0 AND removed_at > 0 AND removed_at < ?")) {
            statement.setLong(1, cutoff);
            int deleted = statement.executeUpdate();
            if (logger != null && deleted > 0) {
                logger.info("Pruned " + deleted + " inactive punishment record(s).");
            }
            return deleted;
        }
    }

    /** YAML storage tracks a schema version in the same way. */
    public static int readYamlVersion(ConfigurationSection section) {
        return section == null ? 0 : section.getInt("schema-version", 0);
    }

    public static void writeYamlVersion(YamlConfiguration configuration) {
        configuration.set("schema-version", CURRENT_VERSION);
    }

    /** Simple in-memory representation used by the YAML export. */
    public static Map<String, Object> emptyExport() {
        return new LinkedHashMap<>();
    }
}