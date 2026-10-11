package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.services.ActionRequest;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Movement and teleport tools (spec 8.4). Everything goes through {@code TeleportService}. */
public final class MovementCommands {

    private MovementCommands() {
    }

    /** Every literal this module owns, in tab-completion order. */
    public static java.util.List<String> literals() {
        return java.util.List.of("tp", "tphere", "tpall", "tppos", "back", "top", "tpoffline", "fly", "speed", "gm", "spectate", "follow", "staffmode");
    }

    /** Builds a single sub-command by literal name, or {@code null} when this module has none. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                  VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "tp" -> return tp(plugin);
            case "tphere" -> return tphere(plugin);
            case "tpall" -> return tpall(plugin);
            case "tppos" -> return tppos(plugin);
            case "back" -> return back(plugin);
            case "top" -> return top(plugin);
            case "tpoffline" -> return tpoffline(plugin);
            case "fly" -> return fly(plugin);
            case "speed" -> return speed(plugin);
            case "gm" -> return gm(plugin);
            case "spectate" -> return spectate(plugin);
            case "follow" -> return follow(plugin);
            case "staffmode" -> return staffmode(plugin);
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

    private static LiteralArgumentBuilder<CommandSourceStack> tp(VisualStudioManagement plugin) {
        return Commands.literal("tp")
                .requires(stack -> plugin.permissionService().has(stack.getSender(), PermissionNodes.TP)
                        || plugin.permissionService().has(stack.getSender(), PermissionNodes.YOUTUBER_TP))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                            if (actor == null) {
                                return 0;
                            }
                            if (target == null) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            if (YouTuberCommands.youTuberPath(plugin, context.getSource(), PermissionNodes.TP)) {
                                return plugin.youTuberService().teleportToPlayer(actor, target) ? 1 : 0;
                            }
                            if (plugin.vanishService().isVanished(actor)) {
                                boolean silent = plugin.teleportService().teleport(actor, target.getLocation(), "tp");
                                plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(),
                                        "TELEPORT_TO", target.getUniqueId(), target.getName(), "silent");
                                return silent ? 1 : 0;
                            }
                            plugin.teleportService().teleport(actor, target.getLocation(), "tp");
                            plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(),
                                    "TELEPORT_TO", target.getUniqueId(), target.getName(), "");
                            return 1;
                        })
                        .then(Commands.argument("destination", StringArgumentType.word())
                                .suggests(Cmd.playerSuggestions(plugin))
                                .executes(context -> {
                                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                                    Player source = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                                    Player destination = Cmd.onlinePlayer(
                                            StringArgumentType.getString(context, "destination"));
                                    if (actor == null || source == null || destination == null) {
                                        plugin.messageService().send(context.getSource().getSender(),
                                                "player-not-found");
                                        return 0;
                                    }
                                    if (!plugin.permissionService().has(actor, PermissionNodes.TP_OTHERS)) {
                                        plugin.messageService().send(context.getSource().getSender(),
                                                "no-permission");
                                        return 0;
                                    }
                                    if (!plugin.permissionService().outranks(actor, source.getUniqueId())) {
                                        plugin.messageService().send(context.getSource().getSender(),
                                                "cannot-punish-higher");
                                        return 0;
                                    }
                                    boolean moved = plugin.teleportService().teleport(source,
                                            destination.getLocation(), "tp-other");
                                    plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(),
                                            "TELEPORT_OTHER", source.getUniqueId(), source.getName(),
                                            destination.getName());
                                    return moved ? 1 : 0;
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tpHere(VisualStudioManagement plugin) {
        return Commands.literal("tphere")
                .requires(Cmd.requires(plugin, PermissionNodes.TP_HERE))
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
                            if (plugin.freezeService().isFrozen(target.getUniqueId())
                                    && !plugin.permissionService().has(actor, PermissionNodes.OVERRIDE)) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            boolean moved = plugin.teleportService().teleport(target, actor.getLocation(), "tphere");
                            plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(),
                                    "TPHERE", target.getUniqueId(), target.getName(), "");
                            return moved ? 1 : 0;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tpAll(VisualStudioManagement plugin) {
        return Commands.literal("tpall")
                .requires(Cmd.requires(plugin, PermissionNodes.TP_ALL))
                .executes(context -> tpAll(plugin, context, false))
                .then(Commands.argument("confirm", StringArgumentType.word())
                        .executes(context -> tpAll(plugin, context,
                                "confirm".equalsIgnoreCase(StringArgumentType.getString(context, "confirm")))));
    }

    private static int tpAll(VisualStudioManagement plugin,
                             com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                             boolean confirmed) {
        if (plugin.vsmConfig().confirmTpAll() && !confirmed) {
            plugin.messageService().send(context.getSource().getSender(), "tpall-confirm");
            return 0;
        }
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        int moved = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(actor.getUniqueId())) {
                continue;
            }
            if (plugin.permissionService().isExempt(context.getSource().getSender(), player.getUniqueId(),
                    PermissionNodes.EXEMPT_KICK)) {
                continue;
            }
            if (plugin.teleportService().teleport(player, actor.getLocation(), "tpall")) {
                moved++;
            }
        }
        plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(), "TPALL",
                null, null, moved + " player(s)");
        plugin.messageService().send(context.getSource().getSender(), "tpall-ok",
                "count", Integer.toString(moved));
        return moved;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tpPos(VisualStudioManagement plugin) {
        return Commands.literal("tppos")
                .requires(Cmd.requires(plugin, PermissionNodes.TP_POS))
                .then(Commands.argument("x", StringArgumentType.word())
                        .then(Commands.argument("y", StringArgumentType.word())
                                .then(Commands.argument("z", StringArgumentType.word())
                                        .executes(context -> tpPos(plugin, context, null))
                                        .then(Commands.argument("world", StringArgumentType.word())
                                                .suggests(Cmd.suggestions(() -> worlds()))
                                                .executes(context -> tpPos(plugin, context,
                                                        StringArgumentType.getString(context, "world")))))));
    }

    private static int tpPos(VisualStudioManagement plugin,
                             com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                             String world) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        boolean moved = plugin.teleportService().teleportToCoords(actor,
                StringArgumentType.getString(context, "x"),
                StringArgumentType.getString(context, "y"),
                StringArgumentType.getString(context, "z"), world);
        return moved ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> back(VisualStudioManagement plugin) {
        return Commands.literal("back")
                .requires(Cmd.requires(plugin, PermissionNodes.BACK))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    return actor == null ? 0 : (plugin.teleportService().back(actor) ? 1 : 0);
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> top(VisualStudioManagement plugin) {
        return Commands.literal("top")
                .requires(Cmd.requires(plugin, PermissionNodes.TOP))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    return actor == null ? 0 : (plugin.teleportService().top(actor) ? 1 : 0);
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tpOffline(VisualStudioManagement plugin) {
        return Commands.literal("tpoffline")
                .requires(Cmd.requires(plugin, PermissionNodes.TP_OFFLINE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            if (actor == null) {
                                return 0;
                            }
                            String name = StringArgumentType.getString(context, "player");
                            var target = Cmd.offlinePlayer(name);
                            return plugin.teleportService().toOffline(actor, target.getUniqueId(), name) ? 1 : 0;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> fly(VisualStudioManagement plugin) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("fly")
                .requires(Cmd.requires(plugin, PermissionNodes.FLY));
        node = node.executes(context -> {
            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
            if (actor == null) {
                return 0;
            }
            return toggleFly(plugin, context, actor, actor);
        });
        node = node.then(Commands.argument("state", StringArgumentType.word())
                .suggests(Cmd.suggestions(() -> List.of("on", "off")))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    return setFly(plugin, context, actor, actor,
                            "on".equalsIgnoreCase(StringArgumentType.getString(context, "state")));
                }));
        node = node.then(Commands.argument("state", StringArgumentType.word())
                .suggests(Cmd.suggestions(() -> List.of("on", "off")))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                            if (target == null) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            return setFly(plugin, context, target, target,
                                    "on".equalsIgnoreCase(StringArgumentType.getString(context, "state")));
                        })));
        return node;
    }

    private static int toggleFly(VisualStudioManagement plugin,
                                 com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                 Player actor, Player target) {
        return setFly(plugin, context, target, actor, !target.getAllowFlight());
    }

    private static int setFly(VisualStudioManagement plugin,
                              com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                              Player target, Player actor, boolean on) {
        target.setAllowFlight(on);
        target.setFlying(on);
        plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(), "FLY",
                target.getUniqueId(), target.getName(), on ? "on" : "off");
        plugin.messageService().send(context.getSource().getSender(), "fly-toggled",
                "player", target.getName(), "state", on ? "on" : "off");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> speed(VisualStudioManagement plugin) {
        return Commands.literal("speed")
                .requires(Cmd.requires(plugin, PermissionNodes.SPEED))
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 10))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            return actor == null ? 0
                                    : setSpeed(plugin, context, actor, actor,
                                    IntegerArgumentType.getInteger(context, "value"));
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.playerSuggestions(plugin))
                                .executes(context -> {
                                    Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                                    if (target == null) {
                                        plugin.messageService().send(context.getSource().getSender(),
                                                "player-not-found");
                                        return 0;
                                    }
                                    return setSpeed(plugin, context, target, target,
                                            IntegerArgumentType.getInteger(context, "value"));
                                })));
    }

    private static int setSpeed(VisualStudioManagement plugin,
                                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                Player target, Player actor, int value) {
        double cap = plugin.vsmConfig().speedCap(plugin.permissionService().rankOf(actor.getUniqueId()));
        double factor = Math.min(value, cap) / 10.0D;
        var attribute = target.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.setBaseValue(factor * 0.1D);
        }
        plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(), "SPEED",
                target.getUniqueId(), target.getName(), String.valueOf(value));
        plugin.messageService().send(context.getSource().getSender(), "speed-set",
                "player", target.getName(), "value", Integer.toString(value), "cap", Double.toString(cap));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gm(VisualStudioManagement plugin) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("gm")
                .requires(stack -> plugin.permissionService().has(stack.getSender(), PermissionNodes.GAMEMODE)
                        || plugin.permissionService().has(stack.getSender(), PermissionNodes.YOUTUBER_GM_SURVIVAL)
                        || plugin.permissionService().has(stack.getSender(), PermissionNodes.YOUTUBER_GM_ADVENTURE)
                        || plugin.permissionService().has(stack.getSender(), PermissionNodes.YOUTUBER_GM_CREATIVE));
        node.then(Commands.literal("toggle")
                .executes(context -> YouTuberCommands.toggleMode(plugin, context)));
        node.then(Commands.literal("survival")
                .executes(context -> YouTuberCommands.setMode(plugin, context, GameMode.SURVIVAL)));
        node.then(Commands.literal("adventure")
                .executes(context -> YouTuberCommands.setMode(plugin, context, GameMode.ADVENTURE)));
        node.then(Commands.literal("creative")
                .executes(context -> YouTuberCommands.setMode(plugin, context, GameMode.CREATIVE)));
        node.then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> List.of("survival", "creative", "adventure", "spectator")))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            return actor == null ? 0 : setGameMode(plugin, context, actor, actor,
                                    StringArgumentType.getString(context, "mode"));
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.playerSuggestions(plugin))
                                .executes(context -> {
                                    Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                                    if (target == null) {
                                        plugin.messageService().send(context.getSource().getSender(),
                                                "player-not-found");
                                        return 0;
                                    }
                                    return setGameMode(plugin, context, target, target,
                                            StringArgumentType.getString(context, "mode"));
                                }));
        return node;
    }

    private static int setGameMode(VisualStudioManagement plugin,
                                   com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                   Player target, Player actor, String raw) {
        GameMode mode;
        try {
            mode = GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messageService().send(context.getSource().getSender(), "invalid-gamemode");
            return 0;
        }
        GameMode previous = target.getGameMode();
        target.setGameMode(mode);
        plugin.auditService().log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(), "GAMEMODE",
                target.getUniqueId(), target.getName(), previous.name() + " -> " + mode.name());
        plugin.messageService().send(context.getSource().getSender(), "gamemode-changed",
                "player", target.getName(), "mode", mode.name(), "previous", previous.name());
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spectate(VisualStudioManagement plugin) {
        return Commands.literal("spectate")
                .requires(Cmd.requires(plugin, PermissionNodes.SPECTATE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                            if (actor == null || target == null) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            Location location = target.getLocation();
                            plugin.teleportService().remember(actor, "spectate");
                            actor.setGameMode(GameMode.SPECTATOR);
                            actor.teleport(location);
                            plugin.messageService().send(context.getSource().getSender(), "spectating",
                                    "target", target.getName());
                            return 1;
                        }))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            if (actor == null) {
                                return 0;
                            }
                            actor.setGameMode(GameMode.SURVIVAL);
                            plugin.teleportService().back(actor);
                            plugin.messageService().send(context.getSource().getSender(), "spectate-stopped");
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> follow(VisualStudioManagement plugin) {
        return Commands.literal("follow")
                .requires(Cmd.requires(plugin, PermissionNodes.FOLLOW))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                            if (actor == null || target == null) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            plugin.teleportService().startFollow(actor, target);
                            return 1;
                        }))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            if (actor == null) {
                                return 0;
                            }
                            plugin.teleportService().stopFollow(actor);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> staffMode(VisualStudioManagement plugin) {
        return Commands.literal("staffmode")
                .requires(Cmd.requires(plugin, PermissionNodes.STAFF_MODE))
                .executes(context -> {
                    Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                    if (actor == null) {
                        return 0;
                    }
                    plugin.staffModeService().toggle(actor, actor.getUniqueId(), actor.getName());
                    return 1;
                })
                .then(Commands.literal("restore")
                        .executes(context -> {
                            Player actor = Cmd.requirePlayer(context.getSource(), plugin);
                            if (actor == null) {
                                return 0;
                            }
                            plugin.staffModeService().restore(actor, actor.getUniqueId(), actor.getName());
                            plugin.messageService().send(context.getSource().getSender(), "staffmode-restored");
                            return 1;
                        }));
    }

    private static List<String> worlds() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }
}