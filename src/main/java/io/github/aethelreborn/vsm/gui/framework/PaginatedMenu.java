package io.github.aethelreborn.vsm.gui.framework;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A paginated menu (spec 16): previous / next arrows plus per-page item callbacks.
 */
public class PaginatedMenu extends Menu {

    private final List<MenuItem> pageItems = new ArrayList<>();
    private int page;
    private final int itemsPerPage;

    public PaginatedMenu(VisualStudioManagement plugin, String title, int size, int itemsPerPage) {
        super(plugin, title, size);
        this.itemsPerPage = itemsPerPage;
    }

    public void setEntries(List<MenuItem> entries) {
        pageItems.clear();
        pageItems.addAll(entries);
        page = 0;
    }

    public int pages() {
        return Math.max(1, (int) Math.ceil(pageItems.size() / (double) itemsPerPage));
    }

    public int page() {
        return page;
    }

    @Override
    protected void build(Player viewer) {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, pageItems.size());
        int slot = 10;
        for (int i = start; i < end && slot < size - 9; i++, slot++) {
            set(slot, pageItems.get(i));
        }
        if (page > 0) {
            set(size - 9, new MenuItem(Material.ARROW, "&ePrevious page", List.of(), null, 1,
                    context -> {
                        page--;
                        open(context.viewer());
                    }));
        }
        if (page < pages() - 1) {
            set(size - 8, new MenuItem(Material.ARROW, "&eNext page", List.of(), null, 1,
                    context -> {
                        page++;
                        open(context.viewer());
                    }));
        }
        set(size - 1, new MenuItem(Material.BARRIER, "&cClose", List.of(), null, 1, context ->
                context.viewer().closeInventory()));
    }

    /** Convenience for a simple one-page list. */
    public static PaginatedMenu single(VisualStudioManagement plugin, String title, List<MenuItem> entries) {
        PaginatedMenu menu = new PaginatedMenu(plugin, title, 27, 21);
        menu.setEntries(entries);
        return menu;
    }

    public ItemStack placeholder(Material material, String name) {
        return new ItemStack(material);
    }
}