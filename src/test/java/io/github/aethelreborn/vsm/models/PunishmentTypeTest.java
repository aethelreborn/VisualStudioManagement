package io.github.aethelreborn.vsm.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PunishmentTypeTest {

    @Test
    @DisplayName("classifies types by family")
    void families() {
        assertTrue(PunishmentType.BAN.isBan());
        assertTrue(PunishmentType.TEMP_IP_BAN.isBan());
        assertTrue(PunishmentType.TEMP_MUTE.isMute());
        assertTrue(PunishmentType.SHADOW_MUTE.isMute());
        assertTrue(PunishmentType.WARN.isWarn());
        assertFalse(PunishmentType.KICK.isBan());
        assertFalse(PunishmentType.KICK.isMute());
    }

    @Test
    @DisplayName("knows which types need an account")
    void targetUuid() {
        assertTrue(PunishmentType.BAN.usesTargetUuid());
        assertFalse(PunishmentType.IP_BAN.usesTargetUuid());
        assertFalse(PunishmentType.IP_MUTE.usesTargetUuid());
    }

    @Test
    @DisplayName("knows which types block chat")
    void blocksChat() {
        assertTrue(PunishmentType.MUTE.blocksChat());
        assertTrue(PunishmentType.SHADOW_MUTE.blocksChat());
        assertTrue(PunishmentType.TEMP_IP_MUTE.blocksChat());
        assertFalse(PunishmentType.BAN.blocksChat());
        assertFalse(PunishmentType.WARN.blocksChat());
    }

    @Test
    @DisplayName("maps between permanent and temporary variants")
    void variants() {
        assertSame(PunishmentType.BAN, PunishmentType.TEMP_BAN.permanentVariant());
        assertSame(PunishmentType.TEMP_BAN, PunishmentType.BAN.temporaryVariant());
        assertSame(PunishmentType.MUTE, PunishmentType.TEMP_MUTE.permanentVariant());
        assertSame(PunishmentType.SHADOW_MUTE, PunishmentType.SHADOW_MUTE.permanentVariant());
        assertSame(PunishmentType.WARN, PunishmentType.WARN.permanentVariant());
    }

    @Test
    @DisplayName("parses from strings, tolerating case and separators")
    void parsing() {
        assertSame(PunishmentType.TEMP_BAN, PunishmentType.fromString("temp_ban"));
        assertSame(PunishmentType.TEMP_BAN, PunishmentType.fromString("Temp-Ban"));
        assertSame(PunishmentType.IP_BAN, PunishmentType.fromString(" ipban "));
        assertNull(PunishmentType.fromString("nope"));
        assertNull(PunishmentType.fromString(null));
    }

    @Test
    @DisplayName("produces stable config keys")
    void configKeys() {
        assertEquals("temp_ip_ban", PunishmentType.TEMP_IP_BAN.configKey());
        assertEquals("warn", PunishmentType.WARN.configKey());
    }
}