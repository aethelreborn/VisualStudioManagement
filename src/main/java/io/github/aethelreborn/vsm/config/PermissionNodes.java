package io.github.aethelreborn.vsm.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every permission node from spec section 7 plus the rank hierarchy from section 17.2.
 *
 * <p>Nodes are data, not code: the tree below is the single source of truth used to register the nodes with
 * Bukkit, to build the default rank grant sets and to drive {@code requires(...)} predicates.
 */
public final class PermissionNodes {

    private PermissionNodes() {
    }

    // ---------------------------------------------------------------- root
    public static final String USE = "vsm.use";
    public static final String ALL = "vsm.*";

    // ------------------------------------------------------------- chat
    public static final String STAFF_CHAT = "vsm.staffchat";
    public static final String STAFF_CHAT_TOGGLE = "vsm.staffchat.toggle";
    public static final String ADMIN_CHAT = "vsm.adminchat";
    public static final String ALERTS = "vsm.alerts";

    // ------------------------------------------------------- punishments
    public static final String WARN = "vsm.warn";
    public static final String UNWARN = "vsm.unwarn";
    public static final String WARNINGS = "vsm.warnings";
    public static final String MUTE = "vsm.mute";
    public static final String TEMP_MUTE = "vsm.tempmute";
    public static final String UNMUTE = "vsm.unmute";
    public static final String SHADOW_MUTE = "vsm.shadowmute";
    /** Lets staff see messages a shadow-muted player sends. */
    public static final String SHADOW_MUTE_SEE = "vsm.shadowmute.see";
    public static final String IP_MUTE = "vsm.ipmute";
    public static final String KICK = "vsm.kick";
    public static final String TEMP_BAN = "vsm.tempban";
    public static final String BAN = "vsm.ban";
    public static final String UNBAN = "vsm.unban";
    public static final String IP_BAN = "vsm.ipban";
    public static final String IP_BAN_RANGE = "vsm.ipban.range";
    public static final String TEMP_IP_BAN = "vsm.tempipban";
    public static final String IP_UNBAN = "vsm.ipunban";
    public static final String NAME_BAN = "vsm.nameban";
    public static final String PUNISH = "vsm.punish";
    public static final String PUNISHMENT_MODIFY = "vsm.punishment.modify";
    public static final String PUNISHMENT_REMOVE = "vsm.punishment.remove";
    public static final String PUNISHMENT_SILENT = "vsm.punishment.silent";
    public static final String PUNISHMENT_UNLIMITED_PREFIX = "vsm.punishment.unlimited.";
    public static final String CHECK_BAN = "vsm.checkban";
    public static final String CHECK_MUTE = "vsm.checkmute";
    public static final String BAN_LIST = "vsm.banlist";
    public static final String MUTE_LIST = "vsm.mutelist";

    // ------------------------------------------------------------- info
    public static final String HISTORY = "vsm.history";
    public static final String HISTORY_OTHERS = "vsm.history.others";
    public static final String NOTES_VIEW = "vsm.notes.view";
    public static final String NOTES_ADD = "vsm.notes.add";
    public static final String NOTES_REMOVE = "vsm.notes.remove";
    public static final String ALTS = "vsm.alts";
    public static final String IP_HISTORY = "vsm.iphistory";
    public static final String IP_VIEW = "vsm.ip.view";
    public static final String LOOKUP = "vsm.lookup";
    public static final String SEEN = "vsm.seen";

    // ----------------------------------------------------------- reports
    public static final String REPORT = "vsm.report";
    public static final String HELP_OP = "vsm.helpop";
    public static final String REPORTS_VIEW = "vsm.reports.view";
    public static final String REPORTS_CLAIM = "vsm.reports.claim";
    public static final String REPORTS_CLOSE = "vsm.reports.close";
    public static final String REPORTS_TELEPORT = "vsm.reports.teleport";
    public static final String REPORTS_ASSIGN = "vsm.reports.assign";

    // ---------------------------------------------------------- movement
    public static final String TP = "vsm.tp";
    public static final String TP_OTHERS = "vsm.tp.others";
    public static final String TP_HERE = "vsm.tphere";
    public static final String TP_ALL = "vsm.tpall";
    public static final String TP_POS = "vsm.tppos";
    public static final String BACK = "vsm.back";
    public static final String TOP = "vsm.top";
    public static final String TP_OFFLINE = "vsm.tpoffline";
    public static final String FLY = "vsm.fly";
    public static final String SPEED = "vsm.speed";
    public static final String GAMEMODE = "vsm.gamemode";
    public static final String SPECTATE = "vsm.spectate";
    public static final String FOLLOW = "vsm.follow";
    public static final String STAFF_MODE = "vsm.staffmode";

