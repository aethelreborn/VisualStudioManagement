package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.IpRecord;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.storage.cache.IpCache;
import io.github.aethelreborn.vsm.storage.cache.PunishmentCache;
import io.github.aethelreborn.vsm.util.CidrMatcher;
import io.github.aethelreborn.vsm.util.IpUtil;
import io.github.aethelreborn.vsm.util.TextUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * IP intelligence: login history, alt detection, CIDR matching, masking and the privacy hashing mode
 * (spec 11.8, 13.5).
 */
public final class IpService {

    private final VsmConfig config;
    private final StorageProvider storage;
    private final IpCache cache;
    private final PunishmentCache punishments;
    private final PermissionService permissions;

    public IpService(VsmConfig config, StorageProvider storage, IpCache cache,
                     PunishmentCache punishments, PermissionService permissions) {
        this.config = config;
        this.storage = storage;
        this.cache = cache;
        this.punishments = punishments;
        this.permissions = permissions;
    }

    /**
     * Normalises an address for storage. With {@code privacy.hash-ips} the raw address is replaced by
     * {@code HMAC-SHA256(ip, serverSecret)}; exact match enforcement still works, CIDR matching does not.
     */
    public String store(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (!config.hashIps()) {
            return IpUtil.normalise(ip);
        }
        return hmac(ip.trim());
    }

