package com.squidgame.build.tools;

import com.squidgame.build.ArenaBuilders;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BlockBuffer;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer tool: runs arena builders without Minecraft and writes {@code <out>/<id>.sqbuf} dumps
 * for {@code tools/preview.py}. Usage: {@code ./gradlew dumpArena -Parena=red_light|all [-Pseed=1]}
 */
public final class DumpArena {
    private DumpArena() {
    }

    public static void main(String[] args) throws Exception {
        String which = args.length > 0 ? args[0] : "all";
        Path out = Path.of(args.length > 1 ? args[1] : "tools/out");
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1L;
        Files.createDirectories(out);
        List<ArenaId> ids = new ArrayList<>();
        if (which.equalsIgnoreCase("all")) {
            ids.addAll(List.of(ArenaId.values()));
        } else {
            ArenaId id = ArenaId.byId(which);
            if (id == null) {
                System.err.println("unknown arena " + which);
                System.exit(2);
            }
            ids.add(id);
        }
        int failures = 0;
        for (ArenaId id : ids) {
            List<String> problems = new ArrayList<>();
            long t0 = System.nanoTime();
            BlockBuffer buf = ArenaBuilders.buildAndValidate(id, seed, problems);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            Path file = out.resolve(id.id + ".sqbuf");
            try (OutputStream os = Files.newOutputStream(file)) {
                buf.writeBinary(os);
            }
            System.out.printf("%-13s %s %,d blocks, bounds x[%d..%d] y[%d..%d] z[%d..%d], %d markers kinds, %d entities, %d ms -> %s%n",
                    id, ArenaBuilders.isPlaceholder(id) ? "(placeholder)" : "", buf.solidCount(),
                    buf.minX(), buf.maxX(), buf.minY(), buf.maxY(), buf.minZ(), buf.maxZ(),
                    buf.markers().size(), buf.entities().size(), ms, file);
            for (String p : problems) {
                System.out.println("  PROBLEM: " + p);
                failures++;
            }
        }
        if (failures > 0) {
            System.exit(1);
        }
    }
}
