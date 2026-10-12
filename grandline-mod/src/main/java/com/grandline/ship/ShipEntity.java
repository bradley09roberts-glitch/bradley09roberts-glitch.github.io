package com.grandline.ship;

import com.grandline.net.GLNetwork;
import com.grandline.player.Characters;
import com.grandline.registry.ModItems;
import com.grandline.world.ReverseMountain;
import com.grandline.world.WorldPlan;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A sailing ship. Server-authoritative movement driven by helm input, wind and sails; a grid of collidable parts
 * forms a walkable deck; entities standing on deck are carried as it moves and turns. Persistent across saves.
 */
public class ShipEntity extends Entity {
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> HULL = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> SAIL = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SAIL_COLOR = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HULL_COLOR = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> UPGRADES = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.STRING);
    public static final byte F_ANCHOR = 1, F_SINKING = 2, F_CANAL = 4, F_NPC = 8;

    public final ShipClass cls;
    private final ShipPart[] parts;
    private final Vec3[] seats;          // local (x, y above waterline, z)
    public final int helmSeat = 0;
    private final Map<UUID, Integer> seatOf = new HashMap<>();

    // server state
    public UUID shipId = UUID.randomUUID();
    @Nullable public UUID owner;
    public String ownerName = "";
    public int interiorIndex = -1;
    private float rudder, speed;
    private int sinkTicks;
    private long lastHelmInput;
    private final Map<Integer, Long> cannonCooldown = new HashMap<>();
    // canal (Reverse Mountain) ride
    int canalLeg = -1;
    private double canalT;
    /** Ferry / Sea Train service (see navigation.Ferries): transient, never saved. */
    @Nullable public Vec3 ferryTarget;
    public String ferryIsland = "";
    public float ferrySpeed = 1.8f;
    public int ferryIdle;
    public float ferryDetour;
    public int ferryDetourT;
    @Nullable public Vec3 ferryWaypoint;

    @Override
    public boolean shouldBeSaved() {
        return ferryTarget == null && super.shouldBeSaved();
    }

    /** Crew bonuses (set every 2 s by CrewBonuses while the captain is aboard). */
    public float crewSpeed = 1f, crewCannon = 1f;
    /** Active special route ride (see {@link SpecialRoutes}); -1 when sailing normally. Not saved. */
    public int ride = -1;
    public double rideT;

    // client interpolation
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;

    public ShipEntity(EntityType<? extends ShipEntity> type, Level level, ShipClass cls) {
        super(type, level);
        this.cls = cls;
        List<ShipPart> list = new ArrayList<>();
        float height = cls.draft + cls.freeboard;
        float half = cls.length / 2f;
        float step = cls == ShipClass.ROWBOAT ? 1.6f : 2.0f;
        float width = step * 1.3f;
        for (float lz = -half + step / 2; lz <= half - step / 2 + 0.01f; lz += step) {
            float t = (lz + half) / cls.length;
            float hb = cls.halfBeamAt(t);
            int across = Math.max(1, Math.round(hb * 2 / step));
            for (int i = 0; i < across; i++) {
                float lx = across == 1 ? 0 : -hb + step / 2 + i * (hb * 2 - step) / Math.max(1, across - 1);
                float top = cls.freeboard + (t < 0.18f && cls.interior ? 1.0f : 0f); // raised quarterdeck at the stern
                list.add(new ShipPart(this, lx, lz, width * (across == 1 ? Math.min(1f, hb * 2 / width + 0.2f) : 1f), height + (top - cls.freeboard), top));
            }
        }
        for (int m = 0; m < cls.masts; m++) {
            float lz = mastZ(m);
            list.add(new ShipPart(this, 0, lz, 0.7f, 10f + cls.freeboard, cls.freeboard + 8));
        }
        this.parts = list.toArray(new ShipPart[0]);
        // seats: helm at the stern, then cannons along both sides, then passenger benches
        List<Vec3> s = new ArrayList<>();
        s.add(new Vec3(0, deckAt(-half + 2.5f), -half + 2.5));
        for (int c = 0; c < cls.cannons; c++) {
            int side = c % 2 == 0 ? 1 : -1;
            double lz = -half * 0.55 + (c / 2) * (cls.length * 0.75 / Math.max(1, cls.cannons / 2));
            s.add(new Vec3(side * (cls.halfBeamAt((float) ((lz + half) / cls.length)) - 1.0), cls.freeboard, lz));
        }
        while (s.size() < cls.seats + cls.cannons) s.add(new Vec3((s.size() % 2 == 0 ? 0.8 : -0.8), cls.freeboard, -half + 4 + s.size() * 1.2 % (cls.length - 6)));
        this.seats = s.toArray(new Vec3[0]);
        this.setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        this.noCulling = true;
        this.blocksBuilding = true;
    }

    public float mastZ(int m) {
        float half = cls.length / 2f;
        if (cls.masts == 1) return cls.length * 0.08f;
        return -half * 0.35f + m * (cls.length * 0.6f / Math.max(1, cls.masts - 1));
    }

    private float deckAt(float lz) {
        float t = (lz + cls.length / 2f) / cls.length;
        return cls.freeboard + (t < 0.18f && cls.interior ? 1.0f : 0f);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(NAME, "");
        b.define(HULL, 100f);
        b.define(SAIL, (byte) 0);
        b.define(FLAGS, F_ANCHOR);
        b.define(SAIL_COLOR, 0xF4EEDC);
        b.define(HULL_COLOR, 0x8A5A32);
        b.define(SPEED, 0f);
        b.define(UPGRADES, "");
    }

    // ------------------------------------------------------------------ accessors

    public String shipName() { return entityData.get(NAME); }
    public void setShipName(String n) { entityData.set(NAME, n); }
    public float hull() { return entityData.get(HULL); }
    public float maxHull() { return cls.hull * (hasUpgrade("reinforced_hull") ? 1.5f : 1f); }
    public int sail() { return entityData.get(SAIL); }
    public float speed() { return entityData.get(SPEED); }
    public int sailColor() { return entityData.get(SAIL_COLOR); }
    public int hullColor() { return entityData.get(HULL_COLOR); }
    public void setColors(int sail, int hull) { entityData.set(SAIL_COLOR, sail); entityData.set(HULL_COLOR, hull); }
    public boolean flag(byte f) { return (entityData.get(FLAGS) & f) != 0; }
    public void setFlag(byte f, boolean on) { entityData.set(FLAGS, (byte) (on ? entityData.get(FLAGS) | f : entityData.get(FLAGS) & ~f)); }
    public boolean isSinking() { return flag(F_SINKING); }
    public boolean anchored() { return flag(F_ANCHOR); }
    public Set<String> upgrades() { String u = entityData.get(UPGRADES); return u.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(u.split(","))); }
    public boolean hasUpgrade(String id) { return upgrades().contains(id); }
    public void removeUpgrade(String id) {
        Set<String> s = new TreeSet<>(upgrades());
        s.remove(id);
        entityData.set(UPGRADES, String.join(",", s));
    }

    public void addUpgrade(String id) {
        Set<String> s = new TreeSet<>(upgrades());
        s.add(id);
        entityData.set(UPGRADES, String.join(",", s));
    }
    public Vec3 seatLocal(int i) { return seats[Math.max(0, Math.min(seats.length - 1, i))]; }
    public int seatCount() { return seats.length; }
    public int seatIndex(Entity e) { return seatOf.getOrDefault(e.getUUID(), -1); }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    // ------------------------------------------------------------------ geometry

    public Vec3 toWorld(double lx, double ly, double lz) {
        double yaw = Math.toRadians(getYRot());
        double c = Math.cos(yaw), s = Math.sin(yaw);
        return new Vec3(getX() + lx * c - lz * s, getY() + ly, getZ() + lx * s + lz * c);
    }

    public Vec3 toLocal(Vec3 w) {
        double yaw = Math.toRadians(getYRot());
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double dx = w.x - getX(), dz = w.z - getZ();
        return new Vec3(dx * c + dz * s, w.y - getY(), -dx * s + dz * c);
    }

    public Vec3 heading() {
        double yaw = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private void updateParts() {
        for (ShipPart p : parts) {
            Vec3 w = toWorld(p.localX, p.top - p.getBbHeight(), p.localZ);
            p.setPos(w.x, w.y, w.z);
            p.xo = p.xOld = w.x;
            p.yo = p.yOld = w.y;
            p.zo = p.zOld = w.z;
        }
    }

    /** True when an entity stands on (or just above) this ship's deck. */
    public boolean carries(Entity e) {
        return carries(e, 0);
    }

    /** As {@link #carries(Entity)}, judged against the deck as it was before this tick's rise of {@code dy}. */
    boolean carries(Entity e, double dy) {
        if (e == this || e.isPassenger() || e instanceof ShipPart || e instanceof ShipEntity) return false;
        AABB box = e.getBoundingBox();
        for (ShipPart p : parts) {
            AABB pb = p.getBoundingBox();
            if (box.maxX < pb.minX || box.minX > pb.maxX || box.maxZ < pb.minZ || box.minZ > pb.maxZ) continue;
            double above = box.minY - (pb.maxY - dy);
            if (above > -0.35 && above < 2.0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        double ox = getX(), oz = getZ();
        float oyaw = getYRot();
        super.tick();
        if (level().isClientSide) {
            tickLerp();
        } else {
            tickServer();
        }
        updateParts();
        double dx = getX() - ox, dz = getZ() - oz;
        float dyaw = Mth.wrapDegrees(getYRot() - oyaw);
        if (Math.abs(dx) + Math.abs(dz) + Math.abs(dyaw) > 1e-5) carry(dx, getY() - yo, dz, dyaw, ox, oz);
    }

    private void carry(double dx, double dy, double dz, float dyaw, double cx, double cz) {
        AABB area = getBoundingBox().inflate(cls.length / 2.0 + 2, 6, cls.length / 2.0 + 2);
        double rad = Math.toRadians(dyaw), c = Math.cos(rad), s = Math.sin(rad);
        for (Entity e : level().getEntities(this, area, en -> !(en instanceof ShipPart))) {
            if (e instanceof Player p) {
                // players are client-authoritative: their own client applies the carry
                if (!level().isClientSide || !ShipClientHooks.isLocalPlayer(p)) continue;
            } else if (level().isClientSide) continue;
            if (!carries(e, dy)) continue;
            double rx = e.getX() - cx, rz = e.getZ() - cz;
            double nx = rx * c - rz * s, nz = rx * s + rz * c;
            e.setPos(cx + nx + dx, e.getY() + dy, cz + nz + dz);
            if (dy < 0) e.fallDistance = 0;
            e.setYRot(e.getYRot() + dyaw);
            e.setYHeadRot(e.getYHeadRot() + dyaw);
            if (e instanceof LivingEntity le) le.yBodyRot += dyaw;
        }
    }

    /** Testing/automation: sail without a helmsman, optionally faster. */
    public boolean autopilot;
    public float testSpeedMult = 1f;

    /** NPC ships: "marine" or "pirate". */
    public String npcFaction = "";
    private int npcFireTimer;

    private void npcAi(ServerLevel sl) {
        Player near = sl.getNearestPlayer(getX(), getY(), getZ(), 320, false);
        if (near == null) {
            for (Entity e : sl.getEntitiesOfClass(com.grandline.npc.GLNpc.class, getBoundingBox().inflate(cls.length), this::carries)) e.discard();
            discard();
            return;
        }
        // pick a target: the nearest player ship, else the nearest player at sea
        Entity target = null;
        double bd = 220 * 220;
        for (ShipEntity s : sl.getEntitiesOfClass(ShipEntity.class, getBoundingBox().inflate(220), s -> s != this && !s.flag(F_NPC))) {
            double d = s.distanceToSqr(this);
            if (d < bd && hostileToShip(s)) {
                bd = d;
                target = s;
            }
        }
        if (target == null && near.distanceToSqr(this) < 160 * 160 && hostileToPlayer(near)) target = near;
        setFlag(F_ANCHOR, false);
        if (target == null) {
            setSail(1);
            rudder = (float) Math.sin(sl.getGameTime() / 400.0) * 0.3f;
            lastHelmInput = sl.getGameTime();
            return;
        }
        Vec3 to = target.position().subtract(position());
        double dist = to.horizontalDistance();
        float want;
        if (dist > 40) want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));          // close in
        else want = (float) Math.toDegrees(Math.atan2(-to.x, to.z)) + 90;               // circle to bring a broadside to bear
        float diff = Mth.wrapDegrees(want - getYRot());
        rudder = Mth.clamp(diff / 30f, -1, 1);
        lastHelmInput = sl.getGameTime();
        setSail(dist > 25 ? 3 : 2);
        if (--npcFireTimer <= 0 && dist < 70 && cls.cannons > 0) {
            npcFireTimer = 50 - Math.min(30, cls.cannons * 2);
            Vec3 aim = target.position().add(0, 1.5, 0).add(target.getDeltaMovement().scale(dist / 2.2)).subtract(position());
            Vec3 starboard = toWorld(1, 0, 0).subtract(position()).normalize();
            boolean right = aim.normalize().dot(starboard) > 0;
            for (int seat = 1; seat <= cls.cannons; seat++) {
                if ((seatLocal(seat).x >= 0) != right) continue;
                if (sl.random.nextInt(3) != 0) continue;
                fireFrom(seat, aim.add(sl.random.nextGaussian() * 2, 0, sl.random.nextGaussian() * 2), null, 10f + cls.ordinal() * 3f);
            }
        }
    }

    private boolean hostileToShip(ShipEntity s) {
        if (s.owner == null) return false;
        Player p = level().getPlayerByUUID(s.owner);
        return p == null || hostileToPlayer(p);
    }

    private boolean hostileToPlayer(Player p) {
        var d = Characters.get(p);
        if (npcFaction.equals("marine")) return d.bounty > 0 || d.faction == com.grandline.player.Faction.PIRATE || d.faction == com.grandline.player.Faction.REVOLUTIONARY;
        return d.faction != com.grandline.player.Faction.PIRATE || d.bounty < 1_000_000 || level().random.nextBoolean();
    }

    private void tickServer() {
        ServerLevel sl = (ServerLevel) level();
        if (flag(F_NPC) && !isSinking() && canalLeg < 0) npcAi(sl);
        if (isRemoved()) return;
        if (isSinking()) {
            sinkTicks++;
            setPos(getX(), getY() - 0.04, getZ());
            if (sinkTicks % 10 == 0) sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1, getZ(), 30, cls.beam / 2.0, 1, cls.length / 2.0, 0.1);
            if (sinkTicks > 160) {
                ShipRegistry.get(sl.getServer()).markSunk(this);
                ejectPassengers();
                discard();
            }
            return;
        }
        if (ride >= 0) {
            SpecialRoutes.tickRide(this, sl);
            return;
        }
        if (canalLeg >= 0) {
            tickCanal();
            return;
        }
        if (ShipTravel.isDeep(sl)) SpecialRoutes.bubble(this, sl);
        float target = 0;
        Vec3 wind = Wind.at(sl, getX(), getZ());
        double windF = Wind.factor(wind, getYRot());
        boolean helmed = getFirstPassengerAtSeat(helmSeat) != null || flag(F_NPC) || autopilot;
        if (ferryTarget != null) {
            target = com.grandline.navigation.Ferries.steer(this, sl);
            if (isRemoved()) return;
            lastHelmInput = sl.getGameTime();
        } else if (!anchored() && helmed) {
            float sailMult = hasUpgrade("fine_sails") ? 1.15f : 1f;
            target = (float) (cls.speed * sail() / 3f * windF * sailMult) * testSpeedMult * crewSpeed;
            if (hasUpgrade("paddle_wheel") && sail() > 0) target = Math.max(target, cls.speed * 0.55f);
            for (Entity p : getPassengers()) if (p instanceof LivingEntity le && com.grandline.combat.CombatState.of(le).hasBuff("tailwind")) target = Math.max(target, cls.speed * 1.25f);
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cls.length / 2.0), this::carries))
                if (com.grandline.combat.CombatState.of(le).hasBuff("tailwind")) target = Math.max(target, cls.speed * 1.25f);
        }
        if (sl.getGameTime() - lastHelmInput > 40) rudder *= 0.8f;
        speed += Mth.clamp(target - speed, -0.01f, 0.006f * testSpeedMult);
        if (speed < 0.002f && target == 0) speed = 0;
        float turn = rudder * cls.turnRate * (0.25f + Math.min(1f, speed / Math.max(0.05f, cls.speed)));
        setYRot(Mth.wrapDegrees(getYRot() + turn));
        Vec3 next = position().add(heading().scale(speed));
        // keep afloat at sea level; leave the world only through ReverseMountain rides
        double y = WorldPlan.SEA_LEVEL + 1 - 0.1;
        if (speed > 0) {
            Vec3 bow = toWorldAt(next, 0, cls.length / 2.0 + 0.5);
            if (!sl.hasChunkAt(BlockPos.containing(bow))) {
                next = position(); // wait for the chunk ahead without losing way
            } else if (solidAt(sl, bow) || solidAt(sl, toWorldAt(next, cls.beam / 2.0, cls.length * 0.25)) || solidAt(sl, toWorldAt(next, -cls.beam / 2.0, cls.length * 0.25))) {
                if (speed > cls.speed * 0.6f) hurtHull(speed * 40, null);
                speed = 0;
                next = position();
            }
        }
        entityData.set(SPEED, speed);
        setPos(next.x, y, next.z);
        SpecialRoutes.afterMove(this, sl);
        // enter Reverse Mountain canals
        if (speed > 0.05f && Math.abs(getX()) < 1300 && Math.abs(getZ()) < 1300) tryStartCanal();
        if (sl.getGameTime() % 100 == 0) ShipRegistry.get(sl.getServer()).update(this);
        if (sl.getGameTime() % 4 == 0 && speed > 0.1f) {
            Vec3 bowP = toWorld(0, 0, cls.length / 2.0);
            sl.sendParticles(ParticleTypes.SPLASH, bowP.x, bowP.y, bowP.z, 6, cls.beam / 3.0, 0.1, 0.5, 0.1);
        }
    }

    private Vec3 toWorldAt(Vec3 origin, double lx, double lz) {
        double yaw = Math.toRadians(getYRot());
        double c = Math.cos(yaw), s = Math.sin(yaw);
        return new Vec3(origin.x + lx * c - lz * s, WorldPlan.SEA_LEVEL + 0.5, origin.z + lx * s + lz * c);
    }

    private static boolean solidAt(ServerLevel l, Vec3 p) {
        BlockPos bp = BlockPos.containing(p);
        return !l.getBlockState(bp).getCollisionShape(l, bp).isEmpty() || !l.getBlockState(bp.above()).getCollisionShape(l, bp.above()).isEmpty();
    }

    // ------------------------------------------------------------------ Reverse Mountain

    private void tryStartCanal() {
        for (int i = 0; i < ReverseMountain.UP_CANALS.length; i++) {
            int[] c = ReverseMountain.UP_CANALS[i];
            if (position().distanceToSqr(c[0], getY(), c[1]) < 30 * 30) {
                canalLeg = i;
                canalT = 0;
                setFlag(F_CANAL, true);
                level().playSound(null, blockPosition(), SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, SoundSource.NEUTRAL, 2f, 0.6f);
                for (Entity p : getPassengers()) if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("grandline.ship.reverse_mountain").withStyle(net.minecraft.ChatFormatting.AQUA));
                return;
            }
        }
    }

    private void tickCanal() {
        int[] c = canalLeg < 4 ? ReverseMountain.UP_CANALS[canalLeg] : ReverseMountain.DOWN_CANAL;
        double len = Math.hypot(c[2] - c[0], c[3] - c[1]);
        canalT += 0.6 / len;
        double t = Math.min(1, canalT);
        double x = c[0] + (c[2] - c[0]) * t, z = c[1] + (c[3] - c[1]) * t;
        double surf = canalLeg < 4 ? 63 + (ReverseMountain.SUMMIT_SURFACE - 63) * t : 63 + (ReverseMountain.SUMMIT_SURFACE - 63) * (1 - t);
        setYRot((float) Math.toDegrees(Math.atan2(-(c[2] - c[0]), c[3] - c[1])));
        setPos(x, surf + 0.9, z);
        speed = 0.6f;
        entityData.set(SPEED, speed);
        if (canalT >= 1) {
            if (canalLeg < 4) {
                canalLeg = 4; // over the summit and down into Paradise
                canalT = 0;
            } else {
                canalLeg = -1;
                setFlag(F_CANAL, false);
                setPos(getX() + 40, WorldPlan.SEA_LEVEL + 0.9, getZ());
                for (Entity p : getPassengers()) if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("grandline.ship.grand_line").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
            }
        }
    }

    // ------------------------------------------------------------------ client interpolation

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 6;
    }

    @Override
    public double lerpTargetX() { return lerpSteps > 0 ? lerpX : getX(); }
    @Override
    public double lerpTargetY() { return lerpSteps > 0 ? lerpY : getY(); }
    @Override
    public double lerpTargetZ() { return lerpSteps > 0 ? lerpZ : getZ(); }

    private void tickLerp() {
        if (lerpSteps > 0) {
            double nx = getX() + (lerpX - getX()) / lerpSteps;
            double ny = getY() + (lerpY - getY()) / lerpSteps;
            double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
            float ny2 = (float) (getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            lerpSteps--;
            setPos(nx, ny, nz);
            setYRot(ny2);
        }
    }

    // ------------------------------------------------------------------ control

    /** Helm input from the steering player. */
    public void helmInput(ServerPlayer sp, float throttle, float rudderIn, byte flags) {
        if (getFirstPassengerAtSeat(helmSeat) != sp) return;
        lastHelmInput = level().getGameTime();
        rudder = Mth.clamp(rudderIn, -1, 1);
        if ((flags & 1) != 0) setFlag(F_ANCHOR, !anchored());
        if ((flags & 8) != 0 && sail() < 3) entityData.set(SAIL, (byte) (sail() + 1));
        if ((flags & 16) != 0 && sail() > 0) entityData.set(SAIL, (byte) (sail() - 1));
        if ((flags & 4) != 0) coupDeBurst(sp);
        if (sail() > 0 && anchored() && (flags & 8) != 0) setFlag(F_ANCHOR, false);
    }

    public void setSail(int s) {
        entityData.set(SAIL, (byte) Mth.clamp(s, 0, 3));
    }

    public void setAnchored(boolean a) {
        setFlag(F_ANCHOR, a);
    }

    public void setRudder(float r) {
        rudder = r;
        lastHelmInput = level().getGameTime();
    }

    private void coupDeBurst(ServerPlayer sp) {
        if (!hasUpgrade("coup_de_burst")) {
            sp.displayClientMessage(Component.translatable("grandline.ship.no_burst"), true);
            return;
        }
        Long cd = cannonCooldown.get(-1);
        if (cd != null && cd > level().getGameTime()) return;
        cannonCooldown.put(-1, level().getGameTime() + 20 * 120);
        speed = cls.speed * 3.5f;
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 2f, 0.7f);
        ((ServerLevel) level()).sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
    }

    /** Fire the cannon at a seat toward the gunner's aim (clamped to the broadside). */
    public void fireCannon(ServerPlayer sp) {
        int seat = seatIndex(sp);
        if (seat <= 0 || seat > cls.cannons) return;
        long now = level().getGameTime();
        if (cannonCooldown.getOrDefault(seat, 0L) > now) return;
        boolean creative = sp.isCreative();
        ItemStack ammo = ItemStack.EMPTY;
        if (!creative) {
            for (ItemStack s : sp.getInventory().items) if (s.is(ModItems.CANNONBALL.get())) { ammo = s; break; }
            if (ammo.isEmpty()) {
                sp.displayClientMessage(Component.translatable("grandline.combat.no_ammo"), true);
                return;
            }
            ammo.shrink(1);
        }
        cannonCooldown.put(seat, now + 50);
        fireFrom(seat, sp.getLookAngle(), sp, (14f + (com.grandline.player.Characters.get(sp).mastery("weapon/hand_cannon") * 0.1f)) * crewCannon);
    }

    public void fireFrom(int seat, Vec3 aim, @Nullable Entity shooter, float damage) {
        Vec3 local = seatLocal(seat);
        int side = local.x >= 0 ? 1 : -1;
        Vec3 sideDir = toWorld(side, 0, 0).subtract(toWorld(0, 0, 0)).normalize();
        Vec3 dir = aim.normalize();
        if (dir.dot(sideDir) < 0.3) dir = dir.add(sideDir.scale(0.6)).normalize();
        Vec3 muzzle = toWorld(local.x + side * 1.2, local.y + 0.6, local.z);
        var p = com.grandline.entity.AbilityProjectile.shoot(level(), shooter instanceof LivingEntity le ? le : null, "cannonball", 0.5f, muzzle, dir.add(0, 0.06, 0), 2.2, damage,
                com.grandline.combat.Hits.Info.of(com.grandline.combat.Element.IMPACT, 1.5f));
        p.gravity = 0.02;
        p.explosion = 2.5f;
        p.life = 120;
        level().playSound(null, BlockPos.containing(muzzle), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.2f, 1.3f);
        ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.3, 0.3, 0.3, 0.02);
    }

    // ------------------------------------------------------------------ damage & repair

    public boolean hurtPart(ShipPart part, DamageSource src, float amount) {
        if (level().isClientSide || isSinking()) return false;
        boolean heavy = src.is(DamageTypeTags.IS_EXPLOSION) || src.is(com.grandline.registry.ModDamageTypes.CANNON) || src.getDirectEntity() instanceof com.grandline.entity.AbilityProjectile;
        if (!heavy && !(src.getEntity() instanceof Player)) return false;
        hurtHull(heavy ? amount * 1.5f : amount * 0.1f, src.getEntity());
        return true;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return parts.length > 0 && hurtPart(parts[0], src, amount);
    }

    public void hurtHull(float amount, @Nullable Entity by) {
        float h = hull() - amount;
        entityData.set(HULL, Math.max(0, h));
        if (h <= 0 && !isSinking()) {
            setFlag(F_SINKING, true);
            level().playSound(null, blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 2f, 0.5f);
            for (Entity p : getPassengers()) if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("grandline.ship.sinking").withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    public void repair(float amount) {
        entityData.set(HULL, Math.min(maxHull(), hull() + amount));
    }

    public void setHull(float h) {
        entityData.set(HULL, h);
    }

    // ------------------------------------------------------------------ interaction & seats

    public InteractionResult interactPart(ShipPart part, Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ItemTags.PLANKS) && hull() < maxHull()) {
            repair(8);
            if (!player.isCreative()) held.shrink(1);
            level().playSound(null, part.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1, 1);
            return InteractionResult.CONSUME;
        }
        if (!carries(player) && !player.isPassenger()) {
            // climb aboard
            Vec3 deck = toWorld(part.localX, part.top + 0.05, part.localZ);
            player.teleportTo(deck.x, deck.y, deck.z);
            return InteractionResult.CONSUME;
        }
        // nearest station to the clicked part
        int best = -1;
        double bd = 2.6 * 2.6;
        for (int i = 0; i < seats.length; i++) {
            double d = (seats[i].x - part.localX) * (seats[i].x - part.localX) + (seats[i].z - part.localZ) * (seats[i].z - part.localZ);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        double hatchD = Math.pow(part.localX, 2) + Math.pow(part.localZ - hatchZ(), 2);
        if (cls.interior && hatchD < 2.6 * 2.6 && (best < 0 || hatchD < bd)) {
            ShipInteriors.enter(sp, this);
            return InteractionResult.CONSUME;
        }
        if (best == helmSeat && !mayCommand(sp)) {
            sp.displayClientMessage(Component.translatable("grandline.ship.not_yours"), true);
            return InteractionResult.FAIL;
        }
        if (best >= 0 && getFirstPassengerAtSeat(best) == null) {
            seatOf.put(sp.getUUID(), best);
            sp.startRiding(this, true);
            if (best == helmSeat) sp.displayClientMessage(Component.translatable("grandline.ship.helm_help"), false);
            else if (best <= cls.cannons) sp.displayClientMessage(Component.translatable("grandline.ship.cannon_help"), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Seat a player at a station (used by tests and crew commands); returns false when the seat is taken. */
    /** Seat a player (or any passenger: crew, pets) on a given seat; false if it is taken or the ride is refused. */
    public boolean seatPlayer(Entity sp, int seat) {
        if (seat < 0 || seat >= seats.length || getFirstPassengerAtSeat(seat) != null) return false;
        seatOf.put(sp.getUUID(), seat);
        if (sp.startRiding(this, true)) return true;
        seatOf.remove(sp.getUUID());
        return false;
    }

    public float hatchZ() {
        return cls.masts >= 2 ? (mastZ(0) + mastZ(1)) / 2 : -cls.length * 0.1f;
    }

    public boolean mayCommand(Player p) {
        if (owner == null || owner.equals(p.getUUID()) || p.hasPermissions(2)) return true;
        var d = Characters.get(p);
        var od = p.level().getPlayerByUUID(owner);
        return od != null && d.crewId != null && d.crewId.equals(Characters.get(od).crewId);
    }

    @Nullable
    public Entity getFirstPassengerAtSeat(int seat) {
        for (Entity e : getPassengers()) if (seatOf.getOrDefault(e.getUUID(), -1) == seat) return e;
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < seats.length;
    }

    @Override
    protected void addPassenger(Entity e) {
        super.addPassenger(e);
        if (!seatOf.containsKey(e.getUUID())) {
            for (int i = 0; i < seats.length; i++) {
                if (getFirstPassengerAtSeat(i) == null) {
                    seatOf.put(e.getUUID(), i);
                    break;
                }
            }
        }
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        seatOf.remove(e.getUUID());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        int idx = seatOf.getOrDefault(passenger.getUUID(), 0);
        Vec3 s = seatLocal(idx);
        Vec3 w = toWorld(s.x, s.y, s.z);
        move.accept(passenger, w.x, w.y, w.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int idx = seatOf.getOrDefault(passenger.getUUID(), 0);
        Vec3 s = seatLocal(idx);
        return toWorld(s.x * 0.5, deckAt((float) s.z) + 0.05, s.z + 1.2);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(cls.length, 14, cls.length);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.hasUUID("ShipId")) shipId = t.getUUID("ShipId");
        owner = t.hasUUID("Owner") ? t.getUUID("Owner") : null;
        ownerName = t.getString("OwnerName");
        interiorIndex = t.contains("Interior") ? t.getInt("Interior") : -1;
        entityData.set(NAME, t.getString("Name"));
        entityData.set(HULL, t.contains("Hull") ? t.getFloat("Hull") : cls.hull);
        entityData.set(SAIL, t.getByte("Sail"));
        entityData.set(FLAGS, t.getByte("Flags"));
        entityData.set(SAIL_COLOR, t.contains("SailColor") ? t.getInt("SailColor") : 0xF4EEDC);
        entityData.set(HULL_COLOR, t.contains("HullColor") ? t.getInt("HullColor") : 0x8A5A32);
        entityData.set(UPGRADES, t.getString("Upgrades"));
        speed = t.getFloat("Speed");
        npcFaction = t.getString("NpcFaction");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putUUID("ShipId", shipId);
        if (owner != null) t.putUUID("Owner", owner);
        t.putString("OwnerName", ownerName);
        t.putInt("Interior", interiorIndex);
        t.putString("Name", shipName());
        t.putFloat("Hull", hull());
        t.putByte("Sail", (byte) sail());
        t.putByte("Flags", entityData.get(FLAGS));
        t.putInt("SailColor", sailColor());
        t.putInt("HullColor", hullColor());
        t.putString("Upgrades", entityData.get(UPGRADES));
        t.putFloat("Speed", speed);
        t.putString("NpcFaction", npcFaction);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        updateParts();
    }
}
