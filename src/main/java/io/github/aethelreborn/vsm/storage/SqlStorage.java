package io.github.aethelreborn.vsm.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.aethelreborn.vsm.models.AuditEntry;
import io.github.aethelreborn.vsm.models.IpRecord;
import io.github.aethelreborn.vsm.models.PlayerNote;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.Report;
import io.github.aethelreborn.vsm.models.SavedLocation;
import io.github.aethelreborn.vsm.models.StaffMember;
import io.github.aethelreborn.vsm.models.WatchEntry;
import io.github.aethelreborn.vsm.util.Json;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Shared JDBC implementation for SQLite and MySQL (spec 15.2). Every statement is parameterised, every
 * call runs on a private daemon thread pool so no database work can ever touch the main thread.
 */
public abstract class SqlStorage implements StorageProvider {

    protected final Logger logger;
    protected final Migrations.Dialect dialect;
    private HikariDataSource dataSource;
    private ExecutorService executor;

    protected SqlStorage(Logger logger, Migrations.Dialect dialect) {
        this.logger = logger;
        this.dialect = dialect;
    }

    /** Fills in the Hikari settings; implemented per backend. */
    protected abstract void configure(HikariConfig config);

    /** Runs after the pool is up and before migrations. */
    protected void afterConnect() throws SQLException {
    }

