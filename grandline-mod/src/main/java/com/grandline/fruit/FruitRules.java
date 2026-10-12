package com.grandline.fruit;

import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.Characters;
import com.grandline.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** The agreed Devil Fruit rules: sea and seastone weakness, Logia intangibility vs Haki, sinking. */
public final class FruitRules {
    @Nullable
    public static DevilFruit fruitOf(LivingEntity e) {
        String id = null;
        if (e instanceof Player p) id = Characters.get(p).fruit;
        else if (e instanceof GLNpc npc) id = npc.fruitId();
        return id == null || id.isEmpty() ? null : DevilFruits.get(id);
    }

    public static boolean isUser(LivingEntity e) {
        return fruitOf(e) != null;
    }

    /** Waist-deep water (or more) drains a fruit user; rain does not. */
    public static boolean inSea(LivingEntity e) {
        if (!e.isInWater()) return false;
        if (com.grandline.ship.SpecialRoutes.inShipBubble(e)) return false; // inside a coated ship's air bubble
        double depth = e.getFluidTypeHeight(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
        return depth >= e.getBbHeight() * 0.45;
    }

    /** Powers disabled (sea, seastone contact, seastone cuffs). */
    public static boolean weakened(LivingEntity e) {
        if (!isUser(e)) return false;
        return inSea(e) || e.hasEffect(ModEffects.SEASTONE_DRAIN);
    }

    public static void applySeastoneContact(LivingEntity e, int ticks) {
        if (!isUser(e)) return;
        MobEffectInstance cur = e.getEffect(ModEffects.SEASTONE_DRAIN);
        if (cur == null || cur.getDuration() < ticks) e.addEffect(new MobEffectInstance(ModEffects.SEASTONE_DRAIN, ticks, 0, false, true));
    }

    public static boolean intangibleTo(LivingEntity victim, LivingEntity attacker, Hits.Info info, boolean attackerHaki) {
        DevilFruit f = fruitOf(victim);
        if (f == null || f.type != FruitType.LOGIA) return false;
        if (attackerHaki || info.seastone()) return false;
        if (weakened(victim)) return false;
        if (CombatState.of(victim).staggered(victim.level().getGameTime())) return false;
        // elemental counters: magma beats fire, etc.
        DevilFruit af = fruitOf(attacker);
        if (af != null && af.type == FruitType.LOGIA && counters(af, f)) return false;
        return f.intangible(victim, attacker, info);
    }

    /** Elemental hierarchy between Logia (magma > fire, light vs darkness absorbs, etc.). */
    public static boolean counters(DevilFruit attacker, DevilFruit defender) {
        return switch (attacker.id) {
            case "magu" -> defender.id.equals("mera") || defender.id.equals("hie") || defender.id.equals("yuki");
            case "yami" -> true;
            case "goro" -> defender.id.equals("suna") || defender.id.equals("moku");
            case "numa" -> defender.id.equals("suna");
            default -> false;
        };
    }

    public static void intangibleFx(LivingEntity victim) {
        DevilFruit f = fruitOf(victim);
        GLNetwork.fx(victim, "intangible_" + (f == null ? "smoke" : f.id), victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 0, 0, 0);
    }

    public static float modifyIncoming(LivingEntity victim, LivingEntity attacker, float amount, Hits.Info info) {
        DevilFruit f = fruitOf(victim);
        if (f == null || weakened(victim)) return amount;
        return f.modifyIncoming(victim, attacker, amount, info);
    }

    /** Attacker-side fruit passives (bonus damage against certain targets, melee buffs). */
    public static float modifyOutgoing(LivingEntity attacker, LivingEntity victim, float amount, Hits.Info info) {
        DevilFruit f = fruitOf(attacker);
        if (f == null || weakened(attacker)) return amount;
        return f.modifyOutgoing(attacker, victim, amount, info);
    }

    /** Called every server tick for fruit users (players and NPCs). */
    public static void tick(LivingEntity user) {
        DevilFruit f = fruitOf(user);
        if (f == null) return;
        if (inSea(user)) {
            // drained: slowed, weakened and sinking; cancels transformations and toggles
            user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
            user.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1, false, false));
            user.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 1, false, false));
            if (!(user instanceof Player)) user.setDeltaMovement(user.getDeltaMovement().add(0, -0.04, 0));
            CombatState cs = CombatState.of(user);
            if (!cs.toggles.isEmpty()) cs.toggles.clear();
            Zoan.forceRevert(user);
            return;
        }
        if (user.hasEffect(ModEffects.SEASTONE_DRAIN)) {
            Zoan.forceRevert(user);
            return;
        }
        f.tick(user);
    }

    private FruitRules() {}
}
