package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.gui.ReportMenu;
import io.github.aethelreborn.vsm.models.Report;
import io.github.aethelreborn.vsm.services.ReportService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Reports and help (spec 8.6). */
public final class ReportCommands {

    private ReportCommands() {
    }

    /** Every literal this module owns, in tab-completion order. */
    public static java.util.List<String> literals() {
        return java.util.List.of("report", "reports", "helpop");
    }

    /** Builds a single sub-command by literal name, or {@code null} when this module has none. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                  VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "report" -> report(plugin);
            case "reports" -> reports(plugin);
            case "helpop" -> helpOp(plugin);
            default -> null;
        };
    }

    /** Attaches every command this module owns to the given parent literal. */
    public static void add(LiteralArgumentBuilder<CommandSourceStack> parent,
                           VisualStudioManagement plugin) {
        for (String literal : literals()) {
            LiteralArgumentBuilder<CommandSourceStack> node = build(literal, plugin);
            if (node != null) {
                parent.then(node);
            }
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> report(VisualStudioManagement plugin) {
        return Commands.literal("report")
                .requires(Cmd.requires(plugin, PermissionNodes.REPORT))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> create(plugin, context))));
    }

    private static int create(VisualStudioManagement plugin,
                              com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        String targetName = StringArgumentType.getString(context, "player");
        OfflinePlayer target = Cmd.offlinePlayer(targetName);
        ReportService.Result result = plugin.reportService().create(context.getSource().getSender(),
                actor == null ? null : actor.getUniqueId(), actor == null ? "console" : actor.getName(),
                target.getUniqueId(), String.valueOf(target.getName()),
                StringArgumentType.getString(context, "reason"));
        switch (result.outcome()) {
            case CREATED -> plugin.messageService().send(context.getSource().getSender(), "report-created",
                    "id", Long.toString(result.report().id()));
            case MERGED -> plugin.messageService().send(context.getSource().getSender(), "report-merged",
                    "id", Long.toString(result.report().id()));
            case SELF_REPORT -> plugin.messageService().send(context.getSource().getSender(), "report-self");
            case COOLDOWN -> plugin.messageService().send(context.getSource().getSender(), "report-cooldown",
                    "seconds", io.github.aethelreborn.vsm.util.TimeParser.format(result.cooldownMillis()));
            case LIMIT -> plugin.messageService().send(context.getSource().getSender(), "report-limit");
            case TARGET_IS_STAFF -> plugin.messageService().send(context.getSource().getSender(), "report-staff");
            default -> plugin.messageService().send(context.getSource().getSender(), "report-invalid");
        }
        return result.outcome() == ReportService.Outcome.CREATED ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reports(VisualStudioManagement plugin) {
        return Commands.literal("reports")
                .requires(Cmd.requires(plugin, PermissionNodes.REPORTS_VIEW))
                .executes(context -> openMenu(plugin, context))
                .then(Commands.literal("open")
                        .executes(context -> openMenu(plugin, context)))
                .then(Commands.literal("list")
                        .then(Commands.literal("open").executes(context -> list(plugin, context, "OPEN")))
                        .then(Commands.literal("mine").executes(context -> list(plugin, context, "MINE")))
                        .then(Commands.literal("claimed").executes(context -> list(plugin, context, "CLAIMED")))
                        .then(Commands.literal("closed").executes(context -> list(plugin, context, null))))
                .then(Commands.literal("claim")
                        .requires(Cmd.requires(plugin, PermissionNodes.REPORTS_CLAIM))
                        .then(Commands.argument("id", LongArgumentType.longArg())
                                .executes(context -> claim(plugin, context, LongArgumentType.getLong(context, "id")))))
                .then(Commands.literal("assign")
                        .requires(Cmd.requires(plugin, PermissionNodes.REPORTS_ASSIGN))
                        .then(Commands.argument("id", LongArgumentType.longArg())
                                .then(Commands.argument("staff", StringArgumentType.word())
                                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                                        .executes(context -> assign(plugin, context)))))
                .then(Commands.literal("close")
                        .requires(Cmd.requires(plugin, PermissionNodes.REPORTS_CLOSE))
                        .then(Commands.argument("id", LongArgumentType.longArg())
                                .then(Commands.argument("note", StringArgumentType.greedyString())
                                        .executes(context -> close(plugin, context)))))
                .then(Commands.literal("teleport")
                        .requires(Cmd.requires(plugin, PermissionNodes.REPORTS_TELEPORT))
                        .then(Commands.argument("id", LongArgumentType.longArg())
                                .executes(context -> teleport(plugin, context))));
    }

    private static int openMenu(VisualStudioManagement plugin,
                                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        Player player = Cmd.requirePlayer(context.getSource(), plugin);
        if (player == null) {
            return 0;
        }
        ReportMenu.open(plugin, player, 0);
        return 1;
    }

    private static int list(VisualStudioManagement plugin,
                            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String filter) {
        java.util.UUID actor = Cmd.actorUuid(context.getSource());
        String status = "OPEN".equals(filter) ? "OPEN" : null;
        boolean mine = "MINE".equals(filter);
        boolean claimed = "CLAIMED".equals(filter);
        var all = plugin.reportService().list(status, null, 0, 100);
        var reports = all.stream()
                .filter(report -> !mine || actor != null && actor.equals(report.reporterUuid()))
                .filter(report -> !claimed || report.assignedStaff() != null)
                .toList();
        plugin.messageService().send(context.getSource().getSender(), "reports-header",
                "count", Integer.toString(reports.size()));
        for (Report report : reports) {
            plugin.messageService().send(context.getSource().getSender(), "reports-entry",
                    "id", Long.toString(report.id()),
                    "target", report.targetName(),
                    "reporter", report.reporterName(),
                    "reason", report.reason(),
                    "status", report.status().name(),
                    "priority", report.priority().name());
        }
        return reports.size();
    }

    private static int claim(VisualStudioManagement plugin,
                             com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, long id) {
        boolean ok = plugin.reportService().claim(context.getSource().getSender(), Cmd.actorUuid(context.getSource()), id);
        plugin.messageService().send(context.getSource().getSender(), ok ? "report-claimed" : "action-failed",
                "id", Long.toString(id));
        return ok ? 1 : 0;
    }

    private static int assign(VisualStudioManagement plugin,
                              com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        long id = LongArgumentType.getLong(context, "id");
        String staffName = StringArgumentType.getString(context, "staff");
        var report = plugin.storage().reportById(id).join();
        if (report.isEmpty()) {
            plugin.messageService().send(context.getSource().getSender(), "report-not-found");
            return 0;
        }
        report.get().assignedStaff(Cmd.offlinePlayer(staffName).getUniqueId().toString());
        report.get().status(Report.Status.IN_PROGRESS);
        plugin.storage().saveReport(report.get());
        plugin.messageService().send(context.getSource().getSender(), "report-assigned",
                "id", Long.toString(id), "staff", staffName);
        return 1;
    }

    private static int close(VisualStudioManagement plugin,
                             com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        long id = LongArgumentType.getLong(context, "id");
        boolean ok = plugin.reportService().close(context.getSource().getSender(),
                Cmd.actorUuid(context.getSource()), id, StringArgumentType.getString(context, "note"),
                Report.Status.RESOLVED);
        plugin.messageService().send(context.getSource().getSender(), ok ? "report-closed" : "action-failed",
                "id", Long.toString(id));
        return ok ? 1 : 0;
    }

    private static int teleport(VisualStudioManagement plugin,
                                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        long id = LongArgumentType.getLong(context, "id");
        var report = plugin.storage().reportById(id).join();
        if (report.isEmpty()) {
            plugin.messageService().send(context.getSource().getSender(), "report-not-found");
            return 0;
        }
        Player target = Bukkit.getPlayer(report.get().targetUuid());
        if (target == null) {
            plugin.messageService().send(context.getSource().getSender(), "player-not-found");
            return 0;
        }
        return plugin.teleportService().teleport(actor, target.getLocation(), "report") ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> helpOp(VisualStudioManagement plugin) {
        return Commands.literal("helpop")
                .requires(Cmd.requires(plugin, PermissionNodes.HELP_OP))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            Player player = Cmd.requirePlayer(context.getSource(), plugin);
                            if (player == null) {
                                return 0;
                            }
                            plugin.messageService().send(context.getSource().getSender(), "helpop-sent");
                            int alerted = 0;
                            for (Player staff : Bukkit.getOnlinePlayers()) {
                                if (plugin.permissionService().has(staff, PermissionNodes.STAFF_CHAT)) {
                                    plugin.messageService().send(staff, "helpop-alert",
                                            "player", player.getName(),
                                            "message", StringArgumentType.getString(context, "message"));
                                    alerted++;
                                }
                            }
                            return Math.max(1, alerted);
                        }));
    }
}