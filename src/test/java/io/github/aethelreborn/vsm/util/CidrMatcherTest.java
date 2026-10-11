package io.github.aethelreborn.vsm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CidrMatcherTest {

    @Test
    @DisplayName("matches a /24 range")
    void matchesSlash24() {
        CidrMatcher matcher = CidrMatcher.parse("203.0.113.0/24");
        assertTrue(matcher.matches("203.0.113.1"));
        assertTrue(matcher.matches("203.0.113.255"));
        assertFalse(matcher.matches("203.0.114.1"));
    }

    @Test
    @DisplayName("matches a single address as /32")
    void matchesSlash32() {
        CidrMatcher matcher = CidrMatcher.parse("203.0.113.10/32");
        assertTrue(matcher.matches("203.0.113.10"));
        assertFalse(matcher.matches("203.0.113.11"));
    }

    @Test
    @DisplayName("matches a /8 range")
    void matchesSlash8() {
        CidrMatcher matcher = CidrMatcher.parse("203.0.0.0/8");
        assertTrue(matcher.matches("203.1.2.3"));
        assertFalse(matcher.matches("204.1.2.3"));
    }

    @Test
    @DisplayName("matches IPv6 ranges")
    void matchesIpv6() {
        CidrMatcher matcher = CidrMatcher.parse("2001:db8::/32");
        assertTrue(matcher.matches("2001:db8:1234::1"));
        assertFalse(matcher.matches("2001:db9::1"));
    }

    @Test
    @DisplayName("never matches across address families")
    void neverCrossesFamilies() {
        CidrMatcher matcher = CidrMatcher.parse("203.0.113.0/24");
        assertFalse(matcher.matches("2001:db8::1"));
        CidrMatcher v6 = CidrMatcher.parse("2001:db8::/32");
        assertFalse(v6.matches("203.0.113.1"));
    }

    @Test
    @DisplayName("rejects malformed ranges")
    void rejectsMalformed() {
        assertNull(CidrMatcher.parse("203.0.113.0"));
        assertNull(CidrMatcher.parse("203.0.113.0/33"));
        assertNull(CidrMatcher.parse("garbage/24"));
        assertNull(CidrMatcher.parse(null));
    }

    @Test
    @DisplayName("exposes the normalised network address")
    void exposesNetwork() {
        assertEquals(24, CidrMatcher.parse("203.0.113.0/24").prefixLength());
        assertEquals(8, CidrMatcher.parse("10.0.0.0/8").prefixLength());
        assertEquals(0, CidrMatcher.parse("0.0.0.0/0").prefixLength());
    }
}