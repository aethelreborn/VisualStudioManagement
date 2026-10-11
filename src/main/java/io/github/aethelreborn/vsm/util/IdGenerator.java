package io.github.aethelreborn.vsm.util;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Generates short, readable, unambiguous punishment ids such as {@code VP-8F3K2Q}. */
public final class IdGenerator {

    /** Ambiguity free alphabet: no 0/O, 1/I/L. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    /**
     * Generates a candidate id.
     *
     * @param prefix id prefix such as {@code VP}
     * @param length number of characters after the dash
     */
    public static String generate(String prefix, int length) {
        int size = Math.max(1, length);
        StringBuilder out = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        String head = prefix == null || prefix.isBlank() ? "VP" : prefix.trim().toUpperCase(Locale.ROOT);
        return head + "-" + out;
    }

    /** Generates an id, retrying until {@code exists} reports it as free. */
    public static String unique(String prefix, int length, int attempts, Predicate<String> exists) {
        for (int i = 0; i < Math.max(1, attempts); i++) {
            String candidate = generate(prefix, length);
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not generate a unique punishment id after " + attempts + " attempts");
    }

    /**
     * Numeric mode, used by the optional "obscured numeric" setting so ids do not leak totals.
     */
    public static String numeric(long value) {
        StringBuilder out = new StringBuilder(Long.toString(Math.abs(value)));
        while (out.length() < 6) {
            out.insert(0, '0');
        }
        return out.toString();
    }

    /** Random six digit numeric code. */
    public static String numericCode() {
        StringBuilder out = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            out.append(DIGITS[RANDOM.nextInt(DIGITS.length)]);
        }
        return out.toString();
    }

    /** @return {@code true} when the text has the shape of a punishment id produced by this class. */
    public static boolean looksLikeId(String input) {
        if (input == null) {
            return false;
        }
        String text = input.trim();
        int dash = text.indexOf('-');
        if (dash <= 0 || dash == text.length() - 1) {
            return false;
        }
        String head = text.substring(0, dash);
        String tail = text.substring(dash + 1);
        for (int i = 0; i < head.length(); i++) {
            if (!Character.isLetterOrDigit(head.charAt(i))) {
                return false;
            }
        }
        if (tail.length() < 4) {
            return false;
        }
        for (int i = 0; i < tail.length(); i++) {
            char c = Character.toUpperCase(tail.charAt(i));
            if (c != '-' && c != '_' && !Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL");

    /** @return {@code true} when a name may be used for {@code /nameban}. */
    public static boolean isValidPlayerName(String name) {
        if (name == null) {
            return false;
        }
        String text = name.trim();
        if (text.length() < 3 || text.length() > 16) {
            return false;
        }
        if (RESERVED.contains(text.toUpperCase(Locale.ROOT))) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}