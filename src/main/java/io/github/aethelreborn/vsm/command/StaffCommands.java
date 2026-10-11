package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.gui.StaffMenu;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.StaffRank;
import io.github.aethelreborn.vsm.services.ActionRequest;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Staff whitelist management (spec 8.1). */
public final class StaffCommands {

    private StaffCommands() {
    }

    public static List<String> literals() {
        return List.of("staff");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                   VisualStudioManagement plugin) {
        if (literal == null || !literal.equalsIgnoreCase("staff")) {
            return null;
        }
        return open(plugin);
    }

    public static void add(LiteralArgumentBuilder<CommandSourceStack> parent, VisualStudioManagement plugin) {
        for (String literal : literals()) {
            LiteralArgumentBuilder<CommandSourceStack> node = build(literal, plugin);
            if (node != null) {
                parent.then(node);
            }
        }
        parent.then(add(plugin));
        parent.then(remove(plugin));
        parent.then(setRank(plugin));
        parent.then(promote(plugin));
        parent.then(demote(plugin));
        parent.then(list(plugin));
        parent.then(info(plugin));
        parent.then(ranks(plugin));
        parent.then(pin(plugin));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> open(VisualStudioManagement plugin) {
        return Commands.literal("staff")
                .requires(Cmd.requires(plugin, PermissionNodes.USE))
                .executes(context -> {
                    org.bukkit.entity.Player player = Cmd.requirePlayer(context.getSource(), plugin);
                    if (player == null) {
                        return 0;
                    }
                    StaffMenu.open(plugin, player);
                    return 1;
                })
                .then(Commands.literal("add").then(target(plugin, PermissionNodes.STAFF_MANAGE,
                        "added", (ctx, targetUuid, name) -> {
                            String rankId = StringArgumentType.getString(ctx, "rank");
                            if (!plugin.rankRegistry().exists(rankId)) {
                                plugin.messageService().send(ctx.getSource().getSender(), "unknown-rank",
                                        "rank", rankId);
                                return false;
                            }
                            if (!plugin.permissionService().canAssignRank(ctx.getSource().getSender(), rankId)) {
                                plugin.messageService().send(ctx.getSource().getSender(), "cannot-promote-to-rank");
                                return false;
                            }
                            plugin.staffService().add(targetUuid, name, rankId, Cmd.actorUuid(ctx.getSource()),
                                    Cmd.actorName(ctx.getSource()));
                            plugin.auditService().log(ActionSource.COMMAND, Cmd.actorUuid(ctx.getSource()),
                                    Cmd.actorName(ctx.getSource()), "STAFF_ADD", targetUuid, name,
                                    "rank=" + rankId);
                            plugin.messageService().send(ctx.getSource().getSender(), "staff-added",
                                    "player", name, "rank", plugin.rankRegistry().display(rankId));
                            return true;
                        })))
                .then(Commands.literal("remove").then(target(plugin, PermissionNodes.STAFF_MANAGE,
                        "removed", (ctx, targetUuid, name) -> {
                            var member = plugin.staffCache().byUuid(targetUuid);
                            if (member == null) {
                                plugin.messageService().send(ctx.getSource().getSender(), "not-staff");
                                return false;
                            }
                            if (!plugin.permissionService().outranks(ctx.getSource().getSender(), targetUuid)
                                    && !plugin.permissionService().has(ctx.getSource().getSender(),
                                    PermissionNodes.OVERRIDE)) {
                                plugin.messageService().send(ctx.getSource().getSender(),
                                        "cannot-punish-higher");
                                return false;
                            }
                            plugin.staffService().remove(targetUuid);
                            plugin.auditService().log(ActionSource.COMMAND, Cmd.actorUuid(ctx.getSource()),
                                    Cmd.actorName(ctx.getSource()), "STAFF_REMOVE", targetUuid, name, "");
                            plugin.messageService().send(ctx.getSource().getSender(), "staff-removed",
                                    "player", name);
                            return true;
                        })))
                .then(Commands.literal("setrank").then(target(plugin, PermissionNodes.STAFF_MANAGE,
                        "rank-set", (ctx, targetUuid, name) -> {
                            String rankId = StringArgumentType.getString(ctx, "rank");
                            var result = plugin.actionService().execute(
                                    Cmd.request(ctx.getSource(), PermissionNodes.STAFF_MANAGE)
                                            .kind(ActionRequest.Kind.RANK_CHANGE)
                                            .target(targetUuid, name)
                                            .rank(rankId)
                                            .reason("staff setrank"),
                                    ctx.getSource().getSender());
                            return result.isSuccess();
                        })))
                .then(Commands.literal("promote").then(target(plugin, PermissionNodes.STAFF_MANAGE,
                        "promoted", (ctx, targetUuid, name) -> step(ctx, plugin, targetUuid, name, true))))
                .then(Commands.literal("demote").then(target(plugin, PermissionNodes.STAFF_MANAGE,
                        "demoted", (ctx, targetUuid, name) -> step(ctx, plugin, targetUuid, name, false))))
                .then(Commands.literal("list")
                        .requires(Cmd.requires(plugin, PermissionNodes.STAFF_LIST))
                        .executes(context -> list(plugin, context)))
                .then(Commands.literal("info")
                        .requires(Cmd.requires(plugin, PermissionNodes.STAFF_INFO))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> staffNames(plugin)))
                                .executes(context -> info(plugin, context,
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("ranks")
                        .requires(Cmd.requires(plugin, PermissionNodes.USE))
                        .executes(context -> ranks(plugin, context)))
                .then(Commands.literal("pin")
                        .then(Commands.literal("set").then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> staffNames(plugin)))
                                .then(Commands.argument("pin", StringArgumentType.word())
                                        .executes(context -> setPin(plugin, context,
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "pin"))))))
                        .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> staffNames(plugin)))
                                .executes(context -> removePin(plugin, context,
                                        StringArgumentType.getString(context, "player")))))));
    }

    /** Shared {@code <player> <rank>} shape. */
    private interface RankAction {
        boolean run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                    UUID targetUuid, String name);
    }

    private interface SimpleAction {
        boolean run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                    UUID targetUuid, String name);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> target(
            VisualStudioManagement plugin, String permission, String key, SimpleAction action) {
        return Commands.literal(key)
                .requires(Cmd.requires(plugin, permission))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "player");
                            OfflinePlayer target = Cmd.offlinePlayer(name);
                            return action.run(context, target.getUniqueId(), String.valueOf(target.getName())) ? 1 : 0;
                        })
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> rankIds(plugin)))
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "player");
                                    OfflinePlayer target = Cmd.offlinePlayer(name);
                                    return action.run(context, target.getUniqueId(),
                                            String.valueOf(target.getName())) ? 1 : 0;
                                })));
    }

    private static boolean step(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                VisualStudioManagement plugin, UUID targetUuid, String name, boolean up) {
        var member = plugin.staffCache().byUuid(targetUuid);
        if (member == null) {
            plugin.messageService().send(context.getSource().getSender(), "not-staff");
            return false;
        }
        StaffRank next = up
                ? plugin.rankRegistry().promote(member.rankId())
                : plugin.rankRegistry().demote(member.rankId());
        if (next == null) {
            plugin.messageService().send(context.getSource().getSender(), "no-rank-change");
            return false;
        }
        var result = plugin.actionService().execute(
                Cmd.request(context.getSource(), PermissionNodes.STAFF_MANAGE)
                        .kind(ActionRequest.Kind.RANK_CHANGE)
                        .target(targetUuid, name)
                        .rank(next.id())
                        .reason(up ? "staff promote" : "staff demote"),
                context.getSource().getSender());
        return result.isSuccess();
    }

    private static int list(VisualStudioManagement plugin,
                            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        plugin.messageService().send(context.getSource().getSender(), "staff-list-header",
                "count", Integer.toString(plugin.staffCache().size()));
        for (StaffRank rank : plugin.staffService().orderedRanks()) {
            var members = plugin.staffCache().byRank(rank.id());
            StringBuilder names = new StringBuilder();
            for (var member : members) {
                if (!names.isEmpty()) {
                    names.append(", ");
                }
                names.append(member.name());
            }
            plugin.messageService().send(context.getSource().getSender(), "staff-list-entry",
                    "rank", rank.display(),
                    "weight", Integer.toString(rank.weight()),
                    "count", Integer.toString(members.size()),
                    "players", names.isEmpty() ? "-" : names.toString());
        }
        return plugin.staffCache().size();
    }

    private static int info(VisualStudioManagement plugin,
                            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        OfflinePlayer target = Cmd.offlinePlayer(name);
        var member = plugin.staffCache().byUuid(target.getUniqueId());
        if (member == null) {
            plugin.messageService().send(context.getSource().getSender(), "not-staff");
            return 0;
        }
        plugin.messageService().send(context.getSource().getSender(), "staff-info",
                "player", String.valueOf(member.name()),
                "rank", plugin.rankRegistry().display(member.rankId()),
                "added_by", member.addedByName() == null ? "console" : member.addedByName(),
                "added_at", io.github.aethelreborn.vsm.util.TimeUtil.toIso(member.addedAt()),
                "last_seen", io.github.aethelreborn.vsm.util.TimeUtil.toIso(member.lastSeen()),
                "issued", Integer.toString(plugin.punishmentService().countsByStaff(member.uuid()).size()));
        return 1;
    }

    private static int ranks(VisualStudioManagement plugin,
                             com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        plugin.messageService().send(context.getSource().getSender(), "ranks-header",
                "count", Integer.toString(plugin.rankRegistry().all().size()));
        for (StaffRank rank : plugin.staffService().ranksDescending()) {
            plugin.messageService().send(context.getSource().getSender(), "ranks-entry",
                    "rank", rank.display(),
                    "id", rank.id(),
                    "weight", Integer.toString(rank.weight()),
                    "nodes", Integer.toString(rank.flattenedPermissions().size()));
        }
        return plugin.rankRegistry().all().size();
    }

    private static int setPin(VisualStudioManagement plugin,
                              com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                              String name, String pin) {
        OfflinePlayer target = Cmd.offlinePlayer(name);
        var member = plugin.staffCache().byUuid(target.getUniqueId());
        if (member == null) {
            plugin.messageService().send(context.getSource().getSender(), "not-staff");
            return 0;
        }
        member.pinHash(plugin.securityService().hashPin(pin));
        plugin.staffCache().put(member);
        plugin.storage().saveStaff(member);
        plugin.messageService().send(context.getSource().getSender(), "staff-pin-set",
                "player", String.valueOf(member.name()));
        return 1;
    }

    private static int removePin(VisualStudioManagement plugin,
                                 com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                 String name) {
        OfflinePlayer target = Cmd.offlinePlayer(name);
        var member = plugin.staffCache().byUuid(target.getUniqueId());
        if (member == null) {
            plugin.messageService().send(context.getSource().getSender(), "not-staff");
            return 0;
        }
        member.pinHash(null);
        plugin.staffCache().put(member);
        plugin.storage().saveStaff(member);
        plugin.messageService().send(context.getSource().getSender(), "staff-pin-removed",
                "player", String.valueOf(member.name()));
        return 1;
    }

    private static List<String> rankIds(VisualStudioManagement plugin) {
        List<String> ids = new ArrayList<>();
        for (StaffRank rank : plugin.rankRegistry().all()) {
            ids.add(rank.id());
        }
        return ids;
    }

    private static List<String> staffNames(VisualStudioManagement plugin) {
        List<String> names = new ArrayList<>();
        for (var member : plugin.staffCache().all()) {
            if (member.name() != null) {
                names.add(member.name());
            }
        }
        return names;
    }

    public static void addRoot(LiteralArgumentBuilder<CommandSourceStack> root, VisualStudioManagement plugin) {
        add(root, plugin);
    }

    /** Kept so the console path keeps compiling when Bukkit's player list is empty. */
    public static int onlineCount() {
        return Bukkit.getOnlinePlayers().size();
    }
}