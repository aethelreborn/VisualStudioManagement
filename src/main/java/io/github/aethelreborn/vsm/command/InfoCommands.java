package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.util.TimeParser;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** History, notes and lookups (spec 8.3). */
public final class InfoCommands {

    private InfoCommands() {
    }

    /** Every literal this module owns, in tab-completion order. */
    public static java.util.List<String> literals() {
        return java.util.List.of("history", "staffhistory", "mutes", "bans", "warns", "note", "alts", "iphistory", "namehistory", "lookup", "seen");
    }

    /** Builds a single sub-command by literal name, or {@code null} when this module has none. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal,
                                                                  VisualStudioManagement plugin) {
        if (literal == null) {
            return null;
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "history" -> history(plugin);
            case "staffhistory" -> staffHistory(plugin);
            case "mutes" -> typeHistory(plugin, "mutes", "mute");
            case "bans" -> typeHistory(plugin, "bans", "ban");
            case "warns" -> typeHistory(plugin, "warns", "warn");
            case "note" -> note(plugin);
            case "alts" -> alts(plugin);
            case "iphistory" -> ipHistory(plugin);
            case "namehistory" -> nameHistory(plugin);
            case "lookup" -> lookup(plugin);
            case "seen" -> seen(plugin);
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

    private static LiteralArgumentBuilder<CommandSourceStack> history(VisualStudioManagement plugin) {
        return Commands.literal("history")
                .requires(Cmd.requires(plugin, PermissionNodes.HISTORY))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> printHistory(plugin, context,
                                StringArgumentType.getString(context, "player"), 0, null))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 100))
                                .executes(context -> printHistory(plugin, context,
                                        StringArgumentType.getString(context, "player"),
                                        IntegerArgumentType.getInteger(context, "page") - 1, null))
                                .then(Commands.literal("--type")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(Cmd.suggestions(() -> List.of("ban", "mute", "warn", "kick")))
                                                .executes(context -> printHistory(plugin, context,
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "page") - 1,
                                                        StringArgumentType.getString(context, "type")))))));
    }

    private static int printHistory(VisualStudioManagement plugin,
                                    com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                    String name, int page, String filter) {
        OfflinePlayer target = Cmd.offlinePlayer(name);
        if (!target.getUniqueId().equals(context.getSource().getSender() instanceof Player player
                ? player.getUniqueId() : null)
                && !plugin.permissionService().has(context.getSource().getSender(), PermissionNodes.HISTORY_OTHERS)) {
            plugin.messageService().send(context.getSource().getSender(), "no-permission");
            return 0;
        }
        List<Punishment> all = plugin.storage().history(target.getUniqueId(), 0, 500).join();
        List<Punishment> filtered = new ArrayList<>();
        for (Punishment punishment : all) {
            if (filter == null) {
                filtered.add(punishment);
                continue;
            }
            PunishmentType type = PunishmentType.fromString(filter.toUpperCase(Locale.ROOT));
            if (type == null) {
                continue;
            }
            boolean match = switch (filter.toLowerCase(Locale.ROOT)) {
                case "ban" -> punishment.type().isBan();
                case "mute" -> punishment.type().isMute();
                case "warn" -> punishment.type().isWarn();
                case "kick" -> punishment.type() == PunishmentType.KICK;
                default -> false;
            };
            if (match) {
                filtered.add(punishment);
            }
        }
        int perPage = 10;
        int from = Math.min(page * perPage, filtered.size());
        int to = Math.min(from + perPage, filtered.size());
        plugin.messageService().send(context.getSource().getSender(), "history-header",
                "player", name, "count", Integer.toString(filtered.size()),
                "page", Integer.toString(page + 1));
        for (Punishment punishment : filtered.subList(from, to)) {
            plugin.messageService().send(context.getSource().getSender(), "history-entry",
                    "id", punishment.id(),
                    "type", punishment.type().name(),
                    "reason", punishment.reason() == null ? "" : punishment.reason(),
                    "staff", punishment.staffName() == null ? "Console" : punishment.staffName(),
                    "expires", punishment.isPermanent() ? "never"
                            : TimeParser.formatRemaining(punishment.expiresAt(), System.currentTimeMillis()),
                    "active", Boolean.toString(punishment.active()));
        }
        return filtered.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> staffHistory(VisualStudioManagement plugin) {
        return Commands.literal("staffhistory")
                .requires(Cmd.requires(plugin, PermissionNodes.HISTORY_OTHERS))
                .then(Commands.argument("staff", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            OfflinePlayer target = Cmd.offlinePlayer(StringArgumentType.getString(context, "staff"));
                            var punishments = plugin.storage().byStaff(target.getUniqueId(), 0, 25).join();
                            plugin.messageService().send(context.getSource().getSender(), "staff-history-header",
                                    "staff", String.valueOf(target.getName()),
                                    "count", Integer.toString(punishments.size()));
                            for (Punishment punishment : punishments) {
                                plugin.messageService().send(context.getSource().getSender(), "history-entry",
                                        "id", punishment.id(),
                                        "type", punishment.type().name(),
                                        "reason", punishment.reason() == null ? "" : punishment.reason(),
                                        "staff", punishment.staffName() == null ? "Console" : punishment.staffName(),
                                        "expires", punishment.isPermanent() ? "never"
                                                : TimeParser.formatRemaining(punishment.expiresAt(),
                                                System.currentTimeMillis()),
                                        "active", Boolean.toString(punishment.active()));
                            }
                            return punishments.size();
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> typeHistory(
            VisualStudioManagement plugin, String name, String type) {
        return Commands.literal(name)
                .requires(Cmd.requires(plugin, PermissionNodes.HISTORY))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> printHistory(plugin, context,
                                StringArgumentType.getString(context, "player"), 0, type)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> note(VisualStudioManagement plugin) {
        return Commands.literal("note")
                .then(Commands.literal("add")
                        .requires(Cmd.requires(plugin, PermissionNodes.NOTES_ADD))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            OfflinePlayer target = Cmd.offlinePlayer(
                                                    StringArgumentType.getString(context, "player"));
                                            var note = plugin.noteService().add(context.getSource().getSender(),
                                                    Cmd.actorUuid(context.getSource()),
                                                    Cmd.actorName(context.getSource()),
                                                    target.getUniqueId(), String.valueOf(target.getName()),
                                                    StringArgumentType.getString(context, "text"), false);
                                            plugin.messageService().send(context.getSource().getSender(), "note-added",
                                                    "id", note.id());
                                            return 1;
                                        }))))
                .then(Commands.literal("remove")
                        .requires(Cmd.requires(plugin, PermissionNodes.NOTES_REMOVE))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> {
                                    plugin.noteService().remove(context.getSource().getSender(),
                                            Cmd.actorUuid(context.getSource()),
                                            Cmd.actorName(context.getSource()),
                                            StringArgumentType.getString(context, "id"));
                                    plugin.messageService().send(context.getSource().getSender(), "note-removed");
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .requires(Cmd.requires(plugin, PermissionNodes.NOTES_VIEW))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                                .executes(context -> {
                                    OfflinePlayer target = Cmd.offlinePlayer(
                                            StringArgumentType.getString(context, "player"));
                                    var notes = plugin.noteService().notes(target.getUniqueId());
                                    plugin.messageService().send(context.getSource().getSender(), "note-list-header",
                                            "player", String.valueOf(target.getName()),
                                            "count", Integer.toString(notes.size()));
                                    for (var note : notes) {
                                        plugin.messageService().send(context.getSource().getSender(), "note-list-entry",
                                                "id", note.id(),
                                                "text", note.text(),
                                                "author", note.authorName() == null ? "-" : note.authorName(),
                                                "pinned", Boolean.toString(note.pinned()));
                                    }
                                    return notes.size();
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> alts(VisualStudioManagement plugin) {
        return Commands.literal("alts")
                .requires(Cmd.requires(plugin, PermissionNodes.ALTS))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            OfflinePlayer target = Cmd.offlinePlayer(StringArgumentType.getString(context, "player"));
                            UUID uuid = target.getUniqueId();
                            List<UUID> alts = plugin.ipService().alts(uuid);
                            plugin.messageService().send(context.getSource().getSender(), "alts-header",
                                    "player", String.valueOf(target.getName()),
                                    "count", Integer.toString(alts.size()));
                            for (UUID alt : alts) {
                                String name = altName(plugin, alt);
                                boolean banned = plugin.punishmentService().activeFor(alt).stream()
                                        .anyMatch(p -> p.type().isBan());
                                boolean muted = plugin.punishmentCache().activeMutes(alt,
                                        System.currentTimeMillis()).isEmpty() == false;
                                plugin.messageService().send(context.getSource().getSender(), "alts-entry",
                                        "player", name,
                                        "banned", Boolean.toString(banned),
                                        "muted", Boolean.toString(muted));
                            }
                            return alts.size();
                        }));
    }

    private static String altName(VisualStudioManagement plugin, UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String stored = plugin.staffCache().byUuid(uuid) == null
                ? null : plugin.staffCache().byUuid(uuid).name();
        return stored == null ? uuid.toString() : stored;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ipHistory(VisualStudioManagement plugin) {
        return Commands.literal("iphistory")
                .requires(Cmd.requires(plugin, PermissionNodes.IP_HISTORY))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            String value = StringArgumentType.getString(context, "target");
                            OfflinePlayer target = Cmd.offlinePlayer(value);
                            var records = plugin.ipService().history(target.getUniqueId());
                            plugin.messageService().send(context.getSource().getSender(), "iphistory-header",
                                    "player", String.valueOf(target.getName()),
                                    "count", Integer.toString(records.size()));
                            for (var record : records) {
                                plugin.messageService().send(context.getSource().getSender(), "iphistory-entry",
                                        "ip", plugin.ipService().display(record.ip(), context.getSource().getSender()),
                                        "first", io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(
                                                record.firstSeen()),
                                        "last", io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(
                                                record.lastSeen()),
                                        "count", Integer.toString(record.timesSeen()));
                            }
                            return records.size();
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nameHistory(VisualStudioManagement plugin) {
        return Commands.literal("namehistory")
                .requires(Cmd.requires(plugin, PermissionNodes.LOOKUP))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            OfflinePlayer target = Cmd.offlinePlayer(StringArgumentType.getString(context, "player"));
                            var names = plugin.storage().nameHistory(target.getUniqueId()).join();
                            plugin.messageService().send(context.getSource().getSender(), "namehistory-header",
                                    "player", String.valueOf(target.getName()),
                                    "count", Integer.toString(names.size()));
                            for (String name : names) {
                                plugin.messageService().send(context.getSource().getSender(), "namehistory-entry",
                                        "name", name);
                            }
                            return names.size();
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lookup(VisualStudioManagement plugin) {
        return Commands.literal("lookup")
                .requires(Cmd.requires(plugin, PermissionNodes.LOOKUP))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            OfflinePlayer target = Cmd.offlinePlayer(StringArgumentType.getString(context, "player"));
                            UUID uuid = target.getUniqueId();
                            long now = System.currentTimeMillis();
                            plugin.messageService().send(context.getSource().getSender(), "lookup-header",
                                    "player", String.valueOf(target.getName()));
                            plugin.messageService().send(context.getSource().getSender(), "lookup-line",
                                    "key", "rank", "value", orDash(plugin.permissionService().rankOf(uuid)));
                            plugin.messageService().send(context.getSource().getSender(), "lookup-line",
                                    "key", "first join", "value", target.getFirstPlayed() == 0 ? "unknown"
                                    : io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(
                                            target.getFirstPlayed()));
                            plugin.messageService().send(context.getSource().getSender(), "lookup-line",
                                    "key", "last join", "value", target.getLastPlayed() == 0 ? "unknown"
                                    : io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(
                                            target.getLastPlayed()));
                            plugin.messageService().send(context.getSource().getSender(), "lookup-line",
                                    "key", "active punishments",
                                    "value", Integer.toString(plugin.punishmentCache().activeFor(uuid, now).size()));
                            plugin.messageService().send(context.getSource().getSender(), "lookup-line",
                                    "key", "warnings",
                                    "value", Integer.toString(plugin.punishmentCache().activeWarnings(uuid, now).size()));
                            plugin.messageService().send(context.getSource().getSender(), "lookup-line",
                                    "key", "notes",
                                    "value", Integer.toString(plugin.noteService().notes(uuid).size()));
                            plugin.messageService().send(context.getSource().getSender(), "lookup-line",
                                    "key", "alts",
                                    "value", Integer.toString(plugin.ipService().alts(uuid).size()));
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> seen(VisualStudioManagement plugin) {
        return Commands.literal("seen")
                .requires(Cmd.requires(plugin, PermissionNodes.SEEN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(Cmd.suggestions(() -> Cmd.onlineNames(plugin)))
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "player");
                            Player online = Cmd.onlinePlayer(name);
                            if (online != null) {
                                plugin.messageService().send(context.getSource().getSender(), "seen-online",
                                        "player", online.getName(),
                                        "world", online.getWorld().getName());
                                return 1;
                            }
                            OfflinePlayer target = Cmd.offlinePlayer(name);
                            if (target.getLastPlayed() == 0L) {
                                plugin.messageService().send(context.getSource().getSender(), "seen-never",
                                        "player", String.valueOf(target.getName()));
                                return 0;
                            }
                            plugin.messageService().send(context.getSource().getSender(), "seen-offline",
                                    "player", String.valueOf(target.getName()),
                                    "last", io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(
                                            target.getLastPlayed()));
                            return 1;
                        }));
    }

    private static String orDash(String value) {
        return value == null ? "-" : value;
    }
}