package io.github.aethelreborn.vsm.models;

import java.util.UUID;

/** A saved position, used by {@code /back} and {@code /tpoffline} (spec 5). */
public final class SavedLocation {

    public static final String KIND_BACK = "BACK";
    public static final String KIND_LOGOUT = "LOGOUT";

    private UUID player;
    private String kind = KIND_BACK;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private long savedAt;
    private String cause = "";

    public SavedLocation() {
    }

    public SavedLocation(UUID player, String kind, String world, double x, double y, double z,
                        float yaw, float pitch, long savedAt, String cause) {
        this.player = player;
        this.kind = kind;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.savedAt = savedAt;
        this.cause = cause;
    }

    public UUID player() {
        return player;
    }

    public SavedLocation player(UUID player) {
        this.player = player;
        return this;
    }

    public String kind() {
        return kind;
    }

    public SavedLocation kind(String kind) {
        this.kind = kind;
        return this;
    }

    public String world() {
        return world;
    }

    public SavedLocation world(String world) {
        this.world = world;
        return this;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public long savedAt() {
        return savedAt;
    }

    public SavedLocation savedAt(long savedAt) {
        this.savedAt = savedAt;
        return this;
    }

    public String cause() {
        return cause;
    }

    public SavedLocation cause(String cause) {
        this.cause = cause;
        return this;
    }

    @Override
    public String toString() {
        return "SavedLocation{" + kind + " " + world + " " + x + "," + y + "," + z + "}";
    }
}