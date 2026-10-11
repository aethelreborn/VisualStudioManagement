package io.github.aethelreborn.vsm.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the universal punishment flags from spec 8.2:
 * {@code -s} silent, {@code -p} force public, {@code -e <url>} evidence, {@code -t <template>} template,
 * {@code -m} modify an active punishment. Flags may appear in any order before the reason.
 */
public final class FlagParser {

    private boolean silent;
    private boolean forcePublic;
    private boolean modify;
    private boolean confirm;
    private boolean dryRun;
    private String evidence;
    private String template;
    private final List<String> reasons = new ArrayList<>();

    public static FlagParser parse(List<String> args, int startIndex) {
        FlagParser parser = new FlagParser();
        if (args == null) {
            return parser;
        }
        for (int i = Math.max(0, startIndex); i < args.size(); i++) {
            String arg = args.get(i);
            if (arg == null) {
                continue;
            }
            String token = arg.trim();
            if (token.length() > 1 && token.charAt(0) == '-') {
                String name = token.substring(1).toLowerCase(Locale.ROOT);
                while (name.startsWith("-")) {
                    name = name.substring(1);
                }
                switch (name) {
                    case "s", "silent" -> parser.silent = true;
                    case "p", "public" -> parser.forcePublic = true;
                    case "m", "modify" -> parser.modify = true;
                    case "c", "confirm" -> parser.confirm = true;
                    case "dry-run", "dryrun" -> parser.dryRun = true;
                    case "e", "evidence" -> {
                        if (i + 1 < args.size()) {
                            parser.evidence = args.get(++i);
                        }
                    }
                    case "t", "template" -> {
                        if (i + 1 < args.size()) {
                            parser.template = args.get(++i);
                        }
                    }
                    default -> parser.reasons.add(token);
                }
            } else {
                parser.reasons.add(token);
            }
        }
        return parser;
    }

    /** Convenience for raw argument arrays. */
    public static FlagParser parse(String[] args, int startIndex) {
        return parse(args == null ? null : List.of(args), startIndex);
    }

    public boolean silent() {
        return silent;
    }

    public boolean forcePublic() {
        return forcePublic;
    }

    public boolean modify() {
        return modify;
    }

    public boolean confirm() {
        return confirm;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public String evidence() {
        return evidence;
    }

    public String template() {
        return template;
    }

    /** The remaining words, joined back into the reason. */
    public String reason() {
        return String.join(" ", reasons).trim();
    }

    public List<String> reasonWords() {
        return List.copyOf(reasons);
    }

    public boolean hasReason() {
        return !reasons.isEmpty();
    }

    /** View of the flags for auditing and webhooks. */
    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("silent", Boolean.toString(silent));
        map.put("forcePublic", Boolean.toString(forcePublic));
        map.put("modify", Boolean.toString(modify));
        if (evidence != null) {
            map.put("evidence", evidence);
        }
        if (template != null) {
            map.put("template", template);
        }
        return map;
    }

    @Override
    public String toString() {
        return "FlagParser{silent=" + silent + ", forcePublic=" + forcePublic + ", modify=" + modify
                + ", evidence=" + evidence + ", template=" + template + ", reason='" + reason() + "'}";
    }
}