package com.grandline.creature;

import com.grandline.combat.CombatState;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.haki.Haki;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModDamageTypes;
import com.grandline.registry.ModDataComponents;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModItems;
import com.grandline.ship.ShipEntity;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

/**
 * Sea King: a colossal sea serpent (25–80 blocks long). The head is the entity itself; {@link #SEGMENTS} body
 * segments follow the path the head swam (a trail of head positions) and are real collidable/hittable part
 * entities, so cannons, weapons, fruits and Haki can hit the body anywhere. Swims at depth, surfaces near ships,
 * looms and bites, slams its tail down, faints or flees under Conqueror's Haki, and drops meat and Beli.
 * Not saved: Sea Kings are transient encounters spawned by {@link SeaKingSpawner} and despawn away from players.
 */
public class SeaKing extends Mob implements GeoEntity, Enemy {
    public static final int SEGMENTS = 14;
    /** Model proportions at scale 1 (blocks): total length, head-centre to first segment, segment spacing. */
    public static final float BASE_LENGTH = 24f, NECK = 1.35f, SPACING = 1.48f;
    public static final float HEAD_W = 2.4f, HEAD_H = 2.0f;
    public static final String[] VARIANTS = {"serpent", "maw", "horned"};
    public static final int SLAM_TICKS = 36, LOOM_TICKS = 24, BITE_TICKS = 12, SLAM_SEGS = 6;

    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(SeaKing.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> TIER = SynchedEntityData.defineId(SeaKing.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SeaKing.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> STATE_TIME = SynchedEntityData.defineId(SeaKing.class, EntityDataSerializers.INT);
    public static final byte SWIM = 0, LOOM = 1, BITE = 2, SLAM = 3, FAINT = 4, FLEE = 5;

    /** Live server-side Sea Kings (for spawn caps). */
    static final Set<SeaKing> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final SeaKingPart[] parts = new SeaKingPart[SEGMENTS];
    private final Vec3[] seg = new Vec3[SEGMENTS];
    private final Vec3[] segO = new Vec3[SEGMENTS];
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private float trailScale = -1;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // ---- server AI state
    @Nullable private Entity target;
    private float speed;
    private float wanderYaw;
    private int wanderTimer, attackCooldown = 60, targetScan, despawnScan = 60, circleDir = 1, noPlayerTicks;
    private boolean announced, hitThisAttack;
    private Vec3 bitePoint = Vec3.ZERO;
    @Nullable private Entity biteTarget;
    /** Testing: hold position at the surface and never despawn. */
    public boolean anchoredForTest;
    /** Testing: stop dead and hold position (see {@link #anchoredForTest}). */
    public void freezeForTest() {
        anchoredForTest = true;
        speed = 0;
        setDeltaMovement(Vec3.ZERO);
    }

    /** Attack counters (diagnostics for tests). */
    public int looms, bites, slams;

    public SeaKing(EntityType<? extends SeaKing> type, Level level) {
        super(type, level);
        for (int i = 0; i < SEGMENTS; i++) parts[i] = new SeaKingPart(this, i);
        this.setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.noCulling = true;
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 180)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 96)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(VARIANT, (byte) 0);
        b.define(TIER, (byte) 1);
        b.define(STATE, SWIM);
        b.define(STATE_TIME, 0);
    }

    // ------------------------------------------------------------------ configuration & accessors

