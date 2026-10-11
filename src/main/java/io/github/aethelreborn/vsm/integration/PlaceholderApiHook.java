package io.github.aethelreborn.vsm.integration;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI hook (spec 18.3). Soft dependency: the plugin enables normally without PlaceholderAPI.
 */
public final class PlaceholderApiHook extends PlaceholderExpansion {

    private final VisualStudioManagement plugin;

    private PlaceholderApiHook(VisualStudioManagement plugin) {
        this.plugin = plugin;
    }

    /** Registers the expansion when PlaceholderAPI is present; never throws when it is absent. */
    public static void register(VisualStudioManagement plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new PlaceholderApiHook(plugin).register();
            plugin.getLogger().info("Registered PlaceholderAPI expansion.");
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not register the PlaceholderAPI expansion: " + throwable.getMessage());
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vsm";
    }

    @Override
    public @NotNull String getAuthor() {
        return "aethelreborn";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        var uuid = player.getUniqueId();
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "rank" -> orEmpty(plugin.permissionService().rankOf(uuid));
            case "rank_display" -> plugin.rankRegistry().display(plugin.permissionService().rankOf(uuid));
            case "is_staff" -> Boolean.toString(plugin.permissionService().isStaff(uuid));
            case "is_staff_rank" -> Boolean.toString(plugin.permissionService().isStaffRank(uuid));
            case "is_vanished" -> Boolean.toString(plugin.vanishService().isVanished(uuid));
            case "is_frozen" -> Boolean.toString(plugin.freezeService().isFrozen(uuid));
            case "is_muted" -> Boolean.toString(plugin.punishmentCache()
                    .activeMutes(uuid, System.currentTimeMillis()).size() > 0);
            case "mute_remaining" -> {
                var mutes = plugin.punishmentCache().activeMutes(uuid, System.currentTimeMillis());
                if (mutes.isEmpty()) {
                    yield "0s";
                }
                var mute = mutes.get(0);
                yield mute.isPermanent() ? "permanent"
                        : io.github.aethelreborn.vsm.util.TimeParser.formatShort(
                        mute.remainingMillis(System.currentTimeMillis()));
            }
            case "warnings" -> Integer.toString(plugin.punishmentCache()
                    .activeWarnings(uuid, System.currentTimeMillis()).size());
            case "open_reports" -> Integer.toString(plugin.reportService().count(null, null));
            case "staff_online" -> Integer.toString(plugin.staffCache().onlineStaff(online ->
                    plugin.getServer().getPlayer(online) != null));
            case "active_punishments" -> Integer.toString(
                    plugin.punishmentCache().activeFor(uuid, System.currentTimeMillis()).size());
            case "is_banned" -> Boolean.toString(plugin.punishmentService()
                    .activeFor(uuid).stream().anyMatch(p -> p.type().isBan()));
            case "staffmode" -> Boolean.toString(plugin.staffModeService().isActive(uuid));
            case "has_alerts" -> Boolean.toString(plugin.staffState().alerts(uuid));
            default -> {
                if (key.startsWith("node_")) {
                    String node = key.substring("node_".length()).replace('_', '.');
                    yield Boolean.toString(plugin.permissionService().has(uuid, "vsm." + node));
                }
                yield null;
            }
        };
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Nodes exposed through {@code %vsm_node_x%}. */
    public static String nodeFor(Player player, String node) {
        return player != null && player.hasPermission(node) ? "true" : "false";
    }

    public static String staffNode() {
        return PermissionNodes.STAFF_CHAT;
    }
}