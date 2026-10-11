package io.github.aethelreborn.vsm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    @Test
    @DisplayName("generates the documented id shape")
    void generatesShape() {
        String id = IdGenerator.generate("VP", 6);
        assertTrue(id.startsWith("VP-"), id);
        assertEquals(9, id.length());
    }

    @Test
    @DisplayName("never uses ambiguous characters")
    void unambiguous() {
        String alphabetCharacters = "0O1IL";
        for (int i = 0; i < 200; i++) {
            String id = IdGenerator.generate("VP", 8);
            for (char c : alphabetCharacters.toCharArray()) {
                assertFalse(id.indexOf(c) >= 0, "id " + id + " contains ambiguous character " + c);
            }
        }
    }

    @Test
    @DisplayName("generates unique ids")
    void generatesUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            assertTrue(seen.add(IdGenerator.generate("VP", 6)), "duplicate id produced");
        }
    }

    @Test
    @DisplayName("retries until the id is free")
    void uniqueRetries() {
        String first = IdGenerator.generate("VP", 6);
        String second = IdGenerator.unique("VP", 6, 32, candidate -> candidate.equals(first));
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("gives up rather than looping forever")
    void uniqueFails() {
        assertThrows(IllegalStateException.class, () -> IdGenerator.unique("VP", 6, 5, candidate -> true));
    }

    @Test
    @DisplayName("defaults the prefix when none is supplied")
    void defaultsPrefix() {
        assertTrue(IdGenerator.generate(null, 4).startsWith("VP-"));
        assertTrue(IdGenerator.generate("  ", 4).startsWith("VP-"));
    }

    @Test
    @DisplayName("recognises id shapes")
    void looksLikeId() {
        assertTrue(IdGenerator.looksLikeId("VP-8F3K2Q"));
        assertFalse(IdGenerator.looksLikeId("VP"));
        assertFalse(IdGenerator.looksLikeId("-8F3K2Q"));
        assertFalse(IdGenerator.looksLikeId(null));
        assertFalse(IdGenerator.looksLikeId("Steve"));
    }

    @Test
    @DisplayName("validates player names for name bans")
    void validatesNames() {
        assertTrue(IdGenerator.isValidPlayerName("Steve"));
        assertTrue(IdGenerator.isValidPlayerName("notch_1"));
        assertFalse(IdGenerator.isValidPlayerName("ab"));
        assertFalse(IdGenerator.isValidPlayerName("waytoolongplayername"));
        assertFalse(IdGenerator.isValidPlayerName("bad name"));
        assertFalse(IdGenerator.isValidPlayerName("CON"));
        assertFalse(IdGenerator.isValidPlayerName(null));
    }

    @Test
    @DisplayName("numeric mode produces zero padded codes")
    void numericMode() {
        assertEquals("000042", IdGenerator.numeric(42));
        assertEquals(6, IdGenerator.numericCode().length());
        assertTrue(IdGenerator.numericCode().chars().allMatch(Character::isDigit));
    }
}