package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The YouTuber-only parts of the toolkit (spec 8.8): {@code /vsm move} and the game-mode shortcuts.
 *
 * <p>{@code freeze}, {@code unfreeze}, {@code tp} and {@code gm} already exist as staff commands, so those
 * branches fall back to the scoped YouTuber powers when the sender holds only a {@code vsm.youtuber.*} node.
 * Every action is audited and limited by {@code YouTuberService}.
 */
public final class YouTuberCommands {

    private YouTuberCommands() {
    }

    /** Literals this module owns. */
    public static List<String> literals() {
        return List.of("move", "gms", "gma", "gmc", "gmt");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                   VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "move" -> move(plugin);
            case "gms" -> shortcut(plugin, org.bukkit.GameMode.SURVIVAL);
            case "gma" -> shortcut(plugin, org.bukkit.GameMode.ADVENTURE);
            case "gmc" -> shortcut(plugin, org.bukkit.GameMode.CREATIVE);
            case "gmt" -> toggle(plugin);
            default -> null;
        };
    }

    /** Attaches the YouTuber-only commands to the root literal. */
    public static void add(LiteralArgumentBuilder<CommandSourceStack> parent, VisualStudioManagement plugin) {
        for (String literal : literals()) {
            LiteralArgumentBuilder<CommandSourceStack> node = build(literal, plugin);
            if (node != null) {
                parent.then(node);
            }
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shortcut(
            VisualStudioManagement plugin, org.bukkit.GameMode mode) {
        String node = switch (mode) {
            case SURVIVAL -> PermissionNodes.YOUTUBER_GM_SURVIVAL;
            case ADVENTURE -> PermissionNodes.YOUTUBER_GM_ADVENTURE;
            default -> PermissionNodes.YOUTUBER_GM_CREATIVE;
        };
        return Commands.literal("g" + mode.name().charAt(0) + mode.name().substring(1).toLowerCase(
                        java.util.Locale.ROOT))
                .requires(Cmd.requires(plugin, node))
                .executes(context -> setMode(plugin, context, mode));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(VisualStudioManagement plugin) {
        return Commands.literal("gmt")
                .requires(Cmd.requires(plugin, PermissionNodes.YOUTUBER_GM_TOGGLE))
                .executes(context -> toggleMode(plugin, context));
    }

    // ------------------------------------------------------------- helpers
    /** Applies a game-mode change through whichever path the sender is entitled to. */
    public static int setMode(VisualStudioManagement plugin,
                              com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                              org.bukkit.GameMode mode) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        if (plugin.permissionService().has(context.getSource().getSender(), PermissionNodes.GAMEMODE)) {
            org.bukkit.GameMode previous = actor.getGameMode();
            actor.setGameMode(mode);
            plugin.auditService().log(io.github.aethelreborn.vsm.models.ActionSource.COMMAND,
                    actor.getUniqueId(), actor.getName(), "GAMEMODE", actor.getUniqueId(), actor.getName(),
                    previous.name() + " -> " + mode.name());
            plugin.messageService().send(context.getSource().getSender(), "youtuber-gamemode-changed",
                    "mode", mode.name());
            return 1;
        }
        return plugin.youTuberService().setGameMode(actor, mode) ? 1 : 0;
    }

    /** {@code gm toggle}: creative <-> survival, adventure -> survival. */
    public static int toggleMode(VisualStudioManagement plugin,
                                 com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        return plugin.youTuberService().toggleGameMode(actor) ? 1 : 0;
    }

    /** {@code true} when the sender is acting through the YouTuber role rather than staff powers. */
    public static boolean youTuberPath(VisualStudioManagement plugin, CommandSourceStack source, String staffNode) {
        return plugin.permissionService().has(source.getSender(),
                io.github.aethelreborn.vsm.config.PermissionNodes.YOUTUBER)
                && !plugin.permissionService().has(source.getSender(), staffNode);
    }

    // ---------------------------------------------------------------- move
    private static LiteralArgumentBuilder<CommandSourceStack> move(VisualStudioManagement plugin) {
        return Commands.literal("move")
                .requires(stack -> plugin.permissionService().has(stack.getSender(), PermissionNodes.YOUTUBER_MOVE)
                        || plugin.permissionService().has(stack.getSender(), PermissionNodes.TP_POS))
                .then(Commands.argument("x", StringArgumentType.word())
                        .then(Commands.argument("y", StringArgumentType.word())
                                .then(Commands.argument("z", StringArgumentType.word())
                                        .executes(context -> moveSelf(plugin, context, null))
                                        .then(Commands.argument("world", StringArgumentType.word())
                                                .suggests(Cmd.suggestions(() -> worlds()))
                                                .executes(context -> moveSelf(plugin, context,
                                                        StringArgumentType.getString(context, "world")))))))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.playerSuggestions(plugin))
                        .then(Commands.argument("x", StringArgumentType.word())
                                .then(Commands.argument("y", StringArgumentType.word())
                                        .then(Commands.argument("z", StringArgumentType.word())
                                                .executes(context -> moveOther(plugin, context, null))
                                                .then(Commands.argument("world", StringArgumentType.word())
                                                        .suggests(Cmd.suggestions(() -> worlds()))
                                                        .executes(context -> moveOther(plugin, context,
                                                                StringArgumentType.getString(context,
                                                                        "world"))))))));
    }

    private static int moveSelf(VisualStudioManagement plugin,
                                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                String world) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        if (actor == null) {
            return 0;
        }
        if (!youTuberPath(plugin, context.getSource(), PermissionNodes.TP_POS)) {
            return plugin.teleportService().teleportToCoords(actor,
                    StringArgumentType.getString(context, "x"),
                    StringArgumentType.getString(context, "y"),
                    StringArgumentType.getString(context, "z"), world) ? 1 : 0;
        }
        if (!plugin.youTuberService().checkCooldown(actor,
                plugin.vsmConfig().youtuberMoveCooldownSeconds(), "move")) {
            return 0;
        }
        Location destination = resolve(actor,
                StringArgumentType.getString(context, "x"),
                StringArgumentType.getString(context, "y"),
                StringArgumentType.getString(context, "z"), world);
        if (destination == null) {
            plugin.messageService().send(context.getSource().getSender(), "invalid-coordinates");
            return 0;
        }
        return plugin.teleportService().teleport(actor, destination, "youtuber-move") ? 1 : 0;
    }

    private static int moveOther(VisualStudioManagement plugin,
                                 com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                 String world) {
        Player actor = Cmd.requirePlayer(context.getSource(), plugin);
        Player target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
        if (actor == null || target == null) {
            plugin.messageService().send(context.getSource().getSender(), "player-not-found");
            return 0;
        }
        if (!plugin.permissionService().has(context.getSource().getSender(),
                PermissionNodes.YOUTUBER_MOVE_OTHERS)) {
            plugin.messageService().send(context.getSource().getSender(), "no-permission");
            return 0;
        }
        Location destination = resolve(target,
                StringArgumentType.getString(context, "x"),
                StringArgumentType.getString(context, "y"),
                StringArgumentType.getString(context, "z"), world);
        if (destination == null) {
            plugin.messageService().send(context.getSource().getSender(), "invalid-coordinates");
            return 0;
        }
        return plugin.youTuberService().move(actor, target, destination) ? 1 : 0;
    }

    private static Location resolve(Player reference, String xRaw, String yRaw, String zRaw, String worldName) {
        Location base = reference.getLocation();
        Double x = coordinate(xRaw, base.getX());
        Double y = coordinate(yRaw, base.getY());
        Double z = coordinate(zRaw, base.getZ());
        if (x == null || y == null || z == null) {
            return null;
        }
        World world = base.getWorld();
        if (worldName != null && !worldName.isBlank()) {
            World target = Bukkit.getWorld(worldName);
            if (target == null) {
                return null;
            }
            world = target;
        }
        return new Location(world, x, y, z);
    }

    private static Double coordinate(String raw, double base) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        try {
            if (text.equals("~")) {
                return base;
            }
            if (text.startsWith("~")) {
                return base + Double.parseDouble(text.substring(1));
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> worlds() {
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }
}