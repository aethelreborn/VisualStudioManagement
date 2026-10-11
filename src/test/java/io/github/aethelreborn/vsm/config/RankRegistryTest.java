package io.github.aethelreborn.vsm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.aethelreborn.vsm.models.StaffRank;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rank inheritance flattening and the outranking rule (spec 6.3, acceptance tests 21). */
class RankRegistryTest {

    private RankRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RankRegistry();
        YamlConfiguration config = new YamlConfiguration();
        config.set("ranks.player.display", "Player");
        config.set("ranks.player.weight", 0);
        config.set("ranks.player.staff", false);
        config.set("ranks.player.permissions", java.util.List.of("vsm.report", "vsm.helpop"));

        config.set("ranks.youtuber.display", "YouTuber");
        config.set("ranks.youtuber.weight", 15);
        config.set("ranks.youtuber.parent", "player");
        config.set("ranks.youtuber.staff", false);
        config.set("ranks.youtuber.permissions",
                java.util.List.of("vsm.youtuber.freeze", "vsm.youtuber.tp"));

        config.set("ranks.helper.display", "Helper");
        config.set("ranks.helper.weight", 20);
        config.set("ranks.helper.parent", "player");
        config.set("ranks.helper.permissions", java.util.List.of("vsm.warn", "vsm.tp"));

        config.set("ranks.moderator.display", "Moderator");
        config.set("ranks.moderator.weight", 40);
        config.set("ranks.moderator.parent", "helper");
        config.set("ranks.moderator.permissions", java.util.List.of("vsm.kick"));

        config.set("ranks.admin.display", "Admin");
        config.set("ranks.admin.weight", 80);
        config.set("ranks.admin.parent", "moderator");
        config.set("ranks.admin.permissions", java.util.List.of("vsm.ban"));

        config.set("ranks.owner.display", "Owner");
        config.set("ranks.owner.weight", 100);
        config.set("ranks.owner.parent", "admin");
        config.set("ranks.owner.permissions", java.util.List.of("vsm.override"));

        config.set("staff.default-rank", "helper");
        registry.load(config);
    }

    @Test
    @DisplayName("inherits every permission down the ladder")
    void inheritsDownTheLadder() {
        StaffRank owner = registry.rank("owner");
        assertNotNull(owner);
        assertTrue(owner.hasNode("vsm.report"));
        assertTrue(owner.hasNode("vsm.warn"));
        assertTrue(owner.hasNode("vsm.kick"));
        assertTrue(owner.hasNode("vsm.ban"));
        assertTrue(owner.hasNode("vsm.override"));
    }

    @Test
    @DisplayName("the youtuber side branch never inherits staff powers")
    void youtuberIsSideBranch() {
        StaffRank youtuber = registry.rank("youtuber");
        assertNotNull(youtuber);
        assertTrue(youtuber.hasNode("vsm.youtuber.freeze"));
        assertTrue(youtuber.hasNode("vsm.report"));
        assertFalse(youtuber.hasNode("vsm.warn"));
        assertFalse(youtuber.hasNode("vsm.kick"));
        assertFalse(youtuber.hasNode("vsm.ban"));
    }

    @Test
    @DisplayName("the youtuber role is not a staff rank")
    void youtuberIsNotStaff() {
        assertFalse(registry.isStaffRank("youtuber"));
        assertFalse(registry.isStaffRank("player"));
        assertTrue(registry.isStaffRank("helper"));
        assertTrue(registry.isStaffRank("owner"));
    }

    @Test
    @DisplayName("weights order the ladder ascending")
    void ladderOrdering() {
        var ladder = registry.ladder();
        assertEquals("player", ladder.get(0).id());
        assertEquals("owner", ladder.get(ladder.size() - 1).id());
        for (int i = 1; i < ladder.size(); i++) {
            assertTrue(ladder.get(i).weight() > ladder.get(i - 1).weight());
        }
    }

    @Test
    @DisplayName("promote and demote move one step")
    void promoteDemote() {
        StaffRank promoted = registry.promote("helper");
        assertNotNull(promoted);
        assertEquals("moderator", promoted.id());
        StaffRank demoted = registry.demote("helper");
        assertNotNull(demoted);
        assertEquals("player", demoted.id());
        assertNull(registry.promote("owner"));
        assertNull(registry.demote("player"));
    }

    @Test
    @DisplayName("promote and demote skip the youtuber side branch (spec 6.2a)")
    void sideBranchIsNotALadderStep() {
        assertEquals("helper", registry.promote("youtuber").id());
        assertEquals("player", registry.demote("youtuber").id());
        assertTrue(registry.isSideBranch(registry.rank("youtuber")));
        assertFalse(registry.isSideBranch(registry.rank("moderator")));
    }

    @Test
    @DisplayName("outranking requires a strictly lower weight")
    void outranking() {
        assertTrue(RankRegistry.outranks(80, 40));
        assertFalse(RankRegistry.outranks(40, 40));
        assertFalse(RankRegistry.outranks(40, 80));
    }

    @Test
    @DisplayName("atLeast compares weights")
    void atLeast() {
        assertTrue(registry.atLeast("admin", "moderator"));
        assertFalse(registry.atLeast("helper", "moderator"));
        assertTrue(registry.atLeast("moderator", "moderator"));
        assertTrue(registry.atLeast("helper", null));
    }

    @Test
    @DisplayName("copes with a missing rank")
    void missingRank() {
        assertNull(registry.rank("does-not-exist"));
        assertFalse(registry.exists("does-not-exist"));
        assertEquals(Integer.MIN_VALUE, registry.weight("does-not-exist"));
    }

    @Test
    @DisplayName("a cyclic parent chain cannot loop forever")
    void cycleGuard() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ranks.a.display", "A");
        config.set("ranks.a.weight", 1);
        config.set("ranks.a.parent", "b");
        config.set("ranks.b.display", "B");
        config.set("ranks.b.weight", 2);
        config.set("ranks.b.parent", "a");
        registry.load(config);
        assertNotNull(registry.rank("a"));
        assertTrue(registry.rank("a").flattenedPermissions().isEmpty());
    }
}