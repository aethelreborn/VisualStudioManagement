package io.github.aethelreborn.vsm.config;

import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed, validated access to {@code config.yml}. Every getter has a safe default so a partially written or
 * slightly outdated configuration never stops the plugin from starting (spec 21: a broken config must not
 * crash the server).
 */
public final class VsmConfig {

    public enum PermissionMode {
        INTERNAL,
        BUKKIT,
        BOTH;

        static PermissionMode parse(String raw) {
            if (raw == null) {
                return BOTH;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return BOTH;
            }
        }
    }

    private final FileConfiguration config;

    public VsmConfig(FileConfiguration config) {
        this.config = config;
    }

    public FileConfiguration raw() {
        return config;
    }

    // ------------------------------------------------------------- prefix
    public String prefix() {
        return config.getString("prefix", "<dark_gray>[<aqua>VSM</aqua>]</dark_gray> ");
    }

    // ----------------------------------------------------------- settings
    public String language() {
        return config.getString("settings.language", "en");
    }

    public boolean legacyColorCodes() {
        return config.getBoolean("settings.legacy-color-codes", false);
    }

    public PermissionMode permissionMode() {
        return PermissionMode.parse(config.getString("settings.permission-mode", "BOTH"));
    }

    public boolean opBypass() {
        return config.getBoolean("settings.op-bypass", false);
    }

    public String serverName() {
        return config.getString("settings.server-name", "main");
    }

    public String appealUrl() {
        return config.getString("settings.appeal-url", "");
    }

    public boolean checkForUpdates() {
        return config.getBoolean("settings.check-for-updates", true);
    }

    // ------------------------------------------------------------ storage
    public String storageType() {
        return config.getString("storage.type", "SQLITE").trim().toUpperCase(Locale.ROOT);
    }

    public String sqliteFile() {
        return config.getString("storage.sqlite.file", "database.db");
    }

    public int expiryCheckIntervalSeconds() {
        return Math.max(5, config.getInt("storage.expiry-check-interval-seconds", 30));
    }

    public int autoPruneDays() {
        return Math.max(0, config.getInt("storage.auto-prune-days", 0));
    }

    public boolean backupsEnabled() {
        return config.getBoolean("storage.backups.enabled", true);
    }

    public int backupIntervalHours() {
        return Math.max(1, config.getInt("storage.backups.interval-hours", 24));
    }

    public int backupKeep() {
        return Math.max(1, config.getInt("storage.backups.keep", 7));
    }

    public String mysqlHost() {
        return config.getString("storage.mysql.host", "localhost");
    }

    public int mysqlPort() {
        return config.getInt("storage.mysql.port", 3306);
    }

    public String mysqlDatabase() {
        return config.getString("storage.mysql.database", "vsm");
    }

    public String mysqlUser() {
        return config.getString("storage.mysql.user", "root");
    }

    public String mysqlPassword() {
        return config.getString("storage.mysql.password", "");
    }

    public boolean mysqlUseSsl() {
        return config.getBoolean("storage.mysql.use-ssl", false);
    }

    public int mysqlPoolMaxSize() {
        return Math.max(1, config.getInt("storage.mysql.pool.max-size", 10));
    }

    public int mysqlPoolMinIdle() {
        return Math.max(0, config.getInt("storage.mysql.pool.min-idle", 2));
    }

    public long mysqlPoolMaxLifetimeMs() {
        return Math.max(30_000L, config.getLong("storage.mysql.pool.max-lifetime-ms", 1_800_000L));
    }

    public long mysqlConnectionTimeoutMs() {
        return Math.max(1_000L, config.getLong("storage.mysql.pool.connection-timeout-ms", 10_000L));
    }

    // -------------------------------------------------------------- staff
    public String defaultRank() {
        return config.getString("staff.default-rank", "helper");
    }

    public boolean allowSelfRankChange() {
        return config.getBoolean("staff.allow-self-rank-change", false);
    }

