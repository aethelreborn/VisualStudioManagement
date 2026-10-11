package io.github.aethelreborn.vsm.api.events;

import io.github.aethelreborn.vsm.models.Report;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after a report has been stored. */
public class ReportCreatedEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Report report;

    public ReportCreatedEvent(@NotNull Report report) {
        this.report = report;
    }

    public Report getReport() {
        return report;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}