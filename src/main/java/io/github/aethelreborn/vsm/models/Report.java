package io.github.aethelreborn.vsm.models;

import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.List;
import java.util.UUID;

/** A player report (spec 10). */
public final class Report {

    public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        REJECTED,
        ESCALATED
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH
    }

    private long id;
    private UUID reporterUuid;
    private String reporterName = "";
    private UUID targetUuid;
    private String targetName = "";
    private String reason = "";
    private long createdAt;
    private Status status = Status.OPEN;
    private Priority priority = Priority.NORMAL;
    private String assignedStaff;
    private String note;
    private String evidence;
    private long claimedAt;
    private long closedAt;

    public long id() {
        return id;
    }

    public Report id(long id) {
        this.id = id;
        return this;
    }

    public UUID reporterUuid() {
        return reporterUuid;
    }

    public Report reporterUuid(UUID reporterUuid) {
        this.reporterUuid = reporterUuid;
        return this;
    }

    public String reporterName() {
        return reporterName;
    }

    public Report reporterName(String reporterName) {
        this.reporterName = reporterName;
        return this;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public Report targetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
        return this;
    }

    public String targetName() {
        return targetName;
    }

    public Report targetName(String targetName) {
        this.targetName = targetName;
        return this;
    }

    public String reason() {
        return reason;
    }

    public Report reason(String reason) {
        this.reason = reason;
        return this;
    }

    public long createdAt() {
        return createdAt;
    }

    public Report createdAt(long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Status status() {
        return status;
    }

    public Report status(Status status) {
        this.status = status;
        return this;
    }

    public Priority priority() {
        return priority;
    }

    public Report priority(Priority priority) {
        this.priority = priority;
        return this;
    }

    public String assignedStaff() {
        return assignedStaff;
    }

    public Report assignedStaff(String assignedStaff) {
        this.assignedStaff = assignedStaff;
        return this;
    }

    public String note() {
        return note;
    }

    public Report note(String note) {
        this.note = note;
        return this;
    }

    public String evidence() {
        return evidence;
    }

    public Report evidence(String evidence) {
        this.evidence = evidence;
        return this;
    }

    public long claimedAt() {
        return claimedAt;
    }

    public Report claimedAt(long claimedAt) {
        this.claimedAt = claimedAt;
        return this;
    }

    public long closedAt() {
        return closedAt;
    }

    public Report closedAt(long closedAt) {
        this.closedAt = closedAt;
        return this;
    }

    public boolean isOpen() {
        return status == Status.OPEN || status == Status.IN_PROGRESS || status == Status.ESCALATED;
    }

    /** Filters this report by a {@code /history --type} style filter, or {@code null} for no filter. */
    public boolean matches(List<PunishmentType> types) {
        return types == null || types.isEmpty();
    }

    public String age(long now) {
        return TimeParser.format(Math.max(0L, now - createdAt));
    }

    @Override
    public String toString() {
        return "Report{" + id + " " + status + " " + targetName + "}";
    }
}