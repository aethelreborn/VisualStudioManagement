package io.github.aethelreborn.vsm.util;

/** Minimal, dependency-free JSON writing used by exports and Discord webhooks (spec 18.4). */
public final class Json {

    private final StringBuilder builder = new StringBuilder(256);
    private boolean needsComma;

    public Json() {
        builder.append('{');
    }

    public static Json object() {
        return new Json();
    }

    /** Starts a nested object under {@code key}. */
    public Json object(String key) {
        comma();
        builder.append(quote(key)).append(":{");
        needsComma = false;
        return this;
    }

    /** Starts a nested array under {@code key}. */
    public Json array(String key) {
        comma();
        builder.append(quote(key)).append(":[");
        needsComma = false;
        return this;
    }

    public Json arrayItem() {
        comma();
        needsComma = true;
        return this;
    }

    public Json put(String key, String value) {
        comma();
        builder.append(quote(key)).append(':').append(value == null ? "null" : quote(value));
        needsComma = true;
        return this;
    }

    public Json put(String key, double value) {
        comma();
        builder.append(quote(key)).append(':').append(value);
        needsComma = true;
        return this;
    }

    public Json put(String key, float value) {
        return put(key, (double) value);
    }

    public Json put(String key, int value) {
        return put(key, (long) value);
    }

    public Json put(String key, long value) {
        comma();
        builder.append(quote(key)).append(':').append(value);
        needsComma = true;
        return this;
    }

    public Json put(String key, boolean value) {
        comma();
        builder.append(quote(key)).append(':').append(value);
        needsComma = true;
        return this;
    }

    public Json putRaw(String key, String rawJson) {
        comma();
        builder.append(quote(key)).append(':').append(rawJson == null ? "null" : rawJson);
        needsComma = true;
        return this;
    }

    /** Closes the current object and appends a key/value pair at the parent level. */
    public Json end() {
        builder.append('}');
        needsComma = true;
        return this;
    }

    public Json endArray() {
        builder.append(']');
        needsComma = true;
        return this;
    }

    public Json item(String value) {
        comma();
        builder.append(value == null ? "null" : quote(value));
        needsComma = true;
        return this;
    }

    private void comma() {
        if (needsComma) {
            builder.append(',');
        }
    }

    @Override
    public String toString() {
        return builder.toString() + "}";
    }

    /** Escapes a string into a JSON string literal, quotes included. */
    public static String quote(String input) {
        if (input == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        out.append('"');
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}