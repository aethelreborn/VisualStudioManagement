package io.github.aethelreborn.vsm.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aethelreborn.vsm.util.TimeParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PunishmentTest {

    @Test
    @DisplayName("a punishment is active until its expiry, then not")
    void activeUntilExpiry() {
        long now = 1_000_000L;
        Punishment punishment = Punishment.of(PunishmentType.TEMP_MUTE, java.util.UUID.randomUUID(),
                "Steve", "Spam");
        punishment.issuedAt(now).expiresAt(now + 60_000L);

        assertTrue(punishment.isActiveNow(now));
        assertTrue(punishment.isActiveNow(now + 59_999L));
        assertFalse(punishment.isActiveNow(now + 60_000L));
        assertFalse(punishment.isActiveNow(now + 60_001L));
    }

    @Test
    @DisplayName("a permanent punishment never expires (spec 9.4 lazy check)")
    void permanentNeverExpires() {
        long now = 1_000_000L;
        Punishment punishment = Punishment.of(PunishmentType.BAN, java.util.UUID.randomUUID(),
                "Steve", "Cheating");
        punishment.issuedAt(now).expiresAt(TimeParser.PERMANENT);
        assertTrue(punishment.isActiveNow(Long.MAX_VALUE));
        assertTrue(punishment.isPermanent());
    }

    @Test
    @DisplayName("removal clears the active flag")
    void removal() {
        Punishment punishment = Punishment.of(PunishmentType.WARN, java.util.UUID.randomUUID(),
                "Steve", "Spam");
        punishment.issuedAt(1L).expiresAt(TimeParser.PERMANENT).id("VP-ABC123");
        java.util.UUID staff = java.util.UUID.randomUUID();
        punishment.remove(staff, "Alex", "appeal accepted", 5L);
        assertFalse(punishment.active());
        assertEquals(staff, punishment.removedBy());
        assertEquals("Alex", punishment.removedByName());
        assertEquals(5L, punishment.removedAt());
        assertFalse(punishment.isActiveNow(System.currentTimeMillis()));
    }

    @Test
    @DisplayName("remaining time never goes negative")
    void remaining() {
        long now = 1_000L;
        Punishment punishment = Punishment.of(PunishmentType.TEMP_BAN, null, "Steve", "x");
        punishment.expiresAt(now - 500L);
        assertEquals(0L, punishment.remainingMillis(now));
        punishment.expiresAt(TimeParser.PERMANENT);
        assertEquals(TimeParser.PERMANENT, punishment.remainingMillis(now));
    }

    @Test
    @DisplayName("identity is the id")
    void identity() {
        Punishment a = new Punishment().id("VP-1");
        Punishment b = new Punishment().id("VP-1");
        Punishment c = new Punishment().id("VP-2");
        assertEquals(a, b);
        assertFalse(a.equals(c));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("display target prefers the name, then the uuid, then the ip")
    void displayTarget() {
        Punishment punishment = new Punishment().targetName("Steve");
        assertEquals("Steve", punishment.displayTarget());
        java.util.UUID uuid = java.util.UUID.randomUUID();
        punishment.targetName(null).targetUuid(uuid);
        assertEquals(uuid.toString(), punishment.displayTarget());
        punishment.targetUuid(null).targetIp("203.0.113.1");
        assertEquals("203.0.113.1", punishment.displayTarget());
    }

    @Test
    @DisplayName("YouTuber scoped records are flagged")
    void youTuberScope() {
        Punishment punishment = new Punishment();
        assertFalse(punishment.isYouTuberScope());
        punishment.scope("ytb:" + java.util.UUID.randomUUID());
        assertTrue(punishment.isYouTuberScope());
        assertFalse(new Punishment().scope(null).isYouTuberScope());
    }
}