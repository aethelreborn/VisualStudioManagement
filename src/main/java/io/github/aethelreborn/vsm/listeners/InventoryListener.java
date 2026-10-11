package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.gui.framework.VsmMenuHolder;
import io.github.aethelreborn.vsm.services.InvseeSession;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Inventory safety (spec 11.4, 16): menus always cancel and re-check permissions, and the read-only
 * inventory viewer blocks taking, placing, dragging and shift-clicking.
 */
public final class InventoryListener implements Listener {

    private final VisualStudioManagement plugin;

    public InventoryListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof VsmMenuHolder holder) {
            event.setCancelled(true);
            holder.handleClick(event);
            return;
        }
        UUID viewer = event.getWhoClicked().getUniqueId();
        InvseeSession session = plugin.invseeSession(viewer);
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (!session.editable()) {
            plugin.messageService().send(event.getWhoClicked(), "invsee-read-only");
            return;
        }
        // Edits are written straight back on the main thread and audited.
        plugin.scheduler().syncLater(() -> session.flush(), 1L, 0L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof VsmMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (plugin.invseeSession(event.getWhoClicked().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (plugin.invseeSession(player.getUniqueId()) != null) {
            plugin.closeInvsee(player);
        }
    }
}