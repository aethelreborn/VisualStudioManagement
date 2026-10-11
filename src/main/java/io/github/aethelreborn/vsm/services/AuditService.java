package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.AuditEntry;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.storage.YamlStorage;
import io.github.aethelreborn.vsm.util.IdGenerator;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.github.aethelreborn.vsm.util.TimeUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Every staff action creates an {@link AuditEntry} (spec 12). Writes are queued and flushed by a single
 * async writer; the main thread only enqueues.
 *
 * <p>Entries form a SHA-256 chain so edits and deletions are detectable with {@code /vsm audit verify}
 * (spec 13.4).
 */
public final class AuditService {

    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(5);
    private static final int MAX_QUEUE = 10_000;

    private final Logger logger;
    private final StorageProvider storage;
    private final VsmConfig config;
    private final Path logDirectory;
    private final ConcurrentLinkedQueue<AuditEntry> queue = new ConcurrentLinkedQueue<>();
    private final Deque<AuditEntry> chainMemory = new ArrayDeque<>();
    private volatile boolean tamperEvident;
    private volatile boolean consoleMajorActions;
    private volatile long lastHash = "";

    public AuditService(Logger logger, StorageProvider storage, VsmConfig config, Path logDirectory) {
        this.logger = logger;
        this.storage = storage;
        this.config = config;
        this.logDirectory = logDirectory;
        this.tamperEvident = config.auditTamperEvident();
        this.consoleMajorActions = config.auditConsoleMajorActions();
    }

    public void applyConfig() {
        this.tamperEvident = config.auditTamperEvident();
        this.consoleMajorActions = config.auditConsoleMajorActions();
    }

    /** Queues an entry. Never blocks and never touches the disk on the calling thread. */
    public AuditEntry log(ActionSource source, UUID staffUuid, String staffName, String action,
                          UUID targetUuid, String targetName, String details) {
        AuditEntry entry = AuditEntry.of(source, staffUuid, staffName, action);
        entry.id(IdGenerator.generate("AUD", 8));
        entry.targetUuid(targetUuid);
        entry.targetName(targetName);
        entry.details(details == null ? "" : details);
        entry.prevHash(tamperEvident ? lastHash : "");
        entry.hash(tamperEvident ? hash(lastHash, entry.canonical()) : "");
        if (tamperEvident) {
            lastHash = entry.hash();
        }
        if (queue.size() > MAX_QUEUE) {
            logger.warning("Audit queue exceeded " + MAX_QUEUE + " entries; flushing early.");
            flushAsync();
        }
        queue.add(entry);
        chainMemory.addLast(entry);
        while (chainMemory.size() > 5_000) {
            chainMemory.pollFirst();
        }
        if (consoleMajorActions && isMajor(action)) {
            logger.info("[AUDIT] " + entry.format());
        }
        return entry;
    }

    private boolean isMajor(String action) {
        return action.contains("BAN") || action.contains("KICK") || action.contains("PUNISH")
                || action.startsWith("STAFF_") || action.contains("RATE_LIMIT") || action.contains("LOCKDOWN");
    }

    /** Schedules a flush on the storage pool; safe to call from any thread. */
    public void flushAsync() {
        List<AuditEntry> batch = drain();
        if (batch.isEmpty()) {
            return;
        }
        for (AuditEntry entry : batch) {
            storage.saveAudit(entry).exceptionally(throwable -> {
                logger.warning("Could not persist audit entry " + entry.id());
                return null;
            });
        }
        writeLogFiles(batch);
    }

    private List<AuditEntry> drain() {
        List<AuditEntry> batch = new ArrayList<>();
        AuditEntry entry;
        while ((entry = queue.poll()) != null && batch.size() < 512) {
            batch.add(entry);
        }
        return batch;
    }

    /** Blocking flush used on shutdown. */
    public void flushBlocking() {
        List<AuditEntry> batch = drain();
        writeLogFiles(batch);
    }