    public int staffInactivityAlertDays() {
        return Math.max(1, config.getInt("staff.inactivity-alert-days", 30));
    }

    // -------------------------------------------------------- punishments
    public boolean requireReason() {
        return config.getBoolean("punishments.require-reason", true);
    }

    public int minReasonLength() {
        return Math.max(1, config.getInt("punishments.min-reason-length", 3));
    }

    public int maxReasonLength() {
        return Math.max(minReasonLength(), config.getInt("punishments.max-reason-length", 256));
    }

    public String idPrefix() {
        return config.getString("punishments.id-prefix", "VP");
    }

    public boolean idNumericMode() {
        return config.getBoolean("punishments.id-numeric-mode", false);
    }

    public boolean broadcast(String typeKey) {
        return config.getBoolean("punishments.broadcast." + typeKey.toLowerCase(Locale.ROOT), false);
    }

    public boolean banIpOnBan() {
        return config.getBoolean("punishments.ban-ip-on-ban", false);
    }

    public boolean alsoBanAccountsOnIpban() {
        return config.getBoolean("punishments.also-ban-accounts-on-ipban", true);
    }

    public int ipHistoryWindowDays() {
        return Math.max(1, config.getInt("punishments.ip-history-window-days", 90));
    }

    public boolean ipAllowPrivate() {
        return config.getBoolean("punishments.ipban.allow-private", false);
    }

    public boolean ipAllowRanges() {
        return config.getBoolean("punishments.ipban.allow-ranges", true);
    }

    public int maxRangePrefixV4() {
        return clamp(config.getInt("punishments.ipban.max-range-prefix-v4", 24), 8, 32);
    }

    public int maxRangePrefixV6() {
        return clamp(config.getInt("punishments.ipban.max-range-prefix-v6", 64), 16, 128);
    }

    public List<String> blockedCommandsWhileMuted() {
        return lower(config.getStringList("punishments.blocked-commands-while-muted"));
    }

    public boolean exemptPermissionChecks() {
        return config.getBoolean("punishments.exempt-permission-checks", true);
    }

    /**
     * Per-rank duration cap.
     *
     * @return milliseconds, or {@link Long#MAX_VALUE} when the rank is unlimited for this type
     */
    public long maxDuration(String rankId, String typeKey) {
        if (rankId == null || typeKey == null) {
            return Long.MAX_VALUE;
        }
        String raw = config.getString("punishments.max-durations." + rankId + "." + typeKey.toLowerCase(Locale.ROOT));
        return parseCap(raw);
    }

    /** @return the rank id from which a punishment type may be used permanently, or {@code null}. */
    public String minimumRankFor(String key) {
        String path = switch (key) {
            case "permanent-ban-min-rank" -> "punishments.permanent-ban-min-rank";
            case "permanent-mute-min-rank" -> "punishments.permanent-mute-min-rank";
            case "ipban-min-rank" -> "punishments.ipban-min-rank";
            case "tempipban-min-rank" -> "punishments.tempipban-min-rank";
            default -> null;
        };
        return path == null ? null : config.getString(path);
    }

