package com.squidgame.game.marbles;

import com.squidgame.SquidConfig;
import com.squidgame.SquidGameMod;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.core.Difficulty;
import com.squidgame.core.GameKind;
import com.squidgame.core.Phase;
import com.squidgame.core.marbles.MarblesRules;
import com.squidgame.core.marbles.MarblesRules.Variant;
import com.squidgame.core.marbles.MarblesRules.VariantSetting;
import com.squidgame.core.marbles.PairingPlanner;
import com.squidgame.core.marbles.Parity;
import com.squidgame.core.marbles.Side;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.ai.WaitingBehavior;
import com.squidgame.game.EliminationCause;
import com.squidgame.game.GameContext;
import com.squidgame.game.GameResult;
import com.squidgame.game.MiniGame;
import com.squidgame.net.HudPayload;
import com.squidgame.net.OpenScreenPayload;
import com.squidgame.registry.ModItems;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Contestant;
import com.squidgame.tournament.Teleporter;
import com.squidgame.tournament.Tournament;
import com.squidgame.tournament.TournamentManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Game 4, Marbles. Contestants pick a partner in the square (phase 1), then every pair goes to its own plot of the
 * village and plays for each other's marbles - odd or even, or a target throw, depending on the
 * {@code marblesVariant} setting. Whoever loses all marbles, or owns fewer when time is up, is eliminated; the winner
 * of every pair survives (an odd one out gets a bye).
 *
 * <p>The rules live in {@code core/marbles} (pure, unit tested); this class drives the phases and the clock, the
 * {@link Match}es drive one pair each, and {@link PairingManager} runs the partnership market. Humans act through
 * screens / the use key, NPCs through {@link MarblesNpcBehavior} - both end up in the same validated submit methods.
 */
public final class MarblesGame implements MiniGame {
    enum Stage {PRE, PAIRING, MATCHES}

    private GameContext ctx;
    private Stage stage = Stage.PRE;
    private Difficulty difficulty = Difficulty.NORMAL;
    private MarblesRules.Params params = MarblesRules.params(Difficulty.NORMAL);
    private VariantSetting variantSetting = VariantSetting.MIXED;
    @Nullable
    private String savedVariant;
    private Rng rng = new Rng(0);
    private final List<Plot> plots = new ArrayList<>();
    private PairingManager pairing;
    private final List<Match> matches = new ArrayList<>();
    private final Map<Integer, Match> matchByNumber = new HashMap<>();
    private final Set<Integer> safe = new LinkedHashSet<>();
    private final Set<Integer> losers = new LinkedHashSet<>();
    private final Map<Integer, Long> lastAction = new HashMap<>();
    private final Map<Integer, Integer> strayTicks = new HashMap<>();
    private int byeNumber = -1;
    private boolean matchesLive;
    private boolean timeCalled;
    private boolean hardCalled;
    private boolean pairingSped;
    private long clock;
    private long pairingEnd;
    private long softDeadline;
    private long hardDeadline;
    private int limitTicks;

    // ------------------------------------------------------------------ package API for matches, pairing and NPCs

    GameContext ctx() {
        return ctx;
    }

    MarblesRules.Params params() {
        return params;
    }

    Difficulty difficulty() {
        return difficulty;
    }

    long clock() {
        return clock;
    }

    PairingManager pairing() {
        return pairing;
    }

    boolean pairingOpen() {
        return stage == Stage.PAIRING;
    }

    @Nullable
    Match matchOf(int number) {
        return matchByNumber.get(number);
    }

    /** Seconds at normal speed to ticks, scaled by the configured {@code timeScale} (the same scale as every game timer). */
    int ticks(double seconds) {
        return SquidConfig.get().ticks(seconds);
    }

