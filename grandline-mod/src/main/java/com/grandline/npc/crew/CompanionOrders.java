package com.grandline.npc.crew;

import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.Characters;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The captain's orders.
 * <ul>
 * <li><b>Follow</b> (default) and <b>Hold</b> (stay here; fight only what comes within a few blocks of the spot),
 * per companion.</li>
 * <li><b>Focus</b>: every active companion attacks the foe the captain is looking at (or, failing that, the captain's
 * lock-on target or whatever the captain is hitting). Never a neutral: companions refuse.</li>
 * <li><b>Regroup</b>: break off now and come back to the captain (also calls back those holding a position); a
 * companion far away and out of sight is brought over.</li>
 * <li><b>Swap</b> (and moving one companion to reserve or back): only at a safe place (in a settlement, or aboard
 * the captain's ship) and not in a fight. At most {@link CrewManager#ACTIVE_LIMIT} are active.</li>
 * </ul>
 * Keys: V opens the roster (client CrewScreen), N is the quick order (looking at a foe: focus it; otherwise regroup).
 * Every order is answered by the companions with a short line.
 */
public final class CompanionOrders {
    public static final double FOCUS_RANGE = 32;
    public static final int FOCUS_TICKS = 400, REGROUP_TICKS = 200;

    private CompanionOrders() {}

    // ================================================================== standing orders

    /** Follow or hold, from the dialogue or the roster. Reserve members take no field orders. */
    public static boolean setOrder(ServerPlayer sp, GLNpc npc, CrewManager.Order o, boolean ack) {
        if (!CrewManager.isCrewOf(npc, sp)) return false;
        CrewManager.Member m = CrewManager.memberOf(npc);
        return m != null && setOrder(sp, m, o, npc instanceof HumanoidNpc h ? h : null, ack);
    }

    static boolean setOrder(ServerPlayer sp, CrewManager.Member m, CrewManager.Order o, @Nullable HumanoidNpc body, boolean ack) {
        if (!m.active) {
            sp.sendSystemMessage(Component.translatable("grandline.crew.in_reserve", name(m.id)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        m.order = o;
        m.regroupUntil = 0;
        if (o == CrewManager.Order.HOLD) {
            m.hold = body != null ? body.blockPosition().asLong() : 0;
            m.focusId = -1;
        } else m.hold = 0;
        if (body != null) {
            body.setAllegiance(Characters.get(sp).faction, o == CrewManager.Order.HOLD ? body.blockPosition() : BlockPos.ZERO);
            body.getNavigation().stop();
            if (ack) Companions.ack(body, sp, o.id());
        }
        CrewManager.data(sp.server).setDirty();
        return true;
    }

    public static boolean order(ServerPlayer sp, String id, CrewManager.Order o) {
        CrewManager.Member m = CrewManager.member(sp, id);
        return m != null && setOrder(sp, m, o, CrewManager.body(sp.server, id), true);
    }

    // ================================================================== focus and regroup

    /** A foe of the captain's party (judged as the active companions would judge it). */
    public static boolean partyFoe(ServerPlayer sp, LivingEntity t) {
        List<HumanoidNpc> bodies = Companions.activeBodies(sp);
        if (!bodies.isEmpty()) return Companions.isFoe(bodies.get(0), t);
        if (t instanceof Mob m && m.getTarget() == sp) return true;
        if (t instanceof GLNpc g) return g.isHostileTo(sp);
        return t instanceof Enemy;
    }

    /**
     * Every active companion attacks this foe ({@code null}: the one the captain is looking at, else the captain's
     * lock-on target or current victim).
     */
    public static boolean focus(ServerPlayer sp, @Nullable LivingEntity target) {
        if (target == null) target = Companions.lookedAt(sp, FOCUS_RANGE);
        if (target == null) target = Companions.captainTarget(sp);
        if (target == null) {
            sp.displayClientMessage(Component.translatable("grandline.crew.no_target").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!partyFoe(sp, target)) {
            sp.displayClientMessage(Component.translatable("grandline.crew.not_a_foe", target.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        CrewManager.Crew c = CrewManager.crewOf(sp);
        if (c == null) return false;
        long now = sp.level().getGameTime();
        boolean any = false;
        for (CrewManager.Member m : c.members.values()) {
            if (!m.active || m.downed) continue;
            m.focusId = target.getId();
            m.focusUntil = now + FOCUS_TICKS;
            m.regroupUntil = 0;
            HumanoidNpc b = CrewManager.body(sp.server, m.id);
            if (b == null || b.level() != sp.level()) continue;
            if (Companions.isFoe(b, target)) b.setTarget(target);
            Companions.ack(b, sp, "focus");
            any = true;
        }
        return any;
    }

    /** Break off and come back now (those holding a position too). */
    public static boolean regroup(ServerPlayer sp) {
        CrewManager.Crew c = CrewManager.crewOf(sp);
        if (c == null) return false;
        long now = sp.level().getGameTime();
        boolean any = false;
        for (CrewManager.Member m : c.members.values()) {
            if (!m.active) continue;
            m.focusId = -1;
            m.order = CrewManager.Order.FOLLOW;
            m.hold = 0;
            m.regroupUntil = now + REGROUP_TICKS;
            HumanoidNpc b = CrewManager.body(sp.server, m.id);
            any = true;
            if (b == null) continue; // aboard or far away: the crew tick brings them as soon as the captain stands on solid ground
            b.setTarget(null);
            b.getNavigation().stop();
            b.setAllegiance(Characters.get(sp).faction, BlockPos.ZERO);
            if (b.level() == sp.level() && b.distanceToSqr(sp) > 16 * 16 && !com.grandline.combat.Cover.canSee(sp, b) && !m.downed) Companions.teleportNear(b, sp);
            if (!m.downed) Companions.ack(b, sp, "regroup");
        }
        CrewManager.data(sp.server).setDirty();
        return any;
    }

    /** The quick-order key: looking at a foe -> focus it; otherwise -> regroup. */
    public static String quick(ServerPlayer sp) {
        LivingEntity t = Companions.lookedAt(sp, FOCUS_RANGE);
        if (t != null && partyFoe(sp, t) && focus(sp, t)) return "focus";
        regroup(sp);
        return "regroup";
    }

    // ================================================================== who is active

    /** Changing who is active needs a safe place and no fight. */
    public static boolean canChangeActive(ServerPlayer sp, boolean tell) {
        if (Companions.inCombat(sp) || Companions.detectCombat(sp)) {
            if (tell) sp.sendSystemMessage(Component.translatable("grandline.crew.swap_combat").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!Companions.safePlace(sp)) {
            if (tell) sp.sendSystemMessage(Component.translatable("grandline.crew.swap_unsafe").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /** Send an active companion to the reserve (they wait here, in this safe place). */
    public static boolean toReserve(ServerPlayer sp, String id) {
        CrewManager.Member m = CrewManager.member(sp, id);
        if (m == null || !m.active || !canChangeActive(sp, true)) return false;
        deactivate(sp, m);
        sp.sendSystemMessage(Component.translatable("grandline.crew.to_reserve", name(id)).withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** Bring a reserve member into a free active slot. */
    public static boolean join(ServerPlayer sp, String id) {
        CrewManager.Crew c = CrewManager.crewOf(sp);
        CrewManager.Member m = c == null ? null : c.members.get(id);
        if (m == null || m.active || !canChangeActive(sp, true)) return false;
        if (c.activeCount() >= CrewManager.ACTIVE_LIMIT) {
            sp.sendSystemMessage(Component.translatable("grandline.crew.active_full", CrewManager.ACTIVE_LIMIT).withStyle(ChatFormatting.RED));
            return false;
        }
        activate(sp, m);
        sp.sendSystemMessage(Component.translatable("grandline.crew.to_active", name(id)).withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** Swap an active companion for a reserve one. */
    public static boolean swap(ServerPlayer sp, String outId, String inId) {
        CrewManager.Crew c = CrewManager.crewOf(sp);
        if (c == null) return false;
        CrewManager.Member out = c.members.get(outId), in = c.members.get(inId);
        if (out == null || in == null || !out.active || in.active || !canChangeActive(sp, true)) return false;
        deactivate(sp, out);
        activate(sp, in);
        sp.sendSystemMessage(Component.translatable("grandline.crew.swapped", name(outId), name(inId)).withStyle(ChatFormatting.AQUA));
        return true;
    }

    private static void deactivate(ServerPlayer sp, CrewManager.Member m) {
        m.active = false;
        m.order = CrewManager.Order.FOLLOW;
        m.hold = 0;
        m.focusId = -1;
        m.regroupUntil = 0;
        HumanoidNpc b = CrewManager.body(sp.server, m.id);
        if (b != null) {
            b.setTarget(null);
            b.getNavigation().stop();
            b.setAllegiance(Characters.get(sp).faction, b.blockPosition()); // waits right here
            m.snapshot = CrewManager.save(b);
            say(b, sp, "rest");
        }
        CrewManager.data(sp.server).setDirty();
    }

    private static void activate(ServerPlayer sp, CrewManager.Member m) {
        m.active = true;
        m.order = CrewManager.Order.FOLLOW;
        m.hold = 0;
        m.missing = 0;
        m.focusId = -1;
        long now = sp.level().getGameTime();
        m.regroupUntil = now + REGROUP_TICKS;
        CrewManager.CrewData cd = CrewManager.data(sp.server);
        NotableState st = NotableState.get(sp.server);
        HumanoidNpc b = CrewManager.body(sp.server, m.id);
        if (b != null) {
            b.setAllegiance(Characters.get(sp).faction, BlockPos.ZERO);
            if (b.level() != sp.level()) CrewManager.stow(cd, m, b); // steps off next to the captain on the next crew tick
            else if (b.distanceToSqr(sp) > 24 * 24) Companions.teleportNear(b, sp);
            if (!b.isRemoved()) say(b, sp, "join");
        } else if (m.stowed != null) {
            CrewManager.disembark(cd, st, sp, m, m.stowed, false);
        } else if (m.snapshot != null) {
            // left in a place that is not loaded now: rebuilt next to the captain (the old body is refused when it loads)
            CrewManager.disembark(cd, st, sp, m, m.snapshot, true);
        }
        HumanoidNpc nb = CrewManager.body(sp.server, m.id);
        if (b == null && nb != null) say(nb, sp, "join");
        cd.setDirty();
    }

    private static void say(HumanoidNpc b, ServerPlayer sp, String order) {
        Companions.ack(b, sp, order);
    }

    static Component name(String id) {
        var n = NotableCharacters.get(id);
        return n == null ? Component.literal(id) : NotableCharacters.name(n);
    }

    // ================================================================== dialogue and the roster screen

    /** The crew options when talking to one of your own companions. */
    public static void dialogueOptions(ServerPlayer sp, GLNpc npc, ListTag opts) {
        CrewManager.Member m = CrewManager.memberOf(npc);
        if (m == null) return;
        if (m.active) {
            opts.add(StringTag.valueOf(m.holding() ? "crew_follow" : "crew_wait"));
            opts.add(StringTag.valueOf("crew_rest"));
        } else opts.add(StringTag.valueOf("crew_join"));
        opts.add(StringTag.valueOf("crew_dismiss"));
    }

    public static void openRoster(ServerPlayer sp) {
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("crew", rosterTag(sp)));
    }

    /** Everything the roster screen shows: per member its role, health, bond and whether it is active. */
    public static CompoundTag rosterTag(ServerPlayer sp) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("safe", Companions.safePlace(sp));
        t.putBoolean("combat", Companions.inCombat(sp) || Companions.detectCombat(sp));
        t.putInt("activeMax", CrewManager.ACTIVE_LIMIT);
        t.putInt("max", CrewManager.MAX_CREW);
        ListTag list = new ListTag();
        CrewManager.Crew c = CrewManager.crewOf(sp);
        if (c != null) {
            for (CrewManager.Member m : c.members.values()) {
                var n = NotableCharacters.get(m.id);
                CompanionRole role = CompanionRole.of(n);
                CompoundTag mt = new CompoundTag();
                mt.putString("id", m.id);
                mt.putString("name", n == null ? m.id : n.nameKey());
                mt.putString("role", role.id());
                mt.putString("job", n == null ? "" : n.crewRoleKey());
                mt.putBoolean("active", m.active);
                mt.putString("order", m.order.id());
                mt.putBoolean("downed", m.downed);
                mt.putInt("rank", m.bondRank());
                mt.putInt("bond", m.bond);
                mt.putInt("next", CrewManager.toNextRank(m.bond));
                mt.putString("technique", role.techniqueKey());
                HumanoidNpc b = CrewManager.body(sp.server, m.id);
                float hp, max;
                String where;
                if (b != null) {
                    hp = b.getHealth();
                    max = b.getMaxHealth();
                    where = b.level() == sp.level() && b.distanceToSqr(sp) < 48 * 48 ? "here" : "away";
                } else {
                    CompoundTag body = m.stowed != null ? m.stowed : m.snapshot;
                    max = com.grandline.balance.CompanionTuning.health(Math.max(1, m.capLevel), m.bondRank(), role);
                    hp = body != null && body.contains("Health") ? Math.min(max, body.getFloat("Health")) : max;
                    where = m.stowed != null ? "aboard" : "away";
                }
                mt.putFloat("hp", hp);
                mt.putFloat("maxHp", max);
                mt.putString("where", where);
                list.add(mt);
            }
        }
        t.put("members", list);
        return t;
    }

    /** UI actions from the roster screen and the order keys ({@code UiAction("crew", ...)}). */
    public static void handle(ServerPlayer sp, CompoundTag t) {
        String op = t.getString("op"), id = t.getString("id");
        switch (op) {
            case "quick" -> quick(sp);
            case "focus" -> focus(sp, null);
            case "regroup" -> regroup(sp);
            case "follow" -> order(sp, id, CrewManager.Order.FOLLOW);
            case "hold" -> order(sp, id, CrewManager.Order.HOLD);
            case "rest" -> toReserve(sp, id);
            case "join" -> join(sp, id);
            case "swap" -> swap(sp, t.getString("out"), id);
            default -> {}
        }
        if (op.equals("roster") || t.getBoolean("screen")) openRoster(sp);
    }

    /** Ids in the roster order: active first, then reserve (tests and the screen). */
    public static List<String> ordered(ServerPlayer sp) {
        List<String> out = new ArrayList<>();
        CrewManager.Crew c = CrewManager.crewOf(sp);
        if (c == null) return out;
        for (CrewManager.Member m : c.members.values()) if (m.active) out.add(m.id);
        for (CrewManager.Member m : c.members.values()) if (!m.active) out.add(m.id);
        return out;
    }
}
