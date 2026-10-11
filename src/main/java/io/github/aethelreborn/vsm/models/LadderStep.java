package io.github.aethelreborn.vsm.models;

import java.util.LinkedHashSet;
import java.util.Set;

/** One rung of a punishment ladder (spec 9.3). */
public final class LadderStep {

    private PunishmentType type;
    private long duration = io.github.aethelreborn.vsm.util.TimeParser.PERMANENT;
    private final Set<String> flags = new LinkedHashSet<>();

    public LadderStep() {
    }

    public LadderStep(PunishmentType type, long duration) {
        this.type = type;
        this.duration = duration;
    }

    public LadderStep(PunishmentType type, long duration, Set<String> flags) {
        this.type = type;
        this.duration = duration;
        if (flags != null) {
            this.flags.addAll(flags);
        }
    }

    public PunishmentType type() {
        return type;
    }

    public LadderStep type(PunishmentType type) {
        this.type = type;
        return this;
    }

    public long duration() {
        return duration;
    }

    public LadderStep duration(long duration) {
        this.duration = duration;
        return this;
    }

    public Set<String> flags() {
        return flags;
    }

    public LadderStep flags(Set<String> flags) {
        this.flags.clear();
        if (flags != null) {
            this.flags.addAll(flags);
        }
        return this;
    }

    public boolean hasFlag(String flag) {
        return flag != null && flags.contains(flag);
    }

    @Override
    public String toString() {
        return "LadderStep{" + type + " " + (duration == io.github.aethelreborn.vsm.util.TimeParser.PERMANENT
                ? "permanent" : io.github.aethelreborn.vsm.util.TimeParser.formatShort(duration)) + "}";
    }
}