package io.github.aethelreborn.vsm;

import io.github.aethelreborn.vsm.command.CommandRegistrar;
import io.github.aethelreborn.vsm.api.VsmAPI;
import java.util.function.Consumer;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

/**
 * Paper bootstrapper (spec 4.1, 17.1).
 *
 * <p>Commands are registered here through {@link LifecycleEvents#COMMANDS} so they exist for datapack
 * command parsing as well. The command tree itself only captures lazy references, so building it before the
 * plugin instance exists is safe: every executor and suggestion provider resolves the singleton at call
 * time, long after {@code onEnable} has finished.
 */
public final class VsmBootstrap implements PluginBootstrap {

    private static volatile Consumer<Commands> commandRegistration = registrar -> {
    };

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                commandRegistration.accept(event.registrar()));
    }

    /** Installed by the main plugin before it enables. */
    public static void setCommandRegistration(Consumer<Commands> registration) {
        if (registration != null) {
            commandRegistration = registration;
        }
    }

    /** Convenience used by tests and the {@code /vsm debug} health screen. */
    public static boolean hasRegistration() {
        return commandRegistration != null;
    }

    static {
        // Guard against the plugin class being loaded before the registrar is installed.
        CommandRegistrar.touch();
    }
}