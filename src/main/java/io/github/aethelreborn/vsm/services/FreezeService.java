package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Freeze (spec 11.2). Position changes are blocked while head rotation stays allowed, teleports and
 * interactions are cancelled, an anti-logout hook notifies staff, and YouTuber freezes carry an owner tag
 * so only that YouTuber can release them.
 */
public final class FreezeService {

    /** Marker prefix for YouTuber owned freezes. */
    public static final String YOUTUBER_SCOPE_PREFIX = "ytb:";

    /** State for a frozen player. */
    public static final class FreezeRecord {
        private final UUID player;
        private final String reason;
        private final UUID staffUuid;
        private final String staffName;
        private final long since;
        private final String ownerScope;
        private final long autoReleaseAt;

        public FreezeRecord(UUID player, String reason, UUID staffUuid, String staffName, String ownerScope,
                            long autoReleaseAt) {
            this.player = player;
            this.reason = reason;
            this.staffUuid = staffUuid;
            this.staffName = staffName;
            this.ownerScope = ownerScope;
            this.since = System.currentTimeMillis();
            this.autoReleaseAt = autoReleaseAt;
        }

        public UUID player() {
            return player;
        }

        public String reason() {
            return reason;
        }

        public UUID staffUuid() {
            return staffUuid;
        }

        public String staffName() {
            return staffName;
        }

        public String ownerScope() {
            return ownerScope;
        }

        public long since() {
            return since;
        }

        public long autoReleaseAt() {
            return autoReleaseAt;
        }

        public boolean youTuberOwned() {
            return ownerScope != null && ownerScope.startsWith(YOUTUBER_SCOPE_PREFIX);
        }
    }

    private final VsmConfig config;
    private final MessageService messages;
    private final AuditService audit;
    private final PermissionService permissions;
    private final SchedulerAdapter scheduler;
    private final Map<UUID, FreezeRecord> frozen = new ConcurrentHashMap<>();

    public FreezeService(VsmConfig config, MessageService messages, AuditService audit,
                         PermissionService permissions, SchedulerAdapter scheduler) {
        this.config = config;
        this.messages = messages;
        this.audit = audit;
        this.permissions = permissions;
        this.scheduler = scheduler;
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.containsKey(uuid);
    }

    public FreezeRecord record(UUID uuid) {
        return frozen.get(uuid);
    }

    public List<UUID> frozenPlayers() {
        return new ArrayList<>(frozen.keySet());
    }

    /** Freezes a player. {@code ownerScope} carries the YouTuber ownership tag. */
    public boolean freeze(UUID target, String reason, UUID staffUuid, String staffName, String ownerScope,
                          long autoReleaseAt) {
        Player player = Bukkit.getPlayer(target);
        if (player == null) {
            return false;
        }
        if (permissions.has(target, PermissionNodes.FREEZE_EXEMPT)) {
            return false;
        }
        FreezeRecord record = new FreezeRecord(target, reason, staffUuid, staffName, ownerScope, autoReleaseAt);
        frozen.put(target, record);
        messages.freezeNotice(player, reason, staffName);
        audit.log(ActionSource.COMMAND, staffUuid, staffName,
                record.youTuberOwned() ? "YOUTUBER_FREEZE" : "FREEZE", target, player.getName(),
                "reason=" + reason + (ownerScope == null ? "" : " owner=" + ownerScope));
        return true;
    }

    /** Toggles the frozen state, reporting the new state. */
    public boolean toggle(UUID target, String reason, UUID staffUuid, String staffName) {
        if (isFrozen(target)) {
            unfreeze(target, staffUuid, staffName);
            return false;
        }
        freeze(target, reason == null ? "No reason given" : reason, staffUuid, staffName, null, 0L);
        return true;
    }