    // ------------------------------------------------- investigation
    public static final String FREEZE = "vsm.freeze";
    public static final String FREEZE_EXEMPT = "vsm.freeze.exempt";
    public static final String VANISH = "vsm.vanish";
    public static final String VANISH_SEE = "vsm.vanish.see";
    public static final String VANISH_INTERACT = "vsm.vanish.interact";
    public static final String INVSEE = "vsm.invsee";
    public static final String INVSEE_EDIT = "vsm.invsee.edit";
    public static final String ENDERSEE = "vsm.endersee";
    public static final String COMMAND_SPY = "vsm.commandspy";
    public static final String SOCIAL_SPY = "vsm.socialspy";
    public static final String WATCH = "vsm.watch";
    public static final String CLEAR_CHAT = "vsm.clearchat";
    public static final String MUTE_CHAT = "vsm.mutechat";
    public static final String SLOWMODE = "vsm.slowmode";
    public static final String CHAT_BYPASS = "vsm.chat.bypass";
    public static final String LOCKDOWN = "vsm.lockdown";
    public static final String MAINTENANCE = "vsm.maintenance";
    public static final String MAINTENANCE_BYPASS = "vsm.maintenance.bypass";

    // ------------------------------------------------------- management
    public static final String STAFF_MANAGE = "vsm.staff.manage";
    public static final String STAFF_LIST = "vsm.staff.list";
    public static final String STAFF_INFO = "vsm.staff.info";
    public static final String STAFF_RANKS = "vsm.staff.ranks";
    public static final String RELOAD = "vsm.reload";
    public static final String IMPORT = "vsm.import";
    public static final String EXPORT = "vsm.export";
    public static final String PURGE = "vsm.purge";
    public static final String DEBUG = "vsm.debug";
    public static final String AUDIT_VIEW = "vsm.audit.view";
    public static final String AUDIT_EXPORT = "vsm.audit.export";
    public static final String OVERRIDE = "vsm.override";

    // --------------------------------------------------------- youtuber
    public static final String YOUTUBER = "vsm.youtuber";
    public static final String YOUTUBER_FREEZE = "vsm.youtuber.freeze";
    public static final String YOUTUBER_UNFREEZE = "vsm.youtuber.unfreeze";
    public static final String YOUTUBER_MOVE = "vsm.youtuber.move";
    public static final String YOUTUBER_MOVE_OTHERS = "vsm.youtuber.move.others";
    public static final String YOUTUBER_TP = "vsm.youtuber.tp";
    public static final String YOUTUBER_GM_SURVIVAL = "vsm.youtuber.gamemode.survival";
    public static final String YOUTUBER_GM_ADVENTURE = "vsm.youtuber.gamemode.adventure";
    public static final String YOUTUBER_GM_CREATIVE = "vsm.youtuber.gamemode.creative";
    public static final String YOUTUBER_GM_TOGGLE = "vsm.youtuber.gamemode.toggle";
    public static final String YOUTUBER_CREATIVE_WORLDS_BYPASS = "vsm.youtuber.creative.worlds.bypass";

    // -------------------------------------------------------- exemptions
    public static final String EXEMPT_BAN = "vsm.exempt.ban";
    public static final String EXEMPT_MUTE = "vsm.exempt.mute";
    public static final String EXEMPT_KICK = "vsm.exempt.kick";
    public static final String EXEMPT_WARN = "vsm.exempt.warn";
    public static final String EXEMPT_IP = "vsm.exempt.ip";
    public static final String EXEMPT_AUTOMOD = "vsm.exempt.automod";

    // ------------------------------------------------------ group nodes
    public static final String GROUP_OWNER = "vsm.owner";
    public static final String GROUP_ADMIN = "vsm.admin";
    public static final String GROUP_SENIOR = "vsm.senior";
    public static final String GROUP_MODERATOR = "vsm.moderator";
    public static final String GROUP_HELPER = "vsm.helper";

    /** The two nodes every player gets. */
    public static final Set<String> DEFAULT_TRUE = Set.of(REPORT, HELP_OP);

    /** Every node declared by the plugin, with a short description. */
    public static Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(ALL, "Every Visual Studio Management permission");
        map.put(USE, "Base access to the plugin");

        map.put(STAFF_CHAT, "Use staff chat");
        map.put(STAFF_CHAT_TOGGLE, "Route normal chat into staff chat");
        map.put(ADMIN_CHAT, "Use admin chat");
        map.put(ALERTS, "Receive staff notifications");

