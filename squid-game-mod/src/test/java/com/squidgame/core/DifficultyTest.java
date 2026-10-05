package com.squidgame.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyTest {
    @Test
    void harderPresetsAreStrictlyTougher() {
        Difficulty[] d = Difficulty.values();
        for (int i = 1; i < d.length; i++) {
            assertTrue(d[i].timeScale < d[i - 1].timeScale, "less time");
            assertTrue(d[i].toleranceScale < d[i - 1].toleranceScale, "tighter tolerance");
            assertTrue(d[i].resourceScale < d[i - 1].resourceScale, "scarcer resources");
            assertTrue(d[i].npcSkillBonus > d[i - 1].npcSkillBonus, "stronger opponents");
            assertTrue(d[i].allowanceScale < d[i - 1].allowanceScale, "shorter allowances");
        }
    }

    @Test
    void normalIsTheNeutralPreset() {
        assertEquals(1.0, Difficulty.NORMAL.timeScale);
        assertEquals(1.0, Difficulty.NORMAL.toleranceScale);
        assertEquals(1.0, Difficulty.NORMAL.resourceScale);
        assertEquals(0.0, Difficulty.NORMAL.npcSkillBonus);
        assertEquals(1.0, Difficulty.NORMAL.allowanceScale);
    }

    @Test
    void lookupIsCaseInsensitiveWithFallback() {
        assertEquals(Difficulty.HARD, Difficulty.byId("HaRd", Difficulty.NORMAL));
        assertEquals(Difficulty.EXTREME, Difficulty.byId("extreme", Difficulty.NORMAL));
        assertEquals(Difficulty.NORMAL, Difficulty.byId("nonsense", Difficulty.NORMAL));
        assertEquals(Difficulty.HARD, Difficulty.byId(null, Difficulty.HARD));
    }

    @Test
    void nextCyclesThroughAllPresets() {
        assertEquals(Difficulty.HARD, Difficulty.NORMAL.next());
        assertEquals(Difficulty.EXTREME, Difficulty.HARD.next());
        assertEquals(Difficulty.NORMAL, Difficulty.EXTREME.next());
    }

    @Test
    void gameKindsHaveStableIds() {
        for (GameKind g : GameKind.values()) {
            assertSame(g, GameKind.byId(g.id));
            assertEquals("squidgame.game." + g.id + ".title", g.titleKey());
        }
        assertNull(GameKind.byId("nope"));
    }
}
