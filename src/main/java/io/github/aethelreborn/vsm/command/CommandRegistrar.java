package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers the {@code /vsm} root literal and the standalone aliases (spec 8.0).
 *
 * <p>Everything is reachable as {@code /vsm <sub-command>}. When {@code commands.standalone-aliases} is on,
 * the same sub-trees are additionally registered under their historical names ({@code /ban}, {@code /tp},
 * {@code /freeze} ...). Each literal has exactly one builder, so a sub-command only appears in
 * tab-completion when the sender holds the node behind its {@code requires(...)} predicate.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    /** Every literal exposed by the plugin, in tab-completion order and free of duplicates. */
    public static List<String> literals(VisualStudioManagement plugin) {
        List<String> names = new ArrayList<>();
        names.addAll(StaffCommands.literals());
        names.addAll(PunishCommands.literals());
        names.addAll(MovementCommands.literals());
        names.addAll(ToolsCommands.literals());
        names.addAll(InfoCommands.literals());
        names.addAll(ReportCommands.literals());
        names.addAll(ChatCommands.literals());
        names.addAll(ChatCommands.longFormLiterals());
        names.addAll(YouTuberCommands.literals());
        names.addAll(UtilityCommands.literals());
        List<String> unique = new ArrayList<>();
        for (String name : names) {
            if (!unique.contains(name)) {
                unique.add(name);
            }
        }
        return unique;
    }

    /** Builds a single sub-command by literal name, or {@code null} when nothing owns it. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                   VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        String key = literal.toLowerCase(Locale.ROOT);
        LiteralArgumentBuilder<CommandSourceStack> node =
                StaffCommands.build(key, plugin);
        if (node == null) {
            node = PunishCommands.build(key, plugin);
        }
        if (node == null) {
            node = MovementCommands.build(key, plugin);
        }
        if (node == null) {
            node = ToolsCommands.build(key, plugin);
        }
        if (node == null) {
            node = InfoCommands.build(key, plugin);
        }
        if (node == null) {
            node = ReportCommands.build(key, plugin);
        }
        if (node == null) {
            node = ChatCommands.build(key, plugin);
        }
        if (node == null) {
            node = ChatCommands.buildLongForm(key, plugin);
        }
        if (node == null) {
            node = YouTuberCommands.build(key, plugin);
        }
        if (node == null) {
            node = UtilityCommands.build(key, plugin);
        }
        return node;
    }

    /**
     * Registers the command tree. Invoked from the Paper {@code COMMANDS} lifecycle event, which fires after
     * {@code onEnable}, so every service the executors touch is already constructed.
     */
    public static void register(Commands registrar, JavaPlugin javaPlugin) {
        if (javaPlugin == null) {
            return;
        }
        VisualStudioManagement plugin = (VisualStudioManagement) javaPlugin;
        var config = plugin.vsmConfig();
        String rootLiteral = config.rootCommand();
        boolean aliases = config.standaloneAliases();
        String root = rootLiteral.toLowerCase(Locale.ROOT);
        LiteralArgumentBuilder<CommandSourceStack> rootNode = Commands.literal(root)
                .requires(stack -> stack.getSender().hasPermission(PermissionNodes.USE)
                        || stack.getSender().hasPermission(PermissionNodes.ALL))
                .executes(context -> {
                    plugin.messageService().send(context.getSource().getSender(), "about",
                            "version", plugin.getPluginMeta().getVersion(),
                            "storage", plugin.storage().name(),
                            "server", plugin.vsmConfig().serverName(),
                            "staff", Integer.toString(plugin.staffCache().size()),
                            "punishments", Integer.toString(plugin.punishmentService().allActive().size()));
                    return 1;
                });

        for (String literal : literals(plugin)) {
            LiteralArgumentBuilder<CommandSourceStack> node = build(literal, plugin);
            if (node != null) {
                rootNode.then(node);
            }
        }
        LiteralCommandNode<CommandSourceStack> built = rootNode.build();
        plugin.getLogger().info("[VSM] cmd root=" + root + " literals=" + literals(plugin)
                + " children=" + built.getChildren().size() + " rootHasCommand=" + (built.getCommand() != null));
        try {
            java.util.Set<String> labels = registrar.register(built, "Visual Studio Management");
            plugin.getLogger().info("[VSM] cmd registered labels=" + labels);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[VSM] root registration failed", t);
            throw t;
        }

        if (!aliases) {
            return;
        }
        for (String alias : literals(plugin)) {
            LiteralArgumentBuilder<CommandSourceStack> node = build(alias, plugin);
            if (node == null) {
                continue;
            }
            try {
                java.util.Set<String> labels = registrar.register(node.build(), (String) null);
                plugin.getLogger().info("[VSM] alias " + alias + " registered labels=" + labels);
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[VSM] alias " + alias + " registration failed", t);
                throw t;
            }
        }
    }
}