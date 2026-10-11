package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Chat enforcement (spec 9.5). Mutes, shadow mutes, global chat lock, slowmode and auto-moderation all
 * run here, reading only the in-memory cache.
 */
public final class ChatListener implements Listener {

    private final VisualStudioManagement plugin;

    public ChatListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isEnabled()) {
            return;
        }
        var uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        // 1. Mute by UUID and by IP (spec 9.5).
        List<Punishment> mutes = new java.util.ArrayList<>(
                plugin.punishmentCache().activeMutes(uuid, now));
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        if (ip != null) {
            mutes.addAll(plugin.ipService().activeIpMutes(ip, now));
        }

        for (Punishment mute : mutes) {
            if (mute.type() == PunishmentType.SHADOW_MUTE) {
                applyShadowMute(event, mute);
                continue;
            }
            event.setCancelled(true);
            plugin.messageService().muteNotice(player, mute);
            return;
        }

        // 2. Global chat lock.
        if (plugin.chatControlService().chatLocked()
                && !plugin.chatControlService().canBypass(player)) {
            event.setCancelled(true);
            plugin.messageService().send(player, "chat-locked");
            return;
        }

        // 3. Slowmode.
        long remaining = plugin.chatControlService().slowModeRemaining(player);
        if (remaining > 0L) {
            event.setCancelled(true);
            plugin.messageService().send(player, "slowmode-wait",
                    "seconds", io.github.aethelreborn.vsm.util.TimeParser.format(remaining));
            return;
        }
        plugin.chatControlService().recordChat(uuid);

        // 4. Staff chat mode routes normal chat into staff chat.
        if (plugin.staffState().staffChatMode(uuid)) {
            event.setCancelled(true);
            plugin.messageService().staffChat(player, plain);
            return;
        }

        // 5. Auto-moderation (spec 11.7).
        var result = plugin.autoModService().onChat(player, plain);
        if (result.replacement() != null) {
            event.message(Component.text(result.replacement()));
        }
        if (result.cancelled()) {
            event.setCancelled(true);
        }
        if (result.alertStaff()) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (plugin.permissionService().has(staff, PermissionNodes.ALERTS)) {
                    plugin.messageService().send(staff, "automod-notice",
                            "player", player.getName(),
                            "violation", result.violation() == null ? "automod" : result.violation());
                }
            }
        }
    }

    /** Shadow mute keeps the message for the sender but removes every other viewer. */
    private void applyShadowMute(AsyncChatEvent event, Punishment mute) {
        Set<Audience> viewers = event.viewers();
        if (viewers == null) {
            return;
        }
        Set<Audience> keep = new HashSet<>();
        keep.add(event.getPlayer());
        for (Audience viewer : viewers) {
            if (viewer instanceof Player player
                    && plugin.permissionService().has(player, PermissionNodes.SHADOW_MUTE_SEE)) {
                keep.add(viewer);
            }
        }
        viewers.retainAll(keep);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (plugin.permissionService().has(staff, PermissionNodes.ALERTS)) {
                plugin.messageService().send(staff, "shadowmute-notice",
                        "player", event.getPlayer().getName(),
                        "reason", mute.reason() == null ? "" : mute.reason());
            }
        }
    }
}