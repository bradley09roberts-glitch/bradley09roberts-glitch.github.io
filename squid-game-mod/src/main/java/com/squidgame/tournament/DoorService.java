package com.squidgame.tournament;

import com.squidgame.build.Marker;
import com.squidgame.registry.ModBlocks;
import com.squidgame.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Animated sliding doors. A door is described by a builder marker (centre-bottom of the doorway, yaw = direction of
 * travel, data {@code w=,h=}); the service spawns two block-display panels that slide apart smoothly (client-side
 * interpolation, identical on every client) and keeps an invisible wall in the opening while closed so the door
 * has real collision.
 */
public final class DoorService {
    public static final class Door {
        public final String id;
        public final Vec3 center;
        public final float yaw;
        public final int width, height;
        final List<UUID> panels = new ArrayList<>();
        final List<BlockPos> collision = new ArrayList<>();
        boolean open;
        final String panelBlock;

        Door(String id, Vec3 center, float yaw, int width, int height, String panelBlock) {
            this.id = id;
            this.center = center;
            this.yaw = yaw;
            this.width = width;
            this.height = height;
            this.panelBlock = panelBlock;
        }

        public boolean isOpen() {
            return open;
        }
    }

    private final Map<String, Door> doors = new LinkedHashMap<>();

    /** Creates a closed door from a marker (replaces an existing door with the same id). */
    public Door create(ServerLevel level, String id, Marker m, String panelBlock) {
        remove(level, id);
        int w = m.getInt("w", 5);
        int h = m.getInt("h", 4);
        Door d = new Door(id, new Vec3(m.x(), m.y(), m.z()), m.yaw(), w, h, panelBlock);
        // collision cells
        float rad = (float) Math.toRadians(m.yaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double lx = -fz, lz = fx;
        for (int k = 0; k < w; k++) {
            double off = k - (w - 1) / 2.0;
            double cx = m.x() + lx * off;
            double cz = m.z() + lz * off;
            for (int y = 0; y < h; y++) {
                BlockPos p = BlockPos.containing(cx, m.y() + y + 0.01, cz);
                d.collision.add(p);
            }
        }
        for (BlockPos p : d.collision) {
            level.getChunk(p);
            level.setBlock(p, ModBlocks.INVISIBLE_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // panels: local x = lateral, local z = travel direction (display rotated by the door's yaw)
        double thickness = 0.5;
        Vec3 anchor = d.center;
        Display.BlockDisplay a = DisplayUtil.spawnBlock(level, anchor, m.yaw(), panelBlock,
                new Vec3(-w / 2.0, 0, -thickness / 2), new Vec3(w / 2.0, h, thickness), "squidgame_door");
        Display.BlockDisplay b = DisplayUtil.spawnBlock(level, anchor, m.yaw(), panelBlock,
                new Vec3(0, 0, -thickness / 2), new Vec3(w / 2.0, h, thickness), "squidgame_door");
        if (a != null) {
            d.panels.add(a.getUUID());
        }
        if (b != null) {
            d.panels.add(b.getUUID());
        }
        doors.put(id, d);
        return d;
    }

    public Door get(String id) {
        return doors.get(id);
    }

    public void open(ServerLevel level, String id) {
        Door d = doors.get(id);
        if (d == null || d.open) {
            return;
        }
        d.open = true;
        slide(level, d, true);
        level.playSound(null, d.center.x, d.center.y + 1, d.center.z, ModSounds.DOOR_SLIDE_OPEN, SoundSource.BLOCKS, 1.5f, 1f);
        // open the collision once the panels have cleared the doorway (about half the slide)
        level.getServer().execute(() -> {
        });
        removeCollision(level, d);
    }

    public void close(ServerLevel level, String id) {
        Door d = doors.get(id);
        if (d == null || !d.open) {
            return;
        }
        d.open = false;
        slide(level, d, false);
        level.playSound(null, d.center.x, d.center.y + 1, d.center.z, ModSounds.DOOR_SLIDE_CLOSE, SoundSource.BLOCKS, 1.5f, 1f);
        for (BlockPos p : d.collision) {
            level.setBlock(p, ModBlocks.INVISIBLE_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void removeCollision(ServerLevel level, Door d) {
        for (BlockPos p : d.collision) {
            if (level.getBlockState(p).is(ModBlocks.INVISIBLE_WALL)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void slide(ServerLevel level, Door d, boolean open) {
        double w = d.width;
        double thickness = 0.5;
        for (int i = 0; i < d.panels.size(); i++) {
            var e = level.getEntity(d.panels.get(i));
            if (!(e instanceof Display disp)) {
                continue;
            }
            double closedX = i == 0 ? -w / 2.0 : 0;
            double openX = i == 0 ? -w : w / 2.0;
            DisplayUtil.animate(disp, new Vec3(open ? openX : closedX, 0, -thickness / 2), new Vec3(w / 2.0, d.height, thickness), 22);
        }
    }

    public void remove(ServerLevel level, String id) {
        Door d = doors.remove(id);
        if (d == null) {
            return;
        }
        for (UUID u : d.panels) {
            var e = level.getEntity(u);
            if (e != null) {
                e.discard();
            }
        }
        removeCollision(level, d);
    }

    public void removeAll(ServerLevel level) {
        for (String id : new ArrayList<>(doors.keySet())) {
            remove(level, id);
        }
    }

    public Iterable<Door> all() {
        return doors.values();
    }
}
