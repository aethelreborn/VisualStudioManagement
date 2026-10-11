package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Investigation and chat control tools (spec 8.5). */
public final class ToolsCommands {

    private ToolsCommands() {
    }

    /** Every literal this module owns, in tab-completion order. */
    public static java.util.List<String> literals() {
        return java.util.List.of("freeze", "unfreeze", "vanish", "invsee", "endersee", "commandspy", "socialspy", "watch", "clearchat", "mutechat", "slowmode", "lockdown", "maintenance", "near", "alerts", "staffauth");
    }

    /** Builds a single sub-command by literal name, or {@code null} when this module has none. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                  VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "freeze" -> return freeze(plugin);
            case "unfreeze" -> return unfreeze(plugin);
            case "vanish" -> return vanish(plugin);
            case "invsee" -> return invsee(plugin);
            case "endersee" -> return endersee(plugin);
            case "commandspy" -> return commandspy(plugin);
            case "socialspy" -> return socialspy(plugin);
            case "watch" -> return watch(plugin);
            case "clearchat" -> return clearchat(plugin);
            case "mutechat" -> return mutechat(plugin);
            case "slowmode" -> return slowmode(plugin);
            case "lockdown" -> return lockdown(plugin);
            case "maintenance" -> return maintenance(plugin);
            case "near" -> return near(plugin);
            case "alerts" -> return alerts(plugin);
            case "staffauth" -> return staffauth(plugin);
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

    private static LiteralArgumentBuilder<CommandSourceStack> freeze(VisualStudioManagement plugin) {
        return Commands.literal("freeze")
                .requires(stack -> plugin.permissionService().has(stack.getSender(), PermissionNodes.FREEZE)
                        || plugin.permissionService().has(stack.getSender(), PermissionNodes.YOUTUBER_FREEZE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> setFreeze(plugin, context, true, null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> setFreeze(plugin, context, true,
                                        StringArgumentType.getString(context, "reason")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unfreeze(VisualStudioManagement plugin) {
        return Commands.literal("unfreeze")
                .requires(stack -> plugin.permissionService().has(stack.getSender(), PermissionNodes.FREEZE)
                        || plugin.permissionService().has(stack.getSender(), PermissionNodes.YOUTUBER_UNFREEZE))
                .then(Commands.literal("all")
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            if (YouTuberCommands.youTuberPath(plugin, context.getSource(),
                                    PermissionNodes.FREEZE) && actor != null) {
                                return Math.max(1, plugin.youTuberService().unfreezeAll(actor));
                            }
                            int released = 0;
                            for (var uuid : plugin.freezeService().frozenPlayers()) {
                                if (plugin.freezeService().unfreeze(uuid, Cmd.actorUuid(context.getSource()),
                                        Cmd.actorName(context.getSource()))) {
                                    released++;
                                }
                            }
                            plugin.messageService().send(context.getSource().getSender(), "unfreeze-all-ok",
                                    "count", Integer.toString(released));
                            return released;
                        }))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> setFreeze(plugin, context, false, null)));
    }

    private static int setFreeze(VisualStudioManagement plugin,
                                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                boolean freeze, String reason) {
        Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
        if (target == null) {
            plugin.messageService().send(context.getSource().getSender(), "player-not-found");
            return 0;
        }
        // A YouTuber without the staff node uses the scoped, owner-tagged freeze (spec 8.8).
        if (YouTuberCommands.youTuberPath(plugin, context.getSource(), PermissionNodes.FREEZE)) {
            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
            if (actor == null) {
                return 0;
            }
            String text = reason == null || reason.isBlank() ? "Frozen by a creator" : reason;
            return freeze
                    ? (plugin.youTuberService().freeze(actor, target, text) ? 1 : 0)
                    : (plugin.youTuberService().unfreeze(actor, target) ? 1 : 0);
        }
        if (!plugin.permissionService().outranks(context.getSource().getSender(), target.getUniqueId())) {
            plugin.messageService().send(context.getSource().getSender(), "cannot-punish-higher");
            return 0;
        }
        if (freeze) {
            if (plugin.permissionService().has(target, PermissionNodes.FREEZE_EXEMPT)) {
                plugin.messageService().send(context.getSource().getSender(), "freeze-exempt");
                return 0;
            }
            boolean applied = plugin.freezeService().isFrozen(target.getUniqueId())
                    ? true
                    : plugin.freezeService().freeze(target.getUniqueId(),
                    reason == null ? "No reason given" : reason,
                    Cmd.actorUuid(context.getSource()), Cmd.actorName(context.getSource()), null, 0L);
            plugin.messageService().send(context.getSource().getSender(), applied ? "freeze-ok" : "freeze-failed",
                    "player", target.getName());
            return applied ? 1 : 0;
        }
        boolean released = plugin.freezeService().unfreeze(target.getUniqueId(),
                Cmd.actorUuid(context.getSource()), Cmd.actorName(context.getSource()));
        plugin.messageService().send(context.getSource().getSender(), released ? "unfreeze-ok" : "unfreeze-failed",
                "player", target.getName());
        return released ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> vanish(VisualStudioManagement plugin) {
        return Commands.literal("vanish")
                .requires(Cmd.requires(plugin, PermissionNodes.VANISH))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    plugin.vanishService().toggle(actor, actor.getUniqueId(), actor.getName());
                    return 1;
                })
                .then(Commands.literal("on").executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    plugin.vanishService().hide(actor, actor.getUniqueId(), actor.getName());
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    plugin.vanishService().reveal(actor, actor.getUniqueId(), actor.getName());
                    return 1;
                }))
                .then(Commands.literal("see").executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    for (var uuid : plugin.vanishService().vanishedPlayers()) {
                        Player hidden = Bukkit.getPlayer(uuid);
                        if (hidden != null) {
                            actor.showPlayer(plugin, hidden);
                        }
                    }
                    plugin.messageService().send(context.getSource().getSender(), "vanish-seen",
                            "count", Integer.toString(plugin.vanishService().vanishedPlayers().size()));
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> invsee(VisualStudioManagement plugin) {
        return Commands.literal("invsee")
                .requires(Cmd.requires(plugin, PermissionNodes.INVSEE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                            if (actor == null || target == null) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            if (!plugin.permissionService().outranks(actor, target.getUniqueId())) {
                                plugin.messageService().send(context.getSource().getSender(), "cannot-punish-higher");
                                return 0;
                            }
                            plugin.openInvsee(actor, target, false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> endersee(VisualStudioManagement plugin) {
        return Commands.literal("endersee")
                .requires(Cmd.requires(plugin, PermissionNodes.ENDERSEE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                            if (actor == null || target == null) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            plugin.openInvsee(actor, target, true);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandSpy(VisualStudioManagement plugin) {
        return Commands.literal("commandspy")
                .requires(Cmd.requires(plugin, PermissionNodes.COMMAND_SPY))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    boolean on = plugin.staffState().toggleCommandSpy(actor.getUniqueId());
                    plugin.messageService().send(context.getSource().getSender(),
                            on ? "commandspy-on" : "commandspy-off");
                    return 1;
                })
                .then(Commands.literal("on").executes(context -> setSpy(plugin, context, true)))
                .then(Commands.literal("off").executes(context -> setSpy(plugin, context, false)));
    }

    private static int setSpy(VisualStudioManagement plugin,
                              com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, boolean on) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        plugin.staffState().commandSpy(actor.getUniqueId(), on);
        plugin.messageService().send(context.getSource().getSender(),
                on ? "commandspy-on" : "commandspy-off");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> socialSpy(VisualStudioManagement plugin) {
        return Commands.literal("socialspy")
                .requires(Cmd.requires(plugin, PermissionNodes.SOCIAL_SPY))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    boolean on = plugin.staffState().toggleSocialSpy(actor.getUniqueId());
                    plugin.messageService().send(context.getSource().getSender(),
                            on ? "socialspy-on" : "socialspy-off");
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> watch(VisualStudioManagement plugin) {
        return Commands.literal("watch")
                .requires(Cmd.requires(plugin, PermissionNodes.WATCH))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.playerSuggestions(plugin))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            var target = Cmd.offlinePlayer(
                                                    StringArgumentType.getString(context, "player"));
                                            plugin.watchService().add(target.getUniqueId(),
                                                    String.valueOf(target.getName()),
                                                    Cmd.actorUuid(context.getSource()),
                                                    Cmd.actorName(context.getSource()),
                                                    StringArgumentType.getString(context, "reason"));
                                            plugin.messageService().send(context.getSource().getSender(),
                                                    "watchlist-added", "player", String.valueOf(target.getName()));
                                            return 1;
                                        }))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.playerSuggestions(plugin))
                                .executes(context -> {
                                    var target = Cmd.offlinePlayer(StringArgumentType.getString(context, "player"));
                                    plugin.watchService().remove(target.getUniqueId(),
                                            Cmd.actorUuid(context.getSource()),
                                            Cmd.actorName(context.getSource()));
                                    plugin.messageService().send(context.getSource().getSender(),
                                            "watchlist-removed", "player", String.valueOf(target.getName()));
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(context -> {
                            var entries = plugin.watchService().all();
                            plugin.messageService().send(context.getSource().getSender(), "watchlist-header",
                                    "count", Integer.toString(entries.size()));
                            for (var entry : entries) {
                                plugin.messageService().send(context.getSource().getSender(), "watchlist-entry",
                                        "player", entry.name(),
                                        "reason", entry.reason(),
                                        "added_by", entry.addedBy());
                            }
                            return entries.size();
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearChat(VisualStudioManagement plugin) {
        return Commands.literal("clearchat")
                .requires(Cmd.requires(plugin, PermissionNodes.CLEAR_CHAT))
                .executes(context -> {
                    plugin.chatControlService().clearChat(Cmd.actorUuid(context.getSource()),
                            Cmd.actorName(context.getSource()));
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> muteChat(VisualStudioManagement plugin) {
        return Commands.literal("mutechat")
                .requires(Cmd.requires(plugin, PermissionNodes.MUTE_CHAT))
                .executes(context -> {
                    plugin.chatControlService().toggleChatLock(null, Cmd.actorUuid(context.getSource()),
                            Cmd.actorName(context.getSource()));
                    return 1;
                })
                .then(Commands.literal("on").executes(context -> {
                    if (!plugin.chatControlService().chatLocked()) {
                        plugin.chatControlService().toggleChatLock(null, Cmd.actorUuid(context.getSource()),
                                Cmd.actorName(context.getSource()));
                    }
                    plugin.messageService().send(context.getSource().getSender(), "mutechat-on");
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    if (plugin.chatControlService().chatLocked()) {
                        plugin.chatControlService().toggleChatLock(null, Cmd.actorUuid(context.getSource()),
                                Cmd.actorName(context.getSource()));
                    }
                    plugin.messageService().send(context.getSource().getSender(), "mutechat-off");
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> slowmode(VisualStudioManagement plugin) {
        return Commands.literal("slowmode")
                .requires(Cmd.requires(plugin, PermissionNodes.SLOWMODE))
                .then(Commands.literal("off").executes(context -> {
                    plugin.chatControlService().setSlowMode(null, Cmd.actorUuid(context.getSource()),
                            Cmd.actorName(context.getSource()), 0L);
                    return 1;
                }))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                        .executes(context -> {
                            plugin.chatControlService().setSlowMode(null, Cmd.actorUuid(context.getSource()),
                                    Cmd.actorName(context.getSource()),
                                    IntegerArgumentType.getInteger(context, "seconds"));
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lockdown(VisualStudioManagement plugin) {
        return Commands.literal("lockdown")
                .requires(Cmd.requires(plugin, PermissionNodes.LOCKDOWN))
                .then(Commands.literal("on").executes(context -> {
                    plugin.lockdownService().setLockdown(true, "lockdown",
                            Cmd.actorUuid(context.getSource()), Cmd.actorName(context.getSource()));
                    plugin.lockdownService().kickDisallowed("screen-lockdown", null);
                    plugin.messageService().send(context.getSource().getSender(), "lockdown-on");
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    plugin.lockdownService().setLockdown(false, "", Cmd.actorUuid(context.getSource()),
                            Cmd.actorName(context.getSource()));
                    plugin.messageService().send(context.getSource().getSender(), "lockdown-off");
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> maintenance(VisualStudioManagement plugin) {
        return Commands.literal("maintenance")
                .requires(Cmd.requires(plugin, PermissionNodes.MAINTENANCE))
                .then(Commands.literal("on").executes(context -> {
                    plugin.lockdownService().setMaintenance(true, Cmd.actorUuid(context.getSource()),
                            Cmd.actorName(context.getSource()));
                    plugin.messageService().send(context.getSource().getSender(), "maintenance-on");
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    plugin.lockdownService().setMaintenance(false, Cmd.actorUuid(context.getSource()),
                            Cmd.actorName(context.getSource()));
                    plugin.messageService().send(context.getSource().getSender(), "maintenance-off");
                    return 1;
                }))
                .then(Commands.literal("whitelist")
                        .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.playerSuggestions(plugin))
                                .executes(context -> {
                                    plugin.lockdownService().whitelistAdd(
                                            StringArgumentType.getString(context, "player"));
                                    return 1;
                                })))
                        .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.playerSuggestions(plugin))
                                .executes(context -> {
                                    plugin.lockdownService().whitelistRemove(
                                            StringArgumentType.getString(context, "player"));
                                    return 1;
                                }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> near(VisualStudioManagement plugin) {
        return Commands.literal("near")
                .requires(Cmd.requires(plugin, PermissionNodes.SEEN))
                .executes(context -> near(plugin, context, 50))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 200))
                        .executes(context -> near(plugin, context,
                                IntegerArgumentType.getInteger(context, "radius"))));
    }

    private static int near(VisualStudioManagement plugin,
                            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, int radius) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        List<String> names = new ArrayList<>();
        Location location = actor.getLocation();
        for (Player player : actor.getNearbyEntities(radius, radius, radius).stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity).toList()) {
            if (plugin.vanishService().isVanished(player)
                    && !plugin.permissionService().has(actor, PermissionNodes.VANISH_SEE)) {
                continue;
            }
            names.add(player.getName() + " (" + String.format("%.1f", location.distance(player.getLocation())) + "m)");
        }
        plugin.messageService().send(context.getSource().getSender(), "near-header",
                "radius", Integer.toString(radius), "count", Integer.toString(names.size()));
        for (String name : names) {
            plugin.messageService().send(context.getSource().getSender(), "near-entry", "player", name);
        }
        return names.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> alerts(VisualStudioManagement plugin) {
        return Commands.literal("alerts")
                .requires(Cmd.requires(plugin, PermissionNodes.ALERTS))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    boolean on = plugin.staffState().toggleAlerts(actor.getUniqueId());
                    plugin.messageService().send(context.getSource().getSender(),
                            on ? "alerts-on" : "alerts-off");
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> staffAuth(VisualStudioManagement plugin) {
        return Commands.literal("staffauth")
                .then(Commands.argument("pin", StringArgumentType.word())
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            if (actor == null) {
                                return 0;
                            }
                            boolean ok = plugin.securityService().checkPinAttempt(actor.getUniqueId(),
                                    StringArgumentType.getString(context, "pin"));
                            plugin.messageService().send(context.getSource().getSender(),
                                    ok ? "staffauth-ok" : "staffauth-failed");
                            return ok ? 1 : 0;
                        }));
    }
}