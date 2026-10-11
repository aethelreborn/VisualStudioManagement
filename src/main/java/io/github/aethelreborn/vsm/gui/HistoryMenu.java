package io.github.aethelreborn.vsm.gui;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.gui.framework.Menu;
import io.github.aethelreborn.vsm.gui.framework.PaginatedMenu;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.util.TimeParser;
import io.github.aethelreborn.vsm.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Paginated punishment history with a type filter (spec 16). */
public final class HistoryMenu extends PaginatedMenu {

    private final UUID targetUuid;
    private final String targetName;
    private final List<PunishmentTypeFilter> filters = List.of(
            new PunishmentTypeFilter("All", null),
            new PunishmentTypeFilter("Bans", "ban"),
            new PunishmentTypeFilter("Mutes", "mute"),
            new PunishmentTypeFilter("Warns", "warn"),
            new PunishmentTypeFilter("Kicks", "kick"));
    private String filter = "All";

    private HistoryMenu(VisualStudioManagement plugin, UUID targetUuid, String targetName, String filter) {
        super(plugin, "History: " + targetName, 54, 36);
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.filter = filter;
    }

    public static void open(VisualStudioManagement plugin, Player viewer, int page) {
        new HistoryMenu(plugin, viewer.getUniqueId(), viewer.getName(), "All").open(viewer);
    }

    public static void open(VisualStudioManagement plugin, Player viewer, UUID targetUuid, String targetName) {
        new HistoryMenu(plugin, targetUuid, targetName, "All").open(viewer);
    }

    @Override
    protected void build(Player viewer) {
        List<Punishment> all = plugin.storage().history(targetUuid, 0, 200).join();
        List<Menu.MenuItem> entries = new ArrayList<>();
        for (Punishment punishment : all) {
            if (!matchesFilter(punishment)) {
                continue;
            }
            entries.add(new Menu.MenuItem(Material.PAPER,
                    "&e" + punishment.type().name() + " &7(" + punishment.id() + ")",
                    List.of(
                            "&7Staff: &f" + punishment.staffName(),
                            "&7Reason: &f" + punishment.reason(),
                            "&7Issued: &f" + TimeUtil.formatTimestamp(punishment.issuedAt()),
                            "&7Expires: &f" + (punishment.isPermanent() ? "never"
                                    : TimeParser.formatRemaining(punishment.expiresAt(), System.currentTimeMillis())),
                            "&7Active: &f" + punishment.active()),
                    null, 1, context -> plugin.messageService().send(context.viewer(), "punishment-detail",
                            "id", punishment.id(),
                            "type", punishment.type().name(),
                            "target", punishment.displayTarget(),
                            "reason", punishment.reason() == null ? "" : punishment.reason(),
                            "staff", punishment.staffName() == null ? "Console" : punishment.staffName(),
                            "expires", punishment.isPermanent() ? "never"
                                    : TimeParser.formatRemaining(punishment.expiresAt(), System.currentTimeMillis()))));
        }
        setEntries(entries);
        super.build(viewer);

        int slot = 46;
        for (PunishmentTypeFilter option : filters) {
            Material material = option.name().equals(filter) ? Material.LIME_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            set(slot++, new MenuItem(material, "&e" + option.name(), List.of(), null, 1, context ->
                    new HistoryMenu(plugin, targetUuid, targetName, option.name()).open(context.viewer())));
        }
        set(53, new MenuItem(Material.BARRIER, "&cClose", List.of(), null, 1,
                context -> context.viewer().closeInventory()));
    }

    private boolean matchesFilter(Punishment punishment) {
        return switch (filter) {
            case "Bans" -> punishment.type().isBan();
            case "Mutes" -> punishment.type().isMute();
            case "Warns" -> punishment.type().isWarn();
            case "Kicks" -> punishment.type() == io.github.aethelreborn.vsm.models.PunishmentType.KICK;
            default -> true;
        };
    }

    private record PunishmentTypeFilter(String name, String key) {
    }

    /** {@code true} when the viewer may open the history of another player. */
    public static boolean mayOpen(VisualStudioManagement plugin, Player viewer, UUID target) {
        if (target.equals(viewer.getUniqueId())) {
            return plugin.permissionService().has(viewer, PermissionNodes.HISTORY);
        }
        return plugin.permissionService().has(viewer, PermissionNodes.HISTORY_OTHERS);
    }

    public static Player resolvePlayer(VisualStudioManagement plugin, String name) {
        return Bukkit.getPlayerExact(name);
    }
}