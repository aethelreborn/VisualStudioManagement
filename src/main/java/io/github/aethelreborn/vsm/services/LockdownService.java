package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.storage.cache.StaffCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Lockdown and maintenance gating (spec 8.5). Checked at pre-login and at join for already-connected
 * players.
 */
public final class LockdownService {

    private final VsmConfig config;
    private final MessageService messages;
    private final AuditService audit;
    private final StaffCache staff;

    private volatile boolean lockdown;
    private volatile String lockdownReason = "";
    private volatile boolean maintenance;
    private volatile java.util.Set<String> maintenanceWhitelist = new java.util.LinkedHashSet<>();

    public LockdownService(VsmConfig config, MessageService messages, AuditService audit, StaffCache staff) {
        this.config = config;
        this.messages = messages;
        this.audit = audit;
        this.staff = staff;
        this.maintenance = config.maintenanceEnabled();
        this.maintenanceWhitelist = new java.util.LinkedHashSet<>(config.maintenanceWhitelist());
    }

    public boolean lockdown() {
        return lockdown;
    }

    public boolean maintenance() {
        return maintenance;
    }

    public String lockdownReason() {
        return lockdownReason;
    }

    public void setLockdown(boolean enabled, String reason, UUID actorUuid, String actorName) {
        this.lockdown = enabled;
        this.lockdownReason = reason == null ? "" : reason;
        audit.log(ActionSource.COMMAND, actorUuid, actorName,
                enabled ? "LOCKDOWN_ON" : "LOCKDOWN_OFF", null, null, this.lockdownReason);
    }

    public void setMaintenance(boolean enabled, UUID actorUuid, String actorName) {
        this.maintenance = enabled;
        audit.log(ActionSource.COMMAND, actorUuid, actorName,
                enabled ? "MAINTENANCE_ON" : "MAINTENANCE_OFF", null, null, "");
    }

    public void whitelistAdd(String name) {
        maintenanceWhitelist.add(name.toLowerCase(java.util.Locale.ROOT));
    }

    public void whitelistRemove(String name) {
        maintenanceWhitelist.remove(name.toLowerCase(java.util.Locale.ROOT));
    }

    public java.util.Set<String> whitelist() {
        return java.util.Set.copyOf(maintenanceWhitelist);
    }

    /** {@code true} when the player is allowed to join while a lockdown or maintenance is active. */
    public boolean mayJoin(Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (staff.isStaffRank(uuid)) {
            return true;
        }
        if (maintenance) {
            if (maintenanceWhitelist.contains(player.getName().toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            return false;
        }
        if (lockdown) {
            if (!config.lockdownAllowReturningPlayers()) {
                return false;
            }
            // Returning players are those with stored history; a first-time player is refused.
            return player.hasPlayedBefore();
        }
        return true;
    }

    /** Kicks online players that no longer qualify after a lockdown or IP ban. */
    public int kickDisallowed(String messageKey, java.util.function.Predicate<Player> extra) {
        int count = 0;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : players) {
            boolean allowed = mayJoin(player) && (extra == null || extra.test(player));
            if (!allowed) {
                player.kick(messages.screen(messageKey));
                count++;
            }
        }
        return count;
    }

    public boolean bypasses(Player player) {
        return staff.isStaff(player.getUniqueId());
    }

    /** Exposed so the join listener can reuse the maintenance bypass node. */
    public boolean hasMaintenanceBypass(Player player) {
        return staff.isStaff(player.getUniqueId()) || staff.isStaff(player.getUniqueId());
    }

    /** The permission node checked for maintenance bypass (spec 7). */
    public String maintenanceBypassNode() {
        return PermissionNodes.MAINTENANCE_BYPASS;
    }
}