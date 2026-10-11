package io.github.aethelreborn.vsm.config;

import io.github.aethelreborn.vsm.models.LadderStep;
import io.github.aethelreborn.vsm.models.PunishmentTemplate;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Loads {@code templates.yml}. Invalid entries are collected instead of throwing so the server keeps
 * running and staff are warned on every punishment command until the file is fixed (spec 9.3).
 */
public final class TemplateRegistry {

    /** Warning threshold escalation, e.g. {@code 3 warnings -> TEMP_MUTE 1h} (spec 9.3). */
    public record WarningThreshold(int count, PunishmentType type, long duration, String reason) {
    }

    private final Map<String, PunishmentTemplate> templates = new LinkedHashMap<>();
    private final Map<Integer, WarningThreshold> warningThresholds = new TreeMap<>();
    private final List<String> problems = new ArrayList<>();

    public void load(FileConfiguration config, Logger logger) {
        templates.clear();
        warningThresholds.clear();
        problems.clear();

        ConfigurationSection section = config.getConfigurationSection("templates");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(id);
                if (node == null) {
                    problems.add("templates." + id + " is not a section");
                    continue;
                }
                PunishmentTemplate template = new PunishmentTemplate(
                        id,
                        node.getString("display", id),
                        node.getString("reason", ""));
                template.flags(new LinkedHashSet<>(node.getStringList("flags")));
                List<LadderStep> steps = new ArrayList<>();
                for (Object raw : node.getList("ladder", List.of())) {
                    LadderStep step = parseStep(raw, id, steps.size());
                    if (step != null) {
                        steps.add(step);
                    }
                }
                template.ladder(steps);
                if (!template.isValid()) {
                    problems.add("template '" + id + "' has no valid ladder");
                    continue;
                }
                templates.put(id, template);
            }
        }

        ConfigurationSection thresholds = config.getConfigurationSection("warning-thresholds");
        if (thresholds != null) {
            for (String key : thresholds.getKeys(false)) {
                int count;
                try {
                    count = Integer.parseInt(key.trim());
                } catch (NumberFormatException ex) {
                    problems.add("warning-thresholds." + key + " is not a number");
                    continue;
                }
                ConfigurationSection node = thresholds.getConfigurationSection(key);
                if (node == null) {
                    problems.add("warning-thresholds." + key + " is not a section");
                    continue;
                }
                PunishmentType type = PunishmentType.fromString(node.getString("type", ""));
                if (type == null) {
                    problems.add("warning-thresholds." + key + " has an unknown type");
                    continue;
                }
                long duration = parseDuration(node.getString("duration", ""), count, type, problems);
                warningThresholds.put(count, new WarningThreshold(count, type, duration,
                        node.getString("reason", count + " warnings reached")));
            }
        }

        if (!problems.isEmpty() && logger != null) {
            for (String problem : problems) {
                logger.warning("templates.yml problem: " + problem);
            }
            logger.warning("templates.yml loaded with " + problems.size() + " problem(s); affected templates are disabled.");
        }
    }

    private LadderStep parseStep(Object raw, String templateId, int index) {
        if (raw instanceof String text) {
            PunishmentType type = PunishmentType.fromString(text);
            if (type == null) {
                problems.add("template '" + templateId + "' step " + index + " has unknown type '" + text + "'");
                return null;
            }
            return new LadderStep(type, defaultDuration(type));
        }
        if (!(raw instanceof Map<?, ?> map)) {
            problems.add("template '" + templateId + "' step " + index + " is not a map");
            return null;
        }
        PunishmentType type = PunishmentType.fromString(String.valueOf(map.get("type")));
        if (type == null) {
            problems.add("template '" + templateId + "' step " + index + " has unknown type '" + map.get("type") + "'");
            return null;
        }
        long duration = defaultDuration(type);
        Object rawDuration = map.get("duration");
        if (rawDuration != null) {
            duration = parseDuration(String.valueOf(rawDuration), index, type, problems);
        }
        Set<String> flags = new LinkedHashSet<>();
        Object rawFlags = map.get("flags");
        if (rawFlags instanceof List<?> list) {
            for (Object flag : list) {
                flags.add(String.valueOf(flag));
            }
        }
        return new LadderStep(type, duration, flags);
    }

    private long parseDuration(String raw, int index, PunishmentType type, List<String> problems) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("permanent")) {
            return TimeParser.PERMANENT;
        }
        try {
            return TimeParser.parse(raw);
        } catch (IllegalArgumentException ex) {
            problems.add("step " + index + " has invalid duration '" + raw + "'");
            return defaultDuration(type);
        }
    }

    private long defaultDuration(PunishmentType type) {
        return type.isTemporary() ? TimeParser.parseOr("1d", TimeParser.parseOr("1h", 3_600_000L))
                : TimeParser.PERMANENT;
    }

    public PunishmentTemplate template(String id) {
        return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return template(id) != null;
    }

    public Map<String, PunishmentTemplate> all() {
        return templates;
    }

    public List<String> ids() {
        return new ArrayList<>(templates.keySet());
    }

    /** The lowest unmet warning threshold, or {@code null} when nothing applies. */
    public WarningThreshold thresholdFor(int activeWarnings) {
        for (Map.Entry<Integer, WarningThreshold> entry : warningThresholds.entrySet()) {
            if (activeWarnings >= entry.getKey()) {
                return entry.getValue();
            }
        }
        return null;
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }
}