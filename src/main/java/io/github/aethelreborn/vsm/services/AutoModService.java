package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.AutoModConfig;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.util.TextUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Auto-moderation (spec 11.7). Everything runs on the async chat thread against cached data, so no
 * database access is ever needed here.
 */
public final class AutoModService {

    /** What the chat listener should do with a message. */
    public record ChatResult(boolean cancelled, String replacement, boolean alertStaff, String violation) {

        public static ChatResult pass() {
            return new ChatResult(false, null, false, null);
        }
    }

    private final AutoModConfig config;
    private final PermissionService permissions;
    private final MessageService messages;
    private final ActionService actions;
    private final Map<UUID, Deque<Long>> messageTimes = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> repeatCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> points = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> commandTimes = new ConcurrentHashMap<>();

    public AutoModService(AutoModConfig config, PermissionService permissions, MessageService messages,
                          ActionService actions) {
        this.config = config;
        this.permissions = permissions;
        this.messages = messages;
        this.actions = actions;
    }

    private boolean exempt(Player player) {
        return permissions.has(player, PermissionNodes.EXEMPT_AUTOMOD);
    }

    /** Evaluates one chat message. Must not touch storage or Bukkit state. */
    public ChatResult onChat(Player player, String rawMessage) {
        if (!config.enabled() || exempt(player)) {
            return ChatResult.pass();
        }
        String message = TextUtil.stripZeroWidth(rawMessage == null ? "" : rawMessage);

        if (message.isBlank()) {
            return ChatResult.pass();
        }

        // Character flood and unicode abuse
        int flood = longestRun(message);
        if (flood >= config.maxCharFlood()) {
            addPoints(player, 2);
            return new ChatResult(true, null, true, "char-flood");
        }
        if (countNonAscii(message) > config.maxUnicodePerMessage()) {
            addPoints(player, 2);
            return new ChatResult(true, null, true, "unicode-abuse");
        }

        // Caps
        if (message.length() >= config.minLengthForCaps()) {
            int letters = 0;
            int upper = 0;
            for (int i = 0; i < message.length(); i++) {
                char c = message.charAt(i);
                if (Character.isLetter(c)) {
                    letters++;
                    if (Character.isUpperCase(c)) {
                        upper++;
                    }
                }
            }
            if (letters > 0 && (upper * 100 / letters) > config.maxCapsPercent()) {
                addPoints(player, 1);
                return new ChatResult(true, null, true, "caps");
            }
        }

        // Blocked words and patterns
        String leet = TextUtil.leet(message);
        for (AutoModConfig.BlockedRule rule : config.blocked()) {
            boolean matched = rule.regex()
                    ? rule.pattern().matcher(message).find()
                    : leet.contains(TextUtil.leet(rule.pattern().pattern()));
            if (!matched) {
                continue;
            }
            addPoints(player, rule.action() == AutoModConfig.Action.TEMP_MUTE ? 4 : 2);
            String violation = rule.message();
            return switch (rule.action()) {
                case CANCEL -> new ChatResult(true, null, true, violation);
                case REPLACE -> new ChatResult(false, "***", true, violation);
                case WARN -> {
                    warn(player, violation);
                    yield new ChatResult(true, null, true, violation);
                }
                case TEMP_MUTE -> {
                    tempMute(player, violation);
                    yield new ChatResult(true, null, true, violation);
                }
                default -> new ChatResult(true, null, true, violation);
            };
        }

        // Advertising
        if (config.isAdvertisement(message.toLowerCase(java.util.Locale.ROOT))) {
            addPoints(player, 3);
            return new ChatResult(true, null, true, "advertising");
        }

        // Spam: rate, identical repeat and near-duplicate similarity
        long now = System.currentTimeMillis();
        Deque<Long> times = messageTimes.computeIfAbsent(player.getUniqueId(), key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < now - config.windowSeconds() * 1000L) {
                times.pollFirst();
            }
            times.addLast(now);
            if (times.size() > config.messagesPerWindow()) {
                addPoints(player, 2);
                return new ChatResult(true, null, true, "spam-rate");
            }
        }
        String previous = lastMessage.get(player.getUniqueId());
        if (previous != null) {
            if (previous.equalsIgnoreCase(message)) {
                int repeats = repeatCount.merge(player.getUniqueId(), 1, Integer::sum);
                if (repeats >= config.identicalRepeatLimit()) {
                    addPoints(player, 2);
                    return new ChatResult(true, null, true, "spam-repeat");
                }
            } else if (AutoModConfig.similarity(previous, message) >= config.similarityThreshold()) {
                addPoints(player, 1);
                return new ChatResult(true, null, true, "spam-similarity");
            } else {
                repeatCount.put(player.getUniqueId(), 1);
            }
        }
        lastMessage.put(player.getUniqueId(), message);
        return ChatResult.pass();
    }

    /** Command rate limiting (spec 11.7). */
    public boolean commandAllowed(Player player) {
        if (!config.enabled() || exempt(player)) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> times = commandTimes.computeIfAbsent(player.getUniqueId(), key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < now - config.commandWindowSeconds() * 1000L) {
                times.pollFirst();
            }
            times.addLast(now);
            return times.size() <= config.commandLimit();
        }
    }

    private void warn(Player player, String violation) {
        actions.execute(ActionRequest.builder(ActionSource.AUTOMOD)
                .kind(ActionRequest.Kind.PUNISH)
                .console()
                .permission(PermissionNodes.WARN)
                .punishment(PunishmentType.WARN)
                .target(player.getUniqueId(), player.getName())
                .reason("AutoMod: " + violation)
                .permanent()
                .silent(false), null);
    }

    private void tempMute(Player player, String violation) {
        actions.execute(ActionRequest.builder(ActionSource.AUTOMOD)
                .kind(ActionRequest.Kind.PUNISH)
                .console()
                .permission(PermissionNodes.TEMP_MUTE)
                .punishment(PunishmentType.TEMP_MUTE)
                .target(player.getUniqueId(), player.getName())
                .reason("AutoMod: " + violation)
                .duration(config.muteDuration())
                .silent(false), null);
    }

    /** Accumulates violation points and triggers escalation thresholds (spec 11.7). */
    private void addPoints(Player player, int amount) {
        int total = points.merge(player.getUniqueId(), amount, Integer::sum);
        alertStaff(player, total);
        if (total >= config.pointsBeforeMute()) {
            tempMute(player, "escalation");
            points.put(player.getUniqueId(), 0);
        } else if (total >= config.pointsBeforeWarn()) {
            warn(player, "escalation");
        }
    }

    private void alertStaff(Player player, int points) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!permissions.has(staff, PermissionNodes.ALERTS)) {
                continue;
            }
            messages.send(staff, "automod-alert",
                    "player", player.getName(),
                    "points", Integer.toString(points),
                    "uuid", player.getUniqueId().toString());
        }
    }

    public int pointsOf(UUID uuid) {
        return points.getOrDefault(uuid, 0);
    }

    public void forget(UUID uuid) {
        messageTimes.remove(uuid);
        lastMessage.remove(uuid);
        repeatCount.remove(uuid);
        points.remove(uuid);
        commandTimes.remove(uuid);
    }

    public void reset() {
        messageTimes.clear();
        lastMessage.clear();
        repeatCount.clear();
        points.clear();
        commandTimes.clear();
    }

    public Map<String, Integer> statistics() {
        return Map.of(
                "tracked", messageTimes.size(),
                "points", points.size());
    }

    public List<String> problems() {
        return config.problems();
    }

    private static int longestRun(String message) {
        int best = 0;
        char current = 0;
        int run = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isWhitespace(c)) {
                run = 0;
                current = 0;
                continue;
            }
            if (c == current) {
                run++;
            } else {
                current = c;
                run = 1;
            }
            if (run > best) {
                best = run;
            }
        }
        return best;
    }

    private static int countNonAscii(String message) {
        int count = 0;
        for (int i = 0; i < message.length(); i++) {
            if (message.charAt(i) > 127) {
                count++;
            }
        }
        return count;
    }

    /** Exposed for the debug command. */
    public List<String> blockedPatterns() {
        List<String> out = new ArrayList<>();
        for (AutoModConfig.BlockedRule rule : config.blocked()) {
            out.add(rule.pattern().pattern());
        }
        return out;
    }
}