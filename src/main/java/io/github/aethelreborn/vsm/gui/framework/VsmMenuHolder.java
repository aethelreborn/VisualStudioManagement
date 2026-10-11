package io.github.aethelreborn.vsm.gui.framework;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Inventory holder used by every menu (spec 16). Listeners recognise menus through this class, never by
 * comparing the inventory title.
 */
public final class VsmMenuHolder implements InventoryHolder {

    private final VisualStudioManagement plugin;
    private final Menu menu;
    private Inventory inventory;

    public VsmMenuHolder(VisualStudioManagement plugin, Menu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    public Menu menu() {
        return menu;
    }

    public VisualStudioManagement plugin() {
        return plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = menu.getInventory();
        }
        return inventory;
    }

    /** Called by {@link MenuListener}; always on the main thread. */
    public void handleClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (Menu.debounced(event.getWhoClicked().getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        Menu.MenuItem item = menu.items().get(slot);
        if (item == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (item.permission() != null && !plugin.permissionService().has(viewer, item.permission())) {
            plugin.messageService().send(viewer, "no-permission");
            return;
        }
        Menu.ClickType clickType = switch (event.getClick()) {
            case RIGHT -> Menu.ClickType.RIGHT;
            case SHIFT_LEFT -> Menu.ClickType.SHIFT_LEFT;
            case SHIFT_RIGHT -> Menu.ClickType.SHIFT_RIGHT;
            case MIDDLE -> Menu.ClickType.MIDDLE;
            default -> Menu.ClickType.LEFT;
        };
        item.click(new Menu.ClickContext(viewer, menu, slot, clickType, item));
    }

}