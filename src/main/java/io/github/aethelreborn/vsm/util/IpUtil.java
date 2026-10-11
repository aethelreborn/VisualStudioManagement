package io.github.aethelreborn.vsm.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** IP address helpers: validation, normalisation and CIDR maths. */
public final class IpUtil {

    private IpUtil() {
    }

    /** @return {@code true} when the text is a literal IPv4 or IPv6 address. */
    public static boolean isValidIp(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.contains("/")) {
            return false;
        }
        return parse(trimmed) != null;
    }

    /** @return {@code true} when the text looks like {@code a.b.c.d/nn} or an IPv6 prefix. */
    public static boolean isCidr(String input) {
        if (input == null) {
            return false;
        }
        int slash = input.lastIndexOf('/');
        if (slash <= 0 || slash == input.length() - 1) {
            return false;
        }
        String address = input.substring(0, slash);
        String prefix = input.substring(slash + 1);
        if (address.isEmpty() || prefix.isEmpty()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (!Character.isDigit(prefix.charAt(i))) {
                return false;
            }
        }
        InetAddress parsed = parse(address);
        if (parsed == null) {
            return false;
        }
        int bits;
        try {
            bits = Integer.parseInt(prefix);
        } catch (NumberFormatException ex) {
            return false;
        }
        return bits >= 0 && bits <= parsed.getAddress().length * 8;
    }

    /** Parses a literal address without ever doing DNS. */
    public static InetAddress parse(String literal) {
        if (literal == null || literal.isBlank()) {
            return null;
        }
        String text = literal.trim();
        // Strip an IPv6 zone id such as fe80::1%eth0
        int zone = text.indexOf('%');
        if (zone > 0) {
            text = text.substring(0, zone);
        }
        boolean looksNumeric = isNumericLiteral(text);
        if (!looksNumeric) {
            return null;
        }
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static boolean isNumericLiteral(String text) {
        if (text.isEmpty()) {
            return false;
        }
        if (text.indexOf(':') >= 0) {
            // IPv6: only hex digits, colons and an optional embedded IPv4 tail
            for (int i = 0; i < text.length(); i++) {
                char c = Character.toLowerCase(text.charAt(i));
                boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == ':' || c == '.';
                if (!ok) {
                    return false;
                }
            }
            return true;
        }
        int dots = 0;
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.') {
                dots++;
            } else if (Character.isDigit(c)) {
                digits++;
            } else {
                return false;
            }
        }
        return dots == 3 && digits > 0;
    }

    /** Canonical textual form of an address, or the input unchanged when it cannot be parsed. */
    public static String normalise(String input) {
        InetAddress address = parse(input);
        return address == null ? input.trim() : address.getHostAddress();
    }

    public static boolean isIpv4(String input) {
        InetAddress address = parse(input);
        return address != null && address.getAddress().length == 4;
    }

    public static boolean isPrivate(String input) {
        InetAddress address = parse(input);
        if (address == null) {
            return true; // refuse anything we cannot classify
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int p0 = b[0] & 0xFF;
            int p1 = b[1] & 0xFF;
            return p0 == 10
                    || p0 == 127
                    || (p0 == 172 && p1 >= 16 && p1 <= 31)
                    || (p0 == 192 && p1 == 168)
                    || (p0 == 169 && p1 == 254)
                    || p0 == 0;
        }
        if (b.length == 16) {
            boolean allZeroButLoopback = true;
            for (int i = 0; i < 15; i++) {
                if (b[i] != 0) {
                    allZeroButLoopback = false;
                    break;
                }
            }
            if (allZeroButLoopback && b[15] == 1) {
                return true; // ::1
            }
            if (b[0] == 0xFE && (b[1] & 0xC0) == 0x80) {
                return true; // fe80::/10 link local
            }
            if ((b[0] & 0xFE) == 0xFC) {
                return true; // fc00::/7 unique local
            }
        }
        return false;
    }

    /** {@code 203.0.113.x} style masking used when the viewer lacks {@code vsm.ip.view}. */
    public static String mask(String input) {
        InetAddress address = parse(input);
        if (address == null) {
            String trimmed = input == null ? "unknown" : input.trim();
            int lastDot = trimmed.lastIndexOf('.');
            if (lastDot > 0 && trimmed.indexOf(':') < 0) {
                return trimmed.substring(0, lastDot + 1) + "x";
            }
            return "masked";
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            return (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + ".x";
        }
        if (b.length == 16) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                if (i > 0) {
                    out.append(':');
                }
                out.append(Integer.toHexString(((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF)));
            }
            out.append("::x");
            return out.toString();
        }
        return "masked";
    }

    /** Keeps at most the first two hextets, e.g. {@code 2001:db8::x}. */
    public static String maskV6Hextets(String input) {
        return mask(input);
    }

    /** Deterministic, stable lowercase representation used as a map key. */
    public static String cacheKey(String input) {
        if (input == null) {
            return "";
        }
        InetAddress address = parse(input);
        if (address == null) {
            return input.trim().toLowerCase(Locale.ROOT);
        }
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }

    /** @return the prefix length of a CIDR string, or {@code -1} when it is not a CIDR. */
    public static int prefixLength(String cidr) {
        int slash = cidr == null ? -1 : cidr.lastIndexOf('/');
        if (slash < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(cidr.substring(slash + 1).trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}