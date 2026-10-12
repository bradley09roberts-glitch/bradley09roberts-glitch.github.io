package com.grandline.fruit.impl;

import com.grandline.combat.AbilityContext;
import com.grandline.combat.Hits;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.TerrainChanges;
import com.grandline.fruit.Zoan;
import com.grandline.npc.GLNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers shared by the second wave of fruit implementations: target picking, pulls and whirls, ground painting
 * (always through {@link TerrainChanges}, so it reverts and never touches towns), encasing, beams and Zoan checks.
 */
public final class PowerKit {

    // ------------------------------------------------------------------ targets

    /** Lock-on target within range, else the nearest hostile in front of the user (cone, then aim line). */
    @Nullable
    public static LivingEntity target(AbilityContext c, double range) {
        if (c.target != null && c.target.isAlive() && c.target.distanceTo(c.user) <= range) return c.target;
        for (LivingEntity e : Hits.cone(c.user, range, 50)) if (Hits.hostileTo(c.user, e)) return e;
        for (LivingEntity e : Hits.line(c.user, c.eye(), c.look(), range, 1.0)) if (Hits.hostileTo(c.user, e)) return e;
        return null;
    }

    /** Hostile living entities within a sphere. */
    public static List<LivingEntity> foes(LivingEntity u, Vec3 center, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Hits.sphere(u, center, r)) if (Hits.hostileTo(u, e)) out.add(e);
        return out;
    }

    /** Hit and return whether it landed. */
    public static boolean hit(LivingEntity u, LivingEntity e, float dmg, Hits.Info info) {
        return Hits.hit(u, e, dmg, info) == Hits.Result.HIT;
    }

    /** Horizontal unit vector of the aim (falls back to the look vector). */
    public static Vec3 flatAim(AbilityContext c) {
        Vec3 d = FruitKit.aimDir(c).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) d = c.look().multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) d = new Vec3(0, 0, 1);
        return d.normalize();
    }

    /** Make mobs that were hunting the user lose track of them (blinding flashes, smoke screens). */
    public static void loseTrack(LivingEntity u, LivingEntity e) {
        if (e instanceof Mob m && m.getTarget() == u) m.setTarget(null);
    }

    // ------------------------------------------------------------------ movement

    /** Pull an entity towards a point (strength in blocks/tick, capped so it never overshoots). */
    public static void pull(LivingEntity e, Vec3 to, double strength, double lift) {
        Vec3 d = to.subtract(e.position());
        double len = d.length();
        if (len < 0.4) {
            if (lift != 0) FruitKit.impulse(e, new Vec3(0, lift, 0), false);
            return;
        }
        FruitKit.impulse(e, d.scale(Math.min(strength, len * 0.5) / len).add(0, lift, 0), false);
    }

    /** Tangential + inward impulse around a centre (whirlpools, sandstorms, vortices). */
    public static void swirl(LivingEntity e, Vec3 center, double spin, double inward, double lift) {
        Vec3 d = e.position().subtract(center);
        Vec3 flat = new Vec3(d.x, 0, d.z);
        double len = flat.length();
        if (len < 0.25) {
            FruitKit.impulse(e, new Vec3(0, lift, 0), false);
            return;
        }
        Vec3 n = flat.scale(1 / len);
        Vec3 tangent = new Vec3(-n.z, 0, n.x);
        FruitKit.impulse(e, tangent.scale(spin).subtract(n.scale(Math.min(inward, len * 0.4))).add(0, lift, 0), false);
    }

    /** Push an entity away from a point. */
    public static void push(LivingEntity e, Vec3 from, double strength, double lift) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) d = new Vec3(e.getRandom().nextDouble() - 0.5, 0, e.getRandom().nextDouble() - 0.5);
        FruitKit.impulse(e, d.normalize().scale(strength).add(0, lift, 0), false);
    }

    /** Teleport to a point in front of/behind someone, keeping feet on a free spot. */
    public static void place(LivingEntity e, Vec3 p) {
        e.teleportTo(p.x, p.y, p.z);
        e.fallDistance = 0;
    }

    // ------------------------------------------------------------------ rays

    /** Distance along a ray until the first solid block (or the full range). */
    public static double rayLength(LivingEntity u, Vec3 from, Vec3 dir, double range) {
        Vec3 to = from.add(dir.normalize().scale(range));
        BlockHitResult r = u.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, u));
        return r.getType() == HitResult.Type.MISS ? range : r.getLocation().distanceTo(from);
    }

    /** Instant beam: hits every hostile along the ray up to the first wall. Returns the end point. */
    public static Vec3 beam(AbilityContext c, Vec3 from, Vec3 dir, double range, double width, float dmg, Hits.Info info) {
        dir = dir.normalize();
        double len = rayLength(c.user, from, dir, range);
        for (LivingEntity e : Hits.line(c.user, from, dir, len, width)) Hits.hit(c.user, e, dmg, info);
        return from.add(dir.scale(len));
    }

    /** Rotate a horizontal-ish direction around the Y axis. */
    public static Vec3 yaw(Vec3 dir, double degrees) {
        double r = Math.toRadians(degrees), cos = Math.cos(r), sin = Math.sin(r);
        return new Vec3(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos);
    }

    // ------------------------------------------------------------------ terrain (always temporary)

    /** Top block of the column (solid ground or a liquid surface) searching downward from a little above {@code y}. */
    @Nullable
    public static BlockPos ground(ServerLevel l, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y + 3, z);
        for (int i = 0; i < 10; i++, p.move(Direction.DOWN)) {
            BlockState s = l.getBlockState(p);
            if (!s.getFluidState().isEmpty() || (!s.isAir() && !s.canBeReplaced())) return p.immutable();
        }
        return null;
    }

    /** Replace the top block of a column: solid ground becomes {@code land}, a water surface becomes {@code sea} (null = leave). */
    public static boolean paintColumn(ServerLevel l, int x, int y, int z, @Nullable BlockState land, @Nullable BlockState sea) {
        BlockPos g = ground(l, x, y, z);
        if (g == null) return false;
        BlockState s = l.getBlockState(g);
        boolean water = s.is(Blocks.WATER);
        if (!s.getFluidState().isEmpty() && !water) return false;
        BlockState to = water ? sea : land;
        if (to == null) return false;
        if (to.is(Blocks.SAND) && !supported(l, g)) to = Blocks.SANDSTONE.defaultBlockState(); // never leave falling sand behind
        // plants on top could not survive the new block and would pop off for good: clear them temporarily instead
        BlockPos up = g.above();
        BlockState plant = l.getBlockState(up);
        if (!plant.isAir() && plant.canBeReplaced() && plant.getFluidState().isEmpty()) {
            if (plant.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) return false;
            TerrainChanges.place(l, up, Blocks.AIR.defaultBlockState());
        }
        return TerrainChanges.place(l, g, to);
    }

    /** Paint a disc of ground (and optionally sea surface). Returns the number of blocks changed. */
    public static int paintDisc(ServerLevel l, Vec3 center, double radius, @Nullable BlockState land, @Nullable BlockState sea) {
        int n = 0, r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(center);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                if (paintColumn(l, c.getX() + dx, c.getY(), c.getZ() + dz, land, sea)) n++;
            }
        return n;
    }

    /** Temporarily dig out one natural block (no liquids, nothing harder than stone-ish terrain). */
    public static boolean dig(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        float hardness = s.getDestroySpeed(l, p);
        if (hardness < 0 || hardness > 4) return false;
        return TerrainChanges.place(l, p, Blocks.AIR.defaultBlockState());
    }

    /** Whether a block has something sturdy below it (so a gravity block placed there would not fall). */
    public static boolean supported(ServerLevel l, BlockPos p) {
        BlockPos b = p.below();
        return l.getBlockState(b).isFaceSturdy(l, b, Direction.UP);
    }

    /** Place a temporary block only into air/plants/water and never inside a living entity. */
    public static boolean placeFree(ServerLevel l, BlockPos p, BlockState state) {
        BlockState cur = l.getBlockState(p);
        if (!cur.canBeReplaced()) return false;
        if (!l.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty()) return false;
        return TerrainChanges.place(l, p, state);
    }

    /** Hollow shell of temporary blocks around an entity (barrier prison, salt statue). */
    public static int encase(ServerLevel l, LivingEntity e, BlockState state) {
        BlockPos b = e.blockPosition();
        int h = Math.max(2, (int) Math.ceil(e.getBbHeight()));
        int w = Math.max(1, (int) Math.ceil(e.getBbWidth() / 2));
        int n = 0;
        for (int dx = -w; dx <= w; dx++)
            for (int dz = -w; dz <= w; dz++)
                for (int dy = -1; dy <= h; dy++) {
                    boolean inner = Math.abs(dx) < w && Math.abs(dz) < w && dy >= 0 && dy < h;
                    if (inner) continue;
                    BlockPos p = b.offset(dx, dy, dz);
                    BlockState cur = l.getBlockState(p);
                    if (cur.canBeReplaced() && TerrainChanges.place(l, p, state)) n++;
                }
        return n;
    }

    // ------------------------------------------------------------------ projectiles

    /** Projectiles (any kind) near a point that were not fired by the user or an ally. */
    public static List<Projectile> hostileProjectiles(LivingEntity u, Vec3 center, double r) {
        List<Projectile> out = new ArrayList<>();
        for (Projectile p : u.level().getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(r), p -> p.isAlive() && p.getOwner() != u)) {
            Entity o = p.getOwner();
            if (o instanceof LivingEntity lo && lo != u && !Hits.hostileTo(u, lo)) continue;
            out.add(p);
        }
        return out;
    }

    /** Send a projectile back at whoever fired it; the reflector becomes its owner. */
    public static void reflect(LivingEntity u, Projectile p) {
        Vec3 v = p.getDeltaMovement();
        double speed = Math.max(0.8, v.length());
        Entity o = p.getOwner();
        Vec3 dir = o != null && o.isAlive() ? o.getBoundingBox().getCenter().subtract(p.position()).normalize() : v.normalize().scale(-1);
        p.setOwner(u);
        p.setDeltaMovement(dir.scale(speed));
        p.hurtMarked = true;
        FruitKit.fx(u, "mirror_reflect", p.position(), 0, 0, 0);
    }

    // ------------------------------------------------------------------ misc

    public static void sound(AbilityContext c, Vec3 at, SoundEvent s, float vol, float pitch) {
        c.level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Zoan abilities need a transformed body; NPC Zoans transform on their own. */
    public static boolean inForm(LivingEntity u) {
        if (!Zoan.form(u).isEmpty()) return true;
        if (u instanceof GLNpc) {
            Zoan.setForm(u, "hybrid");
            return true;
        }
        if (u instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.fruit.zoan_required").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    /** Standard Zoan transform ability (hybrid -> full -> awakened -> human). */
    public static void zoanTransform(DevilFruit f) {
        f.add(f.ability("transform").cooldown(20).stamina(0).anim("transform").exec((u, c) -> {
            if (u instanceof ServerPlayer sp) Zoan.cycle(sp);
            else Zoan.setForm(u, Zoan.form(u).isEmpty() ? "hybrid" : "");
            return true;
        }));
    }

    private PowerKit() {}
}
