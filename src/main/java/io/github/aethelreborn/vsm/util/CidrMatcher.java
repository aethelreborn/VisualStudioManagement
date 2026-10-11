package io.github.aethelreborn.vsm.util;

import java.net.InetAddress;
import java.util.Arrays;

/**
 * CIDR range matching used by IP bans ({@code /vsm ipban 203.0.113.0/24}).
 *
 * <p>Addresses are compared on their raw bytes so the same code works for IPv4 and IPv6.
 */
public final class CidrMatcher {

    private final byte[] network;
    private final int prefixLength;

    public CidrMatcher(InetAddress address, int prefixLength) {
        byte[] bytes = address.getAddress();
        int maxBits = bytes.length * 8;
        if (prefixLength < 0 || prefixLength > maxBits) {
            throw new IllegalArgumentException("prefix length " + prefixLength + " out of range for " + maxBits + " bit address");
        }
        this.network = Arrays.copyOf(bytes, bytes.length);
        this.prefixLength = prefixLength;
        mask(this.network, prefixLength);
    }

    /**
     * Builds a matcher from a CIDR string.
     *
     * @return the matcher, or {@code null} when the input is not a valid range
     */
    public static CidrMatcher parse(String cidr) {
        if (cidr == null) {
            return null;
        }
        String text = cidr.trim();
        int slash = text.lastIndexOf('/');
        if (slash <= 0) {
            return null;
        }
        InetAddress address = IpUtil.parse(text.substring(0, slash));
        if (address == null) {
            return null;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(text.substring(slash + 1).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        try {
            return new CidrMatcher(address, prefix);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** @return {@code true} when the address falls inside this range. */
    public boolean matches(InetAddress candidate) {
        if (candidate == null) {
            return false;
        }
        byte[] bytes = candidate.getAddress();
        if (bytes.length != network.length) {
            return false; // never match an IPv4 address against an IPv6 range
        }
        byte[] copy = Arrays.copyOf(bytes, bytes.length);
        mask(copy, prefixLength);
        return Arrays.equals(copy, network);
    }

    public boolean matches(String candidate) {
        return matches(IpUtil.parse(candidate));
    }

    public int prefixLength() {
        return prefixLength;
    }

    /** Normalises an address down to its network address. */
    public static byte[] networkBytes(String cidr) {
        CidrMatcher matcher = parse(cidr);
        if (matcher == null) {
            return null;
        }
        return matcher.network.clone();
    }

    private static void mask(byte[] bytes, int prefix) {
        for (int i = 0; i < bytes.length; i++) {
            int bitIndex = i * 8;
            if (bitIndex + 8 <= prefix) {
                continue;
            }
            if (bitIndex >= prefix) {
                bytes[i] = 0;
            } else {
                int keep = prefix - bitIndex;
                bytes[i] = (byte) (bytes[i] & (0xFF << (8 - keep)));
            }
        }
    }
}