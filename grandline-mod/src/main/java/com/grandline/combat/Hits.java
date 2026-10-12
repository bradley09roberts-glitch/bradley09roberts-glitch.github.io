package com.grandline.combat;

import com.grandline.fruit.FruitRules;
import com.grandline.haki.Haki;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.CharacterManager;
import com.grandline.registry.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-side hit resolution shared by players, NPCs, weapons, styles, Haki and fruits. */
public final class Hits {

    /** Parameters of one hit. */
    public record Info(Element element, float knockback, float poiseDamage, int stunTicks, boolean imbued, boolean seastone,
                       boolean unblockable, String reaction) {
        public static Info physical(float knockback) {
            return new Info(Element.PHYSICAL, knockback, 12, 0, false, false, false, "hit");
        }

        public static Info of(Element e, float knockback) {
            return new Info(e, knockback, 15, 0, false, false, false, "hit");
        }

        public Info withStun(int ticks) {
            return new Info(element, knockback, poiseDamage, ticks, imbued, seastone, unblockable, reaction);
        }

        public Info withPoise(float p) {
            return new Info(element, knockback, p, stunTicks, imbued, seastone, unblockable, reaction);
        }

        public Info imbue(boolean b) {
            return new Info(element, knockback, poiseDamage, stunTicks, b, seastone, unblockable, reaction);
        }

        public Info asUnblockable() {
            return new Info(element, knockback, poiseDamage, stunTicks, imbued, seastone, true, reaction);
        }

        public Info asSeastone() {
            return new Info(element, knockback, poiseDamage, stunTicks, imbued, true, unblockable, reaction);
        }
    }

    public enum Result { HIT, BLOCKED, PARRIED, DODGED, INTANGIBLE, IGNORED }

    /** Base physical power of an entity (its attack damage attribute). */
    public static float power(LivingEntity e) {
        var inst = e.getAttribute(Attributes.ATTACK_DAMAGE);
        return inst == null ? 2f : (float) inst.getValue();
    }

    /** Scaling for abilities: level and stats for players, tier for NPCs. */
    public static float abilityPower(LivingEntity e) {
        if (e instanceof Player p) {
            CharacterData d = Characters.get(p);
            return 1f + d.level * 0.03f;
        }
        if (e instanceof GLNpc npc) return npc.powerScale();
        return 1f;
    }

