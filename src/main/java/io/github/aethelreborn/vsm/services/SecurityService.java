package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.config.PermissionNodes;
import io.github.aethelreborn.vsm.config.VsmConfig;
import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.storage.StorageProvider;
import io.github.aethelreborn.vsm.storage.cache.StaffCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff authentication hardening and abuse prevention (spec 13.1, 13.2).
 *
 * <p>PINs are stored as salted SHA-256 digests, never in plaintext. Rate limits count punishments per staff
 * member and can suspend their punishment powers for a short window when exceeded.
 */
public final class SecurityService {

    /** How long a staff member stays suspended after exceeding a rate limit. */
    public static final long SUSPENSION_MILLIS = 10 * 60 * 1000L;

    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VsmConfig config;
    private final StaffCache staff;
    private final StorageProvider storage;
    private final PermissionService permissions;
    private final AuditService audit;

    private final Map<UUID, Deque<Long>> actionLog = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pinAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suspendedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionActivity = new ConcurrentHashMap<>();

    public SecurityService(VsmConfig config, StaffCache staff, StorageProvider storage,
                           PermissionService permissions, AuditService audit) {
        this.config = config;
        this.staff = staff;
        this.storage = storage;
        this.permissions = permissions;
        this.audit = audit;
    }

    // --------------------------------------------------------------- PINs
    /** Generates a salted digest for a new PIN. */
    public String hashPin(String pin) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltHex = hex(salt);
        return saltHex + "$" + digest(saltHex, pin);
    }

    public boolean verifyPin(String storedHash, String pin) {
        if (storedHash == null || pin == null) {
            return false;
        }
        int separator = storedHash.indexOf('$');
        if (separator <= 0) {
            return false;
        }
        String saltHex = storedHash.substring(0, separator);
        String expected = storedHash.substring(separator + 1);
        return constantTimeEquals(expected, digest(saltHex, pin));
    }

    private String digest(String saltHex, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(saltHex.getBytes(StandardCharsets.UTF_8));
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Checks a PIN attempt.
     *
     * @return {@code true} when the PIN was correct
     */
    public boolean checkPinAttempt(UUID uuid, String pin) {
        if (!config.staffPinEnabled()) {
            return true;
        }
        var member = staff.byUuid(uuid);
        if (member == null || member.pinHash() == null || member.pinHash().isBlank()) {
            return true; // no PIN configured for this account
        }
        int attempts = pinAttempts.merge(uuid, 1, Integer::sum);
        if (verifyPin(member.pinHash(), pin)) {
            pinAttempts.remove(uuid);
            return true;
        }
        audit.log(ActionSource.COMMAND, uuid, member.name(), "PIN_FAILURE",
                uuid, member.name(), "attempt " + attempts + "/" + config.staffPinMaxAttempts());
        if (attempts >= Math.min(config.staffPinMaxAttempts(), MAX_ATTEMPTS)) {
            suspend(uuid, SUSPENSION_MILLIS);
            pinAttempts.remove(uuid);
        }
        return false;
    }

    public boolean isPinLocked(UUID uuid) {
        if (!config.staffPinEnabled()) {
            return false;
        }
        var member = staff.byUuid(uuid);
        return member != null && member.pinHash() != null && !member.pinHash().isBlank();
    }

    // -------------------------------------------------------- rate limits
    /**
     * Records an action and reports whether the staff member is within their limit.
     *
     * @param action {@code ban}, {@code mute} or {@code kick}
     * @return {@code true} when the action may proceed
     */
    public boolean allowAction(UUID uuid, String action, String staffName) {
        if (uuid == null) {
            return true;
        }
        if (permissions.has(uuid, PermissionNodes.OVERRIDE)) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long suspended = suspendedUntil.get(uuid);
        if (suspended != null && suspended > now) {
            return false;
        }
        if (suspended != null) {
            suspendedUntil.remove(uuid);
        }
        int max = config.rateLimitMax(action);
        if (max <= 0) {
            return true;
        }
        long window = config.rateLimitWindowMinutes(action) * 60_000L;
        Deque<Long> history = actionLog.computeIfAbsent(uuid, key -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst() < now - window) {
                history.pollFirst();
            }
            if (history.size() >= max) {
                audit.log(ActionSource.COMMAND, uuid, staffName, "RATE_LIMIT_HIT",
                        uuid, staffName, action + " limit " + max + "/" + config.rateLimitWindowMinutes(action) + "m");
                suspendedUntil.put(uuid, now + SUSPENSION_MILLIS);
                alertAdmins(uuid, staffName, action);
                return false;
            }
            history.addLast(now);
            return true;
        }
    }

    private void alertAdmins(UUID uuid, String staffName, String action) {
        for (var player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (permissions.has(player.getUniqueId(), PermissionNodes.STAFF_MANAGE)) {
                new MessageServiceBridge(audit).rateLimitAlert(player, staffName, action);
            }
        }
    }

    /** Simple bridge so the audit service can build messages without a circular dependency. */
    private static final class MessageServiceBridge {
        private final AuditService audit;

        private MessageServiceBridge(AuditService audit) {
            this.audit = audit;
        }

        private void rateLimitAlert(org.bukkit.command.CommandSender target, String staffName, String action) {
            target.sendMessage(net.kyori.adventure.text.Component.text(
                    "Staff member " + staffName + " exceeded the " + action + " rate limit and was suspended.", 
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    public boolean isSuspended(UUID uuid) {
        Long until = suspendedUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    public void suspend(UUID uuid, long millis) {
        suspendedUntil.put(uuid, System.currentTimeMillis() + millis);
    }

    public void clearSuspension(UUID uuid) {
        suspendedUntil.remove(uuid);
    }

    // ----------------------------------------------------------- sessions
    public void touchSession(UUID uuid) {
        sessionActivity.put(uuid, System.currentTimeMillis());
    }

    /** @return {@code true} when the staff member must re-authenticate. */
    public boolean sessionExpired(UUID uuid) {
        int minutes = config.sessionMinutes();
        if (minutes <= 0 || !config.staffPinEnabled()) {
            return false;
        }
        Long last = sessionActivity.get(uuid);
        return last == null || System.currentTimeMillis() - last > minutes * 60_000L;
    }

    public void forget(UUID uuid) {
        actionLog.remove(uuid);
        pinAttempts.remove(uuid);
        suspendedUntil.remove(uuid);
        sessionActivity.remove(uuid);
    }

    /** Snapshot used by {@code /vsm debug}. */
    public Map<String, Integer> statistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("rateLimitTracked", actionLog.size());
        stats.put("suspended", suspendedUntil.size());
        stats.put("activeSessions", sessionActivity.size());
        return stats;
    }

    /** Random alphanumeric PIN suggestion printed by {@code /staff pin}. */
    public String suggestPin() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 8);
    }
}