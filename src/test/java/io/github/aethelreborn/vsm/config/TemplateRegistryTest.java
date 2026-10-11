package io.github.aethelreborn.vsm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aethelreborn.vsm.models.PunishmentType;
import io.github.aethelreborn.vsm.util.TimeParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Template and ladder resolution (spec 9.3). */
class TemplateRegistryTest {

    private TemplateRegistry registry;

    @BeforeEach
    void setUp() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("templates.spam.display", "Chat spam");
        config.set("templates.spam.reason", "Spamming chat");
        config.set("templates.spam.ladder", java.util.List.of(
                java.util.Map.of("type", "WARN"),
                java.util.Map.of("type", "TEMP_MUTE", "duration", "30m"),
                java.util.Map.of("type", "TEMP_MUTE", "duration", "6h")));

        config.set("templates.hacking.display", "Hacked client");
        config.set("templates.hacking.reason", "Use of unfair modifications");
        config.set("templates.hacking.ladder", java.util.List.of(
                java.util.Map.of("type", "TEMP_BAN", "duration", "7d",
                        "flags", java.util.List.of("evidence-required")),
                java.util.Map.of("type", "BAN")));

        config.set("warning-thresholds.3.type", "TEMP_MUTE");
        config.set("warning-thresholds.3.duration", "1h");
        config.set("warning-thresholds.3.reason", "3 warnings reached");
        config.set("warning-thresholds.5.type", "TEMP_BAN");
        config.set("warning-thresholds.5.duration", "1d");

        registry = new TemplateRegistry();
        registry.load(config, null);
    }

    @Test
    @DisplayName("loads templates and ladders")
    void loads() {
        assertTrue(registry.exists("spam"));
        assertTrue(registry.exists("hacking"));
        assertEquals(2, registry.all().size());
        assertFalse(registry.hasProblems());
    }

    @Test
    @DisplayName("picks the ladder step matching the prior count")
    void picksStep() {
        var spam = registry.template("spam");
        assertNotNull(spam);
        assertEquals(PunishmentType.WARN, spam.stepFor(0).type());
        assertEquals(PunishmentType.TEMP_MUTE, spam.stepFor(1).type());
        assertEquals(TimeParser.parse("30m"), spam.stepFor(1).duration());
        assertEquals(TimeParser.parse("6h"), spam.stepFor(2).duration());
    }

    @Test
    @DisplayName("the last step repeats once the ladder is exhausted")
    void lastStepRepeats() {
        var spam = registry.template("spam");
        assertEquals(spam.stepFor(2).type(), spam.stepFor(9).type());
        assertEquals(spam.stepFor(2).duration(), spam.stepFor(9).duration());
    }

    @Test
    @DisplayName("reads step flags")
    void readsFlags() {
        var hacking = registry.template("hacking");
        assertNotNull(hacking);
        assertTrue(hacking.stepFor(0).hasFlag("evidence-required"));
        assertFalse(hacking.stepFor(1).hasFlag("evidence-required"));
        assertEquals(PunishmentType.BAN, hacking.stepFor(1).type());
        assertEquals(TimeParser.PERMANENT, hacking.stepFor(1).duration());
    }

    @Test
    @DisplayName("resolves warning thresholds")
    void warningThresholds() {
        var three = registry.thresholdFor(3);
        assertNotNull(three);
        assertEquals(PunishmentType.TEMP_MUTE, three.type());
        assertEquals(TimeParser.parse("1h"), three.duration());
        var five = registry.thresholdFor(5);
        assertNotNull(five);
        assertEquals(PunishmentType.TEMP_BAN, five.type());
        assertNull(registry.thresholdFor(2));
    }

    @Test
    @DisplayName("collects problems instead of throwing on a bad template")
    void collectsProblems() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("templates.broken.display", "Broken");
        config.set("templates.broken.ladder", java.util.List.of(
                java.util.Map.of("type", "NOT_A_TYPE"),
                java.util.Map.of("type", "TEMP_BAN", "duration", "not-a-duration")));
        TemplateRegistry broken = new TemplateRegistry();
        broken.load(config, null);
        assertTrue(broken.hasProblems());
        assertFalse(broken.exists("broken"));
    }

    @Test
    @DisplayName("rejects a template with no ladder")
    void rejectsEmptyLadder() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("templates.empty.display", "Empty");
        config.set("templates.empty.ladder", java.util.List.of());
        TemplateRegistry empty = new TemplateRegistry();
        empty.load(config, null);
        assertFalse(empty.exists("empty"));
        assertTrue(empty.hasProblems());
    }

    @Test
    @DisplayName("template lookup is case insensitive")
    void caseInsensitive() {
        assertNotNull(registry.template("SPAM"));
        assertNull(registry.template(null));
    }
}