    @Override
    public CompletableFuture<Void> init() {
        return supply(() -> {
            HikariConfig config = new HikariConfig();
            config.setPoolName("vsm-storage");
            config.setConnectionTimeout(15_000L);
            config.setInitializationFailTimeout(20_000L);
            configure(config);
            dataSource = new HikariDataSource(config);
            executor = Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                    runnable -> {
                        Thread thread = new Thread(runnable, "VSM-Storage");
                        thread.setDaemon(true);
                        return thread;
                    });
            try (Connection connection = dataSource.getConnection()) {
                afterConnect();
                Migrations.applyBaseline(connection, dialect, logger);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return supply(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
            return null;
        });
    }

    protected DataSource dataSource() {
        return dataSource;
    }

    private <T> CompletableFuture<T> supply(SqlCallable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                future.complete(callable.call());
            } catch (Throwable throwable) {
                logger.severe("Storage operation failed: " + throwable.getMessage());
                future.completeExceptionally(throwable);
            }
        };
        ExecutorService pool = executor;
        if (pool == null || pool.isShutdown()) {
            task.run();
        } else {
            pool.execute(task);
        }
        return future;
    }

    @FunctionalInterface
    protected interface SqlCallable<T> {
        T call() throws SQLException;
    }

    protected <T> T withConnection(SqlFunction<T> function) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return function.apply(connection);
        }
    }

    @FunctionalInterface
    protected interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    // -------------------------------------------------------------- staff
    @Override
    public CompletableFuture<Map<UUID, StaffMember>> loadStaff() {
        return supply(() -> withConnection(connection -> {
            Map<UUID, StaffMember> members = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM staff");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    StaffMember member = readStaff(rs);
                    members.put(member.uuid(), member);
                }
            }
            return members;
        }));
    }

    private StaffMember readStaff(ResultSet rs) throws SQLException {
        StaffMember member = new StaffMember();
        member.uuid(uuid(rs.getString("uuid")));
        member.name(rs.getString("name"));
        member.rankId(rs.getString("rank"));
        member.addedBy(uuid(rs.getString("added_by")));
        member.addedByName(rs.getString("added_by"));
        member.addedAt(rs.getLong("added_at"));
        member.lastSeen(rs.getLong("last_seen"));
        member.pinHash(rs.getString("pin_hash"));
        member.lockedIpHash(rs.getString("locked_ip_hash"));
        return member;
    }

    @Override
    public CompletableFuture<Void> saveStaff(StaffMember member) {
        return supply(() -> withConnection(connection -> {
            String sql = """
                    INSERT INTO staff (uuid, name, rank, added_by, added_at, last_seen, pin_hash, locked_ip_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET name = ?, rank = ?, last_seen = ?, pin_hash = ?, locked_ip_hash = ?
                    """;
            if (dialect.autoIncrement()) {
                sql = """
                        INSERT INTO staff (uuid, name, rank, added_by, added_at, last_seen, pin_hash, locked_ip_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE name = ?, rank = ?, last_seen = ?, pin_hash = ?, locked_ip_hash = ?
                        """;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, str(member.uuid()));
                statement.setString(2, member.name());
                statement.setString(3, member.rankId());
                statement.setString(4, member.addedByName());
                statement.setLong(5, member.addedAt());
                statement.setLong(6, member.lastSeen());
                statement.setString(7, member.pinHash());
                statement.setString(8, member.lockedIpHash());
                statement.setString(9, member.name());
                statement.setString(10, member.rankId());
                statement.setLong(11, member.lastSeen());
                statement.setString(12, member.pinHash());
                statement.setString(13, member.lockedIpHash());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> deleteStaff(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM staff WHERE uuid = ?")) {
                statement.setString(1, str(uuid));
                statement.executeUpdate();
            }
            return null;
        }));
    }

    // ------------------------------------------------------- punishments
    private static final String PUNISHMENT_COLUMNS =
            "id, target_uuid, target_name, target_ip, staff_uuid, staff_name, type, reason, issued_at, "
                    + "expires_at, active, removed_by, removed_at, removal_reason, server, silent, evidence, "
                    + "template_id, ladder_step, parent_id, scope";

    private Punishment readPunishment(ResultSet rs) throws SQLException {
        Punishment punishment = new Punishment();
        punishment.id(rs.getString("id"));
        punishment.targetUuid(uuid(rs.getString("target_uuid")));
        punishment.targetName(rs.getString("target_name"));
        punishment.targetIp(rs.getString("target_ip"));
        punishment.staffUuid(uuid(rs.getString("staff_uuid")));
        punishment.staffName(rs.getString("staff_name"));
        punishment.type(io.github.aethelreborn.vsm.models.PunishmentType.fromString(rs.getString("type")));
        punishment.reason(rs.getString("reason"));
        punishment.issuedAt(rs.getLong("issued_at"));
        long expiresAt = rs.getLong("expires_at");
        punishment.expiresAt(rs.wasNull() ? -1L : expiresAt);
        punishment.active(rs.getInt("active") != 0);
        punishment.removedBy(uuid(rs.getString("removed_by")));
        punishment.removedByName(rs.getString("removed_by"));
        punishment.removedAt(rs.getLong("removed_at"));
        punishment.removalReason(rs.getString("removal_reason"));
        punishment.server(rs.getString("server"));
        punishment.silent(rs.getInt("silent") != 0);
        punishment.evidence(rs.getString("evidence"));
        punishment.templateId(rs.getString("template_id"));
        int step = rs.getInt("ladder_step");
        punishment.ladderStep(rs.wasNull() ? -1 : step);
        punishment.parentId(rs.getString("parent_id"));
        punishment.scope(rs.getString("scope"));
        if (punishment.type() == null) {
            punishment.type(io.github.aethelreborn.vsm.models.PunishmentType.WARN);
        }
        return punishment;
    }

    private void bindPunishment(PreparedStatement statement, Punishment punishment) throws SQLException {
        statement.setString(1, punishment.id());
        statement.setString(2, str(punishment.targetUuid()));
        statement.setString(3, punishment.targetName());
        statement.setString(4, punishment.targetIp());
        statement.setString(5, str(punishment.staffUuid()));
        statement.setString(6, punishment.staffName());
        statement.setString(7, punishment.type().name());
        statement.setString(8, punishment.reason());
        statement.setLong(9, punishment.issuedAt());
        statement.setLong(10, punishment.expiresAt());
        statement.setInt(11, punishment.active() ? 1 : 0);
        statement.setString(12, str(punishment.removedBy()));
        statement.setLong(13, punishment.removedAt());
        statement.setString(14, punishment.removalReason());
        statement.setString(15, punishment.server());
        statement.setInt(16, punishment.silent() ? 1 : 0);
        statement.setString(17, punishment.evidence());
        statement.setString(18, punishment.templateId());
        statement.setInt(19, punishment.ladderStep());
        statement.setString(20, punishment.parentId());
        statement.setString(21, punishment.scope());
    }

    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('?');
        }
        return builder.toString();
    }

    @Override
    public CompletableFuture<Void> savePunishment(Punishment punishment) {
        return supply(() -> withConnection(connection -> {
            int binds = 21;
            StringBuilder sql = new StringBuilder("INSERT INTO punishments (").append(PUNISHMENT_COLUMNS)
                    .append(") VALUES (").append(placeholders(binds)).append(") ");
            sql.append(dialect.autoIncrement() ? "ON DUPLICATE KEY UPDATE " : "ON CONFLICT(id) DO UPDATE SET ");
            // Columns 2..21 are refreshed; column 1 is the conflict key.
            String[] names = PUNISHMENT_COLUMNS.split(", ");
            for (int i = 1; i < names.length; i++) {
                if (i > 1) {
                    sql.append(", ");
                }
                sql.append(names[i]).append(" = ?");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bindPunishment(statement, punishment);
                int index = binds + 1;
                statement.setString(index++, str(punishment.targetUuid()));
                statement.setString(index++, punishment.targetName());
                statement.setString(index++, punishment.targetIp());
                statement.setString(index++, str(punishment.staffUuid()));
                statement.setString(index++, punishment.staffName());
                statement.setString(index++, punishment.type().name());
                statement.setString(index++, punishment.reason());
                statement.setLong(index++, punishment.issuedAt());
                statement.setLong(index++, punishment.expiresAt());
                statement.setInt(index++, punishment.active() ? 1 : 0);
                statement.setString(index++, str(punishment.removedBy()));
                statement.setLong(index++, punishment.removedAt());
                statement.setString(index++, punishment.removalReason());
                statement.setString(index++, punishment.server());
                statement.setInt(index++, punishment.silent() ? 1 : 0);
                statement.setString(index++, punishment.evidence());
                statement.setString(index++, punishment.templateId());
                statement.setInt(index++, punishment.ladderStep());
                statement.setString(index++, punishment.parentId());
                statement.setString(index, punishment.scope());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<Punishment>> loadActivePunishments() {
        return supply(() -> withConnection(connection -> {
            List<Punishment> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + PUNISHMENT_COLUMNS + " FROM punishments WHERE active = 1");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(readPunishment(rs));
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID target, int page, int size) {
        return supply(() -> withConnection(connection -> {
            List<Punishment> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + PUNISHMENT_COLUMNS + " FROM punishments WHERE target_uuid = ? "
                            + "ORDER BY issued_at DESC LIMIT ? OFFSET ?")) {
                statement.setString(1, str(target));
                statement.setInt(2, size);
                statement.setInt(3, Math.max(0, page * size));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readPunishment(rs));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<List<Punishment>> byStaff(UUID staff, int page, int size) {
        return supply(() -> withConnection(connection -> {
            List<Punishment> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + PUNISHMENT_COLUMNS + " FROM punishments WHERE staff_uuid = ? "
                            + "ORDER BY issued_at DESC LIMIT ? OFFSET ?")) {
                statement.setString(1, str(staff));
                statement.setInt(2, size);
                statement.setInt(3, Math.max(0, page * size));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readPunishment(rs));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<List<Punishment>> allHistory(int page, int size) {
        return supply(() -> withConnection(connection -> {
            List<Punishment> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + PUNISHMENT_COLUMNS + " FROM punishments ORDER BY issued_at DESC LIMIT ? OFFSET ?")) {
                statement.setInt(1, size);
                statement.setInt(2, Math.max(0, page * size));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readPunishment(rs));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<Optional<Punishment>> byId(String id) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + PUNISHMENT_COLUMNS + " FROM punishments WHERE id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(readPunishment(rs)) : Optional.<Punishment>empty();
                }
            }
        }));
    }

    @Override
    public CompletableFuture<List<Punishment>> activeForIp(String ipOrHash) {
        return supply(() -> withConnection(connection -> {
            List<Punishment> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + PUNISHMENT_COLUMNS + " FROM punishments WHERE target_ip = ? AND active = 1")) {
                statement.setString(1, ipOrHash);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readPunishment(rs));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<Void> purgePlayer(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            String target = str(uuid);
            for (String sql : List.of(
                    "DELETE FROM punishments WHERE target_uuid = ?",
                    "DELETE FROM ip_history WHERE uuid = ?",
                    "DELETE FROM ip_allow WHERE uuid = ?",
                    "DELETE FROM notes WHERE target_uuid = ?",
                    "DELETE FROM reports WHERE target_uuid = ? OR reporter_uuid = ?",
                    "DELETE FROM saved_locations WHERE player = ?",
                    "DELETE FROM watchlist WHERE uuid = ?")) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, target);
                    if (sql.contains("OR reporter_uuid")) {
                        statement.setString(2, target);
                    }
                    statement.executeUpdate();
                }
            }
            return null;
        }));
    }

    // ----------------------------------------------------------------- ip
    @Override
    public CompletableFuture<Void> recordLogin(UUID uuid, String ip, long now) {
        return supply(() -> withConnection(connection -> {
            String upsertSql = dialect.autoIncrement()
                    ? "INSERT INTO ip_history (uuid, ip, first_seen, last_seen, times_seen) VALUES (?, ?, ?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE last_seen = ?, times_seen = times_seen + 1"
                    : "INSERT INTO ip_history (uuid, ip, first_seen, last_seen, times_seen) VALUES (?, ?, ?, ?, 1) "
                    + "ON CONFLICT(uuid, ip) DO UPDATE SET last_seen = ?, times_seen = times_seen + 1";
            try (PreparedStatement statement = connection.prepareStatement(upsertSql)) {
                statement.setString(1, str(uuid));
                statement.setString(2, ip);
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<IpRecord>> ipHistory(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            List<IpRecord> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, ip, first_seen, last_seen, times_seen FROM ip_history WHERE uuid = ? "
                            + "ORDER BY last_seen DESC")) {
                statement.setString(1, str(uuid));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(new IpRecord(uuid(rs.getString("uuid")), rs.getString("ip"),
                                rs.getLong("first_seen"), rs.getLong("last_seen"), rs.getInt("times_seen")));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<List<UUID>> accountsOnIp(String ip) {
        return supply(() -> withConnection(connection -> {
            List<UUID> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid FROM ip_history WHERE ip = ?")) {
                statement.setString(1, ip);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = uuid(rs.getString("uuid"));
                        if (uuid != null && !out.contains(uuid)) {
                            out.add(uuid);
                        }
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<String> lastKnownIp(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT ip FROM ip_history WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1")) {
                statement.setString(1, str(uuid));
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getString("ip") : null;
                }
            }
        }));
    }

    @Override
    public CompletableFuture<Void> saveIpAllow(UUID uuid, String addedBy, long addedAt) {
        return supply(() -> withConnection(connection -> {
            String sql = dialect.autoIncrement()
                    ? "INSERT INTO ip_allow (uuid, added_by, added_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE added_by = ?, added_at = ?"
                    : "INSERT INTO ip_allow (uuid, added_by, added_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET added_by = ?, added_at = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, str(uuid));
                statement.setString(2, addedBy);
                statement.setLong(3, addedAt);
                statement.setString(4, addedBy);
                statement.setLong(5, addedAt);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> removeIpAllow(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM ip_allow WHERE uuid = ?")) {
                statement.setString(1, str(uuid));
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<UUID>> loadIpAllow() {
        return supply(() -> withConnection(connection -> {
            List<UUID> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid FROM ip_allow");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = uuid(rs.getString("uuid"));
                    if (uuid != null) {
                        out.add(uuid);
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<Void> pruneIpHistory(long olderThan) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM ip_history WHERE last_seen < ?")) {
                statement.setLong(1, olderThan);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    // ------------------------------------------------------ notes, reports
    @Override
    public CompletableFuture<Void> saveNote(PlayerNote note) {
        return supply(() -> withConnection(connection -> {
            String columns = "id, target_uuid, target_name, author_uuid, author_name, text, created_at, pinned";
            String sql = "INSERT INTO notes (" + columns + ") VALUES (" + placeholders(8) + ") "
                    + "ON CONFLICT(id) DO UPDATE SET text = ?, pinned = ?";
            if (dialect.autoIncrement()) {
                sql = "INSERT INTO notes (" + columns + ") VALUES (" + placeholders(8)
                        + ") ON DUPLICATE KEY UPDATE text = ?, pinned = ?";
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, note.id());
                statement.setString(2, str(note.target()));
                statement.setString(3, note.targetName());
                statement.setString(4, str(note.author()));
                statement.setString(5, note.authorName());
                statement.setString(6, note.text());
                statement.setLong(7, note.createdAt());
                statement.setInt(8, note.pinned() ? 1 : 0);
                statement.setString(9, note.text());
                statement.setInt(10, note.pinned() ? 1 : 0);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> deleteNote(String id) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM notes WHERE id = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<PlayerNote>> notes(UUID target) {
        return supply(() -> withConnection(connection -> {
            List<PlayerNote> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM notes WHERE target_uuid = ? ORDER BY created_at DESC")) {
                statement.setString(1, str(target));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readNote(rs));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<List<PlayerNote>> pinnedNotes(UUID target) {
        return notes(target).thenApply(list -> {
            List<PlayerNote> out = new ArrayList<>();
            for (PlayerNote note : list) {
                if (note.pinned()) {
                    out.add(note);
                }
            }
            return out;
        });
    }

    private PlayerNote readNote(ResultSet rs) throws SQLException {
        PlayerNote note = new PlayerNote();
        note.id(rs.getString("id"));
        note.target(uuid(rs.getString("target_uuid")));
        note.targetName(rs.getString("target_name"));
        note.author(uuid(rs.getString("author_uuid")));
        note.authorName(rs.getString("author_name"));
        note.text(rs.getString("text"));
        note.createdAt(rs.getLong("created_at"));
        note.pinned(rs.getInt("pinned") != 0);
        return note;
    }

    @Override
    public CompletableFuture<Report> createReport(Report report) {
        return supply(() -> withConnection(connection -> {
            String sql = """
                    INSERT INTO reports (reporter_uuid, reporter_name, target_uuid, target_name, reason,
                                         created_at, status, priority, assigned_staff, note, evidence,
                                         claimed_at, closed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql,
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                bindReport(statement, report);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        report.id(keys.getLong(1));
                    }
                }
            }
            return report;
        }));
    }

    private void bindReport(PreparedStatement statement, Report report) throws SQLException {
        statement.setString(1, str(report.reporterUuid()));
        statement.setString(2, report.reporterName());
        statement.setString(3, str(report.targetUuid()));
        statement.setString(4, report.targetName());
        statement.setString(5, report.reason());
        statement.setLong(6, report.createdAt());
        statement.setString(7, report.status().name());
        statement.setString(8, report.priority().name());
        statement.setString(9, report.assignedStaff());
        statement.setString(10, report.note());
        statement.setString(11, report.evidence());
        statement.setLong(12, report.claimedAt());
        statement.setLong(13, report.closedAt());
    }

    private Report readReport(ResultSet rs) throws SQLException {
        Report report = new Report();
        report.id(rs.getLong("id"));
        report.reporterUuid(uuid(rs.getString("reporter_uuid")));
        report.reporterName(rs.getString("reporter_name"));
        report.targetUuid(uuid(rs.getString("target_uuid")));
        report.targetName(rs.getString("target_name"));
        report.reason(rs.getString("reason"));
        report.createdAt(rs.getLong("created_at"));
        report.status(parseEnum(Report.Status.class, rs.getString("status"), Report.Status.OPEN));
        report.priority(parseEnum(Report.Priority.class, rs.getString("priority"), Report.Priority.NORMAL));
        report.assignedStaff(rs.getString("assigned_staff"));
        report.note(rs.getString("note"));
        report.evidence(rs.getString("evidence"));
        report.claimedAt(rs.getLong("claimed_at"));
        report.closedAt(rs.getLong("closed_at"));
        return report;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String raw, T fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    @Override
    public CompletableFuture<Void> saveReport(Report report) {
        return supply(() -> withConnection(connection -> {
            String sql = """
                    UPDATE reports SET status = ?, priority = ?, assigned_staff = ?, note = ?, evidence = ?,
                                        claimed_at = ?, closed_at = ? WHERE id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, report.status().name());
                statement.setString(2, report.priority().name());
                statement.setString(3, report.assignedStaff());
                statement.setString(4, report.note());
                statement.setString(5, report.evidence());
                statement.setLong(6, report.claimedAt());
                statement.setLong(7, report.closedAt());
                statement.setLong(8, report.id());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<Optional<Report>> reportById(long id) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM reports WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(readReport(rs)) : Optional.<Report>empty();
                }
            }
        }));
    }

    @Override
    public CompletableFuture<List<Report>> reports(String statusFilter, UUID assignedTo, int page, int size) {
        return supply(() -> withConnection(connection -> {
            List<Report> out = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM reports WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            if (statusFilter != null && !statusFilter.isBlank()) {
                sql.append(" AND status = ?");
                params.add(statusFilter);
            }
            if (assignedTo != null) {
                sql.append(" AND assigned_staff = ?");
                params.add(assignedTo.toString());
            }
            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add(Math.max(0, page * size));
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readReport(rs));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<Integer> countReports(String statusFilter, UUID assignedTo) {
        return supply(() -> withConnection(connection -> {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM reports WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            if (statusFilter != null && !statusFilter.isBlank()) {
                sql.append(" AND status = ?");
                params.add(statusFilter);
            }
            if (assignedTo != null) {
                sql.append(" AND assigned_staff = ?");
                params.add(assignedTo.toString());
            }
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }));
    }

    @Override
    public CompletableFuture<List<Report>> reportsAgainst(UUID target) {
        return supply(() -> withConnection(connection -> {
            List<Report> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM reports WHERE target_uuid = ? ORDER BY created_at DESC")) {
                statement.setString(1, str(target));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readReport(rs));
                    }
                }
            }
            return out;
        }));
    }

    // --------------------------------------------------------------- audit
    @Override
    public CompletableFuture<Void> saveAudit(AuditEntry entry) {
        return supply(() -> withConnection(connection -> {
            String columns = "id, staff_uuid, staff_name, action, target_uuid, target_name, details, source, "
                    + "timestamp, prev_hash, hash";
            String sql = "INSERT INTO audit_logs (" + columns + ") VALUES (" + placeholders(11) + ") "
                    + "ON CONFLICT(id) DO UPDATE SET details = ?, hash = ?";
            if (dialect.autoIncrement()) {
                sql = "INSERT INTO audit_logs (" + columns + ") VALUES (" + placeholders(11)
                        + ") ON DUPLICATE KEY UPDATE details = ?, hash = ?";
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, entry.id());
                statement.setString(2, str(entry.staffUuid()));
                statement.setString(3, entry.staffName());
                statement.setString(4, entry.action());
                statement.setString(5, str(entry.targetUuid()));
                statement.setString(6, entry.targetName());
                statement.setString(7, entry.details());
                statement.setString(8, entry.source().name());
                statement.setLong(9, entry.timestamp());
                statement.setString(10, entry.prevHash());
                statement.setString(11, entry.hash());
                statement.setString(12, entry.details());
                statement.setString(13, entry.hash());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<AuditEntry>> auditPage(UUID staff, String action, int page, int size) {
        return supply(() -> withConnection(connection -> {
            List<AuditEntry> out = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM audit_logs WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            if (staff != null) {
                sql.append(" AND staff_uuid = ?");
                params.add(staff.toString());
            }
            if (action != null && !action.isBlank()) {
                sql.append(" AND action LIKE ?");
                params.add(action + "%");
            }
            sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add(Math.max(0, page * size));
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(readAudit(rs));
                    }
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<List<AuditEntry>> auditAll() {
        return supply(() -> withConnection(connection -> {
            List<AuditEntry> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM audit_logs ORDER BY timestamp ASC");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(readAudit(rs));
                }
            }
            return out;
        }));
    }

    private AuditEntry readAudit(ResultSet rs) throws SQLException {
        AuditEntry entry = new AuditEntry();
        entry.id(rs.getString("id"));
        entry.staffUuid(uuid(rs.getString("staff_uuid")));
        entry.staffName(rs.getString("staff_name"));
        entry.action(rs.getString("action"));
        entry.targetUuid(uuid(rs.getString("target_uuid")));
        entry.targetName(rs.getString("target_name"));
        entry.details(rs.getString("details"));
        entry.source(io.github.aethelreborn.vsm.models.ActionSource.fromString(rs.getString("source")));
        entry.timestamp(rs.getLong("timestamp"));
        entry.prevHash(rs.getString("prev_hash"));
        entry.hash(rs.getString("hash"));
        return entry;
    }

    // ------------------------------------------------- locations, names
    @Override
    public CompletableFuture<Void> saveLocation(SavedLocation location) {
        return supply(() -> withConnection(connection -> {
            String columns = "player, kind, world, x, y, z, yaw, pitch, saved_at, cause";
            String sql = "INSERT INTO saved_locations (" + columns + ") VALUES (" + placeholders(10)
                    + ") ON CONFLICT(player, kind) DO UPDATE SET world = ?, x = ?, y = ?, z = ?, yaw = ?, "
                    + "pitch = ?, saved_at = ?, cause = ?";
            if (dialect.autoIncrement()) {
                sql = "INSERT INTO saved_locations (" + columns + ") VALUES (" + placeholders(10)
                        + ") ON DUPLICATE KEY UPDATE world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, "
                        + "saved_at = ?, cause = ?";
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, str(location.player()));
                statement.setString(2, location.kind());
                statement.setString(3, location.world());
                statement.setDouble(4, location.x());
                statement.setDouble(5, location.y());
                statement.setDouble(6, location.z());
                statement.setDouble(7, location.yaw());
                statement.setDouble(8, location.pitch());
                statement.setLong(9, location.savedAt());
                statement.setString(10, location.cause());
                statement.setString(11, location.world());
                statement.setDouble(12, location.x());
                statement.setDouble(13, location.y());
                statement.setDouble(14, location.z());
                statement.setDouble(15, location.yaw());
                statement.setDouble(16, location.pitch());
                statement.setLong(17, location.savedAt());
                statement.setString(18, location.cause());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<Optional<SavedLocation>> location(UUID player, String kind) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM saved_locations WHERE player = ? AND kind = ?")) {
                statement.setString(1, str(player));
                statement.setString(2, kind);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<SavedLocation>empty();
                    }
                    return Optional.of(new SavedLocation(player, kind, rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            (float) rs.getDouble("yaw"), (float) rs.getDouble("pitch"),
                            rs.getLong("saved_at"), rs.getString("cause")));
                }
            }
        }));
    }

    @Override
    public CompletableFuture<Void> saveNameHistory(UUID uuid, String name, long firstSeen) {
        return supply(() -> withConnection(connection -> {
            String sql = dialect.autoIncrement()
                    ? "INSERT INTO name_history (uuid, name, first_seen) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE first_seen = LEAST(first_seen, ?)"
                    : "INSERT INTO name_history (uuid, name, first_seen) VALUES (?, ?, ?) "
                    + "ON CONFLICT(uuid, name) DO UPDATE SET first_seen = MIN(first_seen, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, str(uuid));
                statement.setString(2, name);
                statement.setLong(3, firstSeen);
                statement.setLong(4, firstSeen);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<String>> nameHistory(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT name FROM name_history WHERE uuid = ? ORDER BY first_seen DESC")) {
                statement.setString(1, str(uuid));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString("name"));
                    }
                }
            }
            return out;
        }));
    }

    // ------------------------------------------------------------ watching
    @Override
    public CompletableFuture<Void> saveWatch(WatchEntry entry) {
        return supply(() -> withConnection(connection -> {
            String sql = "INSERT INTO watchlist (uuid, name, added_by, reason, added_at) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET reason = ?, added_by = ?";
            if (dialect.autoIncrement()) {
                sql = "INSERT INTO watchlist (uuid, name, added_by, reason, added_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE reason = ?, added_by = ?";
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, str(entry.uuid()));
                statement.setString(2, entry.name());
                statement.setString(3, entry.addedBy());
                statement.setString(4, entry.reason());
                statement.setLong(5, entry.addedAt());
                statement.setString(6, entry.reason());
                statement.setString(7, entry.addedBy());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> deleteWatch(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM watchlist WHERE uuid = ?")) {
                statement.setString(1, str(uuid));
                statement.executeUpdate();
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<WatchEntry>> watchlist() {
        return supply(() -> withConnection(connection -> {
            List<WatchEntry> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM watchlist");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new WatchEntry(uuid(rs.getString("uuid")), rs.getString("name"),
                            rs.getString("added_by"), rs.getString("reason"), rs.getLong("added_at")));
                }
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<Optional<WatchEntry>> watchEntry(UUID uuid) {
        return supply(() -> withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM watchlist WHERE uuid = ?")) {
                statement.setString(1, str(uuid));
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<WatchEntry>empty();
                    }
                    return Optional.of(new WatchEntry(uuid(rs.getString("uuid")), rs.getString("name"),
                            rs.getString("added_by"), rs.getString("reason"), rs.getLong("added_at")));
                }
            }
        }));
    }

    @Override
    public CompletableFuture<String> exportAll() {
        return allHistory(0, 10_000).thenApply(punishments -> {
            StringBuilder out = new StringBuilder("{\"punishments\":[");
            boolean first = true;
            for (Punishment punishment : punishments) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(Json.object()
                        .put("id", punishment.id())
                        .put("type", punishment.type().name())
                        .put("target", punishment.displayTarget())
                        .put("targetUuid", punishment.targetUuid() == null ? null : punishment.targetUuid().toString())
                        .put("staff", punishment.staffName())
                        .put("reason", punishment.reason())
                        .put("issuedAt", punishment.issuedAt())
                        .put("expiresAt", punishment.expiresAt())
                        .put("active", punishment.active())
                        .toString());
            }
            return out.append("]}").toString();
        });
    }

    protected static String str(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    protected static UUID uuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}