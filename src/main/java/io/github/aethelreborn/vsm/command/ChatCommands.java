package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import org.bukkit.entity.Player;

/** Staff chat and admin chat (spec 8.6, 11.1). */
public final class ChatCommands {

    private ChatCommands() {
    }

    /** Every literal this module owns, in tab-completion order. */
    public static java.util.List<String> literals() {
        return java.util.List.of("sc", "ac");
    }

    /** Builds a single sub-command by literal name, or {@code null} when this module has none. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                  VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "sc" -> return sc(plugin);
            case "ac" -> return ac(plugin);
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

    private static LiteralArgumentBuilder<CommandSourceStack> staffChat(VisualStudioManagement plugin) {
        return Commands.literal("sc")
                .requires(Cmd.requires(plugin, PermissionNodes.STAFF_CHAT))
                .executes(context -> {
                    Player player = Cmd.requirePlayer(context.getSource(), plugin);
                    if (player == null) {
                        return 0;
                    }
                    boolean on = plugin.staffState().toggleStaffChatMode(player.getUniqueId());
                    plugin.messageService().send(context.getSource().getSender(),
                            on ? "staffchat-toggle-on" : "staffchat-toggle-off");
                    return 1;
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            plugin.messageService().staffChat(context.getSource().getSender(),
                                    StringArgumentType.getString(context, "message"));
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminChat(VisualStudioManagement plugin) {
        return Commands.literal("ac")
                .requires(Cmd.requires(plugin, PermissionNodes.ADMIN_CHAT))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            plugin.messageService().adminChat(context.getSource().getSender(),
                                    StringArgumentType.getString(context, "message"));
                            return 1;
                        }));
    }

    /** The long forms {@code /staffchat} and {@code /adminchat}. */
    public static List<String> longFormLiterals() {
        return List.of("staffchat", "adminchat");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildLongForm(String literal,
                                                                           VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "staffchat" -> Commands.literal("staffchat")
                    .requires(Cmd.requires(plugin, PermissionNodes.STAFF_CHAT))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> {
                                plugin.messageService().staffChat(context.getSource().getSender(),
                                        StringArgumentType.getString(context, "message"));
                                return 1;
                            }));
            case "adminchat" -> Commands.literal("adminchat")
                    .requires(Cmd.requires(plugin, PermissionNodes.ADMIN_CHAT))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> {
                                plugin.messageService().adminChat(context.getSource().getSender(),
                                        StringArgumentType.getString(context, "message"));
                                return 1;
                            }));
            default -> null;
        };
    }
}