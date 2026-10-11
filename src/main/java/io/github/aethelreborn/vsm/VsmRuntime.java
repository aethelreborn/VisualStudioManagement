package io.github.aethelreborn.vsm;

import io.github.aethelreborn.vsm.services.FreezeService;
import io.github.aethelreborn.vsm.services.PunishmentService;
import io.github.aethelreborn.vsm.services.VanishService;
import io.github.aethelreborn.vsm.services.YouTuberService;
import io.github.aethelreborn.vsm.storage.cache.StaffCache;
import org.bukkit.Bukkit;

/**
 * Static handle to the running plugin instance.
 *
 * <p>The command tree is built during the Paper bootstrap phase, before the plugin object exists, so
 * executors and suggestion providers resolve the instance here at call time instead of capturing it.
 */
public final class VsmRuntime {

    private static volatile VisualStudioManagement instance;

    private VsmRuntime() {
    }

    public static void set(VisualStudioManagement plugin) {
        instance = plugin;
    }

    public static void clear() {
        instance = null;
    }

    /** @return the running plugin, or {@code null} when it is not enabled yet. */
    public static VisualStudioManagement get() {
        return instance;
    }

    /** @return the running plugin, or the server when the plugin is still starting. */
    public static VisualStudioManagement require() {
        VisualStudioManagement plugin = instance;
        return plugin != null ? plugin : null;
    }

    /** @return {@code true} when the plugin is enabled and its services are ready. */
    public static boolean ready() {
        VisualStudioManagement plugin = instance;
        return plugin != null && plugin.isEnabled();
    }

    public static FreezeService freezeService() {
        VisualStudioManagement plugin = instance;
        return plugin == null ? null : plugin.freezeService();
    }

    public static VanishService vanishService() {
        VisualStudioManagement plugin = instance;
        return plugin == null ? null : plugin.vanishService();
    }

    public static YouTuberService youTuberService() {
        VisualStudioManagement plugin = instance;
        return plugin == null ? null : plugin.youTuberService();
    }

    public static PunishmentService punishmentService() {
        VisualStudioManagement plugin = instance;
        return plugin == null ? null : plugin.punishmentService();
    }

    public static StaffCache staffCache() {
        VisualStudioManagement plugin = instance;
        return plugin == null ? null : plugin.staffCache();
    }

    /** Runs {@code task} on the main thread through the plugin scheduler. */
    public static void sync(Runnable task) {
        VisualStudioManagement plugin = instance;
        if (plugin == null) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            }
            return;
        }
        plugin.scheduler().sync(task);
    }
}