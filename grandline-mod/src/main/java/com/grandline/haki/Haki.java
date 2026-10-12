package com.grandline.haki;

import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Race;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Haki rules shared by players and NPCs. Levels are mastery 0..100 under "haki/observation", "haki/armament",
 * "haki/conqueror". NPCs report levels from their archetype.
 */
public final class Haki {
    public static final String OBS = "haki/observation", ARM = "haki/armament", COQ = "haki/conqueror";

    public static int observationLevel(LivingEntity e) {
        if (e instanceof Player p) return Characters.get(p).mastery(OBS);
        if (e instanceof GLNpc n) return n.hakiObservation();
        return 0;
    }

    public static int armamentLevel(LivingEntity e) {
        if (e instanceof Player p) return Characters.get(p).mastery(ARM);
        if (e instanceof GLNpc n) return n.hakiArmament();
        return 0;
    }

    public static int conquerorLevel(LivingEntity e) {
        if (e instanceof Player p) {
            CharacterData d = Characters.get(p);
            return d.conquerorAwakened ? 10 + d.mastery(COQ) : 0;
        }
        if (e instanceof GLNpc n) return n.hakiConqueror();
        return 0;
    }

    /** Willpower used to resist Conqueror's Haki. */
    public static int willpower(LivingEntity e) {
        if (e instanceof Player p) {
            CharacterData d = Characters.get(p);
            return d.level + d.stat(com.grandline.player.Stat.WILLPOWER) * 2 + (d.conquerorAwakened ? 40 : 0);
        }
        if (e instanceof GLNpc n) return n.tier() * 10 + n.hakiConqueror();
        if (e instanceof com.grandline.creature.SeaKing sk) return sk.willpower();
        return (int) (e.getMaxHealth() / 2);
    }

    public static boolean anyActive(LivingEntity e) {
        CombatState s = CombatState.of(e);
        return s.observation || s.armament || s.conquerorAura;
    }

    public static boolean armamentActive(LivingEntity e) {
        return CombatState.of(e).armament || CombatState.of(e).hasBuff("hardening") || (e instanceof GLNpc n && n.usesArmament());
    }

    public static boolean tryObservationDodge(LivingEntity victim, LivingEntity attacker, Hits.Info info) {
        CombatState s = CombatState.of(victim);
        boolean futureSight = s.hasBuff("future_sight");
        if (!s.observation && !futureSight && !(victim instanceof GLNpc n && n.usesObservation())) return false;
        if (info.unblockable() && !futureSight) return false;
        int lvl = observationLevel(victim);
        // attackers with stronger Observation (or Conqueror's) read through the dodge
        int counter = observationLevel(attacker);
        double chance = futureSight ? 0.92 : 0.18 + lvl * 0.0045 - counter * 0.003;
        if (victim.getRandom().nextDouble() >= chance) return false;
        if (!spendGauge(victim, futureSight ? 3 : 7)) return false;
        com.grandline.combat.Hits.train(victim, OBS, 3);
        return true;
    }

    /** Damage multiplier applied to hits the defender receives. */
    public static float defenseMultiplier(LivingEntity victim, LivingEntity attacker) {
        CombatState s = CombatState.of(victim);
        boolean hard = s.hasBuff("hardening");
        if (!s.armament && !hard && !(victim instanceof GLNpc n && n.usesArmament())) return 1f;
        int lvl = armamentLevel(victim);
        float reduction = (hard ? 0.35f : 0.12f) + lvl * (hard ? 0.004f : 0.0025f);
        // internal destruction / Ryuo ignores hardening
        if (CombatState.of(attacker).hasBuff("internal_destruction")) reduction *= 0.2f;
        return Math.max(0.25f, 1f - reduction);
    }

    public static boolean spendGauge(LivingEntity e, float amount) {
        if (e instanceof ServerPlayer sp) {
            CharacterData d = Characters.get(sp);
            if (sp.isCreative()) return true;
            if (d.hakiGauge < amount) return false;
            d.hakiGauge -= amount;
            CharacterManager.markDirty(sp);
            return true;
        }
        return true;
    }

