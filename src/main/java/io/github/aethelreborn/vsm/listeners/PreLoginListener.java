package io.github.aethelreborn.vsm.listeners;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.Messages;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import java.util.List;
import java.util.Optional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Login enforcement (spec 9.5). Runs entirely off the main thread and reads only the in-memory cache,
 * so a slow database can never delay a login.
 */
public final class PreLoginListener implements Listener {

    private final VisualStudioManagement plugin;

    public PreLoginListener(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        var uuid = event.getUniqueId();
        var rawIp = event.getAddress() == null ? null : event.getAddress().getAddress().getHostAddress();

        // 5. Record the login IP (after the checks so a rejected player is not tracked twice).
        String storedIp = plugin.ipService().store(rawIp);

        // 2. Active account ban by UUID.
        Optional<Punishment> accountBan = firstMatch(plugin.punishmentCache().activeFor(uuid, now),
                type -> type.isBan() && !type.isIp());
        if (accountBan.isPresent()) {
            deny(event, accountBan.get());
            return;
        }

        // 3. Active IP ban by exact match then CIDR, unless the UUID is allowlisted.
        if (storedIp != null && !plugin.punishmentCache().hasIpAllow(uuid)) {
            Optional<Punishment> ipBan = plugin.ipService().activeIpBan(rawIp, uuid, now);
            if (ipBan.isPresent()) {
                deny(event, ipBan.get());
                return;
            }
        }

        // 4. Active name ban.
        Optional<Punishment> nameBan = firstMatch(plugin.punishmentCache().activeFor(uuid, now),
                type -> type == PunishmentType.NAME_BAN);
        if (nameBan.isPresent()) {
            deny(event, nameBan.get());
            return;
        }

        plugin.ipService().recordLogin(uuid, rawIp);

        // 1. Maintenance mode / lockdown is handled after the bans so the correct screen is shown.
        if (plugin.lockdownService().maintenance()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messageService().screen("screen-maintenance"));
            return;
        }
        if (plugin.lockdownService().lockdown()) {
            boolean returning = plugin.vsmConfig().lockdownAllowReturningPlayers();
            if (!returning) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messageService().screen("screen-lockdown"));
            }
        }
    }

    private Optional<Punishment> firstMatch(List<Punishment> punishments, java.util.function.Predicate<PunishmentType> filter) {
        for (Punishment punishment : punishments) {
            if (filter.test(punishment.type())) {
                return Optional.of(punishment);
            }
        }
        return Optional.empty();
    }

    private void deny(AsyncPlayerPreLoginEvent event, Punishment punishment) {
        // IP ban screens never reveal the address (spec 9.5).
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.messageService().banScreen(punishment));
    }

    /** Exposed so the join listener can reuse the same screen. */
    public Messages messages() {
        return plugin.messages();
    }
}