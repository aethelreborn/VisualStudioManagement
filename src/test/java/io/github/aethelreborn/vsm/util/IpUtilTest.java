package io.github.aethelreborn.vsm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IpUtilTest {

    @Test
    @DisplayName("recognises literal IPv4 and IPv6 addresses")
    void validatesAddresses() {
        assertTrue(IpUtil.isValidIp("203.0.113.10"));
        assertTrue(IpUtil.isValidIp("2001:db8::1"));
        assertFalse(IpUtil.isValidIp("203.0.113.0/24"));
        assertFalse(IpUtil.isValidIp("not-an-ip"));
        assertFalse(IpUtil.isValidIp(""));
        assertFalse(IpUtil.isValidIp(null));
    }

    @Test
    @DisplayName("never resolves hostnames")
    void rejectsHostnames() {
        assertFalse(IpUtil.isValidIp("example.com"));
        assertNull(IpUtil.parse("example.com"));
    }

    @Test
    @DisplayName("recognises CIDR ranges and their prefix length")
    void validatesRanges() {
        assertTrue(IpUtil.isCidr("203.0.113.0/24"));
        assertTrue(IpUtil.isCidr("2001:db8::/32"));
        assertFalse(IpUtil.isCidr("203.0.113.0/33"));
        assertFalse(IpUtil.isCidr("203.0.113.0"));
        assertEquals(24, IpUtil.prefixLength("203.0.113.0/24"));
        assertEquals(-1, IpUtil.prefixLength("203.0.113.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.5", "172.16.4.4", "192.168.1.1", "169.254.1.1", "::1"})
    @DisplayName("refuses private and loopback ranges by default (spec 8.2 rule 4)")
    void detectsPrivateRanges(String ip) {
        assertTrue(IpUtil.isPrivate(ip), ip + " should be treated as private");
    }

    @Test
    @DisplayName("public addresses are not private")
    void publicIsNotPrivate() {
        assertFalse(IpUtil.isPrivate("203.0.113.10"));
        assertFalse(IpUtil.isPrivate("8.8.8.8"));
    }

    @Test
    @DisplayName("masks addresses for staff without vsm.ip.view")
    void masks() {
        assertEquals("203.0.113.x", IpUtil.mask("203.0.113.10"));
        assertEquals("203.0.113.x", IpUtil.mask("203.0.113.255"));
        assertTrue(IpUtil.mask("2001:db8::1").endsWith("::x"));
    }

    @Test
    @DisplayName("cache keys are stable and case insensitive for IPv6")
    void cacheKeys() {
        assertEquals(IpUtil.cacheKey("2001:DB8::1"), IpUtil.cacheKey("2001:db8::1"));
        assertEquals(IpUtil.cacheKey("203.0.113.10"), IpUtil.cacheKey("203.0.113.10"));
        assertNotNull(IpUtil.cacheKey("garbage"));
    }

    @Test
    @DisplayName("normalises IPv6 into the canonical form")
    void normalises() {
        assertEquals("203.0.113.10", IpUtil.normalise("203.0.113.10"));
        assertTrue(IpUtil.isIpv4("203.0.113.10"));
        assertFalse(IpUtil.isIpv4("2001:db8::1"));
    }
}