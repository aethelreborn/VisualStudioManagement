package io.github.aethelreborn.vsm.services;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player, non-persistent runtime toggles (spec 11.1 alerts toggle, staff chat mode, spies). */
public final class StaffState {

    private final Map<UUID, Boolean> alerts = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> staffChat = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> commandSpy = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> socialSpy = new ConcurrentHashMap<>();

    public boolean alerts(UUID uuid) {
        return alerts.getOrDefault(uuid, Boolean.TRUE);
    }

    public void alerts(UUID uuid, boolean value) {
        alerts.put(uuid, value);
    }

    public boolean toggleAlerts(UUID uuid) {
        boolean value = !alerts(uuid);
        alerts.put(uuid, value);
        return value;
    }

    public boolean staffChatMode(UUID uuid) {
        return staffChat.getOrDefault(uuid, Boolean.FALSE);
    }

    public void staffChatMode(UUID uuid, boolean value) {
        staffChat.put(uuid, value);
    }

    public boolean toggleStaffChatMode(UUID uuid) {
        boolean value = !staffChatMode(uuid);
        staffChat.put(uuid, value);
        return value;
    }

    public boolean commandSpy(UUID uuid) {
        return commandSpy.getOrDefault(uuid, Boolean.FALSE);
    }

    public void commandSpy(UUID uuid, boolean value) {
        commandSpy.put(uuid, value);
    }

    public boolean toggleCommandSpy(UUID uuid) {
        boolean value = !commandSpy(uuid);
        commandSpy.put(uuid, value);
        return value;
    }

    public boolean socialSpy(UUID uuid) {
        return socialSpy.getOrDefault(uuid, Boolean.FALSE);
    }

    public void socialSpy(UUID uuid, boolean value) {
        socialSpy.put(uuid, value);
    }

    public boolean toggleSocialSpy(UUID uuid) {
        boolean value = !socialSpy(uuid);
        socialSpy.put(uuid, value);
        return value;
    }

    public void clear(UUID uuid) {
        alerts.remove(uuid);
        staffChat.remove(uuid);
        commandSpy.remove(uuid);
        socialSpy.remove(uuid);
    }
}