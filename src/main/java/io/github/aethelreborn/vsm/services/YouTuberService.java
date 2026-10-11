package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import io.github.aethelreborn.vsm.util.TextUtil;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The YouTuber role (spec 6.2a, 8.8): a side branch with scoped powers only.
 *
 * <p>Everything here is deliberately limited: eligible targets, distances, cooldowns, concurrent freezes,
 * auto-release and the creative-mode safeguards. A holder of the matching full staff node is never subject
 * to these limits — those calls go through {@link ActionService} instead.
 */
public final class YouTuberService {

    private final VsmConfig config;
    private final PermissionService permissions;
    private final AuditService audit;
    private final MessageService messages;
    private final FreezeService freezes;
    private final TeleportService teleports;
    private final SchedulerAdapter scheduler;
    private final Logger logger;
    private final Path stateDirectory;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, CreativeState> creativeStates = new ConcurrentHashMap<>();

    /** Survival inventory saved while a YouTuber is in creative. */
    private static final class CreativeState {
        private final Map<Integer, String> contents = new HashMap<>();
        private String gamemode = "SURVIVAL";
        private int level;
        private float exp;
        private double health = 20;
        private int food = 20;
        private float saturation = 5;
    }

    public YouTuberService(VsmConfig config, PermissionService permissions, AuditService audit,
                           MessageService messages, FreezeService freezes, TeleportService teleports,
                           SchedulerAdapter scheduler, Logger logger, Path stateDirectory) {
        this.config = config;
        this.permissions = permissions;
        this.audit = audit;
        this.messages = messages;
        this.freezes = freezes;
        this.teleports = teleports;
        this.scheduler = scheduler;
        this.logger = logger;
        this.stateDirectory = stateDirectory;
    }

    // ------------------------------------------------------------ eligibility
    /** Target rules from spec 8.8: ordinary players only, never staff, YouTubers or yourself. */
    public boolean eligible(Player caller, Player target) {
        if (target == null || caller.getUniqueId().equals(target.getUniqueId())) {
            return false;
        }
        if (permissions.isStaffRank(target.getUniqueId())) {
            return false;
        }
        String targetRank = permissions.rankOf(target.getUniqueId());
        if (targetRank != null && targetRank.equalsIgnoreCase(config.youtuberRankId())) {
            return false; // never another YouTuber
        }
        if (permissions.has(target, PermissionNodes.FREEZE_EXEMPT)) {
            return false;
        }
        return true;
    }

    /** Vanished staff are never valid targets and never appear in tab-completion (spec 8.8). */
    public boolean targetAllowed(Player caller, Player target) {
        if (!eligible(caller, target)) {
            return false;
        }
        VanishService vanish = io.github.aethelreborn.vsm.VsmRuntime.vanishService();
        return vanish == null || !vanish.isVanished(target);
    }