    /** Releases a freeze. */
    public boolean unfreeze(UUID target, UUID staffUuid, String staffName) {
        FreezeRecord record = frozen.remove(target);
        if (record == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(target);
        if (player != null) {
            messages.unfreezeNotice(player);
        }
        audit.log(ActionSource.COMMAND, staffUuid, staffName,
                record.youTuberOwned() ? "YOUTUBER_UNFREEZE" : "UNFREEZE", target,
                player == null ? String.valueOf(target) : player.getName(), "reason=" + record.reason());
        return true;
    }

    /** A YouTuber may only release freezes they own themselves. */
    public boolean releaseOwned(UUID target, UUID youTuberUuid) {
        FreezeRecord record = frozen.get(target);
        if (record == null || !record.youTuberOwned()) {
            return false;
        }
        String scope = record.ownerScope();
        if (!scope.equals(YOUTUBER_SCOPE_PREFIX + youTuberUuid)) {
            return false;
        }
        return unfreeze(target, youTuberUuid, nameOf(youTuberUuid));
    }

    /** Releases every freeze owned by a YouTuber, used when they disconnect. */
    public int releaseAllOwned(UUID youTuberUuid) {
        String scope = YOUTUBER_SCOPE_PREFIX + youTuberUuid;
        int released = 0;
        for (FreezeRecord record : new ArrayList<>(frozen.values())) {
            if (scope.equals(record.ownerScope())) {
                if (unfreeze(record.player(), youTuberUuid, nameOf(youTuberUuid))) {
                    released++;
                }
            }
        }
        return released;
    }

    private String nameOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? String.valueOf(uuid) : player.getName();
    }

    /** Anti-logout handling (spec 11.2). */
    public void onQuit(Player player) {
        FreezeRecord record = frozen.remove(player.getUniqueId());
        if (record == null) {
            return;
        }
        if (record.youTuberOwned() || !config.freezePunishOnLogoutEnabled()) {
            return;
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (permissions.has(staff, PermissionNodes.ALERTS)) {
                messages.send(staff, "freeze-logout-alert",
                        "player", player.getName(),
                        "reason", record.reason(),
                        "staff", record.staffName());
            }
        }
        audit.log(ActionSource.COMMAND, null, "system", "FREEZE_LOGOUT", player.getUniqueId(),
                player.getName(), "reason=" + record.reason());
        pendingLogoutPunishment.put(player.getUniqueId(), record);
    }

    private final Map<UUID, FreezeRecord> pendingLogoutPunishment = new ConcurrentHashMap<>();

    /** Re-applies the freeze when the player rejoins (spec 11.2). */
    public void onJoin(Player player) {
        FreezeRecord record = pendingLogoutPunishment.remove(player.getUniqueId());
        if (record == null) {
            return;
        }
        frozen.put(player.getUniqueId(), record);
        messages.freezeNotice(player, record.reason(), record.staffName());
    }

    /** Auto-release sweep for YouTuber freezes. */
    public void sweepAutoRelease() {
        long now = System.currentTimeMillis();
        for (FreezeRecord record : new ArrayList<>(frozen.values())) {
            if (record.autoReleaseAt() > 0L && record.autoReleaseAt() <= now) {
                frozen.remove(record.player());
                Player player = Bukkit.getPlayer(record.player());
                if (player != null) {
                    messages.send(player, "freeze-auto-released");
                }
                audit.log(ActionSource.COMMAND, record.staffUuid(), record.staffName(),
                        "FREEZE_AUTO_RELEASE", record.player(),
                        player == null ? String.valueOf(record.player()) : player.getName(),
                        "owner=" + record.ownerScope());
            }
        }
    }

    /** Allowed-command whitelist while frozen (spec 11.2). */
    public boolean isCommandAllowed(String command) {
        if (!config.freezeBlockCommands()) {
            return true;
        }
        String normalised = command.toLowerCase(java.util.Locale.ROOT);
        for (String allowed : config.freezeAllowedCommands()) {
            if (normalised.equals(allowed) || normalised.startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }

    /** Drops all state on disable (spec 4.3). */
    public void releaseAll() {
        for (FreezeRecord record : new ArrayList<>(frozen.values())) {
            Player player = Bukkit.getPlayer(record.player());
            if (player != null) {
                messages.unfreezeNotice(player);
            }
        }
        frozen.clear();
        pendingLogoutPunishment.clear();
    }

    public Location lastLocation(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? null : player.getLocation();
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }
}