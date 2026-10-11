package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.WatchEntry;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Staff watchlist (spec 8.5 {@code /watch}): join alerts and command monitoring. */
public final class WatchService {

    private final StorageProvider storage;
    private final MessageService messages;
    private final AuditService audit;
    private final PermissionService permissions;

    public WatchService(StorageProvider storage, MessageService messages, AuditService audit,
                        PermissionService permissions) {
        this.storage = storage;
        this.messages = messages;
        this.audit = audit;
        this.permissions = permissions;
    }

    public void load() {
        // Entries are read on demand; nothing to preload beyond the storage connection being ready.
    }

    public WatchEntry add(UUID uuid, String name, UUID actorUuid, String actorName, String reason) {
        WatchEntry entry = new WatchEntry(uuid, name, actorName, reason, System.currentTimeMillis());
        storage.saveWatch(entry);
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "WATCH_ADD", uuid, name, reason);
        return entry;
    }

    public void remove(UUID uuid, UUID actorUuid, String actorName) {
        storage.deleteWatch(uuid);
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "WATCH_REMOVE", uuid, null, "");
    }

    public boolean watching(UUID uuid) {
        return storage.watchEntry(uuid).isPresent();
    }

    public Optional<WatchEntry> entry(UUID uuid) {
        return storage.watchEntry(uuid);
    }

    public List<WatchEntry> all() {
        return storage.watchlist().join();
    }

    /** Alerts staff when a watched player joins (spec 8.5). */
    public void alertJoin(Player joining) {
        Optional<WatchEntry> entry = entry(joining.getUniqueId());
        if (entry.isEmpty()) {
            return;
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (permissions.has(staff, io.github.aethelreborn.vsm.config.PermissionNodes.WATCH)) {
                messages.send(staff, "watchlist-join-alert",
                        "player", joining.getName(),
                        "reason", entry.get().reason(),
                        "added_by", entry.get().addedBy());
            }
        }
    }
}