        map.put(WARN, "Warn a player");
        map.put(UNWARN, "Remove a warning");
        map.put(WARNINGS, "List warnings");
        map.put(MUTE, "Permanently mute a player");
        map.put(TEMP_MUTE, "Temporarily mute a player");
        map.put(UNMUTE, "Unmute a player");
        map.put(SHADOW_MUTE, "Shadow mute a player");
        map.put(SHADOW_MUTE_SEE, "See messages a shadow-muted player sends");
        map.put(IP_MUTE, "Mute an IP address");
        map.put(KICK, "Kick a player");
        map.put(TEMP_BAN, "Temporarily ban a player");
        map.put(BAN, "Permanently ban a player");
        map.put(UNBAN, "Unban a player");
        map.put(IP_BAN, "Permanently IP ban");
        map.put(IP_BAN_RANGE, "IP ban a CIDR range");
        map.put(TEMP_IP_BAN, "Temporarily IP ban");
        map.put(IP_UNBAN, "Remove an IP ban");
        map.put(NAME_BAN, "Ban a username");
        map.put(PUNISH, "Apply a punishment template");
        map.put(PUNISHMENT_MODIFY, "Modify an active punishment");
        map.put(PUNISHMENT_REMOVE, "Remove any punishment by id");
        map.put(PUNISHMENT_SILENT, "Issue punishments silently");
        map.put(CHECK_BAN, "Check ban status");
        map.put(CHECK_MUTE, "Check mute status");
        map.put(BAN_LIST, "List active bans");
        map.put(MUTE_LIST, "List active mutes");

        map.put(HISTORY, "View punishment history");
        map.put(HISTORY_OTHERS, "View the punishment history issued by a staff member");
        map.put(NOTES_VIEW, "View staff notes");
        map.put(NOTES_ADD, "Add staff notes");
        map.put(NOTES_REMOVE, "Remove staff notes");
        map.put(ALTS, "View alternate accounts");
        map.put(IP_HISTORY, "View IP history");
        map.put(IP_VIEW, "See unmasked IP addresses");
        map.put(LOOKUP, "Look up a player summary");
        map.put(SEEN, "See when a player was last online");

        map.put(REPORT, "Report a player");
        map.put(HELP_OP, "Ask staff for help");
        map.put(REPORTS_VIEW, "View reports");
        map.put(REPORTS_CLAIM, "Claim a report");
        map.put(REPORTS_CLOSE, "Close a report");
        map.put(REPORTS_TELEPORT, "Teleport to a reported player");
        map.put(REPORTS_ASSIGN, "Assign a report to another staff member");

        map.put(TP, "Teleport to a player");
        map.put(TP_OTHERS, "Teleport one player to another");
        map.put(TP_HERE, "Bring a player to you");
        map.put(TP_ALL, "Teleport everyone to you");
        map.put(TP_POS, "Teleport to coordinates");
        map.put(BACK, "Return to your previous location");
        map.put(TOP, "Teleport to the highest block above you");
        map.put(TP_OFFLINE, "Teleport to an offline player");
        map.put(FLY, "Toggle flight");
        map.put(SPEED, "Change movement speed");
        map.put(GAMEMODE, "Change game mode");
        map.put(SPECTATE, "Spectate a player");
        map.put(FOLLOW, "Follow a player");
        map.put(STAFF_MODE, "Toggle staff mode");

        map.put(FREEZE, "Freeze and unfreeze players");
        map.put(FREEZE_EXEMPT, "Cannot be frozen");
        map.put(VANISH, "Toggle vanish");
        map.put(VANISH_SEE, "See vanished players");
        map.put(VANISH_INTERACT, "Interact while vanished");
        map.put(INVSEE, "View a player inventory");
        map.put(INVSEE_EDIT, "Edit a player inventory");
        map.put(ENDERSEE, "View a player ender chest");
        map.put(COMMAND_SPY, "Spy on player commands");
        map.put(SOCIAL_SPY, "Read private messages");
        map.put(WATCH, "Watchlist a player");
        map.put(CLEAR_CHAT, "Clear chat");
        map.put(MUTE_CHAT, "Toggle global chat lock");
        map.put(SLOWMODE, "Set chat slowmode");
        map.put(CHAT_BYPASS, "Bypass mutechat and slowmode");
        map.put(LOCKDOWN, "Toggle lockdown");
        map.put(MAINTENANCE, "Toggle maintenance mode");
        map.put(MAINTENANCE_BYPASS, "Join during maintenance");

