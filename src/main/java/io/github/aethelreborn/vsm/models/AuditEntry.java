package io.github.aethelreborn.vsm.models;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One audit line (spec 12). The hash chain makes edits and deletions detectable
 * ({@code hash = SHA-256(prevHash + canonical entry JSON)}, spec 13.4).
 */
public final class AuditEntry {

    private String id;
    private UUID staffUuid;
    private String staffName = "console";
    private String action = "";
    private UUID targetUuid;
    private String targetName;
    private String details = "";
    private ActionSource source = ActionSource.COMMAND;
    private long timestamp;
    private String prevHash = "";
    private String hash = "";

    public AuditEntry() {
    }

    public static AuditEntry of(ActionSource source, UUID staffUuid, String staffName, String action) {
        AuditEntry entry = new AuditEntry();
        entry.source = source;
        entry.staffUuid = staffUuid;
        entry.staffName = staffName == null ? "console" : staffName;
        entry.action = action;
        entry.timestamp = System.currentTimeMillis();
        return entry;
    }

    /**
     * Canonical, stable rendering used as hash input. Field order is fixed so the chain can be re-verified
     * at any time.
     */
    public String canonical() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", nullToEmpty(id));
        map.put("staff", nullToEmpty(staffName));
        map.put("staffUuid", staffUuid == null ? "" : staffUuid.toString());
        map.put("action", nullToEmpty(action));
        map.put("target", nullToEmpty(targetName));
        map.put("targetUuid", targetUuid == null ? "" : targetUuid.toString());
        map.put("details", nullToEmpty(details));
        map.put("source", source == null ? ActionSource.COMMAND.name() : source.name());
        map.put("timestamp", Long.toString(timestamp));
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            out.append(entry.getKey()).append('=').append(entry.getValue().replace("|", "\\|")).append('\n');
        }
        return out.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public String id() {
        return id;
    }

    public AuditEntry id(String id) {
        this.id = id;
        return this;
    }

    public UUID staffUuid() {
        return staffUuid;
    }

    public AuditEntry staffUuid(UUID staffUuid) {
        this.staffUuid = staffUuid;
        return this;
    }

    public String staffName() {
        return staffName;
    }

    public AuditEntry staffName(String staffName) {
        this.staffName = staffName;
        return this;
    }

    public String action() {
        return action;
    }

    public AuditEntry action(String action) {
        this.action = action;
        return this;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public AuditEntry targetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
        return this;
    }

    public String targetName() {
        return targetName;
    }

    public AuditEntry targetName(String targetName) {
        this.targetName = targetName;
        return this;
    }

    public String details() {
        return details;
    }

    public AuditEntry details(String details) {
        this.details = details;
        return this;
    }

    public ActionSource source() {
        return source;
    }

    public AuditEntry source(ActionSource source) {
        this.source = source;
        return this;
    }

    public long timestamp() {
        return timestamp;
    }

    public AuditEntry timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public String prevHash() {
        return prevHash;
    }

    public AuditEntry prevHash(String prevHash) {
        this.prevHash = prevHash;
        return this;
    }

    public String hash() {
        return hash;
    }

    public AuditEntry hash(String hash) {
        this.hash = hash;
        return this;
    }

    /** Human readable line used in the daily log file. */
    public String format() {
        return "[" + io.github.aethelreborn.vsm.util.TimeUtil.formatTimestamp(timestamp) + "] "
                + staffName + " " + action
                + (targetName == null || targetName.isBlank() ? "" : " -> " + targetName)
                + (details == null || details.isBlank() ? "" : " | " + details)
                + " | Source: " + (source == null ? ActionSource.COMMAND : source).name();
    }

    @Override
    public String toString() {
        return "AuditEntry{" + id + " " + action + "}";
    }
}