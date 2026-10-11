package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.SavedLocation;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Every teleport goes through here (spec 8.4): the previous location is recorded for {@code /back}, the
 * destination is made safe, world blacklists are honoured, staff actions are audited and frozen staff
 * cannot teleport.
 */
public final class TeleportService {

    private static final int MAX_SEARCH = 32;

    private final VsmConfig config;
    private final StorageProvider storage;
    private final SchedulerAdapter scheduler;
    private final AuditService audit;
    private final MessageService messages;
    private final PermissionService permissions;

    private final Map<UUID, Deque<Location>> history = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, UUID> followTargets = new HashMap<>();
    private volatile FreezeService freezes;

    public TeleportService(VsmConfig config, StorageProvider storage, SchedulerAdapter scheduler,
                           AuditService audit, MessageService messages, PermissionService permissions) {
        this.config = config;
        this.storage = storage;
        this.scheduler = scheduler;
        this.audit = audit;
        this.messages = messages;
        this.permissions = permissions;
    }

    public void freezes(FreezeService freezes) {
        this.freezes = freezes;
    }

    /** @return {@code true} when the teleport was blocked (frozen, blacklisted world, cooldown). */
    public boolean blocked(Player player, String messageKey) {
        if (freezes != null && freezes.isFrozen(player.getUniqueId())) {
            messages.send(player, "frozen-cannot-teleport");
            return true;
        }
        if (isWorldBlacklisted(player.getWorld().getName())) {
            messages.send(player, messageKey);
            return true;
        }
        return false;
    }

