package com.squidgame.build;

import java.util.List;

/**
 * Registry of arena builders. Real builders live in {@code com.squidgame.build.arena} and are
 * discovered by class name (so adding one needs no registry edit); an arena without a builder
 * class gets a minimal {@link PlaceholderBuilder} so the rest of the system keeps working.
 *
 * <p>Naming: {@code HubBuilder}, {@code RedLightBuilder}, {@code DalgonaBuilder}, {@code TugOfWarBuilder},
 * {@code MarblesBuilder}, {@code GlassBridgeBuilder}, {@code FinalBuilder}.
 */
public final class ArenaBuilders {
    private ArenaBuilders() {
    }

    public static String builderClassName(ArenaId id) {
        return "com.squidgame.build.arena." + switch (id) {
            case HUB -> "HubBuilder";
            case RED_LIGHT -> "RedLightBuilder";
            case DALGONA -> "DalgonaBuilder";
            case TUG_OF_WAR -> "TugOfWarBuilder";
            case MARBLES -> "MarblesBuilder";
            case GLASS_BRIDGE -> "GlassBridgeBuilder";
            case FINAL -> "FinalBuilder";
        };
    }

    /**
     * Optional test fixture used while the real builder does not exist yet:
     * {@code com.squidgame.build.placeholder.<Name>Placeholder} (same name as the real builder with "Builder" replaced).
     * A fixture is a small flat arena that honours the marker contract so a game can be developed and tested in-world.
     */
    public static String placeholderClassName(ArenaId id) {
        String real = builderClassName(id);
        String simple = real.substring(real.lastIndexOf('.') + 1);
        return "com.squidgame.build.placeholder." + simple.replace("Builder", "Placeholder");
    }

    private static ArenaBuilder instantiate(String className) throws ClassNotFoundException {
        try {
            return (ArenaBuilder) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate builder " + className, e);
        }
    }

    public static ArenaBuilder get(ArenaId id) {
        try {
            return instantiate(builderClassName(id));
        } catch (ClassNotFoundException e) {
            try {
                return instantiate(placeholderClassName(id));
            } catch (ClassNotFoundException e2) {
                return new PlaceholderBuilder(id);
            }
        }
    }

    /** True when the arena has no real builder (a fixture or the generic placeholder stands in). */
    public static boolean isPlaceholder(ArenaId id) {
        try {
            Class.forName(builderClassName(id));
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    /** Runs a builder into a fresh buffer and checks the required markers/regions. */
    public static BlockBuffer buildAndValidate(ArenaId id, long seed, List<String> problems) {
        ArenaBuilder b = get(id);
        BlockBuffer buf = new BlockBuffer();
        BuildContext ctx = new BuildContext(buf, id.originX, ArenaId.ORIGIN_Y, id.originZ, seed);
        b.build(ctx);
        for (String m : b.requiredMarkers()) {
            if (buf.markers(m).isEmpty()) {
                problems.add(id + ": missing marker '" + m + "'");
            }
        }
        for (String r : b.requiredRegions()) {
            if (buf.regions(r).isEmpty()) {
                problems.add(id + ": missing region '" + r + "'");
            }
        }
        return buf;
    }
}
