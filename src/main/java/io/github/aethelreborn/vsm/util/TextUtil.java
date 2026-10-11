package io.github.aethelreborn.vsm.util;

import java.util.regex.Pattern;

/** Input validation and sanitisation for anything a player can type (spec 13.3). */
public final class TextUtil {

    private static final Pattern MENTION = Pattern.compile("@(everyone|here|[!&]?[0-9]{5,})");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF\\u2060\\u180E]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private TextUtil() {
    }

    /** Strips control characters, keeping newlines and tabs. */
    public static String stripControl(String input) {
        if (input == null) {
            return null;
        }
        return CONTROL.matcher(input).replaceAll("");
    }

    /** Rejects input that contains control characters or zero-width abuse. */
    public static boolean hasControlCharacters(String input) {
        return input != null && CONTROL_CHARS.matcher(input.replace("\n", "").replace("\t", "")).find();
    }

    /** Removes Discord style mentions before text leaves the server (spec 13.3 / 18.4). */
    public static String stripMentions(String input) {
        return input == null ? null : MENTION.matcher(input).replaceAll("@\u200B");
    }

    /** Removes zero width characters used to bypass word filters. */
    public static String stripZeroWidth(String input) {
        return input == null ? null : ZERO_WIDTH.matcher(input).replaceAll("");
    }

    /** Full sanitisation used before storing a reason, note or report. */
    public static String sanitise(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        String value = stripZeroWidth(stripControl(input)).trim();
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
        return value;
    }

    /** Sanitisation applied before text is pushed to a webhook. */
    public static String sanitiseForWebhook(String input) {
        return stripMentions(sanitise(input, 512));
    }

    /**
     * Leetspeak normalisation so {@code f4gg} matches a {@code fagg} block entry.
     */
    public static String leet(String input) {
        if (input == null) {
            return "";
        }
        String out = stripZeroWidth(input).toLowerCase(java.util.Locale.ROOT);
        out = out.replace("0", "o").replace("1", "i").replace("3", "e").replace("4", "a")
                .replace("5", "s").replace("7", "t").replace("@", "a").replace("$", "s");
        return out.replaceAll("[^a-z\\s]", "");
    }

    /** @return {@code true} when the text is acceptable as a punishment reason. */
    public static boolean isValidReason(String reason, int minLength, int maxLength) {
        if (reason == null) {
            return false;
        }
        String value = reason.trim();
        if (value.length() < Math.max(1, minLength) || value.length() > maxLength) {
            return false;
        }
        return !hasControlCharacters(value);
    }

    /** Capitalises the first letter, used for enum names shown to staff. */
    public static String prettyEnum(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String text = value.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Escapes text for use in a CSV export cell. */
    public static String csv(String input) {
        if (input == null) {
            return "";
        }
        String value = input.replace("\"", "\"\"");
        return "\"" + value.replace("\n", " ").replace("\r", " ") + "\"";
    }

    /** Truncates for chat display, appending an ellipsis when shortened. */
    public static String truncate(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        return input.length() <= maxLength ? input : input.substring(0, Math.max(0, maxLength - 1)) + "\u2026";
    }
}