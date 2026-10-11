package io.github.aethelreborn.vsm.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * All user visible text (spec 3.3). Nothing user facing is hard coded in Java: every string lives in
 * {@code messages.yml} and is rendered with MiniMessage.
 *
 * <p>Player supplied text is always injected with {@link Placeholder#unparsed} so a reason containing
 * {@code <click:run_command:...>} renders as plain text (spec 13.3 / acceptance test 21).
 */
public final class Messages {

    private final FileConfiguration messages;
    private final boolean legacyColorCodes;
    private final String prefix;

    public Messages(FileConfiguration messages, boolean legacyColorCodes, String prefix) {
        this.messages = messages;
        this.legacyColorCodes = legacyColorCodes;
        this.prefix = prefix == null ? "" : prefix;
    }

    /** Raw template string for a key, or the key itself when missing so gaps are obvious. */
    public String raw(String key) {
        String value = messages.getString(key);
        if (value == null) {
            return "<red>Missing message: " + key;
        }
        return value;
    }

    public boolean has(String key) {
        return messages.isString(key);
    }

    /** Renders without the plugin prefix. */
    public Component get(String key) {
        return render(raw(key), Map.of(), false);
    }

    /** Renders with the plugin prefix, used for confirmations and errors. */
    public Component prefixed(String key) {
        return render(raw(key), Map.of(), true);
    }

    public Component get(String key, Map<String, String> placeholders) {
        return render(raw(key), placeholders, false);
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        return render(raw(key), placeholders, true);
    }

    /** Convenience for the very common single-placeholder case. */
    public Component get(String key, String placeholder, String value) {
        return render(raw(key), Map.of(placeholder, value), false);
    }

    public Component prefixed(String key, String placeholder, String value) {
        return render(raw(key), Map.of(placeholder, value), true);
    }

    /** A multi-line screen such as the ban message, without any prefix. */
    public Component screen(String key, Map<String, String> placeholders) {
        return render(raw(key), placeholders, false);
    }

    public List<String> rawList(String key) {
        List<String> list = messages.getStringList(key);
        return list == null ? List.of() : list;
    }

    public List<String> rawSection(String path) {
        ConfigurationSection section = messages.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            out.add(key);
        }
        return out;
    }

    public String prefixComponent() {
        return prefix;
    }

    private Component render(String template, Map<String, String> placeholders, boolean withPrefix) {
        String text = withPrefix ? prefix + template : template;
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size() + 1);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            resolvers.add(Placeholder.unparsed(entry.getKey(), value));
        }
        TagResolver[] array = resolvers.toArray(new TagResolver[0]);
        String prepared = legacyColorCodes ? LegacyComponentSerializer.legacyAmpersand().deserialize(text) : text;
        try {
            return MiniMessage.miniMessage().deserialize(prepared, array);
        } catch (RuntimeException ex) {
            // A malformed message must never take the server down; fall back to plain text.
            return Component.text(prepared);
        }
    }

    /** Builds a placeholder map, skipping null values. */
    public static Map<String, String> placeholders(Object... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            map.put(String.valueOf(keyValues[i]), value == null ? "" : String.valueOf(value));
        }
        return map;
    }

    /** Placeholder builder that also carries the configuration for convenience. */
    public Map<String, String> ph(Object... keyValues) {
        return placeholders(keyValues);
    }

    /** Reads a nested section as a map of key to template. */
    public Map<String, String> section(String path) {
        Map<String, String> map = new HashMap<>();
        ConfigurationSection section = messages.getConfigurationSection(path);
        if (section == null) {
            return map;
        }
        for (String key : section.getKeys(false)) {
            map.put(key, section.getString(key, key));
        }
        return map;
    }
}