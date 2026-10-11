package io.github.aethelreborn.vsm;

import io.github.aethelreborn.vsm.api.VsmAPI;
import io.github.aethelreborn.vsm.command.CommandRegistrar;
import io.github.aethelreborn.vsm.config.AutoModConfig;
import io.github.aethelreborn.vsm.config.Messages;
import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.RankRegistry;
import io.github.aethelreborn.vsm.config.ReasonRegistry;
import io.github.aethelreborn.vsm.config.TemplateRegistry;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.gui.framework.MenuListener;
import io.github.aethelreborn.vsm.integration.PlaceholderApiHook;
import io.github.aethelreborn.vsm.listeners.AutoModListener;
import io.github.aethelreborn.vsm.listeners.ChatListener;
import io.github.aethelreborn.vsm.listeners.CommandListener;
import io.github.aethelreborn.vsm.listeners.DamageListener;
import io.github.aethelreborn.vsm.listeners.InventoryListener;
import io.github.aethelreborn.vsm.listeners.JoinListener;
import io.github.aethelreborn.vsm.listeners.MoveListener;
import io.github.aethelreborn.vsm.listeners.PreLoginListener;
import io.github.aethelreborn.vsm.listeners.QuitListener;
import io.github.aethelreborn.vsm.listeners.ServerPingListener;
import io.github.aethelreborn.vsm.listeners.TeleportListener;
import io.github.aethelreborn.vsm.services.ActionService;
import io.github.aethelreborn.vsm.services.AutoModService;
import io.github.aethelreborn.vsm.services.ChatControlService;
import io.github.aethelreborn.vsm.services.FreezeService;
import io.github.aethelreborn.vsm.services.IpService;
import io.github.aethelreborn.vsm.services.InvseeSession;
import io.github.aethelreborn.vsm.services.LockdownService;
import io.github.aethelreborn.vsm.services.MessageService;
import io.github.aethelreborn.vsm.services.NoteService;
import io.github.aethelreborn.vsm.services.PermissionService;
import io.github.aethelreborn.vsm.services.PunishmentService;
import io.github.aethelreborn.vsm.services.ReportService;
import io.github.aethelreborn.vsm.services.SecurityService;
import io.github.aethelreborn.vsm.services.StaffModeService;
import io.github.aethelreborn.vsm.services.StaffService;
import io.github.aethelreborn.vsm.services.StaffState;
import io.github.aethelreborn.vsm.services.TemplateService;
import io.github.aethelreborn.vsm.services.TeleportService;
import io.github.aethelreborn.vsm.services.VanishService;
import io.github.aethelreborn.vsm.services.WatchService;
import io.github.aethelreborn.vsm.services.WebhookService;
import io.github.aethelreborn.vsm.services.YouTuberService;
import io.github.aethelreborn.vsm.services.AuditService;
import io.github.aethelreborn.vsm.storage.MysqlStorage;
import io.github.aethelreborn.vsm.storage.SqliteStorage;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.storage.YamlStorage;
import io.github.aethelreborn.vsm.storage.cache.IpCache;
import io.github.aethelreborn.vsm.storage.cache.PunishmentCache;
import io.github.aethelreborn.vsm.storage.cache.StaffCache;
import io.github.aethelreborn.vsm.util.SchedulerAdapter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class (spec 4.3). Owns every service and the shared caches.
 *
 * <p>Loading order matters: configuration first, then storage, then caches, then services, then listeners,
 * then the scheduled tasks.
 */
public final class VisualStudioManagement extends JavaPlugin {

    /** Files copied into the data folder on first run (spec 4.3). */
    private static final List<String> BUNDLED_FILES = List.of(
            "messages.yml", "ranks.yml", "templates.yml", "automod.yml", "reasons.yml", "permissions.yml");

    private VsmConfig vsmConfig;
    private Messages messages;
    private RankRegistry rankRegistry;
    private TemplateRegistry templateRegistry;
    private AutoModConfig autoModConfig;
    private ReasonRegistry reasonRegistry;

    private StorageProvider storage;
    private StaffCache staffCache;
    private PunishmentCache punishmentCache;
    private IpCache ipCache;

