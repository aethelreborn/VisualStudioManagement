package io.github.aethelreborn.vsm.models;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A punishment template with an optional escalation ladder (spec 9.3). The step that applies is chosen by
 * how many punishments the target already has that used this template.
 */
public final class PunishmentTemplate {

    private String id;
    private String display;
    private String reason = "";
    private final List<LadderStep> ladder = new ArrayList<>();
    private final Set<String> flags = new LinkedHashSet<>();

    public PunishmentTemplate() {
    }

    public PunishmentTemplate(String id, String display, String reason) {
        this.id = id;
        this.display = display;
        this.reason = reason;
    }

    public String id() {
        return id;
    }

    public PunishmentTemplate id(String id) {
        this.id = id;
        return this;
    }

    public String display() {
        return display;
    }

    public PunishmentTemplate display(String display) {
        this.display = display;
        return this;
    }

    public String reason() {
        return reason;
    }

    public PunishmentTemplate reason(String reason) {
        this.reason = reason;
        return this;
    }

    public List<LadderStep> ladder() {
        return ladder;
    }

    public PunishmentTemplate ladder(List<LadderStep> ladder) {
        this.ladder.clear();
        if (ladder != null) {
            this.ladder.addAll(ladder);
        }
        return this;
    }

    /** Template wide flags such as {@code evidence-required}. */
    public Set<String> flags() {
        return flags;
    }

    public PunishmentTemplate flags(Set<String> flags) {
        this.flags.clear();
        if (flags != null) {
            this.flags.addAll(flags);
        }
        return this;
    }

    public boolean isValid() {
        if (id == null || id.isBlank() || ladder.isEmpty()) {
            return false;
        }
        for (LadderStep step : ladder) {
            if (step.type() == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Picks the ladder step for the given number of previous punishments issued with this template.
     * The last step repeats once the ladder is exhausted.
     */
    public LadderStep stepFor(int previousCount) {
        if (ladder.isEmpty()) {
            return null;
        }
        int index = Math.min(Math.max(previousCount, 0), ladder.size() - 1);
        return ladder.get(index);
    }

    @Override
    public String toString() {
        return "PunishmentTemplate{" + id + " steps=" + ladder.size() + "}";
    }
}