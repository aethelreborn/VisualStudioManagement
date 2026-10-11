package io.github.aethelreborn.vsm.storage.cache;

import io.github.aethelreborn.vsm.util.IpUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login IP cache (spec 11.8 / 15.3): the most recent address per account plus the accounts seen on each
 * address, used by {@code /alts} and the join-time alt alert. Never performs I/O.
 */
public final class IpCache {

    private final Map<UUID, String> lastIpByUuid = new ConcurrentHashMap<>();
    private final Map<String, List<UUID>> uuidsByIp = new ConcurrentHashMap<>();

    public void record(UUID uuid, String ip) {
        if (uuid == null || ip == null || ip.isBlank()) {
            return;
        }
        String key = IpUtil.cacheKey(ip);
        lastIpByUuid.put(uuid, key);
        uuidsByIp.computeIfAbsent(key, ignored -> new ArrayList<>()).add(uuid);
    }

    public String lastIp(UUID uuid) {
        return uuid == null ? null : lastIpByUuid.get(uuid);
    }

    public boolean hasSeen(UUID uuid, String ip) {
        List<UUID> list = uuidsByIp.get(IpUtil.cacheKey(ip));
        return list != null && list.contains(uuid);
    }

    /** Accounts seen on this IP, excluding {@code exclude} when supplied. */
    public List<UUID> accountsOnIp(String ip, UUID exclude) {
        List<UUID> list = uuidsByIp.get(IpUtil.cacheKey(ip));
        if (list == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>(list.size());
        for (UUID uuid : list) {
            if (exclude == null || !exclude.equals(uuid)) {
                out.add(uuid);
            }
        }
        return out;
    }

    public int knownIps(UUID uuid) {
        return lastIpByUuid.containsKey(uuid) ? 1 : 0;
    }

    public Map<String, Integer> statistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("uuids", lastIpByUuid.size());
        stats.put("ips", uuidsByIp.size());
        return stats;
    }

    public void remove(UUID uuid) {
        String key = lastIpByUuid.remove(uuid);
        if (key != null) {
            List<UUID> list = uuidsByIp.get(key);
            if (list != null) {
                list.remove(uuid);
                if (list.isEmpty()) {
                    uuidsByIp.remove(key);
                }
            }
        }
    }

    public void clear() {
        lastIpByUuid.clear();
        uuidsByIp.clear();
    }
}