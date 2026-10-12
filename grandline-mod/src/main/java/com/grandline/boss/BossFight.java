package com.grandline.boss;

import com.grandline.GrandLine;
import com.grandline.combat.CombatState;
import com.grandline.combat.Hits;
import com.grandline.haki.Haki;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.PrisonSystem;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.Characters;
import com.grandline.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * One running boss fight: the boss bar shown to nearby players, the phase (from the boss's health; crossing a
 * threshold starts a short invulnerable transition with a roar or a Conqueror's burst, a new move set and an arena
 * change), the boss's telegraphed signature attacks, the arena's hazards, the enrage timer, player-count scaling,
 * the boss's foresight (advanced Observation, broken by stagger) and the fair-knockdown rule (no finishing blows
 * while someone else is still fighting).
 */
public final class BossFight {
    static final double BAR_RADIUS = 48, LEASH = 64, JOIN_RADIUS = 32;
    static final int RESET_TICKS = 20 * 30, TRANSITION_TICKS = 50, FORESIGHT_BREAK = 160;
    private static final ResourceLocation ROOT = GrandLine.id("boss_root");

    public final HumanoidNpc boss;
    public final BossDef def;
    public final ServerLevel level;
    final ServerBossEvent bar;
    int phase, pendingPhase;
    long start, transitionUntil = -1, nextMoveAt, lastFoeSeen, foresightBrokenUntil = -1, lastClash = -1000;
    boolean enraged, ended, wasStaggered;
    /** Players (1-4) counted for scaling. */
    int players = 1;
    int clashes, foresightBreaks, movesStarted;
    @Nullable Telegraph current;
    final List<Telegraph> telegraphs = new ArrayList<>();
    final List<BossArena.Hazard> hazards = new ArrayList<>();
    final Map<UUID, LivingEntity> participants = new LinkedHashMap<>();
    /** Downed players whose knockdown outcome (arrest, robbery) waits until nobody else is fighting. */
    final Map<UUID, ServerPlayer> deferred = new LinkedHashMap<>();
    final Map<String, Long> moveReady = new HashMap<>();
    @Nullable String lastMove;
    /** Free per-fight state for arena scripts (cage radius, counters). */
    final Map<String, Double> vars = new HashMap<>();

    BossFight(HumanoidNpc boss, BossDef def) {
        this.boss = boss;
        this.def = def;
        this.level = (ServerLevel) boss.level();
        long now = level.getGameTime();
        this.start = now;
        this.lastFoeSeen = now;
        this.nextMoveAt = now + 40;
        this.phase = Math.min(def.phaseCount(), def.phaseForHealth(boss.getHealth() / boss.getMaxHealth()));
        this.bar = new ServerBossEvent(barName(), def.color, BossEvent.BossBarOverlay.NOTCHED_20);
        bar.setPlayBossMusic(true);
        updateBar();
    }

    // ------------------------------------------------------------------ queries

    public int phase() { return phase; }
    public boolean enraged() { return enraged; }
    public int players() { return players; }
    public ServerBossEvent bar() { return bar; }
    public boolean transitioning() { return transitionUntil > level.getGameTime() || pendingPhase > 0; }
    @Nullable public Telegraph current() { return current; }
    public List<Telegraph> telegraphs() { return Collections.unmodifiableList(telegraphs); }
    public List<BossArena.Hazard> hazards() { return Collections.unmodifiableList(hazards); }
    public int clashes() { return clashes; }
    public int movesStarted() { return movesStarted; }
    public int foresightBreaks() { return foresightBreaks; }
    public boolean ended() { return ended; }
    public Collection<LivingEntity> participants() { return Collections.unmodifiableCollection(participants.values()); }

    public boolean isParticipant(LivingEntity e) {
        return participants.containsKey(e.getUUID());
    }

