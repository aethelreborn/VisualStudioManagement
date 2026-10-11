package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import java.util.Iterator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * Hides vanished players from the server list (spec 11.3) and shows the maintenance MOTD.
 *
 * <p>The ping event iterates over the displayed players, so vanished staff are removed from that iterator
 * and therefore from both the sample and the reported player count.
 */
public final class ServerPingListener implements Listener {

    private final VisualStudioManagement plugin;

    public ServerPingListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent event) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (plugin.lockdownService().maintenance()) {
            event.motd(plugin.messageService().screen("motd-maintenance"));
        }
        if (!plugin.vsmConfig().vanishHideFromTab()) {
            return;
        }
        Iterator<Player> iterator = event.iterator();
        while (iterator.hasNext()) {
            Player listed = iterator.next();
            if (plugin.vanishService().isVanished(listed.getUniqueId())) {
                iterator.remove();
            }
        }
    }
}