package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Global chat controls: {@code /clearchat}, {@code /mutechat} and {@code /slowmode} (spec 8.5). */
public final class ChatControlService {

    private final VsmConfig config;
    private final PermissionService permissions;
    private final AuditService audit;
    private final MessageService messages;
    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();

    private volatile boolean chatLocked;
    private volatile long slowModeSeconds;
    private volatile String chatLockedBy = "system";

    public ChatControlService(VsmConfig config, PermissionService permissions, AuditService audit,
                              MessageService messages) {
        this.config = config;
        this.permissions = permissions;
        this.audit = audit;
        this.messages = messages;
    }

    public boolean chatLocked() {
        return chatLocked;
    }

    public long slowModeSeconds() {
        return slowModeSeconds;
    }

    /** {@code true} when the sender may talk despite mutechat or slowmode. */
    public boolean canBypass(Player player) {
        return permissions.has(player, PermissionNodes.CHAT_BYPASS);
    }

    public boolean toggleChatLock(Player actor, UUID actorUuid, String actorName) {
        chatLocked = !chatLocked;
        chatLockedBy = actorUuid == null ? "console" : actorUuid.toString();
        audit.log(ActionSource.COMMAND, actorUuid, actorName,
                chatLocked ? "MUTECHAT_ON" : "MUTECHAT_OFF", null, null, "");
        broadcast("mutechat-toggled", actorName);
        return chatLocked;
    }

    public boolean setSlowMode(Player actor, UUID actorUuid, String actorName, long seconds) {
        slowModeSeconds = Math.max(0L, seconds);
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "SLOWMODE_SET", null, null,
                slowModeSeconds + "s");
        broadcast("slowmode-set", actorName);
        return true;
    }

    /** Clears chat for everyone who cannot bypass it (spec 8.5). */
    public void clearChat(UUID actorUuid, String actorName) {
        int cleared = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canBypass(player)) {
                continue;
            }
            for (int i = 0; i < 100; i++) {
                player.sendMessage(" ");
            }
            cleared++;
        }
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "CLEARCHAT", null, null,
                cleared + " player(s)");
        broadcast("chat-cleared", actorName);
    }

    /** @return remaining slowmode milliseconds, or {@code 0} when the player may speak. */
    public long slowModeRemaining(Player player) {
        if (slowModeSeconds <= 0L || canBypass(player)) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last == null) {
            lastMessage.put(player.getUniqueId(), now);
            return 0L;
        }
        long elapsed = now - last;
        long required = slowModeSeconds * 1000L;
        if (elapsed >= required) {
            lastMessage.put(player.getUniqueId(), now);
            return 0L;
        }
        return required - elapsed;
    }

    public void recordChat(UUID uuid) {
        lastMessage.put(uuid, System.currentTimeMillis());
    }

    public void forget(UUID uuid) {
        lastMessage.remove(uuid);
    }

    /** Sends a chat-control notice to everyone who cannot bypass it. */
    public void broadcast(String key, String staffName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canBypass(player)) {
                continue;
            }
            messages.send(player, key, "staff", staffName == null ? "Console" : staffName);
        }
    }

    public void reset() {
        lastMessage.clear();
        chatLocked = false;
        slowModeSeconds = 0L;
    }

    public String lockedBy() {
        return chatLockedBy == null ? "system" : chatLockedBy;
    }

    public VsmConfig config() {
        return config;
    }
}