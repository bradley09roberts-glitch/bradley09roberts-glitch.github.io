package com.squidgame.game;

import com.squidgame.SquidGameMod;
import com.squidgame.core.GameKind;

/**
 * Instantiates games by type. Implementations are discovered by class name so each game lives entirely in its own
 * package: {@code com.squidgame.game.<pkg>.<Name>Game}. A missing implementation falls back to {@link StubGame} so the
 * whole tournament flow can run while games are still being built.
 */
public final class GameRegistry {
    private GameRegistry() {
    }

    public static String className(GameKind type) {
        return switch (type) {
            case RED_LIGHT -> "com.squidgame.game.redlight.RedLightGreenLightGame";
            case DALGONA -> "com.squidgame.game.dalgona.DalgonaGame";
            case TUG_OF_WAR -> "com.squidgame.game.tug.TugOfWarGame";
            case MARBLES -> "com.squidgame.game.marbles.MarblesGame";
            case GLASS_BRIDGE -> "com.squidgame.game.bridge.GlassBridgeGame";
            case FINAL -> "com.squidgame.game.finale.FinalSquidGame";
        };
    }

    public static MiniGame create(GameKind type) {
        try {
            Class<?> c = Class.forName(className(type));
            return (MiniGame) c.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            SquidGameMod.LOGGER.warn("No implementation for {} yet; using the stub game", type);
            return new StubGame(type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create game " + type, e);
        }
    }

    public static boolean isImplemented(GameKind type) {
        try {
            Class.forName(className(type));
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