    /** Set variant (0..2), tier (1..3) and body length in blocks (tier ranges: 25–35, 40–55, 60–80). */
    public void configure(int variant, int tier, float length) {
        tier = Mth.clamp(tier, 1, 3);
        entityData.set(VARIANT, (byte) Math.floorMod(variant, VARIANTS.length));
        entityData.set(TIER, (byte) tier);
        float scale = Mth.clamp(length / BASE_LENGTH, 0.5f, 4f);
        Objects.requireNonNull(getAttribute(Attributes.SCALE)).setBaseValue(scale);
        Objects.requireNonNull(getAttribute(Attributes.MAX_HEALTH)).setBaseValue(new float[]{0, 180, 450, 1000}[tier]);
        Objects.requireNonNull(getAttribute(Attributes.ARMOR)).setBaseValue(new float[]{0, 4, 8, 12}[tier]);
        Objects.requireNonNull(getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(new float[]{0, 10, 18, 30}[tier]);
        setHealth(getMaxHealth());
        xpReward = 30 * tier * tier;
        refreshDimensions();
        trail.clear();
    }

    public int variant() { return entityData.get(VARIANT); }
    public String variantName() { return VARIANTS[Math.floorMod(variant(), VARIANTS.length)]; }
    public int tier() { return entityData.get(TIER); }
    public byte state() { return entityData.get(STATE); }
    public float length() { return getScale() * BASE_LENGTH; }
    @Nullable public Entity target() { return target; }

    /** Ticks since the current state began (client and server agree via the synced start time). */
    public float stateAge(float partial) {
        return level().getGameTime() - entityData.get(STATE_TIME) + partial;
    }

    private void setState(byte s) {
        entityData.set(STATE, s);
        entityData.set(STATE_TIME, (int) level().getGameTime());
        hitThisAttack = false;
    }

    /** Willpower against Conqueror's Haki (see {@link Haki#willpower}). */
    public int willpower() {
        return new int[]{0, 25, 60, 115}[tier()];
    }

    /** Segment diameter at scale 1 (blocks); the generated models use the same profile. */
    public static float diameter(int k) {
        float t = k / (float) (SEGMENTS - 1);
        if (k < 3) return 1.95f * (0.88f + 0.04f * k);
        return 1.95f * (1f - 0.64f * (float) Math.pow((k - 3) / (float) (SEGMENTS - 4), 1.25)) * (t > 0.95f ? 0.95f : 1f);
    }

    /** Arc distance from the head centre to segment k's centre at scale 1. */
    public static float segDistance(int k) {
        return NECK + (k + 0.5f) * SPACING;
    }

    public Vec3 headCenter() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    /** World position of segment k's centre (interpolated for rendering). */
    public Vec3 segment(int k, float partial) {
        Vec3 a = segO[k], b = seg[k];
        if (a == null || b == null) return b == null ? headCenter() : b;
        return a.lerp(b, partial);
    }

    public Vec3 tailTip() {
        return seg[SEGMENTS - 1] == null ? headCenter() : seg[SEGMENTS - 1];
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    protected Component getTypeName() {
        Component base = Component.translatable("entity.grandline.sea_king." + variantName());
        return tier() >= 3 ? Component.translatable("entity.grandline.sea_king.colossal", base) : base;
    }

    // ------------------------------------------------------------------ body (trail of head positions)

    private void resetTrail(Vec3 head, float s) {
        trail.clear();
        double yaw = Math.toRadians(getYRot());
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
        double total = (BASE_LENGTH + 2) * s;
        for (double d = 0.5 * s; d <= total; d += 0.5 * s) trail.addLast(head.add(back.scale(d)));
        trailScale = s;
    }

    private void updateBody() {
        float s = getScale();
        Vec3 head = headCenter();
        if (trail.isEmpty() || Math.abs(s - trailScale) > 1e-3) resetTrail(head, s);
        double step = 0.35 * s;
        if (head.distanceToSqr(trail.peekFirst()) >= step * step) trail.addFirst(head);
        for (int k = 0; k < SEGMENTS; k++) segO[k] = seg[k];
        int k = 0, used = 0;
        double acc = 0, want = segDistance(0) * s;
        Vec3 prev = head;
        for (Vec3 p : trail) {
            used++;
            double len = prev.distanceTo(p);
            while (k < SEGMENTS && len > 1e-6 && acc + len >= want) {
                seg[k] = prev.lerp(p, (want - acc) / len);
                if (++k < SEGMENTS) want = segDistance(k) * s;
            }
            acc += len;
            prev = p;
            if (k >= SEGMENTS) break;
        }
        if (k < SEGMENTS) {
            Vec3 dir = k >= 2 ? seg[k - 1].subtract(seg[k - 2]).normalize() : new Vec3(Math.sin(Math.toRadians(getYRot())), 0, -Math.cos(Math.toRadians(getYRot())));
            Vec3 base = k >= 1 ? seg[k - 1] : head;
            double baseD = k >= 1 ? segDistance(k - 1) * s : 0;
            for (; k < SEGMENTS; k++) seg[k] = base.add(dir.scale(segDistance(k) * s - baseD));
        }
        while (trail.size() > used + 2) trail.removeLast();
        // tail slam: lift the last segments out of the water and bring them down hard
        if (state() == SLAM) {
            float t = stateAge(0) / SLAM_TICKS;
            double lift = slamLift(t) * s;
            for (int i = SEGMENTS - SLAM_SEGS; i < SEGMENTS; i++) {
                double w = Math.pow((i - (SEGMENTS - SLAM_SEGS) + 1) / (double) SLAM_SEGS, 1.6);
                seg[i] = seg[i].add(0, lift * w, 0);
            }
        }
        for (int i = 0; i < SEGMENTS; i++) {
            if (segO[i] == null) segO[i] = seg[i];
            SeaKingPart p = parts[i];
            float d = diameter(i) * s;
            if (Math.abs(p.getBbHeight() - d) > 1e-3) p.refreshDimensions();
            double y = seg[i].y - p.getBbHeight() / 2;
            p.setPos(seg[i].x, y, seg[i].z);
            p.xo = p.xOld = segO[i].x;
            p.yo = p.yOld = segO[i].y - p.getBbHeight() / 2;
            p.zo = p.zOld = segO[i].z;
        }
    }

    /** Height (blocks at scale 1) the tail is raised at slam progress t (0..1). */
    public static double slamLift(float t) {
        if (t <= 0 || t >= 1) return 0;
        if (t < 0.55f) return Math.sin(t / 0.55f * Math.PI / 2) * 7.5;
        if (t < 0.66f) return 7.5 - (t - 0.55f) / 0.11f * 8.5;
        return -1.0 * (1 - (t - 0.66f) / 0.34f);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        yBodyRotO = yHeadRotO = yRotO;
        yBodyRot = yHeadRot = getYRot();
        updateBody();
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        yBodyRot = getYRot();
        return animStep;
    }

    @Override
    public void travel(Vec3 input) {
        if (level().isClientSide) return;
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    protected void customServerAiStep() {
        ServerLevel sl = (ServerLevel) level();
        WorldPlan plan = WorldPlan.of(sl.getServer());
        float s = getScale();
        if (isDeadOrDying()) return;
        if (anchoredForTest) { // held surfaced and passive (tests and inspection)
            target = null;
            steer(getYRot(), 0, WorldPlan.SEA_LEVEL + (state() == FAINT ? 0.15 : 0.6) * s, 0);
            return;
        }
        if (--despawnScan <= 0) {
            despawnScan = 40;
            if (despawnCheck(sl)) return;
        }
        // Conqueror's Haki knock-out: float belly-up at the surface
        if (hasEffect(ModEffects.KNOCKED_OUT)) {
            if (state() != FAINT) setState(FAINT);
        } else if (state() == FAINT && stateAge(0) > 60) {
            setState(FLEE);
        }
        switch (state()) {
            case FAINT -> {
                steer(getYRot(), 0, WorldPlan.SEA_LEVEL + 0.15 * s, 0);
                return;
            }
            case FLEE -> {
                Player p = sl.getNearestPlayer(this, 200);
                float yaw = p == null ? getYRot() : yawTo(position().subtract(p.position()));
                yaw = avoid(plan, yaw, s);
                steer(yaw, 0.45 + 0.06 * s, deepY(plan, s), 6 / s);
                return;
            }
            default -> {}
        }
        if (target != null && !validTarget(target, plan)) {
            target = null;
            if (state() != SWIM) setState(SWIM);
        }
        if (target == null && --targetScan <= 0) {
            targetScan = 20;
            target = findTarget(sl, plan);
            if (target != null && !announced) {
                announced = true;
                announce(sl);
            }
        }
        if (target == null) {
            wander(sl, plan, s);
            return;
        }
        fight(sl, plan, s);
    }

    private boolean despawnCheck(ServerLevel sl) {
        if (anchoredForTest) return false;
        Player near = sl.getNearestPlayer(getX(), getY(), getZ(), 200, false);
        boolean fleeing = state() == FLEE;
        if (near == null ? target == null || fleeing : fleeing && near.distanceToSqr(this) > 100 * 100) {
            discard();
            return true;
        }
        if (near == null) return false;
        if (target == null && near.distanceToSqr(this) > 140 * 140) {
            noPlayerTicks += 40;
            if (noPlayerTicks > 1200) {
                discard();
                return true;
            }
        } else noPlayerTicks = 0;
        return false;
    }

    private void wander(ServerLevel sl, WorldPlan plan, float s) {
        if (state() != SWIM) setState(SWIM);
        if (--wanderTimer <= 0) {
            wanderTimer = 80 + random.nextInt(140);
            wanderYaw = getYRot() + (random.nextFloat() - 0.5f) * 120;
            // drift toward the nearest sailor, so they glimpse what lives below
            Player p = sl.getNearestPlayer(this, 160);
            if (p != null && random.nextInt(3) == 0) wanderYaw = yawTo(p.position().subtract(position())) + (random.nextFloat() - 0.5f) * 60;
        }
        float yaw = avoid(plan, wanderYaw, s);
        if (yaw != wanderYaw) wanderYaw = yaw;
        steer(yaw, 0.22 + 0.03 * s, deepY(plan, s), 5 / s);
    }

    private void fight(ServerLevel sl, WorldPlan plan, float s) {
        Vec3 tp = target.position();
        Vec3 head = headCenter();
        double dist = Math.hypot(tp.x - head.x, tp.z - head.z);
        double r = circleRadius();
        switch (state()) {
            case LOOM -> {
                Vec3 want = bitePoint.add(head.subtract(bitePoint).multiply(1, 0, 1).normalize().scale(2 + 1.4 * s));
                float yaw = yawTo(bitePoint.subtract(head));
                Vec3 to = want.subtract(head);
                setDeltaMovement(new Vec3(to.x, 0, to.z).scale(0.12));
                steerVertical(WorldPlan.SEA_LEVEL + 3 + 2.4 * s, yaw, 8);
                if (stateAge(0) >= LOOM_TICKS) {
                    setState(BITE);
                    sl.playSound(null, BlockPos.containing(head), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.45f);
                }
                return;
            }
            case BITE -> {
                if (biteTarget != null && biteTarget.isAlive()) bitePoint = biteSpot(biteTarget);
                Vec3 to = bitePoint.subtract(head);
                double d = to.length();
                setYRot(Mth.approachDegrees(getYRot(), yawTo(to), 20));
                setXRot((float) Mth.clamp(-Math.toDegrees(Math.atan2(to.y, Math.max(0.1, Math.hypot(to.x, to.z)))), -60, 60));
                setDeltaMovement(d < 1e-3 ? Vec3.ZERO : to.scale(Math.min(1, (1.1 + 0.25 * s) / d)));
                if (!hitThisAttack && (d < 1.2 + 0.9 * s || stateAge(0) >= BITE_TICKS - 2)) {
                    hitThisAttack = true;
                    biteImpact(sl, s);
                }
                if (stateAge(0) >= BITE_TICKS) {
                    setState(SWIM);
                    attackCooldown = cooldown();
                }
                return;
            }
            case SLAM -> {
                circle(plan, tp, dist, r, s, 0.6f);
                float t = stateAge(0) / SLAM_TICKS;
                if (!hitThisAttack && t >= 0.64f) {
                    hitThisAttack = true;
                    slamImpact(sl, s);
                }
                if (t >= 1) {
                    setState(SWIM);
                    attackCooldown = cooldown();
                }
                return;
            }
            default -> {}
        }
        // approach, then circle the prey
        if (dist > r + 18) {
            float yaw = avoidLoose(plan, yawTo(tp.subtract(head)), s);
            double hy = dist > 60 ? WorldPlan.SEA_LEVEL - 4 - s : WorldPlan.SEA_LEVEL + 0.2 * s;
            steer(yaw, 0.42 + 0.06 * s, hy, 7 / s);
            return;
        }
        circle(plan, tp, dist, r, s, 1f);
        if (--attackCooldown > 0) return;
        double tailD = Math.hypot(tailTip().x - tp.x, tailTip().z - tp.z);
        boolean canSlam = tailD < slamRadius() + extent(target) * 0.5 && random.nextInt(3) != 0;
        if (canSlam) {
            setState(SLAM);
            sl.playSound(null, BlockPos.containing(tailTip()), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 0.5f);
        } else if (dist < r + 8) {
            biteTarget = target;
            bitePoint = biteSpot(target);
            setState(LOOM);
            looms++;
            sl.playSound(null, BlockPos.containing(head), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 2.5f, 0.4f);
        } else attackCooldown = 10;
    }

    private void circle(WorldPlan plan, Vec3 tp, double dist, double r, float s, float speedMul) {
        Vec3 head = headCenter();
        double ang = Math.atan2(head.z - tp.z, head.x - tp.x);
        double tx = -Math.sin(ang) * circleDir, tz = Math.cos(ang) * circleDir;
        double err = Mth.clamp((dist - r) / Math.max(4, r) * 1.6, -1, 1);
        double vx = tx - Math.cos(ang) * err, vz = tz - Math.sin(ang) * err;
        float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
        steer(yaw, (0.32 + 0.05 * s) * speedMul, WorldPlan.SEA_LEVEL + 0.25 * s, 9 / s);
    }

    private int cooldown() {
        return 70 - tier() * 8 + random.nextInt(30);
    }

    public double circleRadius() {
        float s = getScale();
        double r = 5 + 3.2 * s;
        if (target instanceof ShipEntity ship) r = Math.max(r, ship.cls.length * 0.5 + 4 + 1.3 * s);
        return r;
    }

    public double slamRadius() {
        return 3 + 2.4 * getScale();
    }

    private static double extent(Entity e) {
        return e instanceof ShipEntity ship ? ship.cls.length : e.getBbWidth();
    }

    /** Where to bite: the near rail of a ship's deck, or a creature's body. */
    private Vec3 biteSpot(Entity e) {
        if (e instanceof ShipEntity ship) {
            Vec3 local = ship.toLocal(headCenter());
            double half = ship.cls.length / 2.0 - 1;
            double lz = Mth.clamp(local.z, -half, half);
            double lx = Mth.clamp(local.x, -ship.cls.beam / 2.0 + 0.5, ship.cls.beam / 2.0 - 0.5);
            return ship.toWorld(lx, ship.cls.freeboard + 0.8, lz);
        }
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private void biteImpact(ServerLevel sl, float s) {
        bites++;
        int t = tier();
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        float hull = new float[]{0, 22, 40, 70}[t];
        Vec3 at = bitePoint;
        ShipEntity hitShip = biteTarget instanceof ShipEntity se ? se : null;
        if (hitShip == null) {
            for (ShipEntity se : sl.getEntitiesOfClass(ShipEntity.class, new AABB(at, at).inflate(20), se -> !se.isSinking())) {
                if (se.carries(biteTarget instanceof LivingEntity le ? le : this) || se.toLocal(at).horizontalDistance() < se.cls.length * 0.5) hitShip = se;
            }
        }
        if (hitShip != null) {
            hitShip.hurtHull(hull, this);
            sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()), at.x, at.y, at.z, 40, 1 + s * 0.5, 0.6, 1 + s * 0.5, 0.2);
        }
        Hits.Info info = Hits.Info.of(Element.PIERCE, 1.4f + 0.3f * s).withStun(12);
        for (LivingEntity e : Hits.sphere(this, at, 1.5 + 0.9 * s)) {
            if (e == this || e instanceof SeaKing) continue;
            Hits.hit(this, e, dmg, info);
        }
        sl.playSound(null, BlockPos.containing(at), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2.5f, 0.5f);
        sl.sendParticles(ParticleTypes.SPLASH, at.x, WorldPlan.SEA_LEVEL + 1, at.z, 60, 1.5 * s, 0.4, 1.5 * s, 0.3);
    }

    private void slamImpact(ServerLevel sl, float s) {
        slams++;
        int t = tier();
        Vec3 tip = tailTip();
        Vec3 at = new Vec3(tip.x, Math.max(tip.y, WorldPlan.SEA_LEVEL + 0.5), tip.z);
        double radius = slamRadius();
        float hull = new float[]{0, 28, 50, 90}[t];
        for (ShipEntity se : sl.getEntitiesOfClass(ShipEntity.class, new AABB(at, at).inflate(radius + 20), se -> !se.isSinking())) {
            Vec3 local = se.toLocal(at);
            double dz = Math.max(0, Math.abs(local.z) - se.cls.length / 2.0), dx = Math.max(0, Math.abs(local.x) - se.cls.beam / 2.0);
            if (Math.hypot(dx, dz) <= radius) se.hurtHull(hull, this);
        }
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8f;
        Hits.Info info = Hits.Info.of(Element.BLUNT, 2.2f + 0.4f * s).withStun(20).asUnblockable();
        for (LivingEntity e : Hits.sphere(this, at, radius)) {
            if (e == this || e instanceof SeaKing) continue;
            Hits.hit(this, e, dmg, info);
        }
        sl.sendParticles(ParticleTypes.SPLASH, at.x, WorldPlan.SEA_LEVEL + 1, at.z, 160, radius * 0.6, 0.6, radius * 0.6, 0.5);
        sl.sendParticles(ParticleTypes.CLOUD, at.x, WorldPlan.SEA_LEVEL + 1.5, at.z, 30, radius * 0.4, 0.8, radius * 0.4, 0.05);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.55f);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 3f, 0.5f);
    }

