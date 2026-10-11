package io.github.aethelreborn.vsm.config;

import io.github.aethelreborn.vsm.models.StaffRank;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Loads {@code ranks.yml}, flattens inheritance once (spec 6.3) and owns the ordering used by every
 * outranking check.
 */
public final class RankRegistry {

    private final Map<String, StaffRank> ranks = new LinkedHashMap<>();
    private final List<StaffRank> byWeight = new ArrayList<>();
    private String defaultRank = "helper";

    /** Loads ranks from configuration, applying the built-in defaults for anything missing. */
    public void load(FileConfiguration config) {
        ranks.clear();
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection rankSection = section.getConfigurationSection(id);
                if (rankSection == null) {
                    continue;
                }
                StaffRank rank = new StaffRank(
                        id,
                        rankSection.getString("display", id),
                        rankSection.getInt("weight", 0),
                        rankSection.getString("parent", "player"));
                rank.prefix(rankSection.getString("prefix", ""));
                rank.staff(rankSection.getBoolean("staff", !"youtuber".equals(id)));
                rank.permissions(rankSection.getStringList("permissions"));
                ranks.put(id, rank);
            }
        }
        defaultRank = config.getString("staff.default-rank", "helper");
        flattenAll();
    }

    /** Convenience overload used by the loader when {@code ranks.yml} is absent. */
    public void loadDefaults(Map<String, StaffRank> defaults) {
        ranks.clear();
        ranks.putAll(defaults);
        flattenAll();
    }

    /**
     * Resolves every rank's effective permission set once. Inheritance follows the declared
     * {@code parent}, so a side branch such as {@code youtuber} only ever inherits from {@code player}.
     */
    public final void flattenAll() {
        for (StaffRank rank : ranks.values()) {
            rank.invalidate();
        }
        for (StaffRank rank : ranks.values()) {
            Set<String> resolved = new LinkedHashSet<>();
            collect(rank, resolved, new LinkedHashSet<>());
            rank.replaceFlattened(resolved);
        }
        byWeight.clear();
        List<StaffRank> sorted = new ArrayList<>(ranks.values());
        sorted.sort((a, b) -> Integer.compare(a.weight(), b.weight()));
        byWeight.addAll(sorted);
    }

    private void collect(StaffRank rank, Set<String> out, Set<String> visiting) {
        if (rank == null || !visiting.add(rank.id())) {
            return; // cycle guard
        }
        String parentId = rank.parent();
        if (parentId != null) {
            StaffRank parent = ranks.get(parentId);
            if (parent != null && !parent.id().equals(rank.id())) {
                collect(parent, out, visiting);
            }
        }
        out.addAll(rank.permissions());
    }

    public StaffRank rank(String id) {
        return id == null ? null : ranks.get(id);
    }

    public String defaultRank() {
        return defaultRank;
    }

    public void defaultRank(String defaultRank) {
        this.defaultRank = defaultRank;
    }

    public Collection<StaffRank> all() {
        return byWeight;
    }

    public List<String> ids() {
        return new ArrayList<>(ranks.keySet());
    }

    /** Ascending by weight; used for {@code /staff promote} and {@code /staff demote}. */
    public List<StaffRank> ladder() {
        return byWeight;
    }

    /** The next rank up the ladder, or {@code null} when already at the top. */
    public StaffRank promote(String rankId) {
        for (int i = 0; i < byWeight.size(); i++) {
            if (byWeight.get(i).id().equals(rankId)) {
                return i + 1 < byWeight.size() ? byWeight.get(i + 1) : null;
            }
        }
        return null;
    }

    /** The next rank down the ladder, or {@code null} when already at the bottom. */
    public StaffRank demote(String rankId) {
        for (int i = 0; i < byWeight.size(); i++) {
            if (byWeight.get(i).id().equals(rankId)) {
                return i > 0 ? byWeight.get(i - 1) : null;
            }
        }
        return null;
    }

    public int weight(String rankId) {
        StaffRank rank = rank(rankId);
        return rank == null ? Integer.MIN_VALUE : rank.weight();
    }

    public String display(String rankId) {
        StaffRank rank = rank(rankId);
        return rank == null ? "Player" : rank.display();
    }

    public boolean exists(String rankId) {
        return rankId != null && ranks.containsKey(rankId);
    }

    /** {@code true} when the rank is flagged as staff ({@code youtuber} is not). */
    public boolean isStaffRank(String rankId) {
        StaffRank rank = rank(rankId);
        return rank != null && rank.staff();
    }

    /** Ranks that appear in staff lists, webhooks and notifications. */
    public List<StaffRank> staffRanks() {
        List<StaffRank> out = new ArrayList<>();
        for (StaffRank rank : byWeight) {
            if (rank.staff() && rank.weight() > 0) {
                out.add(rank);
            }
        }
        return out;
    }

    /**
     * Outranking rule (spec 6.3): an actor may only act on a strictly lower weight.
     *
     * @param actorWeight weight of the acting rank, {@link Integer#MAX_VALUE} for console
     * @param targetWeight weight of the targeted rank
     */
    public static boolean outranks(int actorWeight, int targetWeight) {
        return actorWeight > targetWeight;
    }

    /** True when {@code rankId} is at or above {@code minimumRankId} on the ladder. */
    public boolean atLeast(String rankId, String minimumRankId) {
        if (minimumRankId == null || minimumRankId.isBlank()) {
            return true;
        }
        return weight(rankId) >= weight(minimumRankId);
    }

    /** First rank whose weight is greater than or equal to {@code required}. */
    public String firstRankAtLeast(int required) {
        for (StaffRank rank : byWeight) {
            if (rank.weight() >= required) {
                return rank.id();
            }
        }
        return null;
    }

    public static String normalise(String rankId) {
        return rankId == null ? null : rankId.trim().toLowerCase(Locale.ROOT);
    }
}