    /** Cooldown gate shared by freeze, move and tp. */
    public boolean checkCooldown(Player caller, int seconds, String key) {
        if (seconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(caller.getUniqueId());
        if (last != null && now - last < seconds * 1000L) {
            messages.send(caller, "youtuber-cooldown", "seconds", Integer.toString(seconds));
            return false;
        }
        cooldowns.put(caller.getUniqueId(), now);
        return true;
    }

    public void setCooldown(Player caller, int seconds) {
        if (seconds > 0) {
            cooldowns.put(caller.getUniqueId(), System.currentTimeMillis());
        }
    }

    // ----------------------------------------------------------------- freeze
    /** Applies a YouTuber freeze with every scoped limit in place. */
    public boolean freeze(Player caller, Player target, String reason) {
        if (!targetAllowed(caller, target)) {
            messages.send(caller, "youtuber-target-not-eligible");
            return false;
        }
        if (!checkCooldown(caller, config.youtuberFreezeCooldownSeconds(), "freeze")) {
            return false;
        }
        int concurrent = 0;
        for (UUID uuid : freezes.frozenPlayers()) {
            FreezeService.FreezeRecord record = freezes.record(uuid);
            if (record != null && record.youTuberOwned()
                    && record.ownerScope().equals(FreezeService.YOUTUBER_SCOPE_PREFIX + caller.getUniqueId())) {
                concurrent++;
            }
        }
        if (concurrent >= config.youtuberFreezeMaxConcurrent()) {
            messages.send(caller, "youtuber-freeze-limit", "max", Integer.toString(config.youtuberFreezeMaxConcurrent()));
            return false;
        }
        long autoRelease = System.currentTimeMillis() + config.youtuberFreezeMaxDuration();
        boolean applied = freezes.freeze(target.getUniqueId(),
                reason == null || reason.isBlank() ? "Frozen by a creator" : reason,
                caller.getUniqueId(), caller.getName(),
                FreezeService.YOUTUBER_SCOPE_PREFIX + caller.getUniqueId(), autoRelease);
        if (applied) {
            messages.send(caller, "youtuber-freeze-ok", "target", target.getName());
        } else {
            messages.send(caller, "youtuber-target-not-eligible");
        }
        return applied;
    }

    /** Releases a YouTuber freeze; only their own freezes may be released. */
    public boolean unfreeze(Player caller, Player target) {
        if (!targetAllowed(caller, target)) {
            messages.send(caller, "youtuber-target-not-eligible");
            return false;
        }
        if (!freezes.releaseOwned(target.getUniqueId(), caller.getUniqueId())) {
            messages.send(caller, "youtuber-not-your-freeze");
            return false;
        }
        messages.send(caller, "youtuber-unfreeze-ok", "target", target.getName());
        return true;
    }

    /** {@code /vsm unfreeze all} releases only the caller's own freezes. */
    public int unfreezeAll(Player caller) {
        int released = freezes.releaseAllOwned(caller.getUniqueId());
        messages.send(caller, "youtuber-unfreeze-all", "count", Integer.toString(released));
        return released;
    }

    /** A player frozen by the same YouTuber may still be moved (spec 8.8). */
    public boolean movableBy(Player caller, Player target) {
        if (freezes.isFrozen(target.getUniqueId())) {
            FreezeService.FreezeRecord record = freezes.record(target.getUniqueId());
            if (record == null || !record.youTuberOwned()) {
                return false;
            }
            return record.ownerScope().equals(FreezeService.YOUTUBER_SCOPE_PREFIX + caller.getUniqueId());
        }
        return true;
    }

    /** Releases every freeze owned by a YouTuber when they leave (spec 14.1). */
    public void onQuit(Player caller) {
        if (config.youtuberReleaseWhenActorLeaves()) {
            freezes.releaseAllOwned(caller.getUniqueId());
        }
        if (config.youtuberSeparateInventories()) {
            CreativeState state = creativeStates.remove(caller.getUniqueId());
            if (state != null) {
                writeState(caller, state);
            }
        }
        cooldowns.remove(caller.getUniqueId());
    }

    // ------------------------------------------------------------------- move
    /** Moves a player within {@code youtuber.move.max-distance} of the caller. */
    public boolean move(Player caller, Player target, Location destination) {
        if (target == null || !targetAllowed(caller, target)) {
            messages.send(caller, "youtuber-target-not-eligible");
            return false;
        }
        if (!movableBy(caller, target)) {
            messages.send(caller, "youtuber-frozen-by-other");
            return false;
        }
        if (!config.youtuberMoveAllowOthers()) {
            messages.send(caller, "no-permission");
            return false;
        }
        if (!checkCooldown(caller, config.youtuberMoveCooldownSeconds(), "move")) {
            return false;
        }
        boolean crossWorld = !target.getWorld().equals(destination.getWorld());
        if (crossWorld && !config.youtuberMoveAllowCrossWorld()) {
            messages.send(caller, "youtuber-move-cross-world");
            return false;
        }
        if (crossWorld && !config.youtuberTpAllowCrossWorld()) {
            messages.send(caller, "youtuber-move-cross-world");
            return false;
        }
        for (String blacklisted : config.youtuberMoveWorldBlacklist()) {
            if (destination.getWorld() != null && blacklisted.equalsIgnoreCase(destination.getWorld().getName())) {
                messages.send(caller, "world-blacklisted", "world", destination.getWorld().getName());
                return false;
            }
        }
        double distance = target.getLocation().distance(destination);
        if (distance > config.youtuberMoveMaxDistance()) {
            messages.send(caller, "youtuber-move-too-far",
                    "max", io.github.aethelreborn.vsm.util.TimeParser.formatShort(
                            (long) (config.youtuberMoveMaxDistance() * 1000)));
            return false;
        }
        boolean moved = teleports.teleport(target, destination, "youtuber-move");
        if (moved) {
            audit.log(ActionSource.COMMAND, caller.getUniqueId(), caller.getName(), "YOUTUBER_MOVE_OTHER",
                    target.getUniqueId(), target.getName(), "");
            messages.send(caller, "youtuber-move-ok", "target", target.getName());
        } else {
            messages.send(caller, "tp-no-safe-location");
        }
        return moved;
    }

    // --------------------------------------------------------------------- tp
    public boolean teleportToPlayer(Player caller, Player target) {
        if (target == null || !targetAllowed(caller, target)) {
            messages.send(caller, "youtuber-target-not-eligible");
            return false;
        }
        if (!checkCooldown(caller, config.youtuberTpCooldownSeconds(), "tp")) {
            return false;
        }
        if (!config.youtuberTpAllowCrossWorld() && !target.getWorld().equals(caller.getWorld())) {
            messages.send(caller, "youtuber-move-cross-world");
            return false;
        }
        for (String blacklisted : config.youtuberTpWorldBlacklist()) {
            if (target.getWorld() != null && blacklisted.equalsIgnoreCase(target.getWorld().getName())) {
                messages.send(caller, "world-blacklisted", "world", target.getWorld().getName());
                return false;
            }
        }
        boolean moved = teleports.teleport(caller, target.getLocation(), "youtuber-tp");
        if (moved) {
            audit.log(ActionSource.COMMAND, caller.getUniqueId(), caller.getName(), "YOUTUBER_TP",
                    target.getUniqueId(), target.getName(), "");
            messages.send(caller, "youtuber-tp-ok", "target", target.getName());
        } else {
            messages.send(caller, "tp-no-safe-location");
        }
        return moved;
    }

    // --------------------------------------------------------------- gamemode
    /** Changes the caller's own game mode; spectator is never available to this role. */
    public boolean setGameMode(Player caller, GameMode mode) {
        if (mode == GameMode.SPECTATOR) {
            messages.send(caller, "youtuber-gamemode-not-allowed");
            return false;
        }
        String node = switch (mode) {
            case SURVIVAL -> PermissionNodes.YOUTUBER_GM_SURVIVAL;
            case ADVENTURE -> PermissionNodes.YOUTUBER_GM_ADVENTURE;
            case CREATIVE -> PermissionNodes.YOUTUBER_GM_CREATIVE;
            default -> null;
        };
        if (node == null || !permissions.has(caller, node)) {
            messages.send(caller, "youtuber-gamemode-not-allowed");
            return false;
        }
        if (!config.youtuberAllowedGameModes().contains(mode.name())) {
            messages.send(caller, "youtuber-gamemode-not-allowed");
            return false;
        }
        if (mode == GameMode.CREATIVE) {
            List<String> worlds = config.youtuberCreativeWorlds();
            if (!worlds.isEmpty() && !worlds.contains(caller.getWorld().getName())
                    && !permissions.has(caller, PermissionNodes.YOUTUBER_CREATIVE_WORLDS_BYPASS)) {
                messages.send(caller, "youtuber-creative-world-blocked");
                return false;
            }
        }
        if (!checkCooldown(caller, config.youtuberGameModeCooldownSeconds(), "gamemode")) {
            return false;
        }
        GameMode previous = caller.getGameMode();
        if (mode == GameMode.CREATIVE && previous != GameMode.CREATIVE) {
            saveSurvivalState(caller);
        } else if (previous == GameMode.CREATIVE && mode != GameMode.CREATIVE) {
            restoreSurvivalState(caller);
        }
        caller.setGameMode(mode);
        audit.log(ActionSource.COMMAND, caller.getUniqueId(), caller.getName(), "YOUTUBER_GAMEMODE",
                caller.getUniqueId(), caller.getName(), previous.name() + " -> " + mode.name());
        messages.send(caller, "youtuber-gamemode-changed", "mode", mode.name());
        return true;
    }

    /** {@code /vsm gamemode toggle} flips creative and survival; adventure switches to survival. */
    public boolean toggleGameMode(Player caller) {
        if (!permissions.has(caller, PermissionNodes.YOUTUBER_GM_TOGGLE)) {
            messages.send(caller, "youtuber-gamemode-not-allowed");
            return false;
        }
        GameMode target = caller.getGameMode() == GameMode.CREATIVE ? GameMode.SURVIVAL : GameMode.CREATIVE;
        return setGameMode(caller, target);
    }

    public boolean inCreative(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.getGameMode() == GameMode.CREATIVE;
    }

    /** Creative safeguards: item dropping and PvP are blocked when configured (spec 8.8). */
    public boolean blockItemDrop(Player player) {
        return config.creativeBlockItemDrop() && player.getGameMode() == GameMode.CREATIVE;
    }

    public boolean blockPvp(Player player) {
        return config.creativeBlockPvp() && player.getGameMode() == GameMode.CREATIVE;
    }

    public boolean blockContainerAccess(Player player) {
        return config.creativeBlockContainerAccess() && player.getGameMode() == GameMode.CREATIVE;
    }

    private void saveSurvivalState(Player player) {
        if (!config.youtuberSeparateInventories()) {
            return;
        }
        CreativeState state = new CreativeState();
        state.gamemode = player.getGameMode().name();
        state.level = player.getLevel();
        state.exp = player.getExp();
        state.food = player.getFoodLevel();
        state.saturation = player.getSaturation();
        var health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        state.health = health == null ? 20.0D : health.getValue();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                state.contents.put(slot, StaffModeService.SerializationHelper.serializeItem(item));
            }
        }
        state.contents.put(-1, inventory.getItemInOffHand().getType().name());
        creativeStates.put(player.getUniqueId(), state);
        writeState(player, state);
    }

    private void restoreSurvivalState(Player player) {
        CreativeState state = creativeStates.remove(player.getUniqueId());
        if (state == null || !config.youtuberSeparateInventories()) {
            return;
        }
        var inventory = player.getInventory();
        inventory.clear();
        for (var entry : state.contents.entrySet()) {
            if (entry.getKey() < 0) {
                continue;
            }
            ItemStack item = StaffModeService.SerializationHelper.deserializeItem(entry.getValue());
            if (item != null) {
                inventory.setItem(entry.getKey(), item);
            }
        }
        player.setLevel(state.level);
        player.setExp(state.exp);
        player.setFoodLevel(state.food);
        player.setSaturation(state.saturation);
        var health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(state.health);
        }
        deleteStateFile(player.getUniqueId());
    }

    private Path stateFile(UUID uuid) {
        return stateDirectory.resolve("youtuber-creative-" + uuid + ".json");
    }

    private void writeState(Player player, CreativeState state) {
        scheduler.async(() -> {
            try {
                Files.createDirectories(stateDirectory);
                StringBuilder items = new StringBuilder();
                for (var entry : state.contents.entrySet()) {
                    items.append('"').append(entry.getKey()).append("\":\"")
                            .append(entry.getValue().replace("\"", "")).append("\",");
                }
                String json = "{" + io.github.aethelreborn.vsm.util.Json.quote("gamemode") + ":\""
                        + state.gamemode + "\"," + io.github.aethelreborn.vsm.util.Json.quote("level") + ":"
                        + state.level + "," + io.github.aethelreborn.vsm.util.Json.quote("exp") + ":"
                        + state.exp + "," + io.github.aethelreborn.vsm.util.Json.quote("items") + "{"
                        + items + "}}";
                Files.writeString(stateFile(player.getUniqueId()), json, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                logger.warning("Could not save YouTuber creative state: " + ex.getMessage());
            }
        });
    }

    private void deleteStateFile(UUID uuid) {
        scheduler.async(() -> {
            try {
                Files.deleteIfExists(stateFile(uuid));
            } catch (IOException ex) {
                logger.fine("Could not delete YouTuber creative state: " + ex.getMessage());
            }
        });
    }

    /** Restores anybody left in creative after a crash (spec 8.8, acceptance tests). */
    public void onJoin(Player player) {
        GameMode reset = parseGameMode(config.youtuberResetOnQuit());
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        Path file = stateFile(player.getUniqueId());
        if (Files.exists(file)) {
            CreativeState state = creativeStates.computeIfAbsent(player.getUniqueId(), key -> new CreativeState());
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Files.deleteIfExists(file);
                loadCreativeItems(player, json, state);
            } catch (IOException ex) {
                logger.warning("Could not restore YouTuber creative state: " + ex.getMessage());
            }
            player.setGameMode(reset);
            messages.send(player, "youtuber-gamemode-changed", "mode", reset.name());
            return;
        }
        player.setGameMode(reset);
    }

    private void loadCreativeItems(Player player, String json, CreativeState state) {
        int cursor = json.indexOf("\"" + "items" + "\":");
        if (cursor < 0) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"(-?\\d+)\":\"([^\"]*)\"").matcher(json.substring(cursor));
        while (matcher.find()) {
            int slot = Integer.parseInt(matcher.group(1));
            String base64 = matcher.group(2);
            if (slot < 0) {
                continue;
            }
            ItemStack item = StaffModeService.SerializationHelper.deserializeItem(base64);
            if (item != null) {
                player.getInventory().setItem(slot, item);
            }
            state.contents.put(slot, base64);
        }
        player.getInventory().clear();
        for (var entry : state.contents.entrySet()) {
            if (entry.getKey() < 0) {
                continue;
            }
            ItemStack item = StaffModeService.SerializationHelper.deserializeItem(entry.getValue());
            if (item != null) {
                player.getInventory().setItem(entry.getKey(), item);
            }
        }
    }

    private static GameMode parseGameMode(String raw) {
        try {
            return GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return GameMode.SURVIVAL;
        }
    }

    /** Restores all state on disable (spec 4.3). */
    public void releaseAll() {
        for (UUID uuid : new ArrayList<>(creativeStates.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setGameMode(parseGameMode(config.youtuberResetOnQuit()));
            }
        }
        creativeStates.clear();
        cooldowns.clear();
    }

    public String relativeDistance(double blocks) {
        return TimeParser.formatShort((long) (blocks * 1000));
    }

    /** Debug helper. */
    public Map<String, Integer> statistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("creativeStates", creativeStates.size());
        stats.put("cooldowns", cooldowns.size());
        return stats;
    }

    public String sanitize(String text) {
        return TextUtil.sanitise(text, 120);
    }

    /** All world names offered in tab-completion. */
    public List<String> worlds() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }
}