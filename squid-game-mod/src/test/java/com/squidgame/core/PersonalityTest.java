package com.squidgame.core;

import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PersonalityTest {
    @Test
    void traitsStayInRange() {
        Rng r = new Rng(1);
        for (int i = 0; i < 2000; i++) {
            Personality p = Personality.generate(r);
            for (float t : new float[]{p.courage(), p.reactionSpeed(), p.patience(), p.skill(),
                    p.aggression(), p.cooperation(), p.riskTolerance()}) {
                assertTrue(t >= 0.0f && t <= 1.0f);
            }
        }
    }

    @Test
    void archetypesProduceRecognisableDifferences() {
        Rng r = new Rng(2);
        double nervousCaution = 0, recklessCaution = 0;
        for (int i = 0; i < 300; i++) {
            nervousCaution += Personality.generate(r, Personality.Archetype.NERVOUS).caution();
            recklessCaution += Personality.generate(r, Personality.Archetype.RECKLESS).caution();
        }
        assertTrue(nervousCaution / 300 > recklessCaution / 300 + 0.25);
    }

    @Test
    void fastReactionsAreFasterOnAverage() {
        Rng r = new Rng(3);
        Personality fast = new Personality(Personality.Archetype.ATHLETE, .7f, .95f, .5f, .7f, .5f, .5f, .6f);
        Personality slow = new Personality(Personality.Archetype.ROOKIE, .4f, .05f, .5f, .3f, .3f, .5f, .5f);
        double f = 0, s = 0;
        for (int i = 0; i < 500; i++) {
            f += fast.reactionDelayTicks(r, Difficulty.NORMAL);
            s += slow.reactionDelayTicks(r, Difficulty.NORMAL);
        }
        assertTrue(f / 500 + 5 < s / 500);
    }

    @Test
    void harderDifficultyStrengthensOpponents() {
        Personality p = new Personality(Personality.Archetype.ROOKIE, .4f, .4f, .4f, .3f, .3f, .5f, .5f);
        assertTrue(p.effectiveSkill(Difficulty.EXTREME) > p.effectiveSkill(Difficulty.NORMAL));
    }

    @Test
    void appearancePackRoundTrips() {
        Rng r = new Rng(4);
        for (int i = 0; i < 500; i++) {
            Appearance a = Appearance.generate(r);
            Appearance b = Appearance.unpack(a.pack());
            assertEquals(a.skinTone(), b.skinTone());
            assertEquals(a.hairStyle(), b.hairStyle());
            assertEquals(a.hairColor(), b.hairColor());
            assertEquals(a.face(), b.face());
            assertEquals(a.glasses(), b.glasses());
            assertEquals(a.heightScale(), b.heightScale(), 0.011);
            assertEquals(a.widthScale(), b.widthScale(), 0.011);
        }
    }

    @Test
    void namesAreUnique() {
        Set<String> names = new HashSet<>(NameGenerator.generateUnique(new Rng(5), 200));
        assertTrue(names.size() > 150);
    }
}
