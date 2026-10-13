package com.grandline.balance;

import com.grandline.boss.BossManager;
import com.grandline.core.GLConfig;
import com.grandline.npc.Archetype;
import com.grandline.npc.GLNpc;
import com.grandline.player.Characters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * The one place combat numbers come from. Every enemy number is derived from two things only: the tier of the sea
 * it lives in (its encounter tier, 1-10) and its rank (minion, standard, elite, boss). Nothing scales with the
 * player who happens to be fighting it, so getting stronger, and recruiting a crew, always makes fights easier.
 * <p>
 * The targets (see BALANCE.md for the measured table):
 * <ul>
 * <li>a reference character of the level a sea is meant for, with a common weapon of that sea, fells a minion in 2-3
 * light blows, a standard foe in 4-5, an elite in about 10 (elites are meant to be beaten with guard, dodges and
 * techniques, not by trading blows), and a boss in about 45 light-blow equivalents (one to three minutes);</li>
 * <li>an ordinary foe's light blow takes 8-15 % of that character's health (an elite's 12-18 %), a heavy one about twice that;</li>
 * <li>no single blow that was not announced by a boss's ground marker can take more than a quarter (minions and
 * standard foes) to two fifths (bosses) of a character's health; announced boss attacks are capped at 55 %.</li>
 * </ul>
 * The reference character (level per sea, health, light-blow damage) is written down below; it is a yardstick for
 * the targets, never read from a live player.
 */
public final class Tuning {
    private Tuning() {}

    /** Trained fists (an unarmed fighting style with empty hands) hit like a common blade: +5, as a cutlass adds. */
    public static final float UNARMED_BONUS = 5f;
    /** Bumped whenever NPC health or armour rules change; NPCs saved with an older version are re-tuned when they load. */
    public static final int VERSION = 1;

    // ------------------------------------------------------------------ difficulty

    /** Server difficulty (config: balance.difficulty). Standard is the intended experience and is meant to be approachable. */
    public enum Difficulty {
        ADVENTURE(0.70f, 0.85f, 1, 0.85f), STANDARD(1f, 1f, 2, 1f), HARD(1.30f, 1.15f, 3, 1.15f);

        /** Multiplier on foes' damage, on foes' health, melee attackers allowed on one player at once, multiplier on the per-blow caps. */
        public final float enemyDamage, enemyHealth, capScale;
        public final int meleeSlots;

        Difficulty(float enemyDamage, float enemyHealth, int meleeSlots, float capScale) {
            this.enemyDamage = enemyDamage;
            this.enemyHealth = enemyHealth;
            this.meleeSlots = meleeSlots;
            this.capScale = capScale;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Difficulty byId(String s) {
            for (Difficulty d : values()) if (d.id().equalsIgnoreCase(s)) return d;
            return STANDARD;
        }
    }

    /**
     * The difficulty in force: the config value, or with "auto" (the default) the world's own difficulty setting
     * (Peaceful and Easy = Adventure, Normal = Standard, Hard = Hard). The mod's damage types do not scale with the
     * vanilla difficulty themselves, so this is the only difficulty multiplier on foes' blows.
     */
    public static Difficulty difficulty() {
        String v;
        try {
            v = GLConfig.DIFFICULTY.get();
        } catch (IllegalStateException notLoaded) {
            v = "auto";
        }
        if (!v.equalsIgnoreCase("auto")) return Difficulty.byId(v);
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Difficulty.STANDARD;
        return switch (server.getWorldData().getDifficulty()) {
            case PEACEFUL, EASY -> Difficulty.ADVENTURE;
            case NORMAL -> Difficulty.STANDARD;
            case HARD -> Difficulty.HARD;
        };
    }

    // ------------------------------------------------------------------ ranks

    public enum Rank {
        /** Bandits, deckhands and other rabble: few blows, soft hits. */
        MINION(2.5f, 0.09f, 0.22f, 24f),
        /** Marines, guards, Fish-Man soldiers, bounty hunters. */
        STANDARD(4.5f, 0.11f, 0.25f, 30f),
        /** Officers, captains, named characters, giants: guard and dodge more, use techniques; marked with a silver icon. */
        ELITE(10f, 0.12f, 0.32f, 70f),
        /** Story bosses with phases and ground-marked signature attacks; marked with a gold icon and a boss bar. */
        BOSS(45f, 0.13f, 0.40f, 150f);