    // ------------------------------------------------------------------ steering

    private void steer(float yaw, double wantSpeed, double headY, float maxTurn) {
        float y = Mth.approachDegrees(getYRot(), yaw, Math.max(0.5f, maxTurn));
        setYRot(y);
        speed += (float) Mth.clamp(wantSpeed - speed, -0.03, 0.02);
        double hy = headCenter().y;
        double vy = Mth.clamp((headY - hy) * 0.12, -0.3, 0.3);
        double rad = Math.toRadians(y);
        setDeltaMovement(-Math.sin(rad) * speed, vy, Math.cos(rad) * speed);
        setXRot((float) Mth.clamp(-Math.toDegrees(Math.atan2(vy, Math.max(0.08, speed))), -45, 45));
    }

    private void steerVertical(double headY, float yaw, float maxTurn) {
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxTurn));
        double vy = Mth.clamp((headY - headCenter().y) * 0.18, -0.35, 0.35);
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x, vy, v.z);
        setXRot((float) Mth.approach(getXRot(), 25f, 3f));
        speed = 0;
    }

    private static float yawTo(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    /** Swimming depth for the head centre: deep water, clear of the floor. */
    private double deepY(WorldPlan plan, float s) {
        double want = WorldPlan.SEA_LEVEL - 6 - 2.4 * s;
        if (plan == null) return want;
        WorldPlan.Sample smp = new WorldPlan.Sample();
        plan.sample(getBlockX(), getBlockZ(), smp);
        return Math.max(want, Math.min(WorldPlan.SEA_LEVEL - 1.5, smp.height + 1.5 + diameter(0) * s * 0.6));
    }

    /** True when a probe point is open, deep, loaded water outside safe harbours. */
    private boolean openWater(WorldPlan plan, double x, double z, double minDepth) {
        if (!level().hasChunkAt(BlockPos.containing(x, WorldPlan.SEA_LEVEL, z))) return false;
        if (plan == null) return true;
        WorldPlan.Sample smp = new WorldPlan.Sample();
        plan.sample((int) Math.floor(x), (int) Math.floor(z), smp);
        if (smp.height > WorldPlan.SEA_LEVEL - minDepth || smp.canal || smp.redLine) return false;
        return !SeaKingSpawner.isSafe(plan, (int) x, (int) z);
    }

    private float avoid(WorldPlan plan, float yaw, float s) {
        return avoidWith(plan, yaw, s, 3 + 1.2 * s);
    }

    private float avoidLoose(WorldPlan plan, float yaw, float s) {
        return avoidWith(plan, yaw, s, 2 + 0.5 * s);
    }

    private float avoidWith(WorldPlan plan, float yaw, float s, double minDepth) {
        // re-probe the sea ahead every 4 ticks (or when the wanted heading changes a lot); reuse the answer otherwise
        if (tickCount - avoidTick < 4 && Math.abs(Mth.wrapDegrees(yaw - avoidIn)) < 10) return yaw + avoidOff;
        avoidTick = tickCount;
        avoidIn = yaw;
        avoidOff = probeAvoid(plan, yaw, s, minDepth);
        return yaw + avoidOff;
    }

    private int avoidTick = -100;
    private float avoidIn, avoidOff;

    private float probeAvoid(WorldPlan plan, float yaw, float s, double minDepth) {
        Vec3 h = headCenter();
        double reach = 6 + 5 * s;
        for (float off : new float[]{0, 35, -35, 70, -70, 110, -110, 160, -160}) {
            double r = Math.toRadians(yaw + off);
            double dx = -Math.sin(r), dz = Math.cos(r);
            if (openWater(plan, h.x + dx * reach, h.z + dz * reach, minDepth) && openWater(plan, h.x + dx * reach * 0.5, h.z + dz * reach * 0.5, minDepth))
                return off;
        }
        return 180;
    }

    // ------------------------------------------------------------------ targets

    private Entity findTarget(ServerLevel sl, WorldPlan plan) {
        SeaZone zone = WorldLayout.zoneAt(getBlockX(), getBlockZ());
        double range = (48 + 24 * tier()) * (zone == SeaZone.CALM_BELT ? 1.5 : 1);
        Entity best = null;
        double bd = range * range;
        for (ShipEntity ship : sl.getEntitiesOfClass(ShipEntity.class, getBoundingBox().inflate(range), se -> !se.isSinking() && !se.flag(ShipEntity.F_NPC))) {
            double d = ship.distanceToSqr(this);
            if (d < bd && validTarget(ship, plan) && !deterred(sl, ship)) {
                bd = d;
                best = ship;
            }
        }
        if (best != null) return best;
        for (Player p : sl.players()) {
            double d = p.distanceToSqr(this);
            if (d < bd && validTarget(p, plan) && !deterred(sl, p)) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private boolean validTarget(Entity e, WorldPlan plan) {
        if (!e.isAlive() || e.level() != level()) return false;
        if (e.distanceToSqr(this) > 220 * 220) return false;
        if (plan != null && SeaKingSpawner.isSafe(plan, e.getBlockX(), e.getBlockZ())) return false;
        if (e instanceof ShipEntity ship) return !ship.isSinking();
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (p.getVehicle() instanceof ShipEntity) return true;
            return p.isInWater() || SeaKingSpawner.onShip(p) != null;
        }
        return e instanceof LivingEntity;
    }

    /** Conqueror's Haki: a Sea King will not approach a will that would overwhelm it. */
    private boolean deterred(ServerLevel sl, Entity e) {
        List<Player> ps = new ArrayList<>();
        if (e instanceof Player p) ps.add(p);
        else if (e instanceof ShipEntity ship) ps.addAll(sl.getEntitiesOfClass(Player.class, ship.getBoundingBox().inflate(ship.cls.length * 0.6, 6, ship.cls.length * 0.6)));
        for (Player p : ps) {
            if (!CombatState.of(p).conquerorAura) continue;
            int power = Haki.conquerorLevel(p) * 2 + Haki.willpower(p);
            if (willpower() * 1.3 < power) return true;
        }
        return false;
    }

    private void announce(ServerLevel sl) {
        Component msg = Component.translatable(tier() >= 3 ? "grandline.seaking.approach_colossal" : "grandline.seaking.approach").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(140))) p.displayClientMessage(msg, true);
    }

    /** Called from Conqueror's Haki pressure (see HakiAbilities#conquerorPressure). */
    public void onConquerorPressure(LivingEntity user, boolean knockedOut, boolean overpowered) {
        if (level().isClientSide) return;
        if (knockedOut) {
            target = null;
            setState(FAINT);
            ((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, getX(), WorldPlan.SEA_LEVEL + 1, getZ(), 80, 3, 0.5, 3, 0.3);
        } else if (overpowered) {
            target = null;
            setState(FLEE);
        } else if (user != null) {
            target = SeaKingSpawner.onShip(user) != null ? SeaKingSpawner.onShip(user) : user;
        }
    }

    // ------------------------------------------------------------------ damage

    public boolean hurtFromPart(SeaKingPart part, DamageSource src, float amount) {
        return hurt(src, amount);
    }

    /** Cannon fire (a gunner at a ship's cannon station, cannonballs, explosions) hits Sea Kings hard. */
    private static boolean cannonLike(DamageSource src) {
        if (src.is(ModDamageTypes.CANNON) || src.is(DamageTypeTags.IS_EXPLOSION)) return true;
        if (src.getDirectEntity() instanceof AbilityProjectile p && "cannonball".equals(p.kind())) return true;
        if (src.getEntity() instanceof LivingEntity a && a.getVehicle() instanceof ShipEntity ship) {
            int seat = ship.seatIndex(a);
            return seat >= 1 && seat <= ship.cls.cannons;
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.is(DamageTypes.IN_WALL) || src.is(DamageTypes.DROWN) || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypes.CRAMMING)) return false;
        if (cannonLike(src)) amount *= 2.0f;
        invulnerableTime = 0;
        boolean r = super.hurt(src, amount);
        invulnerableTime = 0;
        if (r && !level().isClientSide && src.getEntity() instanceof LivingEntity attacker && attacker != this) {
            ShipEntity ship = SeaKingSpawner.onShip(attacker);
            if (state() != FAINT && state() != FLEE) target = ship != null ? ship : attacker;
            if (getHealth() < getMaxHealth() * 0.2f && tier() < 3 && random.nextInt(4) == 0 && state() != FAINT) setState(FLEE);
        }
        return r;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity e) {}

    @Override
    public boolean canBeCollidedWith() {
        return isAlive();
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    public void checkDespawn() {}

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        setDeltaMovement(0, -0.04 * getScale(), 0);
        if (!level().isClientSide) move(MoverType.SELF, getDeltaMovement());
        if (deathTime >= 50 && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void die(DamageSource src) {
        com.grandline.GrandLine.LOGGER.debug("Sea King {} ({} m) slain: {} by {}", getId(), (int) length(), src.getMsgId(), src.getEntity());
        super.die(src);
        if (level() instanceof ServerLevel sl) {
            setState(FAINT);
            if (src.getEntity() instanceof ServerPlayer sp) {
                CharacterData d = Characters.get(sp);
                if (d.created) {
                    int levels = d.addXp(new int[]{0, 220, 650, 1600}[tier()]);
                    sp.sendSystemMessage(Component.translatable("grandline.seaking.slain", getDisplayName()).withStyle(ChatFormatting.GOLD));
                    com.grandline.journey.Journey.onSeaKingSlain(sp);
                    if (levels > 0) {
                        sp.sendSystemMessage(Component.translatable("grandline.level.up", d.level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                        CharacterManager.applyAttributes(sp);
                    }
                    CharacterManager.markDirty(sp);
                }
            }
            sl.sendParticles(ParticleTypes.SPLASH, getX(), WorldPlan.SEA_LEVEL + 1, getZ(), 120, 3 * getScale(), 0.5, 3 * getScale(), 0.4);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource src, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, src, recentlyHit);
        int t = tier();
        Vec3 at = new Vec3(getX(), Math.max(getY(), WorldPlan.SEA_LEVEL + 1), getZ());
        int steaks = 2 + t * 3 + random.nextInt(2 + t * 2);
        drop(level, at, new ItemStack(ModItems.SEA_KING_STEAK.get(), steaks));
        for (int i = 0; i < t; i++) {
            ItemStack pouch = new ItemStack(ModItems.BELI_POUCH.get());
            int lo = new int[]{0, 1500, 6000, 18000}[t];
            pouch.set(ModDataComponents.BELI.get(), lo + random.nextInt(lo * 2));
            drop(level, at, pouch);
        }
    }

    private void drop(ServerLevel level, Vec3 at, ItemStack stack) {
        var ie = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, stack);
        ie.setDefaultPickUpDelay();
        if (captureDrops() != null) captureDrops().add(ie);
        else level.addFreshEntity(ie);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) LIVE.add(this);
        updateBody();
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        LIVE.remove(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        double r = 64 + length() * 2;
        return d < r * r;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = getBoundingBox();
        float s = getScale();
        for (Vec3 p : seg) if (p != null) box = box.minmax(new AABB(p, p).inflate(2 * s + 2, 8 * s + 2, 2 * s + 2));
        return box;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        configure(t.getByte("Variant"), Math.max(1, t.getByte("Tier")), t.contains("Length") ? t.getFloat("Length") : 30);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putByte("Variant", (byte) variant());
        t.putByte("Tier", (byte) tier());
        t.putFloat("Length", length());
    }

    // ------------------------------------------------------------------ GeckoLib

    private static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.sea_king.swim");
    private static final RawAnimation LOOM_ANIM = RawAnimation.begin().thenLoop("animation.sea_king.roar");
    private static final RawAnimation BITE_ANIM = RawAnimation.begin().thenLoop("animation.sea_king.bite");
    private static final RawAnimation FAINT_ANIM = RawAnimation.begin().thenLoop("animation.sea_king.faint");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 4, st -> {
            byte s = state();
            if (isDeadOrDying() || s == FAINT) return st.setAndContinue(FAINT_ANIM);
            if (s == LOOM) return st.setAndContinue(LOOM_ANIM);
            if (s == BITE) return st.setAndContinue(BITE_ANIM);
            return st.setAndContinue(SWIM_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
