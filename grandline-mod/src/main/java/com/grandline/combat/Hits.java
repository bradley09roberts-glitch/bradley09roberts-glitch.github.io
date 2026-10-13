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

    /**
     * Parameters of one hit. {@code penetrating} skips the line-of-effect check (see {@link Cover}); it is reserved for
     * the narrow exceptions listed in OVERHAUL_AUDIT.md.
     */
    public record Info(Element element, float knockback, float poiseDamage, int stunTicks, boolean imbued, boolean seastone,
                       boolean unblockable, String reaction, boolean penetrating) {
        public Info(Element element, float knockback, float poiseDamage, int stunTicks, boolean imbued, boolean seastone,
                    boolean unblockable, String reaction) {
            this(element, knockback, poiseDamage, stunTicks, imbued, seastone, unblockable, reaction, false);
        }

        public static Info physical(float knockback) {
            return new Info(Element.PHYSICAL, knockback, 12, 0, false, false, false, "hit");
        }

        public static Info of(Element e, float knockback) {
            return new Info(e, knockback, 15, 0, false, false, false, "hit");
        }

        public Info withStun(int ticks) {
            return new Info(element, knockback, poiseDamage, ticks, imbued, seastone, unblockable, reaction, penetrating);
        }

        public Info withPoise(float p) {
            return new Info(element, knockback, p, stunTicks, imbued, seastone, unblockable, reaction, penetrating);
        }

        public Info imbue(boolean b) {
            return new Info(element, knockback, poiseDamage, stunTicks, b, seastone, unblockable, reaction, penetrating);
        }

        public Info asUnblockable() {
            return new Info(element, knockback, poiseDamage, stunTicks, imbued, seastone, true, reaction, penetrating);
        }

        public Info asSeastone() {
            return new Info(element, knockback, poiseDamage, stunTicks, imbued, true, unblockable, reaction, penetrating);
        }

        /** Works through walls by design (a documented exception to line of effect). */
        public Info asPenetrating() {
            return new Info(element, knockback, poiseDamage, stunTicks, imbued, seastone, unblockable, reaction, true);
        }
    }

    /** {@code COVERED}: something solid stood between the blow and every part of the victim. */
    /** Set while a boss's ground-marked (announced) attack or a visible hazard resolves: those may hit harder. */
    private static final ThreadLocal<Boolean> ANNOUNCED = ThreadLocal.withInitial(() -> false);

    public static boolean announced() {
        return ANNOUNCED.get();
    }

    /** Resolve hits as an announced attack (boss ground markers, hazard zones). */
    public static Result announced(java.util.function.Supplier<Result> body) {
        boolean prev = ANNOUNCED.get();
        ANNOUNCED.set(true);
        try {
            return body.get();
        } finally {
            ANNOUNCED.set(prev);
        }
    }

    public enum Result { HIT, BLOCKED, PARRIED, DODGED, INTANGIBLE, IGNORED, COVERED }

    /** Base physical power of an entity (its attack damage attribute). */
    public static float power(LivingEntity e) {
        if (e instanceof GLNpc n) return com.grandline.balance.Tuning.npcPower(n); // NPC blows come from the central tuning
        var inst = e.getAttribute(Attributes.ATTACK_DAMAGE);
        float p = inst == null ? 2f : (float) inst.getValue();
        if (e instanceof Player pl && unarmedFighter(pl)) p += com.grandline.balance.Tuning.UNARMED_BONUS; // trained fists count as a weapon
        return p;
    }

    /** Bare hands (or a non-weapon item) with an unarmed fighting style. */
    private static boolean unarmedFighter(Player p) {
        var main = p.getMainHandItem();
        if (CombatManager.weaponOf(main) != null) return false;
        var st = com.grandline.combat.style.FightingStyles.get(Characters.get(p).activeStyle);
        return st == null || st.unarmed();
    }

    /** Scaling for abilities: level and stats for players, tier for NPCs. */
    public static float abilityPower(LivingEntity e) {
        if (e instanceof Player p) {
            CharacterData d = Characters.get(p);
            return 1f + d.level * 0.03f;
        }
        if (e instanceof GLNpc npc) return com.grandline.balance.Tuning.npcTechniquePower(npc);
        return 1f;
    }

    public static Result hit(LivingEntity attacker, LivingEntity victim, float damage, Info info) {
        if (!(attacker.level() instanceof ServerLevel level)) return Result.IGNORED;
        if (victim == attacker || !victim.isAlive() || victim.isInvulnerable()) return Result.IGNORED;
        if (!hostileTo(attacker, victim)) return Result.IGNORED;
        if (!info.penetrating() && !Cover.lineOfEffect(attacker, victim)) {
            coverFeedback(level, attacker, victim);
            return Result.COVERED;
        }
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
            if (victim instanceof ServerPlayer sp) Lessons.done(sp, Lessons.DODGE);
            feedback(attacker, victim, "dodged");
            return Result.DODGED;
        }
        // Observation Haki: chance to sidestep
        if (Haki.tryObservationDodge(victim, attacker, info)) {
            GLNetwork.fx(victim, "observation_dodge", vp.x, vp.y + 1, vp.z, 0, 0, 0);
            GLNetwork.anim(victim, "dodge_side");
            return Result.DODGED;
        }
        // racial evasion (Three-Eye foresight, Snakeneck sway, Tontatta size)
        if (com.grandline.race.RacePowers.tryEvade(victim, attacker, info)) return Result.DODGED;
        // Logia intangibility
        boolean attackerHaki = info.imbued() || Haki.armamentActive(attacker);
        if (FruitRules.intangibleTo(victim, attacker, info, attackerHaki)) {
            FruitRules.intangibleFx(victim);
            feedback(attacker, victim, "intangible");
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
                if (victim instanceof ServerPlayer sp) Lessons.done(sp, Lessons.GUARD);
                feedback(attacker, victim, "parried");
                return Result.PARRIED;
            }
            float guardCost = damage * 2.2f + info.poiseDamage() * 0.5f;
            if (spendStamina(victim, guardCost)) {
                damage *= 0.25f;
                level.playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.0f);
                GLNetwork.fx(victim, "block", vp.x, vp.y + 1.2, vp.z, 0, 0, 0);
                if (Sparring.isPartner(attacker) && victim instanceof Player) damage = 0.01f; // practice blows
                applyDamage(level, attacker, victim, damage, info);
                knockback(attacker, victim, info.knockback() * 0.3f);
                if (victim instanceof ServerPlayer sp) Lessons.done(sp, Lessons.GUARD);
                feedback(attacker, victim, "blocked");
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
        damage = com.grandline.race.RacePowers.modifyDamage(attacker, victim, damage, info); // racial traits
        // Attacker's imbued strike against Logia that is otherwise intangible already handled; Armament adds damage
        if (attackerHaki) damage *= 1.15f + Haki.armamentLevel(attacker) * 0.004f;
        if (damage <= 0) return Result.IGNORED;
        if (Sparring.isPartner(attacker) && victim instanceof Player) damage = 0.01f; // practice blows land but do not hurt
        if (Sparring.isPartner(victim)) damage = Math.min(damage, Math.max(0, victim.getHealth() - 1)); // a partner bows out, never dies
        applyDamage(level, attacker, victim, damage, info);
        if (!victim.isAlive() && attacker instanceof ServerPlayer sp && !(victim instanceof Player))
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("grandline.combat.fb.defeated", victim.getDisplayName())
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        knockback(attacker, victim, Sparring.isPartner(attacker) ? info.knockback() * 0.25f : info.knockback());
        // poise & stagger
        // poise & stagger: a stagger is followed by a short window in which the victim cannot be staggered again, and a
        // player is never held for more than 1.5 s by one blow (2.5 s by a boss's announced attack): no stun-locks
        vs.poise -= info.poiseDamage();
        boolean canStagger = now >= vs.staggerImmuneUntil;
        if (canStagger && (vs.poise <= 0 || info.stunTicks() > 0)) {
            int stun = Math.max(info.stunTicks(), vs.poise <= 0 ? 24 : 0);
            if (victim instanceof Player) stun = Math.min(stun, announced() ? 50 : 30);
            vs.poise = 60;
            vs.staggeredUntil = now + stun;
            vs.staggerImmuneUntil = vs.staggeredUntil + (victim instanceof Player || victim instanceof GLNpc gn && com.grandline.npc.crew.CrewManager.captainOf(gn) != null ? 40 : 20);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 4, false, false));
            GLNetwork.anim(victim, "staggered");
        } else {
            if (vs.poise <= 0) vs.poise = 20;
            GLNetwork.anim(victim, info.reaction().equals("hit") ? hitReaction(attacker, victim) : info.reaction());
        }
        GLNetwork.fx(victim, "hit_" + info.element().name().toLowerCase(java.util.Locale.ROOT), vp.x, vp.y + victim.getBbHeight() * 0.6, vp.z, damage, attackerHaki ? 1 : 0, 0);
        return Result.HIT;
    }

    private static final java.util.Map<java.util.UUID, Long> COVER_MSG = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long> FEEDBACK = new java.util.HashMap<>();

    /**
     * One short line over the hotbar when a blow did not simply land: for the player who struck ("Blocked",
     * "Parried — you're open!", "Dodged", "No effect — Logia") and for the player who defended ("Guarded", "Parry!",
     * "Dodged!"). At most one line every 0.75 s each.
     */
    private static void feedback(LivingEntity attacker, LivingEntity victim, String what) {
        if (attacker instanceof ServerPlayer sp) say(sp, "grandline.combat.fb." + what);
        if (victim instanceof ServerPlayer sp) say(sp, "grandline.combat.fb.you_" + what);
    }

    private static void say(ServerPlayer sp, String key) {
        long now = sp.level().getGameTime();
        Long last = FEEDBACK.get(sp.getUUID());
        if (last != null && now - last < 15) return;
        FEEDBACK.put(sp.getUUID(), now);
        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY), true);
    }

    /** A spark where the blow met the wall, and a short note for a player who swung into cover. */
    private static void coverFeedback(ServerLevel level, LivingEntity attacker, LivingEntity victim) {
        Vec3 at = Cover.obstruction(attacker, victim);
        if (at != null) GLNetwork.fx(attacker, "cover_spark", at.x, at.y, at.z, 0, 0, 0);
        if (attacker instanceof ServerPlayer sp) {
            long now = level.getGameTime();
            Long last = COVER_MSG.get(sp.getUUID());
            if (last == null || now - last > 40) {
                COVER_MSG.put(sp.getUUID(), now);
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("grandline.combat.covered")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
        }
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
            amount = com.grandline.base.CrewBases.trainingBonus(sp, masteryKey, amount); // a base dojo
            if (d.addMastery(masteryKey, amount)) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("grandline.mastery.up",
                        net.minecraft.network.chat.Component.translatable("mastery.grandline." + masteryKey.replace('/', '.')), d.mastery(masteryKey)), true);
                com.grandline.combat.Loadouts.refresh(sp, d);
                com.grandline.journey.Journey.onMastery(sp, d, masteryKey);
            }
            CharacterManager.markDirty(sp);
        }
    }

    // ------------------------------------------------------------------ target selection

    /**
     * Melee and close techniques: living things within reach in front of the user, nearest first. Reach is measured to
     * the nearest point of the victim's body (so big foes are as easy to reach as small ones) and only victims with
     * some part of their body in the open from the user's eyes or body count (see {@link Cover}).
     */
    public static List<LivingEntity> cone(LivingEntity user, double range, double arcDegrees) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        double cos = Math.cos(Math.toRadians(arcDegrees / 2));
        double reach = range + user.getBbWidth();
        AABB box = user.getBoundingBox().inflate(reach);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive() && !e.isSpectator())) {
            if (inCone(eye, look, cos, reach, e.getBoundingBox()) && Cover.reachable(user, e)) out.add(e);
        }
        addPartOwners(user, box, out, p -> inCone(eye, look, cos, reach, p.getBoundingBox()) && Cover.reachable(user, p));
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(user)));
        for (LivingEntity e : out) Cover.markCleared(user, e);
        return out;
    }

    private static boolean inCone(Vec3 eye, Vec3 look, double cos, double reach, AABB b) {
        Vec3 near = new Vec3(Math.max(b.minX, Math.min(b.maxX, eye.x)), Math.max(b.minY, Math.min(b.maxY, eye.y)), Math.max(b.minZ, Math.min(b.maxZ, eye.z)));
        double dist = near.distanceTo(eye);
        if (dist > reach) return false;
        if (dist <= 1.2) return true;
        Vec3 toCentre = b.getCenter().subtract(eye).normalize(), toNear = near.subtract(eye).normalize();
        return toCentre.dot(look) >= cos || toNear.dot(look) >= cos;
    }

    /** Areas (explosions, shockwaves, zones): living things within a radius that the centre has a clear line to. */
    public static List<LivingEntity> sphere(LivingEntity user, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        Vec3 from = Cover.freeOrigin(user.level(), center, user.getEyePosition());
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive() && !e.isSpectator())) {
            if (e.getBoundingBox().getCenter().distanceTo(center) <= radius + e.getBbWidth() * 0.5 && Cover.exposed(user.level(), from, e)) out.add(e);
        }
        addPartOwners(user, box, out, p -> p.getBoundingBox().getCenter().distanceTo(center) <= radius + p.getBbWidth() * 0.5 && Cover.exposed(user.level(), from, p));
        for (LivingEntity e : out) Cover.markCleared(user, e);
        return out;
    }

    /**
     * Entities along a ray (beam / thrust / dash path). The ray stops at the first solid block, and a victim beside the
     * ray must also be in the open from the nearest point of the ray (wide beams do not curl round corners).
     */
    public static List<LivingEntity> line(LivingEntity user, Vec3 from, Vec3 dir, double length, double width) {
        // dir may be an unnormalised displacement (dash start -> end); the line is at most `length` long
        Vec3 n = dir.normalize();
        net.minecraft.world.level.Level level = user.level();
        Vec3 start = Cover.freeOrigin(level, from, from.add(n));
        Vec3 to = start.add(n.scale(length));
        var block = level.clip(new net.minecraft.world.level.ClipContext(start, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) to = block.getLocation().subtract(n.scale(0.05));
        final Vec3 end = to;
        AABB box = new AABB(start, end).inflate(width + 1);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive() && !e.isSpectator())) {
            if (onLine(level, start, end, width, e.getBoundingBox())) out.add(e);
        }
        addPartOwners(user, box, out, p -> onLine(level, start, end, width, p.getBoundingBox()));
        for (LivingEntity e : out) Cover.markCleared(user, e);
        return out;
    }

    private static boolean onLine(net.minecraft.world.level.Level level, Vec3 a, Vec3 b, double width, AABB body) {
        AABB wide = body.inflate(width);
        if (!wide.clip(a, b).isPresent() && !wide.contains(a)) return false;
        if (body.inflate(0.3).clip(a, b).isPresent() || body.inflate(0.3).contains(a)) return true; // the ray itself meets the body
        Vec3 c = body.getCenter(), ab = b.subtract(a);
        double t = ab.lengthSqr() < 1e-6 ? 0 : Math.max(0, Math.min(1, c.subtract(a).dot(ab) / ab.lengthSqr()));
        return Cover.exposed(level, a.add(ab.scale(t)), body);
    }

    /** Multipart creatures (Sea Kings): a body segment inside the area counts as a hit on the creature itself. */
    private static void addPartOwners(LivingEntity user, AABB box, List<LivingEntity> out, java.util.function.Predicate<Entity> test) {
        for (Entity p : user.level().getEntities(user, box, en -> en instanceof net.neoforged.neoforge.entity.PartEntity<?> pe && pe.getParent() instanceof LivingEntity)) {
            LivingEntity owner = (LivingEntity) ((net.neoforged.neoforge.entity.PartEntity<?>) p).getParent();
            if (owner == user || !owner.isAlive() || out.contains(owner)) continue;
            if (test.test(p)) out.add(owner);
        }
    }

    public static void particles(ServerLevel level, net.minecraft.core.particles.ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    public static void burst(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
    }

    private Hits() {}
}
