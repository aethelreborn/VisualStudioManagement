package io.github.aethelreborn.vsm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlagParserTest {

    @Test
    @DisplayName("parses the universal flags in any order (spec 8.2)")
    void parsesFlags() {
        FlagParser flags = FlagParser.parse(new String[]{"-s", "Spamming", "chat"}, 0);
        assertTrue(flags.silent());
        assertEquals("Spamming chat", flags.reason());

        FlagParser reordered = FlagParser.parse(new String[]{"Spamming", "-s", "chat"}, 0);
        assertTrue(reordered.silent());
        assertEquals("Spamming chat", reordered.reason());
    }

    @Test
    @DisplayName("parses evidence and template values")
    void parsesValues() {
        FlagParser flags = FlagParser.parse(
                new String[]{"-e", "https://example.com/a.png", "-t", "hacking", "cheating"}, 0);
        assertEquals("https://example.com/a.png", flags.evidence());
        assertEquals("hacking", flags.template());
        assertEquals("cheating", flags.reason());
    }

    @Test
    @DisplayName("treats unknown flags as part of the reason")
    void keepsUnknownFlags() {
        FlagParser flags = FlagParser.parse(new String[]{"-z", "weird reason"}, 0);
        assertNull(flags.evidence());
        assertEquals("-z weird reason", flags.reason());
    }

    @Test
    @DisplayName("supports force public, modify, confirm and dry-run")
    void parsesToggles() {
        FlagParser flags = FlagParser.parse(new String[]{"-p", "-m", "-c", "--dry-run", "reason"}, 0);
        assertTrue(flags.forcePublic());
        assertTrue(flags.modify());
        assertTrue(flags.confirm());
        assertTrue(flags.dryRun());
        assertEquals("reason", flags.reason());
    }

    @Test
    @DisplayName("honours the start index")
    void honoursStartIndex() {
        FlagParser flags = FlagParser.parse(new String[]{"ignored", "-s", "reason"}, 1);
        assertTrue(flags.silent());
        assertEquals("reason", flags.reason());
    }

    @Test
    @DisplayName("is empty safe")
    void emptySafe() {
        FlagParser flags = FlagParser.parse((java.util.List<String>) null, 0);
        assertFalse(flags.silent());
        assertFalse(flags.hasReason());
        assertEquals("", flags.reason());
    }

    @Test
    @DisplayName("produces a flag map for the audit log")
    void flagMap() {
        FlagParser flags = FlagParser.parse(new String[]{"-s", "-e", "url", "reason"}, 0);
        Set<String> keys = new HashSet<>(flags.asMap().keySet());
        assertTrue(keys.contains("silent"));
        assertTrue(keys.contains("evidence"));
        assertEquals("true", flags.asMap().get("silent"));
    }
}