package io.github.aethelreborn.vsm.gui;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.gui.framework.Menu;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The staff menu (spec 16): the shortcuts every staff member uses. */
public final class StaffMenu extends Menu {

    public StaffMenu(VisualStudioManagement plugin) {
        super(plugin, "Staff Menu", 45);
    }

    public static void open(VisualStudioManagement plugin, Player viewer) {
        new StaffMenu(plugin).open(viewer);
    }

    @Override
    protected void build(Player viewer) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        set(11, new MenuItem(Material.BOOK, "&eReports",
                List.of("&7Browse open reports.", "&eLeft click to open."),
                PermissionNodes.REPORTS_VIEW, 1, context ->
                ReportMenu.open(plugin, context.viewer(), 0)));
        set(13, new MenuItem(Material.PAPER, "&eFreeze tool",
                List.of("&7Freeze whoever you right click."),
                PermissionNodes.FREEZE, 1, context ->
                plugin.messageService().send(context.viewer(), "staffmode-tool-enabled", "tool", "freeze")));
        set(15, new MenuItem(Material.ENDER_EYE, "&eVanish toggle",
                List.of("&7Hides you from players without", "&7vsm.vanish.see"),
                PermissionNodes.VANISH, 1, context ->
                plugin.vanishService().toggle(context.viewer(), context.viewer().getUniqueId(),
                        context.viewer().getName())));
        set(21, new MenuItem(Material.CHEST, "&eInventory viewer",
                List.of("&7Inspect a player's inventory."),
                PermissionNodes.INVSEE, 1, context ->
                plugin.messageService().send(context.viewer(), "invsee-use-hint")));
        set(23, new MenuItem(Material.COMPASS, "&eOnline players",
                List.of("&7Teleport and lookup tools."),
                PermissionNodes.TP, 1, context -> PlayerListMenu.open(plugin, context.viewer())));
        set(25, new MenuItem(Material.WRITTEN_BOOK, "&ePunishment history",
                List.of("&7Browse recent punishments."),
                PermissionNodes.HISTORY, 1, context -> HistoryMenu.open(plugin, context.viewer(), 0)));
        set(27, new MenuItem(Material.IRON_AXE, "&ePunish",
                List.of("&7Open the punish menu."),
                PermissionNodes.PUNISH, 1, context -> PunishMenu.open(plugin, context.viewer(), null)));
        set(31, new MenuItem(Material.DIAMOND, "&eStaff mode",
                List.of("&7Save your state and get the tool bar."),
                PermissionNodes.STAFF_MODE, 1, context ->
                plugin.staffModeService().toggle(context.viewer(), context.viewer().getUniqueId(),
                        context.viewer().getName())));
        set(33, new MenuItem(Material.WRITABLE_BOOK, "&eAudit log",
                List.of("&7Recent staff actions."),
                PermissionNodes.AUDIT_VIEW, 1, context ->
                plugin.messageService().send(context.viewer(), "audit-use-hint")));
        set(40, new MenuItem(Material.CHEST, "&eMy profile",
                List.of("&7Your rank and recent actions."),
                PermissionNodes.STAFF_INFO, 1, context ->
                PlayerInfoMenu.open(plugin, context.viewer(), context.viewer())));
        set(44, new MenuItem(Material.BARRIER, "&cClose", List.of(), null, 1,
                context -> context.viewer().closeInventory()));
    }
}