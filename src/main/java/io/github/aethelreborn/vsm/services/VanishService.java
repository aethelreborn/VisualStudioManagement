package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Vanish (spec 11.3). Players are hidden from viewers without {@code vsm.vanish.see}, join and leave
 * messages are suppressed, and state can persist across relogs.
 */
public final class VanishService {

    private final VsmConfig config;
    private final PermissionService permissions;
    private final AuditService audit;
    private final MessageService messages;
    private final SchedulerAdapter scheduler;
    private final Map<UUID, Boolean> vanished = new ConcurrentHashMap<>();

    public VanishService(VsmConfig config, PermissionService permissions, AuditService audit,
                         MessageService messages, SchedulerAdapter scheduler) {
        this.config = config;
        this.permissions = permissions;
        this.audit = audit;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    public boolean isVanished(UUID uuid) {
        return Boolean.TRUE.equals(vanished.get(uuid));
    }

    public boolean isVanished(Player player) {
        return player != null && isVanished(player.getUniqueId());
    }

    public List<UUID> vanishedPlayers() {
        return new ArrayList<>(vanished.keySet());
    }

    /** Toggles vanish for a player and updates every viewer. Must run on the main thread. */
    public boolean toggle(Player player, UUID actorUuid, String actorName) {
        if (isVanished(player)) {
            reveal(player, actorUuid, actorName);
            return false;
        }
        hide(player, actorUuid, actorName);
        return true;
    }

    public void hide(Player player, UUID actorUuid, String actorName) {
        vanished.put(player.getUniqueId(), Boolean.TRUE);
        applyVisibility(player);
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "VANISH_ON", player.getUniqueId(),
                player.getName(), "");
        messages.send(player, "vanish-on");
    }

    public void reveal(Player player, UUID actorUuid, String actorName) {
        vanished.remove(player.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            viewer.showPlayer(getPlugin(), player);
        }
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "VANISH_OFF", player.getUniqueId(),
                player.getName(), "");
        messages.send(player, "vanish-off");
    }

    /** Applies the correct visibility of {@code target} for every viewer. */
    public void applyVisibility(Player target) {
        boolean hidden = isVanished(target);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            if (hidden && !permissions.has(viewer, PermissionNodes.VANISH_SEE)) {
                viewer.hidePlayer(getPlugin(), target);
            } else {
                viewer.showPlayer(getPlugin(), target);
            }
        }
    }

    /** Re-applies visibility when a viewer joins or gains the see node. */
    public void refreshFor(Player viewer) {
        boolean canSee = permissions.has(viewer, PermissionNodes.VANISH_SEE);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            if (isVanished(target) && !canSee) {
                viewer.hidePlayer(getPlugin(), target);
            } else {
                viewer.showPlayer(getPlugin(), target);
            }
        }
    }

    /** Restores everybody on disable (spec 4.3). */
    public void releaseAll() {
        for (UUID uuid : new ArrayList<>(vanished.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(uuid)) {
                    continue;
                }
                viewer.showPlayer(getPlugin(), player);
            }
        }
        vanished.clear();
    }

    public void onQuit(Player player) {
        if (!config.vanishPersistAcrossRelogs()) {
            vanished.remove(player.getUniqueId());
        }
    }

    /** Auto vanish on join when configured (spec 11.3). */
    public void onJoin(Player player) {
        if (config.vanishAutoOnJoin() && permissions.has(player, PermissionNodes.VANISH)) {
            hide(player, player.getUniqueId(), player.getName());
        }
    }

    /** Players that should be hidden from the server list ping (spec 11.3). */
    public Set<UUID> hiddenFromPing() {
        return Set.copyOf(vanished.keySet());
    }

    public boolean suppressJoinLeave(Player player) {
        return config.vanishSuppressJoinLeave() && isVanished(player);
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }

    private org.bukkit.plugin.Plugin getPlugin() {
        return io.github.aethelreborn.vsm.VsmRuntime.get();
    }
}