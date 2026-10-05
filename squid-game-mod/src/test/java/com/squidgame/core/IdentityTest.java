package com.squidgame.core;

import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Names and appearances: uniqueness, determinism, variety and the entity-data packing. */
class IdentityTest {
    @Test
    void namesAreUniqueAndDeterministic() {
        List<String> a = NameGenerator.generateUnique(new Rng(11), 200);
        List<String> b = NameGenerator.generateUnique(new Rng(11), 200);
        assertEquals(a, b);
        assertEquals(200, new HashSet<>(a).size());
    }

    @Test
    void appearancePackingRoundTrips() {
        Rng r = new Rng(21);
        for (int i = 0; i < 1000; i++) {
            Appearance a = Appearance.generate(r);
            Appearance u = Appearance.unpack(a.pack());
            assertEquals(a.skinTone(), u.skinTone());
            assertEquals(a.hairStyle(), u.hairStyle());
            assertEquals(a.hairColor(), u.hairColor());
            assertEquals(a.face(), u.face());
            assertEquals(a.glasses(), u.glasses());
            assertEquals(a.heightScale(), u.heightScale(), 0.006f);
            assertEquals(a.widthScale(), u.widthScale(), 0.006f);
        }
    }

    @Test
    void appearancesAreVariedAndInRange() {
        Rng r = new Rng(33);
        Set<String> combos = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            Appearance a = Appearance.generate(r);
            assertTrue(a.skinTone() < Appearance.SKIN_TONES.length);
            assertTrue(a.hairStyle() < Appearance.HAIR_STYLES);
            assertTrue(a.hairColor() < Appearance.HAIR_COLORS.length);
            assertTrue(a.face() < Appearance.FACES);
            assertTrue(a.heightScale() >= 0.93f && a.heightScale() <= 1.06f);
            combos.add(a.skinTone() + "/" + a.hairStyle() + "/" + a.hairColor() + "/" + a.face() + "/" + a.glasses());
        }
        assertTrue(combos.size() > 250, "expected a lot of variety, got " + combos.size());
    }

    @Test
    void hairBonesCoverEveryStyle() {
        assertEquals(Appearance.HAIR_STYLES, Appearance.HAIR_BONES.length);
        assertNull(Appearance.HAIR_BONES[0]);
    }
}