    private SchedulerAdapter scheduler;
    private PermissionService permissionService;
    private MessageService messageService;
    private AuditService auditService;
    private PunishmentService punishmentService;
    private IpService ipService;
    private SecurityService securityService;
    private TemplateService templateService;
    private StaffService staffService;
    private ActionService actionService;
    private TeleportService teleportService;
    private FreezeService freezeService;
    private VanishService vanishService;
    private StaffModeService staffModeService;
    private YouTuberService youTuberService;
    private ReportService reportService;
    private ChatControlService chatControlService;
    private AutoModService autoModService;
    private WebhookService webhookService;
    private LockdownService lockdownService;
    private NoteService noteService;
    private WatchService watchService;
    private StaffState staffState;
    private VsmAPI api;

    private Path logDirectory;
    private Path staffModeDirectory;
    private boolean storageReady;

    @Override
    public void onEnable() {
        VsmRuntime.set(this);
        long start = System.currentTimeMillis();

        try {
            prepareDataFolder();
            loadConfiguration();
        } catch (RuntimeException ex) {
            getLogger().severe("Configuration could not be loaded, disabling: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!startStorage()) {
            return;
        }

        scheduler = new SchedulerAdapter(this);
        buildServices();
        loadCaches();
        registerListeners();
        registerPermissions();
        registerCommands();
        registerTasks();
        hookIntegrations();

        for (String warning : permissionService.auditWildcardHolders()) {
            getLogger().warning(warning);
        }
        if (!Bukkit.getOnlineMode()) {
            getLogger().warning("The server is running in offline mode: staff identity can be spoofed. "
                    + "Enable security.staff-pin or security.staff-ip-lock (see docs/privacy.md).");
        }
        getLogger().info("Enabled in " + (System.currentTimeMillis() - start) + " ms using "
                + storage.name() + " storage.");
    }

    @Override
    public void onDisable() {
        if (freezeService != null) {
            freezeService.releaseAll();
        }
        if (vanishService != null) {
            vanishService.releaseAll();
        }
        if (youTuberService != null) {
            youTuberService.releaseAll();
        }
        if (staffModeService != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (staffModeService.isActive(player.getUniqueId())) {
                    staffModeService.restore(player, null, "console");
                }
            }
        }
        if (auditService != null) {
            auditService.flushBlocking();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        if (storage != null && storageReady) {
            storage.shutdown().join();
        }
        VsmRuntime.clear();
    }

    // ----------------------------------------------------------- config
    private void prepareDataFolder() {
        saveDefaultConfig();
        for (String fileName : BUNDLED_FILES) {
            File target = new File(getDataFolder(), fileName);
            if (target.exists()) {
                continue;
            }
            try (InputStream stream = getResource(fileName)) {
                if (stream == null) {
                    getLogger().warning("Bundled " + fileName + " is missing from the jar.");
                    continue;
                }
                Files.createDirectories(getDataFolder().toPath());
                Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                getLogger().warning("Could not write " + fileName + ": " + ex.getMessage());
            }
        }
        logDirectory = getDataFolder().toPath().resolve("logs");
        staffModeDirectory = getDataFolder().toPath().resolve("state");
        try {
            Files.createDirectories(logDirectory);
            Files.createDirectories(staffModeDirectory);
        } catch (IOException ex) {
            getLogger().warning("Could not create the data folders: " + ex.getMessage());
        }
    }

    private void loadConfiguration() {
        reloadConfig();
        vsmConfig = new VsmConfig(getConfig());
        messages = new Messages(load("messages.yml"), vsmConfig.legacyColorCodes(), vsmConfig.prefix());

        rankRegistry = new RankRegistry();
        rankRegistry.load(load("ranks.yml"));

        templateRegistry = new TemplateRegistry();
        templateRegistry.load(load("templates.yml"), getLogger());

        autoModConfig = new AutoModConfig();
        autoModConfig.load(load("automod.yml"));
        for (String problem : autoModConfig.problems()) {
            getLogger().warning(problem);
        }

        reasonRegistry = new ReasonRegistry();
        reasonRegistry.load(load("reasons.yml"), templateRegistry);

        if (templateRegistry.hasProblems()) {
            getLogger().warning("templates.yml has problems; affected templates stay disabled until fixed.");
        }
    }

    private FileConfiguration load(String fileName) {
        File file = new File(getDataFolder(), fileName);
        YamlConfiguration configuration = new YamlConfiguration();
        if (!file.exists()) {
            return configuration;
        }
        try {
            configuration.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            getLogger().severe("Could not read " + fileName + ": " + ex.getMessage()
                    + " — the previous or default values are being used.");
        }
        return configuration;
    }

    /** Reloads configuration atomically; the old configuration stays on a parse error (spec 8.1). */
    public boolean reload() {
        try {
            reloadConfig();
            VsmConfig previousConfig = vsmConfig;
            Messages previousMessages = messages;
            RankRegistry previousRanks = rankRegistry;
            TemplateRegistry previousTemplates = templateRegistry;

            loadConfiguration();
            rankRegistry.ranks(staffCache);
            if (!ranksCompatible(previousRanks)) {
                rankRegistry = previousRanks;
                rankRegistry.ranks(staffCache);
            }
            if (templateRegistry.hasProblems()) {
                getLogger().warning("templates.yml has problems after reload; keeping the previous templates.");
                templateRegistry = previousTemplates;
            }
            if (vsmConfig == null) {
                vsmConfig = previousConfig;
            }
            if (messages == null) {
                messages = previousMessages;
            }
            permissionService.ranks(rankRegistry);
            actionService.ranks(rankRegistry);
            auditService.applyConfig();
            staffCache.ranks(rankRegistry);
            staffService.loadCache();
            reloadCaches();
            ipService.loadAllowList();
            return true;
        } catch (RuntimeException ex) {
            getLogger().severe("Reload failed, keeping the last good configuration: " + ex.getMessage());
            return false;
        }
    }

    private boolean ranksCompatible(RankRegistry previous) {
        return previous != null && !previous.all().isEmpty() && rankRegistry.all().size() > 1;
    }

    // ---------------------------------------------------------- storage
    private boolean startStorage() {
        String type = vsmConfig.storageType();
        try {
            switch (type) {
                case "YAML" -> storage = new YamlStorage(new File(getDataFolder(), "data.yml"), getLogger());
                case "MYSQL", "MARIADB" -> storage = new MysqlStorage(vsmConfig, getLogger());
                default -> storage = new SqliteStorage(getDataFolder().toPath().resolve(vsmConfig.sqliteFile()),
                        getLogger());
            }
        } catch (RuntimeException ex) {
            getLogger().severe("Could not create the storage backend: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        try {
            storage.init().get();
            storageReady = true;
            return true;
        } catch (Exception ex) {
            getLogger().severe("Storage initialisation failed (" + type + "): " + rootMessage(ex));
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    // ---------------------------------------------------------- services
    private void buildServices() {
        staffCache = new StaffCache(rankRegistry);
        punishmentCache = new PunishmentCache();
        ipCache = new IpCache();
        staffState = new StaffState();

        permissionService = new PermissionService(vsmConfig, rankRegistry, staffCache, getServer().getPluginManager());
        messageService = new MessageService(vsmConfig, messages, permissionService, scheduler);
        messageService.staffState(staffState);
        auditService = new AuditService(getLogger(), storage, vsmConfig, logDirectory);
        punishmentService = new PunishmentService(vsmConfig, storage, punishmentCache, scheduler, messageService);
        ipService = new IpService(vsmConfig, storage, ipCache, punishmentCache, permissionService);
        securityService = new SecurityService(vsmConfig, staffCache, storage, permissionService, auditService);
        staffService = new StaffService(storage, staffCache, rankRegistry);
        templateService = new TemplateService(templateRegistry, vsmConfig, punishmentCache, null, messageService);
        actionService = new ActionService(vsmConfig, permissionService, punishmentService, auditService,
                messageService, securityService, staffCache, templateService);
        templateService = new TemplateService(templateRegistry, vsmConfig, punishmentCache, actionService, messageService);
        actionService.ranks(rankRegistry);
        actionService.staffService(staffService);
        actionService.ownerRankId(highestRankId());

        teleportService = new TeleportService(vsmConfig, storage, scheduler, auditService, messageService, permissionService);
        freezeService = new FreezeService(vsmConfig, messageService, auditService, permissionService, scheduler);
        vanishService = new VanishService(vsmConfig, permissionService, auditService, messageService, scheduler);
        staffModeService = new StaffModeService(vsmConfig, permissionService, auditService, messageService,
                scheduler, getLogger(), staffModeDirectory, this);
        youTuberService = new YouTuberService(vsmConfig, permissionService, auditService, messageService,
                freezeService, teleportService, scheduler, getLogger(), staffModeDirectory);
        reportService = new ReportService(storage, vsmConfig, messageService, auditService, permissionService);
        chatControlService = new ChatControlService(vsmConfig, permissionService, auditService, messageService);
        autoModService = new AutoModService(autoModConfig, permissionService, messageService, actionService);
        webhookService = new WebhookService(vsmConfig, getLogger());
        lockdownService = new LockdownService(vsmConfig, messageService, auditService, staffCache);
        noteService = new NoteService(storage, messageService, auditService,
                () -> vsmConfig.maxReasonLength() * 4);
        watchService = new WatchService(storage, messageService, auditService, permissionService);

        teleportService.freezes(freezeService);
        actionService.webhooks(webhookService);
        actionService.teleports(teleportService);
        actionService.freezes(freezeService);

        api = new VsmAPI(actionService, punishmentService, permissionService);
        VsmBootstrap.setCommandRegistration(registrar ->
                CommandRegistrar.register(registrar, this, vsmConfig.rootCommand(), vsmConfig.standaloneAliases()));
    }

    private String highestRankId() {
        var ranks = rankRegistry.ladder();
        return ranks.isEmpty() ? "owner" : ranks.get(ranks.size() - 1).id();
    }

    private void loadCaches() {
        staffService.loadCache();
        reloadCaches();
        ipService.loadAllowList();
        storage.auditAll().thenAccept(auditService::resetChain).exceptionally(throwable -> null);
    }

    /** Rebuilds the punishment cache from storage. */
    private void reloadCaches() {
        punishmentCache.replaceAll(storage.loadActivePunishments().join());
    }

    private void registerPermissions() {
        permissionService.registerNodes();
    }

    private void registerListeners() {
        var manager = getServer().getPluginManager();
        manager.registerEvents(new PreLoginListener(this), this);
        manager.registerEvents(new JoinListener(this), this);
        manager.registerEvents(new QuitListener(this), this);
        manager.registerEvents(new ChatListener(this), this);
        manager.registerEvents(new CommandListener(this), this);
        manager.registerEvents(new MoveListener(this), this);
        manager.registerEvents(new InventoryListener(this), this);
        manager.registerEvents(new DamageListener(this), this);
        manager.registerEvents(new TeleportListener(this), this);
        manager.registerEvents(new AutoModListener(this), this);
        manager.registerEvents(new ServerPingListener(this), this);
        manager.registerEvents(new MenuListener(this), this);
    }

    private void registerCommands() {
        // Commands are registered by VsmBootstrap during the Paper COMMANDS lifecycle event.
        // The handler is installed in buildServices(); nothing else is required here.
    }

    private void registerTasks() {
        long intervalTicks = Math.max(100L, vsmConfig.expiryCheckIntervalSeconds() * 20L);
        scheduler.syncTimer(() -> scheduler.async(() -> {
            punishmentService.expireIfNeeded();
            freezeService.sweepAutoRelease();
        }), intervalTicks, intervalTicks);

        scheduler.syncTimer(() -> scheduler.async(auditService::flushAsync), 100L, 100L);
        scheduler.syncTimer(() -> scheduler.async(ipService::pruneHistory), 20L * 60L * 30L, 20L * 60L * 60L);
        scheduler.syncTimer(teleportService::tickFollow, 20L, 20L);
        scheduler.syncTimer(() -> scheduler.async(() -> auditService.compressOldLogs(vsmConfig.auditRetentionDays())),
                20L * 60L * 60L, 20L * 60L * 60L * 12L);
    }

    private void hookIntegrations() {
        PlaceholderApiHook.register(this);
    }

    // ----------------------------------------------------------- accessors
    public VsmConfig vsmConfig() {
        return vsmConfig;
    }

    public Messages messages() {
        return messages;
    }

    public RankRegistry rankRegistry() {
        return rankRegistry;
    }

    public TemplateRegistry templateRegistry() {
        return templateRegistry;
    }

    public AutoModConfig autoModConfig() {
        return autoModConfig;
    }

    public ReasonRegistry reasonRegistry() {
        return reasonRegistry;
    }

    public StorageProvider storage() {
        return storage;
    }

    public StaffCache staffCache() {
        return staffCache;
    }

    public PunishmentCache punishmentCache() {
        return punishmentCache;
    }

    public IpCache ipCache() {
        return ipCache;
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }

    public PermissionService permissionService() {
        return permissionService;
    }

    public MessageService messageService() {
        return messageService;
    }

    public AuditService auditService() {
        return auditService;
    }

    public PunishmentService punishmentService() {
        return punishmentService;
    }

    public IpService ipService() {
        return ipService;
    }

    public SecurityService securityService() {
        return securityService;
    }

    public TemplateService templateService() {
        return templateService;
    }

    public StaffService staffService() {
        return staffService;
    }

    public ActionService actionService() {
        return actionService;
    }

    public TeleportService teleportService() {
        return teleportService;
    }

    public FreezeService freezeService() {
        return freezeService;
    }

    public VanishService vanishService() {
        return vanishService;
    }

    public StaffModeService staffModeService() {
        return staffModeService;
    }

    public YouTuberService youTuberService() {
        return youTuberService;
    }

    public ReportService reportService() {
        return reportService;
    }

    public ChatControlService chatControlService() {
        return chatControlService;
    }

    public AutoModService autoModService() {
        return autoModService;
    }

    public WebhookService webhookService() {
        return webhookService;
    }

    public LockdownService lockdownService() {
        return lockdownService;
    }

    public NoteService noteService() {
        return noteService;
    }

    public WatchService watchService() {
        return watchService;
    }

    public StaffState staffState() {
        return staffState;
    }

    public VsmAPI api() {
        return api;
    }

    /** Live inventory views opened by {@code /invsee} and {@code /endersee}. */
    private final Map<java.util.UUID, InvseeSession> invseeSessions = new java.util.concurrent.ConcurrentHashMap<>();

    public InvseeSession invseeSession(java.util.UUID viewer) {
        return viewer == null ? null : invseeSessions.get(viewer);
    }

    public void openInvsee(Player viewer, Player target, boolean enderChest) {
        org.bukkit.inventory.Inventory source = enderChest ? target.getEnderChest() : target.getInventory();
        org.bukkit.inventory.Inventory mirror = Bukkit.createInventory(null, source.getSize(),
                net.kyori.adventure.text.Component.text(
                        (enderChest ? "Ender chest of " : "Inventory of ") + target.getName()));
        mirror.setContents(source.getContents());
        boolean editable = !enderChest && permissionService.has(viewer, PermissionNodes.INVSEE_EDIT);
        InvseeSession session = new InvseeSession(viewer.getUniqueId(), target.getUniqueId(), target.getName(),
                editable, enderChest, mirror, InvseeSession.snapshotOf(target.getInventory()), false);
        invseeSessions.put(viewer.getUniqueId(), session);
        auditService.log(io.github.aethelreborn.vsm.models.ActionSource.COMMAND, viewer.getUniqueId(),
                viewer.getName(), session.auditAction(), target.getUniqueId(), target.getName(), "");
        viewer.openInventory(mirror);
    }

    public void closeInvsee(Player viewer) {
        InvseeSession session = invseeSessions.remove(viewer.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.editable()) {
            session.flush();
            auditService.log(io.github.aethelreborn.vsm.models.ActionSource.COMMAND, viewer.getUniqueId(),
                    viewer.getName(), "INVSEE_EDIT", session.targetUuid(), session.targetName(), "");
        }
    }

    public Path logDirectory() {
        return logDirectory;
    }

    /** Exposed so {@code /vsm debug} can report node coverage. */
    public int declaredNodes() {
        return PermissionNodes.all().size();
    }
}