package io.github.aethelreborn.vsm.storage.cache;

import io.github.aethelreborn.vsm.config.RankRegistry;
import io.github.aethelreborn.vsm.models.StaffMember;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable snapshots of the staff whitelist for permission checks. This class never performs I/O, so it is
 * safe to read from chat, pre-login and tab-completion handlers (spec 3.2, 15.3).
 */
public final class StaffCache {

    private final Map<UUID, StaffMember> byUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private volatile RankRegistry ranks;

    public StaffCache(RankRegistry ranks) {
        this.ranks = ranks;
    }

    public void ranks(RankRegistry ranks) {
        this.ranks = ranks;
    }

    public RankRegistry ranks() {
        return ranks;
    }

    /** Replaces the whole cache; used on startup and after a reload. */
    public void replaceAll(Collection<StaffMember> members) {
        byUuid.clear();
        byName.clear();
        for (StaffMember member : members) {
            if (member == null || member.uuid() == null) {
                continue;
            }
            byUuid.put(member.uuid(), member);
            if (member.name() != null) {
                byName.put(member.name().toLowerCase(Locale.ROOT), member.uuid());
            }
        }
    }

    public void put(StaffMember member) {
        if (member == null || member.uuid() == null) {
            return;
        }
        StaffMember previous = byUuid.put(member.uuid(), member);
        if (previous != null && previous.name() != null) {
            byName.remove(previous.name().toLowerCase(Locale.ROOT));
        }
        if (member.name() != null) {
            byName.put(member.name().toLowerCase(Locale.ROOT), member.uuid());
        }
    }

    public void remove(UUID uuid) {
        StaffMember removed = byUuid.remove(uuid);
        if (removed != null && removed.name() != null) {
            byName.remove(removed.name().toLowerCase(Locale.ROOT));
        }
    }

    public StaffMember byUuid(UUID uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    public StaffMember byName(String name) {
        if (name == null) {
            return null;
        }
        UUID uuid = byName.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : byUuid.get(uuid);
    }

    public boolean isStaff(UUID uuid) {
        return uuid != null && byUuid.containsKey(uuid);
    }

    public boolean isStaff(String name) {
        return byName(name) != null;
    }

    public String rankOf(UUID uuid) {
        StaffMember member = byUuid.get(uuid);
        return member == null ? null : member.rankId();
    }

    public int weightOf(UUID uuid) {
        StaffMember member = byUuid.get(uuid);
        if (member == null) {
            return Integer.MIN_VALUE;
        }
        return ranks == null ? Integer.MIN_VALUE : ranks.weight(member.rankId());
    }

    public boolean isStaffRank(UUID uuid) {
        StaffMember member = byUuid.get(uuid);
        return member != null && ranks != null && ranks.isStaffRank(member.rankId());
    }

    public Collection<StaffMember> all() {
        return new ArrayList<>(byUuid.values());
    }

    public List<StaffMember> byRank(String rankId) {
        List<StaffMember> out = new ArrayList<>();
        for (StaffMember member : byUuid.values()) {
            if (member.rankId().equalsIgnoreCase(rankId)) {
                out.add(member);
            }
        }
        return out;
    }

    public Map<String, Integer> countsByRank() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StaffMember member : byUuid.values()) {
            counts.merge(member.rankId(), 1, Integer::sum);
        }
        return counts;
    }

    public int size() {
        return byUuid.size();
    }

    /** Online staff count used by the {@code %vsm_staff_online%} placeholder. */
    public int onlineStaff(java.util.function.Predicate<UUID> online) {
        int count = 0;
        for (UUID uuid : byUuid.keySet()) {
            if (online.test(uuid)) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        byUuid.clear();
        byName.clear();
    }
}