package com.grandline.ship;

import com.grandline.world.DeepWorld;
import com.grandline.world.Island;
import com.grandline.world.SkyWorld;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The canon ways between the seas:
 * <ul>
 * <li><b>Knock Up Stream</b> off Jaya: erupts every 10 minutes for 30 s; ships inside are thrown 10,000 m up to the
 * White Sea (sky dimension), arriving by Heaven's Gate.</li>
 * <li><b>Cloud End</b>: sailing off the east edge of the White Sea drops the ship back into the Blue Sea east of Jaya.</li>
 * <li><b>Coated dive</b>: a ship with a Sabaody coating that sails onto the dive point east of Sabaody sinks into the
 * deep sea (deep-sea dimension); Fish-Man Island lies on the way.</li>
 * <li><b>Ascent</b>: sailing past the deep sea's east end surfaces at the New World Gate; the coating is spent.</li>
 * </ul>
 * Rides are scripted ship motions (like the Reverse Mountain canals) that end in {@link ShipTravel#transfer}.
 */
public final class SpecialRoutes {
    public static final int KNOCK_UP = 0, CLOUD_FALL = 1, DIVE = 2, ASCEND = 3;
    public static final long CYCLE = 12000, ERUPTION = 600, WARNING = 1200;
    public static final String COATING = "coating";

    // ------------------------------------------------------------------ fixed points in the ocean world

    @Nullable
    public static BlockPos knockUpPoint(MinecraftServer server) {
        WorldPlan plan = WorldPlan.of(server);
        Island jaya = plan == null ? null : plan.byId("jaya");
        if (jaya == null) return null;
        for (Island.LandmarkPlacement lp : jaya.landmarks)
            if (lp.id().equals("knock_up_marker")) return new BlockPos(jaya.x + lp.dx(), WorldPlan.SEA_LEVEL, jaya.z + lp.dz());
        return new BlockPos(jaya.x + 520, WorldPlan.SEA_LEVEL, jaya.z + 300);
    }

    /** Open water between Sabaody and the Red Line where coated ships dive. */
    @Nullable
    public static BlockPos divePoint(MinecraftServer server) {
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return null;
        WorldPlan.Sample s = new WorldPlan.Sample();
        for (int dx = 0; dx <= 600; dx += 40) {
            int x = 59050 - dx;
            plan.sample(x, 0, s);
            if (s.height < WorldPlan.SEA_LEVEL - 6 && !s.redLine) return new BlockPos(x, WorldPlan.SEA_LEVEL, 0);
        }
        return new BlockPos(59050, WorldPlan.SEA_LEVEL, 0);
    }

    /** Where ships surface in the New World (east of the New World Gate, heading east). */
    public static Vec3 newWorldSurface(MinecraftServer server) {
        WorldPlan plan = WorldPlan.of(server);
        Island gate = plan == null ? null : plan.byId("new_world_gate");
        int x = gate != null ? gate.x + gate.influenceRadius() + 60 : -57700;
        return new Vec3(x + 0.5, WorldPlan.SEA_LEVEL + 0.9, 0.5);
    }

    public static boolean erupting(long gameTime) {
        return gameTime % CYCLE < ERUPTION;
    }

    // ------------------------------------------------------------------ per-tick

    public static void tick(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        long t = ow.getGameTime();
        BlockPos ku = knockUpPoint(server);
        if (ku != null) tickKnockUp(ow, ku, t);
        ServerLevel sky = server.getLevel(SkyWorld.LEVEL);
        if (sky != null && t % 10 == 0) {
            // anyone who falls through the sea of clouds drops back to the Blue Sea
            for (ServerPlayer sp : sky.players()) {
                if (sp.getY() < -20 && ku != null) {
                    ShipTravel.sendPlayer(sp, ow, ku.getX() + sp.getX() * 0.05, 330, ku.getZ() + sp.getZ() * 0.05);
                    sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 40, 0));
                    sp.sendSystemMessage(Component.translatable("grandline.sky.fell").withStyle(ChatFormatting.AQUA));
                }
            }
        }
    }

    private static void tickKnockUp(ServerLevel ow, BlockPos ku, long t) {
        long phase = t % CYCLE;
        if (phase >= CYCLE - WARNING && t % 100 == 0) {
            int secs = (int) ((CYCLE - phase) / 20);
            for (ServerPlayer sp : ow.players())
                if (sp.blockPosition().distSqr(ku) < 500 * 500)
                    sp.displayClientMessage(Component.translatable("grandline.knock_up.warning", secs).withStyle(ChatFormatting.AQUA), true);
        }
        if (!erupting(t)) return;
        if (!ow.isLoaded(ku)) return;
        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double y = ku.getY() + ow.random.nextDouble() * 120;
                ow.sendParticles(ParticleTypes.CLOUD, ku.getX() + ow.random.nextGaussian() * 8, y, ku.getZ() + ow.random.nextGaussian() * 8, 4, 2, 2, 2, 0.05);
                ow.sendParticles(ParticleTypes.SPLASH, ku.getX() + ow.random.nextGaussian() * 10, ku.getY() + 1 + ow.random.nextDouble() * 20, ku.getZ() + ow.random.nextGaussian() * 10, 10, 2, 2, 2, 0.3);
            }
        }
        if (t % 40 == 0) ow.playSound(null, ku, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 4f, 0.5f);
        AABB column = new AABB(ku).inflate(40, 0, 40).expandTowards(0, 300, 0);
        for (ShipEntity ship : ow.getEntitiesOfClass(ShipEntity.class, column, s -> s.ride < 0 && s.canalLeg < 0 && !s.isSinking()))
            startRide(ship, KNOCK_UP);
        // swimmers are thrown up too (and reach the sky if they are lucky)
        for (ServerPlayer sp : ow.players()) {
            if (sp.isPassenger() || sp.blockPosition().distSqr(ku) > 32 * 32 && !(sp.getY() > 200 && Math.hypot(sp.getX() - ku.getX(), sp.getZ() - ku.getZ()) < 60)) continue;
            if (sp.getY() > 320) {
                ServerLevel sky = ow.getServer().getLevel(SkyWorld.LEVEL);
                if (sky != null) {
                    ShipTravel.sendPlayer(sp, sky, SkyWorld.ARRIVAL_X + ow.random.nextInt(20), 110, SkyWorld.ARRIVAL_Z + ow.random.nextInt(20) - 10);
                    sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 10, 0));
                    sp.sendSystemMessage(Component.translatable("grandline.sky.arrived").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }
            } else if (sp.isInWater() || sp.getY() > ku.getY()) {
                sp.setDeltaMovement(sp.getDeltaMovement().x * 0.5, 2.4, sp.getDeltaMovement().z * 0.5);
                sp.hurtMarked = true;
                sp.fallDistance = 0;
            }
        }
    }

    // ------------------------------------------------------------------ ship hooks

    public static void startRide(ShipEntity ship, int ride) {
        ship.ride = ride;
        ship.rideT = 0;
        String key = switch (ride) {
            case KNOCK_UP -> "grandline.knock_up.launch";
            case CLOUD_FALL -> "grandline.sky.cloud_end";
            case DIVE -> "grandline.deep.dive";
            default -> "grandline.deep.ascend";
        };
        tell(ship, Component.translatable(key).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    /** Called after each normal movement step: start a ride when the ship reaches a route's trigger. */
    public static void afterMove(ShipEntity ship, ServerLevel sl) {
        if (ship.ride >= 0 || sl.getGameTime() % 5 != 0) return;
        if (sl.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            BlockPos dive = divePoint(sl.getServer());
            if (dive != null && Math.hypot(ship.getX() - dive.getX(), ship.getZ() - dive.getZ()) < 50) {
                if (ship.hasUpgrade(COATING)) startRide(ship, DIVE);
                else if (sl.getGameTime() % 200 == 0) tell(ship, Component.translatable("grandline.deep.need_coating").withStyle(ChatFormatting.YELLOW));
            }
        } else if (ShipTravel.isSky(sl)) {
            if (ship.getX() > SkyWorld.CLOUD_END_X || Math.abs(ship.getX()) > SkyWorld.BOUNDS || Math.abs(ship.getZ()) > SkyWorld.BOUNDS) startRide(ship, CLOUD_FALL);
        } else if (ShipTravel.isDeep(sl)) {
            if (ship.getX() > DeepWorld.EXIT_X || Math.abs(ship.getX()) > DeepWorld.BOUNDS || Math.abs(ship.getZ()) > DeepWorld.BOUNDS) startRide(ship, ASCEND);
        }
    }

    /** Scripted motion while a ride is active; ends with the transfer. */
    public static void tickRide(ShipEntity ship, ServerLevel sl) {
        MinecraftServer server = sl.getServer();
        double base = WorldPlan.SEA_LEVEL + 0.9;
        Vec3 fwd = ship.heading();
        switch (ship.ride) {
            case KNOCK_UP -> {
                ship.rideT += 1.0 / 260;
                double k = ship.rideT * ship.rideT;
                ship.setPos(ship.getX() + fwd.x * 0.05, base + 270 * k, ship.getZ() + fwd.z * 0.05);
                if (sl.getGameTime() % 2 == 0)
                    sl.sendParticles(ParticleTypes.CLOUD, ship.getX(), ship.getY() - 3, ship.getZ(), 20, ship.cls.beam, 2, ship.cls.length / 2.0, 0.08);
                if (ship.rideT >= 1) {
                    ServerLevel sky = server.getLevel(SkyWorld.LEVEL);
                    if (sky == null) { ship.ride = -1; return; }
                    ShipEntity n = ShipTravel.transfer(ship, sky, SkyWorld.ARRIVAL_X, SkyWorld.ARRIVAL_Z, -90);
                    if (n != null) tell(n, Component.translatable("grandline.sky.arrived").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }
            }
            case CLOUD_FALL -> {
                ship.rideT += 1.0 / 140;
                ship.setPos(ship.getX() + fwd.x * 0.4, base - 70 * ship.rideT * ship.rideT, ship.getZ() + fwd.z * 0.4);
                if (sl.getGameTime() % 3 == 0) sl.sendParticles(ParticleTypes.CLOUD, ship.getX(), ship.getY() + 2, ship.getZ(), 16, ship.cls.beam, 1, ship.cls.length / 2.0, 0.02);
                if (ship.rideT >= 1) {
                    BlockPos ku = knockUpPoint(server);
                    double x = ku != null ? ku.getX() + 1400 : 27400, z = ku != null ? ku.getZ() : 2900;
                    ShipEntity n = ShipTravel.transfer(ship, server.overworld(), x, z, -90);
                    if (n != null) {
                        server.overworld().sendParticles(ParticleTypes.SPLASH, n.getX(), n.getY() + 1, n.getZ(), 200, n.cls.beam, 1, n.cls.length / 2.0, 0.5);
                        tell(n, Component.translatable("grandline.sky.landed").withStyle(ChatFormatting.AQUA));
                    }
                }
            }
            case DIVE -> {
                ship.rideT += 1.0 / 200;
                ship.setPos(ship.getX() + fwd.x * 0.15, base - 45 * ship.rideT, ship.getZ() + fwd.z * 0.15);
                if (sl.getGameTime() % 2 == 0) sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, ship.getX(), ship.getY() + 2, ship.getZ(), 24, ship.cls.beam, 2, ship.cls.length / 2.0, 0.1);
                bubble(ship, sl);
                if (ship.rideT >= 1) {
                    ServerLevel deep = server.getLevel(DeepWorld.LEVEL);
                    if (deep == null) { ship.ride = -1; return; }
                    ShipEntity n = ShipTravel.transfer(ship, deep, DeepWorld.ENTRY_X, DeepWorld.ENTRY_Z, -90);
                    if (n != null) tell(n, Component.translatable("grandline.deep.arrived").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
                }
            }
            case ASCEND -> {
                ship.rideT += 1.0 / 160;
                ship.setPos(ship.getX() + fwd.x * 0.2, base + 50 * ship.rideT, ship.getZ() + fwd.z * 0.2);
                bubble(ship, sl);
                if (ship.rideT >= 1) {
                    Vec3 out = newWorldSurface(server);
                    ShipEntity n = ShipTravel.transfer(ship, server.overworld(), out.x, out.z, -90);
                    if (n != null) {
                        n.removeUpgrade(COATING);
                        server.overworld().sendParticles(ParticleTypes.BUBBLE_POP, n.getX(), n.getY() + 2, n.getZ(), 300, n.cls.beam, 3, n.cls.length / 2.0, 0.2);
                        tell(n, Component.translatable("grandline.deep.new_world").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    }
                }
            }
            default -> ship.ride = -1;
        }
    }

    /** Coating bubble in the deep sea: everyone aboard breathes and moves as if in air; fruit users are not weakened. */
    public static void bubble(ShipEntity ship, ServerLevel sl) {
        if (sl.getGameTime() % 10 != 0) return;
        double r = ship.cls.length / 2.0 + 5;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, ship.getBoundingBox().inflate(r, 8, r), e -> e.distanceToSqr(ship) < r * r)) {
            e.getPersistentData().putLong(BUBBLE_TAG, sl.getGameTime());
            e.setAirSupply(e.getMaxAirSupply());
            e.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            e.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, true, false));
            e.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 40, 0, true, false));
        }
        if (sl.getGameTime() % 40 == 0) sl.sendParticles(ParticleTypes.BUBBLE, ship.getX(), ship.getY() + 3, ship.getZ(), 30, r * 0.6, 4, r * 0.6, 0.02);
    }

    public static final String BUBBLE_TAG = "grandline_ship_bubble";

    /** True while the entity is inside a coated ship's bubble (refreshed every 10 ticks). */
    public static boolean inShipBubble(Entity e) {
        long last = e.getPersistentData().getLong(BUBBLE_TAG);
        return last != 0 && e.level().getGameTime() - last < 25;
    }

    private static void tell(ShipEntity ship, Component msg) {
        double r = ship.cls.length / 2.0 + 4;
        for (ServerPlayer sp : ((ServerLevel) ship.level()).getEntitiesOfClass(ServerPlayer.class, ship.getBoundingBox().inflate(r, 10, r)))
            sp.sendSystemMessage(msg);
    }

    private SpecialRoutes() {}
}
