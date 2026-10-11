package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.TemplateRegistry;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.LadderStep;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentTemplate;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.storage.cache.PunishmentCache;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.CommandSender;

/**
 * Applies templates and ladders (spec 9.3) and runs the automatic warning-threshold escalation.
 */
public final class TemplateService {

    private final TemplateRegistry templates;
    private final VsmConfig config;
    private final PunishmentCache cache;
    private final ActionService actions;
    private final MessageService messages;

    public TemplateService(TemplateRegistry templates, VsmConfig config, PunishmentCache cache,
                           ActionService actions, MessageService messages) {
        this.templates = templates;
        this.config = config;
        this.cache = cache;
        this.actions = actions;
        this.messages = messages;
    }

    public TemplateRegistry registry() {
        return templates;
    }

    /**
     * Applies the ladder step that matches how many prior punishments the target has with this template.
     *
     * @return the issued punishment
     */
    public Punishment apply(CommandSender sender, UUID actorUuid, String actorName, UUID targetUuid,
                            String targetName, String templateId, boolean silent, String evidence) {
        PunishmentTemplate template = templates.template(templateId);
        if (template == null) {
            messages.send(sender, "unknown-template", "template", String.valueOf(templateId));
            return null;
        }
        if (templates.hasProblems()) {
            messages.send(sender, "templates-invalid");
        }
        long now = System.currentTimeMillis();
        int prior = cache.countWithTemplate(targetUuid, template.id(), now);
        LadderStep step = template.stepFor(prior);
        if (step == null) {
            messages.send(sender, "unknown-template", "template", templateId);
            return null;
        }
        if (step.hasFlag("evidence-required") && (evidence == null || evidence.isBlank())) {
            messages.send(sender, "evidence-required");
            return null;
        }
        String reason = TextUtil.sanitise(template.reason(), config.maxReasonLength());
        long duration = step.duration();
        if (duration == TimeParser.PERMANENT && step.type().isTemporary()) {
            duration = TimeParser.PERMANENT;
        }

        ActionRequest request = ActionRequest.builder(ActionSource.COMMAND)
                .kind(ActionRequest.Kind.PUNISH)
                .actor(actorUuid, actorName)
                .permission(permissionFor(step.type()))
                .punishment(step.type())
                .target(targetUuid, targetName)
                .reason(reason)
                .duration(duration)
                .silent(silent)
                .evidence(evidence)
                .template(template.id())
                ;
        ActionResult result = actions.execute(request, sender);
        if (!result.isSuccess()) {
            return null;
        }
        Punishment punishment = result.punishment();
        if (punishment != null) {
            punishment.ladderStep(prior);
        }
        messages.send(sender, "template-applied",
                "template", template.display(),
                "type", step.type().name(),
                "target", targetName == null ? String.valueOf(targetUuid) : targetName);
        return punishment;
    }

    /** Escalates automatically once the warning threshold is reached (spec 9.3). */
    public void checkWarningThresholds(Punishment warning) {
        if (warning == null || !warning.type().isWarn() || warning.targetUuid() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int active = cache.activeWarnings(warning.targetUuid(), now).size();
        TemplateRegistry.WarningThreshold threshold = templates.thresholdFor(active);
        if (threshold == null) {
            return;
        }
        if (warning.templateId() != null && threshold.reason() != null
                && warning.templateId().equals(threshold.reason())) {
            return; // already escalated by this threshold
        }
        ActionRequest request = ActionRequest.builder(ActionSource.AUTOMOD)
                .kind(ActionRequest.Kind.PUNISH)
                .console()
                .permission(permissionFor(threshold.type()))
                .punishment(threshold.type())
                .target(warning.targetUuid(), warning.targetName())
                .reason(threshold.reason())
                .duration(threshold.duration())
                .silent(false)
                ;
        actions.execute(request, null);
    }

    /** Called by {@link ActionService} right after a punishment is stored. */
    public void afterIssue(Punishment punishment) {
        if (punishment == null || !punishment.type().isWarn()) {
            return;
        }
        checkWarningThresholds(punishment);
    }

    public Optional<PunishmentTemplate> template(String id) {
        return Optional.ofNullable(templates.template(id));
    }

    private static String permissionFor(PunishmentType type) {
        return switch (type) {
            case WARN, TEMP_WARN -> "vsm.warn";
            case MUTE, TEMP_MUTE -> "vsm.tempmute";
            case SHADOW_MUTE -> "vsm.shadowmute";
            case IP_MUTE, TEMP_IP_MUTE -> "vsm.ipmute";
            case KICK -> "vsm.kick";
            case BAN -> "vsm.ban";
            case TEMP_BAN -> "vsm.tempban";
            case IP_BAN -> "vsm.ipban";
            case TEMP_IP_BAN -> "vsm.tempipban";
            case NAME_BAN -> "vsm.nameban";
        };
    }
}