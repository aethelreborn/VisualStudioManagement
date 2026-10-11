package io.github.aethelreborn.vsm.gui;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.gui.framework.Menu;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.services.ActionRequest;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Player information menu (spec 11.6): the summary plus one-click actions, each of which goes through
 * {@code ActionService}.
 */
public final class PlayerInfoMenu extends Menu {

    private final Player target;

    private PlayerInfoMenu(VisualStudioManagement plugin, Player target) {
        super(plugin, "Player: " + (target == null ? "unknown" : target.getName()), 45);
        this.target = target;
    }

    public static void open(VisualStudioManagement plugin, Player viewer, Player target) {
        if (target == null) {
            plugin.messageService().send(viewer, "player-not-found");
            return;
        }
        new PlayerInfoMenu(plugin, target).open(viewer);
    }

    @Override
    protected void build(Player viewer) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        var uuid = target.getUniqueId();
        long now = System.currentTimeMillis();

        List<String> lore = new ArrayList<>();
        lore.add("&7Rank: &f" + orUnknown(plugin.permissionService().rankOf(uuid)));
        lore.add("&7First join: &f" + (target.getFirstPlayed() == 0 ? "unknown"
                : io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(target.getFirstPlayed())));
        lore.add("&7Last join: &f" + io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(target.getLastPlayed()));
        lore.add("&7Ping: &f" + target.getPing() + "ms");
        lore.add("&7World: &f" + target.getWorld().getName());
        lore.add("&7IP: &f" + plugin.ipService().display(
                target.getAddress() == null ? null : target.getAddress().getAddress().getHostAddress(), viewer));
        lore.add("&7Active punishments: &f"
                + plugin.punishmentCache().activeFor(uuid, now).size());
        lore.add("&7Warnings: &f" + plugin.punishmentCache().activeWarnings(uuid, now).size());
        set(4, new MenuItem(Material.PLAYER_HEAD, "&e" + target.getName(), lore, null, 1, context -> {
        }));

        action(10, Material.PAPER, "Warn", PermissionNodes.WARN, context ->
                punish(context.viewer(), PunishmentType.WARN));
        action(12, Material.ORANGE_DYE, "Mute", PermissionNodes.TEMP_MUTE, context ->
                punish(context.viewer(), PunishmentType.TEMP_MUTE));
        action(14, Material.LEATHER_BOOTS, "Kick", PermissionNodes.KICK, context ->
                punish(context.viewer(), PunishmentType.KICK));
        action(16, Material.RED_DYE, "Ban", PermissionNodes.BAN, context ->
                punish(context.viewer(), PunishmentType.BAN));
        action(19, Material.ICE, "Freeze", PermissionNodes.FREEZE, context ->
                plugin.freezeService().toggle(target.getUniqueId(), "player-info-menu",
                        context.viewer().getUniqueId(), context.viewer().getName()));
        action(21, Material.COMPASS, "Teleport", PermissionNodes.TP, context ->
                plugin.teleportService().teleport(context.viewer(), target.getLocation(), "player-info"));
        action(23, Material.CHEST, "Inventory", PermissionNodes.INVSEE, context ->
                plugin.openInvsee(context.viewer(), target, false));
        action(25, Material.WRITTEN_BOOK, "History", PermissionNodes.HISTORY, context ->
                HistoryMenu.open(plugin, context.viewer(), uuid, target.getName()));
        set(40, new MenuItem(Material.BARRIER, "&cClose", List.of(), null, 1,
                context -> context.viewer().closeInventory()));
    }

    private void action(int slot, Material material, String label, String permission,
                        java.util.function.Consumer<ClickContext> handler) {
        set(slot, new MenuItem(material, "&e" + label, List.of("&7Target: &f" + target.getName()),
                permission, 1, context -> {
                    if (!plugin.permissionService().outranks(context.viewer(), target.getUniqueId())) {
                        plugin.messageService().send(context.viewer(), "cannot-punish-higher");
                        return;
                    }
                    handler.accept(context);
                }));
    }

    private void punish(Player viewer, PunishmentType type) {
        String permission = switch (type) {
            case WARN -> PermissionNodes.WARN;
            case TEMP_MUTE -> PermissionNodes.TEMP_MUTE;
            case KICK -> PermissionNodes.KICK;
            case BAN -> PermissionNodes.BAN;
            default -> PermissionNodes.PUNISH;
        };
        var result = plugin.actionService().execute(ActionRequest.builder(ActionSource.GUI)
                .kind(ActionRequest.Kind.PUNISH)
                .actor(viewer.getUniqueId(), viewer.getName())
                .permission(permission)
                .punishment(type)
                .target(target.getUniqueId(), target.getName())
                .reason("player-info-menu")
                .permanent(type == PunishmentType.BAN || type == PunishmentType.KICK)
                , viewer);
        if (result.isSuccess()) {
            plugin.messageService().send(viewer, "punish-menu-applied", "type", type.name());
            viewer.closeInventory();
        }
    }

    private static String orUnknown(String value) {
        return value == null ? "none" : value;
    }

    public static Player resolve(VisualStudioManagement plugin, String name) {
        return Bukkit.getPlayerExact(name);
    }
}