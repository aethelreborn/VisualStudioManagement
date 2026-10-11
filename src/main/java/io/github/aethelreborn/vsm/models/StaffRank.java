package io.github.aethelreborn.vsm.models;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A rank definition loaded from {@code ranks.yml} (spec 6.2). Ranks are data: weights, display, permissions
 * and limits are all configurable, and the permission set is flattened once at load time.
 */
public final class StaffRank {

    private String id;
    private String display;
    private int weight;
    private String parent;
    private String prefix = "";
    private boolean staff = true;
    private List<String> permissions = new ArrayList<>();
    private final Set<String> flattened = new LinkedHashSet<>();

    public StaffRank() {
    }

    public StaffRank(String id, String display, int weight, String parent) {
        this.id = id;
        this.display = display;
        this.weight = weight;
        this.parent = parent;
    }

    public String id() {
        return id;
    }

    public StaffRank id(String id) {
        this.id = id;
        return this;
    }

    public String display() {
        return display;
    }

    public StaffRank display(String display) {
        this.display = display;
        return this;
    }

    public int weight() {
        return weight;
    }

    public StaffRank weight(int weight) {
        this.weight = weight;
        return this;
    }

    public String parent() {
        return parent;
    }

    public StaffRank parent(String parent) {
        this.parent = parent;
        return this;
    }

    /** MiniMessage prefix, empty when the rank has none. */
    public String prefix() {
        return prefix;
    }

    public StaffRank prefix(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
        return this;
    }

    /**
     * {@code false} for side-branch roles such as {@code youtuber}, which keeps them out of staff-only
     * lists, notifications and webhooks (spec 6.2a).
     */
    public boolean staff() {
        return staff;
    }

    public StaffRank staff(boolean staff) {
        this.staff = staff;
        return this;
    }

    /** Permissions declared directly on this rank, before inheritance. */
    public List<String> permissions() {
        return permissions;
    }

    public StaffRank permissions(List<String> permissions) {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
        return this;
    }

    /** The immutable, fully resolved permission set including inherited nodes. */
    public Set<String> flattenedPermissions() {
        return flattened;
    }

    public void replaceFlattened(Set<String> permissions) {
        flattened.clear();
        flattened.addAll(permissions);
    }

    /** Clears the cache so the next {@code flatten} call recomputes it. */
    public void invalidate() {
        flattened.clear();
    }

    public boolean hasNode(String node) {
        return flattened.contains(node);
    }

    @Override
    public String toString() {
        return "StaffRank{" + id + " w=" + weight + "}";
    }
}