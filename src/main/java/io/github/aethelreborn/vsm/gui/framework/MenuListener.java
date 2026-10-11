package io.github.aethelreborn.vsm.gui.framework;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Routes every click inside a plugin menu to its holder (spec 16). The event is cancelled first, then the
 * permission is re-checked by the holder before any action runs.
 */
public final class MenuListener implements Listener {

    private final VisualStudioManagement plugin;

    public MenuListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof VsmMenuHolder holder) {
            event.setCancelled(true);
            holder.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof VsmMenuHolder) {
            event.setCancelled(true);
        }
    }
}