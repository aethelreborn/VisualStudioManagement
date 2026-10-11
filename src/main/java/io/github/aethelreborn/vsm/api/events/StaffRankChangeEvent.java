package io.github.aethelreborn.vsm.api.events;

import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired before a staff rank change is applied; cancelling it keeps the previous rank. */
public class StaffRankChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final UUID targetUuid;
    private final @Nullable String oldRank;
    private final String newRank;
    private final UUID actorUuid;
    private boolean cancelled;

    public StaffRankChangeEvent(@NotNull UUID targetUuid, @Nullable String oldRank,
                                @NotNull String newRank, @Nullable UUID actorUuid) {
        this.targetUuid = targetUuid;
        this.oldRank = oldRank;
        this.newRank = newRank;
        this.actorUuid = actorUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public @Nullable String getOldRank() {
        return oldRank;
    }

    public String getNewRank() {
        return newRank;
    }

    public UUID getActorUuid() {
        return actorUuid;
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