    /** A short message in the action bar of a human-controlled contestant. */
    void tell(Contestant c, Component msg) {
        if (c.isHumanControlled()) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null) {
                p.displayClientMessage(msg, true);
            }
        }
    }

    /** Mirrors a ledger count into the human's hotbar (slot 0 is selected; the real inventory is restored by the tournament). */
    void giveMarbles(Contestant c, int count) {
        if (!c.isHumanControlled()) {
            return;
        }
        ServerPlayer p = c.player(ctx.server());
        if (p == null) {
            return;
        }
        Inventory inv = p.getInventory();
        ItemStack cur = inv.getItem(0);
        boolean same = count > 0 ? cur.is(ModItems.MARBLE) && cur.getCount() == count : cur.isEmpty();
        if (!same) {
            inv.setItem(0, count > 0 ? new ItemStack(ModItems.MARBLE, count) : ItemStack.EMPTY);
            p.containerMenu.broadcastChanges();
        }
        if (inv.selected != 0) {
            inv.selected = 0;
            p.connection.send(new ClientboundSetCarriedItemPacket(0));
        }
    }

    private void takeMarbles(Contestant c) {
        ServerPlayer p = c.isHuman() ? c.player(ctx.server()) : null;
        if (p == null) {
            return;
        }
        Inventory inv = p.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.MARBLE)) {
                inv.setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            p.containerMenu.broadcastChanges();
        }
    }

    // ------------------------------------------------------------------ MiniGame: description

    @Override
    public GameKind type() {
        return GameKind.MARBLES;
    }

    @Override
    public List<Component> instructions(GameContext ctx) {
        MarblesRules.Params p = MarblesRules.params(ctx.difficulty());
        VariantSetting setting = MarblesRules.parseVariant(ctx.config().marblesVariant);
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("squidgame.game.marbles.instruction.1", (int) p.pairingSeconds()));
        l.add(Component.translatable("squidgame.game.marbles.instruction.2", p.startMarbles()));
        if (setting != VariantSetting.THROW) {
            l.add(Component.translatable("squidgame.game.marbles.instruction.oddeven.1"));
            l.add(Component.translatable("squidgame.game.marbles.instruction.oddeven.2"));
        }
        if (setting != VariantSetting.ODD_EVEN) {
            l.add(Component.translatable("squidgame.game.marbles.instruction.throw.1"));
            l.add(Component.translatable("squidgame.game.marbles.instruction.throw.2", p.stakeCap()));
        }
        if (setting == VariantSetting.MIXED) {
            l.add(Component.translatable("squidgame.game.marbles.instruction.mixed"));
        }
        l.add(Component.translatable("squidgame.game.marbles.instruction.out", ctx.difficulty().id));
        return l;
    }

    @Override
    public Component objective(GameContext ctx, @Nullable Contestant viewer) {
        if (viewer != null && viewer.isAlive()) {
            if (stage == Stage.PAIRING) {
                return Component.translatable(pairing != null && pairing.isPaired(viewer.number)
                        ? "squidgame.game.marbles.objective.paired" : "squidgame.game.marbles.objective.pairing");
            }
            if (stage == Stage.MATCHES) {
                if (viewer.number == byeNumber) {
                    return Component.translatable("squidgame.game.marbles.objective.bye");
                }
                Match m = matchByNumber.get(viewer.number);
                if (m != null) {
                    if (m.decided()) {
                        return Component.translatable(m.winner().number == viewer.number
                                ? "squidgame.game.marbles.objective.won" : "squidgame.game.marbles.objective.lost");
                    }
                    return Component.translatable(m.variant == Variant.THROW
                            ? "squidgame.game.marbles.objective.throw" : "squidgame.game.marbles.objective.oddeven");
                }
                if (safe.contains(viewer.number)) {
                    return Component.translatable("squidgame.game.marbles.objective.won");
                }
            }
        }
        return Component.translatable("squidgame.game.marbles.objective");
    }

    @Override
    public int timeLimitTicks(GameContext ctx) {
        return (int) (MarblesRules.params(ctx.difficulty()).totalSeconds() * 20);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void prepare(GameContext ctx) {
        this.ctx = ctx;
        difficulty = ctx.difficulty();
        params = MarblesRules.params(difficulty);
        variantSetting = MarblesRules.parseVariant(savedVariant != null ? savedVariant : ctx.config().marblesVariant);
        rng = ctx.rng().fork(0x4D415242L);
        resetState();
        loadPlots();
        ctx.spawnGuards();
        SquidGameMod.LOGGER.info("Marbles: {} plot(s), variant {}, {} marbles each, difficulty {}", plots.size(), variantSetting,
                params.startMarbles(), difficulty.id);
    }

    private void resetState() {
        stage = Stage.PRE;
        plots.clear();
        matches.clear();
        matchByNumber.clear();
        safe.clear();
        losers.clear();
        lastAction.clear();
        strayTicks.clear();
        byeNumber = -1;
        matchesLive = false;
        timeCalled = false;
        hardCalled = false;
        pairingSped = false;
        clock = 0;
        pairing = null;
    }

    private void loadPlots() {
        Map<Integer, Marker> a = byK(ctx.markers("marbles.pair_a"));
        Map<Integer, Marker> b = byK(ctx.markers("marbles.pair_b"));
        Map<Integer, Marker> line = byK(ctx.markers("marbles.pair_line"));
        Map<Integer, Marker> target = byK(ctx.markers("marbles.pair_target"));
        Map<Integer, Marker> table = byK(ctx.markers("marbles.table"));
        List<Region> regions = ctx.regions("marbles.plot");
        List<Integer> ks = new ArrayList<>(a.keySet());
        ks.sort(Integer::compare);
        for (int k : ks) {
            if (b.containsKey(k) && line.containsKey(k) && target.containsKey(k)) {
                plots.add(new Plot(k, a.get(k), b.get(k), line.get(k), target.get(k), table.get(k),
                        k >= 0 && k < regions.size() ? regions.get(k) : null));
            }
        }
    }

    private static Map<Integer, Marker> byK(List<Marker> markers) {
        Map<Integer, Marker> out = new HashMap<>();
        for (int i = 0; i < markers.size(); i++) {
            out.put(markers.get(i).getInt("k", i), markers.get(i));
        }
        return out;
    }

    @Override
    public void placeContestants(GameContext ctx) {
        Teleporter.spread(ctx.level, ctx.alive(), ctx.markers("marbles.square_spawn"));
        for (Contestant c : ctx.alive()) {
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setActivity(Activity.NONE);
            }
        }
    }

    @Override
    public void begin(GameContext ctx) {
        stage = Stage.PAIRING;
        clock = 0;
        limitTicks = (int) Math.max(20, timeLimitTicks(ctx) * SquidConfig.get().timeScale);
        pairingEnd = ticks(params.pairingSeconds());
        softDeadline = limitTicks - ticks(params.overtimeReserveSeconds());
        hardDeadline = limitTicks - ticks(1.5);
        pairing = new PairingManager(this, ctx, rng.fork(1));
        ctx.doors().close(ctx.level, "gate");
        ctx.assignBehaviors(c -> new MarblesNpcBehavior(this, c));
        ctx.title(Component.translatable("squidgame.game.marbles.pairing.title"),
                Component.translatable("squidgame.game.marbles.pairing.subtitle"), 5, 60, 10);
        ctx.sound(ModSounds.ANNOUNCE_CHIME, 1f, 1f);
        for (Contestant c : ctx.aliveHumans()) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null) {
                p.sendSystemMessage(Component.translatable("squidgame.game.marbles.pairing.hint", (int) params.pairingSeconds()));
            }
        }
    }

    @Override
    public void onControllerChanged(GameContext ctx, Contestant c) {
        if (!c.isAlive() || stage == Stage.PRE) {
            return;
        }
        if (c.isAiControlled()) {
            ContestantEntity e = ctx.npc(c);
            if (e != null && !safe.contains(c.number) && c.number != byeNumber) {
                e.setBehavior(new MarblesNpcBehavior(this, c));
            } else if (e != null) {
                e.setBehavior(new WaitingBehavior());
            }
            if (pairing != null) {
                pairing.onControllerChanged(c);
            }
        } else {
            Match m = matchByNumber.get(c.number);
            if (m != null && !m.closed()) {
                m.setUpIfNeeded();
                m.resendUi(c);
                giveMarbles(c, m.shown(m.sideOf(c)));
            }
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick(GameContext ctx) {
        clock++;
        switch (stage) {
            case PAIRING -> tickPairing();
            case MATCHES -> tickMatches();
            default -> {
            }
        }
    }

    private void tickPairing() {
        pairing.tick(clock);
        if (!pairingSped && clock > ticks(6) && pairing.everyonePaired()) {
            // everybody found a partner: no reason to wait for the clock
            pairingSped = true;
            pairingEnd = Math.min(pairingEnd, clock + ticks(3));
            SquidGameMod.LOGGER.info("Marbles: everybody has a partner after {} ticks", clock);
        }
        if (clock >= pairingEnd) {
            startMatches(false);
        }
    }

    private void tickMatches() {
        if (matchesLive) {
            for (Match m : matches) {
                if (!m.closed()) {
                    m.tick(clock);
                }
            }
            if (clock % 20 == 0) {
                housekeeping();
            }
        }
        if (!timeCalled && clock >= softDeadline) {
            timeCalled = true;
            for (Match m : matches) {
                if (!m.decided()) {
                    m.timeCall(clock);
                }
            }
        }
        if (!hardCalled && clock >= hardDeadline) {
            hardCalled = true;
            for (Match m : matches) {
                if (!m.decided()) {
                    m.forceResolve(clock);
                }
            }
        }
    }

    /** Once a second: hotbar mirrors, stray marble items, humans who wandered away from their plot. */
    private void housekeeping() {
        for (Match m : matches) {
            if (m.closed()) {
                continue;
            }
            for (Side s : Side.values()) {
                Contestant c = m.of(s);
                if (!c.isAlive() || !c.isHumanControlled()) {
                    continue;
                }
                ServerPlayer p = c.player(ctx.server());
                if (p == null) {
                    continue;
                }
                m.giveMarblesTo(c, s);
                for (ItemEntity item : ctx.level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6.0))) {
                    if (item.getItem().is(ModItems.MARBLE)) {
                        item.discard();
                    }
                }
                Inventory inv = p.getInventory();
                for (int i = 1; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).is(ModItems.MARBLE)) {
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                }
                if (!m.plot.near(p.position(), 3.0)) {
                    int n = strayTicks.merge(c.number, 1, Integer::sum);
                    if (n >= 4) {
                        strayTicks.remove(c.number);
                        Vec3 pad = m.plot.padPos(s);
                        ctx.teleport(c, pad, s == Side.A ? m.plot.padA().yaw() : m.plot.padB().yaw());
                        tell(c, Component.translatable("squidgame.game.marbles.stay_at_plot"));
                    }
                } else {
                    strayTicks.remove(c.number);
                }
            }
        }
    }

    // ------------------------------------------------------------------ phase change: pairing -> matches

    /**
     * Closes the pairing phase: agreed partners stay together, everybody else is paired at random, the odd one out gets
     * the bye, then every pair is moved to its plot. {@code instant} skips the fade (the game is being concluded).
     */
    private void startMatches(boolean instant) {
        stage = Stage.MATCHES;
        List<Integer> numbers = new ArrayList<>();
        for (Contestant c : ctx.alive()) {
            numbers.add(c.number);
        }
        int agreed = pairing.pairsFormed();
        PairingPlanner.Plan plan = PairingPlanner.complete(numbers, pairing.locked(), rng.fork(2));
        byeNumber = plan.bye();
        List<int[]> pairs = plan.pairs();
        int oddEven = 0, throwing = 0;
        for (int i = 0; i < pairs.size(); i++) {
            Contestant x = ctx.byNumber(pairs.get(i)[0]);
            Contestant y = ctx.byNumber(pairs.get(i)[1]);
            if (i >= plots.size()) {
                // the arena has fewer plots than pairs: the surplus pairs cannot play and are safe (logged, never expected)
                SquidGameMod.LOGGER.warn("Marbles: {} pairs but only {} plots; No. {} and No. {} get a bye", pairs.size(), plots.size(),
                        x.displayNumber(), y.displayNumber());
                safe.add(x.number);
                safe.add(y.number);
                continue;
            }
            Plot plot = plots.get(i);
            Variant v = MarblesRules.variantFor(variantSetting, rng.fork(1000 + plot.k()));
            Match m = v == Variant.ODD_EVEN ? new OddEvenMatch(this, plot, x, y, rng.fork(2000 + plot.k()))
                    : new ThrowMatch(this, plot, x, y, rng.fork(2000 + plot.k()));
            matches.add(m);
            matchByNumber.put(x.number, m);
            matchByNumber.put(y.number, m);
            if (v == Variant.ODD_EVEN) {
                oddEven++;
            } else {
                throwing++;
            }
        }
        if (byeNumber >= 0) {
            safe.add(byeNumber);
            Contestant bye = ctx.byNumber(byeNumber);
            ContestantEntity body = bye == null ? null : ctx.npc(bye);
            if (body != null && bye.isAiControlled()) {
                body.setBehavior(new WaitingBehavior());
            }
        }
        SquidGameMod.LOGGER.info("Marbles: pairing closed: {} agreed pair(s), {} match(es) ({} odd-even, {} throw), bye {}", agreed,
                matches.size(), oddEven, throwing, byeNumber >= 0 ? "No. " + String.format("%03d", byeNumber) : "none");
        // clear the pairing screens of everybody who is not paired yet
        for (Contestant c : ctx.aliveHumans()) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null) {
                ctx.screen(p, PairingManager.SCREEN, OpenScreenPayload.CLOSE, new CompoundTag());
            }
        }
        announceMatches();
        if (instant) {
            moveToPlots();
        } else {
            ctx.sound(ModSounds.ANNOUNCE_CHIME_ALERT, 1f, 1f);
            for (Contestant c : ctx.aliveHumans()) {
                ServerPlayer p = c.player(ctx.server());
                if (p != null && matchByNumber.containsKey(c.number)) {
                    ctx.fade(p, 8, 16, 12, 0xFF000000);
                }
            }
            ctx.schedule(10, this::moveToPlots);
        }
    }

    private void announceMatches() {
        for (Contestant c : ctx.alive()) {
            Match m = matchByNumber.get(c.number);
            if (m != null) {
                Contestant opp = m.opponentOf(c);
                tellTitle(c, Component.translatable(m.variant == Variant.THROW ? "squidgame.game.marbles.match.throw"
                                : "squidgame.game.marbles.match.oddeven"),
                        Component.translatable("squidgame.game.marbles.match.versus", opp.displayNumber(), opp.name));
            } else if (c.number == byeNumber) {
                tellTitle(c, Component.translatable("squidgame.game.marbles.bye.title"),
                        Component.translatable("squidgame.game.marbles.bye.subtitle"));
            }
        }
        if (byeNumber >= 0) {
            ctx.broadcast(Component.translatable("squidgame.game.marbles.bye.announce", String.format("%03d", byeNumber)));
        }
    }

    private void tellTitle(Contestant c, Component title, Component subtitle) {
        if (c.isHumanControlled()) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null) {
                com.squidgame.tournament.Announcer.title(p, title, subtitle, 8, 60, 15);
            }
        }
    }

    private void moveToPlots() {
        for (Match m : matches) {
            for (Side s : Side.values()) {
                Contestant c = m.of(s);
                if (!c.isAlive()) {
                    continue;
                }
                Vec3 pad = m.plot.padPos(s);
                float yaw = m.variant == Variant.THROW ? Plot.yawToward(pad, m.plot.bullseye())
                        : (s == Side.A ? m.plot.padA().yaw() : m.plot.padB().yaw());
                ctx.teleport(c, pad, yaw);
                ContestantEntity e = ctx.npc(c);
                if (e != null && c.isAiControlled()) {
                    e.setActivity(Activity.NONE);
                    e.getNavigation().stop();
                }
            }
        }
        matchesLive = true;
        for (Match m : matches) {
            if (m.decided()) {
                continue;
            }
            m.begin(clock);
        }
    }

    // ------------------------------------------------------------------ end of a match

    /** A match's end sequence is over: the loser is eliminated (by a guard, if one is near), the winner is safe. */
    void concludeMatch(Match m) {
        concludeMatch(m, ticks(1.2) + rng.nextInt(12));
    }

    void concludeMatch(Match m, int aimTicks) {
        Contestant w = m.winner(), l = m.loser();
        safe.add(w.number);
        if (w.isAlive()) {
            ContestantEntity e = ctx.npc(w);
            if (e != null && w.isAiControlled()) {
                e.setBehavior(new WaitingBehavior());
                // the cheering stops after a few seconds
                ctx.schedule(ticks(5), () -> {
                    if (e.isAlive() && e.getActivity() == Activity.CELEBRATE_FIST) {
                        e.setActivity(Activity.NONE);
                    }
                });
            }
        }
        if (l.isAlive() && losers.add(l.number)) {
            LivingEntity body = l.body(ctx.level);
            if (body == null || ctx.guards().list().isEmpty()) {
                ctx.eliminate(l, EliminationCause.LOST_MATCH);
            } else {
                ctx.guards().fireAt(body, aimTicks, () -> ctx.eliminate(l, EliminationCause.LOST_MATCH));
            }
        }
    }

    @Override
    public void onContestantEliminated(GameContext ctx, Contestant c, EliminationCause cause) {
        if (stage == Stage.PAIRING && pairing != null) {
            pairing.remove(c.number);
        }
        Match m = matchByNumber.get(c.number);
        if (m != null && !m.decided()) {
            m.forfeit(m.sideOf(c), clock);
        }
        takeMarbles(c);
    }

    // ------------------------------------------------------------------ human input

    /** A human right-clicked another human during the pairing phase (see {@link MarblesNet}). */
    static boolean onPlayerClickedPlayer(ServerPlayer from, ServerPlayer to) {
        TournamentManager manager = TournamentManager.get();
        Tournament t = manager == null ? null : manager.tournament();
        if (t == null || !(t.game instanceof MarblesGame g) || t.ctx == null || t.phase != Phase.GAME) {
            return false;
        }
        Contestant cf = t.roster.ofPlayer(from.getUUID());
        Contestant ct = t.roster.ofPlayer(to.getUUID());
        return cf != null && ct != null && cf.isAlive() && ct.isAlive() && g.proposeByClick(cf, ct);
    }

    private boolean proposeByClick(Contestant from, Contestant to) {
        if (stage != Stage.PAIRING || pairing == null) {
            return false;
        }
        pairing.report(from, to, pairing.propose(from, to));
        return true;
    }

    @Override
    public boolean onInteractContestant(GameContext ctx, Contestant c, ServerPlayer player, ContestantEntity target) {
        if (stage != Stage.PAIRING || pairing == null || !c.isAlive()) {
            return false;
        }
        Contestant to = ctx.byNumber(target.contestantNumber());
        if (to == null || !to.isAlive()) {
            return false;
        }
        pairing.report(c, to, pairing.propose(c, to));
        return true;
    }

    @Override
    public void onMarbleRelease(GameContext ctx, Contestant c, ServerPlayer player, int chargedTicks) {
        if (!c.isAlive() || !c.isHumanControlled()) {
            return;
        }
        if (matchByNumber.get(c.number) instanceof ThrowMatch tm && !tm.decided()) {
            tm.onHumanRelease(c, player, chargedTicks);
        }
    }

    @Override
    public void onClientAction(GameContext ctx, Contestant c, ServerPlayer player, String id, CompoundTag data) {
        if (!c.isAlive() || !c.isHumanControlled()) {
            return;
        }
        Long last = lastAction.get(c.number);
        if (last != null && clock - last < 2) {
            return;
        }
        lastAction.put(c.number, clock);
        Match m = matchByNumber.get(c.number);
        switch (id) {
            case MarblesNet.ACTION_HOLD -> {
                if (m instanceof OddEvenMatch o && data.contains("count", Tag.TAG_ANY_NUMERIC)) {
                    if (!o.submitHold(c, data.getInt("count"))) {
                        o.resendUi(c);
                    }
                }
            }
            case MarblesNet.ACTION_GUESS -> {
                if (m instanceof OddEvenMatch o && data.contains("wager", Tag.TAG_ANY_NUMERIC) && data.contains("odd")) {
                    Parity call = data.getBoolean("odd") ? Parity.ODD : Parity.EVEN;
                    if (!o.submitGuess(c, data.getInt("wager"), call)) {
                        o.resendUi(c);
                    }
                }
            }
            case MarblesNet.ACTION_PAIR_ANSWER -> {
                if (pairing != null && stage == Stage.PAIRING && data.contains("from", Tag.TAG_ANY_NUMERIC)) {
                    pairing.answer(c, data.getInt("from"), data.getBoolean("accept"));
                }
            }
            case MarblesNet.ACTION_REOPEN -> {
                if (m != null && !m.closed()) {
                    m.resendUi(c);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ end of the game

    @Override
    public boolean isFinished(GameContext ctx) {
        if (stage != Stage.MATCHES || !matchesLive) {
            return false;
        }
        for (Match m : matches) {
            if (!m.closed()) {
                return false;
            }
        }
        for (int n : losers) {
            Contestant c = ctx.byNumber(n);
            if (c != null && c.isAlive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onTimeout(GameContext ctx) {
        ctx.title(Component.translatable("squidgame.game.marbles.time_up"), Component.empty(), 0, 30, 10);
    }

    @Override
    public GameResult conclude(GameContext ctx) {
        if (stage == Stage.PRE || stage == Stage.PAIRING) {
            if (pairing == null) {
                pairing = new PairingManager(this, ctx, rng.fork(1));
            }
            startMatches(true);
        }
        int i = 0;
        for (Match m : matches) {
            if (!m.decided()) {
                m.forceResolve(clock);
            }
            if (!m.closed()) {
                m.closeNow(2 + 2 * i++);
            }
        }
        List<Contestant> survivors = new ArrayList<>();
        List<Contestant> eliminated = new ArrayList<>();
        for (Contestant c : ctx.alive()) {
            if (safe.contains(c.number)) {
                survivors.add(c);
            }
        }
        for (int n : losers) {
            Contestant c = ctx.byNumber(n);
            if (c != null) {
                eliminated.add(c);
            }
        }
        int pairsPlayed = matches.size();
        return new GameResult(survivors, eliminated, Component.translatable("squidgame.game.marbles.headline", survivors.size()),
                Component.translatable("squidgame.game.marbles.detail", pairsPlayed, byeNumber >= 0 ? 1 : 0));
    }

    @Override
    public void cleanup(GameContext ctx) {
        for (Match m : matches) {
            m.cleanup();
        }
        if (ctx != null && ctx.tournament != null) {
            for (Contestant c : ctx.tournament.roster.all()) {
                takeMarbles(c);
                ServerPlayer p = c.isHuman() ? c.player(ctx.server()) : null;
                if (p != null) {
                    ctx.screen(p, OddEvenMatch.SCREEN, OpenScreenPayload.CLOSE, new CompoundTag());
                    ctx.screen(p, PairingManager.SCREEN, OpenScreenPayload.CLOSE, new CompoundTag());
                    com.squidgame.net.ModNetwork.send(p, ThrowStatePayload.inactive());
                }
            }
        }
        matches.clear();
        matchByNumber.clear();
        matchesLive = false;
        stage = Stage.PRE;
        if (ctx != null) {
            ctx.cleanupGuards();
        }
    }

    // ------------------------------------------------------------------ HUD

    @Override
    public void hudWidgets(GameContext ctx, @Nullable Contestant viewer, List<HudPayload.Widget> out) {
        if (stage == Stage.PAIRING && pairing != null) {
            int seconds = (int) Math.ceil(Math.max(0, pairingEnd - clock) / (20.0 * SquidConfig.get().timeScale));
            out.add(HudPayload.Widget.line("pairing", Component.translatable("squidgame.game.marbles.hud.pairing", seconds)));
            out.add(HudPayload.Widget.counter("pairs", "icon_survivors", Component.translatable("squidgame.game.marbles.hud.pairs"),
                    pairing.pairsFormed(), ctx.alive().size() / 2));
            if (viewer != null && viewer.isAlive()) {
                int partner = pairing.partnerOf(viewer.number);
                out.add(HudPayload.Widget.line("partner", partner >= 0
                        ? Component.translatable("squidgame.game.marbles.hud.partner", String.format("%03d", partner))
                        : Component.translatable("squidgame.game.marbles.hud.no_partner")));
            }
        } else if (stage == Stage.MATCHES) {
            Match m = viewer == null ? null : matchByNumber.get(viewer.number);
            if (m != null) {
                m.hudWidgets(viewer, out);
            } else if (viewer != null && viewer.isAlive() && viewer.number == byeNumber) {
                out.add(HudPayload.Widget.banner("bye", Component.translatable("squidgame.game.marbles.banner.bye"), 0x55FF88));
            }
            int running = 0;
            for (Match x : matches) {
                if (!x.decided()) {
                    running++;
                }
            }
            out.add(HudPayload.Widget.line("matches", Component.translatable("squidgame.game.marbles.hud.matches", running, matches.size())));
        }
    }

    // ------------------------------------------------------------------ persistence

    /** Only the variant setting must survive a restart: the interrupted game is replayed from its start with the same rules. */
    @Override
    public void saveState(CompoundTag tag) {
        tag.putString("variant", variantSetting.name());
    }

    @Override
    public void loadState(CompoundTag tag) {
        if (tag.contains("variant")) {
            savedVariant = tag.getString("variant");
        }
    }
}
