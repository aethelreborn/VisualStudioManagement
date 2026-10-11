package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.PunishmentType;
import java.util.UUID;

/**
 * A single staff action travelling through {@link ActionService} (spec 4.2).
 *
 * <p>Every mutation of punishment, rank or sensitive state is expressed as one of these, so permission
 * checks, outranking, exemptions, limits, auditing, events and webhooks can never be bypassed by a GUI
 * click or an API call.
 */
public final class ActionRequest {

    public enum Kind {
        /** Issue a punishment. */
        PUNISH,
        /** Lift a punishment. */
        REMOVE_PUNISHMENT,
        /** Change a punishment in place. */
        MODIFY_PUNISHMENT,
        /** Add, change or remove a staff rank. */
        RANK_CHANGE,
        /** Freeze / unfreeze. */
        FREEZE,
        /** Vanish toggle. */
        VANISH,
        /** Staff mode toggle. */
        STAFF_MODE,
        /** Teleport a player. */
        TELEPORT,
        /** Open a player's inventory. */
        INVSEE_VIEW,
        /** Open a player's ender chest. */
        ENDERSEE_VIEW,
        /** Edit a player's inventory. */
        INVSEE_EDIT,
        /** Claim / close / assign a report. */
        REPORT,
        /** Chat control action. */
        CHAT_CONTROL,
        /** Anything else that still needs auditing. */
        GENERIC
    }

    private Kind kind = Kind.GENERIC;
    private UUID actorUuid;
    private String actorName = "console";
    private ActionSource source = ActionSource.COMMAND;
    private String permission = "";

    private PunishmentType punishmentType;
    private UUID targetUuid;
    private String targetName;
    private String targetIp;
    private String reason = "";
    private long durationMillis = Long.MIN_VALUE;
    private boolean permanent;
    private boolean silent;
    private String evidence;
    private String templateId;
    private String scope = "";

    private String rankId;
    private String extra = "";
    private boolean consoleActor;

    public static ActionRequest builder(ActionSource source) {
        return new ActionRequest().source(source);
    }

    public ActionRequest kind(Kind kind) {
        this.kind = kind;
        return this;
    }

    public ActionRequest actor(UUID uuid, String name) {
        this.actorUuid = uuid;
        this.actorName = name == null ? "console" : name;
        this.consoleActor = uuid == null;
        return this;
    }

    public ActionRequest consoleActor() {
        this.actorUuid = null;
        this.actorName = "console";
        this.consoleActor = true;
        return this;
    }

    public ActionRequest source(ActionSource source) {
        this.source = source == null ? ActionSource.COMMAND : source;
        return this;
    }

    public ActionRequest permission(String permission) {
        this.permission = permission == null ? "" : permission;
        return this;
    }

    public ActionRequest punishment(PunishmentType type) {
        this.punishmentType = type;
        return this;
    }

    public ActionRequest target(UUID uuid, String name) {
        this.targetUuid = uuid;
        this.targetName = name;
        return this;
    }

    public ActionRequest targetIp(String ip) {
        this.targetIp = ip;
        return this;
    }

    public ActionRequest reason(String reason) {
        this.reason = reason == null ? "" : reason;
        return this;
    }

    public ActionRequest duration(long millis) {
        this.durationMillis = millis;
        return this;
    }

    public ActionRequest permanent() {
        this.permanent = true;
        return this;
    }

    public ActionRequest silent(boolean silent) {
        this.silent = silent;
        return this;
    }

    public ActionRequest evidence(String evidence) {
        this.evidence = evidence;
        return this;
    }

    public ActionRequest template(String templateId) {
        this.templateId = templateId;
        return this;
    }

    public ActionRequest scope(String scope) {
        this.scope = scope == null ? "" : scope;
        return this;
    }

    public ActionRequest rank(String rankId) {
        this.rankId = rankId;
        return this;
    }

    public ActionRequest extra(String extra) {
        this.extra = extra == null ? "" : extra;
        return this;
    }

    public Kind kind() {
        return kind;
    }

    public UUID actorUuid() {
        return actorUuid;
    }

    public String actorName() {
        return actorName;
    }

    public boolean consoleActor() {
        return consoleActor;
    }

    public ActionSource source() {
        return source;
    }

    public String permission() {
        return permission;
    }

    public PunishmentType punishmentType() {
        return punishmentType;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public String targetIp() {
        return targetIp;
    }

    public String reason() {
        return reason;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public boolean permanentRequested() {
        return permanent;
    }

    public boolean silent() {
        return silent;
    }

    public String evidence() {
        return evidence;
    }

    public String templateId() {
        return templateId;
    }

    public String scope() {
        return scope;
    }

    public String rankId() {
        return rankId;
    }

    public String extra() {
        return extra;
    }
}