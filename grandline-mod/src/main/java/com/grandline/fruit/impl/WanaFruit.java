package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Wana Wana no Mi — ORIGINAL trap Paramecia. The user turns any patch of ground into a trap: snares, pitfalls that
 * open in the earth (temporary), steel jaws, tripwires that expose even invisible intruders, iron cages and, at
 * awakening, a whole minefield. Traps are armed zones that wait for an enemy to step in.
 * Passive: anything caught in one of the user's traps takes 25% more damage from them for a while.
 */
public final class WanaFruit {
    static final String CAUGHT = "wana_caught";
    static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();

    /**
     * Arm a trap at a point: every 3 ticks for {@code life} ticks it checks for an enemy within {@code r}; the first one
     * springs it ({@code spring} receives the victim and the trap position). One self-rescheduling check per trap, so
     * many armed traps stay cheap. Only the trapper sees where an armed trap lies.
     */
    static void arm(AbilityContext c, Vec3 at, double r, int life, String fx, BiConsumer<LivingEntity, Vec3> spring) {
        LivingEntity u = c.user;
        long end = c.level.getGameTime() + life;
        Runnable[] check = new Runnable[1];
        check[0] = () -> {
            if (!u.isAlive()) return;
            long now = c.level.getGameTime();
            for (LivingEntity e : PowerKit.foes(u, at.add(0, 0.6, 0), r)) {
                if (Math.abs(e.getY() - at.y) > 2) continue;
                CombatState.of(e).buffs.put(CAUGHT, 200);
                spring.accept(e, at);
                return;
            }
            if (now % 40 < 3 && u instanceof ServerPlayer sp) com.grandline.net.GLNetwork.toPlayer(sp, new com.grandline.net.GLNetwork.Fx(fx, at.x, at.y, at.z, (float) r, 0, 0));
            if (now + 3 <= end) CombatState.of(u).schedule(now + 3, check[0]);
        };
        CombatState.of(u).schedule(c.level.getGameTime() + 2, check[0]);
        if (u instanceof ServerPlayer sp) com.grandline.net.GLNetwork.toPlayer(sp, new com.grandline.net.GLNetwork.Fx(fx, at.x, at.y, at.z, (float) r, 1, 0));
    }

