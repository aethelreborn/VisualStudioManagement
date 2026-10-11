package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Freeze enforcement (spec 11.2). Position changes are cancelled while head rotation stays allowed by
 * comparing only the block coordinates of the movement.
 */
public final class MoveListener implements Listener {

    private final VisualStudioManagement plugin;

    public MoveListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.freezeService().isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return; // head rotation only
        }
        Location corrected = from.clone();
        corrected.setYaw(to.getYaw());
        corrected.setPitch(to.getPitch());
        event.setTo(corrected);
    }
}