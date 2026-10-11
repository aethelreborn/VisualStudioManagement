package io.github.aethelreborn.vsm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimeParserTest {

    @Test
    @DisplayName("parses every supported unit")
    void parsesUnits() {
        assertEquals(1_000L, TimeParser.parse("1s"));
        assertEquals(60_000L, TimeParser.parse("1m"));
        assertEquals(3_600_000L, TimeParser.parse("1h"));
        assertEquals(86_400_000L, TimeParser.parse("1d"));
        assertEquals(604_800_000L, TimeParser.parse("1w"));
        assertEquals(30L * 86_400_000L, TimeParser.parse("1mo"));
        assertEquals(365L * 86_400_000L, TimeParser.parse("1y"));
    }

    @Test
    @DisplayName("parses combined forms such as 1d12h and 2w3d")
    void parsesCombined() {
        assertEquals(86_400_000L + 12 * 3_600_000L, TimeParser.parse("1d12h"));
        assertEquals(2 * 604_800_000L + 3 * 86_400_000L, TimeParser.parse("2w3d"));
        assertEquals(86_400_000L + 2 * 3_600_000L + 30 * 60_000L, TimeParser.parse("1d2h30m"));
    }

    @Test
    @DisplayName("permanent tokens map to -1")
    void permanentTokens() {
        assertEquals(TimeParser.PERMANENT, TimeParser.parse("perm"));
        assertEquals(TimeParser.PERMANENT, TimeParser.parse("permanent"));
        assertEquals(TimeParser.PERMANENT, TimeParser.parse("FOREVER"));
        assertTrue(TimeParser.isPermanentToken("perm"));
        assertFalse(TimeParser.isPermanentToken("1h"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "0", "0s", "-5m", "1", "10x", "1.5h", "d1", "99999999999999999999y"})
    @DisplayName("rejects malformed input (spec 9.1 / acceptance test 21)")
    void rejectsMalformed(String input) {
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parse(input));
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parse(null));
    }

    @Test
    @DisplayName("formats durations as human text")
    void formats() {
        assertEquals("1 day, 12 hours", TimeParser.format(86_400_000L + 12 * 3_600_000L));
        assertEquals("30 minutes", TimeParser.format(1_800_000L));
        assertEquals("1 second", TimeParser.format(1_000L));
        assertEquals("permanent", TimeParser.format(TimeParser.PERMANENT));
        assertEquals("2 seconds", TimeParser.format(2_000L));
    }

    @Test
    @DisplayName("formats short durations for broadcasts")
    void formatsShort() {
        assertEquals("30m", TimeParser.formatShort(1_800_000L));
        assertEquals("7d", TimeParser.formatShort(604_800_000L));
        assertEquals("permanent", TimeParser.formatShort(TimeParser.PERMANENT));
    }

    @Test
    @DisplayName("parseOr falls back instead of throwing")
    void parseOrFallsBack() {
        assertEquals(42L, TimeParser.parseOr("nonsense", 42L));
        assertEquals(60_000L, TimeParser.parseOr("1m", 42L));
    }

    @Test
    @DisplayName("remaining time never shows negative values")
    void remaining() {
        long now = 1_000_000L;
        assertEquals("Never", TimeParser.formatRemaining(TimeParser.PERMANENT, now));
        assertEquals("Expired", TimeParser.formatRemaining(now - 1, now));
        assertEquals("1 hour", TimeParser.formatRemaining(now + 3_600_000L, now));
    }
}