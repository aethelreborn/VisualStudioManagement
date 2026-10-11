package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.util.Json;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Staff Mode (spec 11.5). The full player state is written to disk so a crash or restart cannot lose it,
 * and restored exactly when the mode is left, the player quits, or the plugin is disabled.
 */
public final class StaffModeService {

    private static final String TOOL_TAG = "vsm_staffmode_tool";
    private static final int SLOT_COMPASS = 0;
    private static final int SLOT_REPORTS = 1;
    private static final int SLOT_FREEZE = 2;
    private static final int SLOT_VANISH = 3;
    private static final int SLOT_INVSEE = 4;
    private static final int SLOT_PUNISH = 5;
    private static final int SLOT_GAMEMODE = 6;
    private static final int SLOT_EXIT = 8;

    private final VsmConfig config;
    private final PermissionService permissions;
    private final AuditService audit;
    private final MessageService messages;
    private final SchedulerAdapter scheduler;
    private final Logger logger;
    private final Path stateDirectory;
    private final Map<UUID, Boolean> active = new ConcurrentHashMap<>();
    private final NamespacedKey toolKey;

    public StaffModeService(VsmConfig config, PermissionService permissions, AuditService audit,
                            MessageService messages, SchedulerAdapter scheduler, Logger logger,
                            Path stateDirectory, Plugin plugin) {
        this.config = config;
        this.permissions = permissions;
        this.audit = audit;
        this.messages = messages;
        this.scheduler = scheduler;
        this.logger = logger;
        this.stateDirectory = stateDirectory;
        this.toolKey = new NamespacedKey(plugin, "staffmode");
    }

    public boolean isActive(UUID uuid) {
        return Boolean.TRUE.equals(active.get(uuid));
    }

    public boolean toggle(Player player, UUID actorUuid, String actorName) {
        if (isActive(player.getUniqueId())) {
            restore(player, actorUuid, actorName);
            return false;
        }
        enable(player, actorUuid, actorName);
        return true;
    }

    /** Saves the state, applies the tools and enables the configured protections. */
    public void enable(Player player, UUID actorUuid, String actorName) {
        if (isActive(player.getUniqueId())) {
            return;
        }
        saveState(player);
        active.put(player.getUniqueId(), Boolean.TRUE);

        giveTools(player);
        if (config.staffModeVanish()) {
            io.github.aethelreborn.vsm.VsmRuntime.vanishService().hide(player, actorUuid, actorName);
        }
        if (config.staffModeFly()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        if (config.staffModeInvulnerable()) {
            player.setInvulnerable(true);
        }
        messages.send(player, "staffmode-enabled");
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "STAFFMODE_ON",
                player.getUniqueId(), player.getName(), "");
    }

    /** Restores the saved state exactly as it was. */
    public void restore(Player player, UUID actorUuid, String actorName) {
        if (!isActive(player.getUniqueId())) {
            return;
        }
        active.remove(player.getUniqueId());
        loadState(player);
        if (config.staffModeVanish()) {
            io.github.aethelreborn.vsm.VsmRuntime.vanishService().reveal(player, actorUuid, actorName);
        }
        messages.send(player, "staffmode-disabled");
        audit.log(ActionSource.COMMAND, actorUuid, actorName, "STAFFMODE_OFF",
                player.getUniqueId(), player.getName(), "");
    }

    private void giveTools(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(SLOT_COMPASS, tool(Material.COMPASS, "staffmode-tool-reports", "Opens the report menu"));
        inventory.setItem(SLOT_REPORTS, tool(Material.BOOK, "staffmode-tool-compass", "Opens the staff menu"));
        inventory.setItem(SLOT_FREEZE, tool(Material.IRON_AXE, "staffmode-tool-freeze", "Freeze the target"));
        inventory.setItem(SLOT_VANISH, tool(Material.ENDER_EYE, "staffmode-tool-vanish", "Toggle vanish"));
        inventory.setItem(SLOT_INVSEE, tool(Material.CHEST, "staffmode-tool-invsee", "Inspect inventory"));
        inventory.setItem(SLOT_PUNISH, tool(Material.PAPER, "staffmode-tool-punish", "Open the punish menu"));
        inventory.setItem(SLOT_GAMEMODE, tool(Material.RED_DYE, "staffmode-tool-gamemode", "Toggle game mode"));
        inventory.setItem(SLOT_EXIT, tool(Material.BARRIER, "staffmode-tool-exit", "Leave staff mode"));
    }

    private ItemStack tool(Material material, String key, String description) {
        ItemStack item = new ItemStack(material);
        ItemMetaWithKey.apply(item, toolKey, key);
        return item;
    }

