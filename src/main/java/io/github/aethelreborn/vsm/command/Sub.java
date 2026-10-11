package io.github.aethelreborn.vsm.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** Helper used by the command modules to attach a sub-tree, optionally filtered to a single literal. */
public final class Sub {

    private Sub() {
    }

    /**
     * Attaches {@code child} to {@code parent}.
     *
     * @param only when non-null, the child is attached only if its literal matches
     */
    public static void then(LiteralArgumentBuilder<CommandSourceStack> parent, String name,
                            LiteralArgumentBuilder<CommandSourceStack> child, String only) {
        if (only == null || only.equalsIgnoreCase(name)) {
            parent.then(child);
        }
    }

    /** {@code true} when this module should contribute anything at all. */
    public static boolean contributes(String only, String... names) {
        if (only == null) {
            return true;
        }
        for (String name : names) {
            if (name.equalsIgnoreCase(only)) {
                return true;
            }
        }
        return false;
    }
}