    public static void toggle(ServerPlayer sp, int type) {
        CharacterData d = Characters.get(sp);
        CombatState s = CombatState.of(sp);
        switch (type) {
            case 0 -> {
                if (!d.observationUnlocked) {
                    sp.displayClientMessage(Component.translatable("grandline.haki.locked.observation").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                s.observation = !s.observation;
            }
            case 1 -> {
                if (!d.armamentUnlocked) {
                    sp.displayClientMessage(Component.translatable("grandline.haki.locked.armament").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                s.armament = !s.armament;
            }
            case 2 -> {
                if (!d.conquerorAwakened) {
                    sp.displayClientMessage(Component.translatable("grandline.haki.locked.conqueror").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                s.conquerorAura = !s.conquerorAura;
            }
            default -> {}
        }
        sendState(sp);
    }

    public static void sendState(ServerPlayer sp) {
        CombatState s = CombatState.of(sp);
        var t = CharacterManager.visuals(sp);
        t.putBoolean("obs", s.observation);
        t.putBoolean("arm", s.armament || s.hasBuff("hardening"));
        t.putBoolean("coq", s.conquerorAura);
        t.putInt("obsRange", 12 + observationLevel(sp) / 2);
        GLNetwork.toTrackingAndSelf(sp, new GLNetwork.EntityVisuals(sp.getId(), t));
    }

    /** Per-tick upkeep, passive training and unlock checks. */
    public static void tick(ServerPlayer sp, CharacterData d) {
        CombatState s = CombatState.of(sp);
        boolean changed = false;
        if (s.observation) {
            if (!spendGauge(sp, 0.12f)) {
                s.observation = false;
                changed = true;
            } else if (sp.tickCount % 100 == 0) Hits.train(sp, OBS, 1);
        }
        if (s.armament) {
            if (!spendGauge(sp, 0.18f)) {
                s.armament = false;
                changed = true;
            }
        }
        if (s.conquerorAura) {
            if (!spendGauge(sp, 0.4f)) {
                s.conquerorAura = false;
                changed = true;
            } else if (sp.tickCount % 20 == 0) HakiAbilities.conquerorPressure(sp, 8, false);
        }
        if (changed) sendState(sp);
        if (sp.tickCount % 40 == 0) checkUnlocks(sp, d);
        // meditation: crouching still with full health trains Haki slowly
        if (sp.isShiftKeyDown() && sp.getDeltaMovement().horizontalDistanceSqr() < 1e-4 && sp.tickCount % 60 == 0 && sp.getLastHurtByMob() == null) {
            if (d.observationUnlocked) Hits.train(sp, OBS, 1);
            if (d.armamentUnlocked) Hits.train(sp, ARM, 1);
            d.masteryXp.merge("combat/meditation", 1, Integer::sum);
        }
    }

    static void checkUnlocks(ServerPlayer sp, CharacterData d) {
        boolean affinity = d.race == Race.SKY_PEOPLE || d.race == Race.THREE_EYE;
        int dodges = d.mastery("combat/dodge"), hits = d.mastery("combat/hits"), med = d.mastery("combat/meditation");
        if (!d.observationUnlocked && ((d.level >= (affinity ? 3 : 8) && dodges >= (affinity ? 2 : 5)) || med >= 6)) {
            d.observationUnlocked = true;
            announce(sp, "grandline.haki.unlock.observation");
        }
        if (!d.armamentUnlocked && ((d.level >= 12 && hits >= 8) || (d.level >= 8 && med >= 10))) {
            d.armamentUnlocked = true;
            announce(sp, "grandline.haki.unlock.armament");
        }
        if (d.conquerorTrait && !d.conquerorAwakened && d.level >= 20 && d.flags.contains("survived_knockdown")) {
            d.conquerorAwakened = true;
            announce(sp, "grandline.haki.unlock.conqueror");
            HakiAbilities.conquerorPressure(sp, 24, true);
        }
        if (d.observationUnlocked && !d.futureSight && d.mastery(OBS) >= 60) {
            d.futureSight = true;
            announce(sp, "grandline.haki.unlock.future_sight");
        }
        if (d.armamentUnlocked && !d.emission && d.mastery(ARM) >= 45) {
            d.emission = true;
            announce(sp, "grandline.haki.unlock.emission");
        }
        if (d.armamentUnlocked && !d.internalDestruction && d.mastery(ARM) >= 70) {
            d.internalDestruction = true;
            announce(sp, "grandline.haki.unlock.internal");
        }
        if (d.conquerorAwakened && !d.conquerorInfusion && d.mastery(COQ) >= 40) {
            d.conquerorInfusion = true;
            announce(sp, "grandline.haki.unlock.infusion");
        }
    }

    private static void announce(ServerPlayer sp, String key) {
        sp.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        com.grandline.combat.Loadouts.refresh(sp, Characters.get(sp));
        CharacterManager.markDirty(sp);
        com.grandline.journey.Journey.onHaki(sp, key);
    }

    private Haki() {}
}
