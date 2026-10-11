package io.github.aethelreborn.vsm.storage.cache;

import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.util.CidrMatcher;
import io.github.aethelreborn.vsm.util.IpUtil;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory view of every active punishment, keyed by target UUID, by IP and by punishment id, plus the
 * CIDR ranges for range bans (spec 15.3). Pre-login and chat handlers read only from here.
 */
public final class PunishmentCache {

    public record RangeBan(CidrMatcher matcher, Punishment punishment) {
    }

    private final Map<UUID, List<Punishment>> byUuid = new ConcurrentHashMap<>();
    private final Map<String, List<Punishment>> byIp = new ConcurrentHashMap<>();
    private final Map<String, Punishment> byId = new ConcurrentHashMap<>();
    private final List<RangeBan> ranges = new ArrayList<>();
    private final List<UUID> ipAllowed = new ArrayList<>();

    /** Builds the cache from storage at startup. */
    public void replaceAll(Collection<Punishment> activePunishments) {
        clear();
        for (Punishment punishment : activePunishments) {
            add(punishment);
        }
    }

    public synchronized void add(Punishment punishment) {
        if (punishment == null) {
            return;
        }
        if (punishment.id() != null && !punishment.id().isBlank()) {
            byId.put(punishment.id(), punishment);
        }
        if (punishment.targetUuid() != null) {
            byUuid.computeIfAbsent(punishment.targetUuid(), uuid -> new ArrayList<>()).add(punishment);
        }
        if (punishment.targetIp() != null && !punishment.targetIp().isBlank()) {
            addIp(punishment);
        }
    }

    private void addIp(Punishment punishment) {
        String ip = punishment.targetIp().trim();
        String key = IpUtil.cacheKey(ip);
        if (key.isEmpty()) {
            return;
        }
        byIp.computeIfAbsent(key, ignored -> new ArrayList<>()).add(punishment);
        if (IpUtil.isCidr(ip)) {
            CidrMatcher matcher = CidrMatcher.parse(ip);
            if (matcher != null) {
                ranges.add(new RangeBan(matcher, punishment));
            }
        }
    }

    /** Removes a punishment from every index; used when it expires or is lifted. */
    public synchronized void remove(Punishment punishment) {
        if (punishment == null) {
            return;
        }
        if (punishment.targetUuid() != null) {
            List<Punishment> list = byUuid.get(punishment.targetUuid());
            if (list != null) {
                list.removeIf(p -> p.id() != null && p.id().equals(punishment.id()));
                if (list.isEmpty()) {
                    byUuid.remove(punishment.targetUuid());
                }
            }
        }
        if (punishment.targetIp() != null && !punishment.targetIp().isBlank()) {
            String key = IpUtil.cacheKey(punishment.targetIp());
            List<Punishment> list = byIp.get(key);
            if (list != null) {
                list.removeIf(p -> p.id() != null && p.id().equals(punishment.id()));
                if (list.isEmpty()) {
                    byIp.remove(key);
                }
            }
            ranges.removeIf(range -> range.punishment().id() != null
                    && range.punishment().id().equals(punishment.id()));
        }
        if (punishment.id() != null) {
            byId.remove(punishment.id());
        }
    }

    public Optional<Punishment> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /** Active punishments for an account, expiry already evaluated. */
    public List<Punishment> activeFor(UUID uuid, long now) {
        List<Punishment> list = byUuid.get(uuid);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Punishment> out = new ArrayList<>(list.size());
        for (Punishment punishment : list) {
            if (punishment.isActiveNow(now)) {
                out.add(punishment);
            }
        }
        return out;
    }

