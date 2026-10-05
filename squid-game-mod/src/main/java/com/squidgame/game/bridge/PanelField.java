package com.squidgame.game.bridge;

import com.squidgame.SquidGameMod;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.core.bridge.BridgeLayout;
import com.squidgame.core.bridge.BridgeRoute;
import com.squidgame.game.GameContext;
import com.squidgame.registry.ModBlocks;
import com.squidgame.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The glass panels in the world: where they are (from the arena's {@code bridge.panel} markers), what state each is
 * in, and everything that changes blocks or plays an effect (crack overlay, shatter, rebuild). It knows nothing about
 * which panel is fragile: the game tells it when a panel starts cracking.
 */
final class PanelField {
    enum State {
        /** The glass is there. */
        INTACT,
        /** Cracking: shatters at {@link Cell#stateUntil}. */
        CRACKING,
        /** Shattered for good (a fragile panel). */
        BROKEN,
        /** Shattered by the stall rule; re-forms at {@link Cell#stateUntil}. */
        REBUILDING
    }

    /** One panel (2x2 blocks). */
    static final class Cell {
        final int index;
        final int row;
        final int lane;
        final BridgeLayout.Panel geo;
        final BlockPos[] blocks = new BlockPos[4];
        State state = State.INTACT;
        long stateUntil;
        long crackStart;
        /** Cracking because somebody stalled on it (the panel was tempered and is rebuilt) rather than because it was fragile. */
        boolean stallBreak;
        /** Contestant number currently above the panel, 0 = nobody. */
        int occupant;
        /** Contestant number about to hop onto the panel, 0 = nobody. */
        int reservedBy;
        int lastStage = -1;

        Cell(int index, BridgeLayout.Panel geo, int blockY) {
            this.index = index;
            this.row = geo.row();
            this.lane = geo.lane();
            this.geo = geo;
            int x0 = (int) geo.rect().minX();
            int z0 = (int) geo.rect().minZ();
            for (int i = 0; i < 4; i++) {
                blocks[i] = new BlockPos(x0 + (i & 1), blockY, z0 + (i >> 1));
            }
        }

        boolean hasGlass() {
            return state == State.INTACT || state == State.CRACKING;
        }

        double centerX() {
            return geo.rect().centerX();
        }

        double centerZ() {
            return geo.rect().centerZ();
        }
    }

    private static final int BREAKER_BASE = 0x5B100000;

    final ServerLevel level;
    final BridgeLayout layout;
    final Cell[][] cells;
    final double topY;
    private final List<Cell> all = new ArrayList<>();

    private PanelField(ServerLevel level, BridgeLayout layout) {
        this.level = level;
        this.layout = layout;
        this.topY = layout.deckTop();
        this.cells = new Cell[layout.rows()][BridgeRoute.LANES];
        int blockY = (int) Math.floor(topY) - 1;
        int idx = 0;
        for (int r = 0; r < layout.rows(); r++) {
            for (int l = 0; l < BridgeRoute.LANES; l++) {
                Cell c = new Cell(idx++, layout.panel(r, l), blockY);
                cells[r][l] = c;
                all.add(c);
            }
        }
    }

    /** Reads the panels and platforms from the arena markers; null (with a log line) when the arena has no usable bridge. */
    @Nullable
    static PanelField load(GameContext ctx) {
        List<BridgeLayout.PanelSpec> specs = new ArrayList<>();
        for (Marker m : ctx.markers("bridge.panel")) {
            int row = m.getInt("row", -1);
            int lane = m.getInt("lane", -1);
            if (row >= 0 && lane >= 0 && lane < BridgeRoute.LANES) {
                specs.add(new BridgeLayout.PanelSpec(row, lane, m.x(), m.z(), m.y()));
            }
        }
        Region start = ctx.region("bridge.start");
        Region finish = ctx.region("bridge.finish");
        if (specs.isEmpty() || start == null || finish == null) {
            SquidGameMod.LOGGER.error("Glass bridge: the arena has no bridge.panel markers / bridge.start / bridge.finish regions");
            return null;
        }
        try {
            BridgeLayout layout = BridgeLayout.of(specs, rect(start), rect(finish));
            return new PanelField(ctx.level, layout);
        } catch (IllegalArgumentException e) {
            SquidGameMod.LOGGER.error("Glass bridge: invalid panel markers: {}", e.getMessage());
            return null;
        }
    }

    private static BridgeLayout.Rect rect(Region r) {
        return new BridgeLayout.Rect(r.minX(), r.maxX() + 1.0, r.minZ(), r.maxZ() + 1.0);
    }

    int rows() {
        return layout.rows();
    }

    Cell cell(int row, int lane) {
        return cells[row][lane];
    }

    List<Cell> all() {
        return all;
    }

    // ------------------------------------------------------------------ lookup

    /** The panel a body is standing on (on the ground, on the glass surface), or null. */
    @Nullable
    Cell supportOf(LivingEntity body) {
        if (!body.onGround()) {
            return null;
        }
        double dy = body.getY() - topY;
        if (dy < -0.05 || dy > 0.25) {
            return null;
        }
        BridgeLayout.Panel p = layout.supporting(body.getX(), body.getZ());
        if (p == null) {
            return null;
        }
        Cell c = cells[p.row()][p.lane()];
        return c.hasGlass() ? c : null;
    }

    /** The panel a body is above (standing, hopping or about to land), or null. Used for occupancy. */
    @Nullable
    Cell cellOver(Vec3 pos) {
        if (pos.y < topY - 0.6 || pos.y > topY + 2.2) {
            return null;
        }
        BridgeLayout.Panel p = layout.supporting(pos.x, pos.z);
        return p == null ? null : cells[p.row()][p.lane()];
    }

    /** True when somebody stands above either panel of {@code row}, ignoring {@code except}. */
    boolean rowOccupied(int row, int except) {
        if (row < 0 || row >= rows()) {
            return false;
        }
        for (int l = 0; l < BridgeRoute.LANES; l++) {
            int o = cells[row][l].occupant;
            if (o != 0 && o != except) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ blocks

    private BlockState glass() {
        return ModBlocks.BRIDGE_GLASS.defaultBlockState();
    }

    /** Puts every panel back (idempotent): used at the start of a game, on cleanup and after a restart. */
    void restoreAll() {
        for (Cell c : all) {
            restore(c);
        }
    }

    void restore(Cell c) {
        setCrackStage(c, -1);
        BlockState glass = glass();
        for (BlockPos p : c.blocks) {
            level.getChunk(p);
            if (!level.getBlockState(p).is(glass.getBlock())) {
                level.setBlock(p, glass, Block.UPDATE_CLIENTS);
            }
        }
        c.state = State.INTACT;
        c.stallBreak = false;
        c.stateUntil = 0;
        c.occupant = 0;
        c.reservedBy = 0;
        c.lastStage = -1;
    }

    /** Removes the glass of a panel with the full shatter effect. */
    void shatter(Cell c, boolean fragile) {
        setCrackStage(c, -1);
        BlockState glass = glass();
        for (BlockPos p : c.blocks) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        double x = c.centerX(), z = c.centerZ();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, glass), x, topY + 0.1, z, 70, 0.9, 0.15, 0.9, 0.35);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, glass), x, topY - 0.3, z, 30, 0.8, 0.1, 0.8, 0.1);
        level.sendParticles(ParticleTypes.CLOUD, x, topY, z, 6, 0.7, 0.1, 0.7, 0.02);
        level.playSound(null, x, topY, z, ModSounds.GLASS_SHATTER, SoundSource.BLOCKS, fragile ? 2.4f : 2.0f, fragile ? 1.0f : 0.9f);
        c.state = fragile ? State.BROKEN : State.REBUILDING;
    }

    /** Re-forms a panel that the stall rule broke. Returns false (and does nothing) while something is inside it. */
    boolean rebuild(Cell c) {
        AABB box = new AABB(c.geo.rect().minX(), topY - 1.0, c.geo.rect().minZ(), c.geo.rect().maxX(), topY + 1.9, c.geo.rect().maxZ());
        if (!level.getEntities((Entity) null, box, e -> e instanceof LivingEntity).isEmpty()) {
            return false;
        }
        restore(c);
        double x = c.centerX(), z = c.centerZ();
        level.sendParticles(ParticleTypes.END_ROD, x, topY + 0.4, z, 24, 0.8, 0.4, 0.8, 0.02);
        level.playSound(null, x, topY, z, ModSounds.DOOR_LOCK, SoundSource.BLOCKS, 1.2f, 1.5f);
        return true;
    }

    /** Vanilla block-breaking cracks (stage 0..9) on all four blocks; -1 clears them. */
    void setCrackStage(Cell c, int stage) {
        if (stage == c.lastStage) {
            return;
        }
        c.lastStage = stage;
        for (int i = 0; i < 4; i++) {
            level.destroyBlockProgress(BREAKER_BASE + c.index * 4 + i, c.blocks[i], stage < 0 ? -1 : Math.min(9, stage));
        }
    }

    /** Sound and sparks at the moment a fragile panel is touched. */
    void crackFx(Cell c, Vec3 at) {
        level.playSound(null, at.x, topY, at.z, ModSounds.GLASS_CRACK, SoundSource.BLOCKS, 1.8f, 0.9f + level.random.nextFloat() * 0.25f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, glass()), at.x, topY + 0.05, at.z, 20, 0.35, 0.05, 0.35, 0.05);
        level.sendParticles(ParticleTypes.CRIT, at.x, topY + 0.1, at.z, 8, 0.4, 0.05, 0.4, 0.12);
    }

    /** The soft chime and shimmer that confirm a tempered panel held. */
    void holdFx(Cell c, Vec3 at) {
        level.playSound(null, at.x, topY, at.z, ModSounds.UI_CONFIRM, SoundSource.BLOCKS, 0.7f, 1.7f);
        level.sendParticles(ParticleTypes.END_ROD, at.x, topY + 0.2, at.z, 5, 0.3, 0.1, 0.3, 0.01);
    }

    /** Removes crack overlays everywhere (cleanup). */
    void clearCracks() {
        for (Cell c : all) {
            setCrackStage(c, -1);
        }
    }
}
