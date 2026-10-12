package com.grandline.haki;

import com.grandline.combat.Ability;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Haki techniques (slot set 3). Requirements are checked in {@link #canUse}. */
public final class HakiAbilities {
    private static boolean booted;

    public static synchronized void bootstrap() {
        if (booted) return;
        booted = true;
        Ability.builder("haki/observation/toggle", Ability.Source.HAKI, "observation").cooldown(10).stamina(0).anim("focus").exec((u, c) -> {
            if (u instanceof ServerPlayer sp) Haki.toggle(sp, 0);
            return true;
        });
        Ability.builder("haki/observation/future_sight", Ability.Source.HAKI, "observation").unlock(60).cooldown(600).stamina(0).haki(40).anim("focus").exec((u, c) -> {
            CombatState.of(u).buffs.put("future_sight", 20 * 8);
            FruitKit.fx(u, "future_sight", u.position().add(0, 1.6, 0), 0, 0, 0);
            return true;
        });
        Ability.builder("haki/armament/toggle", Ability.Source.HAKI, "armament").cooldown(10).stamina(0).anim("harden").exec((u, c) -> {
            if (u instanceof ServerPlayer sp) Haki.toggle(sp, 1);
            u.level().playSound(null, u.getX(), u.getY(), u.getZ(), com.grandline.registry.ModSounds.HAKI_ARMAMENT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.1f);
            return true;
        });
        Ability.builder("haki/armament/hardening", Ability.Source.HAKI, "armament").unlock(20).cooldown(300).stamina(0).haki(30).anim("harden").exec((u, c) -> {
            CombatState.of(u).buffs.put("hardening", 20 * 10);
            u.level().playSound(null, u.getX(), u.getY(), u.getZ(), com.grandline.registry.ModSounds.HAKI_ARMAMENT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.8f);
            if (u instanceof ServerPlayer sp) Haki.sendState(sp);
            return true;
        });
        Ability.builder("haki/armament/emission", Ability.Source.HAKI, "armament").unlock(45).cooldown(120).stamina(10).haki(25).anim("palm_strike").exec((u, c) -> {
            var p = FruitKit.shoot(c, "haki_wave", 1.1f, 2.0, FruitKit.dmg(c, 9) * (1 + Haki.armamentLevel(u) / 80f), Hits.Info.of(Element.IMPACT, 2f).imbue(true).withPoise(60));
            p.pierce = true;
            return true;
        });
        Ability.builder("haki/armament/internal_destruction", Ability.Source.HAKI, "armament").unlock(70).cooldown(200).stamina(15).haki(35).anim("palm_strike").exec((u, c) -> {
            CombatState.of(u).buffs.put("internal_destruction", 10);
            LivingEntity t = c.target != null && c.target.distanceTo(u) < 4.5 + u.getBbWidth() ? c.target : Hits.cone(u, 3.6, 60).stream().findFirst().orElse(null);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 16), Hits.Info.of(Element.IMPACT, 2.5f).imbue(true).asUnblockable().withStun(30));
            FruitKit.fx(u, "ryuo_burst", t.position().add(0, 1, 0), 0, 0, 0);
            return true;
        });
        Ability.builder("haki/conqueror/toggle", Ability.Source.HAKI, "conqueror").cooldown(20).stamina(0).anim("roar").exec((u, c) -> {
            if (u instanceof ServerPlayer sp) Haki.toggle(sp, 2);
            return true;
        });
        Ability.builder("haki/conqueror/burst", Ability.Source.HAKI, "conqueror").cooldown(600).stamina(0).haki(60).anim("roar").exec((u, c) -> {
            conquerorPressure(u, 18 + Haki.conquerorLevel(u) / 4, true);
            Hits.train(u, Haki.COQ, 5);
            return true;
        });
        Ability.builder("haki/conqueror/infusion", Ability.Source.HAKI, "conqueror").unlock(40).cooldown(400).stamina(10).haki(50).anim("roar").exec((u, c) -> {
            CombatState.of(u).buffs.put("infusion", 20 * 15);
            FruitKit.fx(u, "black_lightning", u.position().add(0, 1, 0), 3, 0, 0);
            return true;
        });
    }

    public static boolean canUse(LivingEntity u, Ability a) {
        if (!(u instanceof Player p)) return true;
        CharacterData d = com.grandline.player.Characters.get(p);
        return switch (a.owner()) {
            case "observation" -> d.observationUnlocked && (!a.id().endsWith("future_sight") || d.futureSight);
            case "armament" -> d.armamentUnlocked && (!a.id().endsWith("emission") || d.emission) && (!a.id().endsWith("internal_destruction") || d.internalDestruction);
            case "conqueror" -> d.conquerorAwakened && (!a.id().endsWith("infusion") || d.conquerorInfusion);
            default -> true;
        };
    }

    /** Conqueror's Haki pressure: weaker wills are knocked out; equals feel it; stronger ones resist. */
    public static void conquerorPressure(LivingEntity user, double radius, boolean burst) {
        int power = Haki.conquerorLevel(user) * 2 + Haki.willpower(user);
        Vec3 c = user.position().add(0, 1, 0);
        GLNetwork.fx(user, burst ? "conqueror_burst" : "conqueror_pulse", c.x, c.y, c.z, (float) radius, 0, 0);
        for (LivingEntity e : Hits.sphere(user, c, radius)) {
            if (!Hits.hostileTo(user, e)) continue;
            int will = Haki.willpower(e);
            if (e instanceof com.grandline.creature.SeaKing sk) sk.onConquerorPressure(user, will * 1.3 < power, will < power);
            if (will * 1.3 < power) {
                e.addEffect(new MobEffectInstance(ModEffects.KNOCKED_OUT, burst ? 20 * 12 : 20 * 4, 0));
                if (e instanceof GLNpc n) n.onConquerorKnockout(user);
            } else if (will < power) {
                e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 60, 1));
            }
        }
    }

    private HakiAbilities() {}
}