    private long parseCap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Long.MAX_VALUE;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.equals("none") || text.equals("unlimited") || text.equals("forever")) {
            return Long.MAX_VALUE;
        }
        try {
            long value = TimeParser.parse(text);
            return value <= 0L ? Long.MAX_VALUE : value;
        } catch (IllegalArgumentException ex) {
            return Long.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------ reports
    public int maxOpenReportsPerPlayer() {
        return Math.max(1, config.getInt("reports.max-open-reports-per-player", 3));
    }

    public int reportCooldownSeconds() {
        return Math.max(0, config.getInt("reports.cooldown-seconds", 60));
    }

    public boolean notifyStaffOnReport() {
        return config.getBoolean("reports.notify-staff", true);
    }

    public boolean allowReportingStaff() {
        return config.getBoolean("reports.allow-reporting-staff", false);
    }

    public int mergeDuplicatesMinutes() {
        return Math.max(0, config.getInt("reports.merge-duplicates-minutes", 10));
    }

    public int escalateThreshold() {
        return Math.max(1, config.getInt("reports.escalate-threshold", 3));
    }

    public int escalateWindowMinutes() {
        return Math.max(1, config.getInt("reports.escalate-window-minutes", 30));
    }

    // ------------------------------------------------------------- freeze
    public boolean freezeBlockCommands() {
        return config.getBoolean("freeze.block-commands", true);
    }

    public List<String> freezeAllowedCommands() {
        return lower(config.getStringList("freeze.allowed-commands"));
    }

    public boolean freezePunishOnLogoutEnabled() {
        return config.getBoolean("freeze.punish-on-logout.enabled", true);
    }

    public String freezePunishOnLogoutTemplate() {
        return config.getString("freeze.punish-on-logout.template", "freeze-logout");
    }

    public boolean freezeBlindness() {
        return config.getBoolean("freeze.effects.blindness", false);
    }

    public boolean freezeTitle() {
        return config.getBoolean("freeze.effects.title", true);
    }

    public boolean freezeBlockDamageDealt() {
        return config.getBoolean("freeze.block-damage-dealt", true);
    }

    // ------------------------------------------------------------ vanish
    public boolean vanishHideFromTab() {
        return config.getBoolean("vanish.hide-from-tab", true);
    }

    public boolean vanishSuppressJoinLeave() {
        return config.getBoolean("vanish.suppress-join-leave", true);
    }

    public boolean vanishSilentContainers() {
        return config.getBoolean("vanish.silent-containers", true);
    }

    public boolean vanishBlockItemPickup() {
        return config.getBoolean("vanish.block-item-pickup", true);
    }

    public boolean vanishAutoOnJoin() {
        return config.getBoolean("vanish.auto-on-join", false);
    }

    public boolean vanishPersistAcrossRelogs() {
        return config.getBoolean("vanish.persist-across-relogs", true);
    }

    // ---------------------------------------------------------- teleport
    public int backHistory() {
        return clamp(config.getInt("teleport.back-history", 5), 1, 50);
    }

    public boolean safeTeleport() {
        return config.getBoolean("teleport.safe-teleport", true);
    }

    public List<String> teleportWorldBlacklist() {
        return config.getStringList("teleport.world-blacklist");
    }

    public boolean confirmTpAll() {
        return config.getBoolean("teleport.confirm-tpall", true);
    }

    public int teleportCooldownSeconds() {
        return Math.max(0, config.getInt("teleport.cooldown-seconds", 0));
    }

    // --------------------------------------------------------- staff mode
    public boolean staffModeSaveToDisk() {
        return config.getBoolean("staffmode.save-to-disk", true);
    }

    public boolean staffModeVanish() {
        return config.getBoolean("staffmode.vanish", true);
    }

    public boolean staffModeFly() {
        return config.getBoolean("staffmode.fly", true);
    }

    public boolean staffModeInvulnerable() {
        return config.getBoolean("staffmode.invulnerable", true);
    }

    public boolean staffModeAllowBuilding() {
        return config.getBoolean("staffmode.allow-building", false);
    }

    // ---------------------------------------------------------- movement
    public double speedCap(String rankId) {
        String raw = config.getString("movement.speed-cap." + rankId);
        if (raw == null) {
            return 10.0D;
        }
        try {
            return clampDouble(Double.parseDouble(raw), 0.1D, 10.0D);
        } catch (NumberFormatException ex) {
            return 10.0D;
        }
    }

    // ------------------------------------------------------------- invsee
    public boolean invseeOfflineSupport() {
        return config.getBoolean("invsee.offline-support", false);
    }

    public boolean invseeEditRequiresConfirmation() {
        return config.getBoolean("invsee.edit-requires-confirmation", true);
    }

    // ----------------------------------------------------------- commands
    public String rootCommand() {
        return config.getString("commands.root", "vsm").trim().toLowerCase(Locale.ROOT);
    }

    public boolean standaloneAliases() {
        return config.getBoolean("commands.standalone-aliases", true);
    }

    // ----------------------------------------------------------- youtuber
    public String youtuberRankId() {
        return config.getString("youtuber.rank-id", "youtuber");
    }

    public long youtuberFreezeMaxDuration() {
        return parseCap(config.getString("youtuber.freeze.max-duration", "10m"));
    }

    public int youtuberFreezeMaxConcurrent() {
        return Math.max(1, config.getInt("youtuber.freeze.max-concurrent", 5));
    }

    public int youtuberFreezeCooldownSeconds() {
        return Math.max(0, config.getInt("youtuber.freeze.cooldown-seconds", 5));
    }

    public boolean youtuberNotifyTarget() {
        return config.getBoolean("youtuber.freeze.notify-target", true);
    }

    public List<String> youtuberFreezeAllowedCommands() {
        List<String> list = lower(config.getStringList("youtuber.freeze.allowed-commands"));
        return list.isEmpty() ? freezeAllowedCommands() : list;
    }

    public boolean youtuberFreezePunishOnLogout() {
        return config.getBoolean("youtuber.freeze.punish-on-logout", false);
    }

    public boolean youtuberReleaseWhenActorLeaves() {
        return config.getBoolean("youtuber.freeze.release-when-actor-leaves", true);
    }

    public boolean youtuberMoveAllowOthers() {
        return config.getBoolean("youtuber.move.allow-others", true);
    }

    public double youtuberMoveMaxDistance() {
        return clampDouble(config.getDouble("youtuber.move.max-distance", 100.0D), 1.0D, 10_000.0D);
    }

    public boolean youtuberMoveAllowCrossWorld() {
        return config.getBoolean("youtuber.move.allow-cross-world", false);
    }

    public List<String> youtuberMoveWorldBlacklist() {
        return config.getStringList("youtuber.move.world-blacklist");
    }

    public int youtuberMoveCooldownSeconds() {
        return Math.max(0, config.getInt("youtuber.move.cooldown-seconds", 3));
    }

    public int youtuberTpCooldownSeconds() {
        return Math.max(0, config.getInt("youtuber.tp.cooldown-seconds", 3));
    }

    public boolean youtuberTpAllowToStaff() {
        return config.getBoolean("youtuber.tp.allow-to-staff", false);
    }

    public boolean youtuberTpAllowCrossWorld() {
        return config.getBoolean("youtuber.tp.allow-cross-world", true);
    }

    public List<String> youtuberTpWorldBlacklist() {
        return config.getStringList("youtuber.tp.world-blacklist");
    }

    public List<String> youtuberAllowedGameModes() {
        List<String> raw = config.getStringList("youtuber.gamemode.allowed");
        return raw.isEmpty() ? List.of("SURVIVAL", "ADVENTURE", "CREATIVE") : upper(raw);
    }

    public int youtuberGameModeCooldownSeconds() {
        return Math.max(0, config.getInt("youtuber.gamemode.switch-cooldown-seconds", 2));
    }

    public List<String> youtuberCreativeWorlds() {
        return config.getStringList("youtuber.gamemode.creative-worlds");
    }

    public boolean youtuberSeparateInventories() {
        return config.getBoolean("youtuber.gamemode.separate-inventories", true);
    }

    public String youtuberResetOnQuit() {
        return config.getString("youtuber.gamemode.reset-on-quit", "SURVIVAL").trim().toUpperCase(Locale.ROOT);
    }

    public boolean creativeBlockItemDrop() {
        return config.getBoolean("youtuber.gamemode.creative-restrictions.block-item-drop", true);
    }

    public boolean creativeBlockPvp() {
        return config.getBoolean("youtuber.gamemode.creative-restrictions.block-pvp", true);
    }

    public boolean creativeBlockContainerAccess() {
        return config.getBoolean("youtuber.gamemode.creative-restrictions.block-container-access", false);
    }

    // -------------------------------------------------- lockdown / maintenance
    public boolean lockdownAllowReturningPlayers() {
        return config.getBoolean("lockdown.allow-returning-players", true);
    }

    public String lockdownMessageKey() {
        return config.getString("lockdown.message-key", "lockdown");
    }

    public boolean maintenanceEnabled() {
        return config.getBoolean("maintenance.enabled", false);
    }

    public List<String> maintenanceWhitelist() {
        return config.getStringList("maintenance.whitelist");
    }

    // ----------------------------------------------------------- security
    public boolean staffPinEnabled() {
        return config.getBoolean("security.staff-pin.enabled", false);
    }

    public int staffPinMaxAttempts() {
        return Math.max(1, config.getInt("security.staff-pin.max-attempts", 3));
    }

    public boolean staffIpLock() {
        return config.getBoolean("security.staff-ip-lock", false);
    }

    public int sessionMinutes() {
        return Math.max(0, config.getInt("security.session-minutes", 0));
    }

    public boolean twoPersonRuleEnabled() {
        return config.getBoolean("security.two-person-rule.enabled", false);
    }

    public Set<String> twoPersonActions() {
        return new java.util.LinkedHashSet<>(upper(config.getStringList("security.two-person-rule.actions")));
    }

    /** @return {@code max} actions allowed per window, or {@code -1} when unlimited. */
    public int rateLimitMax(String action) {
        String path = "security.rate-limits." + action.toLowerCase(Locale.ROOT) + ".max";
        return config.contains(path) ? config.getInt(path) : -1;
    }

    public int rateLimitWindowMinutes(String action) {
        String path = "security.rate-limits." + action.toLowerCase(Locale.ROOT) + ".per-minutes";
        return Math.max(1, config.getInt(path, 10));
    }

    // ------------------------------------------------------------ privacy
    public boolean hashIps() {
        return config.getBoolean("privacy.hash-ips", false);
    }

    public boolean logRawIps() {
        return config.getBoolean("privacy.log-raw-ips", false);
    }

    public int ipHistoryRetentionDays() {
        return Math.max(0, config.getInt("privacy.ip-history-retention-days", 180));
    }

    public boolean maskIpsForStaff() {
        return config.getBoolean("privacy.mask-ips-for-staff", true);
    }

    // --------------------------------------------------------------- alts
    public boolean altsAlertOnBannedLink() {
        return config.getBoolean("alts.alert-on-banned-link", true);
    }

    public String altsAutoAction() {
        return config.getString("alts.auto-action", "ALERT").trim().toUpperCase(Locale.ROOT);
    }

    // -------------------------------------------------------------- audit
    public int auditRetentionDays() {
        return Math.max(0, config.getInt("audit.retention-days", 365));
    }

    public boolean auditConsoleMajorActions() {
        return config.getBoolean("audit.console-major-actions", true);
    }

    public boolean auditTamperEvident() {
        return config.getBoolean("audit.tamper-evident", true);
    }

    // ----------------------------------------------------------- webhooks
    public boolean discordEnabled() {
        return config.getBoolean("webhooks.discord.enabled", false);
    }

    public String discordUrl() {
        return config.getString("webhooks.discord.url", "");
    }

    public Set<String> discordEvents() {
        return new java.util.LinkedHashSet<>(upper(config.getStringList("webhooks.discord.events")));
    }

    public String discordMentionRoleId() {
        return config.getString("webhooks.discord.mention-role-id", "");
    }

    // ------------------------------------------------------------ helpers
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<String> lower(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String value : input) {
            out.add(value.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<String> upper(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String value : input) {
            out.add(value.toUpperCase(Locale.ROOT));
        }
        return out;
    }
}