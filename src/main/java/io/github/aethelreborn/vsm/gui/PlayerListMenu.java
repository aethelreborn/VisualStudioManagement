package io.github.aethelreborn.vsm.gui;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.gui.framework.Menu;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Online player list used from the staff menu (spec 16). */
public final class PlayerListMenu extends Menu {

    public PlayerListMenu(VisualStudioManagement plugin) {
        super(plugin, "Online players", 54);
    }

    public static void open(VisualStudioManagement plugin, Player viewer) {
        new PlayerListMenu(plugin).open(viewer);
    }

    @Override
    protected void build(Player viewer) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Rank: &f" + plugin.permissionService().rankOf(online));
            lore.add("&7Ping: &f" + online.getPing() + "ms");
            lore.add("&7World: &f" + online.getWorld().getName());
            lore.add("");
            lore.add("&eLeft click: teleport");
            lore.add("&eRight click: info menu");
            set(slot++, new MenuItem(Material.PLAYER_HEAD, "&e" + online.getName(), lore, null, 1,
                    context -> {
                        if (context.clickType() == Menu.ClickType.RIGHT) {
                            PlayerInfoMenu.open(plugin, context.viewer(), online);
                            return;
                        }
                        if (!plugin.permissionService().has(context.viewer(), PermissionNodes.TP)) {
                            plugin.messageService().send(context.viewer(), "no-permission");
                            return;
                        }
                        plugin.teleportService().teleport(context.viewer(), online.getLocation(), "staff-menu");
                    }));
        }
        set(53, new MenuItem(Material.BARRIER, "&cClose", List.of(), null, 1,
                context -> context.viewer().closeInventory()));
    }
}