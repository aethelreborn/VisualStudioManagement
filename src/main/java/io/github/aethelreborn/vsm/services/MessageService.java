package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.Messages;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.models.Report;
import io.github.aethelreborn.vsm.util.MessageUtil;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Themed screens and notifications (spec 9.5, 9.6, 10, 11.2, 12).
 *
 * <p>This is the only place that decides which {@code messages.yml} key to use for a situation, so no user
 * visible text is ever hard coded in the rest of the plugin.
 */
public final class MessageService {

    private final VsmConfig config;
    private final Messages messages;
    private final MessageUtil util;
    private final PermissionService permissions;
    private final SchedulerAdapter scheduler;

    public MessageService(VsmConfig config, Messages messages, PermissionService permissions,
                          SchedulerAdapter scheduler) {
        this.config = config;
        this.messages = messages;
        this.util = new MessageUtil(messages);
        this.permissions = permissions;
        this.scheduler = scheduler;
    }

    public Messages messages() {
        return messages;
    }

    public MessageUtil util() {
        return util;
    }

    /** Sends a prefixed message with ordered key/value placeholders. */
    public void send(CommandSender to, String key, Object... placeholders) {
        util.send(to, key, placeholders);
    }

    public void sendRaw(CommandSender to, String key, Object... placeholders) {
        util.sendRaw(to, key, placeholders);
    }

    // ------------------------------------------------------------ screens
    private static String screenKey(PunishmentType type) {
        return switch (type) {
            case BAN -> "screen-ban";
            case TEMP_BAN -> "screen-tempban";
            case IP_BAN, TEMP_IP_BAN -> "screen-ipban";
            case KICK -> "screen-kick";
            case NAME_BAN -> "screen-nameban";
            default -> "screen-ban";
        };
    }

    /** The themed ban/kick screen shown on login or on disconnect. */
    public Component banScreen(Punishment punishment) {
        String key = screenKey(punishment.type());
        String expires = punishment.isPermanent()
                ? "Never"
                : TimeParser.formatRemaining(punishment.expiresAt(), System.currentTimeMillis());
        String reason = punishment.reason() == null ? "No reason given" : punishment.reason();
        return messages.screen(key, Messages.placeholders(
                "reason", reason,
                "staff", punishment.staffName() == null ? "Console" : punishment.staffName(),
                "expires", expires,
                "id", punishment.id(),
                "appeal", config.appealUrl(),
                "type", punishment.type().name()));
    }

    /** Disconnects a player with the themed screen; must run on the main thread. */
    public void banScreenKick(Player player, Punishment punishment) {
        if (player == null) {
            return;
        }
        player.kick(banScreen(punishment));
    }

    /** Message shown to a muted player when they try to talk. */
    public void muteNotice(Player player, Punishment mute) {
        String remaining = mute.isPermanent()
                ? "permanently"
                : TimeParser.formatRemaining(mute.expiresAt(), System.currentTimeMillis());
        send(player, "mute-notice",
                "reason", mute.reason() == null ? "No reason given" : mute.reason(),
                "staff", mute.staffName() == null ? "Console" : mute.staffName(),
                "remaining", remaining,
                "id", mute.id());
    }

    /** Staff notification about a punishment; honours the per-staff alert toggle. */
    public void notifyStaffIfEnabled(Player staff, Punishment punishment, String actorName) {
        if (staff == null || !permissions.has(staff.getUniqueId(), PermissionNodes.ALERTS)) {
            return;
        }
        if (!staffState.alerts(staff.getUniqueId())) {
            return;
        }
        send(staff, "staff-alert-punishment",
                "staff", safe(actorName),
                "type", punishment.type().name(),
                "target", punishment.displayTarget(),
                "reason", punishment.reason() == null ? "" : punishment.reason(),
                "id", punishment.id());
    }

    /** Broadcasts a punishment to everyone, per the per-type configuration. */
    public void broadcast(Punishment punishment, String staffName) {
        String duration = punishment.isPermanent()
                ? "never"
                : TimeParser.formatRemaining(punishment.expiresAt(), System.currentTimeMillis());
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, "punishment-broadcast",
                    "player", punishment.displayTarget(),
                    "type", punishment.type().name(),
                    "reason", punishment.reason() == null ? "" : punishment.reason(),
                    "staff", staffName == null ? "Console" : staffName,
                    "duration", duration,
                    "id", punishment.id());
        }
    }

    /** Title and chat notice shown when a player is frozen (spec 11.2). */
    public void freezeNotice(Player player, String reason, String staffName) {
        if (config.freezeTitle()) {
            player.showTitle(Title.title(
                    messages.get("freeze-title", "reason", safe(reason)),
                    messages.get("freeze-subtitle", "staff", safe(staffName)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
        }
        send(player, "freeze-notice", "reason", safe(reason), "staff", safe(staffName));
    }

    public void unfreezeNotice(Player player) {
        send(player, "unfreeze-notice");
    }

    /** Staff alert with a click-to-teleport hint for a new report (spec 10). */
    public void reportAlert(Player staff, Report report) {
        if (!permissions.has(staff.getUniqueId(), PermissionNodes.REPORTS_VIEW) || !config.notifyStaffOnReport()) {
            return;
        }
        staff.sendMessage(messages.get("report-alert", Messages.placeholders(
                "id", Long.toString(report.id()),
                "reporter", safe(report.reporterName()),
                "target", safe(report.targetName()),
                "reason", safe(report.reason()),
                "priority", report.priority().name())));
    }

    /** Staff chat; never visible to normal players (spec 11.1). */
    public void staffChat(CommandSender sender, String message) {
        Component component = messages.get("staffchat-format", Messages.placeholders(
                "staff", safe(sender.getName()),
                "message", TextUtil.sanitiseForWebhook(message)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permissions.has(player.getUniqueId(), PermissionNodes.STAFF_CHAT)) {
                player.sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    /** Admin chat, admin only (spec 8.6). */
    public void adminChat(CommandSender sender, String message) {
        Component component = messages.get("adminchat-format", Messages.placeholders(
                "staff", safe(sender.getName()),
                "message", TextUtil.sanitiseForWebhook(message)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permissions.has(player.getUniqueId(), PermissionNodes.ADMIN_CHAT)) {
                player.sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    /** Placeholder helper. */
    public Map<String, String> placeholders(Object... pairs) {
        return Messages.placeholders(pairs);
    }

    public void onMain(Runnable task) {
        scheduler.sync(task);
    }

    /** Resolves a UUID to a live name where possible. */
    public String nameOf(UUID uuid, String fallback) {
        Player online = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return uuid == null ? "unknown" : uuid.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Per-player toggles that are not persisted (alerts, staff chat mode). */
    private volatile StaffState staffState = new StaffState();

    public void staffState(StaffState staffState) {
        this.staffState = staffState == null ? new StaffState() : staffState;
    }

    public StaffState staffState() {
        return staffState;
    }
}