package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Quit handling (spec 4.3, 11.2): save locations, restore state and release YouTuber freezes. */
public final class QuitListener implements Listener {

    private final VisualStudioManagement plugin;

    public QuitListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        var uuid = player.getUniqueId();

        if (plugin.vanishService().isVanished(player)) {
            event.quitMessage(plugin.vsmConfig().vanishSuppressJoinLeave() ? null : event.quitMessage());
        }

        plugin.teleportService().saveLogout(player);
        plugin.teleportService().clearHistory(uuid);
        plugin.teleportService().stopFollow(player);
        plugin.autoModService().forget(uuid);
        plugin.chatControlService().forget(uuid);
        plugin.securityService().forget(uuid);
        plugin.staffState().clear(uuid);

        if (plugin.staffModeService().isActive(uuid)) {
            plugin.staffModeService().restore(player, null, player.getName());
        }
        plugin.freezeService().onQuit(player);
        plugin.vanishService().onQuit(player);
        plugin.youTuberService().onQuit(player);
        plugin.staffService().updateLastSeen(uuid, player.getName(), System.currentTimeMillis());
    }
}