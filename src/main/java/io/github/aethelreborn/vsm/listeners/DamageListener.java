package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/** Freeze, vanish and staff-mode protections (spec 11.2, 11.3, 11.5). */
public final class DamageListener implements Listener {

    private final VisualStudioManagement plugin;

    public DamageListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        var uuid = player.getUniqueId();
        if (plugin.freezeService().isFrozen(uuid) && plugin.vsmConfig().freezeBlockDamageDealt()) {
            event.setCancelled(true);
            return;
        }
        if (plugin.staffModeService().isActive(uuid) && plugin.vsmConfig().staffModeInvulnerable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (plugin.freezeService().isFrozen(attacker.getUniqueId())
                && plugin.vsmConfig().freezeBlockDamageDealt()) {
            event.setCancelled(true);
            return;
        }
        // Creative safeguards for the YouTuber role (spec 8.8).
        if (plugin.youTuberService().blockPvp(attacker)) {
            event.setCancelled(true);
            plugin.messageService().send(attacker, "youtuber-creative-pvp-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player hidden && plugin.vanishService().isVanished(hidden)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.freezeService().isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.vsmConfig().vanishBlockItemPickup()
                && plugin.vanishService().isVanished(player)
                && !plugin.permissionService().has(player,
                io.github.aethelreborn.vsm.config.PermissionNodes.VANISH_INTERACT)) {
            event.setCancelled(true);
        }
        if (plugin.staffModeService().isActive(player.getUniqueId())
                && !plugin.vsmConfig().staffModeAllowBuilding()) {
            event.setCancelled(true);
        }
    }
}