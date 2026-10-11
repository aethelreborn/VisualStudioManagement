package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Dedicated auto-moderation listener (spec 11.7). The main chat listener already evaluates the rules;
 * this listener exists so the violation points can be recorded independently of the mute path and so
 * the action surface stays easy to extend.
 */
public final class AutoModListener implements Listener {

    private final VisualStudioManagement plugin;

    public AutoModListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.isEnabled() || !plugin.autoModConfig().enabled()) {
            return;
        }
        // The heavy checks run in ChatListener; here we only keep a cheap record for the debug screen.
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plain.length() > 512) {
            plugin.autoModService().onChat(event.getPlayer(), plain.substring(0, 512));
        }
    }
}