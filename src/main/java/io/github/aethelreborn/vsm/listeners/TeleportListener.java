package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Teleport handling (spec 11.2): frozen players cannot escape through portals, ender pearls or chorus
 * fruit, and staff teleports record the previous location.
 */
public final class TeleportListener implements Listener {

    private final VisualStudioManagement plugin;

    public TeleportListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.freezeService().isFrozen(player.getUniqueId())) {
            // Internal teleports come from the plugin itself and are allowed.
            if (!isInternal(event)) {
                event.setCancelled(true);
                plugin.messageService().send(player, "frozen-cannot-teleport");
                return;
            }
        }
        if (plugin.teleportService().isWorldBlacklisted(player.getWorld().getName())
                && plugin.freezeService().isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean isInternal(PlayerTeleportEvent event) {
        // Teleports issued by the plugin run while the player is frozen (move / vsm move) and are
        // flagged through the cause; vanilla caused teleports are not.
        return event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && plugin.freezeService().isFrozen(event.getPlayer().getUniqueId());
    }

    /** Records a {@code /back} location when a player dies (spec 18.1). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (plugin.freezeService().isFrozen(event.getEntity().getUniqueId())) {
            for (Player staff : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (plugin.permissionService().has(staff, io.github.aethelreborn.vsm.config.PermissionNodes.ALERTS)) {
                    plugin.messageService().send(staff, "frozen-player-died",
                            "player", event.getEntity().getName());
                }
            }
        }
        if (plugin.watchService().watching(event.getEntity().getUniqueId())) {
            for (Player staff : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (plugin.permissionService().has(staff, io.github.aethelreborn.vsm.config.PermissionNodes.WATCH)) {
                    plugin.messageService().send(staff, "watchlist-death-alert",
                            "player", event.getEntity().getName());
                }
            }
        }
        // Saving a death location lets /back return the player there after respawn.
        var location = event.getEntity().getLocation();
        plugin.storage().saveLocation(new io.github.aethelreborn.vsm.models.SavedLocation(
                event.getEntity().getUniqueId(), io.github.aethelreborn.vsm.models.SavedLocation.KIND_BACK,
                location.getWorld() == null ? "world" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
                System.currentTimeMillis(), "death"));
    }

    /** Frozen players cannot pick up or drop items. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (plugin.freezeService().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getItemDrop().getItemStack().getType() == Material.AIR) {
            event.setCancelled(true);
        }
    }
}