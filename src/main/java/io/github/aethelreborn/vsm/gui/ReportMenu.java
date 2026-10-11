package io.github.aethelreborn.vsm.gui;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.gui.framework.Menu;
import io.github.aethelreborn.vsm.models.Report;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Report browsing (spec 10, 16): paper item per report, click actions are claim / teleport / close and
 * every click re-checks the matching node.
 */
public final class ReportMenu extends Menu {

    private int page;
    private final List<Report> reports;

    private ReportMenu(VisualStudioManagement plugin, int page) {
        super(plugin, "Reports", 54);
        this.page = Math.max(0, page);
        this.reports = plugin.reportService().list(null, null, this.page, 45);
    }

    public static void open(VisualStudioManagement plugin, Player viewer, int page) {
        new ReportMenu(plugin, page).open(viewer);
    }

    @Override
    protected void build(Player viewer) {
        fill(Material.BLACK_STAINED_GLASS_PANE, " ");
        int slot = 0;
        for (Report report : reports) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Reporter: &f" + report.reporterName());
            lore.add("&7Reason: &f" + report.reason());
            lore.add("&7Status: &f" + report.status().name());
            lore.add("&7Priority: &f" + report.priority().name());
            lore.add("&7Assigned: &f" + (report.assignedStaff() == null ? "unassigned" : report.assignedStaff()));
            lore.add("&7Age: &f" + report.age(System.currentTimeMillis()));
            lore.add("");
            lore.add("&eLeft click: claim");
            lore.add("&eRight click: teleport");
            lore.add("&eShift right: close");
            lore.add("&eMiddle click: player info");
            Material material = report.priority() == Report.Priority.HIGH ? Material.REDSTONE : Material.PAPER;
            set(slot++, new MenuItem(material, "&eReport #" + report.id(), lore, null, 1,
                    context -> handle(context, report)));
        }
        if (page > 0) {
            set(48, new MenuItem(Material.ARROW, "&ePrevious page", List.of(), null, 1, context ->
                    open(plugin, context.viewer(), page - 1)));
        }
        if (plugin.reportService().count(null, null) > (page + 1) * reports.size() + reports.size()) {
            set(52, new MenuItem(Material.ARROW, "&eNext page", List.of(), null, 1, context ->
                    open(plugin, context.viewer(), page + 1)));
        }
        set(53, new MenuItem(Material.BARRIER, "&cClose", List.of(), null, 1,
                context -> context.viewer().closeInventory()));
    }

    private void handle(ClickContext context, Report report) {
        Player viewer = context.viewer();
        switch (context.clickType()) {
            case LEFT -> {
                if (!plugin.permissionService().has(viewer, PermissionNodes.REPORTS_CLAIM)) {
                    plugin.messageService().send(viewer, "no-permission");
                    return;
                }
                plugin.reportService().claim(viewer, viewer.getUniqueId(), report.id());
                plugin.messageService().send(viewer, "report-claimed", "id", Long.toString(report.id()));
                open(plugin, viewer, page);
            }
            case RIGHT -> {
                if (!plugin.permissionService().has(viewer, PermissionNodes.REPORTS_TELEPORT)) {
                    plugin.messageService().send(viewer, "no-permission");
                    return;
                }
                Player target = Bukkit.getPlayer(report.targetUuid());
                if (target == null) {
                    plugin.messageService().send(viewer, "player-not-found");
                    return;
                }
                plugin.teleportService().teleport(viewer, target.getLocation(), "report");
            }
            case SHIFT_RIGHT -> {
                if (!plugin.permissionService().has(viewer, PermissionNodes.REPORTS_CLOSE)) {
                    plugin.messageService().send(viewer, "no-permission");
                    return;
                }
                plugin.reportService().close(viewer, viewer.getUniqueId(), report.id(), "closed from the report menu",
                        Report.Status.RESOLVED);
                plugin.messageService().send(viewer, "report-closed", "id", Long.toString(report.id()));
                open(plugin, viewer, page);
            }
            case MIDDLE -> PlayerInfoMenu.open(plugin, viewer, Bukkit.getPlayer(report.targetUuid()));
            default -> {
            }
        }
    }
}