package io.github.aethelreborn.vsm.models;

import java.util.UUID;

/**
 * A whitelisted staff or YouTuber account (spec 5). UUID is the identity; the name is informational only.
 */
public final class StaffMember {

    private UUID uuid;
    private String name;
    private String rankId = "helper";
    private UUID addedBy;
    private String addedByName = "console";
    private long addedAt;
    private long lastSeen;
    private String pinHash;
    private String lockedIpHash;
    private boolean staffChatToggle;
    private boolean alertsEnabled = true;
    private boolean commandSpy;
    private boolean socialSpy;
    private long playtimeMillis;

    public StaffMember() {
    }

    public StaffMember(UUID uuid, String name, String rankId) {
        this.uuid = uuid;
        this.name = name;
        this.rankId = rankId;
    }

    public UUID uuid() {
        return uuid;
    }

    public StaffMember uuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public String name() {
        return name;
    }

    public StaffMember name(String name) {
        this.name = name;
        return this;
    }

    public String rankId() {
        return rankId;
    }

    public StaffMember rankId(String rankId) {
        this.rankId = rankId;
        return this;
    }

    public UUID addedBy() {
        return addedBy;
    }

    public StaffMember addedBy(UUID addedBy) {
        this.addedBy = addedBy;
        return this;
    }

    public String addedByName() {
        return addedByName;
    }

    public StaffMember addedByName(String addedByName) {
        this.addedByName = addedByName;
        return this;
    }

    public long addedAt() {
        return addedAt;
    }

    public StaffMember addedAt(long addedAt) {
        this.addedAt = addedAt;
        return this;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public StaffMember lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        return this;
    }

    public String pinHash() {
        return pinHash;
    }

    public StaffMember pinHash(String pinHash) {
        this.pinHash = pinHash;
        return this;
    }

    public String lockedIpHash() {
        return lockedIpHash;
    }

    public StaffMember lockedIpHash(String lockedIpHash) {
        this.lockedIpHash = lockedIpHash;
        return this;
    }

    public boolean staffChatToggle() {
        return staffChatToggle;
    }

    public StaffMember staffChatToggle(boolean staffChatToggle) {
        this.staffChatToggle = staffChatToggle;
        return this;
    }

    public boolean alertsEnabled() {
        return alertsEnabled;
    }

    public StaffMember alertsEnabled(boolean alertsEnabled) {
        this.alertsEnabled = alertsEnabled;
        return this;
    }

    public boolean commandSpy() {
        return commandSpy;
    }

    public StaffMember commandSpy(boolean commandSpy) {
        this.commandSpy = commandSpy;
        return this;
    }

    public boolean socialSpy() {
        return socialSpy;
    }

    public StaffMember socialSpy(boolean socialSpy) {
        this.socialSpy = socialSpy;
        return this;
    }

    public long playtimeMillis() {
        return playtimeMillis;
    }

    public StaffMember playtimeMillis(long playtimeMillis) {
        this.playtimeMillis = playtimeMillis;
        return this;
    }

    @Override
    public String toString() {
        return "StaffMember{" + name + " (" + rankId + ")}";
    }
}