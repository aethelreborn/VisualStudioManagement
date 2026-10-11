package io.github.aethelreborn.vsm.models;

import java.util.UUID;

/** A player placed on the staff watchlist by {@code /vsm watch}. */
public final class WatchEntry {

    private UUID uuid;
    private String name = "";
    private String addedBy = "console";
    private String reason = "";
    private long addedAt;

    public WatchEntry() {
    }

    public WatchEntry(UUID uuid, String name, String addedBy, String reason, long addedAt) {
        this.uuid = uuid;
        this.name = name;
        this.addedBy = addedBy;
        this.reason = reason;
        this.addedAt = addedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public WatchEntry uuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public String name() {
        return name;
    }

    public WatchEntry name(String name) {
        this.name = name;
        return this;
    }

    public String addedBy() {
        return addedBy;
    }

    public WatchEntry addedBy(String addedBy) {
        this.addedBy = addedBy;
        return this;
    }

    public String reason() {
        return reason;
    }

    public WatchEntry reason(String reason) {
        this.reason = reason;
        return this;
    }

    public long addedAt() {
        return addedAt;
    }

    public WatchEntry addedAt(long addedAt) {
        this.addedAt = addedAt;
        return this;
    }

    @Override
    public String toString() {
        return "WatchEntry{" + name + "}";
    }
}