    public static void init(DevilFruit f) {
        Hits.Info jaw = FruitKit.info(Element.PIERCE, 0.1f);

        // Snare: a rope snare at a point (or right under the target) — it snaps shut on the first enemy to step in.
        f.add(f.ability("snare").cooldown(40).stamina(10).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 16);
            arm(c, at, 1.6, 600, "wana_snare", (e, p) -> {
                Hits.hit(u, e, FruitKit.dmg(c, 5), jaw.withStun(20).withPoise(40));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6));
                FruitKit.fx(u, "wana_snap", e.position(), e.getBbWidth(), 0, 0);
                PowerKit.sound(c, p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.6f);
            });
            return true;
        }));

        // Pitfall: the ground at a point is hollowed into a hidden pit that drops open under the first enemy.
        f.add(f.ability("pitfall").unlock(5).cooldown(120).stamina(16).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 16);
            arm(c, at, 1.8, 600, "wana_pit", (e, p) -> {
                BlockPos g = PowerKit.ground(c.level, (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
                if (g != null) {
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            for (int dy = 0; dy < 3; dy++) PowerKit.dig(c.level, g.offset(dx, -dy, dz));
                }
                Hits.hit(u, e, FruitKit.dmg(c, 6), FruitKit.info(Element.BLUNT, 0f).withStun(30).withPoise(40));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                FruitKit.impulse(e, new Vec3(0, -0.6, 0), false);
                FruitKit.fx(u, "wana_pit", p, 1.8f, 2, 0);
                PowerKit.sound(c, p, SoundEvents.GRAVEL_BREAK, 1.5f, 0.6f);
            });
            return true;
        }));

        // Bear trap: steel jaws hidden in the ground — they bite deep, pin the victim and make it bleed.
        f.add(f.ability("bear_trap").unlock(12).cooldown(140).stamina(18).anim("cast_ground").exec((u, c) -> {
            Vec3 at = FruitKit.aimPoint(c, 16);
            arm(c, at, 1.4, 600, "wana_jaws", (e, p) -> {
                Hits.hit(u, e, FruitKit.dmg(c, 10), jaw.withStun(30).withPoise(60).asUnblockable());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 9));
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                FruitKit.fx(u, "wana_snap", e.position(), e.getBbWidth(), 1, 0);
                PowerKit.sound(c, p, SoundEvents.ANVIL_PLACE, 0.8f, 1.6f);
            });
            return true;
        }));

        // Tripwire ring: tripwires around the user for half a minute. Every enemy that crosses is tripped and exposed —
        // invisible intruders are revealed and glow — and the user is warned.
        f.add(f.ability("tripwire").unlock(20).cooldown(400).stamina(18).anim("cast_ground").exec((u, c) -> {
            Vec3 center = u.position();
            double r = 8;
            Set<Integer> tripped = new HashSet<>();
            OriginalKit.pulses(c, 2, 4, 150, n -> {
                if (n % 10 == 0) FruitKit.fx(u, "wana_wire", center, (float) r, 0, 0);
                for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), r + 1)) {
                    double d = e.position().subtract(center).horizontalDistance();
                    if (d < r - 1.2 || d > r + 1.2 || !tripped.add(e.getId())) continue;
                    e.removeEffect(MobEffects.INVISIBILITY);
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                    CombatState.of(e).buffs.put(CAUGHT, 200);
                    Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.BLUNT, 0f).withStun(30).withPoise(60));
                    if (u instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.wana.tripped", e.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
                }
            });
            // laying the wires whips anyone already standing close
            for (LivingEntity e : PowerKit.foes(u, center.add(0, 1, 0), 3.5)) Hits.hit(u, e, FruitKit.dmg(c, 3), FruitKit.info(Element.SLASH, 0.5f).withPoise(15));
            PowerKit.sound(c, center, SoundEvents.CHAIN_PLACE, 1f, 1.4f);
            return true;
        }));

        // Iron cage: an iron-barred cage drops around the target and holds it for a few seconds.
        f.add(f.ability("iron_cage").unlock(30).cooldown(280).stamina(28).anim("cast_forward").exec((u, c) -> {
            LivingEntity t = PowerKit.target(c, 16);
            if (t == null) return false;
            Hits.hit(u, t, FruitKit.dmg(c, 6), FruitKit.info(Element.BLUNT, 0f).withStun(40).withPoise(60));
            OriginalKit.encaseFor(c, t, BARS, 120);
            CombatState.of(t).buffs.put(CAUGHT, 200);
            FruitKit.fx(u, "wana_cage", t.position(), t.getBbWidth(), t.getBbHeight(), 0);
            PowerKit.sound(c, t.position(), SoundEvents.ANVIL_LAND, 1f, 1.2f);
            return true;
        }));

        // Awakening — Minefield: eight hidden mines are sown around a point; each blows up under the first enemy to
        // step near it.
        f.add(f.ability("minefield").unlock(55).cooldown(1000).stamina(80).awakened().anim("cast_ground").exec((u, c) -> {
            Vec3 center = FruitKit.aimPoint(c, 20);
            LivingEntity t = PowerKit.target(c, 20);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4, d = i == 0 ? 0 : 3 + u.getRandom().nextDouble() * 6;
                Vec3 at = i == 0 && t != null ? t.position() : center.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                arm(c, at, 1.6, 400, "wana_mine", (e, p) -> {
                    for (LivingEntity v : PowerKit.foes(u, p.add(0, 1, 0), 3)) Hits.hit(u, v, FruitKit.dmg(c, 12), FruitKit.info(Element.IMPACT, 2f).withPoise(70));
                    TerrainChanges.crater(c.level, BlockPos.containing(p), 1, u);
                    FruitKit.fx(u, "explosion", p.add(0, 0.5, 0), 3, 0, 0);
                    PowerKit.sound(c, p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.1f);
                });
            }
            return true;
        }));

        // Passive: the trapper's advantage — whatever the user's traps catch takes 25% more damage from them.
        f.outgoingHook = (u, v, amount, info) -> CombatState.of(v).hasBuff(CAUGHT) ? amount * 1.25f : amount;
    }

    private WanaFruit() {}
}
