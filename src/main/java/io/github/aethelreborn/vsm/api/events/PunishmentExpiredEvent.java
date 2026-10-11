package io.github.aethelreborn.vsm.api.events;

import io.github.aethelreborn.vsm.models.Punishment;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a temporary punishment reaches its expiry and is deactivated. */
public class PunishmentExpiredEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Punishment punishment;

    public PunishmentExpiredEvent(@NotNull Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}