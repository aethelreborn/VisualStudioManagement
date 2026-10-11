package io.github.aethelreborn.vsm.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Auto-moderation rules from {@code automod.yml} (spec 11.7). Everything is validated at load time so a
 * broken regex is reported once instead of throwing on every chat message.
 */
public final class AutoModConfig {

    public enum Action {
        CANCEL,
        REPLACE,
        WARN,
        STAFF_ALERT,
        TEMP_MUTE;

        static Action parse(String raw, Action fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return fallback;
            }
        }
    }

    public record BlockedRule(Pattern pattern, String message, Action action, boolean regex) {
    }

    private boolean enabled;
    private int messagesPerWindow = 5;
    private int windowSeconds = 10;
    private int identicalRepeatLimit = 3;
    private double similarityThreshold = 0.85D;
    private int maxCapsPercent = 70;
    private int minLengthForCaps = 12;
    private int maxCharFlood = 12;
    private int maxUnicodePerMessage = 12;
    private int commandLimit = 12;
    private int commandWindowSeconds = 10;
    private int pointsBeforeMute = 8;
    private int pointsBeforeWarn = 3;
    private long muteDuration = 3_600_000L;
    private final List<String> adWhitelist = new ArrayList<>();
    private final List<BlockedRule> blocked = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    public void load(FileConfiguration config) {
        problems.clear();
        blocked.clear();
        enabled = config.getBoolean("automod.enabled", true);
        messagesPerWindow = Math.max(2, config.getInt("automod.spam.messages-per-window", 5));
        windowSeconds = Math.max(1, config.getInt("automod.spam.window-seconds", 10));
        identicalRepeatLimit = Math.max(2, config.getInt("automod.spam.identical-repeat-limit", 3));
        similarityThreshold = clamp(config.getDouble("automod.spam.similarity-threshold", 0.85D), 0.1D, 1.0D);
        maxCapsPercent = Math.min(100, Math.max(0, config.getInt("automod.caps.max-percent", 70)));
        minLengthForCaps = Math.max(1, config.getInt("automod.caps.min-length", 12));
        maxCharFlood = Math.max(3, config.getInt("automod.char-flood.max-repeats", 12));
        maxUnicodePerMessage = Math.max(0, config.getInt("automod.unicode.max-per-message", 12));
        commandLimit = Math.max(2, config.getInt("automod.command-rate.max-per-window", 12));
        commandWindowSeconds = Math.max(1, config.getInt("automod.command-rate.window-seconds", 10));
        pointsBeforeWarn = Math.max(1, config.getInt("automod.escalation.warn-points", 3));
        pointsBeforeMute = Math.max(1, config.getInt("automod.escalation.mute-points", 8));
        muteDuration = io.github.aethelreborn.vsm.util.TimeParser.parseOr(
                config.getString("automod.escalation.mute-duration", "1h"), 3_600_000L);
        adWhitelist.clear();
        adWhitelist.addAll(config.getStringList("automod.advertising.whitelist"));

        loadBlocked(config.getConfigurationSection("automod.blocked-words"), false);
        loadBlocked(config.getConfigurationSection("automod.blocked-patterns"), true);
    }

    private void loadBlocked(ConfigurationSection section, boolean regex) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            String patternText;
            String message;
            Action action;
            if (node == null) {
                patternText = section.getString(key, "");
                message = key;
                action = Action.CANCEL;
            } else {
                patternText = node.getString("pattern", node.getString("word", key));
                message = node.getString("message", key);
                action = Action.parse(node.getString("action", "CANCEL"), Action.CANCEL);
            }
            try {
                blocked.add(new BlockedRule(Pattern.compile(patternText, Pattern.CASE_INSENSITIVE),
                        message, action, regex));
            } catch (PatternSyntaxException ex) {
                problems.add("automod." + (regex ? "blocked-patterns." : "blocked-words.") + key
                        + " has an invalid pattern: " + ex.getDescription());
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean enabled() {
        return enabled;
    }

    public int messagesPerWindow() {
        return messagesPerWindow;
    }

    public int windowSeconds() {
        return windowSeconds;
    }

    public int identicalRepeatLimit() {
        return identicalRepeatLimit;
    }

    public double similarityThreshold() {
        return similarityThreshold;
    }

    public int maxCapsPercent() {
        return maxCapsPercent;
    }

    public int minLengthForCaps() {
        return minLengthForCaps;
    }

    public int maxCharFlood() {
        return maxCharFlood;
    }

    public int maxUnicodePerMessage() {
        return maxUnicodePerMessage;
    }

    public int commandLimit() {
        return commandLimit;
    }

    public int commandWindowSeconds() {
        return commandWindowSeconds;
    }

    public int pointsBeforeWarn() {
        return pointsBeforeWarn;
    }

    public int pointsBeforeMute() {
        return pointsBeforeMute;
    }

    public long muteDuration() {
        return muteDuration;
    }

    public List<BlockedRule> blocked() {
        return blocked;
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    /** @return {@code true} when the text contains an advertising domain, IP or link. */
    public boolean isAdvertisement(String normalised) {
        String text = normalised.toLowerCase(Locale.ROOT);
        for (String allowed : adWhitelist) {
            if (!allowed.isBlank() && text.contains(allowed.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        String leet = io.github.aethelreborn.vsm.util.TextUtil.leet(text);
        for (String allowed : adWhitelist) {
            if (!allowed.isBlank() && leet.contains(allowed.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (text.contains("http://") || text.contains("https://") || text.contains("www.")) {
            return true;
        }
        if (AD_PATTERN.matcher(text).find()) {
            return true;
        }
        if (text.matches(".*\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?\\b.*")) {
            return true;
        }
        return IPV6_PATTERN.matcher(text).find() || DOMAIN_PATTERN.matcher(text).find();
    }

    private static final Pattern AD_PATTERN = Pattern.compile("\\b(?:discord\\.gg|dsc\\.gg|add\\.tiktok|skype|whatsapp|telegram)\\b");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("\\b[a-z0-9][a-z0-9-]{1,30}\\.(com|net|org|io|gg|xyz|ru|cc|me|co)\\b");
    private static final Pattern IPV6_PATTERN = Pattern.compile("\\b(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{1,4}\\b");

    /** Jaccard similarity of two messages, used for "near duplicate" spam detection. */
    public static double similarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return a == null || b == null ? 0.0D : (a.equals(b) ? 1.0D : 0.0D);
        }
        Set<String> setA = new LinkedHashSet<>(List.of(a.toLowerCase(Locale.ROOT).split("\\s+")));
        Set<String> setB = new LinkedHashSet<>(List.of(b.toLowerCase(Locale.ROOT).split("\\s+")));
        setA.remove("");
        setB.remove("");
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0D;
        }
        int intersection = 0;
        for (String token : setA) {
            if (setB.contains(token)) {
                intersection++;
            }
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0D : (double) intersection / union;
    }
}