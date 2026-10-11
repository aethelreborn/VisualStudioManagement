package io.github.aethelreborn.vsm.api;

import io.github.aethelreborn.vsm.models.ActionSource;
import io.github.aethelreborn.vsm.models.Punishment;
import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.models.StaffMember;
import io.github.aethelreborn.vsm.services.ActionRequest;
import io.github.aethelreborn.vsm.services.ActionResult;
import io.github.aethelreborn.vsm.services.ActionService;
import io.github.aethelreborn.vsm.services.PermissionService;
import io.github.aethelreborn.vsm.services.PunishmentService;
import io.github.aethelreborn.vsm.util.TimeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Public, stable API (spec 18.2). Everything that changes state routes through {@link ActionService}, so
 * API callers get the same permission, outranking, exemption, limit, audit and webhook behaviour as staff.
 */
public final class VsmAPI {

    private final ActionService actions;
    private final PunishmentService punishments;
    private final PermissionService permissions;

    public VsmAPI(ActionService actions, PunishmentService punishments, PermissionService permissions) {
        this.actions = actions;
        this.punishments = punishments;
        this.permissions = permissions;
    }

    /** @return {@code true} when the player has an active ban. */
    public boolean isBanned(UUID uuid) {
        return active(uuid).stream().anyMatch(p -> p.type().isBan());
    }

    /** @return {@code true} when the player has an active account mute. */
    public boolean isMuted(UUID uuid) {
        return active(uuid).stream().anyMatch(p -> p.type().blocksChat() && !p.type().isIp());
    }

    public boolean isWarned(UUID uuid) {
        return active(uuid).stream().anyMatch(p -> p.type().isWarn());
    }

    public boolean isFrozen(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isDead() == false && io.github.aethelreborn.vsm.VsmRuntime.get() != null
                && io.github.aethelreborn.vsm.VsmRuntime.get().freezeService().isFrozen(uuid);
    }

    public List<Punishment> getActivePunishments(UUID uuid) {
        return active(uuid);
    }

    public List<Punishment> getActivePunishments(UUID uuid, PunishmentType type) {
        List<Punishment> out = new ArrayList<>();
        for (Punishment punishment : active(uuid)) {
            if (punishment.type() == type) {
                out.add(punishment);
            }
        }
        return out;
    }

    private List<Punishment> active(UUID uuid) {
        return punishments.activeFor(uuid);
    }

    /** @return the rank id of a whitelisted account, or {@code null}. */
    public String getStaffRank(UUID uuid) {
        return permissions.rankOf(uuid);
    }

    public boolean isStaff(UUID uuid) {
        return permissions.isStaff(uuid);
    }

    public StaffMember getStaffMember(UUID uuid) {
        return io.github.aethelreborn.vsm.VsmRuntime.get() == null
                ? null : io.github.aethelreborn.vsm.VsmRuntime.get().staffCache().byUuid(uuid);
    }

    /**
     * Issues a punishment as {@code actor}, subject to every check in {@link ActionService}.
     *
     * @param durationMillis milliseconds, or {@link TimeParser#PERMANENT}
     */
    public ActionResult issue(CommandSender actor, UUID targetUuid, String targetName, PunishmentType type,
                              String reason, long durationMillis, boolean silent) {
        return actions.execute(ActionRequest.builder(ActionSource.API)
                .kind(ActionRequest.Kind.PUNISH)
                .actor(actor instanceof Player player ? player.getUniqueId() : null,
                        actor == null ? "console" : actor.getName())
                .permission(permissionFor(type))
                .punishment(type)
                .target(targetUuid, targetName)
                .reason(reason)
                .duration(durationMillis)
                .silent(silent)
                , actor);
    }

    /** Removes a punishment by id as {@code actor}. */
    public ActionResult remove(CommandSender actor, String punishmentId, String reason) {
        return actions.execute(ActionRequest.builder(ActionSource.API)
                .kind(ActionRequest.Kind.REMOVE_PUNISHMENT)
                .actor(actor instanceof Player player ? player.getUniqueId() : null,
                        actor == null ? "console" : actor.getName())
                .permission("vsm.punishment.remove")
                .reason(reason)
                .extra(punishmentId)
                , actor);
    }

    private static String permissionFor(PunishmentType type) {
        return switch (type) {
            case WARN, TEMP_WARN -> "vsm.warn";
            case MUTE, TEMP_MUTE -> "vsm.tempmute";
            case SHADOW_MUTE -> "vsm.shadowmute";
            case IP_MUTE, TEMP_IP_MUTE -> "vsm.ipmute";
            case KICK -> "vsm.kick";
            case BAN -> "vsm.ban";
            case TEMP_BAN -> "vsm.tempban";
            case IP_BAN -> "vsm.ipban";
            case TEMP_IP_BAN -> "vsm.tempipban";
            case NAME_BAN -> "vsm.nameban";
        };
    }
}