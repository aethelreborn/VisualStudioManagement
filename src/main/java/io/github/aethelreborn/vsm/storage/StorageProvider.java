package io.github.aethelreborn.vsm.storage;

import io.github.aethelreborn.vsm.models.AuditEntry;
import io.github.aethelreborn.vsm.models.IpRecord;
import io.github.aethelreborn.vsm.models.PlayerNote;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.Report;
import io.github.aethelreborn.vsm.models.SavedLocation;
import io.github.aethelreborn.vsm.models.StaffMember;
import io.github.aethelreborn.vsm.models.WatchEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * All persistence goes through this asynchronous interface (spec 3.2, 15.1). Every method returns a
 * {@link CompletableFuture}; callers must never block on it from the main thread.
 */
public interface StorageProvider {

    /** Human readable name used in {@code /vsm debug} and startup logs. */
    String name();

    CompletableFuture<Void> init();

    CompletableFuture<Void> shutdown();

    // -------------------------------------------------------------- staff
    CompletableFuture<Map<UUID, StaffMember>> loadStaff();

    CompletableFuture<Void> saveStaff(StaffMember member);

    CompletableFuture<Void> deleteStaff(UUID uuid);

    // ------------------------------------------------------- punishments
    CompletableFuture<Void> savePunishment(Punishment punishment);

    CompletableFuture<List<Punishment>> loadActivePunishments();

    CompletableFuture<List<Punishment>> history(UUID target, int page, int size);

    CompletableFuture<List<Punishment>> byStaff(UUID staff, int page, int size);

    CompletableFuture<List<Punishment>> allHistory(int page, int size);

    CompletableFuture<Optional<Punishment>> byId(String id);

    CompletableFuture<List<Punishment>> activeForIp(String ipOrHash);

    CompletableFuture<Void> purgePlayer(UUID uuid);

    // ----------------------------------------------------------------- ip
    CompletableFuture<Void> recordLogin(UUID uuid, String ip, long now);

    CompletableFuture<List<IpRecord>> ipHistory(UUID uuid);

    CompletableFuture<List<UUID>> accountsOnIp(String ip);

    CompletableFuture<String> lastKnownIp(UUID uuid);

    CompletableFuture<Void> saveIpAllow(UUID uuid, String addedBy, long addedAt);

    CompletableFuture<Void> removeIpAllow(UUID uuid);

    CompletableFuture<List<UUID>> loadIpAllow();

    CompletableFuture<Void> pruneIpHistory(long olderThan);

    // ------------------------------------------------------ notes, reports
    CompletableFuture<Void> saveNote(PlayerNote note);

    CompletableFuture<Void> deleteNote(String id);

    CompletableFuture<List<PlayerNote>> notes(UUID target);

    CompletableFuture<List<PlayerNote>> pinnedNotes(UUID target);

    CompletableFuture<Report> createReport(Report report);

    CompletableFuture<Void> saveReport(Report report);

    CompletableFuture<Optional<Report>> reportById(long id);

    CompletableFuture<List<Report>> reports(String statusFilter, UUID assignedTo, int page, int size);

    CompletableFuture<Integer> countReports(String statusFilter, UUID assignedTo);

    CompletableFuture<List<Report>> reportsAgainst(UUID target);

    // --------------------------------------------------------------- audit
    CompletableFuture<Void> saveAudit(AuditEntry entry);

    CompletableFuture<List<AuditEntry>> auditPage(UUID staff, String action, int page, int size);

    CompletableFuture<List<AuditEntry>> auditAll();

    // ------------------------------------------------- locations, names
    CompletableFuture<Void> saveLocation(SavedLocation location);

    CompletableFuture<Optional<SavedLocation>> location(UUID player, String kind);

    CompletableFuture<Void> saveNameHistory(UUID uuid, String name, long firstSeen);

    CompletableFuture<List<String>> nameHistory(UUID uuid);

    // ------------------------------------------------------------ watching
    CompletableFuture<Void> saveWatch(WatchEntry entry);

    CompletableFuture<Void> deleteWatch(UUID uuid);

    CompletableFuture<List<WatchEntry>> watchlist();

    CompletableFuture<Optional<WatchEntry>> watchEntry(UUID uuid);

    /** Full logical backup used by {@code /vsm backup}. */
    CompletableFuture<String> exportAll();
}