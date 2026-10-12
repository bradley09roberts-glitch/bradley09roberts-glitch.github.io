package com.grandline.race;

import com.grandline.combat.Ability;
import com.grandline.combat.AbilityContext;
import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Race;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Small helpers shared by the race abilities and passives. */
public final class RaceKit {

    /** The race of any living entity: a created player character, a Grand Line NPC, or null for everything else. */
    @Nullable
    public static Race raceOf(LivingEntity e) {
        if (e instanceof Player p) {
            CharacterData d = Characters.get(p);
            return d.created ? d.race : null;
        }
        if (e instanceof GLNpc n) return Race.byId(n.raceId());
        return null;
    }

    public static boolean is(LivingEntity e, Race r) {
        return raceOf(e) == r;
    }

    /** Builder for an ability of a race (id "race/&lt;race&gt;/&lt;name&gt;", mastery key "race/&lt;race&gt;"). */
    static Ability.Builder ability(Race r, String name) {
        return Ability.builder("race/" + r.id() + "/" + name, Ability.Source.RACE, r.id());
    }

    /**
     * Racial technique damage: level/tier power and race mastery like a Devil Fruit, times the user's racial strength
     * (Giants, Buccaneers and Tontatta hit far above their weight).
     */
    static float dmg(AbilityContext c, float base) {
        Race r = raceOf(c.user);
        return base * c.power * c.masteryScale() * (r == null ? 1f : r.damageMultiplier);
    }

    static Hits.Info info(Element e, float kb) {
        return Hits.Info.of(e, kb);
    }

    static void eff(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(effect, ticks, amp, false, true));
    }

    static void buff(LivingEntity e, String id, int ticks) {
        CombatState.of(e).buffs.put(id, ticks);
    }

    static void tell(LivingEntity e, String key, Object... args) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    static void fx(LivingEntity near, String kind, Vec3 p, float a, float b, float c) {
        FruitKit.fx(near, kind, p, a, b, c);
    }

    static long now(LivingEntity e) {
        return e.level().getGameTime();
    }

    static void later(LivingEntity u, int ticks, Runnable r) {
        CombatState.of(u).schedule(now(u) + ticks, r);
    }

    /** In water (body or rain) — Fish-Men, Merfolk and other sea races draw strength from it. */
    static boolean wet(LivingEntity e) {
        return e.isInWaterOrRain() || e.isInWaterRainOrBubble();
    }

    /** Submerged (eyes under water). */
    static boolean submerged(LivingEntity e) {
        return e.isEyeInFluid(FluidTags.WATER);
    }

    /** Any water within {@code r} blocks (a puddle, the sea, a barrel's worth). */
    static boolean waterNear(LivingEntity e, int r) {
        if (wet(e)) return true;
        BlockPos b = e.blockPosition();
        Level l = e.level();
        for (BlockPos p : BlockPos.betweenClosed(b.offset(-r, -2, -r), b.offset(r, 1, r))) {
            if (l.getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** Night time (13000–23000 of the day cycle). */
    public static boolean night(Level l) {
        long t = l.getDayTime() % 24000L;
        return t > 13000 && t < 23000;
    }

    /** A full moon is up and visible from {@code pos}: the only time a Mink can enter Sulong. */
    public static boolean fullMoonVisible(Level l, BlockPos pos) {
        return night(l) && l.getMoonPhase() == 0 && l.canSeeSky(pos.above());
    }

    /** Open sky at night (Starborn). */
    static boolean starlit(LivingEntity e) {
        return night(e.level()) && e.level().canSeeSky(e.blockPosition().above());
    }

    static boolean sunlit(LivingEntity e) {
        Level l = e.level();
        return l.isDay() && !l.isRaining() && l.canSeeSky(e.blockPosition().above());
    }

    static ServerLevel level(AbilityContext c) {
        return c.level;
    }

    private RaceKit() {}
}