    /** HMAC-SHA256 of the address, used in hashed mode. */
    public String hmac(String ip) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(serverSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 is required but unavailable", ex);
        }
    }

    private String serverSecret() {
        String configured = System.getenv("VSM_SERVER_SECRET");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("vsm:" + config.serverName()).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    /** Records a login and updates the caches. Safe to call from the async pre-login thread. */
    public void recordLogin(UUID uuid, String ip) {
        String stored = store(ip);
        if (stored == null || uuid == null) {
            return;
        }
        cache.record(uuid, stored);
        storage.recordLogin(uuid, stored, System.currentTimeMillis());
    }

    public String lastKnownIp(UUID uuid) {
        String cached = cache.lastIp(uuid);
        if (cached != null) {
            return cached;
        }
        return storage.lastKnownIp(uuid).join();
    }

    /**
     * Safety guards from spec 8.2 rule 4.
     *
     * @return {@code null} when the address may be banned, otherwise the rejection message key
     */
    public String guardIpBan(String ip, CommandSenderContext actor) {
        if (ip == null || ip.isBlank()) {
            return "invalid-ip";
        }
        if (!IpUtil.isCidr(ip) && !IpUtil.isValidIp(ip)) {
            return "invalid-ip";
        }
        if (!config.ipAllowPrivate() && !IpUtil.isCidr(ip) && IpUtil.isPrivate(ip)) {
            return "ip-ban-private-blocked";
        }
        if (IpUtil.isCidr(ip)) {
            if (!config.ipAllowRanges()) {
                return "ip-ban-range-disabled";
            }
            if (!IpUtil.isValidIp(ip.substring(0, ip.lastIndexOf('/')))) {
                return "invalid-ip";
            }
            int prefix = IpUtil.prefixLength(ip);
            boolean v4 = IpUtil.isIpv4(ip.substring(0, ip.lastIndexOf('/')));
            int maxPrefix = v4 ? config.maxRangePrefixV4() : config.maxRangePrefixV6();
            if (prefix < maxPrefix) {
                return "ip-ban-range-too-large";
            }
            if (!config.ipAllowPrivate() && IpUtil.isPrivate(networkAddress(ip))) {
                return "ip-ban-private-blocked";
            }
        }
        if (actor != null && actor.selfIp() != null
                && IpUtil.cacheKey(actor.selfIp()).equals(IpUtil.cacheKey(ip))
                && !IpUtil.isCidr(ip)) {
            return "ip-ban-own-ip";
        }
        return null;
    }

    private static String networkAddress(String cidr) {
        byte[] bytes = CidrMatcher.networkBytes(cidr);
        if (bytes == null) {
            return cidr;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                out.append(i == bytes.length / 2 ? ":" : ".");
            }
            out.append(bytes[i] & 0xFF);
        }
        return out.toString();
    }

    /** Masks an address unless the viewer holds {@code vsm.ip.view}. */
    public String display(String ip, org.bukkit.command.CommandSender viewer) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        if (config.hashIps()) {
            return "hashed";
        }
        if (viewer == null) {
            return IpUtil.mask(ip);
        }
        boolean maySee = permissions.has(viewer, PermissionNodes.IP_VIEW);
        if (maySee && config.logRawIps()) {
            return ip;
        }
        return maySee ? ip : IpUtil.mask(ip);
    }

    public String display(String ip, UUID viewer) {
        if (config.hashIps()) {
            return "hashed";
        }
        if (viewer != null && permissions.has(viewer, PermissionNodes.IP_VIEW)) {
            return ip;
        }
        return IpUtil.mask(ip);
    }

    /** Alternate accounts on the same address, most recent first. */
    public List<UUID> alts(UUID uuid) {
        String ip = lastKnownIp(uuid);
        if (ip == null) {
            return List.of();
        }
        List<UUID> known = cache.accountsOnIp(ip, uuid);
        if (known.isEmpty()) {
            known = storage.accountsOnIp(ip).join();
        }
        List<UUID> out = new ArrayList<>();
        for (UUID candidate : known) {
            if (!candidate.equals(uuid) && !out.contains(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    /** True when the account's address is shared with a currently banned account (spec 11.8). */
    public boolean linkedToBannedAccount(UUID uuid, String ip) {
        if (ip == null) {
            return false;
        }
        for (UUID alt : alts(uuid)) {
            if (!punishments.activeFor(alt, System.currentTimeMillis()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Accounts seen on an address inside the configured window, used when cascading account bans. */
    public List<UUID> accountsOnIpForIpBan(String ip) {
        String key = IpUtil.cacheKey(ip);
        List<UUID> fromCache = cache.accountsOnIp(key, null);
        if (!fromCache.isEmpty()) {
            return fromCache;
        }
        return storage.accountsOnIp(store(ip)).join();
    }

    public List<IpRecord> history(UUID uuid) {
        return storage.ipHistory(uuid).join();
    }

    /** Adds a UUID to the IP allowlist so it can join despite an IP ban (spec 8.2 rule 5). */
    public void allowIp(UUID uuid, String addedBy) {
        punishments.addIpAllow(uuid);
        storage.saveIpAllow(uuid, addedBy, System.currentTimeMillis());
    }

    public void removeIpAllow(UUID uuid) {
        punishments.removeIpAllow(uuid);
        storage.removeIpAllow(uuid);
    }

    public void loadAllowList() {
        for (UUID uuid : storage.loadIpAllow().join()) {
            punishments.addIpAllow(uuid);
        }
    }

    /** Prunes IP rows older than the configured retention window (spec 13.5). */
    public void pruneHistory() {
        int days = config.ipHistoryRetentionDays();
        if (days <= 0) {
            return;
        }
        storage.pruneIpHistory(System.currentTimeMillis() - days * 86_400_000L);
    }

    /** Masks anything that looks like an IP in free text before it reaches a log or webhook. */
    public String maskInText(String text) {
        if (text == null) {
            return null;
        }
        if (config.hashIps()) {
            return TextUtil.stripMentions(text);
        }
        return text.replaceAll("\\b(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b", "$1.$2.$3.x")
                .replaceAll("(?i)\\b([0-9a-f]{1,4}:){2,7}[0-9a-f]{1,4}\\b", "::x");
    }

    /** Simple holder so the guard method can stay free of Bukkit types. */
    public record CommandSenderContext(String selfIp, boolean isConsole) {

        public static CommandSenderContext of(org.bukkit.command.CommandSender sender, String ip) {
            return new CommandSenderContext(ip, !(sender instanceof org.bukkit.entity.Player));
        }
    }

    /** Reads the ban that applies to an address, exact matches first then ranges (spec 9.5 step 3). */
    public Optional<Punishment> activeIpBan(String ip, UUID uuid, long now) {
        if (ip == null || ip.isBlank()) {
            return Optional.empty();
        }
        if (uuid != null && punishments.hasIpAllow(uuid)) {
            return Optional.empty();
        }
        String key = IpUtil.cacheKey(ip);
        if (!config.hashIps()) {
            List<Punishment> exact = punishments.activeForIp(key, now);
            for (Punishment punishment : exact) {
                if (punishment.type().isBan()) {
                    return Optional.of(punishment);
                }
            }
        }
        Punishment range = punishments.rangeBanFor(key, now);
        return Optional.ofNullable(range);
    }

    /** Active IP mutes that should block chat for this address. */
    public List<Punishment> activeIpMutes(String ip, long now) {
        if (ip == null || ip.isBlank() || config.hashIps()) {
            return List.of();
        }
        return punishments.activeIpMutes(IpUtil.cacheKey(ip), now);
    }

    /** Normalised key used consistently across caches. */
    public String key(String ip) {
        return IpUtil.cacheKey(ip);
    }

    /** Text-safe rendering used by the audit log. */
    public String forLog(String ip) {
        if (ip == null) {
            return "unknown";
        }
        if (config.logRawIps()) {
            return ip;
        }
        return IpUtil.mask(ip);
    }

    /** Parses a punishment type from a raw string, case insensitive. */
    public PunishmentType typeOf(String raw) {
        return PunishmentType.fromString(raw == null ? null : raw.trim().toUpperCase(Locale.ROOT));
    }
}