package io.github.aethelreborn.vsm.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** Quick reasons from {@code reasons.yml}, used for tab completion and the punish GUI. */
public final class ReasonRegistry {

    private final List<String> reasons = new ArrayList<>();
    private final List<String> templateSuggestions = new ArrayList<>();

    public void load(FileConfiguration config, TemplateRegistry templates) {
        reasons.clear();
        for (String reason : config.getStringList("reasons")) {
            if (reason != null && !reason.isBlank()) {
                reasons.add(reason.trim());
            }
        }
        templateSuggestions.clear();
        templateSuggestions.addAll(templates.ids());
    }

    public List<String> reasons() {
        return reasons;
    }

    public List<String> templateSuggestions() {
        return templateSuggestions;
    }

    /** Common duration suggestions used by Brigadier suggestions (spec section 8 preamble). */
    public static List<String> durationSuggestions() {
        return List.of("30m", "1h", "6h", "12h", "1d", "3d", "7d", "14d", "30d", "perm");
    }

    /** Duration suggestions that are valid for a given punishment type. */
    public static List<String> durationSuggestions(String punishmentTypeName) {
        if (punishmentTypeName == null) {
            return durationSuggestions();
        }
        String name = punishmentTypeName.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("ip")) {
            return List.of("1h", "6h", "12h", "1d", "3d", "7d", "30d", "perm");
        }
        if (name.contains("mute")) {
            return List.of("30m", "1h", "6h", "12h", "1d", "3d", "7d");
        }
        if (name.contains("warn")) {
            return List.of("1h", "12h", "1d", "7d", "14d", "30d");
        }
        return durationSuggestions();
    }
}