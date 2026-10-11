package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.RankRegistry;
import io.github.aethelreborn.vsm.models.StaffMember;
import io.github.aethelreborn.vsm.models.StaffRank;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.storage.cache.StaffCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Whitelist management (spec 8.1). All rank mutations are validated by {@link ActionService}. */
public final class StaffService {

    private final StorageProvider storage;
    private final StaffCache cache;
    private final RankRegistry ranks;

    public StaffService(StorageProvider storage, StaffCache cache, RankRegistry ranks) {
        this.storage = storage;
        this.cache = cache;
        this.ranks = ranks;
    }

    /** Loads the whitelist into the cache at startup. */
    public void loadCache() {
        cache.replaceAll(storage.loadStaff().join().values());
        cache.ranks(ranks);
    }

    public void add(UUID uuid, String name, String rankId, UUID actorUuid, String actorName) {
        StaffMember member = new StaffMember(uuid, name, rankId);
        member.addedAt(System.currentTimeMillis());
        member.lastSeen(System.currentTimeMillis());
        member.addedByName(actorName);
        member.addedBy(actorUuid);
        cache.put(member);
        storage.saveStaff(member);
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
        storage.deleteStaff(uuid);
    }

    public void setRank(UUID uuid, String rankId, UUID actorUuid, String actorName) {
        StaffMember member = cache.byUuid(uuid);
        if (member == null) {
            member = new StaffMember(uuid, uuid.toString(), rankId);
            member.addedAt(System.currentTimeMillis());
            member.addedByName(actorName);
        }
        member.rankId(rankId);
        cache.put(member);
        storage.saveStaff(member);
    }

    public void updateLastSeen(UUID uuid, String name, long when) {
        StaffMember member = cache.byUuid(uuid);
        if (member == null) {
            return;
        }
        member.lastSeen(when);
        if (name != null) {
            member.name(name);
        }
        cache.put(member);
        storage.saveStaff(member);
    }

    public StaffMember member(UUID uuid) {
        return cache.byUuid(uuid);
    }

    public StaffMember memberByName(String name) {
        return cache.byName(name);
    }

    /** Ranks and weights, highest first, for {@code /staff ranks}. */
    public List<StaffRank> ranksDescending() {
        List<StaffRank> list = new ArrayList<>(ranks.all());
        list.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return list;
    }

    /** Staff grouped by rank, highest first (spec 8.1 {@code /staff list}). */
    public List<StaffRank> orderedRanks() {
        List<StaffRank> list = new ArrayList<>(ranks.staffRanks());
        list.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return list;
    }
}