        /** Light blows of the reference character to fell it; share of the reference health one light blow takes; per-blow cap; the archetype health this rank is measured against. */
        public final float hits, blowShare, cap, healthRef;

        Rank(float hits, float blowShare, float cap, float healthRef) {
            this.hits = hits;
            this.blowShare = blowShare;
            this.cap = cap;
            this.healthRef = healthRef;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final java.util.Set<String> ELITE_ROLES = java.util.Set.of("pirate_captain", "marine_officer", "marine_captain", "vice_admiral",
            "admiral", "warden", "cp_agent", "cp_leader", "celestial_guard", "master", "fishman_brute", "fishman_boss", "giant_warrior",
            "giant_elder", "jailer_beast", "ruin_guardian", "lunarian_warrior", "buccaneer_brawler", "kuja_warrior");

    public static Rank rank(LivingEntity e) {
        if (e instanceof GLNpc n) {
            if (BossManager.isBoss(n)) return Rank.BOSS;
            if (n.flag(GLNpc.FLAG_NOTABLE) || ELITE_ROLES.contains(n.role())) return Rank.ELITE;
            Archetype a = n.archetype();
            return a.coward() || a.hostility() == Archetype.Hostility.PASSIVE ? Rank.MINION : Rank.STANDARD;
        }
        if (e instanceof com.grandline.creature.SeaKing) return Rank.BOSS;
        if (e instanceof com.grandline.creature.WildBeast) return Rank.ELITE;
        return Rank.STANDARD;
    }

    // ------------------------------------------------------------------ the reference character, per sea tier 1-10

    /** Level a character is meant to have when the seas of each tier open up (East Blue 1-2, Paradise 3-5, New World 6-10). */
    private static final int[] LEVEL = {1, 8, 16, 26, 36, 46, 56, 68, 80, 92};
    /** Common weapon damage of each sea (cutlass 6, fine blades 7.5, skilful 9, supreme blades 12). */
    private static final float[] WEAPON = {6, 6, 7.5f, 7.5f, 7.5f, 9, 9, 9, 12, 12};

    public static int tierClamp(int t) {
        return Math.max(1, Math.min(10, t));
    }

    public static int referenceLevel(int tier) {
        return LEVEL[tierClamp(tier) - 1];
    }

    /** Health of a human of the reference level who put a third of their points into Endurance. */
    public static float referenceHealth(int tier) {
        int l = referenceLevel(tier);
        return 20 + l + (l / 5) * 2f;
    }

    /** Weapon mastery of the reference character: half its level (mastery grows by use; a character who fights with one weapon). */
    public static int referenceMastery(int tier) {
        return Math.min(100, referenceLevel(tier) / 2);
    }

    /** Light blow of that character: weapon + a third of their points in Strength, with the bonus of its weapon mastery. */
    public static float referenceBlow(int tier) {
        int l = referenceLevel(tier);
        float power = WEAPON[tierClamp(tier) - 1] + l * 0.25f;
        return power * (1 + referenceMastery(tier) * 0.006f);
    }

    /** The tier a character of this level is "at" (the highest sea tier whose reference level they have reached). */
    public static int tierOfLevel(int level) {
        int t = 1;
        for (int i = 0; i < LEVEL.length; i++) if (level >= LEVEL[i]) t = i + 1;
        return t;
    }

    // ------------------------------------------------------------------ enemies

    /**
     * The tier an enemy's numbers come from. Ordinary NPCs: their own tier (set from the island they live on and their
     * role). A story boss on a low-tier island fights one tier above its island (Gallowgate is a Shells Town boss,
     * not a Paradise one), whatever its lore rank, so each arc's boss is beatable by a character who reached that arc.
     */
    public static int encounterTier(LivingEntity e) {
        if (e instanceof GLNpc n) {
            if (BossManager.isBoss(n) && e.level() instanceof ServerLevel sl) {
                var plan = com.grandline.world.WorldPlan.forLevel(sl);
                var isl = plan == null || n.islandId().isEmpty() ? null : plan.byId(n.islandId());
                if (isl != null) return tierClamp(Math.min(n.tier(), isl.tier + 1));
            }
            return tierClamp(n.tier());
        }
        if (e instanceof com.grandline.creature.SeaKing sk) return tierClamp(sk.tier() * 3);
        if (e instanceof com.grandline.creature.WildBeast) return 3;
        return 1;
    }

    /** Maximum health of an NPC: the rank's blow count of the reference character, flavoured by its archetype (±25 %). */
    public static float npcHealth(Archetype a, int tier, Rank r) {
        float flavour = Math.max(0.8f, Math.min(1.25f, a.health() / r.healthRef));
        return Math.max(4, r.hits * referenceBlow(tier) * flavour * difficulty().enemyHealth);
    }

    /** Health of a named character (notables): elites and bosses by the same rule, from their encounter tier. */
    public static float notableHealth(int encounterTier, Rank r) {
        return Math.max(10, r.hits * referenceBlow(encounterTier) * difficulty().enemyHealth);
    }

    /** Base power of an NPC's blows (a light blow is about 1x, a heavy about 2.2x). Weapons only flavour it (+10 %). */
    public static float npcPower(GLNpc n) {
        int t = encounterTier(n);
        Rank r = rank(n);
        float weapon = n.getMainHandItem().isEmpty() ? 1f : 1.1f;
        return r.blowShare * referenceHealth(t) * weapon;
    }

    /**
     * Ability scale for NPC users, so that a typical technique (base 7) lands for about two light blows. Techniques
     * with a big base still meet the per-blow cap when they hit a player.
     */
    public static float npcTechniquePower(GLNpc n) {
        float masteryScale = 1f + Math.min(100, n.tier() * 10) / 100f;
        return 2f * npcPower(n) / (7f * masteryScale);
    }

    /** Signature (ground-marked) boss attacks: about a third of the reference health per power-1.0 move. */
    public static float bossStrike(GLNpc boss) {
        return 0.30f * referenceHealth(encounterTier(boss));
    }

    // ------------------------------------------------------------------ what reaches the player

    /** Foes' damage to a player: the difficulty multiplier, then the per-blow cap of the attacker's rank. */
    public static float incomingToPlayer(Player p, LivingEntity attacker, float amount, boolean announced) {
        if (!isFoeCreature(attacker)) return amount;
        Difficulty d = difficulty();
        amount *= d.enemyDamage;
        Rank r = rank(attacker);
        float cap = (announced && r == Rank.BOSS ? 0.55f : announced ? 0.40f : r.cap) * d.capScale;
        return Math.min(amount, Math.max(2f, p.getMaxHealth() * cap));
    }

    private static boolean isFoeCreature(LivingEntity e) {
        return e instanceof GLNpc || e instanceof com.grandline.creature.WildBeast || e instanceof com.grandline.creature.SeaKing;
    }

    /** Melee attackers allowed to press one victim at once (bosses never wait for a slot). */
    public static int meleeSlots(LivingEntity victim) {
        if (victim instanceof Player) return difficulty().meleeSlots;
        return 2;
    }

    // ------------------------------------------------------------------ the threat readout

    public enum Threat { WEAKER, MATCHED, DANGEROUS, OVERWHELMING }

    /** How dangerous a foe is for this player: its encounter tier against the tier the player's level reaches, plus rank. */
    public static Threat threat(Player p, LivingEntity foe) {
        int pt = tierOfLevel(Characters.get(p).level);
        int ft = encounterTier(foe);
        Rank r = rank(foe);
        int gap = ft - pt + (r == Rank.ELITE ? 1 : r == Rank.BOSS ? 1 : 0);
        if (gap <= -2) return Threat.WEAKER;
        if (gap <= 0) return Threat.MATCHED;
        if (gap <= 2) return Threat.DANGEROUS;
        return Threat.OVERWHELMING;
    }
}