        map.put(STAFF_MANAGE, "Add, remove and change staff ranks");
        map.put(STAFF_LIST, "List staff members");
        map.put(STAFF_INFO, "View staff member details");
        map.put(STAFF_RANKS, "Edit rank definitions");
        map.put(RELOAD, "Reload the plugin configuration");
        map.put(IMPORT, "Import punishments from another plugin");
        map.put(EXPORT, "Export plugin data");
        map.put(PURGE, "Purge player data and create backups");
        map.put(DEBUG, "Toggle debug logging");
        map.put(AUDIT_VIEW, "Browse the audit log");
        map.put(AUDIT_EXPORT, "Export the audit log");
        map.put(OVERRIDE, "Bypass outranking and exemption checks");

        map.put(YOUTUBER, "YouTuber role parent node");
        map.put(YOUTUBER_FREEZE, "YouTuber: freeze a player");
        map.put(YOUTUBER_UNFREEZE, "YouTuber: release a frozen player");
        map.put(YOUTUBER_MOVE, "YouTuber: move yourself");
        map.put(YOUTUBER_MOVE_OTHERS, "YouTuber: move another player");
        map.put(YOUTUBER_TP, "YouTuber: teleport");
        map.put(YOUTUBER_GM_SURVIVAL, "YouTuber: survival mode");
        map.put(YOUTUBER_GM_ADVENTURE, "YouTuber: adventure mode");
        map.put(YOUTUBER_GM_CREATIVE, "YouTuber: creative mode");
        map.put(YOUTUBER_GM_TOGGLE, "YouTuber: toggle creative and survival");
        map.put(YOUTUBER_CREATIVE_WORLDS_BYPASS, "YouTuber: creative in restricted worlds");

        map.put(EXEMPT_BAN, "Exempt from bans");
        map.put(EXEMPT_MUTE, "Exempt from mutes");
        map.put(EXEMPT_KICK, "Exempt from kicks");
        map.put(EXEMPT_WARN, "Exempt from warnings");
        map.put(EXEMPT_IP, "Exempt from IP punishments");
        map.put(EXEMPT_AUTOMOD, "Exempt from auto-moderation");

