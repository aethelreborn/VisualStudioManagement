package io.github.aethelreborn.vsm.models;

import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.Objects;
import java.util.UUID;

/**
 * A single punishment record (spec 5). Mutable by design: {@code /vsm punishment modify} edits an active
 * record in place rather than issuing a new one.
 */
public final class Punishment {

    private String id = "";
    private UUID targetUuid;
    private String targetName;
    private String targetIp;
    private UUID staffUuid;
    private String staffName = "console";
    private PunishmentType type = PunishmentType.WARN;
    private String reason = "";
    private long issuedAt;
    private long expiresAt = TimeParser.PERMANENT;
    private boolean active = true;
    private UUID removedBy;
    private String removedByName;
    private long removedAt;
    private String removalReason;
    private String server = "global";
    private boolean silent;
    private String evidence;
    private String templateId;
    private int ladderStep = -1;
    private String parentId;
    private String scope = "";

    public Punishment() {
    }

    public static Punishment of(PunishmentType type, UUID targetUuid, String targetName, String reason) {
        Punishment p = new Punishment();
        p.type = type;
        p.targetUuid = targetUuid;
        p.targetName = targetName;
        p.reason = reason;
        p.issuedAt = System.currentTimeMillis();
        return p;
    }

    /** Lazily evaluates expiry so a player is never blocked past their expiry (spec 9.4). */
    public boolean isActiveNow(long now) {
        if (!active) {
            return false;
        }
        return expiresAt == TimeParser.PERMANENT || expiresAt > now;
    }

    public boolean isPermanent() {
        return expiresAt == TimeParser.PERMANENT;
    }

    public long remainingMillis(long now) {
        if (isPermanent()) {
            return TimeParser.PERMANENT;
        }
        return Math.max(0L, expiresAt - now);
    }

    /** Removes this punishment in place. */
    public void remove(UUID by, String byName, String removalReason, long when) {
        this.active = false;
        this.removedBy = by;
        this.removedByName = byName;
        this.removalReason = removalReason;
        this.removedAt = when;
    }

    public String id() {
        return id;
    }

    public Punishment id(String id) {
        this.id = id;
        return this;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public Punishment targetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
        return this;
    }

    public String targetName() {
        return targetName;
    }

    public Punishment targetName(String targetName) {
        this.targetName = targetName;
        return this;
    }

    public String targetIp() {
        return targetIp;
    }

    public Punishment targetIp(String targetIp) {
        this.targetIp = targetIp;
        return this;
    }

    public UUID staffUuid() {
        return staffUuid;
    }

    public Punishment staffUuid(UUID staffUuid) {
        this.staffUuid = staffUuid;
        return this;
    }

    public String staffName() {
        return staffName;
    }

    public Punishment staffName(String staffName) {
        this.staffName = staffName;
        return this;
    }

    public PunishmentType type() {
        return type;
    }

    public Punishment type(PunishmentType type) {
        this.type = type;
        return this;
    }

    public String reason() {
        return reason;
    }

    public Punishment reason(String reason) {
        this.reason = reason;
        return this;
    }

    public long issuedAt() {
        return issuedAt;
    }

    public Punishment issuedAt(long issuedAt) {
        this.issuedAt = issuedAt;
        return this;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public Punishment expiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public boolean active() {
        return active;
    }

    public Punishment active(boolean active) {
        this.active = active;
        return this;
    }

    public UUID removedBy() {
        return removedBy;
    }

    public String removedByName() {
        return removedByName;
    }

    public long removedAt() {
        return removedAt;
    }

    public Punishment removedAt(long removedAt) {
        this.removedAt = removedAt;
        return this;
    }

    public String removalReason() {
        return removalReason;
    }

    public Punishment removalReason(String removalReason) {
        this.removalReason = removalReason;
        return this;
    }

    public String server() {
        return server;
    }

    public Punishment server(String server) {
        this.server = server;
        return this;
    }

    public boolean silent() {
        return silent;
    }

    public Punishment silent(boolean silent) {
        this.silent = silent;
        return this;
    }

    public String evidence() {
        return evidence;
    }

    public Punishment evidence(String evidence) {
        this.evidence = evidence;
        return this;
    }

    public String templateId() {
        return templateId;
    }

    public Punishment templateId(String templateId) {
        this.templateId = templateId;
        return this;
    }

    public int ladderStep() {
        return ladderStep;
    }

    public Punishment ladderStep(int ladderStep) {
        this.ladderStep = ladderStep;
        return this;
    }

    public String parentId() {
        return parentId;
    }

    public Punishment parentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    /** Free-form marker such as {@code ytb:<uuid>} used by YouTuber freezes. */
    public String scope() {
        return scope;
    }

    public Punishment scope(String scope) {
        this.scope = scope == null ? "" : scope;
        return this;
    }

    /** @return {@code true} when this record was produced by the YouTuber role, not moderation. */
    public boolean isYouTuberScope() {
        return scope != null && scope.startsWith("ytb:");
    }

    public String displayTarget() {
        if (targetName != null && !targetName.isBlank()) {
            return targetName;
        }
        return targetUuid != null ? targetUuid.toString() : (targetIp != null ? targetIp : "unknown");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Punishment other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Punishment{" + id + " " + type + " -> " + displayTarget() + " active=" + active + "}";
    }
}