    private void writeLogFiles(List<AuditEntry> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(logDirectory);
            batch.sort(java.util.Comparator.comparingLong(AuditEntry::timestamp));
            String day = TimeUtil.formatDate(batch.get(0).timestamp());
            StringBuilder builder = new StringBuilder();
            for (AuditEntry entry : batch) {
                builder.append(entry.format()).append(System.lineSeparator());
            }
            Path file = logDirectory.resolve("audit-" + day + ".log");
            boolean exists = Files.exists(file);
            try (OutputStream out = new java.io.BufferedOutputStream(
                    new java.io.FileOutputStream(file.toFile(), true), StandardCharsets.UTF_8)) {
                if (!exists) {
                    out.write(("--- Visual Studio Management audit log for " + day + " ---"
                            + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                }
                out.write(builder.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            logger.warning("Could not write the audit log file: " + ex.getMessage());
        }
    }

    /** Persists one entry immediately, used for high-value actions such as bans. */
    public void persist(AuditEntry entry) {
        if (storage instanceof YamlStorage yaml) {
            yaml.persistAudit(entry);
            return;
        }
        storage.saveAudit(entry).exceptionally(throwable -> {
            logger.warning("Could not persist audit entry " + entry.id());
            return null;
        });
    }

    public int pending() {
        return queue.size();
    }

    /** Compresses audit files older than the retention window (spec 12). */
    public void compressOldLogs(int retentionDays) {
        if (retentionDays <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        try (var stream = Files.list(logDirectory)) {
            List<Path> files = stream.filter(path -> path.getFileName().toString().endsWith(".log")).toList();
            for (Path path : files) {
                if (Files.getLastModifiedTime(path).toMillis() > cutoff) {
                    continue;
                }
                Path target = logDirectory.resolve(path.getFileName().toString() + ".gz");
                try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(target))) {
                    Files.copy(path, out);
                }
                Files.delete(path);
            }
        } catch (IOException ex) {
            logger.fine("Could not compress old audit logs: " + ex.getMessage());
        }
    }

    /** Result of {@code /vsm audit verify}. */
    public record Verification(boolean valid, String firstBrokenId, int checked) {
    }

    /** Recomputes the chain and reports the first broken link (spec 13.4). */
    public Verification verify(List<AuditEntry> entries) {
        if (!tamperEvident) {
            return new Verification(true, null, entries.size());
        }
        String previous = "";
        int index = 0;
        for (AuditEntry entry : entries) {
            String expectedPrev = index == 0 ? "" : previous;
            if (!entry.prevHash().equals(expectedPrev)) {
                return new Verification(false, entry.id(), entries.size());
            }
            String expectedHash = hash(expectedPrev, entry.canonical());
            if (!expectedHash.equals(entry.hash())) {
                return new Verification(false, entry.id(), entries.size());
            }
            previous = entry.hash();
            index++;
        }
        return new Verification(true, null, entries.size());
    }

    /** Rebuilds the in-memory head of the chain after a reload. */
    public void resetChain(List<AuditEntry> existing) {
        lastHash = "";
        if (existing.isEmpty()) {
            return;
        }
        AuditEntry last = existing.get(existing.size() - 1);
        lastHash = last.hash() == null ? "" : last.hash();
    }

    static String hash(String previousHash, String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((previousHash == null ? "" : previousHash).getBytes(StandardCharsets.UTF_8));
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = digest.digest();
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    /** Renders a line for the console, masking anything sensitive per the privacy settings. */
    public String describe(AuditEntry entry) {
        String details = entry.details();
        if (!config.logRawIps()) {
            details = TextUtil.stripMentions(details);
        }
        return String.format(Locale.ROOT, "[%s] %s %s%s | Source: %s",
                TimeUtil.formatTimestamp(entry.timestamp()),
                entry.staffName(),
                entry.action(),
                details.isEmpty() ? "" : " | " + details,
                entry.source().name());
    }

    public boolean tamperEvident() {
        return tamperEvident;
    }

    Duration flushInterval() {
        return FLUSH_INTERVAL;
    }
}