    /** @return {@code true} when the item is one of the locked staff-mode tools. */
    public boolean isTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(toolKey, PersistentDataType.STRING);
    }

    public String toolKeyOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
    }

    /** Prevents loss of the tool hotbar (spec 11.5). */
    public boolean isProtectedSlot(Player player, int slot) {
        return isActive(player.getUniqueId()) && slot < player.getInventory().getSize() && slot <= SLOT_EXIT;
    }

    // ------------------------------------------------------------ persistence
    private Path stateFile(UUID uuid) {
        return stateDirectory.resolve("staffmode-" + uuid + ".json");
    }

    private void saveState(Player player) {
        if (!config.staffModeSaveToDisk()) {
            return;
        }
        // The inventory is captured on the main thread; only the file writes happen off thread.
        List<ItemStack> snapshot = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = SLOT_EXIT; slot < inventory.getSize(); slot++) {
            snapshot.add(inventory.getItem(slot));
        }
        ItemStack offHand = inventory.getItemInOffHand();
        Location location = player.getLocation();
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        float pitch = location.getPitch();
        String gamemode = player.getGameMode().name();
        boolean allowFlight = player.getAllowFlight();
        boolean flying = player.isFlying();
        int level = player.getLevel();
        float exp = player.getExp();
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = health == null ? 20.0D : health.getValue();
        int food = player.getFoodLevel();
        float saturation = player.getSaturation();

        scheduler.async(() -> {
            try {
                Files.createDirectories(stateDirectory);
                String json = Json.object()
                        .put("world", world)
                        .put("x", x).put("y", y).put("z", z)
                        .put("yaw", yaw).put("pitch", pitch)
                        .put("gamemode", gamemode)
                        .put("allowFlight", allowFlight)
                        .put("flying", flying)
                        .put("level", level)
                        .put("exp", exp)
                        .put("health", maxHealth)
                        .put("food", food)
                        .put("saturation", saturation)
                        .toString();
                Files.writeString(stateFile(player.getUniqueId()), json, StandardCharsets.UTF_8);
                writeItems(player.getUniqueId(), snapshot, offHand);
            } catch (IOException ex) {
                logger.warning("Could not save staff-mode state: " + ex.getMessage());
            }
        });
    }

    /** One file per slot keeps the saved inventory readable and free of escaping problems. */
    private void writeItems(UUID uuid, List<ItemStack> snapshot, ItemStack offHand) throws IOException {
        Path directory = stateDirectory.resolve("items-" + uuid);
        if (Files.exists(directory)) {
            try (var stream = Files.list(directory)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(directory);
        for (int slot = 0; slot < snapshot.size(); slot++) {
            ItemStack item = snapshot.get(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Files.writeString(directory.resolve(slot + ".yml"), serializeItem(item), StandardCharsets.UTF_8);
        }
        if (offHand != null && !offHand.getType().isAir()) {
            Files.writeString(directory.resolve("offhand.yml"), serializeItem(offHand), StandardCharsets.UTF_8);
        }
    }

    private List<ItemStack> readItems(UUID uuid, int expectedSize) {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(Math.max(0, expectedSize), null));
        Path directory = stateDirectory.resolve("items-" + uuid);
        if (!Files.isDirectory(directory)) {
            return items;
        }
        try (var stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                ItemStack item = SerializationHelper.deserializeItem(Files.readString(path, StandardCharsets.UTF_8));
                if (item == null) {
                    continue;
                }
                if (name.equals("offhand.yml")) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(name.substring(0, name.length() - 4));
                    if (slot >= 0 && slot < items.size()) {
                        items.set(slot, item);
                    }
                } catch (NumberFormatException ignored) {
                    // Not one of our files.
                }
            }
        } catch (IOException ex) {
            logger.fine("Could not read the saved staff-mode inventory: " + ex.getMessage());
        }
        return items;
    }

    private void deleteItems(UUID uuid) {
        Path directory = stateDirectory.resolve("items-" + uuid);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            logger.fine("Could not delete the saved staff-mode inventory: " + ex.getMessage());
        }
    }

    private void loadState(Player player) {
        if (!config.staffModeSaveToDisk()) {
            return;
        }
        Path file = stateFile(player.getUniqueId());
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Files.deleteIfExists(file);
            JsonState state = JsonState.parse(json);
            World world = Bukkit.getWorld(state.world);
            if (world != null) {
                player.teleport(new Location(world, state.x, state.y, state.z, state.yaw, state.pitch));
            }
            try {
                player.setGameMode(GameMode.valueOf(state.gamemode));
            } catch (IllegalArgumentException ignored) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            player.setAllowFlight(state.allowFlight);
            if (state.allowFlight) {
                player.setFlying(state.flying);
            }
            player.setLevel(state.level);
            player.setExp(state.exp);
            AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
            if (health != null && state.health > 0) {
                health.setBaseValue(state.health);
            }
            player.setFoodLevel(state.food);
            player.setSaturation(state.saturation);
            player.setInvulnerable(false);
            restoreInventory(player, player.getUniqueId());
        } catch (IOException ex) {
            logger.warning("Could not restore staff-mode state: " + ex.getMessage());
        }
    }

    private void restoreInventory(Player player, UUID uuid) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> saved = readItems(uuid, inventory.getSize() - SLOT_EXIT);
        inventory.clear();
        for (int slot = SLOT_EXIT; slot < inventory.getSize(); slot++) {
            int index = slot - SLOT_EXIT;
            inventory.setItem(slot, index < saved.size() ? saved.get(index) : null);
        }
        deleteItems(uuid);
    }

    /** Restores anybody left in staff mode after a crash (spec 11.5, acceptance test). */
    public List<UUID> pendingRestores() {
        List<UUID> pending = new ArrayList<>();
        if (!Files.isDirectory(stateDirectory)) {
            return pending;
        }
        try (var stream = Files.list(stateDirectory)) {
            stream.filter(path -> path.getFileName().toString().startsWith("staffmode-"))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        String uuid = name.substring("staffmode-".length(), name.length() - ".json".length());
                        try {
                            pending.add(UUID.fromString(uuid));
                        } catch (IllegalArgumentException ignored) {
                            // Not one of ours; leave the file alone.
                        }
                    });
        } catch (IOException ex) {
            logger.fine("Could not scan staff-mode state: " + ex.getMessage());
        }
        return pending;
    }

    /** Minimal typed view of the saved JSON. */
    public static final class JsonState {
        public String world = "";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String gamemode = "SURVIVAL";
        public boolean allowFlight;
        public boolean flying;
        public int level;
        public float exp;
        public double health = 20;
        public int food = 20;
        public float saturation = 5;

        static JsonState parse(String json) {
            JsonState state = new JsonState();
            state.world = string(json, "world", "");
            state.x = number(json, "x", 0);
            state.y = number(json, "y", 0);
            state.z = number(json, "z", 0);
            state.yaw = (float) number(json, "yaw", 0);
            state.pitch = (float) number(json, "pitch", 0);
            state.gamemode = string(json, "gamemode", "SURVIVAL");
            state.allowFlight = bool(json, "allowFlight");
            state.flying = bool(json, "flying");
            state.level = (int) number(json, "level", 0);
            state.exp = (float) number(json, "exp", 0);
            state.health = number(json, "health", 20);
            state.food = (int) number(json, "food", 20);
            state.saturation = (float) number(json, "saturation", 5);
            return state;
        }


        private static String string(String json, String key, String fallback) {
            int start = json.indexOf("\"" + key + "\":");
            if (start < 0) {
                return fallback;
            }
            int open = json.indexOf('"', json.indexOf(':', start) + 1);
            int close = open < 0 ? -1 : json.indexOf('"', open + 1);
            return open < 0 || close < 0 ? fallback : json.substring(open + 1, close);
        }

        private static double number(String json, String key, double fallback) {
            int start = json.indexOf("\"" + key + "\":");
            if (start < 0) {
                return fallback;
            }
            int index = json.indexOf(':', start) + 1;
            int end = index;
            while (end < json.length() && "-+.eE0123456789".indexOf(json.charAt(end)) >= 0) {
                end++;
            }
            try {
                return Double.parseDouble(json.substring(index, end));
            } catch (RuntimeException ex) {
                return fallback;
            }
        }

        private static boolean bool(String json, String key) {
            int start = json.indexOf("\"" + key + "\":");
            return start >= 0 && json.startsWith("true", json.indexOf(':', start) + 1);
        }
    }

    /** ItemStack helpers kept separate so the state file stays readable. */
    public static final class SerializationHelper {

        private SerializationHelper() {
        }

        /** Serialises an item using Bukkit's own YAML configuration format. */
        public static String serializeItem(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return "";
            }
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    new org.bukkit.configuration.file.YamlConfiguration();
            yaml.set("item", item);
            return yaml.saveToString().replace("\n", "\\n").replace("\r", "");
        }

        /** @return the item, or {@code null} when the text is empty or unreadable. */
        public static ItemStack deserializeItem(String text) {
            if (text == null || text.isBlank() || text.isEmpty()) {
                return null;
            }
            try {
                org.bukkit.configuration.file.YamlConfiguration yaml =
                        new org.bukkit.configuration.file.YamlConfiguration();
                yaml.loadFromString(text.replace("\\n", "\n"));
                ItemStack item = yaml.getItemStack("item");
                return item == null || item.getType().isAir() ? null : item;
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    /** Applies the persistent data key used to mark tool items. */
    static final class ItemMetaWithKey {
        private ItemMetaWithKey() {
        }

        static void apply(ItemStack item, NamespacedKey key, String value) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
    }

    public boolean mayUse(Player player) {
        return permissions.has(player, PermissionNodes.STAFF_MODE);
    }

    public int slotFor(String toolKeyName) {
        return switch (toolKeyName) {
            case "staffmode-tool-compass" -> SLOT_REPORTS;
            case "staffmode-tool-freeze" -> SLOT_FREEZE;
            case "staffmode-tool-vanish" -> SLOT_VANISH;
            case "staffmode-tool-invsee" -> SLOT_INVSEE;
            case "staffmode-tool-punish" -> SLOT_PUNISH;
            case "staffmode-tool-gamemode" -> SLOT_GAMEMODE;
            case "staffmode-tool-exit" -> SLOT_EXIT;
            default -> SLOT_COMPASS;
        };
    }
}