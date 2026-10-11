package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.models.Punishment;

/** Outcome of an {@link ActionRequest} handled by {@link ActionService}. */
public final class ActionResult {

    public enum Status {
        SUCCESS,
        DENIED_PERMISSION,
        DENIED_OUTRANK,
        DENIED_EXEMPT,
        DENIED_LIMIT,
        DENIED_RATE_LIMIT,
        DENIED_INVALID,
        DENIED_SELF,
        FAILED
    }

    private final Status status;
    private final String messageKey;
    private final Punishment punishment;
    private final Object payload;

    private ActionResult(Status status, String messageKey, Punishment punishment, Object payload) {
        this.status = status;
        this.messageKey = messageKey;
        this.punishment = punishment;
        this.payload = payload;
    }

    public static ActionResult success(Punishment punishment) {
        return new ActionResult(Status.SUCCESS, "", punishment, null);
    }

    public static ActionResult success(Punishment punishment, Object payload) {
        return new ActionResult(Status.SUCCESS, "", punishment, payload);
    }

    public static ActionResult success(Object payload) {
        return new ActionResult(Status.SUCCESS, "", null, payload);
    }

    public static ActionResult denied(Status status, String messageKey) {
        return new ActionResult(status, messageKey, null, null);
    }

    public static ActionResult failed(String messageKey) {
        return new ActionResult(Status.FAILED, messageKey, null, null);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }

    public Punishment punishment() {
        return punishment;
    }

    public Object payload() {
        return payload;
    }

    @Override
    public String toString() {
        return "ActionResult{" + status + (messageKey.isEmpty() ? "" : " " + messageKey) + "}";
    }
}