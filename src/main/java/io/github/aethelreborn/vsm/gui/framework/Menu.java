package io.github.aethelreborn.vsm.gui.framework;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Base menu (spec 16).
 *
 * <p>Every menu uses a custom {@link InventoryHolder} so listeners can identify it reliably rather than by
 * inventory title, and every click is cancelled, permission-checked and debounced.
 */
public class Menu implements InventoryHolder {

    protected final VisualStudioManagement plugin;
    protected final Map<Integer, MenuItem> items = new HashMap<>();
    private Inventory inventory;
    private String title = "Menu";
    private int size = 27;

    public Menu(VisualStudioManagement plugin, String title, int size) {
        this.plugin = plugin;
        this.title = title;
        this.size = Math.max(9, Math.min(54, (size / 9 + 1) * 9));
    }

    /** Builds the inventory content. Implementations fill {@link #items()}. */
    protected void build(Player viewer) {
    }

    public void open(Player viewer) {
        items.clear();
        build(viewer);
        Inventory target = Bukkit.createInventory(new VsmMenuHolder(plugin, this), size,
                net.kyori.adventure.text.Component.text(title));
        for (Map.Entry<Integer, MenuItem> entry : items.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < target.getSize()) {
                target.setItem(entry.getKey(), entry.getValue().render());
            }
        }
        inventory = target;
        viewer.openInventory(target);
    }

    /** Refreshes the open menu in place. */
    public void refresh(Player viewer) {
        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof VsmMenuHolder holder
                && holder.menu() == this) {
            open(viewer);
        }
    }

    public Map<Integer, MenuItem> items() {
        return items;
    }

    public void set(int slot, MenuItem item) {
        items.put(slot, item);
    }

    public void fill(Material material, String displayName) {
        for (int slot = 0; slot < size; slot++) {
            if (!items.containsKey(slot)) {
                items.put(slot, MenuItem.simple(material, displayName));
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(new VsmMenuHolder(plugin, this), size,
                    net.kyori.adventure.text.Component.text(title));
        }
        return inventory;
    }

    public String title() {
        return title;
    }

    /** A single clickable entry. */
    public static final class MenuItem {

        private final Material material;
        private final String displayName;
        private final List<String> lore;
        private final Consumer<ClickContext> action;
        private final String permission;
        private final int amount;

        public MenuItem(Material material, String displayName, List<String> lore, String permission,
                        int amount, Consumer<ClickContext> action) {
            this.material = material;
            this.displayName = displayName;
            this.lore = lore == null ? List.of() : new ArrayList<>(lore);
            this.permission = permission;
            this.amount = Math.max(1, amount);
            this.action = action;
        }

        public static MenuItem simple(Material material, String displayName) {
            return new MenuItem(material, displayName, List.of(), null, 1, context -> {
            });
        }

        public Material material() {
            return material;
        }

        public List<String> lore() {
            return lore;
        }

        public String permission() {
            return permission;
        }

        public ItemStack render() {
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(displayName));
                if (!lore.isEmpty()) {
                    List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
                    for (String line : lore) {
                        lines.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(line));
                    }
                    meta.lore(lines);
                }
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
            return item;
        }

        public void click(ClickContext context) {
            if (action != null) {
                action.accept(context);
            }
        }
    }

    /** Context handed to every click handler. */
    public record ClickContext(Player viewer, Menu menu, int slot, ClickType clickType, MenuItem item) {

        public enum ClickType {
            LEFT,
            RIGHT,
            SHIFT_LEFT,
            SHIFT_RIGHT,
            MIDDLE
        }
    }

    /** Debounce map so a double click cannot run an action twice. */
    public static final Map<UUID, Long> LAST_CLICK = new HashMap<>();

    public static boolean debounced(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = LAST_CLICK.get(uuid);
        if (last != null && now - last < 250L) {
            return true;
        }
        LAST_CLICK.put(uuid, now);
        return false;
    }
}