    /** Whom the boss's attacks and hazards may hit. */
    public boolean isFoe(LivingEntity e) {
        if (e == boss || !e.isAlive()) return false;
        return isParticipant(e) || Hits.hostileTo(boss, e);
    }

    /** Living things in a box (the level's, plus the fight's participants, which may not be in the level's lookup). */
    List<LivingEntity> inBox(net.minecraft.world.phys.AABB box) {
        Set<LivingEntity> out = new LinkedHashSet<>(level.getEntitiesOfClass(LivingEntity.class, box, e -> e != boss && e.isAlive() && !e.isSpectator()));
        for (LivingEntity e : participants.values()) if (e != boss && e.isAlive() && !e.isSpectator() && box.contains(e.position())) out.add(e);
        return new ArrayList<>(out);
    }

    /** Living things within a radius of a point. */
    List<LivingEntity> around(Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : inBox(new net.minecraft.world.phys.AABB(c, c).inflate(r + 2))) if (e.getBoundingBox().getCenter().distanceTo(c) <= r + e.getBbWidth() * 0.5) out.add(e);
        return out;
    }

    /** Still in the fight: alive, close, on their feet, not jailed or surrendered. */
    public boolean fighting(LivingEntity e) {
        if (!e.isAlive() || e.isRemoved() || e.level() != level || e.distanceToSqr(boss) > LEASH * LEASH) return false;
        if (e instanceof ServerPlayer sp)
            return !sp.isSpectator() && !sp.isCreative() && !Characters.get(sp).isKnockedDown() && !PrisonSystem.isJailed(sp);
        if (e instanceof GLNpc n) return !n.flag(GLNpc.FLAG_SURRENDER) && !n.hasEffect(ModEffects.KNOCKED_OUT);
        return true;
    }

    /** Someone (player or NPC ally) other than {@code except} is still fighting the boss. */
    public boolean othersFighting(@Nullable LivingEntity except) {
        for (LivingEntity e : participants.values()) if (e != except && fighting(e)) return true;
        return false;
    }

    /** Another player is still fighting the boss (what spares a downed player from the finishing blow). */
    public boolean otherPlayersFighting(@Nullable LivingEntity except) {
        for (LivingEntity e : participants.values()) if (e != except && e instanceof ServerPlayer && fighting(e)) return true;
        return false;
    }

    /** Effective health multiplier for the number of players (incoming damage is divided by it). */
    public float healthScale() {
        return 1f + 0.75f * (players - 1);
    }

    /** Damage multiplier of the boss's signature attacks and hazards. */
    public float damageScale() {
        return (1f + 0.1f * (players - 1)) * (enraged ? 1.5f : 1f) * (phase >= 3 ? 1.1f : 1f);
    }

    /**
     * Base strength of the boss's signature attacks and hazards: its tier, not its weapon (Hits adds the Armament
     * bonus on top), so that a telegraphed blow hurts badly but stays survivable at full health.
     */
    public float strike() {
        return 3f + boss.tier() * 1.1f;
    }

    /** Ticks between two signature attacks. */
    int cadence() {
        int base = phase >= 3 ? 70 : phase == 2 ? 90 : 110;
        double c = base * (enraged ? 0.6 : 1.0) * (1 - 0.07 * (players - 1));
        return Math.max(30, (int) c);
    }

    int windupFor(BossMove m) {
        double w = m.windup * (enraged ? 0.75 : 1.0) * (phase >= 3 ? 0.85 : 1.0);
        return Math.max(10, (int) Math.round(w));
    }

    public boolean hasForesight() {
        return Haki.observationLevel(boss) >= 80;
    }

    /** Advanced Observation is up: not staggered and not broken recently. */
    public boolean foresightActive(long now) {
        return hasForesight() && now >= foresightBrokenUntil && !CombatState.of(boss).staggered(now);
    }

    void breakForesight(long now) {
        if (!hasForesight()) return;
        if (foresightBrokenUntil < now) foresightBreaks++;
        foresightBrokenUntil = now + FORESIGHT_BREAK;
        Vec3 p = boss.position();
        GLNetwork.fx(boss, "boss_foresight_break", p.x, p.y + boss.getBbHeight() + 0.4, p.z, boss.getBbWidth(), 0, 0);
        actionBar(Component.translatable("grandline.boss.foresight_broken", boss.getDisplayName()).withStyle(ChatFormatting.AQUA));
    }

    /** The boss is winding up a signature attack or changing phase (its ordinary combat AI waits). */
    public boolean busy() {
        return current != null || transitioning();
    }

    // ------------------------------------------------------------------ participants and the bar

    public void engage(LivingEntity e) {
        if (e == boss || !e.isAlive() || ended) return;
        if (e instanceof ServerPlayer sp && sp.isSpectator()) return;
        participants.put(e.getUUID(), e);
        lastFoeSeen = level.getGameTime();
        if (e instanceof ServerPlayer sp && sp.distanceToSqr(boss) <= LEASH * LEASH) bar.addPlayer(sp);
    }

    /** Update participants, boss-bar viewers and the player count now (normally every half second). */
    public void refreshNow() {
        refresh(level.getGameTime());
    }

    private void refresh(long now) {
        participants.values().removeIf(e -> e.isRemoved() || !e.isAlive() || e.level() != level || e.distanceToSqr(boss) > LEASH * LEASH
                || e instanceof ServerPlayer sp && (sp.isSpectator() || sp.hasDisconnected()));
        LivingEntity t = boss.getTarget();
        if (t != null && t.isAlive() && t != boss) engage(t);
        for (ServerPlayer sp : level.players()) {
            if (participants.containsKey(sp.getUUID()) || sp.isSpectator() || sp.isCreative() || sp.distanceToSqr(boss) > JOIN_RADIUS * JOIN_RADIUS) continue;
            if (boss.isHostileTo(sp)) engage(sp);
        }
        Set<ServerPlayer> want = new HashSet<>();
        for (ServerPlayer sp : level.players()) if (sp.distanceToSqr(boss) <= BAR_RADIUS * BAR_RADIUS) want.add(sp);
        for (LivingEntity e : participants.values()) if (e instanceof ServerPlayer sp) want.add(sp);
        for (ServerPlayer sp : new ArrayList<>(bar.getPlayers())) if (!want.contains(sp) || sp.hasDisconnected()) bar.removePlayer(sp);
        for (ServerPlayer sp : want) bar.addPlayer(sp);
        int n = 0;
        for (LivingEntity e : participants.values())
            if (e instanceof ServerPlayer sp && !sp.isCreative() && sp.distanceToSqr(boss) <= BAR_RADIUS * BAR_RADIUS) n++;
        players = Math.max(1, Math.min(4, n));
    }

    private Component barName() {
        var n = NotableCharacters.get(def.id);
        Component name = boss.getDisplayName(), title = n == null ? Component.empty() : NotableCharacters.title(n);
        return Component.translatable(enraged ? "grandline.boss.bar.enraged" : "grandline.boss.bar", name, title);
    }

    void updateBar() {
        bar.setName(barName());
        bar.setColor(enraged || phase >= 3 ? BossEvent.BossBarColor.RED : phase == 2 ? BossEvent.BossBarColor.YELLOW : def.color);
        bar.setDarkenScreen(enraged);
        bar.setProgress(Math.max(0f, Math.min(1f, boss.getHealth() / boss.getMaxHealth())));
    }

    /** A line for everyone in the fight (chat). */
    void say(Component c) {
        Set<ServerPlayer> to = new HashSet<>(bar.getPlayers());
        for (LivingEntity e : participants.values()) if (e instanceof ServerPlayer sp) to.add(sp);
        for (ServerPlayer sp : to) sp.sendSystemMessage(c);
    }

    void actionBar(Component c) {
        Set<ServerPlayer> to = new HashSet<>(bar.getPlayers());
        for (LivingEntity e : participants.values()) if (e instanceof ServerPlayer sp) to.add(sp);
        for (ServerPlayer sp : to) sp.displayClientMessage(c, true);
    }

    // ------------------------------------------------------------------ tick

    void tick(long now) {
        if (ended) return;
        if (!boss.isAlive() || boss.isRemoved()) {
            end(false);
            return;
        }
        if (boss.flag(GLNpc.FLAG_SURRENDER)) { // a rival who yields has been bested
            BossRewards.defeated(boss, def, boss.getLastHurtByMob(), "yield", this);
            end(false);
            return;
        }
        if ((now - start) % 10 == 0) refresh(now);
        bar.setProgress(Math.max(0f, Math.min(1f, boss.getHealth() / boss.getMaxHealth())));
        BossCombat.resist(this, now);
        boolean anyone = othersFighting(null);
        if (anyone) lastFoeSeen = now;
        else if (now - lastFoeSeen > RESET_TICKS && (boss.getTarget() == null || !fighting(boss.getTarget()))) {
            reset();
            return;
        }
        // foresight: broken by a stagger
        boolean staggered = CombatState.of(boss).staggered(now);
        if (staggered && !wasStaggered) breakForesight(now);
        wasStaggered = staggered;
        if (!enraged && now - start >= def.enrageTicks) enrage();
        retarget();
        if (!deferred.isEmpty() && !otherPlayersFighting(null)) releaseDeferred();
        // phase transition
        if (pendingPhase > 0) {
            holdStill();
            if (now >= transitionUntil) completeTransition(now);
            else return;
        }
        // telegraphs in flight
        for (Telegraph t : new ArrayList<>(telegraphs)) {
            t.tickFx(now);
            if (now >= t.hitAt) t.resolve();
        }
        telegraphs.removeIf(Telegraph::done);
        if (current != null) {
            if (current.done()) current = null;
            else holdStill();
        }
        // the arena
        if ((now - start) % 10 == 0) {
            for (BossArena.Hazard h : new ArrayList<>(hazards)) h.tick(this, now);
            hazards.removeIf(h -> now >= h.until);
        }
        if ((now - start) % 20 == 0) def.arena.tick(this, now);
        BossHaki.watchClashes(this, now);
        // the next signature attack
        if (current == null && now >= nextMoveAt) {
            LivingEntity t = boss.getTarget();
            if (t != null && t.isAlive() && t.level() == level && isFoe(t) && !staggered && !boss.hasEffect(ModEffects.KNOCKED_OUT)) chooseMove(t, now);
        }
        if (current == null) root(false);
    }

    /** Never stand over a downed player while someone else is still fighting: move on to the next foe. */
    private void retarget() {
        LivingEntity t = boss.getTarget();
        if (t == null || fighting(t) || !othersFighting(t)) return;
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : participants.values()) {
            if (e == t || !fighting(e)) continue;
            double d = e.distanceToSqr(boss);
            if (d < best) {
                best = d;
                next = e;
            }
        }
        if (next != null) boss.setTarget(next);
    }

    private void releaseDeferred() {
        List<ServerPlayer> list = new ArrayList<>(deferred.values());
        deferred.clear();
        for (ServerPlayer sp : list) if (sp.isAlive() && Characters.get(sp).isKnockedDown()) boss.onKnockedDownPlayer(sp);
    }

    private void enrage() {
        enraged = true;
        Vec3 p = boss.position();
        GLNetwork.fx(boss, "boss_enrage", p.x, p.y + 1, p.z, boss.getBbWidth() + 2, 0, 0);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
        say(Component.translatable("grandline.boss.enraged", boss.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        updateBar();
    }

    // ------------------------------------------------------------------ phases

    /** Health crossed a threshold: a short invulnerable roar, then the next phase. */
    void beginTransition(int next) {
        if (next <= phase || next <= pendingPhase || next > def.phaseCount()) return;
        long now = level.getGameTime();
        pendingPhase = next;
        transitionUntil = now + TRANSITION_TICKS;
        telegraphs.removeIf(t -> t.own);
        current = null;
        holdStill();
        GLNetwork.anim(boss, "roar");
        Vec3 p = boss.position();
        level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5f, 0.8f);
        GLNetwork.fx(boss, "boss_roar", p.x, p.y + 0.2, p.z, (float) (8 + boss.getBbWidth()), next, 0);
        say(Component.translatable("grandline.boss.says", boss.getDisplayName(), Component.translatable(def.phaseLineKey(next))).withStyle(ChatFormatting.GOLD));
        BossHaki.phaseBurst(this, now);
        updateBar();
    }

    private void completeTransition(long now) {
        phase = pendingPhase;
        pendingPhase = 0;
        transitionUntil = -1;
        root(false);
        enterPhase(phase);
        nextMoveAt = now + 20;
        updateBar();
    }

    /** Phase powers (existing abilities), phase actions and the arena change. */
    void enterPhase(int p) {
        for (String id : def.phasePowers(p)) BossManager.runAbility(this, id, boss.getTarget());
        var action = def.phaseActions.get(p);
        if (action != null) action.accept(this);
        def.arena.onPhase(this, p);
    }

    // ------------------------------------------------------------------ signature attacks

    private void chooseMove(LivingEntity target, long now) {
        double dist = Math.sqrt(boss.distanceToSqr(target)) - boss.getBbWidth() * 0.5;
        List<BossMove> options = new ArrayList<>();
        double total = 0;
        for (BossMove m : def.movesFor(phase)) {
            if (moveReady.getOrDefault(m.id(), 0L) > now || dist > m.range) continue;
            options.add(m);
            total += m.id().equals(lastMove) ? 0.35 : 1.0;
        }
        if (options.isEmpty()) {
            nextMoveAt = now + 20;
            return;
        }
        double roll = boss.getRandom().nextDouble() * total;
        BossMove pick = options.get(options.size() - 1);
        for (BossMove m : options) {
            roll -= m.id().equals(lastMove) ? 0.35 : 1.0;
            if (roll <= 0) {
                pick = m;
                break;
            }
        }
        startMove(pick, target, null, true);
    }

    /** Start a telegraphed attack. {@code at} overrides the marker centre (tests, arena events). */
    public Telegraph startMove(BossMove m, @Nullable LivingEntity target, @Nullable Vec3 at, boolean own) {
        return startMove(m, target, at, own, null);
    }

    /** As above, with the attack coming from somewhere else than the boss (arena beams crossing the field). */
    public Telegraph startMove(BossMove m, @Nullable LivingEntity target, @Nullable Vec3 at, boolean own, @Nullable Vec3 from) {
        long now = level.getGameTime();
        Vec3 origin = from != null ? ground(from.x, from.z, from.y) : boss.position();
        Vec3 aim = at != null ? at : target != null ? target.position() : origin.add(boss.getLookAngle().scale(4));
        Vec3 dir = new Vec3(aim.x - origin.x, 0, aim.z - origin.z);
        if (dir.lengthSqr() < 1e-4) dir = boss.getLookAngle().multiply(1, 0, 1);
        List<Vec3> centers = new ArrayList<>();
        switch (m.shape) {
            case CIRCLE, CROSS -> centers.add(at != null ? at : target != null ? target.position() : aim);
            case RAIN -> {
                if (at != null) centers.add(at);
                else for (LivingEntity e : participants.values()) if (centers.size() < 6 && fighting(e) && e.distanceToSqr(boss) < 30 * 30) centers.add(e.position());
                if (centers.isEmpty() && target != null) centers.add(target.position());
                for (int i = 0; i < m.extra; i++) {
                    double a = boss.getRandom().nextDouble() * Math.PI * 2, d = 4 + boss.getRandom().nextDouble() * 7;
                    centers.add(ground(origin.x + Math.cos(a) * d, origin.z + Math.sin(a) * d, origin.y));
                }
            }
            default -> {}
        }
        int windup = windupFor(m);
        Telegraph t = new Telegraph(this, m, own, origin, dir, centers, now, windup);
        telegraphs.add(t);
        t.windupSound();
        if (own) {
            current = t;
            lastMove = m.id();
            movesStarted++;
            moveReady.put(m.id(), now + m.cooldown);
            CombatState s = CombatState.of(boss);
            s.nextAttackAllowed = Math.max(s.nextAttackAllowed, t.hitAt + 4);
            s.blocking = false;
            GLNetwork.anim(boss, m.windAnim);
            face(dir);
            holdStill();
            actionBar(Component.translatable("grandline.boss.move", boss.getDisplayName(), Component.translatable(m.nameKey())).withStyle(ChatFormatting.RED));
        }
        return t;
    }

    /** Ground height near a point (markers sit on the floor; falls back to the boss's level on cliffs and at sea). */
    Vec3 ground(double x, double z, double fallback) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        return new Vec3(x, Math.abs(h - fallback) <= 6 ? h : fallback, z);
    }

    void onResolved(Telegraph t) {
        if (t == current) {
            current = null;
            nextMoveAt = level.getGameTime() + cadence();
            root(false);
        }
    }

    /** Resolve every pending attack now (tests). */
    public void releaseAll() {
        for (Telegraph t : new ArrayList<>(telegraphs)) t.resolve();
        telegraphs.removeIf(Telegraph::done);
        if (current != null && current.done()) current = null;
    }

    private void face(Vec3 dir) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
    }

    private void holdStill() {
        boss.getNavigation().stop();
        root(true);
    }

    private void root(boolean on) {
        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on) speed.addOrUpdateTransientModifier(new AttributeModifier(ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (speed.hasModifier(ROOT)) speed.removeModifier(ROOT);
    }

    // ------------------------------------------------------------------ the end

    /** Everyone left or fell: the boss recovers and the next fight starts from the top. */
    private void reset() {
        if (boss.isAlive()) {
            boss.setHealth(boss.getMaxHealth());
            CombatState.of(boss).buffs.remove("hardening");
        }
        end(false);
    }

    void end(boolean unused) {
        if (ended) return;
        ended = true;
        bar.removeAllPlayers();
        root(false);
        telegraphs.clear();
        hazards.clear();
        current = null;
        BossManager.forget(this);
    }

    // ------------------------------------------------------------------ test hooks

    /** Pretend the fight started this many ticks ago. */
    public void ageForTest(long ticks) {
        start -= ticks;
    }

    /** Pretend nobody has fought the boss for this many ticks. */
    public void idleForTest(long ticks) {
        lastFoeSeen -= ticks;
    }

    public void tickForTest() {
        tick(level.getGameTime());
    }

    /** Jump to a phase as if its transition had just finished (phase powers, actions and arena change included). */
    public void forcePhaseForTest(int p) {
        phase = Math.max(1, Math.min(def.phaseCount(), p));
        pendingPhase = 0;
        transitionUntil = -1;
        enterPhase(phase);
        updateBar();
    }

    /** Run the arena script as if {@code seconds} of the fight had passed (each second once). */
    public void arenaTickForTest(int seconds) {
        for (int s = 1; s <= seconds; s++) def.arena.tick(this, start + s * 20L);
    }

    public void tickHazardsForTest() {
        long now = level.getGameTime();
        for (BossArena.Hazard h : new ArrayList<>(hazards)) h.tick(this, now);
    }

    public int deferredCount() {
        return deferred.size();
    }

    public long foresightBrokenUntil() {
        return foresightBrokenUntil;
    }
}
