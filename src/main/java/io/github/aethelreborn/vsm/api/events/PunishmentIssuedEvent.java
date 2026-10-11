package io.github.aethelreborn.vsm.api.events;

import io.github.aethelreborn.vsm.models.Punishment;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a punishment has been created and before it is announced (spec 18.2).
 * Cancelling the event removes the punishment again.
 */
public class PunishmentIssuedEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Punishment punishment;
    private final UUID staffUuid;
    private final String staffName;
    private boolean cancelled;

    public PunishmentIssuedEvent(@NotNull Punishment punishment, UUID staffUuid, String staffName) {
        this.punishment = punishment;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public String getStaffName() {
        return staffName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}