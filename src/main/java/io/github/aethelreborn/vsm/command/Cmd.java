package io.github.aethelreborn.vsm.command;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.services.ActionRequest;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for every Brigadier command: actor resolution, permission predicates, player lookup and
 * the translation of a Brigadier context into an {@link ActionRequest}.
 */
public final class Cmd {

    private Cmd() {
    }

    public static CommandSender sender(CommandSourceStack source) {
        return source.getSender();
    }

    /** The acting staff member, or {@code null} for the console. */
    public static @Nullable UUID actorUuid(CommandSourceStack source) {
        return source.getSender() instanceof Player player ? player.getUniqueId() : null;
    }

    public static String actorName(CommandSourceStack source) {
        return source.getSender() == null ? "console" : source.getSender().getName();
    }

    public static ActionSource source(CommandSourceStack source) {
        return source.getSender() instanceof Player ? ActionSource.COMMAND : ActionSource.CONSOLE;
    }

    /** A permission predicate used by {@code requires(...)}; console always passes. */
    public static java.util.function.Predicate<CommandSourceStack> requires(
            VisualStudioManagement plugin, String node) {
        return stack -> plugin.permissionService().has(stack.getSender(), node);
    }

    /** Same as {@link #requires} but keeps sub-commands hidden when the player already owns the node. */
    public static java.util.function.Predicate<CommandSourceStack> requiresAnd(
            VisualStudioManagement plugin, String node) {
        return stack -> stack.getSender() instanceof Player && plugin.permissionService().has(stack.getSender(), node);
    }

    /** Resolves an online player by exact name, ignoring case. */
    public static @Nullable Player onlinePlayer(String name) {
        return Bukkit.getPlayerExact(name);
    }

    /**
     * Resolves a player by name, including offline accounts through the server's profile cache.
     *
     * @return the profile, never {@code null} unless the name is malformed
     */
    public static OfflinePlayer offlinePlayer(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    /** Node permission for a punishment type, used by both the command and the GUI. */
    public static String permissionFor(PunishmentType type) {
        return switch (type) {
            case WARN, TEMP_WARN -> PermissionNodes.WARN;
            case MUTE -> PermissionNodes.MUTE;
            case TEMP_MUTE -> PermissionNodes.TEMP_MUTE;
            case SHADOW_MUTE -> PermissionNodes.SHADOW_MUTE;
            case IP_MUTE -> PermissionNodes.IP_MUTE;
            case TEMP_IP_MUTE -> PermissionNodes.IP_MUTE;
            case KICK -> PermissionNodes.KICK;
            case BAN -> PermissionNodes.BAN;
            case TEMP_BAN -> PermissionNodes.TEMP_BAN;
            case IP_BAN -> PermissionNodes.IP_BAN;
            case TEMP_IP_BAN -> PermissionNodes.TEMP_IP_BAN;
            case NAME_BAN -> PermissionNodes.NAME_BAN;
        };
    }

    /** Builds an {@link ActionRequest} pre-filled with the actor and the command source. */
    public static ActionRequest request(CommandSourceStack source, String permission) {
        return ActionRequest.builder(sourceOf(source))
                .actor(actorUuid(source), actorName(source))
                .permission(permission);
    }

    private static ActionSource sourceOf(CommandSourceStack source) {
        return source.getSender() instanceof Player ? ActionSource.COMMAND : ActionSource.CONSOLE;
    }

    /** Sends a message and returns a single-success result. */
    public static int ok(CommandSourceStack source, VisualStudioManagement plugin, String key, Object... placeholders) {
        plugin.messageService().send(source.getSender(), key, placeholders);
        return 1;
    }

    public static int info(CommandSourceStack source, VisualStudioManagement plugin, String key, Object... placeholders) {
        plugin.messageService().sendRaw(source.getSender(), key, placeholders);
        return 1;
    }

    /** Requires a player sender; console is refused with the console-only message. */
    public static Player requirePlayer(CommandSourceStack source, VisualStudioManagement plugin) {
        if (source.getSender() instanceof Player player) {
            return player;
        }
        plugin.messageService().send(source.getSender(), "player-only-command");
        return null;
    }

    /** Online player name suggestions. */
    public static List<String> onlineNames(VisualStudioManagement plugin) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    /** Suggestion provider for online players, hiding vanished staff from unprivileged viewers. */
    public static com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> playerSuggestions(
            VisualStudioManagement plugin) {
        return (context, builder) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (plugin.vanishService().isVanished(player)
                        && !plugin.permissionService().has(context.getSource().getSender(), PermissionNodes.VANISH_SEE)) {
                    continue;
                }
                builder.suggest(player.getName());
            }
            return builder.buildFuture();
        };
    }

    /** Suggestion provider backed by a static list. */
    public static com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> suggestions(
            Supplier<List<String>> values) {
        return (context, builder) -> {
            String current = context.getLastChild().getCommand().getInput();
            for (String value : values.get()) {
                if (current.isEmpty() || value.toLowerCase(java.util.Locale.ROOT)
                        .startsWith(current.toLowerCase(java.util.Locale.ROOT))) {
                    builder.suggest(value);
                }
            }
            return builder.buildFuture();
        };
    }
}