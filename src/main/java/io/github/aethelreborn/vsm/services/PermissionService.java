package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.RankRegistry;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.storage.cache.StaffCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

/**
 * Resolves {@code vsm.*} nodes from the internal whitelist and, depending on
 * {@code settings.permission-mode}, from Bukkit as well (spec 6.1, 6.4).
 */
public final class PermissionService {

    private final VsmConfig config;
    private final RankRegistry ranks;
    private final StaffCache staff;
    private final PluginManager pluginManager;
    private volatile boolean registered;

    public PermissionService(VsmConfig config, RankRegistry ranks, StaffCache staff, PluginManager pluginManager) {
        this.config = config;
        this.ranks = ranks;
        this.staff = staff;
        this.pluginManager = pluginManager;
    }

    /** Registers every node from spec 7 with Bukkit so other permission plugins can grant them. */
    public void registerNodes() {
        if (registered) {
            return;
        }
        registered = true;
        Map<String, Map<String, Boolean>> children = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : PermissionNodes.hierarchy().entrySet()) {
            Map<String, Boolean> map = new LinkedHashMap<>();
            for (String child : entry.getValue()) {
                map.put(child, Boolean.TRUE);
            }
            children.put(entry.getKey(), map);
        }
        for (Map.Entry<String, String> entry : PermissionNodes.all().entrySet()) {
            String node = entry.getKey();
            if (pluginManager.getPermission(node) != null) {
                continue;
            }
            PermissionDefault permissionDefault = PermissionNodes.DEFAULT_TRUE.contains(node)
                    ? PermissionDefault.TRUE
                    : PermissionDefault.FALSE;
            Permission permission = new Permission(node, entry.getValue(), permissionDefault, children.get(node));
            pluginManager.addPermission(permission);
        }
    }

    /** Console is treated as owner for permission purposes but is still audited (spec 6.3). */
    public boolean has(CommandSender sender, String node) {
        if (sender == null || node == null || node.isEmpty()) {
            return false;
        }
        if (sender instanceof Player player) {
            return has(player.getUniqueId(), player, node);
        }
        // Console and command blocks always pass.
        return true;
    }

    public boolean has(UUID uuid, String node) {
        return has(uuid, null, node);
    }

    public boolean has(UUID uuid, CommandSender bukkitSender, String node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        VsmConfig.PermissionMode mode = config.permissionMode();
        if (mode != VsmConfig.PermissionMode.BUKKIT && internalGrant(uuid, node)) {
            return true;
        }
        if (mode == VsmConfig.PermissionMode.INTERNAL) {
            return false;
        }
        if (bukkitSender != null) {
            return bukkitSender.hasPermission(node);
        }
        return false;
    }

    /** Internal (whitelist) grant check: rank permissions plus any node inherited through the hierarchy. */
    public boolean internalGrant(UUID uuid, String node) {
        if (uuid == null) {
            return false;
        }
        String rankId = staff.rankOf(uuid);
        if (rankId == null) {
            return false;
        }
        if (PermissionNodes.DEFAULT_TRUE.contains(node) && !isStaff(uuid)) {
            return true;
        }
        var rank = ranks.rank(rankId);
        if (rank == null) {
            return false;
        }
        if (rank.hasNode(node)) {
            return true;
        }
        // Group nodes inherit their children, so holding a group node grants its members' nodes.
        if (PermissionNodes.ALL.equals(node)) {
            return false;
        }
        return false;
    }

    /** {@code true} when the player is on the whitelist at all (staff or YouTuber). */
    public boolean isStaff(UUID uuid) {
        return staff.isStaff(uuid);
    }

    /** {@code true} when the player holds a rank flagged as staff, excluding the YouTuber side branch. */
    public boolean isStaffRank(UUID uuid) {
        return staff.isStaffRank(uuid);
    }

    public boolean isStaffRank(String rankId) {
        return ranks.isStaffRank(rankId);
    }

    public String rankOf(UUID uuid) {
        return staff.rankOf(uuid);
    }

    public String rankOf(Player player) {
        return player == null ? null : staff.rankOf(player.getUniqueId());
    }

    /** Weight of the player's rank, or {@link Integer#MIN_VALUE} for a normal player. */
    public int weightOf(UUID uuid) {
        return staff.weightOf(uuid);
    }

    public int weightOfRank(String rankId) {
        return ranks.weight(rankId);
    }

    /** Console behaves as owner (spec 6.3). */
    public int weightOfSender(CommandSender sender) {
        if (sender == null) {
            return Integer.MAX_VALUE;
        }
        if (sender instanceof Player player) {
            return staff.weightOf(player.getUniqueId());
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Outranking rule (spec 6.3): an actor may only act on a strictly lower weight.
     * {@code vsm.override} bypasses this for owners.
     */
    public boolean outranks(CommandSender actor, UUID targetUuid) {
        if (actor == null) {
            return true;
        }
        if (!(actor instanceof Player)) {
            return true;
        }
        UUID actorUuid = ((Player) actor).getUniqueId();
        if (actorUuid.equals(targetUuid)) {
            return false;
        }
        if (has(actorUuid, actor, PermissionNodes.OVERRIDE)) {
            return true;
        }
        int actorWeight = weightOf(actorUuid);
        int targetWeight = weightOf(targetUuid);
        if (targetWeight == Integer.MIN_VALUE) {
            return true; // ordinary player
        }
        return actorWeight > targetWeight;
    }

    public boolean outranksRanks(String actorRank, String targetRank) {
        if (actorRank == null) {
            return false;
        }
        return ranks.weight(actorRank) > ranks.weight(targetRank);
    }

    /** Nobody may promote to a rank at or above their own weight (spec 6.3). */
    public boolean canAssignRank(CommandSender actor, String newRankId) {
        if (actor == null) {
            return true;
        }
        if (!(actor instanceof Player)) {
            return true;
        }
        Player player = (Player) actor;
        if (has(player.getUniqueId(), player, PermissionNodes.OVERRIDE)) {
            return true;
        }
        int actorWeight = weightOf(player.getUniqueId());
        return ranks.weight(newRankId) < actorWeight;
    }

    /**
     * Exemption check (spec 4.2 step 4). {@code vsm.override} and console bypass it.
     *
     * @param exemptionNode one of the {@code vsm.exempt.*} nodes, may be {@code null}
     */
    public boolean isExempt(CommandSender actor, UUID targetUuid, String exemptionNode) {
        if (exemptionNode == null || exemptionNode.isBlank()) {
            return false;
        }
        if (targetUuid == null) {
            return false;
        }
        if (actor != null && !(actor instanceof Player)) {
            return false;
        }
        if (!config.exemptPermissionChecks()) {
            return false;
        }
        if (actor instanceof Player player && has(player.getUniqueId(), player, PermissionNodes.OVERRIDE)) {
            return false;
        }
        return has(targetUuid, exemptionNode);
    }

    /**
     * Warns on startup when a non-whitelisted account holds {@code vsm.*} while the plugin is in
     * {@code INTERNAL} mode (spec 13.6).
     */
    public List<String> auditWildcardHolders() {
        List<String> warnings = new ArrayList<>();
        if (config.permissionMode() != VsmConfig.PermissionMode.INTERNAL) {
            return warnings;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (staff.isStaff(uuid)) {
                continue;
            }
            if (player.hasPermission(PermissionNodes.ALL) && seen.add(player.getName())) {
                warnings.add(player.getName() + " holds vsm.* but is not on the staff whitelist; "
                        + "the node is ignored while permission-mode is INTERNAL.");
            }
        }
        return warnings;
    }
}