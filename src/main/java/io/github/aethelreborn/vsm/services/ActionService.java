package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.api.events.PunishmentIssuedEvent;
import io.github.aethelreborn.vsm.api.events.PunishmentRemovedEvent;
import io.github.aethelreborn.vsm.api.events.StaffActionEvent;
import io.github.aethelreborn.vsm.api.events.StaffRankChangeEvent;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.models.StaffMember;
import io.github.aethelreborn.vsm.storage.cache.StaffCache;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The single choke point for every staff action (spec 4.2). Commands, GUI clicks, API calls and the console
 * all funnel through {@link #execute(ActionRequest, CommandSender)} so permission checks, outranking,
 * exemptions, limits, auditing, events and webhooks can never be bypassed.
 */
public final class ActionService {

    private final VsmConfig config;
    private final PermissionService permissions;
    private final PunishmentService punishments;
    private final AuditService audit;
    private final MessageService messages;
    private final SecurityService security;
    private final StaffCache staff;
    private final TemplateService templates;

    private volatile WebhookService webhooks;
    private volatile TeleportService teleports;
    private volatile FreezeService freezes;

    public ActionService(VsmConfig config, PermissionService permissions, PunishmentService punishments,
                         AuditService audit, MessageService messages, SecurityService security,
                         StaffCache staff, TemplateService templates) {
        this.config = config;
        this.permissions = permissions;
        this.punishments = punishments;
        this.audit = audit;
        this.messages = messages;
        this.security = security;
        this.staff = staff;
        this.templates = templates;
    }

    public void webhooks(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    public void teleports(TeleportService teleports) {
        this.teleports = teleports;
    }

    public void freezes(FreezeService freezes) {
        this.freezes = freezes;
    }

    /** Main entry point. The sender may be {@code null} for API and auto-mod callers. */
    public ActionResult execute(ActionRequest request, CommandSender sender) {
        // 1. resolve the actor
        UUID actorUuid = request.actorUuid();
        String actorName = request.actorName();
        if (actorUuid == null) {
            actorName = "console";
        }

        // 2. permission check
        String node = request.permission();
        if (node != null && !node.isBlank()) {
            if (actorUuid != null) {
                if (!permissions.has(actorUuid, sender, node)) {
                    return deny(sender, ActionResult.Status.DENIED_PERMISSION, "no-permission");
                }
            } else if (sender != null && !sender.hasPermission(node)) {
                // Console is owner by definition (spec 6.3), so this only triggers for restricted senders.
                return deny(sender, ActionResult.Status.DENIED_PERMISSION, "no-permission");
            }
        }

        // Cannot act on yourself (spec 13.2)
        if (actorUuid != null && actorUuid.equals(request.targetUuid()) && request.kind() != ActionRequest.Kind.VANISH) {
            return deny(sender, ActionResult.Status.DENIED_SELF, "cannot-punish-self");
        }

        // 3. rank-outranks check
        if (request.targetUuid() != null && actorUuid != null) {
            if (!permissions.outranks(sender, request.targetUuid())) {
                return deny(sender, ActionResult.Status.DENIED_OUTRANK, "cannot-punish-higher");
            }
        }

        return switch (request.kind()) {
            case PUNISH -> issue(request, sender, actorUuid, actorName);
            case REMOVE_PUNISHMENT -> remove(request, sender, actorUuid, actorName);
            case MODIFY_PUNISHMENT -> modify(request, sender, actorUuid, actorName);
            case RANK_CHANGE -> rankChange(request, sender, actorUuid, actorName);
            default -> generic(request, sender, actorUuid, actorName);
        };
    }

    // ------------------------------------------------------------- punish
    private ActionResult issue(ActionRequest request, CommandSender sender, UUID actorUuid, String actorName) {
        PunishmentType type = request.punishmentType();
        if (type == null) {
            return ActionResult.failed("invalid-punishment-type");
        }
        // vsm.punishment.unlimited.<type> bypasses the duration cap (spec 7).
        boolean unlimited = actorUuid != null
                && permissions.has(actorUuid, sender, PermissionNodes.PUNISHMENT_UNLIMITED_PREFIX + type.configKey());

        // 4. exemption check
        String exemption = PermissionNodes.exemptionFor(type.name());
        if (sender != null && permissions.isExempt(sender, request.targetUuid(), exemption)
                && !permissions.has(actorUuid, sender, PermissionNodes.OVERRIDE)) {
            return deny(sender, ActionResult.Status.DENIED_EXEMPT, "cannot-punish-exempt");
        }

        // 5. limits check
        String actorRank = rankOf(actorUuid);
        Long duration = resolveDuration(request, type, actorRank, unlimited, sender);
        if (duration == null) {
            return deny(sender, ActionResult.Status.DENIED_LIMIT, "duration-too-long");
        }
        if (!validateReason(sender, request.reason())) {
            return deny(sender, ActionResult.Status.DENIED_INVALID, "reason-required");
        }

        String rateAction = rateActionFor(type);
        if (rateAction != null && !security.allowAction(actorUuid, rateAction, actorName)) {
            return deny(sender, ActionResult.Status.DENIED_RATE_LIMIT, "rate-limit-hit");
        }

        // 6. execute
        Punishment punishment = punishments.issue(
                type,
                request.targetUuid(),
                request.targetName(),
                TextUtil.sanitise(request.reason(), config.maxReasonLength()),
                actorUuid,
                actorName,
                duration,
                request.silent(),
                request.evidence(),
                request.templateId(),
                -1,
                request.extra().isBlank() ? null : request.extra(),
                request.scope());

        // 7. audit
        audit.log(request.source(), actorUuid, actorName, "PUNISH_" + type.name(),
                punishment.targetUuid(), punishment.displayTarget(),
                "reason=" + punishment.reason() + " duration="
                        + (punishment.isPermanent() ? "permanent" : TimeParser.format(duration)) + " id=" + punishment.id());

        // 8. custom event
        PunishmentIssuedEvent event = new PunishmentIssuedEvent(punishment, actorUuid, actorName);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            punishments.remove(punishment, actorUuid, actorName, "cancelled");
            return ActionResult.failed("action-cancelled");
        }

        // 9. broadcast, webhook, staff notification
        punishments.broadcast(punishment, actorName);
        punishments.notifyStaff(punishment, actorName);
        if (webhooks != null) {
            webhooks.send("BAN", punishment, actorName);
        }

        // online enforcement
        if (type.isBan()) {
            punishments.enforceOnline(punishment);
        }
        templates.afterIssue(punishment);
        return ActionResult.success(punishment);
    }

    /**
     * Resolves the effective duration in milliseconds.
     *
     * @return {@link TimeParser#PERMANENT} for a permanent punishment, the duration in millis, or
     *         {@code null} when the actor is not allowed to issue it at that length
     */
    private Long resolveDuration(ActionRequest request, PunishmentType type, String actorRank, boolean unlimited,
                                 CommandSender sender) {
        boolean wantsPermanent = request.permanentRequested()
                || request.durationMillis() == Long.MIN_VALUE
                || request.durationMillis() == TimeParser.PERMANENT;

        if (type.isTemporary() && !wantsPermanent) {
            long requested = request.durationMillis();
            if (requested <= 0L) {
                return null;
            }
            long cap = unlimited ? Long.MAX_VALUE : capFor(actorRank, type);
            if (cap != Long.MAX_VALUE && requested > cap) {
                return null;
            }
            return requested;
        }
        if (!type.isTemporary() && !wantsPermanent) {
            // A permanent command was given a duration: fall back to the temporary variant's cap.
            long cap = unlimited ? Long.MAX_VALUE : capFor(actorRank, type.temporaryVariant());
            if (cap != Long.MAX_VALUE && request.durationMillis() > cap) {
                return null;
            }
            return request.durationMillis();
        }
        // Permanent punishment: check the minimum rank requirement (spec 9.2).
        if (sender != null && sender instanceof Player player
                && permissions.has(player.getUniqueId(), player, PermissionNodes.OVERRIDE)) {
            return TimeParser.PERMANENT;
        }
        String minimum = minimumRankFor(type);
        if (minimum == null) {
            return TimeParser.PERMANENT;
        }
        if (actorRank == null) {
            return null;
        }
        if (permissionsRanks != null && permissionsRanks.atLeast(actorRank, minimum)) {
            return TimeParser.PERMANENT;
        }
        return null;
    }

    private String rankOf(UUID actorUuid) {
        if (actorUuid == null) {
            return "owner";
        }
        return staff.rankOf(actorUuid);
    }

    private String minimumRankFor(PunishmentType type) {
        if (type == PunishmentType.NAME_BAN || type == PunishmentType.IP_BAN) {
            return config.minimumRankFor("ipban-min-rank");
        }
        if (type.isBan()) {
            return config.minimumRankFor("permanent-ban-min-rank");
        }
        if (type.isMute()) {
            return config.minimumRankFor("permanent-mute-min-rank");
        }
        return null;
    }

    /** Per-rank duration cap; {@link Long#MAX_VALUE} means unlimited. */
    public long capFor(String rankId, PunishmentType type) {
        return config.maxDuration(rankId, type.configKey());
    }

    /** Maximum duration a specific staff member may issue. */
    public String capDisplay(String rankId, PunishmentType type) {
        long cap = capFor(rankId, type);
        return cap == Long.MAX_VALUE ? "unlimited" : TimeParser.formatShort(cap);
    }

    private boolean validateReason(CommandSender sender, String reason) {
        if (!config.requireReason()) {
            return true;
        }
        return TextUtil.isValidReason(reason, config.minReasonLength(), config.maxReasonLength());
    }

    private String rateActionFor(PunishmentType type) {
        if (type.isBan()) {
            return "ban";
        }
        if (type.isMute()) {
            return "mute";
        }
        if (type == PunishmentType.KICK) {
            return "kick";
        }
        return null;
    }

    // ------------------------------------------------------------- remove
    private ActionResult remove(ActionRequest request, CommandSender sender, UUID actorUuid, String actorName) {
        String id = request.extra();
        if (id == null || id.isBlank()) {
            return ActionResult.failed("punishment-id-required");
        }
        Optional<Punishment> found = punishments.byId(id);
        if (found.isEmpty()) {
            // Allow removing by player name or IP for convenience.
            List<Punishment> matches = new ArrayList<>();
            for (Punishment punishment : punishments.allActive()) {
                if (id.equalsIgnoreCase(punishment.displayTarget()) || id.equals(punishment.targetIp())) {
                    matches.add(punishment);
                }
            }
            if (matches.isEmpty()) {
                return deny(sender, ActionResult.Status.DENIED_INVALID, "punishment-not-found");
            }
            Punishment punishment = matches.get(0);
            return finishRemoval(punishment, request, sender, actorUuid, actorName);
        }
        Punishment punishment = found.get();
        if (punishment.targetUuid() != null && actorUuid != null
                && !permissions.outranks(sender, punishment.targetUuid())) {
            return deny(sender, ActionResult.Status.DENIED_OUTRANK, "cannot-punish-higher");
        }
        return finishRemoval(punishment, request, sender, actorUuid, actorName);
    }

    private ActionResult finishRemoval(Punishment punishment, ActionRequest request, CommandSender sender,
                                       UUID actorUuid, String actorName) {
        if (!punishment.active()) {
            return ActionResult.failed("punishment-already-inactive");
        }
        String removalReason = request.reason() == null || request.reason().isBlank()
                ? "no reason"
                : TextUtil.sanitise(request.reason(), config.maxReasonLength());

        punishments.remove(punishment, actorUuid, actorName, removalReason);
        audit.log(request.source(), actorUuid, actorName, "PUNISHMENT_REMOVED",
                punishment.targetUuid(), punishment.displayTarget(),
                "id=" + punishment.id() + " type=" + punishment.type().name() + " reason=" + removalReason);

        Bukkit.getPluginManager().callEvent(new PunishmentRemovedEvent(punishment, actorUuid, actorName));

        // Cascade: one /ipunban reverses every account ban linked to the IP ban (spec 8.2 rule 2).
        if (punishment.parentId() != null || punishment.type().isIp()) {
            for (Punishment child : punishments.childrenOf(punishment.id())) {
                if (child.active()) {
                    punishments.remove(child, actorUuid, actorName, "cascaded from " + punishment.id());
                }
            }
        }
        if (webhooks != null) {
            webhooks.send("UNBAN", punishment, actorName);
        }
        if (sender != null) {
            messages.send(sender, "punishment-removed-ok", "id", punishment.id(),
                    "target", punishment.displayTarget(), "type", punishment.type().name());
        }
        return ActionResult.success(punishment);
    }

    // ------------------------------------------------------------- modify
    private ActionResult modify(ActionRequest request, CommandSender sender, UUID actorUuid, String actorName) {
        Optional<Punishment> found = punishments.byId(request.extra());
        if (found.isEmpty()) {
            return deny(sender, ActionResult.Status.DENIED_INVALID, "punishment-not-found");
        }
        Punishment punishment = found.get();
        if (!punishment.active()) {
            return ActionResult.failed("punishment-already-inactive");
        }
        if (punishment.targetUuid() != null && actorUuid != null
                && !permissions.outranks(sender, punishment.targetUuid())) {
            return deny(sender, ActionResult.Status.DENIED_OUTRANK, "cannot-punish-higher");
        }
        String field = request.templateId();
        String value = request.evidence();
        if (!"duration".equalsIgnoreCase(field) && !"reason".equalsIgnoreCase(field)) {
            return ActionResult.failed("unknown-modify-field");
        }
        Long duration = null;
        String newReason = null;
        if ("duration".equalsIgnoreCase(field)) {
            long parsed;
            try {
                parsed = TimeParser.parse(value);
            } catch (IllegalArgumentException ex) {
                return deny(sender, ActionResult.Status.DENIED_INVALID, "invalid-duration");
            }
            if (parsed != TimeParser.PERMANENT) {
                PunishmentType type = punishment.type().isTemporary()
                        ? punishment.type() : punishment.type().temporaryVariant();
                long cap = capFor(rankOf(actorUuid), type);
                if (cap != Long.MAX_VALUE && parsed > cap) {
                    return deny(sender, ActionResult.Status.DENIED_LIMIT, "duration-too-long");
                }
                duration = parsed;
            }
        } else {
            newReason = TextUtil.sanitise(value, config.maxReasonLength());
        }
        punishments.modify(punishment, duration, newReason);
        audit.log(request.source(), actorUuid, actorName, "PUNISHMENT_MODIFIED",
                punishment.targetUuid(), punishment.displayTarget(), "id=" + punishment.id() + " field=" + field);
        if (sender != null) {
            messages.send(sender, "punishment-modified-ok", "id", punishment.id(), "field", field);
        }
        return ActionResult.success(punishment);
    }

    // -------------------------------------------------------- rank change
    private ActionResult rankChange(ActionRequest request, CommandSender sender, UUID actorUuid, String actorName) {
        String rankId = request.rankId();
        if (rankId == null || permissionsRanks == null || !permissionsRanks.exists(rankId)) {
            return ActionResult.failed("unknown-rank");
        }
        if (!config.allowSelfRankChange() && actorUuid != null && actorUuid.equals(request.targetUuid())) {
            return deny(sender, ActionResult.Status.DENIED_SELF, "cannot-change-own-rank");
        }
        if (sender != null && !permissions.canAssignRank(sender, rankId)) {
            return deny(sender, ActionResult.Status.DENIED_OUTRANK, "cannot-promote-to-rank");
        }
        StaffMember member = staff.byUuid(request.targetUuid());
        String previousRank = member == null ? null : member.rankId();
        if (rankId.equals(previousRank)) {
            return ActionResult.failed("rank-unchanged");
        }
        if (!lastOwnerGuard(member, rankId)) {
            return deny(sender, ActionResult.Status.DENIED_OUTRANK, "last-owner-guard");
        }

        Bukkit.getPluginManager().callEvent(new StaffRankChangeEvent(request.targetUuid(), previousRank, rankId, actorUuid));
        if (staffService != null) {
            staffService.setRank(request.targetUuid(), rankId, actorUuid, actorName);
        }
        audit.log(request.source(), actorUuid, actorName, "STAFF_RANK_CHANGE",
                request.targetUuid(), request.targetName(), previousRank + " -> " + rankId);
        if (webhooks != null) {
            webhooks.sendRankChange(request.targetName(), previousRank, rankId, actorName);
        }
        if (sender != null) {
            messages.send(sender, "rank-changed-ok", "player",
                    request.targetName() == null ? String.valueOf(request.targetUuid()) : request.targetName(),
                    "rank", permissionsRanks.display(rankId));
        }
        return ActionResult.success(rankId);
    }

    /** The last remaining owner cannot be removed or demoted (spec 6.3). */
    private boolean lastOwnerGuard(StaffMember member, String newRankId) {
        if (member == null || permissionsRanks == null) {
            return true;
        }
        String ownerId = ownerRankId();
        if (ownerId == null || !ownerId.equals(member.rankId())) {
            return true;
        }
        if (newRankId == null || !ownerId.equals(newRankId)) {
            return countOwners() > 1;
        }
        return true;
    }

    private volatile String ownerRankIdCache;

    public void ownerRankId(String rankId) {
        this.ownerRankIdCache = rankId;
    }

    private String ownerRankId() {
        return ownerRankIdCache;
    }

    public int countOwners() {
        if (permissionsRanks == null || ownerRankIdCache == null) {
            return 0;
        }
        return staff.byRank(ownerRankIdCache).size();
    }

    private volatile StaffService staffService;

    public void staffService(StaffService staffService) {
        this.staffService = staffService;
    }

    private volatile io.github.aethelreborn.vsm.config.RankRegistry permissionsRanks;

    public void ranks(io.github.aethelreborn.vsm.config.RankRegistry ranks) {
        this.permissionsRanks = ranks;
    }

    // ------------------------------------------------------------ generic
    private ActionResult generic(ActionRequest request, CommandSender sender, UUID actorUuid, String actorName) {
        audit.log(request.source(), actorUuid, actorName, request.kind().name(),
                request.targetUuid(), request.targetName(), request.reason());
        Bukkit.getPluginManager().callEvent(new StaffActionEvent(request.kind().name(), actorUuid, actorName,
                request.targetUuid(), request.reason()));
        return ActionResult.success(request.extra());
    }

    private ActionResult deny(CommandSender sender, ActionResult.Status status, String key) {
        if (sender != null) {
            messages.send(sender, key);
        }
        return ActionResult.denied(status, key);
    }

    /** Broadcast helper used by chat control commands. */
    public void broadcastChatControl(String key, CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permissions.has(player, PermissionNodes.CHAT_BYPASS)) {
                continue;
            }
            messages.send(player, key);
        }
    }

    public PermissionService permissions() {
        return permissions;
    }

    public VsmConfig config() {
        return config;
    }

    public AuditService audit() {
        return audit;
    }
}