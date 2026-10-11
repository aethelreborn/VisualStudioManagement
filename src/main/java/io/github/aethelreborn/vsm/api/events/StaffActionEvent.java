package io.github.aethelreborn.vsm.api.events;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Generic hook fired for any staff action routed through {@code ActionService}. */
public class StaffActionEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final String action;
    private final UUID actorUuid;
    private final String actorName;
    private final UUID targetUuid;
    private final String details;

    public StaffActionEvent(@NotNull String action, @Nullable UUID actorUuid, @NotNull String actorName,
                            @Nullable UUID targetUuid, @Nullable String details) {
        this.action = action;
        this.actorUuid = actorUuid;
        this.actorName = actorName;
        this.targetUuid = targetUuid;
        this.details = details;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorUuid() {
        return actorUuid;
    }

    public String getActorName() {
        return actorName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}