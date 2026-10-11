package io.github.aethelreborn.vsm.gui;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.gui.framework.Menu;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.services.ActionRequest;
import io.github.aethelreborn.vsm.models.ActionSource;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The punish menu (spec 16): pick a punishment, then confirm. Every action still goes through
 * {@code ActionService}, so the GUI can never bypass a check.
 */
public final class PunishMenu extends Menu {

    private final UUID targetUuid;
    private final String targetName;

    private PunishMenu(VisualStudioManagement plugin, UUID targetUuid, String targetName) {
        super(plugin, "Punish: " + targetName, 27);
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public static void open(VisualStudioManagement plugin, Player viewer, Player target) {
        if (target == null) {
            plugin.messageService().send(viewer, "player-not-found");
            return;
        }
        new PunishMenu(plugin, target.getUniqueId(), target.getName()).open(viewer);
    }

    @Override
    protected void build(Player viewer) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        put(10, "Warn", Material.PAPER, PermissionNodes.WARN, PunishmentType.WARN);
        put(12, "Temporary mute", Material.ORANGE_DYE, PermissionNodes.TEMP_MUTE, PunishmentType.TEMP_MUTE);
        put(14, "Temporary ban", Material.RED_DYE, PermissionNodes.TEMP_BAN, PunishmentType.TEMP_BAN);
        put(16, "Kick", Material.LEATHER_BOOTS, PermissionNodes.KICK, PunishmentType.KICK);
        put(22, "Permanent ban", Material.BEDROCK, PermissionNodes.BAN, PunishmentType.BAN);
        if (plugin.permissionService().has(viewer, PermissionNodes.IP_BAN)) {
            put(24, "IP ban", Material.SKELETON_SKULL, PermissionNodes.IP_BAN, PunishmentType.IP_BAN);
        }
        set(26, new MenuItem(Material.BARRIER, "&cClose", List.of(), null, 1,
                context -> context.viewer().closeInventory()));
    }

    private void put(int slot, String label, Material material, String permission, PunishmentType type) {
        set(slot, new MenuItem(material, "&e" + label,
                List.of("&7Target: &f" + targetName,
                        "&7Left click to issue",
                        "&7Shift click to cancel"),
                permission, 1, context -> {
                    if (context.clickType() == Menu.ClickType.SHIFT_LEFT) {
                        plugin.messageService().send(context.viewer(), "action-cancelled");
                        return;
                    }
                    issue(context.viewer(), type);
                }));
    }

    private void issue(Player viewer, PunishmentType type) {
        var target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            plugin.messageService().send(viewer, "player-not-found");
            return;
        }
        String permission = switch (type) {
            case WARN -> PermissionNodes.WARN;
            case TEMP_MUTE -> PermissionNodes.TEMP_MUTE;
            case TEMP_BAN -> PermissionNodes.TEMP_BAN;
            case KICK -> PermissionNodes.KICK;
            case BAN -> PermissionNodes.BAN;
            case IP_BAN -> PermissionNodes.IP_BAN;
            default -> PermissionNodes.PUNISH;
        };
        var result = plugin.actionService().execute(ActionRequest.builder(ActionSource.GUI)
                .kind(ActionRequest.Kind.PUNISH)
                .actor(viewer.getUniqueId(), viewer.getName())
                .permission(permission)
                .punishment(type)
                .target(target.getUniqueId(), target.getName())
                .reason("punish-menu")
                .permanent(type == PunishmentType.BAN || type == PunishmentType.IP_BAN || type == PunishmentType.KICK)
                .template(null)
                .evidence(null)
                , viewer);
        if (result.isSuccess()) {
            plugin.messageService().send(viewer, "punish-menu-applied", "type", type.name());
            viewer.closeInventory();
        }
    }
}