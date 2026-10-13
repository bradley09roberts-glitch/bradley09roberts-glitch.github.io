package com.grandline.balance;

import com.grandline.npc.GLNpc;
import com.grandline.npc.crew.CompanionRole;
import com.grandline.npc.crew.CrewManager;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Numbers for recruited companions. Enemies take theirs from the sea they live in ({@link Tuning}); a companion takes
 * its from its <b>captain's level</b>, so a crewmate recruited in East Blue keeps up when the captain reaches the New
 * World, and never outgrows the captain.
 * <ul>
 * <li><b>Blows:</b> {@link #DAMAGE_SHARE} of the light blow of the reference character of the captain's level (see
 * {@link Tuning#referenceBlow}): a companion helps, it does not carry. Its techniques scale from the same power
 * ({@link Tuning#npcTechniquePower}), and it uses them sparingly.</li>
 * <li><b>Health:</b> the reference character's health ({@link Tuning#referenceHealth}) times the role's factor
 * (front-line fighters 1.3, others 1.0). An ordinary foe's light blow takes 11-12 % of that, so a fighter weathers
 * about nine of them and a marksman or a support about seven: enough for a standard fight, and a companion that does
 * drop is only downed (see CompanionCare), never killed.</li>
 * <li><b>Bond:</b> each bond rank above 1 adds {@link #BOND_POWER} to blows and {@link #BOND_HEALTH} to health
 * (rank 5: +16 % and +20 %).</li>
 * </ul>
 * Values between two reference levels are interpolated, so a level-up is felt at once. Companions are never an elite
 * rank: they count as {@link Tuning.Rank#STANDARD} wherever a rank matters (per-blow caps in PvP, the threat readout).
 */
public final class CompanionTuning {
    private CompanionTuning() {}

    /** A companion's light blow against the reference character's. */
    public static final float DAMAGE_SHARE = 0.40f;
    /** Per bond rank above the first. */
    public static final float BOND_POWER = 0.04f, BOND_HEALTH = 0.05f;
    /** Rank companions count as (never elite: they are not enemies to be worn down). */
    public static final Tuning.Rank RANK = Tuning.Rank.STANDARD;

    /** The reference character's light blow at this level (interpolated between the reference levels of the seas). */
    public static float referenceBlowAt(int level) {
        return lerp(level, true);
    }

    public static float referenceHealthAt(int level) {
        return lerp(level, false);
    }

    private static float lerp(int level, boolean blow) {
        level = Math.max(1, level);
        int t = Tuning.tierOfLevel(level);
        float a = blow ? Tuning.referenceBlow(t) : Tuning.referenceHealth(t);
        if (t >= 10) return a;
        int l0 = Tuning.referenceLevel(t), l1 = Tuning.referenceLevel(t + 1);
        float b = blow ? Tuning.referenceBlow(t + 1) : Tuning.referenceHealth(t + 1);
        float f = l1 > l0 ? Math.max(0, Math.min(1, (level - l0) / (float) (l1 - l0))) : 0;
        return a + (b - a) * f;
    }

    /** Base power of a companion's blows (a light blow is about 1x). */
    public static float power(int captainLevel, int bondRank) {
        return DAMAGE_SHARE * referenceBlowAt(captainLevel) * (1 + BOND_POWER * (clampRank(bondRank) - 1));
    }

    public static float health(int captainLevel, int bondRank, CompanionRole role) {
        return referenceHealthAt(captainLevel) * role.healthFactor * (1 + BOND_HEALTH * (clampRank(bondRank) - 1));
    }

    private static int clampRank(int r) {
        return Math.max(1, Math.min(5, r));
    }

    public static float power(GLNpc n) {
        return power(CrewManager.captainLevel(n), CrewManager.bondRank(n));
    }

    public static float health(GLNpc n) {
        return health(CrewManager.captainLevel(n), CrewManager.bondRank(n), CompanionRole.of(n));
    }

    public static Tuning.Rank rank(GLNpc n) {
        return RANK;
    }

    /** Whether the companion's attributes no longer match its captain's level and its bond. */
    public static boolean stale(GLNpc n) {
        return Math.abs(n.getMaxHealth() - Math.min(1024, Tuning.npcHealth(n, n.getMaxHealth()))) > 0.05f;
    }

    /** Set the companion's health (keeping its current share of it) and attack power from the numbers above. */
    public static void apply(GLNpc n) {
        float share = n.getMaxHealth() > 0 ? n.getHealth() / n.getMaxHealth() : 1f;
        AttributeInstance hp = n.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.min(1024, health(n)));
        AttributeInstance atk = n.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(power(n));
        n.setHealth(Math.max(1f, n.getMaxHealth() * Math.min(1f, share)));
    }
}