    public boolean isWorldBlacklisted(String worldName) {
        for (String blacklisted : config.teleportWorldBlacklist()) {
            if (blacklisted.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    /** Records the current position so {@code /back} can return to it. */
    public void remember(Player player, String cause) {
        Deque<Location> deque = history.computeIfAbsent(player.getUniqueId(),
                key -> new ArrayDeque<>());
        deque.push(player.getLocation().clone());
        while (deque.size() > config.backHistory()) {
            deque.pollLast();
        }
        storage.saveLocation(new SavedLocation(player.getUniqueId(), SavedLocation.KIND_BACK,
                player.getWorld().getName(), player.getLocation().getX(), player.getLocation().getY(),
                player.getLocation().getZ(), player.getLocation().getYaw(), player.getLocation().getPitch(),
                System.currentTimeMillis(), cause == null ? "manual" : cause));
    }

    /** Saves the logout location so {@code /tpoffline} can use it. */
    public void saveLogout(Player player) {
        storage.saveLocation(new SavedLocation(player.getUniqueId(), SavedLocation.KIND_LOGOUT,
                player.getWorld().getName(), player.getLocation().getX(), player.getLocation().getY(),
                player.getLocation().getZ(), player.getLocation().getYaw(), player.getLocation().getPitch(),
                System.currentTimeMillis(), "logout"));
    }

    public void clearHistory(UUID uuid) {
        history.remove(uuid);
        cooldowns.remove(uuid);
        followTargets.remove(uuid);
    }

    /** Teleports with a safe destination search and records the previous location. */
    public boolean teleport(Player player, Location destination, String cause) {
        Location safe = safe(destination);
        if (safe == null) {
            messages.send(player, "tp-no-safe-location");
            return false;
        }
        remember(player, cause);
        return player.teleportAsync(safe).thenApply(success -> {
            if (!success) {
                messages.send(player, "tp-failed");
            }
            return success;
        }).exceptionally(throwable -> {
            messages.send(player, "tp-failed");
            return false;
        }).join();
    }

    /** Finds a safe spot: not inside a block, not in lava, not in the void. */
    public Location safe(Location desired) {
        if (desired == null || desired.getWorld() == null) {
            return null;
        }
        if (!config.safeTeleport()) {
            return desired;
        }
        Location candidate = desired.clone();
        if (isSafe(candidate)) {
            return candidate;
        }
        for (int offset = 1; offset <= MAX_SEARCH; offset++) {
            Location up = candidate.clone().add(0, offset, 0);
            if (isSafe(up)) {
                return up;
            }
            Location down = candidate.clone().subtract(0, offset, 0);
            if (down.getY() > desired.getWorld().getMinHeight()) {
                if (isSafe(down)) {
                    return down;
                }
            }
        }
        return null;
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        if (location.getY() < world.getMinHeight() + 1 || location.getY() > world.getMaxHeight() - 1) {
            return false;
        }
        Block feet = location.getBlock();
        if (!feet.isPassable()) {
            return false;
        }
        Block head = feet.getRelative(0, 1, 0);
        if (!head.isPassable()) {
            return false;
        }
        Block ground = feet.getRelative(0, -1, 0);
        if (ground.isEmpty()) {
            return false;
        }
        String material = ground.getType().name();
        return !material.contains("LAVA") && !material.contains("MAGMA") && !material.contains("FIRE");
    }

    /** {@code /back} — returns to the previous in-memory location or the last saved one. */
    public boolean back(Player player) {
        Deque<Location> deque = history.get(player.getUniqueId());
        if (deque != null && !deque.isEmpty()) {
            Location previous = deque.poll();
            return teleportRaw(player, previous, "back");
        }
        SavedLocation saved = storage.location(player.getUniqueId(), SavedLocation.KIND_BACK).join().orElse(null);
        if (saved == null) {
            messages.send(player, "back-no-location");
            return false;
        }
        World world = Bukkit.getWorld(saved.world());
        if (world == null) {
            messages.send(player, "back-no-location");
            return false;
        }
        return teleportRaw(player, new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch()),
                "back");
    }

    private boolean teleportRaw(Player player, Location destination, String cause) {
        Location safe = safe(destination);
        if (safe == null) {
            messages.send(player, "tp-no-safe-location");
            return false;
        }
        return player.teleportAsync(safe).exceptionally(throwable -> {
            messages.send(player, "tp-failed");
            return false;
        }).join();
    }

    /** Teleport to a coordinate, parsing {@code ~} relative values. */
    public boolean teleportToCoords(Player player, String xRaw, String yRaw, String zRaw, String worldName) {
        Location base = player.getLocation();
        Double x = parseCoordinate(xRaw, base.getX());
        Double y = parseCoordinate(yRaw, base.getY());
        Double z = parseCoordinate(zRaw, base.getZ());
        if (x == null || y == null || z == null) {
            messages.send(player, "invalid-coordinates");
            return false;
        }
        World world = base.getWorld();
        if (worldName != null && !worldName.isBlank()) {
            World target = Bukkit.getWorld(worldName);
            if (target == null) {
                messages.send(player, "world-not-found", "world", worldName);
                return false;
            }
            world = target;
        }
        if (isWorldBlacklisted(world.getName())) {
            messages.send(player, "world-blacklisted", "world", world.getName());
            return false;
        }
        return teleport(player, new Location(world, x, y, z), "coords");
    }

    private Double parseCoordinate(String raw, double base) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        try {
            if (text.equals("~")) {
                return base;
            }
            if (text.startsWith("~")) {
                return base + Double.parseDouble(text.substring(1));
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Moves a player to the highest safe block above them ({@code /top}). */
    public boolean top(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int y = world.getMaxHeight() - 1;
        while (y > world.getMinHeight()) {
            Block block = world.getBlockAt(x, y, z);
            if (block.isPassable() && world.getBlockAt(x, y + 1, z).isPassable()) {
                break;
            }
            y--;
        }
        return teleport(player, new Location(world, x + 0.5D, y + 1, z + 0.5D), "top");
    }

    /** Teleport to an offline player's last logout location ({@code /tpoffline}). */
    public boolean toOffline(Player actor, UUID targetUuid, String targetName) {
        SavedLocation saved = storage.location(targetUuid, SavedLocation.KIND_LOGOUT).join().orElse(null);
        if (saved == null) {
            messages.send(actor, "tpoffline-no-location", "target", targetName);
            return false;
        }
        World world = Bukkit.getWorld(saved.world());
        if (world == null) {
            messages.send(actor, "tpoffline-no-location", "target", targetName);
            return false;
        }
        audit.log(ActionSource.COMMAND, actor.getUniqueId(), actor.getName(), "TELEPORT_OFFLINE",
                targetUuid, targetName, saved.world());
        return teleport(actor, new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch()),
                "tpoffline");
    }

    /** Enforces the staff teleport cooldown; staff are exempt by default. */
    public boolean checkCooldown(Player player) {
        int seconds = config.teleportCooldownSeconds();
        if (seconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < seconds * 1000L) {
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }

    // -------------------------------------------------------------- follow
    public void startFollow(Player follower, Player target) {
        followTargets.put(follower.getUniqueId(), target.getUniqueId());
        messages.send(follower, "follow-started", "target", target.getName());
    }

    public void stopFollow(Player follower) {
        if (followTargets.remove(follower.getUniqueId()) != null) {
            messages.send(follower, "follow-stopped");
        }
    }

    public boolean following(Player follower) {
        return followTargets.containsKey(follower.getUniqueId());
    }

    /** Re-teleports the follower behind the target; called by the movement task. */
    public void tickFollow() {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(followTargets.entrySet())) {
            Player follower = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(entry.getValue());
            if (follower == null || target == null) {
                followTargets.remove(entry.getKey());
                continue;
            }
            Vector back = target.getLocation().getDirection().normalize().multiply(-2.0D);
            Location destination = target.getLocation().add(back);
            Location safe = safe(destination);
            if (safe != null) {
                follower.teleportAsync(safe);
            }
        }
    }

    /** Drops every in-memory entry on disable. */
    public void shutdown() {
        history.clear();
        cooldowns.clear();
        followTargets.clear();
    }

    public List<UUID> followers() {
        return new ArrayList<>(followTargets.keySet());
    }

    public boolean isFollowingWorldBlacklisted(Player follower) {
        UUID target = followTargets.get(follower.getUniqueId());
        if (target == null) {
            return false;
        }
        Player targetPlayer = Bukkit.getPlayer(target);
        return targetPlayer != null && isWorldBlacklisted(targetPlayer.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public Entity nearest(Player player, double radius) {
        Entity nearest = null;
        double nearestDistance = radius * radius;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    public void runAsync(Runnable task) {
        scheduler.async(task);
    }

    public void runSync(Runnable task) {
        scheduler.sync(task);
    }

    public PermissionService permissions() {
        return permissions;
    }
}