package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PlayerNote;
import io.github.aethelreborn.vsm.models.Report;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Join handling (spec 9.5 join messages, 11.3 auto vanish, 11.5 staff mode, 11.8 alt alerts).
 */
public final class JoinListener implements Listener {

    private final VisualStudioManagement plugin;

    public JoinListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        plugin.staffService().updateLastSeen(uuid, player.getName(), now);
        plugin.securityService().touchSession(uuid);
        plugin.storage().saveNameHistory(uuid, player.getName(), now);

        // Pending notices for the player (warnings, report outcomes, freeze re-apply).
        showPendingNotices(player, now);

        // Staff facing notices.
        if (plugin.permissionService().isStaffRank(uuid)) {
            plugin.noteService().showPinnedNotes(player);
        }
        plugin.watchService().alertJoin(player);

        // Alt detection (spec 11.8).
        String ip = player.getAddress() == null ? null : player.getAddress().getHostAddress();
        if (ip != null && plugin.vsmConfig().altsAlertOnBannedLink()) {
            plugin.scheduler().async(() -> {
                if (!plugin.ipService().linkedToBannedAccount(uuid, ip)) {
                    return;
                }
                plugin.scheduler().sync(() -> {
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (plugin.permissionService().has(staff, PermissionNodes.ALTS)) {
                            plugin.messageService().send(staff, "alt-banned-link-alert",
                                    "player", player.getName(),
                                    "ip", plugin.ipService().display(ip, staff));
                        }
                    }
                    String action = plugin.vsmConfig().altsAutoAction();
                    if ("KICK".equalsIgnoreCase(action)) {
                        player.kick(plugin.messageService().screen("screen-kick"));
                    }
                });
            });
        }

        plugin.freezeService().onJoin(player);
        plugin.vanishService().onJoin(player);
        plugin.vanishService().refreshFor(player);
        plugin.youTuberService().onJoin(player);

        if (plugin.vsmConfig().vanishSuppressJoinLeave() && plugin.vanishService().isVanished(player)) {
            event.joinMessage(null);
        }
        if (plugin.lockdownService().maintenance() && !plugin.lockdownService().mayJoin(player)) {
            player.kick(plugin.messageService().screen("screen-maintenance"));
        }
    }

    private void showPendingNotices(Player player, long now) {
        List<Punishment> warnings = plugin.punishmentCache().activeWarnings(player.getUniqueId(), now);
        if (!warnings.isEmpty()) {
            plugin.messageService().send(player, "join-warnings", "count", Integer.toString(warnings.size()));
            for (Punishment warning : warnings) {
                plugin.messageService().send(player, "join-warning-detail",
                        "reason", warning.reason() == null ? "" : warning.reason(),
                        "staff", warning.staffName() == null ? "Console" : warning.staffName(),
                        "id", warning.id());
            }
        }
        plugin.scheduler().async(() -> {
            List<Report> reports = plugin.storage().reportsAgainst(player.getUniqueId()).join();
            List<Report> resolved = reports.stream().filter(r -> !r.isOpen()).toList();
            if (resolved.isEmpty()) {
                return;
            }
            plugin.scheduler().sync(() -> {
                for (Report report : resolved) {
                    plugin.messageService().send(player, "report-outcome-notice",
                            "id", Long.toString(report.id()),
                            "target", report.targetName(),
                            "status", report.status().name());
                }
            });
            List<PlayerNote> notes = plugin.noteService().pinned(player.getUniqueId());
            if (!notes.isEmpty()) {
                plugin.scheduler().sync(() -> plugin.messageService().send(player, "join-notes",
                        "count", Integer.toString(notes.size())));
            }
        });
    }
}