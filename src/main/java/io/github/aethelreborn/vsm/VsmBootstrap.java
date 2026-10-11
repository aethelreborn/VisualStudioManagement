package io.github.aethelreborn.vsm;

import io.github.aethelreborn.vsm.command.CommandRegistrar;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper bootstrapper (spec 4.1, 17.1).
 *
 * <p>The command tree is built here, but the handler is installed from {@link #install(JavaPlugin)} during
 * {@code onEnable} rather than from {@link #bootstrap(BootstrapContext)}.
 *
 * <p>Paper fires {@link LifecycleEvents#COMMANDS} with the owner filter {@code Plugin.class::isInstance}
 * ({@code LifecycleEventRunner#callReloadableRegistrarEvent}). A handler registered from a bootstrapper is
 * owned by the {@link BootstrapContext} instead of the {@code Plugin}, so it is filtered out and never runs;
 * it additionally sets {@code blocksPluginReloading}, which would break {@code /reload}. Registering through
 * the plugin's own lifecycle manager is the supported path and is what Paper's own test plugin does.
 *
 * <p>The tree only captures lazy references, so building it inside the handler is safe: every executor and
 * suggestion provider resolves the services at call time, long after {@code onEnable} has finished.
 */
public final class VsmBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        // Intentionally empty: see the class javadoc. The bootstrapper stays declared so the loader,
        // dependency classpath and plugin metadata are resolved exactly as the specification describes.
    }

    /**
     * Installs the command handler on the plugin's lifecycle manager.
     *
     * <p>Must be called from {@code onEnable}: Paper closes lifecycle registration as soon as {@code onEnable}
     * returns, and the {@code COMMANDS} event fires once the server has finished loading plugins.
     */
    public static void install(JavaPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                CommandRegistrar.register(event.registrar(), plugin));
    }

    /** Kept for tests and for {@link CommandRegistrar} callers that already hold a registrar. */
    public static void register(Commands registrar, JavaPlugin plugin) {
        CommandRegistrar.register(registrar, plugin);
    }
}