    public static Result hit(LivingEntity attacker, LivingEntity victim, float damage, Info info) {
        if (!(attacker.level() instanceof ServerLevel level)) return Result.IGNORED;
        if (victim == attacker || !victim.isAlive() || victim.isInvulnerable()) return Result.IGNORED;
        if (!hostileTo(attacker, victim)) return Result.IGNORED;
        long now = level.getGameTime();
        CombatState vs = CombatState.of(victim);
        Vec3 vp = victim.position();

        // PvP gate
        if (attacker instanceof Player pa && victim instanceof Player pv) {
            if (!Pvp.allowed(pa, pv)) return Result.IGNORED;
            damage *= Pvp.multiplier();
        }
        // knocked-down players are only finished by lethal attackers (handled in Knockdown)
        if (vs.invulnerable(now)) {
            GLNetwork.fx(victim, "dodge", vp.x, vp.y + 1, vp.z, 0, 0, 0);
            return Result.DODGED;
        }
        // Observation Haki: chance to sidestep
        if (Haki.tryObservationDodge(victim, attacker, info)) {
            GLNetwork.fx(victim, "observation_dodge", vp.x, vp.y + 1, vp.z, 0, 0, 0);
            GLNetwork.anim(victim, "dodge_side");
            return Result.DODGED;
        }
        // Logia intangibility
        boolean attackerHaki = info.imbued() || Haki.armamentActive(attacker);
        if (FruitRules.intangibleTo(victim, attacker, info, attackerHaki)) {
            FruitRules.intangibleFx(victim);
            return Result.INTANGIBLE;
        }
        // Guard / parry
        if (vs.blocking && !info.unblockable() && facing(victim, attacker)) {
            if (now - vs.blockStart <= 6) {
                CombatState as = CombatState.of(attacker);
                as.staggeredUntil = now + 25;
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 3, false, false));
                level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
                GLNetwork.fx(victim, "parry", vp.x, vp.y + 1.2, vp.z, 0, 0, 0);
                GLNetwork.anim(victim, "parry");
                GLNetwork.anim(attacker, "staggered");
                train(victim, "combat/parry", 4);
                return Result.PARRIED;
            }
            float guardCost = damage * 2.2f + info.poiseDamage() * 0.5f;
            if (spendStamina(victim, guardCost)) {
                damage *= 0.25f;
                level.playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.0f);
                GLNetwork.fx(victim, "block", vp.x, vp.y + 1.2, vp.z, 0, 0, 0);
                applyDamage(level, attacker, victim, damage, info);
                knockback(attacker, victim, info.knockback() * 0.3f);
                return Result.BLOCKED;
            } else {
                vs.blocking = false;
                vs.staggeredUntil = now + 30;
                GLNetwork.anim(victim, "guard_break");
                GLNetwork.fx(victim, "guard_break", vp.x, vp.y + 1.2, vp.z, 0, 0, 0);
            }
        }
        // Armament hardening on the defender
        damage *= Haki.defenseMultiplier(victim, attacker);
        // Devil fruit passives (rubber vs blunt, etc.)
        damage = FruitRules.modifyIncoming(victim, attacker, damage, info);
        damage = FruitRules.modifyOutgoing(attacker, victim, damage, info);
        // Attacker's imbued strike against Logia that is otherwise intangible already handled; Armament adds damage
        if (attackerHaki) damage *= 1.15f + Haki.armamentLevel(attacker) * 0.004f;
        if (damage <= 0) return Result.IGNORED;
        applyDamage(level, attacker, victim, damage, info);
        knockback(attacker, victim, info.knockback());
        // poise & stagger
        vs.poise -= info.poiseDamage();
        if (vs.poise <= 0 || info.stunTicks() > 0) {
            int stun = Math.max(info.stunTicks(), vs.poise <= 0 ? 24 : 0);
            vs.poise = 60;
            vs.staggeredUntil = now + stun;
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 4, false, false));
            GLNetwork.anim(victim, "staggered");
        } else {
            GLNetwork.anim(victim, info.reaction().equals("hit") ? hitReaction(attacker, victim) : info.reaction());
        }
        GLNetwork.fx(victim, "hit_" + info.element().name().toLowerCase(java.util.Locale.ROOT), vp.x, vp.y + victim.getBbHeight() * 0.6, vp.z, damage, attackerHaki ? 1 : 0, 0);
        return Result.HIT;
    }

    private static void applyDamage(ServerLevel level, LivingEntity attacker, LivingEntity victim, float damage, Info info) {
        var type = info.seastone() ? ModDamageTypes.SEASTONE : attackerHakiType(attacker, info);
        victim.invulnerableTime = 0;
        victim.hurt(ModDamageTypes.source(level, type, attacker), damage);
        victim.invulnerableTime = 3;
        if (info.seastone()) FruitRules.applySeastoneContact(victim, 60);
        if (info.element() == Element.FIRE || info.element() == Element.MAGMA) victim.igniteForSeconds(info.element() == Element.MAGMA ? 6 : 3);
        if (info.element() == Element.ICE) victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze() + 120, victim.getTicksFrozen() + 80));
        if (attacker instanceof ServerPlayer sp) CombatState.of(victim).lastHurtByPlayerTick = level.getGameTime();
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> attackerHakiType(LivingEntity attacker, Info info) {
        if (info.imbued() && info.element().physical()) return ModDamageTypes.HAKI;
        return ModDamageTypes.forElement(info.element());
    }

    public static void knockback(LivingEntity attacker, LivingEntity victim, float strength) {
        if (strength <= 0) return;
        float sizeRatio = attacker.getScale() / Math.max(0.3f, victim.getScale());
        double kb = strength * Math.min(3f, Math.max(0.4f, sizeRatio));
        Vec3 d = victim.position().subtract(attacker.position());
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        if (len < 1e-4) return;
        victim.knockback(kb, -d.x / len, -d.z / len);
        if (strength > 1.5f) victim.setDeltaMovement(victim.getDeltaMovement().add(0, Math.min(0.6, strength * 0.12), 0));
        victim.hurtMarked = true;
    }

    private static String hitReaction(LivingEntity attacker, LivingEntity victim) {
        Vec3 toAttacker = attacker.position().subtract(victim.position()).normalize();
        Vec3 look = victim.getLookAngle();
        double side = look.x * toAttacker.z - look.z * toAttacker.x;
        return side > 0.3 ? "hit_left" : side < -0.3 ? "hit_right" : "hit_front";
    }

    public static boolean facing(LivingEntity defender, Entity attacker) {
        Vec3 look = defender.getLookAngle();
        Vec3 to = attacker.position().subtract(defender.position()).normalize();
        return look.x * to.x + look.z * to.z > 0.2;
    }

    /** Whether a would hit b (prevents friendly fire between crewmates / allied NPCs). */
    public static boolean hostileTo(LivingEntity a, LivingEntity b) {
        if (b instanceof ArmorStand) return true;
        if (a instanceof Player pa && b instanceof Player pb) {
            CharacterData da = Characters.get(pa), db = Characters.get(pb);
            return da.crewId == null || !da.crewId.equals(db.crewId);
        }
        if (a instanceof GLNpc na) return na.isHostileTo(b) || na.getTarget() == b;
        if (b instanceof GLNpc nb && a instanceof Player) return true;
        if (b instanceof TamableAnimal t && a instanceof Player p && t.isOwnedBy(p)) return false;
        return true;
    }

    public static boolean spendStamina(LivingEntity e, float amount) {
        if (e instanceof ServerPlayer sp) {
            CharacterData d = Characters.get(sp);
            if (sp.isCreative()) return true;
            if (d.stamina < amount) return false;
            d.stamina -= amount;
            CharacterManager.markDirty(sp);
            return true;
        }
        if (e instanceof GLNpc npc) return npc.spendStamina(amount);
        return true;
    }

    public static void train(LivingEntity e, String masteryKey, int amount) {
        if (e instanceof ServerPlayer sp) {
            CharacterData d = Characters.get(sp);
            if (d.addMastery(masteryKey, amount)) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("grandline.mastery.up",
                        net.minecraft.network.chat.Component.translatable("mastery.grandline." + masteryKey.replace('/', '.')), d.mastery(masteryKey)), true);
                com.grandline.combat.Loadouts.refresh(sp, d);
            }
            CharacterManager.markDirty(sp);
        }
    }

    // ------------------------------------------------------------------ target selection

    public static List<LivingEntity> cone(LivingEntity user, double range, double arcDegrees) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        double cos = Math.cos(Math.toRadians(arcDegrees / 2));
        double reach = range + user.getBbWidth();
        AABB box = user.getBoundingBox().inflate(reach);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive() && !e.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 to = c.subtract(eye);
            double dist = to.length() - e.getBbWidth() * 0.5;
            if (dist > reach) continue;
            Vec3 dir = to.normalize();
            if (dist > 1.2 && dir.dot(look) < cos) continue;
            out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(user)));
        return out;
    }

    public static List<LivingEntity> sphere(LivingEntity user, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive() && !e.isSpectator())) {
            if (e.getBoundingBox().getCenter().distanceTo(center) <= radius + e.getBbWidth() * 0.5) out.add(e);
        }
        return out;
    }

    /** Entities along a ray (beam / thrust / dash path). */
    public static List<LivingEntity> line(LivingEntity user, Vec3 from, Vec3 dir, double length, double width) {
        // dir may be an unnormalised displacement (dash start -> end); the line is always exactly `length` long
        Vec3 to = from.add(dir.normalize().scale(length));
        AABB box = new AABB(from, to).inflate(width + 1);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive() && !e.isSpectator())) {
            if (e.getBoundingBox().inflate(width).clip(from, to).isPresent() || e.getBoundingBox().inflate(width).contains(from)) out.add(e);
        }
        return out;
    }

    public static void particles(ServerLevel level, net.minecraft.core.particles.ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    public static void burst(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
    }

    private Hits() {}
}
