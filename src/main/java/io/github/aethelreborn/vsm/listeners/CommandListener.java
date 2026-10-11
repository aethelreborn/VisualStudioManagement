package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Command filtering (spec 9.5, 11.2, 11.7): muted players lose chat-like commands, frozen players lose
 * everything outside the whitelist, staff get command spy and auto-mod rate limiting applies.
 */
public final class CommandListener implements Listener {

    /** Commands that are always blocked while muted (spec 9.5). */
    private static final List<String> SOCIAL_COMMANDS = List.of(
            "/me", "/say", "/msg", "/tell", "/w", "/reply", "/r", "/mail", "/nick", "/sign", "/book");

    private final VisualStudioManagement plugin;

    public CommandListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        var uuid = player.getUniqueId();
        String raw = event.getMessage();
        String command = raw.split(" ")[0].toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        // Frozen players may only use the whitelist (spec 11.2).
        if (plugin.freezeService().isFrozen(uuid)) {
            String[] allowed = plugin.vsmConfig().youtuberFreezeAllowedCommands().toArray(new String[0]);
            String[] staffAllowed = plugin.vsmConfig().freezeAllowedCommands().toArray(new String[0]);
            boolean permitted = matches(command, allowed) || matches(command, staffAllowed);
            if (!permitted) {
                event.setCancelled(true);
                plugin.messageService().send(player, "frozen-command-blocked");
                return;
            }
        }

        // Muted players lose chat-like commands (spec 9.5).
        List<Punishment> mutes = plugin.punishmentCache().activeMutes(uuid, now);
        if (!mutes.isEmpty()) {
            String ip = player.getAddress() == null ? null : player.getAddress().getHostAddress();
            if (ip != null) {
                mutes = new java.util.ArrayList<>(mutes);
                mutes.addAll(plugin.ipService().activeIpMutes(ip, now));
            }
            if (!mutes.isEmpty() && isBlockedWhileMuted(command, player)) {
                event.setCancelled(true);
                plugin.messageService().muteNotice(player, mutes.get(0));
                return;
            }
        }

        // Auto-mod command rate limiting (spec 11.7).
        if (!plugin.autoModService().commandAllowed(player)) {
            event.setCancelled(true);
            plugin.messageService().send(player, "automod-command-rate");
            return;
        }

        // Command spy (spec 8.5).
        broadcastSpy(player, raw, command);
    }

    private boolean isBlockedWhileMuted(String command, Player player) {
        for (String blocked : plugin.vsmConfig().blockedCommandsWhileMuted()) {
            if (matches(command, blocked)) {
                return true;
            }
        }
        return matches(command, SOCIAL_COMMANDS.toArray(new String[0]))
                && !plugin.permissionService().has(player, PermissionNodes.CHAT_BYPASS);
    }

    private boolean matches(String command, String[] allowed) {
        for (String candidate : allowed) {
            String normalised = candidate.toLowerCase(Locale.ROOT);
            if (command.equals(normalised)) {
                return true;
            }
        }
        return false;
    }

    private void broadcastSpy(Player player, String raw, String command) {
        String safeCommand = sanitiseCommand(raw);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!plugin.staffState().commandSpy(staff.getUniqueId())) {
                continue;
            }
            if (!plugin.permissionService().has(staff, PermissionNodes.COMMAND_SPY)) {
                continue;
            }
            plugin.messageService().send(staff, "commandspy",
                    "player", player.getName(),
                    "command", safeCommand,
                    "ip", plugin.ipService().display(
                            player.getAddress() == null ? null : player.getAddress().getHostAddress(),
                            staff));
        }
        // Watchlist players are monitored even when nobody explicitly enabled spy.
        if (plugin.watchService().watching(player.getUniqueId())) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (plugin.permissionService().has(staff, PermissionNodes.WATCH)) {
                    plugin.messageService().send(staff, "watchlist-command-alert",
                            "player", player.getName(),
                            "command", safeCommand);
                }
            }
        }
    }

    /** Strips anything that could look like a MiniMessage tag in spied output. */
    private static String sanitiseCommand(String command) {
        return command.replace('<', '‹').replace('>', '›');
    }
}