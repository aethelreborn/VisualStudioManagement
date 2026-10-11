package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.models.Report;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.util.TextUtil;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Report creation and lifecycle (spec 10): cooldowns, per-player limits, de-duplication, priority
 * escalation and staff notification.
 */
public final class ReportService {

    /** Result of a report attempt, so commands can pick the right message. */
    public enum Outcome {
        CREATED,
        MERGED,
        SELF_REPORT,
        COOLDOWN,
        LIMIT,
        TARGET_IS_STAFF,
        INVALID
    }

    public record Result(Outcome outcome, Report report, long cooldownMillis) {
    }

    private final StorageProvider storage;
    private final VsmConfig config;
    private final MessageService messages;
    private final AuditService audit;
    private final PermissionService permissions;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();

    public ReportService(StorageProvider storage, VsmConfig config, MessageService messages,
                         AuditService audit, PermissionService permissions) {
        this.storage = storage;
        this.config = config;
        this.messages = messages;
        this.audit = audit;
        this.permissions = permissions;
    }

    /** Validates and stores a report. */
    public Result create(CommandSender reporter, UUID reporterUuid, String reporterName,
                         UUID targetUuid, String targetName, String reason) {
        if (reporterUuid != null && reporterUuid.equals(targetUuid)) {
            return new Result(Outcome.SELF_REPORT, null, 0L);
        }
        if (!config.allowReportingStaff() && permissions.isStaffRank(targetUuid)) {
            return new Result(Outcome.TARGET_IS_STAFF, null, 0L);
        }
        long now = System.currentTimeMillis();
        Long previous = lastReport.get(reporterUuid);
        long cooldown = config.reportCooldownSeconds() * 1000L;
        if (previous != null && now - previous < cooldown) {
            return new Result(Outcome.COOLDOWN, null, cooldown - (now - previous));
        }

        // De-duplicate: same reporter + target inside the merge window bumps the existing report.
        long mergeWindow = config.mergeDuplicatesMinutes() * 60_000L;
        if (mergeWindow > 0L) {
            List<Report> existing = storage.reportsAgainst(targetUuid).join();
            for (Report report : existing) {
                boolean sameReporter = reporterUuid != null && reporterUuid.equals(report.reporterUuid());
                if (sameReporter && now - report.createdAt() < mergeWindow) {
                    lastReport.put(reporterUuid, now);
                    return new Result(Outcome.MERGED, report, 0L);
                }
            }
        }

        int open = 0;
        for (Report report : storage.reportsAgainst(reporterUuid).join()) {
            if (report.isOpen()) {
                open++;
            }
        }
        if (open >= config.maxOpenReportsPerPlayer()) {
            return new Result(Outcome.LIMIT, null, 0L);
        }

        Report report = new Report();
        report.reporterUuid(reporterUuid);
        report.reporterName(reporterName);
        report.targetUuid(targetUuid);
        report.targetName(targetName);
        report.reason(TextUtil.sanitise(reason, 256));
        report.createdAt(now);
        report.status(Report.Status.OPEN);
        report.priority(Report.Priority.NORMAL);
        Report stored = storage.createReport(report);
        lastReport.put(reporterUuid, now);

        // Escalate priority when several reporters target the same player inside the window.
        long escalateWindow = config.escalateWindowMinutes() * 60_000L;
        int distinctReporters = 0;
        java.util.Set<UUID> reporters = new java.util.HashSet<>();
        for (Report candidate : storage.reportsAgainst(targetUuid).join()) {
            if (now - candidate.createdAt() <= escalateWindow && candidate.reporterUuid() != null) {
                reporters.add(candidate.reporterUuid());
            }
        }
        distinctReporters = reporters.size();
        if (distinctReporters >= config.escalateThreshold()) {
            stored.priority(Report.Priority.HIGH);
            stored.status(Report.Status.ESCALATED);
            storage.saveReport(stored);
        }

        audit.log(ActionSource.COMMAND, reporterUuid, reporterName, "REPORT_CREATED",
                targetUuid, targetName, "id=" + stored.id() + " priority=" + stored.priority().name());
        Bukkit.getPluginManager().callEvent(new io.github.aethelreborn.vsm.api.events.ReportCreatedEvent(stored));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            messages.reportAlert(staff, stored);
        }
        return new Result(Outcome.CREATED, stored, 0L);
    }

    public boolean claim(CommandSender actor, UUID actorUuid, long id) {
        Report report = storage.reportById(id).join().orElse(null);
        if (report == null) {
            return false;
        }
        if (!permissions.has(actor, io.github.aethelreborn.vsm.config.PermissionNodes.REPORTS_CLAIM)) {
            return false;
        }
        report.status(Report.Status.IN_PROGRESS);
        report.assignedStaff(actorUuid == null ? "console" : actorUuid.toString());
        report.claimedAt(System.currentTimeMillis());
        storage.saveReport(report);
        audit.log(ActionSource.COMMAND, actorUuid, actorName(actor), "REPORT_CLAIM", report.targetUuid(),
                report.targetName(), "id=" + id);
        return true;
    }

    public boolean close(CommandSender actor, UUID actorUuid, long id, String note, Report.Status status) {
        Report report = storage.reportById(id).join().orElse(null);
        if (report == null) {
            return false;
        }
        if (!permissions.has(actor, io.github.aethelreborn.vsm.config.PermissionNodes.REPORTS_CLOSE)) {
            return false;
        }
        report.status(status);
        report.note(TextUtil.sanitise(note, 256));
        report.closedAt(System.currentTimeMillis());
        storage.saveReport(report);
        audit.log(ActionSource.COMMAND, actorUuid, actorName(actor), "REPORT_CLOSE", report.targetUuid(),
                report.targetName(), "id=" + id + " status=" + status.name());
        Player reporter = report.reporterUuid() == null ? null : Bukkit.getPlayer(report.reporterUuid());
        if (reporter != null) {
            messages.send(reporter, "report-closed-notice", "id", Long.toString(id), "status", status.name());
        }
        return true;
    }

    public List<Report> list(String statusFilter, UUID assignedTo, int page, int size) {
        return storage.reports(statusFilter, assignedTo, page, size).join();
    }

    public int count(String statusFilter, UUID assignedTo) {
        Integer value = storage.countReports(statusFilter, assignedTo).join();
        return value == null ? 0 : value;
    }

    public Punishment noPunishment() {
        return null;
    }

    public PunishmentType placeholderType() {
        return PunishmentType.WARN;
    }

    private static String actorName(CommandSender actor) {
        return actor == null ? "console" : actor.getName();
    }
}