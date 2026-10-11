package io.github.aethelreborn.vsm.integration;

import io.github.aethelreborn.vsm.VisualStudioManagement;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI hook (spec 18.3).
 *
 * <p>PlaceholderAPI is a soft dependency, so the class that actually extends
 * {@code PlaceholderExpansion} lives in a nested type. That nested type is only linked once the caller has
 * confirmed PlaceholderAPI is installed, which is what keeps the plugin loadable on servers that do not
 * have it.
 */
public final class PlaceholderApiHook {

    private PlaceholderApiHook() {
    }

    /** Registers the expansion when PlaceholderAPI is present; never throws when it is absent. */
    public static void register(VisualStudioManagement plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            VsmExpansion.register(plugin);
            plugin.getLogger().info("Registered the PlaceholderAPI expansion.");
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not register the PlaceholderAPI expansion: "
                    + throwable.getMessage());
        }
    }

    /** Nodes exposed through {@code %vsm_node_x%}. */
    public static String nodeFor(Player player, String node) {
        return player != null && player.hasPermission(node) ? "true" : "false";
    }

    public static String staffNode() {
        return PermissionNodes.STAFF_CHAT;
    }

    /** Only ever loaded when PlaceholderAPI is on the classpath. */
    private static final class VsmExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {

        private static VisualStudioManagement plugin;

        static void register(VisualStudioManagement instance) {
            plugin = instance;
            new VsmExpansion().register();
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
            if (player == null || plugin == null) {
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
                case "is_muted" -> Boolean.toString(!plugin.punishmentCache()
                        .activeMutes(uuid, System.currentTimeMillis()).isEmpty());
                case "mute_remaining" -> remaining(uuid);
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

        private String remaining(UUID uuid) {
            var mutes = plugin.punishmentCache().activeMutes(uuid, System.currentTimeMillis());
            if (mutes.isEmpty()) {
                return "0s";
            }
            var mute = mutes.get(0);
            return mute.isPermanent() ? "permanent"
                    : io.github.aethelreborn.vsm.util.TimeParser.formatShort(mute.remainingMillis(
                    System.currentTimeMillis()));
        }

        private static String orEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}