    public List<Punishment> activeForUuidIncludingExpired(UUID uuid) {
        List<Punishment> list = byUuid.get(uuid);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Active punishments attached to an IP, exact matches only. */
    public List<Punishment> activeForIp(String ip, long now) {
        List<Punishment> list = byIp.get(IpUtil.cacheKey(ip));
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Punishment> out = new ArrayList<>(list.size());
        for (Punishment punishment : list) {
            if (punishment.isActiveNow(now)) {
                out.add(punishment);
            }
        }
        return out;
    }

    /** First active CIDR range ban matching the IP, or {@code null}. */
    public Punishment rangeBanFor(String ip, long now) {
        for (RangeBan range : ranges) {
            if (range.punishment().isActiveNow(now) && range.matcher().matches(ip)) {
                return range.punishment();
            }
        }
        return null;
    }

    public boolean hasIpAllow(UUID uuid) {
        synchronized (ipAllowed) {
            return ipAllowed.contains(uuid);
        }
    }

    public void addIpAllow(UUID uuid) {
        synchronized (ipAllowed) {
            if (!ipAllowed.contains(uuid)) {
                ipAllowed.add(uuid);
            }
        }
    }

    public void removeIpAllow(UUID uuid) {
        synchronized (ipAllowed) {
            ipAllowed.remove(uuid);
        }
    }

    public List<UUID> ipAllowList() {
        synchronized (ipAllowed) {
            return List.copyOf(ipAllowed);
        }
    }

    /** All active mutes for an account, used by {@code AsyncChatEvent}. */
    public List<Punishment> activeMutes(UUID uuid, long now) {
        List<Punishment> out = new ArrayList<>();
        for (Punishment punishment : activeFor(uuid, now)) {
            if (punishment.type().blocksChat()) {
                out.add(punishment);
            }
        }
        return out;
    }

    /** All active IP mutes for an IP address. */
    public List<Punishment> activeIpMutes(String ip, long now) {
        List<Punishment> out = new ArrayList<>();
        for (Punishment punishment : activeForIp(ip, now)) {
            if (punishment.type().blocksChat()) {
                out.add(punishment);
            }
        }
        return out;
    }

    /** Active warnings, ordered oldest first, used by the warning threshold escalation. */
    public List<Punishment> activeWarnings(UUID uuid, long now) {
        List<Punishment> out = new ArrayList<>();
        for (Punishment punishment : activeFor(uuid, now)) {
            if (punishment.type().isWarn()) {
                out.add(punishment);
            }
        }
        out.sort(Comparator.comparingLong(Punishment::issuedAt));
        return out;
    }

    /** Active punishments that were issued with a given template, used to pick a ladder step. */
    public int countWithTemplate(UUID uuid, String templateId, long now) {
        if (templateId == null) {
            return 0;
        }
        int count = 0;
        for (Punishment punishment : activeFor(uuid, now)) {
            if (templateId.equals(punishment.templateId())) {
                count++;
            }
        }
        return count;
    }

    /** Expired-but-still-flagged active records, swept by the expiry task. */
    public List<Punishment> expired(long now) {
        List<Punishment> out = new ArrayList<>();
        for (List<Punishment> list : byUuid.values()) {
            for (Punishment punishment : list) {
                if (punishment.active() && !punishment.isActiveNow(now)) {
                    out.add(punishment);
                }
            }
        }
        for (List<Punishment> list : byIp.values()) {
            for (Punishment punishment : list) {
                if (punishment.active() && !punishment.isActiveNow(now)) {
                    out.add(punishment);
                }
            }
        }
        return out;
    }

    public List<Punishment> allActive(long now) {
        List<Punishment> out = new ArrayList<>();
        for (List<Punishment> list : byUuid.values()) {
            for (Punishment punishment : list) {
                if (punishment.isActiveNow(now)) {
                    out.add(punishment);
                }
            }
        }
        for (List<Punishment> list : byIp.values()) {
            for (Punishment punishment : list) {
                if (punishment.isActiveNow(now)) {
                    out.add(punishment);
                }
            }
        }
        return out;
    }

    /** Debug helper for {@code /vsm debug}. */
    public Map<String, Integer> statistics() {
        Map<String, Integer> stats = new java.util.LinkedHashMap<>();
        stats.put("byUuid", byUuid.size());
        stats.put("byIp", byIp.size());
        stats.put("byId", byId.size());
        stats.put("ranges", ranges.size());
        stats.put("ipAllow", ipAllowed.size());
        return stats;
    }

    public void clear() {
        byUuid.clear();
        byIp.clear();
        byId.clear();
        ranges.clear();
        synchronized (ipAllowed) {
            ipAllowed.clear();
        }
    }

    /** Formats a remaining time for display, never showing {@code -1}. */
    public static String describe(Punishment punishment, long now) {
        return punishment.expiresAt() == TimeParser.PERMANENT
                ? "never"
                : TimeParser.formatRemaining(punishment.expiresAt(), now);
    }
}