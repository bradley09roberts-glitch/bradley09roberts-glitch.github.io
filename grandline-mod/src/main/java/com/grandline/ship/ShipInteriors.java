package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.registry.ModBlocks;
import com.grandline.world.build.BlockSink;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Below-deck interiors live in the pocket dimension grandline:ship_interiors, one 256-block slot per ship.
 * Entering through the deck hatch keeps the ship's chunk loaded while anyone is below; the ladder exit returns
 * the player to the hatch wherever the ship has sailed.
 */
public final class ShipInteriors {
    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, GrandLine.id("ship_interiors"));
    public static final TicketType<ChunkPos> TICKET = TicketType.create("grandline_ship", Comparator.comparingLong(ChunkPos::toLong), 300);
    private static final int SPACING = 256, BASE_Y = 64;
    /** Players below deck → ship id they entered from. */
    private static final Map<UUID, UUID> OCCUPANTS = new HashMap<>();

    public static BlockPos origin(int index) {
        return new BlockPos(index * SPACING, BASE_Y, 0);
    }

    /** Ensure the ship has an interior slot and that its rooms are built. Returns the interior origin. */
    public static BlockPos prepare(net.minecraft.server.MinecraftServer server, ShipEntity ship) {
        ServerLevel dim = server.getLevel(DIM);
        if (dim == null) return null;
        ShipRegistry reg = ShipRegistry.get(server);
        if (ship.interiorIndex < 0) {
            if (reg.get(ship.shipId) == null) reg.register(ship);
            else ship.interiorIndex = reg.get(ship.shipId).interior;
        }
        BlockPos o = origin(ship.interiorIndex);
        dim.getChunk(o.getX() >> 4, o.getZ() >> 4);
        if (!dim.getBlockState(o.offset(1, -1, 1)).is(Blocks.SPRUCE_PLANKS) && !dim.getBlockState(o.offset(1, -1, 1)).is(Blocks.DARK_OAK_PLANKS)) build(dim, o, ship.cls);
        return o;
    }

    public static Vec3 deckExit(ShipEntity ship) {
        return ship.toWorld(0, ship.cls.freeboard + 0.1, ship.hatchZ() + 1.5);
    }

    public static void enter(ServerPlayer sp, ShipEntity ship) {
        ServerLevel dim = sp.server.getLevel(DIM);
        BlockPos o = prepare(sp.server, ship);
        if (dim == null || o == null) return;
        OCCUPANTS.put(sp.getUUID(), ship.shipId);
        keepLoaded(ship);
        sp.teleportTo(dim, o.getX() + 3.5, o.getY(), o.getZ() + 2.5, sp.getYRot(), sp.getXRot());
        sp.sendSystemMessage(Component.translatable("grandline.ship.below_deck", ship.shipName().isEmpty() ? Component.translatable("ship_class.grandline." + ship.cls.id()) : Component.literal(ship.shipName())));
    }

    public static void exit(ServerPlayer sp) {
        UUID shipId = OCCUPANTS.remove(sp.getUUID());
        ServerLevel ow = sp.server.overworld();
        ShipRegistry reg = ShipRegistry.get(sp.server);
        ShipRegistry.Record rec = null;
        if (shipId == null) {
            int idx = Math.floorDiv(sp.getBlockX() + SPACING / 2, SPACING);
            rec = reg.byInterior(idx);
            if (rec != null) shipId = rec.shipId;
        } else rec = reg.get(shipId);
        ShipEntity found = shipId == null ? null : ShipTravel.find(sp.server, shipId);
        if (found != null) {
            Vec3 hatch = deckExit(found);
            sp.teleportTo((ServerLevel) found.level(), hatch.x, hatch.y, hatch.z, sp.getYRot(), sp.getXRot());
            return;
        }
        if (rec != null) {
            sp.teleportTo(ShipTravel.level(sp.server, rec.dim), rec.x, rec.y + 3, rec.z, sp.getYRot(), sp.getXRot());
            if (rec.sunk) sp.sendSystemMessage(Component.translatable("grandline.ship.sunk_exit"));
            return;
        }
        var spawn = ow.getSharedSpawnPos();
        sp.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, sp.getYRot(), sp.getXRot());
    }

    /** Called every 5 s per ship with occupants below: keep the ship's chunk loaded so it stays simulated. */
    public static void keepLoaded(ShipEntity ship) {
        if (ship.level() instanceof ServerLevel sl) {
            ChunkPos cp = new ChunkPos(ship.blockPosition());
            sl.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
        }
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 100 != 0 || OCCUPANTS.isEmpty()) return;
        ServerLevel ow = server.overworld();
        OCCUPANTS.entrySet().removeIf(en -> {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            return p == null || p.level().dimension() != DIM;
        });
        for (UUID id : OCCUPANTS.values()) {
            ShipEntity ship = ShipTravel.find(server, id);
            if (ship != null) {
                keepLoaded(ship);
                ((ServerLevel) ship.level()).resetEmptyTime(); // keep the ship's sea ticking while its crew is below deck
            }
        }
    }

    /** Build the rooms for a ship class: hold, galley, crew quarters, captain's cabin, ladder up to the hatch. */
    static void build(ServerLevel l, BlockPos o, ShipClass cls) {
        BlockSink sink = new BlockSink() {
            final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            @Override
            public void set(int x, int y, int z, BlockState s) {
                l.setBlock(m.set(x, y, z), s, 2);
            }
            @Override
            public BlockState get(int x, int y, int z) {
                return l.getBlockState(m.set(x, y, z));
            }
        };
        build(sink, o, cls);
    }

    /**
     * The rooms written into any sink (the structure dump renders them offline): a curved hull under deck beams, the
     * bow with the ladder and hatches, crew bunks, a galley and mess table, the cargo hold, and the captain's cabin
     * behind a partition at the stern with stern windows. The floor cell (1,0,1) stays spruce planks: prepare() uses it
     * to tell that a slot is already built, so existing interiors (and what players stored in them) are never rebuilt.
     */
    public static void build(BlockSink sink, BlockPos o, ShipClass cls) {
        int len = Math.max(9, cls.length - 2), w = Math.max(5, cls.beam - 1);
        Builder b = new Builder(sink, o.getX(), o.getY() - 1, o.getZ(), w + 2, len + 2, Rotation.NONE, 7);
        BlockState hull = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        b.fill(0, 0, 0, w + 1, 5, len + 1, hull);
        b.clear(1, 1, 1, w, 4, len);
        b.fill(1, 0, 1, w, 0, len, Blocks.SPRUCE_PLANKS);
        // the hull curves in at the bilge: upside-down stairs along both sides
        for (int z = 1; z <= len; z++) {
            b.set(1, 1, z, Arch.stair(Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.WEST, Half.BOTTOM));
            b.set(w, 1, z, Arch.stair(Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.EAST, Half.BOTTOM));
        }
        // deck beams with knees, lanterns
        for (int z = 2; z < len; z += 3) {
            b.fill(1, 4, z, w, 4, z, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
            b.set(1, 3, z, Arch.stair(Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.WEST, Half.TOP));
            b.set(w, 3, z, Arch.stair(Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.EAST, Half.TOP));
            b.hanging(w / 2 + 1, 3, z, Blocks.LANTERN);
        }
        // bow: exit ladder and hatch blocks
        b.clear(2, 1, 1, 3, 3, 1);
        b.ladderUp(2, 1, 1, 3, Direction.SOUTH);
        b.set(2, 4, 1, ModBlocks.SHIP_HATCH.get());
        b.set(3, 1, 1, ModBlocks.SHIP_HATCH.get());
        b.set(w, 2, 1, Arch.decor(com.grandline.registry.ArchBlocks.ROPE_COIL, Direction.WEST));
        // zones along the keel
        int cabin = len - 2;                       // captain's cabin: the last three rows
        int holdEnd = cabin - 2, holdStart = Math.max(4, holdEnd - Math.max(1, len / 5));
        int galley = Math.max(3, holdStart - 2);
        // crew quarters: bunks along the port side
        for (int z = 3; z < galley - 1; z += 2) b.bed(1, 1, z, Blocks.WHITE_BED, Direction.EAST);
        // galley and mess
        b.set(w, 1, galley, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, Direction.WEST));
        b.set(w, 1, galley + 1, Blocks.CRAFTING_TABLE);
        b.set(w, 1, galley - 1, Blocks.BARREL);
        if (w >= 6) Arch.tableSet(b, w / 2 + 1, 1, galley, Blocks.SPRUCE_STAIRS.defaultBlockState());
        else b.set(w / 2 + 1, 1, galley, Arch.decor(com.grandline.registry.ArchBlocks.PLANK_TABLE, Direction.NORTH));
        // cargo hold: loot barrels and storage chests, crates, sacks
        for (int x = 1; x <= w; x += 2) {
            b.barrel(x, 1, holdEnd - 1, Buildings.LOOT_WAREHOUSE);
            b.chest(x, 1, holdEnd, Direction.NORTH, "minecraft:empty");
        }
        for (int z = holdStart; z < holdEnd - 1; z++) {
            b.set(1, 1, z, com.grandline.registry.ArchBlocks.CARGO_CRATE.get().defaultBlockState());
            b.set(w, 1, z, Arch.decor(com.grandline.registry.ArchBlocks.CARGO_SACKS, Direction.WEST));
        }
        // captain's cabin behind a partition with a door
        b.fill(1, 1, cabin, w, 3, cabin, Blocks.SPRUCE_PLANKS.defaultBlockState());
        b.door(w / 2 + 1, 1, cabin, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.set(w / 2 + 1, 1, cabin + 1, Blocks.RED_CARPET.defaultBlockState());
        b.set(1, 1, len, Blocks.CARTOGRAPHY_TABLE);
        b.set(2, 1, len, Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.NORTH));
        b.set(w, 1, len, Blocks.BOOKSHELF);
        b.bed(w, 1, cabin + 1, Blocks.RED_BED, Direction.SOUTH);
        b.hanging(w / 2 + 1, 3, len, com.grandline.registry.ArchBlocks.BRASS_LANTERN.get());
        for (int x = 2; x < w; x += 2) b.fill(x, 2, len + 1, x, 3, len + 1, Arch.along(Blocks.GLASS_PANE.defaultBlockState(), Direction.Axis.X)); // stern windows
        // portholes
        for (int z = 3; z < cabin; z += 4) {
            b.set(0, 2, z, Blocks.GLASS);
            b.set(w + 1, 2, z, Blocks.GLASS);
        }
    }

    private ShipInteriors() {}
}
