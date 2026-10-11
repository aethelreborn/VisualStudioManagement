package io.github.aethelreborn.vsm.models;

import java.util.UUID;

/** Login history row: {@code (uuid, ip, firstSeen, lastSeen, timesSeen)} (spec 5 / 11.8). */
public final class IpRecord {

    private UUID uuid;
    private String ip = "";
    private long firstSeen;
    private long lastSeen;
    private int timesSeen = 1;

    public IpRecord() {
    }

    public IpRecord(UUID uuid, String ip, long firstSeen, long lastSeen, int timesSeen) {
        this.uuid = uuid;
        this.ip = ip;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.timesSeen = timesSeen;
    }

    public UUID uuid() {
        return uuid;
    }

    public IpRecord uuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public String ip() {
        return ip;
    }

    public IpRecord ip(String ip) {
        this.ip = ip;
        return this;
    }

    public long firstSeen() {
        return firstSeen;
    }

    public IpRecord firstSeen(long firstSeen) {
        this.firstSeen = firstSeen;
        return this;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public IpRecord lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        return this;
    }

    public int timesSeen() {
        return timesSeen;
    }

    public IpRecord timesSeen(int timesSeen) {
        this.timesSeen = timesSeen;
        return this;
    }

    @Override
    public String toString() {
        return "IpRecord{" + uuid + " -> " + ip + "}";
    }
}