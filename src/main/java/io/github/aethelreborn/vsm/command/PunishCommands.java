package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.services.ActionRequest;
import io.github.aethelreborn.vsm.util.FlagParser;
import io.github.aethelreborn.vsm.util.IdGenerator;
import io.github.aethelreborn.vsm.util.IpUtil;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.github.aethelreborn.vsm.util.TimeParser;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Punishment commands (spec 8.2). Every one of them builds an {@link ActionRequest} and hands it to
 * {@code ActionService}, so the GUI, API and console paths cannot diverge.
 */
public final class PunishCommands {

    private PunishCommands() {
    }

    /** Attaches every punishment command to the given parent literal. */
    /** Every literal this module owns, in tab-completion order. */
    public static java.util.List<String> literals() {
        return java.util.List.of("warn", "tempwarn", "unwarn", "warnings", "mute", "tempmute", "shadowmute", "ipmute", "unmute", "kick", "kickall", "tempban", "ban", "unban", "ipban", "tempipban", "ipunban", "nameban", "punish", "pardon", "punishment", "checkban", "checkmute", "banlist", "mutelist");
    }

    /** Builds a single sub-command by literal name, or {@code null} when this module has none. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                  VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "warn" -> warn(plugin);
            case "tempwarn" -> tempWarn(plugin);
            case "unwarn" -> unwarn(plugin);
            case "warnings" -> warnings(plugin);
            case "mute" -> mute(plugin);
            case "tempmute" -> tempMute(plugin);
            case "shadowmute" -> shadowMute(plugin);
            case "ipmute" -> ipMute(plugin);
            case "unmute" -> unmute(plugin);
            case "kick" -> kick(plugin);
            case "kickall" -> kickAll(plugin);
            case "tempban" -> tempBan(plugin);
            case "ban" -> ban(plugin);
            case "unban" -> unban(plugin);
            case "ipban" -> ipBan(plugin);
            case "tempipban" -> tempIpBan(plugin);
            case "ipunban" -> ipUnban(plugin);
            case "nameban" -> nameBan(plugin);
            case "punish" -> punish(plugin);
            case "pardon" -> pardon(plugin);
            case "punishment" -> punishment(plugin);
            case "checkban" -> checkBan(plugin);
            case "checkmute" -> checkMute(plugin);
            case "banlist" -> banList(plugin);
            case "mutelist" -> muteList(plugin);
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

    /** Generic punishment builder shared by warn / mute / kick / ban. */
    private static LiteralArgumentBuilder<CommandSourceStack> punish(
            VisualStudioManagement plugin, String name, PunishmentType type, boolean needsDuration,
            boolean needsReason) {
        String permission = Cmd.permissionFor(type);
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name)
                .requires(Cmd.requires(plugin, permission));
        if (!needsDuration) {
            node = node.then(Commands.argument("target", StringArgumentType.word())
                    .suggests(Cmd.playerSuggestions(plugin))
                    .executes(context -> issueFromArgs(plugin, context, type, null, 0)));
            if (needsReason) {
                node = node.then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> issueFromArgs(plugin, context, type, null, 0)));
            }
            return node.executes(context -> help(plugin, context, name));
        }
        return node.then(Commands.argument("target", StringArgumentType.word())
                .suggests(Cmd.playerSuggestions(plugin))
                .then(Commands.argument("duration", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() ->
                                io.github.aethelreborn.vsm.config.ReasonRegistry.durationSuggestions(type.name())))
                        .executes(context -> issueFromArgs(plugin, context, type, null, 0))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> issueFromArgs(plugin, context, type, null, 0)))));
    }

    private static int help(VisualStudioManagement plugin,
                            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        plugin.messageService().sendRaw(context.getSource().getSender(), "usage",
                "command", "/" + plugin.vsmConfig().rootCommand() + " " + name + " <player> <reason>");
        return 1;
    }

    // -------------------------------------------------------- execution
    private static int issueFromArgs(VisualStudioManagement plugin,
                                     com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                     PunishmentType type, String forcedIp, int unused) {
        String targetArg = StringArgumentType.getString(context, "target");
        UUID actorUuid = Cmd.actorUuid(context.getSource());
        String actorName = Cmd.actorName(context.getSource());

        String durationToken = null;
        try {
            durationToken = StringArgumentType.getString(context, "duration");
        } catch (IllegalArgumentException ignored) {
            // no duration argument on this branch
        }
        String reasonRaw = "";
        try {
            reasonRaw = StringArgumentType.getString(context, "reason");
        } catch (IllegalArgumentException ignored) {
            // no reason supplied
        }
        // Flags may appear in any order before the reason (spec 8.2).
        FlagParser flags = FlagParser.parse(Arrays.asList(reasonRaw.split(" ")), 0);

        long duration = TimeParser.PERMANENT;
        PunishmentType effective = type;
        if (durationToken != null) {
            if (TimeParser.isPermanentToken(durationToken)) {
                effective = type.permanentVariant();
            } else {
                try {
                    duration = TimeParser.parse(durationToken);
                } catch (IllegalArgumentException ex) {
                    plugin.messageService().send(context.getSource().getSender(), "invalid-duration");
                    return 0;
                }
                effective = type.temporaryVariant();
            }
        }

        String reason = flags.reason();
        if (!TextUtil.isValidReason(reason, plugin.vsmConfig().minReasonLength(),
                plugin.vsmConfig().maxReasonLength())) {
            plugin.messageService().send(context.getSource().getSender(), "reason-required");
            return 0;
        }

        UUID targetUuid = null;
        String targetName = null;
        String ip = forcedIp;
        if (targetArg != null && (IpUtil.isValidIp(targetArg) || IpUtil.isCidr(targetArg))) {
            ip = targetArg;
        } else {
            OfflinePlayer target = Cmd.offlinePlayer(targetArg);
            targetUuid = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : targetArg;
        }

        if (ip != null && effective.isIp()) {
            String rejection = plugin.ipService().guardIpBan(ip, null);
            if (rejection != null) {
                plugin.messageService().send(context.getSource().getSender(), rejection);
                return 0;
            }
            if (!effective.isBan() && targetIpNeeded(plugin, context)) {
                String resolved = resolveIpForPlayer(plugin, targetName);
                if (resolved == null) {
                    plugin.messageService().send(context.getSource().getSender(), "no-known-ip");
                    return 0;
                }
                ip = resolved;
            }
            ip = plugin.ipService().store(ip);
        } else if (effective.isIp() && plugin.vsmConfig().banIpOnBan()) {
            String resolved = resolveIpForPlayer(plugin, targetName);
            if (resolved != null) {
                ip = plugin.ipService().store(resolved);
            }
        }

        boolean silent = flags.silent() && plugin.permissionService().has(context.getSource().getSender(),
                PermissionNodes.PUNISHMENT_SILENT);

        ActionRequest request = Cmd.request(context.getSource(), Cmd.permissionFor(effective))
                .kind(ActionRequest.Kind.PUNISH)
                .punishment(effective)
                .target(targetUuid, targetName)
                .targetIp(ip)
                .reason(reason)
                .duration(duration)
                .silent(silent)
                .evidence(flags.evidence())
                .template(flags.template());

        var result = plugin.actionService().execute(request, context.getSource().getSender());
        if (result.isSuccess() && result.punishment() != null) {
            plugin.messageService().send(context.getSource().getSender(), "punishment-issued-ok",
                    "id", result.punishment().id(),
                    "type", effective.name(),
                    "target", result.punishment().displayTarget());
            if (effective.isIp() && plugin.vsmConfig().alsoBanAccountsOnIpban()
                    && result.punishment().targetIp() != null) {
                cascadeAccounts(plugin, result.punishment(), actorUuid, actorName);
            }
        }
        return result.isSuccess() ? 1 : 0;
    }

    private static boolean targetIpNeeded(VisualStudioManagement plugin,
                                          com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender() != null;
    }

    private static String resolveIpForPlayer(VisualStudioManagement plugin, String name) {
        if (name == null) {
            return null;
        }
        return plugin.ipService().lastKnownIp(Cmd.offlinePlayer(name).getUniqueId());
    }

    /** Creates account bans for every account seen on a banned IP (spec 8.2 rule 2). */
    private static void cascadeAccounts(VisualStudioManagement plugin,
                                        io.github.aethelreborn.vsm.models.Punishment ipBan,
                                        UUID actorUuid, String actorName) {
        if (ipBan.targetIp() == null) {
            return;
        }
        List<UUID> accounts = plugin.ipService().accountsOnIpForIpBan(ipBan.targetIp());
        for (UUID account : accounts) {
            if (plugin.punishmentCache().activeFor(account, System.currentTimeMillis()).stream()
                    .anyMatch(p -> p.type().isBan())) {
                continue;
            }
            plugin.punishmentService().issue(io.github.aethelreborn.vsm.models.PunishmentType.BAN,
                    account, String.valueOf(account), "Account on banned IP",
                    actorUuid, actorName, TimeParser.PERMANENT, ipBan.silent(), null, null, -1,
                    ipBan.id(), "");
        }
    }

    // --------------------------------------------------------- commands
    private static LiteralArgumentBuilder<CommandSourceStack> warn(VisualStudioManagement plugin) {
        return punish(plugin, "warn", PunishmentType.WARN, false, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tempWarn(VisualStudioManagement plugin) {
        return punish(plugin, "tempwarn", PunishmentType.TEMP_WARN, true, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mute(VisualStudioManagement plugin) {
        return punish(plugin, "mute", PunishmentType.MUTE, false, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tempMute(VisualStudioManagement plugin) {
        return punish(plugin, "tempmute", PunishmentType.TEMP_MUTE, true, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shadowMute(VisualStudioManagement plugin) {
        return punish(plugin, "shadowmute", PunishmentType.SHADOW_MUTE, false, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ban(VisualStudioManagement plugin) {
        return punish(plugin, "ban", PunishmentType.BAN, false, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tempBan(VisualStudioManagement plugin) {
        return punish(plugin, "tempban", PunishmentType.TEMP_BAN, true, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kick(VisualStudioManagement plugin) {
        return punish(plugin, "kick", PunishmentType.KICK, false, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ipMute(VisualStudioManagement plugin) {
        return punish(plugin, "ipmute", PunishmentType.IP_MUTE, true, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ipBan(VisualStudioManagement plugin) {
        return Commands.literal("ipban")
                .requires(Cmd.requires(plugin, PermissionNodes.IP_BAN))
                .then(Commands.literal("allow")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(Cmd.playerSuggestions(plugin))
                                        .executes(context -> {
                                            OfflinePlayer target = Cmd.offlinePlayer(
                                                    StringArgumentType.getString(context, "player"));
                                            plugin.ipService().allowIp(target.getUniqueId(),
                                                    Cmd.actorName(context.getSource()));
                                            plugin.messageService().send(context.getSource().getSender(),
                                                    "ip-allow-added", "player", String.valueOf(target.getName()));
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(Cmd.playerSuggestions(plugin))
                                        .executes(context -> {
                                            OfflinePlayer target = Cmd.offlinePlayer(
                                                    StringArgumentType.getString(context, "player"));
                                            plugin.ipService().removeIpAllow(target.getUniqueId());
                                            plugin.messageService().send(context.getSource().getSender(),
                                                    "ip-allow-removed", "player", String.valueOf(target.getName()));
                                            return 1;
                                        }))))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            String ip = resolveBanTargetIp(plugin, context);
                            if (ip == null) {
                                return 0;
                            }
                            return issueFromArgs(plugin, context, PunishmentType.IP_BAN, ip, 0);
                        })
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String ip = resolveBanTargetIp(plugin, context);
                                    if (ip == null) {
                                        return 0;
                                    }
                                    return issueFromArgs(plugin, context, PunishmentType.IP_BAN, ip, 0);
                                })));
    }

    /** Resolves a player name to their last known IP, or {@code null} with a clear message. */
    private static String resolveBanTargetIp(VisualStudioManagement plugin,
                                             com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String target = StringArgumentType.getString(context, "target");
        if (IpUtil.isValidIp(target) || IpUtil.isCidr(target)) {
            return target;
        }
        String ip = plugin.ipService().lastKnownIp(Cmd.offlinePlayer(target).getUniqueId());
        if (ip == null) {
            plugin.messageService().send(context.getSource().getSender(), "no-known-ip");
        }
        return ip;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tempIpBan(VisualStudioManagement plugin) {
        return punish(plugin, "tempipban", PunishmentType.TEMP_IP_BAN, true, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nameBan(VisualStudioManagement plugin) {
        return punish(plugin, "nameban", PunishmentType.NAME_BAN, false, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kickAll(VisualStudioManagement plugin) {
        return Commands.literal("kickall")
                .requires(Cmd.requires(plugin, PermissionNodes.KICK))
                .executes(context -> {
                    int kicked = 0;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (plugin.permissionService().isExempt(context.getSource().getSender(),
                                player.getUniqueId(), PermissionNodes.EXEMPT_KICK)) {
                            continue;
                        }
                        player.kick(plugin.messageService().screen("screen-kick"));
                        kicked++;
                    }
                    plugin.auditService().log(ActionSource.COMMAND, Cmd.actorUuid(context.getSource()),
                            Cmd.actorName(context.getSource()), "KICKALL", null, null,
                            kicked + " player(s)");
                    plugin.messageService().send(context.getSource().getSender(), "kickall-ok",
                            "count", Integer.toString(kicked));
                    return kicked;
                })
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> {
                            int kicked = 0;
                            String reason = StringArgumentType.getString(context, "reason");
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (plugin.permissionService().isExempt(context.getSource().getSender(),
                                        player.getUniqueId(), PermissionNodes.EXEMPT_KICK)) {
                                    continue;
                                }
                                player.kick(plugin.messageService().screen("screen-kick"));
                                kicked++;
                            }
                            plugin.auditService().log(ActionSource.COMMAND, Cmd.actorUuid(context.getSource()),
                                    Cmd.actorName(context.getSource()), "KICKALL", null, null,
                                    kicked + " player(s) reason=" + reason);
                            plugin.messageService().send(context.getSource().getSender(), "kickall-ok",
                                    "count", Integer.toString(kicked));
                            return kicked;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unwarn(VisualStudioManagement plugin) {
        return Commands.literal("unwarn")
                .requires(Cmd.requires(plugin, PermissionNodes.UNWARN))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> warningIds(plugin)))
                        .executes(context -> {
                            String id = StringArgumentType.getString(context, "target");
                            return removeById(plugin, context, id, PermissionNodes.UNWARN);
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unmute(VisualStudioManagement plugin) {
        return Commands.literal("unmute")
                .requires(Cmd.requires(plugin, PermissionNodes.UNMUTE))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            OfflinePlayer target = Cmd.offlinePlayer(StringArgumentType.getString(context, "target"));
                            for (var punishment : plugin.punishmentService().activeFor(target.getUniqueId())) {
                                if (punishment.type().isMute()) {
                                    return removePunishment(plugin, context, punishment, PermissionNodes.UNMUTE);
                                }
                            }
                            plugin.messageService().send(context.getSource().getSender(), "punishment-not-found");
                            return 0;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unban(VisualStudioManagement plugin) {
        return Commands.literal("unban")
                .requires(Cmd.requires(plugin, PermissionNodes.UNBAN))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> punishmentIds(plugin)))
                        .executes(context -> removeById(plugin, context,
                                StringArgumentType.getString(context, "target"), PermissionNodes.UNBAN)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ipUnban(VisualStudioManagement plugin) {
        return Commands.literal("ipunban")
                .requires(Cmd.requires(plugin, PermissionNodes.IP_UNBAN))
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests(Cmd.suggestions(() -> punishmentIds(plugin)))
                        .executes(context -> {
                            String value = StringArgumentType.getString(context, "target");
                            if (IpUtil.isValidIp(value) || IpUtil.isCidr(value)) {
                                String key = plugin.ipService().store(value);
                                for (var punishment : plugin.punishmentCache().activeForIp(key,
                                        System.currentTimeMillis())) {
                                    return removePunishment(plugin, context, punishment, PermissionNodes.IP_UNBAN);
                                }
                            } else {
                                OfflinePlayer target = Cmd.offlinePlayer(value);
                                for (var punishment : plugin.punishmentService().activeFor(target.getUniqueId())) {
                                    if (punishment.type().isIp()) {
                                        return removePunishment(plugin, context, punishment,
                                                PermissionNodes.IP_UNBAN);
                                    }
                                }
                            }
                            plugin.messageService().send(context.getSource().getSender(), "punishment-not-found");
                            return 0;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pardon(VisualStudioManagement plugin) {
        return Commands.literal("pardon")
                .requires(Cmd.requires(plugin, PermissionNodes.PUNISHMENT_REMOVE))
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(Cmd.suggestions(() -> punishmentIds(plugin)))
                        .executes(context -> removeById(plugin, context,
                                StringArgumentType.getString(context, "id"), PermissionNodes.PUNISHMENT_REMOVE))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    String reason = StringArgumentType.getString(context, "reason");
                                    var found = plugin.punishmentService().byId(id);
                                    if (found.isEmpty()) {
                                        plugin.messageService().send(context.getSource().getSender(),
                                                "punishment-not-found");
                                        return 0;
                                    }
                                    return removePunishment(plugin, context, found.get(),
                                            PermissionNodes.PUNISHMENT_REMOVE, reason);
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> punishment(VisualStudioManagement plugin) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("punishment");
        node.then(Commands.literal("info")
                .requires(Cmd.requires(plugin, PermissionNodes.HISTORY))
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(Cmd.suggestions(() -> punishmentIds(plugin)))
                        .executes(context -> {
                            var found = plugin.punishmentService().byId(StringArgumentType.getString(context, "id"));
                            if (found.isEmpty()) {
                                plugin.messageService().send(context.getSource().getSender(),
                                        "punishment-not-found");
                                return 0;
                            }
                            var punishment = found.get();
                            plugin.messageService().send(context.getSource().getSender(), "punishment-detail",
                                    "id", punishment.id(),
                                    "type", punishment.type().name(),
                                    "target", punishment.displayTarget(),
                                    "reason", punishment.reason() == null ? "" : punishment.reason(),
                                    "staff", punishment.staffName() == null ? "Console" : punishment.staffName(),
                                    "expires", punishment.isPermanent() ? "never"
                                            : TimeParser.formatRemaining(punishment.expiresAt(),
                                            System.currentTimeMillis()));
                            return 1;
                        })));
        node.then(Commands.literal("modify")
                .requires(Cmd.requires(plugin, PermissionNodes.PUNISHMENT_MODIFY))
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(Cmd.suggestions(() -> punishmentIds(plugin)))
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> List.of("duration", "reason")))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            var request = Cmd.request(context.getSource(),
                                                    PermissionNodes.PUNISHMENT_MODIFY)
                                                    .kind(ActionRequest.Kind.MODIFY_PUNISHMENT)
                                                    .extra(StringArgumentType.getString(context, "id"))
                                                    .template(StringArgumentType.getString(context, "field"))
                                                    .evidence(StringArgumentType.getString(context, "value"));
                                            var result = plugin.actionService().execute(request,
                                                    context.getSource().getSender());
                                            return result.isSuccess() ? 1 : 0;
                                        })))));
        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> punish(VisualStudioManagement plugin) {
        return Commands.literal("punish")
                .requires(Cmd.requires(plugin, PermissionNodes.PUNISH))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> plugin.templateRegistry().ids()))
                                .executes(context -> {
                                    OfflinePlayer target = Cmd.offlinePlayer(
                                            StringArgumentType.getString(context, "target"));
                                    var punishment = plugin.templateService().apply(
                                            context.getSource().getSender(),
                                            Cmd.actorUuid(context.getSource()),
                                            Cmd.actorName(context.getSource()),
                                            target.getUniqueId(),
                                            target.getName(),
                                            StringArgumentType.getString(context, "template"),
                                            false, null);
                                    return punishment == null ? 0 : 1;
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> warnings(VisualStudioManagement plugin) {
        return Commands.literal("warnings")
                .requires(Cmd.requires(plugin, PermissionNodes.WARNINGS))
                .executes(context -> {
                    Player player = Cmd.requirePlayer(context.getSource(), plugin);
                    if (player == null) {
                        return 0;
                    }
                    return printWarnings(plugin, context, player.getUniqueId(), player.getName());
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "player");
                            OfflinePlayer target = Cmd.offlinePlayer(name);
                            return printWarnings(plugin, context, target.getUniqueId(), name);
                        }));
    }

    private static int printWarnings(VisualStudioManagement plugin,
                                     com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                     UUID uuid, String name) {
        var warnings = plugin.punishmentCache().activeWarnings(uuid, System.currentTimeMillis());
        plugin.messageService().send(context.getSource().getSender(), "warnings-header",
                "player", name, "count", Integer.toString(warnings.size()));
        for (var warning : warnings) {
            plugin.messageService().send(context.getSource().getSender(), "warnings-entry",
                    "id", warning.id(),
                    "reason", warning.reason() == null ? "" : warning.reason(),
                    "staff", warning.staffName() == null ? "Console" : warning.staffName(),
                    "expires", warning.isPermanent() ? "never"
                            : TimeParser.formatRemaining(warning.expiresAt(), System.currentTimeMillis()));
        }
        return warnings.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> checkBan(VisualStudioManagement plugin) {
        return Commands.literal("checkban")
                .requires(Cmd.requires(plugin, PermissionNodes.CHECK_BAN))
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests(Cmd.suggestions(() -> {
                            List<String> values = new ArrayList<>(Cmd.onlineNames(plugin));
                            return values;
                        }))
                        .executes(context -> {
                            String value = StringArgumentType.getString(context, "target");
                            long now = System.currentTimeMillis();
                            if (IpUtil.isValidIp(value) || IpUtil.isCidr(value)) {
                                var ban = plugin.ipService().activeIpBan(value, null, now);
                                if (ban.isPresent()) {
                                    plugin.messageService().send(context.getSource().getSender(), "checkban-ip-banned",
                                            "ip", plugin.ipService().display(value, context.getSource().getSender()),
                                            "id", ban.get().id(),
                                            "reason", ban.get().reason() == null ? "" : ban.get().reason());
                                } else {
                                    plugin.messageService().send(context.getSource().getSender(), "checkban-ip-clear",
                                            "ip", plugin.ipService().display(value, context.getSource().getSender()));
                                }
                                return ban.isPresent() ? 1 : 0;
                            }
                            OfflinePlayer target = Cmd.offlinePlayer(value);
                            for (var punishment : plugin.punishmentService().activeFor(target.getUniqueId())) {
                                if (punishment.type().isBan()) {
                                    plugin.messageService().send(context.getSource().getSender(),
                                            "checkban-banned",
                                            "player", String.valueOf(target.getName()),
                                            "type", punishment.type().name(),
                                            "id", punishment.id(),
                                            "reason", punishment.reason() == null ? "" : punishment.reason());
                                    return 1;
                                }
                            }
                            String ip = plugin.ipService().lastKnownIp(target.getUniqueId());
                            var ipBan = ip == null ? null : plugin.ipService().activeIpBan(ip,
                                    target.getUniqueId(), now).orElse(null);
                            plugin.messageService().send(context.getSource().getSender(),
                                    ipBan == null ? "checkban-clear" : "checkban-account-allowed-ip",
                                    "player", String.valueOf(target.getName()),
                                    "ip", ip == null ? "unknown"
                                            : plugin.ipService().display(ip, context.getSource().getSender()));
                            return 0;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> checkMute(VisualStudioManagement plugin) {
        return Commands.literal("checkmute")
                .requires(Cmd.requires(plugin, PermissionNodes.CHECK_MUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            OfflinePlayer target = Cmd.offlinePlayer(StringArgumentType.getString(context, "player"));
                            var mutes = plugin.punishmentCache().activeMutes(target.getUniqueId(),
                                    System.currentTimeMillis());
                            if (mutes.isEmpty()) {
                                plugin.messageService().send(context.getSource().getSender(), "checkmute-clear",
                                        "player", String.valueOf(target.getName()));
                                return 0;
                            }
                            var mute = mutes.get(0);
                            plugin.messageService().send(context.getSource().getSender(), "checkmute-muted",
                                    "player", String.valueOf(target.getName()),
                                    "id", mute.id(),
                                    "reason", mute.reason() == null ? "" : mute.reason(),
                                    "expires", mute.isPermanent() ? "never"
                                            : TimeParser.formatRemaining(mute.expiresAt(),
                                            System.currentTimeMillis()));
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> banList(VisualStudioManagement plugin) {
        return listCommand(plugin, "banlist", PermissionNodes.BAN_LIST, true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> muteList(VisualStudioManagement plugin) {
        return listCommand(plugin, "mutelist", PermissionNodes.MUTE_LIST, false);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listCommand(
            VisualStudioManagement plugin, String name, String permission, boolean bans) {
        return Commands.literal(name)
                .requires(Cmd.requires(plugin, permission))
                .executes(context -> printList(plugin, context, 0, bans))
                .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .executes(context -> printList(plugin, context,
                                IntegerArgumentType.getInteger(context, "page"), bans)));
    }

    private static int printList(VisualStudioManagement plugin,
                                 com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                 int page, boolean bans) {
        int perPage = 10;
        var all = plugin.punishmentService().allActive();
        List<io.github.aethelreborn.vsm.models.Punishment> filtered = new ArrayList<>();
        for (var punishment : all) {
            if (bans && punishment.type().isBan() || !bans && punishment.type().isMute()) {
                filtered.add(punishment);
            }
        }
        int from = Math.min(page * perPage, filtered.size());
        int to = Math.min(from + perPage, filtered.size());
        plugin.messageService().send(context.getSource().getSender(), "list-header",
                "page", Integer.toString(page + 1),
                "count", Integer.toString(filtered.size()));
        for (var punishment : filtered.subList(from, to)) {
            plugin.messageService().send(context.getSource().getSender(), "list-entry",
                    "id", punishment.id(),
                    "type", punishment.type().name(),
                    "target", punishment.displayTarget(),
                    "reason", punishment.reason() == null ? "" : punishment.reason(),
                    "expires", punishment.isPermanent() ? "never"
                            : TimeParser.formatRemaining(punishment.expiresAt(), System.currentTimeMillis()));
        }
        return filtered.size();
    }

    // ------------------------------------------------------------ helpers
    private static int removeById(VisualStudioManagement plugin,
                                  com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                  String id, String permission) {
        var found = plugin.punishmentService().byId(id);
        if (found.isEmpty()) {
            plugin.messageService().send(context.getSource().getSender(), "punishment-not-found");
            return 0;
        }
        return removePunishment(plugin, context, found.get(), permission);
    }

    private static int removePunishment(VisualStudioManagement plugin,
                                       com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                       io.github.aethelreborn.vsm.models.Punishment punishment, String permission) {
        return removePunishment(plugin, context, punishment, permission, "no reason");
    }

    private static int removePunishment(VisualStudioManagement plugin,
                                       com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                       io.github.aethelreborn.vsm.models.Punishment punishment, String permission,
                                       String reason) {
        var request = Cmd.request(context.getSource(), permission)
                .kind(ActionRequest.Kind.REMOVE_PUNISHMENT)
                .target(punishment.targetUuid(), punishment.displayTarget())
                .reason(reason)
                .extra(punishment.id());
        var result = plugin.actionService().execute(request, context.getSource().getSender());
        return result.isSuccess() ? 1 : 0;
    }

    private static List<String> punishmentIds(VisualStudioManagement plugin) {
        List<String> ids = new ArrayList<>();
        for (var punishment : plugin.punishmentService().allActive()) {
            ids.add(punishment.id());
        }
        return ids;
    }

    private static List<String> warningIds(VisualStudioManagement plugin) {
        List<String> ids = new ArrayList<>();
        for (var punishment : plugin.punishmentService().allActive()) {
            if (punishment.type().isWarn()) {
                ids.add(punishment.id());
            }
        }
        return ids;
    }

    /** Exposed for tests: generates the id shape used by the plugin. */
    public static String sampleId(String prefix) {
        return IdGenerator.generate(prefix, 6);
    }

    /** Convenience used by the importer summary. */
    public static List<String> defaultAliases() {
        return Arrays.asList("vsm", "staff");
    }
}