package io.github.aethelreborn.vsm.models;

import java.util.UUID;

/** A private staff note. Pinned notes are shown to staff when the player joins (spec 8.3). */
public final class PlayerNote {

    private String id = "";
    private UUID target;
    private String targetName = "";
    private UUID author;
    private String authorName = "";
    private String text = "";
    private long createdAt;
    private boolean pinned;

    public PlayerNote() {
    }

    public static PlayerNote of(UUID target, String targetName, UUID author, String authorName, String text) {
        PlayerNote note = new PlayerNote();
        note.target = target;
        note.targetName = targetName;
        note.author = author;
        note.authorName = authorName;
        note.text = text;
        note.createdAt = System.currentTimeMillis();
        return note;
    }

    public String id() {
        return id;
    }

    public PlayerNote id(String id) {
        this.id = id;
        return this;
    }

    public UUID target() {
        return target;
    }

    public PlayerNote target(UUID target) {
        this.target = target;
        return this;
    }

    public String targetName() {
        return targetName;
    }

    public PlayerNote targetName(String targetName) {
        this.targetName = targetName;
        return this;
    }

    public UUID author() {
        return author;
    }

    public PlayerNote author(UUID author) {
        this.author = author;
        return this;
    }

    public String authorName() {
        return authorName;
    }

    public PlayerNote authorName(String authorName) {
        this.authorName = authorName;
        return this;
    }

    public String text() {
        return text;
    }

    public PlayerNote text(String text) {
        this.text = text;
        return this;
    }

    public long createdAt() {
        return createdAt;
    }

    public PlayerNote createdAt(long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public boolean pinned() {
        return pinned;
    }

    public PlayerNote pinned(boolean pinned) {
        this.pinned = pinned;
        return this;
    }

    @Override
    public String toString() {
        return "PlayerNote{" + id + " on " + targetName + "}";
    }
}