package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.storage.cache.PunishmentCache;
import io.github.aethelreborn.vsm.util.IdGenerator;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Creates, removes and expires punishments. Callers never touch storage directly: everything goes
 * through {@link ActionService}, which performs the permission and outranking checks first (spec 4.2).
 */
public final class PunishmentService {

    private final VsmConfig config;
    private final StorageProvider storage;
    private final PunishmentCache cache;
    private final SchedulerAdapter scheduler;
    private final MessageService messages;
    private final AtomicInteger idCounter = new AtomicInteger();

    public PunishmentService(VsmConfig config, StorageProvider storage, PunishmentCache cache,
                             SchedulerAdapter scheduler, MessageService messages) {
        this.config = config;
        this.storage = storage;
        this.cache = cache;
        this.scheduler = scheduler;
        this.messages = messages;
    }

    /** Creates and stores a punishment record and updates every cache index. */
    public Punishment issue(PunishmentType type, UUID targetUuid, String targetName, String reason,
                            UUID staffUuid, String staffName, long durationMillis, boolean silent,
                            String evidence, String templateId, int ladderStep, String parentId, String scope) {
        long now = System.currentTimeMillis();
        Punishment punishment = Punishment.of(type, targetUuid, targetName, reason);
        punishment.id(nextId());
        punishment.staffUuid(staffUuid);
        punishment.staffName(staffName);
        punishment.issuedAt(now);
        punishment.expiresAt(durationMillis == TimeParser.PERMANENT ? TimeParser.PERMANENT : now + durationMillis);
        punishment.active(true);
        punishment.silent(silent);
        punishment.evidence(evidence);
        punishment.templateId(templateId);
        punishment.ladderStep(ladderStep);
        punishment.parentId(parentId);
        punishment.scope(scope);
        punishment.server(config.serverName());

        cache.add(punishment);
        storage.savePunishment(punishment);
        return punishment;
    }

    /** Stores a fully prepared punishment without touching id generation (used by importers). */
    public Punishment store(Punishment punishment) {
        cache.add(punishment);
        storage.savePunishment(punishment);
        return punishment;
    }

    private String nextId() {
        if (config.idNumericMode()) {
            return IdGenerator.numeric(System.nanoTime());
        }
        String prefix = config.idPrefix();
        return IdGenerator.unique(prefix, 6, 16, id -> cache.byId(id).isPresent());
    }

    /** Lifts a punishment and notifies the affected player if they are online. */
    public void remove(Punishment punishment, UUID by, String byName, String reason) {
        punishment.remove(by, byName, reason, System.currentTimeMillis());
        cache.remove(punishment);
        storage.savePunishment(punishment);
        notifyLift(punishment);
    }

    private void notifyLift(Punishment punishment) {
        Player online = punishment.targetUuid() == null ? null : Bukkit.getPlayer(punishment.targetUuid());
        if (online != null) {
            scheduler.sync(() -> messages.send(online, "punishment-lifted",
                    "type", punishment.type().name(),
                    "reason", punishment.removalReason() == null ? "" : punishment.removalReason()));
        }
    }

    /** Applies an in-place change to an active punishment (spec 8.2 {@code /punishment modify}). */
    public void modify(Punishment punishment, Long newDuration, String newReason) {
        if (newDuration != null) {
            long now = System.currentTimeMillis();
            punishment.expiresAt(newDuration == TimeParser.PERMANENT ? TimeParser.PERMANENT : now + newDuration);
            punishment.active(true);
        }
        if (newReason != null) {
            punishment.reason(newReason);
        }
        cache.remove(punishment);
        cache.add(punishment);
        storage.savePunishment(punishment);
    }

    /** Kicks an online player when a new ban arrives (spec 8.2 rule 6). */
    public void enforceOnline(Punishment punishment) {
        if (punishment.targetUuid() == null) {
            return;
        }
        Player online = Bukkit.getPlayer(punishment.targetUuid());
        if (online == null) {
            return;
        }
        if (!punishment.type().isBan() && punishment.type() != PunishmentType.KICK) {
            return;
        }
        scheduler.sync(() -> messages.banScreenKick(online, punishment));
    }

    /**
     * Expiry sweep (spec 9.4). Runs every 30-60 seconds off the main thread and notifies online players.
     */
    public List<Punishment> expireIfNeeded() {
        long now = System.currentTimeMillis();
        List<Punishment> expired = cache.expired(now);
        List<Punishment> lifted = new ArrayList<>();
        for (Punishment punishment : expired) {
            punishment.active(false);
            punishment.removedAt(now);
            punishment.removalReason("expired");
            cache.remove(punishment);
            storage.savePunishment(punishment);
            lifted.add(punishment);
            Player online = punishment.targetUuid() == null ? null : Bukkit.getPlayer(punishment.targetUuid());
            if (online != null) {
                scheduler.sync(() -> messages.send(online, "punishment-expired", "type", punishment.type().name()));
            }
        }
        return lifted;
    }

    public Optional<Punishment> byId(String id) {
        return cache.byId(id);
    }

    public List<Punishment> activeFor(UUID uuid) {
        return cache.activeFor(uuid, System.currentTimeMillis());
    }

    public List<Punishment> allActive() {
        return cache.allActive(System.currentTimeMillis());
    }

    public PunishmentCache cache() {
        return cache;
    }

    /** Counts active punishments issued by a staff member, for {@code /staff info}. */
    public java.util.Map<String, Integer> countsByStaff(UUID staff) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Punishment punishment : storage.byStaff(staff, 0, 1000).join()) {
            counts.merge(punishment.type().name(), 1, Integer::sum);
        }
        return counts;
    }

    /** Removes every record attached to an IP ban's parent id (spec 8.2 rule 2). */
    public List<Punishment> childrenOf(String parentId) {
        if (parentId == null) {
            return List.of();
        }
        List<Punishment> out = new ArrayList<>();
        for (Punishment punishment : storage.allHistory(0, 10_000).join()) {
            if (parentId.equals(punishment.parentId())) {
                out.add(punishment);
            }
        }
        return out;
    }

    /** Public broadcast honouring the per-type configuration and the silent flag. */
    public void broadcast(Punishment punishment, String staffName) {
        if (punishment.silent() || !config.broadcast(punishment.type().configKey())) {
            return;
        }
        messages.broadcast(punishment, staffName);
    }

    /** Staff notification, sent even when the public broadcast is silenced. */
    public void notifyStaff(Punishment punishment, String staffName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            messages.notifyStaffIfEnabled(player, punishment, staffName);
        }
    }

    public int generatedIds() {
        return idCounter.get();
    }
}