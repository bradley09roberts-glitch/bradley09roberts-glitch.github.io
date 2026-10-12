package com.grandline.npc.notable;

import com.grandline.GrandLine;
import com.grandline.fruit.DevilFruits;
import com.grandline.fruit.FruitWorldData;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters.Notable;
import com.grandline.npc.notable.NotableState.Status;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Brings the story cast into the world. Each notable is spawned once per world, on its home island, the first time
 * a player comes within {@link #SPAWN_RADIUS} blocks of its home spot (and the chunk is loaded). Dead or arrested
 * notables never come back. A notable whose entity has gone missing near its home (lost to an unloaded chunk while
 * chasing someone) is re-created; the stale copy is rejected when its chunk loads ({@link NotableHooks}).
 * Also drives the crew system and the yield/recovery timers.
 */
public final class NotableSpawner {
    public static final int SPAWN_RADIUS = 160;
    private static final int STAND_SEARCH = 40;
    private static final int FALLEN_CREDIT_RADIUS = 64;

    public static void tick(MinecraftServer server) {
        CrewManager.tick(server);
        NotableHooks.tick(server);
        ServerLevel level = server.overworld();
        if (level.getGameTime() % 40 != 7) return;
        NotableState st = NotableState.get(server);
        Set<String> missingChecked = new HashSet<>();
        // the ocean world, the White Sea and the deep sea each have their own plan, and their own residents
        for (ServerLevel l : server.getAllLevels()) {
            if (l.players().isEmpty()) continue;
            WorldPlan plan = WorldPlan.forLevel(l);
            if (plan == null) continue;
            for (ServerPlayer sp : l.players())
                if (!sp.isSpectator()) scan(l, plan, st, sp.getBlockX(), sp.getBlockZ(), sp, missingChecked);
        }
    }

    /** Spawn, check on or credit the notables whose homes are near (px, pz) in this level. */
    private static void scan(ServerLevel level, WorldPlan plan, NotableState st, int px, int pz, @Nullable ServerPlayer sp, Set<String> missingChecked) {
        for (String islandId : NotableCharacters.islands()) {
            Island isl = plan.byId(islandId);
            if (isl == null || isl.distance(px, pz) > isl.radius + SPAWN_RADIUS + 64) continue;
            for (Notable n : NotableCharacters.onIsland(islandId)) {
                int hx = isl.x + n.dx(), hz = isl.z + n.dz();
                double d = Math.sqrt((double) (hx - px) * (hx - px) + (double) (hz - pz) * (hz - pz));
                NotableState.Entry e = st.entry(n.id());
                switch (e.status) {
                    case UNSPAWNED -> {
                        // wait (non-blocking) until the stand search area has finished loading instead of stalling the
                        // server thread on chunks that are still generating (self-test scans have no player and go now)
                        if (d <= SPAWN_RADIUS && (sp == null || !com.grandline.core.Chunks.pendingAround(level, hx, hz, STAND_SEARCH))) spawn(level, n, isl);
                    }
                    case ALIVE -> {
                        if (d <= 48 && missingChecked.add(n.id())) checkMissing(level, st, n, e, hx, hz);
                    }
                    case DEFEATED, ARRESTED -> {
                        if (sp != null && d <= FALLEN_CREDIT_RADIUS) NotableHooks.creditFallen(sp, n);
                    }
                    default -> {}
                }
            }
        }
    }

    /** Run the proximity rules as if a player stood at (x, z) of this level (self-test hook; works in any sea). */
    public static void scanAt(ServerLevel level, int x, int z) {
        WorldPlan plan = WorldPlan.forLevel(level);
        if (plan != null) scan(level, plan, NotableState.get(level.getServer()), x, z, null, new HashSet<>());
    }

    /** Spawn a notable at its home now if it has not been spawned yet (story/test hook). */
    @Nullable
    public static HumanoidNpc spawnNow(ServerLevel level, String id) {
        Notable n = NotableCharacters.get(id);
        WorldPlan plan = WorldPlan.forLevel(level); // the sea this level holds (ocean, sky or deep)
        if (n == null || plan == null) return null;
        Island isl = plan.byId(n.island());
        if (isl == null || NotableState.get(level.getServer()).status(id) != Status.UNSPAWNED) return null;
        return spawn(level, n, isl);
    }

    @Nullable
    static HumanoidNpc spawn(ServerLevel level, Notable n, Island isl) {
        BlockPos pos = findStand(level, isl.x + n.dx(), isl.z + n.dz(), STAND_SEARCH);
        if (pos == null) return null;
        HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(level);
        if (npc == null) return null;
        MinecraftServer server = level.getServer();
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        npc.configure(n.role(), n.tier(), isl.id, pos, "", level.random);
        // the archetype may have rolled a random fruit from the pool: give it back, notables carry their own
        String rolled = npc.fruitId();
        if (rolled != null) FruitWorldData.get(server).release(server, rolled, npc.getUUID());
        npc.makeNotable(n.id(), claimFruit(server, n, npc.getUUID()), n.obs(), n.arm(), n.coq(), n.style());
        npc.setNotableBody(n.tier(), n.race());
        npc.setAllegiance(n.faction(), pos);
        applyStats(npc, n);
        equip(npc, n);
        npc.setCustomName(Component.translatable(n.nameKey()));
        npc.setCustomNameVisible(true);
        NotableState.get(server).set(n.id(), Status.ALIVE, npc.getUUID(), "", level.getGameTime());
        level.addFreshEntity(npc);
        GrandLine.LOGGER.debug("Notable {} appeared on {} at {}", n.id(), isl.id, pos);
        return npc;
    }

    private static void checkMissing(ServerLevel level, NotableState st, Notable n, NotableState.Entry e, int hx, int hz) {
        BlockPos home = new BlockPos(hx, WorldPlan.SEA_LEVEL, hz);
        if (!level.isPositionEntityTicking(home)) return;
        if (e.entity != null && level.getEntity(e.entity) != null) {
            e.missing = 0;
            return;
        }
        if (++e.missing >= 8) {
            GrandLine.LOGGER.info("Notable {} went missing near its home; it will reappear", n.id());
            st.set(n.id(), Status.UNSPAWNED, null, "", level.getGameTime());
        }
    }

    // ------------------------------------------------------------------ setup helpers

    static void applyStats(HumanoidNpc npc, Notable n) {
        setBase(npc.getAttribute(Attributes.MAX_HEALTH), Math.min(1024, n.maxHealth()));
        setBase(npc.getAttribute(Attributes.ATTACK_DAMAGE), 2 + n.tier() * 1.4);
        setBase(npc.getAttribute(Attributes.ARMOR), Math.min(30, 2 + n.tier() * 1.6));
        if (Math.abs(n.scale() - 1f) > 1e-3) setBase(npc.getAttribute(Attributes.SCALE), n.scale());
        npc.setHealth(npc.getMaxHealth());
        npc.refreshDimensions();
    }

    private static void setBase(@Nullable AttributeInstance a, double v) {
        if (a != null) a.setBaseValue(v);
    }

    private static void equip(HumanoidNpc npc, Notable n) {
        if (n.weapon().isEmpty()) {
            npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(GrandLine.MODID, n.weapon()));
        if (item == Items.AIR) return;
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        npc.setDropChance(EquipmentSlot.MAINHAND, 0.25f);
    }

    static String ledgerName(Notable n) {
        return "Notable:" + n.id();
    }

    /** One of each fruit per world: a notable only gets its fruit if nobody else has found or eaten it first. */
    @Nullable
    static String claimFruit(MinecraftServer server, Notable n, UUID entity) {
        if (n.fruit() == null || DevilFruits.get(n.fruit()) == null) return null;
        FruitWorldData led = FruitWorldData.get(server);
        FruitWorldData.State s = led.state(n.fruit());
        if (s == FruitWorldData.State.POOL) {
            if (!led.claimSpecific(n.fruit())) return null;
            led.eaten(n.fruit(), entity, ledgerName(n));
            return n.fruit();
        }
        if (s == FruitWorldData.State.OWNED && ledgerName(n).equals(led.ownerName(n.fruit()))) {
            led.eaten(n.fruit(), entity, ledgerName(n)); // same character, new body (respawned/recalled)
            return n.fruit();
        }
        return null;
    }

    /** The notable is gone for good: its fruit returns to the world and will regrow elsewhere. */
    static void releaseFruit(MinecraftServer server, Notable n) {
        if (n.fruit() == null) return;
        FruitWorldData led = FruitWorldData.get(server);
        if (led.state(n.fruit()) == FruitWorldData.State.OWNED && ledgerName(n).equals(led.ownerName(n.fruit()))) led.release(server, n.fruit(), null);
    }

    /** Rebind a notable's fruit ownership to a new entity UUID (crew recall). */
    public static void rebindFruit(MinecraftServer server, String notableId, UUID entity) {
        Notable n = NotableCharacters.get(notableId);
        if (n != null) claimFruit(server, n, entity);
    }

    // ------------------------------------------------------------------ placement

    /** A spot where a humanoid can stand, searching outward from (x, z). Prefers ground floors over roofs. */
    @Nullable
    public static BlockPos findStand(ServerLevel level, int x, int z, int maxRadius) {
        for (int r = 0; r <= maxRadius; r += 4) {
            int steps = r == 0 ? 1 : Math.max(8, r * 2);
            for (int i = 0; i < steps; i++) {
                double a = Math.PI * 2 * i / steps;
                BlockPos p = standColumn(level, x + (int) Math.round(Math.cos(a) * r), z + (int) Math.round(Math.sin(a) * r));
                if (p != null) return p;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos standColumn(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= WorldPlan.SEA_LEVEL) return null; // open water
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = WorldPlan.SEA_LEVEL - 1; y <= top; y++) {
            m.set(x, y - 1, z);
            BlockState below = level.getBlockState(m);
            if (!below.getFluidState().isEmpty() || !below.isFaceSturdy(level, m, Direction.UP)) continue;
            if (!free(level, m.set(x, y, z)) || !free(level, m.set(x, y + 1, z))) continue;
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Safe spot within a few blocks of a position (crew stepping off the ship next to their captain). */
    @Nullable
    public static BlockPos safeSpotNear(ServerLevel level, BlockPos c) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 1; r <= 4; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -1; dy <= 2; dy++) {
                        int x = c.getX() + dx, y = c.getY() + dy, z = c.getZ() + dz;
                        m.set(x, y - 1, z);
                        BlockState below = level.getBlockState(m);
                        if (!below.getFluidState().isEmpty() || !below.isFaceSturdy(level, m, Direction.UP)) continue;
                        if (free(level, m.set(x, y, z)) && free(level, m.set(x, y + 1, z))) return new BlockPos(x, y, z);
                    }
                }
        return null;
    }

    /** Home block of a notable (island centre + offset, at sea level; used for distance checks). */
    @Nullable
    public static BlockPos homeOf(MinecraftServer server, Notable n) {
        WorldPlan plan = WorldPlan.of(server);
        Island isl = plan == null ? null : plan.byId(n.island());
        return isl == null ? null : new BlockPos(isl.x + n.dx(), WorldPlan.SEA_LEVEL, isl.z + n.dz());
    }

    private NotableSpawner() {}
}
