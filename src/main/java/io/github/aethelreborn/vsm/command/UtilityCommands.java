package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.integration.importers.AdvancedBanImporter;
import io.github.aethelreborn.vsm.integration.importers.LiteBansImporter;
import io.github.aethelreborn.vsm.integration.importers.VanillaBanImporter;
import io.github.aethelreborn.vsm.models.AuditEntry;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Plugin administration commands (spec 8.7). */
public final class UtilityCommands {

    private UtilityCommands() {
    }

    public static List<String> literals() {
        return List.of("about", "reload", "debug", "import", "export", "purge", "backup", "audit", "playerinfo");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                   VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(Locale.ROOT)) {
            case "about" -> about(plugin);
            case "reload" -> reload(plugin);
            case "debug" -> debug(plugin);
            case "import" -> importData(plugin);
            case "export" -> export(plugin);
            case "purge" -> purge(plugin);
            case "backup" -> backup(plugin);
            case "audit" -> audit(plugin);
            case "playerinfo" -> playerInfo(plugin);
            default -> null;
        };
    }

    public static void add(LiteralArgumentBuilder<CommandSourceStack> root, VisualStudioManagement plugin) {
        for (String literal : literals()) {
            LiteralArgumentBuilder<CommandSourceStack> node = build(literal, plugin);
            if (node != null) {
                root.then(node);
            }
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> about(VisualStudioManagement plugin) {
        return Commands.literal("about")
                .executes(context -> {
                    plugin.messageService().send(context.getSource().getSender(), "about",
                            "version", plugin.getPluginMeta().getVersion(),
                            "storage", plugin.storage().name(),
                            "server", plugin.vsmConfig().serverName(),
                            "staff", Integer.toString(plugin.staffCache().size()),
                            "punishments", Integer.toString(plugin.punishmentService().allActive().size()));
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reload(VisualStudioManagement plugin) {
        return Commands.literal("reload")
                .requires(Cmd.requires(plugin, PermissionNodes.RELOAD))
                .executes(context -> {
                    boolean ok = plugin.reload();
                    plugin.messageService().send(context.getSource().getSender(),
                            ok ? "reload-ok" : "reload-failed");
                    return ok ? 1 : 0;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debug(VisualStudioManagement plugin) {
        return Commands.literal("debug")
                .requires(Cmd.requires(plugin, PermissionNodes.DEBUG))
                .executes(context -> {
                    var punishmentStats = plugin.punishmentCache().statistics();
                    plugin.messageService().send(context.getSource().getSender(), "debug-header");
                    plugin.messageService().send(context.getSource().getSender(), "debug-line",
                            "key", "staff cached", "value", Integer.toString(plugin.staffCache().size()));
                    for (var entry : punishmentStats.entrySet()) {
                        plugin.messageService().send(context.getSource().getSender(), "debug-line",
                                "key", "punishments " + entry.getKey(), "value", entry.getValue().toString());
                    }
                    for (var entry : plugin.ipCache().statistics().entrySet()) {
                        plugin.messageService().send(context.getSource().getSender(), "debug-line",
                                "key", "ips " + entry.getKey(), "value", entry.getValue().toString());
                    }
                    plugin.messageService().send(context.getSource().getSender(), "debug-line",
                            "key", "audit queue", "value", Integer.toString(plugin.auditService().pending()));
                    plugin.messageService().send(context.getSource().getSender(), "debug-line",
                            "key", "declared nodes", "value", Integer.toString(plugin.declaredNodes()));
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> importData(VisualStudioManagement plugin) {
        return Commands.literal("import")
                .requires(Cmd.requires(plugin, PermissionNodes.IMPORT))
                .then(Commands.argument("source", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> List.of("litebans", "advancedban", "vanilla")))
                        .executes(context -> runImport(plugin, context, false))
                        .then(Commands.literal("--dry-run")
                                .executes(context -> runImport(plugin, context, true))));
    }

    private static int runImport(VisualStudioManagement plugin,
                                 com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                 boolean dryRun) {
        String source = StringArgumentType.getString(context, "source").toLowerCase(Locale.ROOT);
        Path plugins = Bukkit.getPluginsFolder().toPath();
        var importer = switch (source) {
            case "litebans" -> new LiteBansImporter(plugin, plugins.resolve("LiteBans/database.sqlite"));
            case "advancedban" -> new AdvancedBanImporter(plugin,
                    plugin.vsmConfig().mysqlHost() + "/" + plugin.vsmConfig().mysqlDatabase(),
                    plugin.vsmConfig().mysqlUser(), plugin.vsmConfig().mysqlPassword());
            case "vanilla" -> new VanillaBanImporter(plugin, plugins.getParent());
            default -> null;
        };
        if (importer == null) {
            plugin.messageService().send(context.getSource().getSender(), "unknown-import-source",
                    "source", source);
            return 0;
        }
        plugin.messageService().send(context.getSource().getSender(), "import-started",
                "source", importer.id());
        plugin.scheduler().async(() -> {
            String summary = importer.run(dryRun);
            plugin.scheduler().sync(() -> plugin.messageService().send(context.getSource().getSender(),
                    "import-finished", "summary", TextUtil.truncate(summary, 180)));
        });
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> export(VisualStudioManagement plugin) {
        return Commands.literal("export")
                .requires(Cmd.requires(plugin, PermissionNodes.EXPORT))
                .then(Commands.argument("what", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> List.of("punishments", "audit")))
                        .executes(context -> {
                            plugin.scheduler().async(() -> {
                                String json = "audit".equalsIgnoreCase(
                                        StringArgumentType.getString(context, "what"))
                                        ? auditJson(plugin) : plugin.storage().exportAll().join();
                                plugin.scheduler().sync(() -> writeExport(plugin, context, json));
                            });
                            return 1;
                        }));
    }

    private static String auditJson(VisualStudioManagement plugin) {
        StringBuilder out = new StringBuilder("{\"audit\":[");
        List<AuditEntry> entries = plugin.storage().auditAll().join();
        for (int i = 0; i < entries.size(); i++) {
            AuditEntry entry = entries.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append(io.github.aethelreborn.vsm.util.Json.object()
                    .put("id", entry.id())
                    .put("timestamp", entry.timestamp())
                    .put("staff", entry.staffName())
                    .put("action", entry.action())
                    .put("target", entry.targetName())
                    .put("source", entry.source().name())
                    .toString());
        }
        return out.append("]}").toString();
    }

    private static void writeExport(VisualStudioManagement plugin,
                                    com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                    String json) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = plugin.getDataFolder().toPath().resolve("export-" + stamp + ".json");
        try {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            plugin.messageService().send(context.getSource().getSender(), "export-ok",
                    "file", file.getFileName().toString());
        } catch (IOException ex) {
            plugin.messageService().send(context.getSource().getSender(), "export-failed");
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> purge(VisualStudioManagement plugin) {
        return Commands.literal("purge")
                .requires(Cmd.requires(plugin, PermissionNodes.PURGE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            OfflinePlayer target = Cmd.offlinePlayer(StringArgumentType.getString(context, "player"));
                            plugin.scheduler().async(() -> plugin.storage()
                                    .purgePlayer(target.getUniqueId())
                                    .thenRun(() -> plugin.scheduler().sync(() -> {
                                        plugin.ipCache().remove(target.getUniqueId());
                                        plugin.messageService().send(context.getSource().getSender(),
                                                "purge-ok", "player", String.valueOf(target.getName()));
                                    })));
                            plugin.auditService().log(io.github.aethelreborn.vsm.models.ActionSource.COMMAND,
                                    Cmd.actorUuid(context.getSource()), Cmd.actorName(context.getSource()),
                                    "PURGE", target.getUniqueId(), String.valueOf(target.getName()), "");
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> backup(VisualStudioManagement plugin) {
        return Commands.literal("backup")
                .requires(Cmd.requires(plugin, PermissionNodes.PURGE))
                .executes(context -> {
                    plugin.scheduler().async(() -> {
                        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                        String json = plugin.storage().exportAll().join();
                        Path file = plugin.getDataFolder().toPath().resolve("backup-" + stamp + ".json");
                        try {
                            Files.writeString(file, json, StandardCharsets.UTF_8);
                            plugin.scheduler().sync(() -> plugin.messageService().send(
                                    context.getSource().getSender(), "backup-ok",
                                    "file", file.getFileName().toString()));
                        } catch (IOException ex) {
                            plugin.getLogger().warning("Backup failed: " + ex.getMessage());
                        }
                    });
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> audit(VisualStudioManagement plugin) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("audit")
                .requires(Cmd.requires(plugin, PermissionNodes.AUDIT_VIEW));
        node.then(Commands.literal("verify")
                .executes(context -> {
                    var entries = plugin.storage().auditAll().join();
                    var result = plugin.auditService().verify(entries);
                    plugin.messageService().send(context.getSource().getSender(),
                            result.valid() ? "audit-verify-ok" : "audit-verify-broken",
                            "checked", Integer.toString(result.checked()),
                            "id", result.firstBrokenId() == null ? "-" : result.firstBrokenId());
                    return result.valid() ? 1 : 0;
                }));
        node.then(Commands.literal("export")
                .requires(Cmd.requires(plugin, PermissionNodes.AUDIT_EXPORT))
                .executes(context -> {
                    plugin.scheduler().async(() -> {
                        String json = auditJson(plugin);
                        plugin.scheduler().sync(() -> writeExport(plugin, context, json));
                    });
                    return 1;
                }));
        node.then(Commands.argument("page", IntegerArgumentType.integer(1, 1000))
                .executes(context -> printAudit(plugin, context,
                        IntegerArgumentType.getInteger(context, "page") - 1, null, null)));
        return node;
    }

    private static int printAudit(VisualStudioManagement plugin,
                                  com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                  int page, String staff, String action) {
        var entries = plugin.storage().auditPage(null, action, page, 10).join();
        plugin.messageService().send(context.getSource().getSender(), "audit-header",
                "page", Integer.toString(page + 1), "count", Integer.toString(entries.size()));
        for (AuditEntry entry : entries) {
            plugin.messageService().send(context.getSource().getSender(), "audit-entry",
                    "time", io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(entry.timestamp()),
                    "staff", entry.staffName(),
                    "action", entry.action(),
                    "target", entry.targetName() == null ? "-" : entry.targetName(),
                    "source", entry.source().name());
        }
        return entries.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerInfo(VisualStudioManagement plugin) {
        return Commands.literal("playerinfo")
                .requires(Cmd.requires(plugin, PermissionNodes.LOOKUP))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            var target = Cmd.onlinePlayer(StringArgumentType.getString(context, "player"));
                            if (target == null) {
                                plugin.messageService().send(context.getSource().getSender(), "player-not-found");
                                return 0;
                            }
                            io.github.aethelreborn.vsm.gui.PlayerInfoMenu.open(plugin,
                                    Cmd.requirePlayer(context.getSource(), plugin), target);
                            return 1;
                        }));
    }
}