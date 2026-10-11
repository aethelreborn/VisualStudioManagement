package io.github.aethelreborn.vsm.services;

import io.github.aethelreborn.vsm.models.ActionSource;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A live inventory view opened by {@code /invsee} or {@code /endersee} (spec 11.4).
 *
 * <p>The session is read-only unless the viewer holds {@code vsm.invsee.edit}; every view and every write
 * is audited.
 */
public final class InvseeSession {

    private final UUID viewerUuid;
    private final UUID targetUuid;
    private final String targetName;
    private final boolean editable;
    private final boolean enderChest;
    private final Inventory mirror;
    private final PlayerInventorySnapshot snapshot;
    private final long openedAt;
    private final boolean offlineTarget;

    public InvseeSession(UUID viewerUuid, UUID targetUuid, String targetName, boolean editable,
                         boolean enderChest, Inventory mirror, PlayerInventorySnapshot snapshot,
                         boolean offlineTarget) {
        this.viewerUuid = viewerUuid;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.editable = editable;
        this.enderChest = enderChest;
        this.mirror = mirror;
        this.snapshot = snapshot;
        this.offlineTarget = offlineTarget;
        this.openedAt = System.currentTimeMillis();
    }

    public UUID viewerUuid() {
        return viewerUuid;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public boolean editable() {
        return editable;
    }

    public boolean enderChest() {
        return enderChest;
    }

    public Inventory mirror() {
        return mirror;
    }

    public PlayerInventorySnapshot snapshot() {
        return snapshot;
    }

    public boolean offlineTarget() {
        return offlineTarget;
    }

    public long openedAt() {
        return openedAt;
    }

    /**
     * Writes the mirror back to the target when editing is allowed.
     *
     * <p>Refuses the write if the target joined while the view was open, so a stale view can never
     * overwrite live data (spec 11.4).
     */
    public void flush() {
        if (!editable) {
            return;
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            return; // never write back to an offline or disconnected player
        }
        for (int slot = 0; slot < snapshot.size(); slot++) {
            ItemStack current = snapshot.get(slot);
            ItemStack updated = slot < mirror.getSize() ? mirror.getItem(slot) : null;
            if (java.util.Objects.equals(current, updated)) {
                continue;
            }
            target.getInventory().setItem(slot, updated);
        }
    }

    /** Immutable copy of an inventory at the moment the view was opened. */
    public static final class PlayerInventorySnapshot {

        private final ItemStack[] contents;

        public PlayerInventorySnapshot(ItemStack[] contents) {
            this.contents = contents.clone();
        }

        public ItemStack get(int slot) {
            return slot >= 0 && slot < contents.length ? contents[slot] : null;
        }

        public int size() {
            return contents.length;
        }
    }

    public static PlayerInventorySnapshot snapshotOf(org.bukkit.inventory.PlayerInventory inventory) {
        ItemStack[] copy = new ItemStack[inventory.getSize()];
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            copy[slot] = inventory.getItem(slot);
        }
        return new PlayerInventorySnapshot(copy);
    }

    public String auditAction() {
        return (enderChest ? "ENDERSEE_VIEW" : "INVSEE_VIEW") + (editable ? "_EDIT" : "");
    }

    public ActionSource source() {
        return ActionSource.COMMAND;
    }
}