        map.put(GROUP_HELPER, "Helper rank group");
        map.put(GROUP_MODERATOR, "Moderator rank group");
        map.put(GROUP_SENIOR, "Senior moderator rank group");
        map.put(GROUP_ADMIN, "Admin rank group");
        map.put(GROUP_OWNER, "Owner rank group");
        return map;
    }

    /** Parent nodes mirroring spec 17.2. */
    public static Map<String, Set<String>> hierarchy() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        map.put(ALL, Set.of(GROUP_OWNER));
        map.put(GROUP_OWNER, new LinkedHashSet<>(List.of(
                GROUP_ADMIN, YOUTUBER, STAFF_RANKS, OVERRIDE, PURGE, DEBUG)));
        map.put(GROUP_ADMIN, new LinkedHashSet<>(List.of(
                GROUP_SENIOR, STAFF_MANAGE, BAN, UNBAN, IP_BAN, IP_UNBAN, TP_ALL, LOCKDOWN, RELOAD,
                IP_VIEW, INVSEE_EDIT, IMPORT, EXPORT, ADMIN_CHAT, AUDIT_VIEW)));
        map.put(GROUP_SENIOR, new LinkedHashSet<>(List.of(
                GROUP_MODERATOR, MUTE, TEMP_IP_BAN, PUNISHMENT_MODIFY, PUNISHMENT_REMOVE, ENDERSEE,
                TP_OFFLINE, CLEAR_CHAT, MUTE_CHAT, SLOWMODE, COMMAND_SPY, SHADOW_MUTE, IP_MUTE)));
        map.put(GROUP_MODERATOR, new LinkedHashSet<>(List.of(
                GROUP_HELPER, TEMP_MUTE, UNMUTE, KICK, TEMP_BAN, FREEZE, VANISH, INVSEE, TP_HERE, FLY,
                SPEED, GAMEMODE, STAFF_MODE, NOTES_ADD, ALTS, PUNISH, VANISH_SEE, SOCIAL_SPY)));
        map.put(GROUP_HELPER, new LinkedHashSet<>(List.of(
                USE, STAFF_CHAT, ALERTS, WARN, WARNINGS, HISTORY, REPORTS_VIEW, REPORTS_CLAIM,
                REPORTS_CLOSE, REPORTS_TELEPORT, TP, BACK, SPECTATE, SEEN, NOTES_VIEW, CHECK_BAN,
                CHECK_MUTE, STAFF_LIST, STAFF_INFO)));
        // Side branch: never a child of helper or above (spec 6.2a).
        map.put(YOUTUBER, new LinkedHashSet<>(List.of(
                YOUTUBER_FREEZE, YOUTUBER_UNFREEZE, YOUTUBER_MOVE, YOUTUBER_MOVE_OTHERS, YOUTUBER_TP,
                YOUTUBER_GM_SURVIVAL, YOUTUBER_GM_ADVENTURE, YOUTUBER_GM_CREATIVE, YOUTUBER_GM_TOGGLE)));
        return map;
    }

    /** Nodes granted by each built-in rank (spec 6.2). Flattened at load time. */
    public static Map<String, List<String>> rankDefaults() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("player", List.of(REPORT, HELP_OP));
        map.put("youtuber", List.of(
                YOUTUBER_FREEZE, YOUTUBER_UNFREEZE, YOUTUBER_MOVE, YOUTUBER_MOVE_OTHERS, YOUTUBER_TP,
                YOUTUBER_GM_SURVIVAL, YOUTUBER_GM_ADVENTURE, YOUTUBER_GM_CREATIVE, YOUTUBER_GM_TOGGLE));
        map.put("trainee", List.of(USE, STAFF_CHAT, ALERTS, REPORT, HELP_OP, REPORTS_VIEW, NOTES_VIEW, TP));
        map.put("helper", List.of(
                STAFF_CHAT, ALERTS, WARN, WARNINGS, UNWARN, HISTORY, REPORTS_VIEW, REPORTS_CLAIM,
                REPORTS_CLOSE, REPORTS_TELEPORT, TP, BACK, SPECTATE, SEEN, NOTES_VIEW, CHECK_BAN,
                CHECK_MUTE, STAFF_LIST, STAFF_INFO, LOOKUP, REPORT, HELP_OP));
        map.put("moderator", List.of(
                MUTE, TEMP_MUTE, UNMUTE, SHADOW_MUTE, KICK, TEMP_BAN, FREEZE, VANISH, VANISH_SEE, INVSEE,
                TP_HERE, FLY, SPEED, GAMEMODE, STAFF_MODE, NOTES_ADD, NOTES_VIEW, ALTS, PUNISH, SOCIAL_SPY,
                CHECK_BAN, CHECK_MUTE, BAN_LIST, MUTE_LIST, REPORT, HELP_OP));
        map.put("senior_mod", List.of(
                MUTE, TEMP_MUTE, UNMUTE, SHADOW_MUTE, IP_MUTE, KICK, TEMP_BAN, BAN, UNBAN, PUNISHMENT_MODIFY,
                PUNISHMENT_REMOVE, PUNISHMENT_SILENT, ENDERSEE, TP_OFFLINE, CLEAR_CHAT, MUTE_CHAT, SLOWMODE,
                COMMAND_SPY, TEMP_IP_BAN, ADMIN_CHAT, IP_HISTORY, PURGE, REPORT, HELP_OP));
        map.put("admin", List.of(
                BAN, UNBAN, IP_BAN, IP_UNBAN, IP_BAN_RANGE, NAME_BAN, TP_ALL, LOCKDOWN, RELOAD, IP_VIEW,
                INVSEE_EDIT, IMPORT, EXPORT, ADMIN_CHAT, AUDIT_VIEW, STAFF_MANAGE, STAFF_LIST, STAFF_INFO,
                MAINTENANCE, WATCH, REPORT, HELP_OP));
        map.put("owner", List.of(GROUP_OWNER, GROUP_ADMIN, GROUP_SENIOR, GROUP_MODERATOR, GROUP_HELPER,
                YOUTUBER, STAFF_RANKS, OVERRIDE, PURGE, DEBUG, AUDIT_EXPORT, PUNISHMENT_UNLIMITED_PREFIX + "ban"));
        return map;
    }

    /** Every node declared, in declaration order. */
    public static Set<String> allNodes() {
        return new LinkedHashSet<>(all().keySet());
    }

    /** Exemption node that protects a player from a punishment type, or {@code null}. */
    public static String exemptionFor(String punishmentTypeName) {
        if (punishmentTypeName == null) {
            return null;
        }
        String name = punishmentTypeName.toUpperCase(java.util.Locale.ROOT);
        if (name.contains("IP")) {
            // IP punishments are always covered by vsm.exempt.ip (spec 8.2 rule 4).
            return EXEMPT_IP;
        }
        if (name.contains("MUTE")) {
            return EXEMPT_MUTE;
        }
        if (name.contains("KICK")) {
            return EXEMPT_KICK;
        }
        if (name.contains("WARN")) {
            return EXEMPT_WARN;
        }
        if (name.contains("BAN")) {
            return EXEMPT_BAN;
        }
        return null;
    }
}