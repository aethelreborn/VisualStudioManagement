package io.github.aethelreborn.vsm.storage;

import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.AuditEntry;
import io.github.aethelreborn.vsm.models.IpRecord;
import io.github.aethelreborn.vsm.models.PlayerNote;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.models.Report;
import io.github.aethelreborn.vsm.models.SavedLocation;
import io.github.aethelreborn.vsm.models.StaffMember;
import io.github.aethelreborn.vsm.models.WatchEntry;
import io.github.aethelreborn.vsm.util.IpUtil;
import io.github.aethelreborn.vsm.util.Json;
import io.github.aethelreborn.vsm.util.TimeUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * YAML backend (spec 15.1). Everything is held in memory and written back atomically (temp file plus move).
 * Intended for small servers; a warning is logged once the record count gets large.
 */
public final class YamlStorage implements StorageProvider {

    private static final int LARGE_SERVER_WARNING = 5_000;

    private final File file;
    private final Logger logger;
    private YamlConfiguration data;
    private ExecutorService executor;
    private volatile boolean warnedAboutSize;

    public YamlStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public String name() {
        return "YAML";
    }

    @Override
    public CompletableFuture<Void> init() {
        return run(() -> {
            data = new YamlConfiguration();
            if (file.exists()) {
                try {
                    data.load(file);
                } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
                    logger.severe("Could not read " + file.getName() + ": " + ex.getMessage()
                            + " — the file will be recreated; please restore a backup if you have one.");
                    data = new YamlConfiguration();
                }
            }
            Migrations.writeYamlVersion(data);
            save();
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "VSM-YamlStorage");
                thread.setDaemon(true);
                return thread;
            });
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return run(() -> {
            save();
            if (executor != null) {
                executor.shutdown();
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> run(SqlCall<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                future.complete(callable.call());
            } catch (Throwable throwable) {
                logger.severe("YAML storage operation failed: " + throwable.getMessage());
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
    private interface SqlCall<T> {
        T call() throws Exception;
    }

    /** Writes through a temp file and an atomic move so a crash cannot corrupt the data. */
    private synchronized void save() {
        if (data == null) {
            return;
        }
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "vsm-", ".tmp");
            data.save(temp.toFile());
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            logger.severe("Could not write " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void warnIfLarge(int records) {
        if (!warnedAboutSize && records > LARGE_SERVER_WARNING) {
            warnedAboutSize = true;
            logger.warning("YAML storage holds " + records + " records; switch storage.type to SQLITE for "
                    + "servers of this size.");
        }
    }

    // -------------------------------------------------------------- staff
    @Override
    public CompletableFuture<Map<UUID, StaffMember>> loadStaff() {
        return run(() -> {
            Map<UUID, StaffMember> members = new LinkedHashMap<>();
            ConfigurationSection section = data.getConfigurationSection("staff");
            if (section == null) {
                return members;
            }
            for (String key : section.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null) {
                    continue;
                }
                members.put(uuid, new StaffMember(uuid,
                        node.getString("name", key),
                        node.getString("rank", "helper"))
                        .addedByName(node.getString("added_by", "console"))
                        .addedAt(TimeUtil.fromIso(node.getString("added_at", "")))
                        .lastSeen(TimeUtil.fromIso(node.getString("last_seen", "")))
                        .pinHash(node.getString("pin_hash"))
                        .lockedIpHash(node.getString("locked_ip_hash"))
                        .staffChatToggle(node.getBoolean("staff_chat_toggle", false))
                        .alertsEnabled(node.getBoolean("alerts_enabled", true))
                        .commandSpy(node.getBoolean("command_spy", false))
                        .socialSpy(node.getBoolean("social_spy", false)));
            }
            return members;
        });
    }

    @Override
    public CompletableFuture<Void> saveStaff(StaffMember member) {
        return run(() -> {
            ConfigurationSection section = data.getConfigurationSection("staff");
            if (section == null) {
                section = data.createSection("staff");
            }
            ConfigurationSection node = section.getConfigurationSection(member.uuid().toString());
            if (node == null) {
                node = section.createSection(member.uuid().toString());
            }
            node.set("name", member.name());
            node.set("rank", member.rankId());
            node.set("added_by", member.addedByName());
            node.set("added_at", TimeUtil.toIso(member.addedAt()));
            node.set("last_seen", TimeUtil.toIso(member.lastSeen()));
            node.set("pin_hash", member.pinHash());
            node.set("locked_ip_hash", member.lockedIpHash());
            node.set("staff_chat_toggle", member.staffChatToggle());
            node.set("alerts_enabled", member.alertsEnabled());
            node.set("command_spy", member.commandSpy());
            node.set("social_spy", member.socialSpy());
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteStaff(UUID uuid) {
        return run(() -> {
            data.set("staff." + uuid, null);
            save();
            return null;
        });
    }

    // ------------------------------------------------------- punishments
    @Override
    public CompletableFuture<Void> savePunishment(Punishment punishment) {
        return run(() -> {
            data.set("punishments." + punishment.id() + ".id", punishment.id());
            data.set("punishments." + punishment.id() + ".target-uuid", str(punishment.targetUuid()));
            data.set("punishments." + punishment.id() + ".target-name", punishment.targetName());
            data.set("punishments." + punishment.id() + ".target-ip", punishment.targetIp());
            data.set("punishments." + punishment.id() + ".staff-uuid", str(punishment.staffUuid()));
            data.set("punishments." + punishment.id() + ".staff-name", punishment.staffName());
            data.set("punishments." + punishment.id() + ".type", punishment.type().name());
            data.set("punishments." + punishment.id() + ".reason", punishment.reason());
            data.set("punishments." + punishment.id() + ".issued-at", punishment.issuedAt());
            data.set("punishments." + punishment.id() + ".expires-at", punishment.expiresAt());
            data.set("punishments." + punishment.id() + ".active", punishment.active());
            data.set("punishments." + punishment.id() + ".removed-by", str(punishment.removedBy()));
            data.set("punishments." + punishment.id() + ".removed-by-name", punishment.removedByName());
            data.set("punishments." + punishment.id() + ".removed-at", punishment.removedAt());
            data.set("punishments." + punishment.id() + ".removal-reason", punishment.removalReason());
            data.set("punishments." + punishment.id() + ".server", punishment.server());
            data.set("punishments." + punishment.id() + ".silent", punishment.silent());
            data.set("punishments." + punishment.id() + ".evidence", punishment.evidence());
            data.set("punishments." + punishment.id() + ".template-id", punishment.templateId());
            data.set("punishments." + punishment.id() + ".ladder-step", punishment.ladderStep());
            data.set("punishments." + punishment.id() + ".parent-id", punishment.parentId());
            data.set("punishments." + punishment.id() + ".scope", punishment.scope());
            warnIfLarge(data.getConfigurationSection("punishments").getKeys(false).size());
            save();
            return null;
        });
    }

    private Punishment readPunishment(ConfigurationSection node) {
        Punishment punishment = new Punishment();
        punishment.id(node.getString("id"));
        punishment.targetUuid(parseUuid(node.getString("target-uuid")));
        punishment.targetName(node.getString("target-name"));
        punishment.targetIp(node.getString("target-ip"));
        punishment.staffUuid(parseUuid(node.getString("staff-uuid")));
        punishment.staffName(node.getString("staff-name", "console"));
        punishment.type(orDefault(PunishmentType.fromString(node.getString("type")), PunishmentType.WARN));
        punishment.reason(node.getString("reason", ""));
        punishment.issuedAt(node.getLong("issued-at"));
        punishment.expiresAt(node.getLong("expires-at", -1L));
        punishment.active(node.getBoolean("active", true));
        punishment.removedBy(parseUuid(node.getString("removed-by")));
        punishment.removedByName(node.getString("removed-by-name"));
        punishment.removedAt(node.getLong("removed-at"));
        punishment.removalReason(node.getString("removal-reason"));
        punishment.server(node.getString("server", "global"));
        punishment.silent(node.getBoolean("silent", false));
        punishment.evidence(node.getString("evidence"));
        punishment.templateId(node.getString("template-id"));
        punishment.ladderStep(node.getInt("ladder-step", -1));
        punishment.parentId(node.getString("parent-id"));
        punishment.scope(node.getString("scope", ""));
        return punishment;
    }

    @Override
    public CompletableFuture<List<Punishment>> loadActivePunishments() {
        return allPunishments().thenApply(list -> {
            List<Punishment> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Punishment punishment : list) {
                if (punishment.isActiveNow(now)) {
                    out.add(punishment);
                }
            }
            return out;
        });
    }

    private CompletableFuture<List<Punishment>> allPunishments() {
        return run(() -> {
            List<Punishment> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("punishments");
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node != null) {
                    out.add(readPunishment(node));
                }
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID target, int page, int size) {
        return allPunishments().thenApply(list -> {
            List<Punishment> filtered = new ArrayList<>();
            for (Punishment punishment : list) {
                if (target.equals(punishment.targetUuid())) {
                    filtered.add(punishment);
                }
            }
            return page(filtered, page, size);
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> byStaff(UUID staff, int page, int size) {
        return allPunishments().thenApply(list -> {
            List<Punishment> filtered = new ArrayList<>();
            for (Punishment punishment : list) {
                if (staff.equals(punishment.staffUuid())) {
                    filtered.add(punishment);
                }
            }
            return page(filtered, page, size);
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> allHistory(int page, int size) {
        return allPunishments().thenApply(list -> page(list, page, size));
    }

    private static List<Punishment> page(List<Punishment> list, int page, int size) {
        List<Punishment> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingLong((Punishment punishment) -> punishment.issuedAt()).reversed());
        int from = Math.min(Math.max(0, page * size), sorted.size());
        int to = Math.min(from + size, sorted.size());
        return new ArrayList<>(sorted.subList(from, to));
    }

    @Override
    public CompletableFuture<Optional<Punishment>> byId(String id) {
        return allPunishments().thenApply(list -> {
            for (Punishment punishment : list) {
                if (punishment.id().equals(id)) {
                    return Optional.of(punishment);
                }
            }
            return Optional.<Punishment>empty();
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> activeForIp(String ipOrHash) {
        return allPunishments().thenApply(list -> {
            List<Punishment> out = new ArrayList<>();
            String key = IpUtil.cacheKey(ipOrHash);
            for (Punishment punishment : list) {
                if (punishment.targetIp() != null
                        && key.equals(IpUtil.cacheKey(punishment.targetIp()))
                        && punishment.active()) {
                    out.add(punishment);
                }
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<Void> purgePlayer(UUID uuid) {
        return run(() -> {
            for (String sectionName : List.of("punishments", "ip-history", "notes", "reports", "locations", "watchlist")) {
                ConfigurationSection section = data.getConfigurationSection(sectionName);
                if (section == null) {
                    continue;
                }
                List<String> remove = new ArrayList<>();
                for (String key : section.getKeys(false)) {
                    ConfigurationSection node = section.getConfigurationSection(key);
                    if (node == null) {
                        continue;
                    }
                    String target = node.getString("target-uuid", node.getString("uuid", node.getString("player", "")));
                    if (uuid.toString().equals(target)) {
                        remove.add(key);
                    }
                }
                for (String key : remove) {
                    section.set(key, null);
                }
            }
            save();
            return null;
        });
    }

    // ----------------------------------------------------------------- ip
    @Override
    public CompletableFuture<Void> recordLogin(UUID uuid, String ip, long now) {
        return run(() -> {
            String key = uuid + "." + Integer.toHexString(ip.hashCode());
            String path = "ip-history." + key;
            long firstSeen = data.getLong(path + ".first-seen", now);
            int times = data.getInt(path + ".times-seen", 0) + 1;
            data.set(path + ".uuid", uuid.toString());
            data.set(path + ".ip", ip);
            data.set(path + ".first-seen", firstSeen);
            data.set(path + ".last-seen", now);
            data.set(path + ".times-seen", times);
            save();
            return null;
        });
    }

    private String ipKey(UUID uuid, String ip) {
        return uuid + "." + Integer.toHexString(ip.hashCode());
    }

    @Override
    public CompletableFuture<List<IpRecord>> ipHistory(UUID uuid) {
        return run(() -> {
            List<IpRecord> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("ip-history");
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null || !uuid.toString().equals(node.getString("uuid"))) {
                    continue;
                }
                out.add(new IpRecord(uuid, node.getString("ip"), node.getLong("first-seen"),
                        node.getLong("last-seen"), node.getInt("times-seen", 1)));
            }
            out.sort(Comparator.comparingLong((IpRecord record) -> record.lastSeen()).reversed());
            return out;
        });
    }

    @Override
    public CompletableFuture<List<UUID>> accountsOnIp(String ip) {
        return run(() -> {
            List<UUID> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("ip-history");
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null || !ip.equals(node.getString("ip"))) {
                    continue;
                }
                UUID uuid = parseUuid(node.getString("uuid"));
                if (uuid != null && !out.contains(uuid)) {
                    out.add(uuid);
                }
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<String> lastKnownIp(UUID uuid) {
        return ipHistory(uuid).thenApply(list -> list.isEmpty() ? null : list.get(0).ip());
    }

    @Override
    public CompletableFuture<Void> saveIpAllow(UUID uuid, String addedBy, long addedAt) {
        return run(() -> {
            data.set("ip-allow." + uuid + ".added-by", addedBy);
            data.set("ip-allow." + uuid + ".added-at", addedAt);
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> removeIpAllow(UUID uuid) {
        return run(() -> {
            data.set("ip-allow." + uuid, null);
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<List<UUID>> loadIpAllow() {
        return run(() -> {
            List<UUID> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("ip-allow");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    UUID uuid = parseUuid(key);
                    if (uuid != null) {
                        out.add(uuid);
                    }
                }
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<Void> pruneIpHistory(long olderThan) {
        return run(() -> {
            ConfigurationSection section = data.getConfigurationSection("ip-history");
            if (section != null) {
                List<String> remove = new ArrayList<>();
                for (String key : section.getKeys(false)) {
                    ConfigurationSection node = section.getConfigurationSection(key);
                    if (node != null && node.getLong("last-seen") < olderThan) {
                        remove.add(key);
                    }
                }
                for (String key : remove) {
                    section.set(key, null);
                }
                save();
            }
            return null;
        });
    }

    // ------------------------------------------------------ notes, reports
    @Override
    public CompletableFuture<Void> saveNote(PlayerNote note) {
        return run(() -> {
            String path = "notes." + note.id();
            data.set(path + ".id", note.id());
            data.set(path + ".target-uuid", str(note.target()));
            data.set(path + ".target-name", note.targetName());
            data.set(path + ".author-uuid", str(note.author()));
            data.set(path + ".author-name", note.authorName());
            data.set(path + ".text", note.text());
            data.set(path + ".created-at", note.createdAt());
            data.set(path + ".pinned", note.pinned());
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteNote(String id) {
        return run(() -> {
            data.set("notes." + id, null);
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<List<PlayerNote>> notes(UUID target) {
        return run(() -> readNotes("notes", target, false));
    }

    @Override
    public CompletableFuture<List<PlayerNote>> pinnedNotes(UUID target) {
        return run(() -> readNotes("notes", target, true));
    }

    private List<PlayerNote> readNotes(String sectionName, UUID target, boolean pinnedOnly) {
        List<PlayerNote> out = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(sectionName);
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null || !target.toString().equals(node.getString("target-uuid"))) {
                continue;
            }
            PlayerNote note = PlayerNote.of(target, node.getString("target-name"),
                    parseUuid(node.getString("author-uuid")), node.getString("author-name"), node.getString("text"))
                    .id(node.getString("id", key))
                    .createdAt(node.getLong("created-at"))
                    .pinned(node.getBoolean("pinned", false));
            if (pinnedOnly && !note.pinned()) {
                continue;
            }
            out.add(note);
        }
        out.sort(Comparator.comparingLong((PlayerNote note) -> note.createdAt()).reversed());
        return out;
    }

    @Override
    public CompletableFuture<Report> createReport(Report report) {
        return run(() -> {
            long nextId = data.getLong("report-counter", 0L) + 1L;
            data.set("report-counter", nextId);
            report.id(nextId);
            writeReport(report);
            save();
            return report;
        });
    }

    private void writeReport(Report report) {
        String path = "reports." + report.id();
        data.set(path + ".id", report.id());
        data.set(path + ".reporter-uuid", str(report.reporterUuid()));
        data.set(path + ".reporter-name", report.reporterName());
        data.set(path + ".target-uuid", str(report.targetUuid()));
        data.set(path + ".target-name", report.targetName());
        data.set(path + ".reason", report.reason());
        data.set(path + ".created-at", report.createdAt());
        data.set(path + ".status", report.status().name());
        data.set(path + ".priority", report.priority().name());
        data.set(path + ".assigned-staff", report.assignedStaff());
        data.set(path + ".note", report.note());
        data.set(path + ".evidence", report.evidence());
        data.set(path + ".claimed-at", report.claimedAt());
        data.set(path + ".closed-at", report.closedAt());
    }

    private Report readReport(ConfigurationSection node) {
        Report report = new Report();
        report.id(node.getLong("id"));
        report.reporterUuid(parseUuid(node.getString("reporter-uuid")));
        report.reporterName(node.getString("reporter-name", ""));
        report.targetUuid(parseUuid(node.getString("target-uuid")));
        report.targetName(node.getString("target-name", ""));
        report.reason(node.getString("reason", ""));
        report.createdAt(node.getLong("created-at"));
        report.status(orDefault(enumOrNull(Report.Status.class, node.getString("status")), Report.Status.OPEN));
        report.priority(orDefault(enumOrNull(Report.Priority.class, node.getString("priority")), Report.Priority.NORMAL));
        report.assignedStaff(node.getString("assigned-staff"));
        report.note(node.getString("note"));
        report.evidence(node.getString("evidence"));
        report.claimedAt(node.getLong("claimed-at"));
        report.closedAt(node.getLong("closed-at"));
        return report;
    }

    @Override
    public CompletableFuture<Void> saveReport(Report report) {
        return run(() -> {
            writeReport(report);
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<Report>> reportById(long id) {
        return allReports().thenApply(list -> {
            for (Report report : list) {
                if (report.id() == id) {
                    return Optional.of(report);
                }
            }
            return Optional.<Report>empty();
        });
    }

    private CompletableFuture<List<Report>> allReports() {
        return run(() -> {
            List<Report> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("reports");
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node != null) {
                    out.add(readReport(node));
                }
            }
            out.sort(Comparator.comparingLong((Report report) -> report.createdAt()).reversed());
            return out;
        });
    }

    @Override
    public CompletableFuture<List<Report>> reports(String statusFilter, UUID assignedTo, int page, int size) {
        return allReports().thenApply(list -> {
            List<Report> filtered = new ArrayList<>();
            for (Report report : list) {
                if (statusFilter != null && !statusFilter.isBlank()
                        && !report.status().name().equalsIgnoreCase(statusFilter)) {
                    continue;
                }
                if (assignedTo != null && !assignedTo.toString().equals(report.assignedStaff())) {
                    continue;
                }
                filtered.add(report);
            }
            int from = Math.min(Math.max(0, page * size), filtered.size());
            int to = Math.min(from + size, filtered.size());
            return new ArrayList<>(filtered.subList(from, to));
        });
    }

    @Override
    public CompletableFuture<Integer> countReports(String statusFilter, UUID assignedTo) {
        return reports(statusFilter, assignedTo, 0, Integer.MAX_VALUE).thenApply(List::size);
    }

    @Override
    public CompletableFuture<List<Report>> reportsAgainst(UUID target) {
        return allReports().thenApply(list -> {
            List<Report> out = new ArrayList<>();
            for (Report report : list) {
                if (target.toString().equals(String.valueOf(report.targetUuid()))) {
                    out.add(report);
                }
            }
            return out;
        });
    }

    // --------------------------------------------------------------- audit
    @Override
    public CompletableFuture<Void> saveAudit(AuditEntry entry) {
        return run(() -> {
            writeAudit(entry);
            save();
            return null;
        });
    }

    private void writeAudit(AuditEntry entry) {
        String path = "audit." + entry.id();
        data.set(path + ".id", entry.id());
        data.set(path + ".staff-uuid", str(entry.staffUuid()));
        data.set(path + ".staff-name", entry.staffName());
        data.set(path + ".action", entry.action());
        data.set(path + ".target-uuid", str(entry.targetUuid()));
        data.set(path + ".target-name", entry.targetName());
        data.set(path + ".details", entry.details());
        data.set(path + ".source", entry.source().name());
        data.set(path + ".timestamp", entry.timestamp());
        data.set(path + ".prev-hash", entry.prevHash());
        data.set(path + ".hash", entry.hash());
    }

    @Override
    public CompletableFuture<List<AuditEntry>> auditPage(UUID staff, String action, int page, int size) {
        return allAudit().thenApply(list -> {
            List<AuditEntry> filtered = new ArrayList<>();
            for (AuditEntry entry : list) {
                if (staff != null && !staff.equals(entry.staffUuid())) {
                    continue;
                }
                if (action != null && !action.isBlank() && !entry.action().startsWith(action)) {
                    continue;
                }
                filtered.add(entry);
            }
            java.util.Collections.reverse(filtered);
            int from = Math.min(Math.max(0, page * size), filtered.size());
            int to = Math.min(from + size, filtered.size());
            return new ArrayList<>(filtered.subList(from, to));
        });
    }

    @Override
    public CompletableFuture<List<AuditEntry>> auditAll() {
        return allAudit();
    }

    private CompletableFuture<List<AuditEntry>> allAudit() {
        return run(() -> {
            List<AuditEntry> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("audit");
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null) {
                    continue;
                }
                AuditEntry entry = AuditEntry.of(ActionSource.fromString(node.getString("source")),
                        parseUuid(node.getString("staff-uuid")), node.getString("staff-name"), node.getString("action"));
                entry.id(node.getString("id", key))
                        .targetUuid(parseUuid(node.getString("target-uuid")))
                        .targetName(node.getString("target-name"))
                        .details(node.getString("details", ""))
                        .timestamp(node.getLong("timestamp"))
                        .prevHash(node.getString("prev-hash", ""))
                        .hash(node.getString("hash", ""));
                out.add(entry);
            }
            out.sort(Comparator.comparingLong((AuditEntry entry) -> entry.timestamp()));
            return out;
        });
    }

    /** Persists an audit line; called by the audit writer. Kept public for the audit flush path. */
    public void persistAudit(AuditEntry entry) {
        writeAudit(entry);
        save();
    }

    // ------------------------------------------------- locations, names
    @Override
    public CompletableFuture<Void> saveLocation(SavedLocation location) {
        return run(() -> {
            String path = "locations." + location.player() + "." + location.kind();
            data.set(path + ".world", location.world());
            data.set(path + ".x", location.x());
            data.set(path + ".y", location.y());
            data.set(path + ".z", location.z());
            data.set(path + ".yaw", (double) location.yaw());
            data.set(path + ".pitch", (double) location.pitch());
            data.set(path + ".saved-at", location.savedAt());
            data.set(path + ".cause", location.cause());
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<SavedLocation>> location(UUID player, String kind) {
        return run(() -> {
            String path = "locations." + player + "." + kind;
            ConfigurationSection section = data.getConfigurationSection(path);
            if (section == null) {
                return Optional.<SavedLocation>empty();
            }
            return Optional.of(new SavedLocation(player, kind, section.getString("world"),
                    section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"), (float) section.getDouble("pitch"),
                    section.getLong("saved-at"), section.getString("cause")));
        });
    }

    @Override
    public CompletableFuture<Void> saveNameHistory(UUID uuid, String name, long firstSeen) {
        return run(() -> {
            String path = "name-history." + uuid + "." + name.toLowerCase(Locale.ROOT);
            if (!data.contains(path + ".first-seen")) {
                data.set(path + ".first-seen", firstSeen);
            }
            data.set(path + ".name", name);
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<List<String>> nameHistory(UUID uuid) {
        return run(() -> {
            List<String> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("name-history." + uuid);
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                out.add(section.getString(key + ".name", key));
            }
            return out;
        });
    }

    // ------------------------------------------------------------ watching
    @Override
    public CompletableFuture<Void> saveWatch(WatchEntry entry) {
        return run(() -> {
            String path = "watchlist." + entry.uuid();
            data.set(path + ".uuid", entry.uuid().toString());
            data.set(path + ".name", entry.name());
            data.set(path + ".added-by", entry.addedBy());
            data.set(path + ".reason", entry.reason());
            data.set(path + ".added-at", entry.addedAt());
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteWatch(UUID uuid) {
        return run(() -> {
            data.set("watchlist." + uuid, null);
            save();
            return null;
        });
    }

    @Override
    public CompletableFuture<List<WatchEntry>> watchlist() {
        return run(() -> {
            List<WatchEntry> out = new ArrayList<>();
            ConfigurationSection section = data.getConfigurationSection("watchlist");
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ConfigurationSection node = section.getConfigurationSection(key);
                out.add(new WatchEntry(uuid,
                        node == null ? key : node.getString("name", key),
                        node == null ? "console" : node.getString("added-by", "console"),
                        node == null ? "" : node.getString("reason", ""),
                        node == null ? 0L : node.getLong("added-at")));
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<Optional<WatchEntry>> watchEntry(UUID uuid) {
        return watchlist().thenApply(list -> {
            for (WatchEntry entry : list) {
                if (entry.uuid().equals(uuid)) {
                    return Optional.of(entry);
                }
            }
            return Optional.<WatchEntry>empty();
        });
    }

    @Override
    public CompletableFuture<String> exportAll() {
        return allPunishments().thenApply(punishments -> {
            StringBuilder out = new StringBuilder("{\"punishments\":[");
            for (int i = 0; i < punishments.size(); i++) {
                Punishment punishment = punishments.get(i);
                if (i > 0) {
                    out.append(',');
                }
                out.append(Json.object()
                        .put("id", punishment.id())
                        .put("type", punishment.type().name())
                        .put("target", punishment.displayTarget())
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

    // ------------------------------------------------------------ helpers
    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static <T extends Enum<T>> T enumOrNull(Class<T> type, String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String str(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static UUID parseUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Exposed so {@code /vsm backup} can write a timestamped copy. */
    public File file() {
        return file;
    }
}