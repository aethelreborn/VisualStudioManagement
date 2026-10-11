package io.github.aethelreborn.vsm.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Wraps the server scheduler so no service has to know whether it is talking to Bukkit or Folia.
 *
 * <p>Paper is the supported platform today; the indirection exists so Folia support is a single class change
 * (spec 3.1) rather than a change across every service.
 */
public final class SchedulerAdapter {

    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs later on the main thread. */
    public void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Runs later on the main thread, even when already on it. */
    public void syncLater(Runnable task, long delayTicks, long periodTicks) {
        if (periodTicks > 0L) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public void syncTimer(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    /** Runs off the main thread. */
    public void async(Runnable task) {
        if (!Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void asyncLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
    }

    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    /** Completes the future on the main thread; the async work must already be finished. */
    public <T> CompletableFuture<T> completeOnMain(CompletableFuture<T> future, T value) {
        CompletableFuture<T> out = new CompletableFuture<>();
        sync(() -> out.complete(value));
        return out;
    }

    /** Runs {@code asyncWork} off thread then {@code then} on the main thread. */
    public <T> CompletableFuture<Void> supplyAsync(CompletableFuture<T> asyncWork, Consumer<T> then) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        asyncWork.whenComplete((value, throwable) -> {
            if (throwable != null) {
                done.completeExceptionally(throwable);
                return;
            }
            sync(() -> {
                then.accept(value);
                done.complete(null);
            });
        });
        return done;
    }

    public Plugin plugin() {
        return plugin;
    }
}