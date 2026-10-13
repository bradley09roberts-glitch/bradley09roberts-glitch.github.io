package com.grandline.test;

import com.grandline.balance.CompanionTuning;
import com.grandline.balance.Tuning;
import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Cover;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.fruit.FruitWorldData;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.crew.CompanionCare;
import com.grandline.npc.crew.CompanionCredit;
import com.grandline.npc.crew.CompanionOrders;
import com.grandline.npc.crew.CompanionRole;
import com.grandline.npc.crew.Companions;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.player.Race;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.*;

import static com.grandline.test.SelfTest.check;

/**
 * Crew companions (GL_SELFTEST=crew): roles, the active limit, swaps only at a safe place, orders, never attacking a
 * neutral, kill credit, downed and revived, the healing limit, no friendly fire, cover, save compatibility, no
 * duplicate bodies, and a measured contribution (ticks to fell a standard foe alone and with two companions).
 * Everything runs on the sky platform above Shiokaze Isle with a fake captain registered as online.
 */
final class CrewTests {
    private static final UUID CAP_ID = UUID.fromString("c7e3a1f0-5b2d-4e8c-9a11-7d0c4e7a2b91");
    private static final List<String> TRIO = List.of("petra", "carmen", "kenji");
    private static final Map<String, NotableState.Entry> SAVED = new LinkedHashMap<>();
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static final List<BlockPos> BUILT = new ArrayList<>();
    private static BlockPos C, P2;
    // values carried between steps
    private static final float[] F = new float[8];
    private static final long[] L = new long[8];
    private static final boolean[] B = new boolean[8];
    private static final Object[] O = new Object[8];
    private static final int[] N = new int[4];
    private static final StringBuilder TIMELINE = new StringBuilder();

    private CrewTests() {}

    // ================================================================== helpers

    static FakePlayer captain(MinecraftServer s) {
        FakePlayer fp = FakePlayerFactory.get(s.overworld(), new GameProfile(CAP_ID, "CrewCaptain"));
        CharacterData d = Characters.get(fp);
        if (!d.created) {
            d.created = true;
            d.name = "CrewCaptain";
            d.race = Race.HUMAN;
            d.level = 16;
            d.faction = Faction.PIRATE;
            d.knownStyles.add("brawler");
        }
        fp.setGameMode(GameType.SURVIVAL);
        return fp;
    }

    private static void putCaptain(FakePlayer fp, double dx, double dz, float yaw) {
        fp.moveTo(C.getX() + dx + 0.5, C.getY(), C.getZ() + dz + 0.5, yaw, 0);
        fp.setOnGround(true);
        fp.setDeltaMovement(Vec3.ZERO);
    }

    private static void place(LivingEntity e, BlockPos at) {
        e.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof HumanoidNpc h) h.getNavigation().stop();
    }

    private static void lookAt(FakePlayer fp, Entity t) {
        Vec3 to = t.getBoundingBox().getCenter().subtract(fp.getEyePosition());
        fp.setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f);
        fp.setXRot((float) (-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG)));
        fp.setYHeadRot(fp.getYRot());
    }

    /** A story companion as the spawner makes it, standing on the platform (its world entry is restored at the end). */
    private static HumanoidNpc companion(ServerLevel l, String id, BlockPos at) {
        MinecraftServer s = l.getServer();
        NotableCharacters.Notable n = NotableCharacters.get(id);
        NotableState st = NotableState.get(s);
        if (!SAVED.containsKey(id)) {
            NotableState.Entry cur = st.entry(id), c = new NotableState.Entry();
            c.status = cur.status;
            c.entity = cur.entity;
            c.fate = cur.fate;
            c.since = cur.since;
            SAVED.put(id, c);
        }
        HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(l);
        npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        npc.configure(n.role(), n.tier(), "", at, "", l.random);
        if (npc.fruitId() != null) FruitWorldData.get(s).release(s, npc.fruitId(), npc.getUUID());
        npc.makeNotable(id, null, n.obs(), n.arm(), n.coq(), n.style());
        npc.setNotableBody(n.tier(), n.race());
        npc.setAllegiance(n.faction(), at);
        npc.retune();
        npc.setHealth(npc.getMaxHealth());
        Item w = n.weapon().isEmpty() ? Items.AIR : BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("grandline", n.weapon()));
        npc.setItemSlot(EquipmentSlot.MAINHAND, w == Items.AIR ? ItemStack.EMPTY : new ItemStack(w));
        npc.setCustomName(NotableCharacters.name(n));
        st.set(id, NotableState.Status.ALIVE, npc.getUUID(), "", l.getGameTime());
        l.addFreshEntity(npc);
        return npc;
    }

    /** A foe standing still (no AI) that is going for the captain. */
    private static HumanoidNpc foe(ServerLevel l, String role, int tier, BlockPos at, FakePlayer cap, float health) {
        HumanoidNpc n = SelfTest.npc(l, role, tier, at);
        n.setNoAi(true);
        n.clearHakiForTest();
        if (n.fruitId() != null) {
            FruitWorldData.get(l.getServer()).release(l.getServer(), n.fruitId(), n.getUUID());
            n.forceFruitForTest("");
        }
        if (health > 0) {
            n.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            n.setHealth(health);
        }
        n.setTarget(cap);
        SPAWNED.add(n);
        return n;
    }

    private static HumanoidNpc body(MinecraftServer s, String id) {
        return CrewManager.body(s, id);
    }

    private static void build(ServerLevel l, BlockPos p) {
        l.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        BUILT.add(p);
    }

    private static int bodies(MinecraftServer s, String id) {
        int n = 0;
        for (ServerLevel l : s.getAllLevels())
            for (Entity e : l.getAllEntities()) if (e instanceof GLNpc g && g.isAlive() && id.equals(g.notableId())) n++;
        return n;
    }

    private static String pos(Entity e) {
        return e == null ? "none" : String.format("(%.1f %.1f %.1f)", e.getX() - C.getX(), e.getY() - C.getY(), e.getZ() - C.getZ());
    }

    private static String name(LivingEntity e) {
        return e == null ? "none" : e.getName().getString();
    }

    // ================================================================== the steps

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- set up: a captain and the three early companions on the platform ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            C = SelfTest.arena(l);
            FakePlayer fp = captain(s);
            CrewManager.disband(s, Characters.get(fp).characterId);
            CrewManager.testCaptain(fp, true);
            putCaptain(fp, 0, -6, 0);
            companion(l, "petra", C.offset(-4, 0, -2));
            companion(l, "carmen", C.offset(2, 0, -4));
            companion(l, "kenji", C.offset(4, 0, 0));
            return 5;
        });

        // ---------- recruiting: roles, the active limit, the numbers ----------
        steps.add(s -> {
            FakePlayer fp = captain(s);
            UUID cid = Characters.get(fp).characterId;
            HumanoidNpc petra = body(s, "petra"), carmen = body(s, "carmen"), kenji = body(s, "kenji");
            boolean a = petra != null && CrewManager.recruit(fp, petra), b = carmen != null && CrewManager.recruit(fp, carmen),
                    k = kenji != null && CrewManager.recruit(fp, kenji);
            boolean roles = a && b && k && CompanionRole.of(petra) == CompanionRole.RANGED && CompanionRole.of(carmen) == CompanionRole.SUPPORT
                    && CompanionRole.of(kenji) == CompanionRole.FIGHTER;
            check("crew: recruit three companions with their roles (Petra marksman, Carmen support, Kenji fighter)", roles,
                    "joined " + a + "/" + b + "/" + k + (roles ? "" : ", roles " + (petra == null ? "?" : CompanionRole.of(petra)) + "/" + (carmen == null ? "?" : CompanionRole.of(carmen))
                            + "/" + (kenji == null ? "?" : CompanionRole.of(kenji))));
            List<String> act = CrewManager.activeIds(s, cid);
            CrewManager.Member km = CrewManager.member(fp, "kenji");
            check("crew: at most two active; the third recruit waits in reserve (roster limit stays " + CrewManager.MAX_CREW + ")",
                    act.equals(List.of("petra", "carmen")) && km != null && !km.active() && CrewManager.members(s, cid).size() == 3 && CrewManager.MAX_CREW == 18,
                    "active " + act + ", roster " + CrewManager.members(s, cid));
            if (kenji != null) {
                float ref = CompanionTuning.referenceBlowAt(16), pw = Tuning.npcPower(kenji);
                float hp = kenji.getMaxHealth(), want = CompanionTuning.health(16, 1, CompanionRole.FIGHTER);
                check("crew: companion numbers come from the captain's level (40 % of the reference blow), never an elite rank",
                        Tuning.rank(kenji) == Tuning.Rank.STANDARD && Math.abs(pw - 0.4f * ref) < 0.01f && Math.abs(hp - want) < 0.1f,
                        String.format("rank %s, power %.2f vs reference blow %.2f, health %.1f (want %.1f, reference %.1f); elite power would be %.2f",
                                Tuning.rank(kenji), pw, ref, hp, want, CompanionTuning.referenceHealthAt(16), Tuning.Rank.ELITE.blowShare * Tuning.referenceHealth(kenji.tier()) * 1.1f));
            }
            StringBuilder table = new StringBuilder();
            for (int lv : new int[]{1, 8, 16, 26, 46, 68, 92})
                table.append(String.format("L%d: blow %.1f/%.1f hp F%.0f R%.0f S%.0f; ", lv, CompanionTuning.power(lv, 1), CompanionTuning.referenceBlowAt(lv),
                        CompanionTuning.health(lv, 1, CompanionRole.FIGHTER), CompanionTuning.health(lv, 1, CompanionRole.RANGED), CompanionTuning.health(lv, 1, CompanionRole.SUPPORT)));
            check("crew: companion numbers by captain level (companion blow / reference blow, health by role)", CompanionTuning.power(92, 5) < CompanionTuning.referenceBlowAt(92),
                    table.toString() + "rank 5: +" + Math.round(CompanionTuning.BOND_POWER * 400) + "% blows, +" + Math.round(CompanionTuning.BOND_HEALTH * 400) + "% health");
            return 20;
        });

        // ---------- the support's songs: a quarter of the most hurt ally's health, 30 s apart, three per fight ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            HumanoidNpc carmen = body(s, "carmen"), petra = body(s, "petra");
            putCaptain(fp, 0, -6, 0);
            place(carmen, C.offset(2, 0, -6));
            place(petra, C.offset(-2, 0, -8));
            HumanoidNpc f = foe(l, "bandit", 1, C.offset(9, 0, 9), fp, 400);
            Companions.endFightForTest(fp);
            Companions.refreshFight(fp); // a foe is going for the captain: a new fight
            boolean fight = Companions.inCombat(fp);
            float max = fp.getMaxHealth();
            long now = l.getGameTime();
            fp.setHealth(max * 0.3f);
            float h0 = fp.getHealth();
            boolean h1 = CompanionCare.trySupportHeal(carmen, fp, now);
            float gain = fp.getHealth() - h0;
            boolean cooldown = CompanionCare.trySupportHeal(carmen, fp, now + 20);
            fp.setHealth(max * 0.3f);
            boolean h2 = CompanionCare.trySupportHeal(carmen, fp, now + 600);
            fp.setHealth(max * 0.3f);
            boolean h3 = CompanionCare.trySupportHeal(carmen, fp, now + 1200);
            fp.setHealth(max * 0.3f);
            boolean h4 = CompanionCare.trySupportHeal(carmen, fp, now + 1800);
            boolean petraHeals = CompanionCare.trySupportHeal(petra, fp, now + 2400);
            check("crew: support heal restores 25 % of the most hurt ally, 30 s cooldown, at most 3 per fight; a marksman does not heal",
                    fight && h1 && Math.abs(gain - max * 0.25f) < 0.01f && !cooldown && h2 && h3 && !h4 && !petraHeals,
                    "fight=" + fight + " heals " + h1 + "/" + cooldown + "(cooldown)/" + h2 + "/" + h3 + "/" + h4 + "(4th) gain " + gain + " of " + max + ", marksman " + petraHeals);
            Companions.endFightForTest(fp);
            Companions.refreshFight(fp);
            fp.setHealth(max * 0.3f);
            boolean again = CompanionCare.trySupportHeal(carmen, fp, now + 2400);
            check("crew: a new fight brings the songs back", again, "");
            f.discard();
            fp.setHealth(max);
            Companions.endFightForTest(fp);
            return 5;
        });

        // ---------- follow (formation), hold, follow again ----------
        steps.add(s -> {
            FakePlayer fp = captain(s);
            putCaptain(fp, 0, -6, 0); // facing +z
            place(body(s, "petra"), C.offset(9, 0, 9));
            place(body(s, "carmen"), C.offset(-9, 0, 9));
            return 100;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc petra = body(s, "petra"), carmen = body(s, "carmen");
            double dp = petra.distanceTo(fp), dc = carmen.distanceTo(fp);
            check("crew follow: companions come back to the captain and keep formation (marksman behind, support close)", dp < 5 && dc < 4 && petra.getZ() < fp.getZ() + 0.5,
                    String.format("petra %.1f blocks %s, carmen %.1f blocks %s, captain %s", dp, pos(petra), dc, pos(carmen), pos(fp)));
            boolean ok = CompanionOrders.order(fp, "petra", CrewManager.Order.HOLD);
            L[0] = petra.blockPosition().asLong();
            B[0] = ok;
            putCaptain(fp, 0, 10, 0);
            return 80;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc petra = body(s, "petra"), carmen = body(s, "carmen");
            BlockPos hold = BlockPos.of(L[0]);
            double off = Math.sqrt(petra.distanceToSqr(Vec3.atBottomCenterOf(hold)));
            check("crew hold: a companion told to hold stays at its spot while the captain walks away (the other follows)",
                    B[0] && off < 2.5 && carmen.distanceTo(fp) < 5 && CrewManager.isWaiting(petra),
                    String.format("petra %.1f from her spot, %.1f from the captain; carmen %.1f from the captain", off, petra.distanceTo(fp), carmen.distanceTo(fp)));
            CompanionOrders.order(fp, "petra", CrewManager.Order.FOLLOW);
            return 100;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc petra = body(s, "petra");
            check("crew follow again: back at the captain's side", petra.distanceTo(fp) < 5 && !CrewManager.isWaiting(petra), String.format("%.1f blocks", petra.distanceTo(fp)));
            return 0;
        });

        // ---------- swaps: never outside a safe place or in a fight; fine in a town ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            Companions.endFightForTest(fp);
            UUID cid = Characters.get(fp).characterId;
            fp.moveTo(C.getX() + 3000.5, C.getY(), C.getZ() + 0.5);
            boolean unsafe = !Companions.safePlace(fp);
            boolean refused = !CompanionOrders.swap(fp, "carmen", "kenji");
            boolean unchanged = CrewManager.activeIds(s, cid).equals(List.of("petra", "carmen"));
            check("crew swap refused outside a safe place (open sea, no ship)", unsafe && refused && unchanged, "active " + CrewManager.activeIds(s, cid));
            Island isl = WorldPlan.of(s).byId("shiokaze_isle");
            var town = isl.settlement();
            l.getChunk(town.centerX >> 4, town.centerZ >> 4);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, town.centerX, town.centerZ);
            fp.moveTo(town.centerX + 0.5, y, town.centerZ + 0.5);
            fp.setOnGround(true);
            boolean safe = Companions.safePlace(fp);
            HumanoidNpc attacker = foe(l, "bandit", 1, C.offset(10, 0, 10), fp, 0);
            fp.setLastHurtByMob(attacker); // a fight is on
            boolean combatRefused = !CompanionOrders.swap(fp, "carmen", "kenji");
            fp.setLastHurtByMob(null);
            attacker.discard();
            Companions.endFightForTest(fp);
            boolean ok = CompanionOrders.swap(fp, "carmen", "kenji");
            CrewManager.Member cm = CrewManager.member(fp, "carmen"), km = CrewManager.member(fp, "kenji");
            check("crew swap refused mid-fight even in town; accepted in town out of combat (Kenji in, Carmen to reserve)",
                    safe && combatRefused && ok && CrewManager.activeIds(s, cid).equals(List.of("petra", "kenji")) && cm != null && !cm.active() && km != null && km.active(),
                    "safe=" + safe + " midFightRefused=" + combatRefused + " swapped=" + ok + " active " + CrewManager.activeIds(s, cid));
            putCaptain(fp, 0, -8, 0);
            return 20;
        });

        // ---------- focus what the captain looks at; regroup ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            putCaptain(fp, 0, -8, 0);
            HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
            place(petra, C.offset(-3, 0, -10));
            place(kenji, C.offset(3, 0, -7));
            HumanoidNpc a = foe(l, "bandit", 1, C.offset(-7, 0, 4), fp, 400), b = foe(l, "bandit", 1, C.offset(7, 0, 4), fp, 400);
            O[0] = a;
            O[1] = b;
            lookAt(fp, b);
            B[1] = CompanionOrders.focus(fp, null);
            return 10;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
            LivingEntity a = (LivingEntity) O[0], b = (LivingEntity) O[1];
            check("crew focus: both active companions attack the foe the captain is looking at", B[1] && petra.getTarget() == b && kenji.getTarget() == b,
                    "petra -> " + name(petra.getTarget()) + (petra.getTarget() == a ? " (the other foe)" : "") + ", kenji -> " + name(kenji.getTarget()) + (kenji.getTarget() == a ? " (the other foe)" : ""));
            CompanionOrders.regroup(fp);
            boolean brokeOff = petra.getTarget() == null && kenji.getTarget() == null;
            a.discard();
            b.discard();
            B[2] = brokeOff;
            return 60;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
            check("crew regroup: they break off at once and come back to the captain", B[2] && petra.distanceTo(fp) < 5 && kenji.distanceTo(fp) < 5,
                    String.format("broke off=%s, petra %.1f, kenji %.1f blocks", B[2], petra.distanceTo(fp), kenji.distanceTo(fp)));
            // far away and out of sight (walled in 24 blocks off): regroup brings him over
            BlockPos pk = C.offset(-24, 0, 0);
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    build(l, pk.offset(dx, -1, dz));
                    build(l, pk.offset(dx, 3, dz));
                    for (int dy = 0; dy < 3; dy++) if (Math.abs(dx) == 2 || Math.abs(dz) == 2) build(l, pk.offset(dx, dy, dz));
                }
            place(kenji, pk);
            boolean hidden = !Cover.canSee(fp, kenji);
            CompanionOrders.regroup(fp);
            check("crew regroup brings a companion that is far away and out of sight", hidden && kenji.distanceTo(fp) < 6,
                    String.format("hidden=%s, %.1f blocks after the order", hidden, kenji.distanceTo(fp)));
            return 40;
        });

        // ---------- never a neutral ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            putCaptain(fp, 0, -6, 0);
            HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
            place(petra, C.offset(-2, 0, -9));
            place(kenji, C.offset(2, 0, -5));
            HumanoidNpc v = SelfTest.npc(l, "villager", 1, C.offset(2, 0, -1));
            v.setNoAi(true);
            SPAWNED.add(v);
            v.hurt(fp.damageSources().playerAttack(fp), 0.5f); // the captain shoves a villager
            F[0] = v.getHealth();
            B[3] = Companions.isFoe(kenji, v) || Companions.isFoe(petra, v);
            O[2] = Hits.hit(kenji, v, 5f, Hits.Info.physical(0.2f));
            kenji.setTarget(v);
            B[4] = kenji.getTarget() == v;
            lookAt(fp, v);
            O[3] = CompanionOrders.quick(fp);
            O[4] = v;
            return 60;
        });
        steps.add(s -> {
            HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
            LivingEntity v = (LivingEntity) O[4];
            check("crew never attack a neutral: a villager the captain shoved is no foe, blows are ignored, nobody targets it, N regroups instead of focusing",
                    !B[3] && O[2] == Hits.Result.IGNORED && !B[4] && "regroup".equals(O[3]) && v.getHealth() == F[0] && petra.getTarget() != v && kenji.getTarget() != v,
                    "foe=" + B[3] + " hit=" + O[2] + " target taken=" + B[4] + " quick=" + O[3] + " villager health " + F[0] + " -> " + v.getHealth());
            v.discard();
            return 0;
        });

        // ---------- kills count for the captain, exactly once ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            CharacterData d = Characters.get(fp);
            HumanoidNpc kenji = body(s, "kenji");
            putCaptain(fp, 0, -6, 0);
            place(kenji, C.offset(1, 0, -4));
            QuestDef q = null;
            int oi = -1;
            for (QuestDef qd : QuestRegistry.all()) {
                if (QuestRegistry.isGenerated(qd.id())) continue;
                for (int i = 0; i < qd.objectives().size(); i++) {
                    QuestDef.Objective o = qd.objectives().get(i);
                    if (o.type().equals("kill") && o.count() >= 3 && (o.target().equals("bandit") || o.target().equals("pirate"))) {
                        q = qd;
                        oi = i;
                        break;
                    }
                }
                if (q != null) break;
            }
            if (q == null) {
                check("crew kill credit: a quest with a kill objective to test with", false, "none found");
                return 0;
            }
            String role = q.objectives().get(oi).target();
            d.questProgress.remove(q.id());
            d.completedQuests.remove(q.id());
            d.questProgress.put(q.id(), new CompoundTag());
            d.level = 30; // far from the next level: the experience is measured exactly
            d.xp = 0;
            long beli0 = d.beli;
            HumanoidNpc x = foe(l, role, 1, C.offset(2, 0, -2), fp, 0);
            x.hurt(kenji.damageSources().mobAttack(kenji), 10000f); // Kenji alone
            int p1 = d.questProgress.containsKey(q.id()) ? d.questProgress.get(q.id()).getInt("o" + oi) : -1;
            long xp1 = d.xp;
            HumanoidNpc y = foe(l, role, 1, C.offset(-2, 0, -2), fp, 0);
            y.hurt(fp.damageSources().playerAttack(fp), 1f); // the captain strikes it too...
            y.hurt(kenji.damageSources().mobAttack(kenji), 10000f); // ...and Kenji fells it
            int p2 = d.questProgress.containsKey(q.id()) ? d.questProgress.get(q.id()).getInt("o" + oi) : -1;
            long xp2 = d.xp - xp1;
            check("crew kill credit: a companion's kill counts for the captain's kill objective exactly once (also when both struck)",
                    !x.isAlive() && !y.isAlive() && p1 == 1 && p2 == 2, q.id() + " objective '" + role + "': " + p1 + " then " + p2);
            check("crew kill credit: half the experience for a companion's solo kill, all of it when the captain struck too; the outlaw reward is paid",
                    xp1 > 0 && Math.abs(xp1 * 2 - xp2) <= 2 && d.beli > beli0, "xp " + xp1 + " then " + xp2 + ", beli +" + (d.beli - beli0));
            d.questProgress.remove(q.id());
            d.level = 16;
            d.xp = 0;
            return 0;
        });

        // ---------- downed, not dead; helped up; gets up on its own ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            HumanoidNpc kenji = body(s, "kenji");
            place(kenji, C.offset(1, 0, -4));
            HumanoidNpc z = foe(l, "bandit", 1, C.offset(3, 0, -2), fp, 400);
            kenji.hurt(z.damageSources().mobAttack(z), 10000f);
            boolean downed = CompanionCare.isDowned(kenji) && kenji.isAlive() && kenji.getHealth() <= 1f && kenji.hasEffect(ModEffects.KNOCKED_OUT);
            kenji.hurt(z.damageSources().mobAttack(z), 50f);
            boolean spared = kenji.isAlive() && kenji.getHealth() <= 1f && kenji.getHealth() > 0;
            z.setTarget(kenji);
            boolean ignored = z.getTarget() != kenji;
            long now = l.getGameTime();
            boolean early = false;
            for (int i = 0; i < 7; i++) early |= CompanionCare.helpUp(fp, kenji, now + 4L * i);
            boolean up = CompanionCare.helpUp(fp, kenji, now + 28);
            check("crew downed, not dead: kneels at 1 health, takes no damage, foes drop it; the captain helps it up with a short hold (35 %)",
                    downed && spared && ignored && !early && up && !CompanionCare.isDowned(kenji) && kenji.getHealth() >= kenji.getMaxHealth() * 0.35f - 0.01f,
                    "downed=" + downed + " spared=" + spared + " foe ignores=" + ignored + " early=" + early + " up=" + up + " health " + kenji.getHealth() + "/" + kenji.getMaxHealth());
            z.discard();
            fp.setLastHurtByMob(null);
            Companions.endFightForTest(fp);
            kenji.hurt(l.damageSources().generic(), 10000f); // down again, nobody around to help
            L[1] = l.getGameTime();
            N[0] = 0;
            return 0;
        });
        steps.add(s -> {
            HumanoidNpc kenji = body(s, "kenji");
            if (kenji != null && CompanionCare.isDowned(kenji) && ++N[0] < 900) return -1;
            long took = s.overworld().getGameTime() - L[1];
            check("crew: a downed companion gets up on its own about 10 s after the fight, with 30 % health",
                    kenji != null && !CompanionCare.isDowned(kenji) && Math.abs(kenji.getHealth() - kenji.getMaxHealth() * 0.3f) < 1f && took >= 190,
                    "after " + took + " ticks, health " + (kenji == null ? "?" : kenji.getHealth() + "/" + kenji.getMaxHealth()));
            return 0;
        });

        // ---------- no friendly fire ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
            putCaptain(fp, 2, 0, 90);
            place(kenji, C);
            place(petra, C.offset(-2, 0, 0));
            kenji.setHealth(kenji.getMaxHealth());
            petra.setHealth(petra.getMaxHealth());
            HumanoidNpc f = foe(l, "bandit", 1, C.offset(0, 0, 2), fp, 400);
            HumanoidNpc v = SelfTest.npc(l, "villager", 1, C.offset(0, 0, -2));
            v.setNoAi(true);
            SPAWNED.add(v);
            F[1] = petra.getHealth();
            F[2] = f.getHealth();
            F[3] = v.getHealth();
            F[4] = fp.getHealth();
            Ability wave = Abilities.get("style/tide_grapple/breaking_wave");
            CombatState.of(kenji).cooldowns.clear();
            CombatState.of(kenji).staggeredUntil = 0;
            B[5] = wave != null && CombatManager.useAbility(kenji, wave, f);
            O[5] = Hits.hit(kenji, fp, 8f, Hits.Info.physical(0.5f));
            fp.hurt(kenji.damageSources().mobAttack(kenji), 8f);
            F[5] = fp.getHealth();
            O[6] = Hits.hit(fp, petra, 8f, Hits.Info.of(Element.FIRE, 1.2f)); // the captain's fire blow lands on a crewmate
            O[0] = f;
            O[1] = v;
            return 2;
        });
        steps.add(s -> {
            HumanoidNpc petra = body(s, "petra");
            LivingEntity f = (LivingEntity) O[0], v = (LivingEntity) O[1];
            check("crew no friendly fire: a companion's area technique hits the foe but never the captain, a crewmate or a bystander",
                    B[5] && f.getHealth() < F[2] && petra.getHealth() == F[1] && v.getHealth() == F[3] && O[5] == Hits.Result.IGNORED && F[5] == F[4],
                    "technique=" + B[5] + " foe " + F[2] + "->" + f.getHealth() + ", crewmate " + F[1] + "->" + petra.getHealth() + ", villager " + F[3] + "->" + v.getHealth()
                            + ", captain hit " + O[5] + " health " + F[4] + "->" + F[5]);
            long now = s.overworld().getGameTime();
            check("crew no friendly fire: the captain's fire blow on a companion leaves no damage, no burning and no stagger",
                    petra.getHealth() == F[1] && !petra.isOnFire() && !CombatState.of(petra).staggered(now),
                    "result " + O[6] + ", burning=" + petra.isOnFire() + ", staggered=" + CombatState.of(petra).staggered(now));
            f.discard();
            v.discard();
            return 0;
        });

        // ---------- cover: no blow through a wall ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            putCaptain(fp, -4, -6, 0);
            HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
            place(petra, C.offset(-7, 0, 0));
            place(kenji, C.offset(-6, 0, -4));
            BlockPos at = C.offset(7, 0, 0);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 0; dy <= 2; dy++) if (!(dx == 0 && dz == 0 && dy < 2)) build(l, at.offset(dx, dy, dz));
            HumanoidNpc f = foe(l, "bandit", 1, at, fp, 400);
            O[0] = f;
            F[0] = f.getHealth();
            return 100;
        });
        steps.add(s -> {
            HumanoidNpc petra = body(s, "petra");
            LivingEntity f = (LivingEntity) O[0];
            boolean reach = Cover.reachable(petra, f);
            Hits.Result r = Hits.hit(petra, f, 5f, Hits.Info.physical(0.2f));
            check("crew cover: a companion cannot hit a foe behind a wall (no damage in 5 s; a direct blow is COVERED)",
                    f.getHealth() == F[0] && r == Hits.Result.COVERED && !reach,
                    "foe health " + F[0] + " -> " + f.getHealth() + ", blow " + r + ", petra's target " + name(petra.getTarget()));
            f.discard();
            for (BlockPos p : BUILT) s.overworld().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            BUILT.clear();
            return 0;
        });

        // ---------- stuck: walled in a dozen blocks off with no way out, the companion is brought over ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            putCaptain(fp, 0, -6, 0);
            HumanoidNpc kenji = body(s, "kenji");
            BlockPos at = C.offset(-10, 0, 5);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 0; dy <= 2; dy++) if (!(dx == 0 && dz == 0 && dy < 2)) build(l, at.offset(dx, dy, dz));
            place(kenji, at);
            L[6] = l.getGameTime();
            N[0] = 0;
            return 0;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc kenji = body(s, "kenji");
            if (kenji != null && kenji.distanceTo(fp) > 5 && ++N[0] < 500) return -1;
            check("crew stuck recovery: a companion walled in with no path to the captain is brought over after a few seconds",
                    kenji != null && kenji.distanceTo(fp) < 5, "after " + (s.overworld().getGameTime() - L[6]) + " ticks, " + pos(kenji));
            for (BlockPos p : BUILT) s.overworld().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            BUILT.clear();
            return 0;
        });

        // ---------- saves: old format, round trip, bond ----------
        steps.add(s -> {
            UUID ch = UUID.fromString("0f0f0f0f-1111-4222-8333-444455556666"), pl = UUID.fromString("0f0f0f0f-aaaa-4bbb-8ccc-ddddeeeeffff");
            CompoundTag old = new CompoundTag(), ct = new CompoundTag();
            ListTag crews = new ListTag(), ms = new ListTag();
            ct.putUUID("character", ch);
            ct.putUUID("player", pl);
            ct.putString("captain", "Oldtimer");
            String[][] members = {{"odile", "false"}, {"tomas", "true"}, {"kaimon", "false"}, {"aria", "false"}};
            for (String[] m : members) {
                CompoundTag mt = new CompoundTag();
                mt.putString("id", m[0]);
                mt.putBoolean("waiting", Boolean.parseBoolean(m[1]));
                mt.putLong("home", 12345L);
                if (m[0].equals("aria")) mt.put("stowed", new CompoundTag());
                ms.add(mt);
            }
            ct.put("members", ms);
            crews.add(ct);
            old.put("crews", crews);
            CrewManager.CrewData d1 = CrewManager.CrewData.load(old, s.registryAccess());
            CrewManager.Crew c1 = d1.crew(ch);
            boolean migrated = c1 != null && c1.get("odile").active() && c1.get("kaimon").active() && !c1.get("tomas").active() && c1.get("tomas").holding()
                    && !c1.get("aria").active() && c1.get("aria").stowed() && c1.get("odile").bondRank() == 1 && c1.activeCount() == 2 && c1.questsSeen() == -1;
            CrewManager.CrewData d2 = CrewManager.CrewData.load(d1.save(new CompoundTag(), s.registryAccess()), s.registryAccess());
            CrewManager.Crew c2 = d2.crew(ch);
            boolean round = c2 != null && c2.roster().size() == 4 && c2.get("odile").active() && c2.get("kaimon").active() && !c2.get("tomas").active()
                    && c2.get("tomas").holding() && c2.get("aria").stowed() && c2.activeCount() == 2;
            check("crew save: an old-format crew loads (the followers active, up to two; the rest in reserve; defaults for new fields) and round-trips",
                    migrated && round, "migrated=" + migrated + " roundTrip=" + round);
            FakePlayer fp = captain(s);
            CrewManager.Member pm = CrewManager.member(fp, "petra");
            HumanoidNpc petra = body(s, "petra");
            float before = petra.getMaxHealth();
            CompanionCredit.addBond(fp, pm, 9);
            float after = petra.getMaxHealth();
            CrewManager.CrewData d3 = CrewManager.CrewData.load(CrewManager.data(s).save(new CompoundTag(), s.registryAccess()), s.registryAccess());
            CrewManager.Member saved = d3.crew(Characters.get(fp).characterId).get("petra");
            check("crew bond: rank 3 after 9 points (a small boost, the role technique), saved with the crew",
                    pm.bondRank() == 3 && after > before && saved != null && saved.bondRank() == 3 && saved.bond() == 9 && saved.active(),
                    "rank " + pm.bondRank() + ", health " + before + " -> " + after + ", saved rank " + (saved == null ? "?" : saved.bondRank()));
            return 0;
        });

        // ---------- dismissal: home, and welcome back ----------
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc petra = body(s, "petra");
            CrewManager.dismiss(fp, petra);
            NotableState.Status st = NotableState.get(s).status("petra");
            boolean gone = CrewManager.member(fp, "petra") == null && !CrewManager.isCrew(petra) && st != NotableState.Status.RECRUITED && petra.faction() == Faction.MARINE;
            boolean back = CrewManager.recruit(fp, petra);
            CrewManager.Member pm = CrewManager.member(fp, "petra");
            check("crew dismissal: a dismissed companion goes home (no longer crew) and can be recruited again, remembering the bond",
                    gone && back && pm != null && pm.bond() == 9 && pm.active(), "after dismissal " + st + ", rejoined=" + back + ", bond " + (pm == null ? "?" : pm.bond()));
            putCaptain(fp, 0, -6, 0);
            return 20;
        });

        // ---------- logging out and back in ----------
        steps.add(s -> {
            FakePlayer fp = captain(s);
            putCaptain(fp, 0, -6, 0);
            CrewManager.onLogout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(fp));
            CrewManager.testCaptain(fp, false);
            CrewManager.Member pm = CrewManager.member(fp, "petra"), km = CrewManager.member(fp, "kenji");
            B[6] = body(s, "petra") == null && body(s, "kenji") == null && pm != null && pm.stowed() && km != null && km.stowed();
            return 30;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            B[7] = body(s, "petra") == null && body(s, "kenji") == null;
            CrewManager.testCaptain(fp, true); // back online
            return 30;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc p = body(s, "petra"), k = body(s, "kenji");
            check("crew logout: the active pair goes aboard as data while the captain is away and steps off next to them on return (one body each)",
                    B[6] && B[7] && p != null && k != null && p.distanceTo(fp) < 6 && k.distanceTo(fp) < 6 && bodies(s, "petra") == 1 && bodies(s, "kenji") == 1,
                    "aboard at logout=" + B[6] + ", still aboard while away=" + B[7] + ", back: petra " + pos(p) + " kenji " + pos(k));
            return 0;
        });

        // ---------- another dimension and back ----------
        steps.add(s -> {
            ServerLevel other = s.getLevel(net.minecraft.world.level.Level.NETHER);
            if (other == null) for (ServerLevel l : s.getAllLevels()) if (l != s.overworld()) other = l;
            if (other == null) {
                check("crew dimension travel: a second dimension to test with", false, "none");
                return 0;
            }
            FakePlayer fp = captain(s);
            BlockPos dp = new BlockPos(8, Math.min(other.getMaxBuildHeight() - 20, 200), 8);
            other.setChunkForced(0, 0, true);
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++) {
                    other.setBlock(dp.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = 0; dy < 3; dy++) other.setBlock(dp.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            FakePlayer away = FakePlayerFactory.get(other, new GameProfile(CAP_ID, "CrewCaptain"));
            Characters.get(away).deserializeNBT(s.registryAccess(), Characters.get(fp).serializeNBT(s.registryAccess()));
            away.moveTo(dp.getX() + 0.5, dp.getY(), dp.getZ() + 0.5);
            away.setOnGround(true);
            away.setGameMode(GameType.SURVIVAL);
            CrewManager.testCaptain(away, true); // the captain is now in the other dimension
            O[6] = away;
            N[0] = 0;
            return 20;
        });
        steps.add(s -> {
            if (!(O[6] instanceof FakePlayer away)) return 0;
            HumanoidNpc p = body(s, "petra"), k = body(s, "kenji");
            boolean followed = p != null && k != null && p.level() == away.level() && k.level() == away.level() && p.distanceTo(away) < 6 && k.distanceTo(away) < 6
                    && bodies(s, "petra") == 1 && bodies(s, "kenji") == 1;
            CrewManager.Member tm = CrewManager.member(away, "petra");
            String state = (tm != null && tm.stowed() ? "aboard" : p == null ? "missing" + (tm == null ? "" : tm.missingChecks()) : p.level().dimension().location().getPath());
            if (!state.equals(O[2])) {
                TIMELINE.append(20 + N[0]).append(':').append(state).append(' ');
                O[2] = state;
            }
            if (!followed && ++N[0] < 200) return -1;
            CrewManager.Member pm = CrewManager.member(away, "petra");
            check("crew dimension travel: the active pair follows the captain into another dimension (one body each)", followed,
                    away.level().dimension().location() + " after " + (20 + N[0]) + " ticks (petra: " + TIMELINE + "): petra " + (p == null ? "none" : p.level().dimension().location() + " " + pos(p)) + ", kenji "
                            + (k == null ? "none" : k.level().dimension().location()) + (followed ? "" : "; petra aboard=" + (pm != null && pm.stowed())
                            + " captainOnline=" + (p != null && CrewManager.captainOf(p) == away) + " onGround=" + away.onGround() + " spectator=" + away.isSpectator()
                            + " spot=" + com.grandline.npc.notable.NotableSpawner.safeSpotNear((ServerLevel) away.level(), away.blockPosition()) + " at " + away.blockPosition()));
            FakePlayer fp = captain(s);
            putCaptain(fp, 0, -6, 0);
            CrewManager.testCaptain(fp, true); // back to the platform
            return 40;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc p = body(s, "petra"), k = body(s, "kenji");
            check("crew dimension travel: and back again with the captain", p != null && k != null && p.level() == fp.level() && k.level() == fp.level()
                    && p.distanceTo(fp) < 6 && k.distanceTo(fp) < 6 && bodies(s, "petra") == 1 && bodies(s, "kenji") == 1, "petra " + pos(p) + ", kenji " + pos(k));
            if (O[6] instanceof FakePlayer away) ((ServerLevel) away.level()).setChunkForced(0, 0, false);
            return 0;
        });

        // ---------- travel: aboard as data, stepping off; recalled with a fresh body; never two ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            int cx = ((C.getX() + 110) >> 4) * 16 + 8;
            P2 = new BlockPos(cx, C.getY(), C.getZ());
            l.setChunkForced(P2.getX() >> 4, P2.getZ() >> 4, true);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) build(l, P2.offset(dx, -1, dz));
            fp.moveTo(P2.getX() + 0.5, P2.getY(), P2.getZ() + 0.5);
            fp.setOnGround(true);
            L[2] = body(s, "petra").getUUID().getMostSignificantBits();
            L[3] = body(s, "petra").getUUID().getLeastSignificantBits();
            return 40;
        });
        steps.add(s -> {
            FakePlayer fp = captain(s);
            HumanoidNpc p = body(s, "petra");
            UUID was = new UUID(L[2], L[3]);
            int n = bodies(s, "petra");
            check("crew travel: far from the platform the companion is taken aboard and steps off next to the captain, one body only",
                    p != null && p.distanceTo(fp) < 6 && n == 1 && p.getUUID().equals(was), "body " + (p == null ? "none" : String.format("%.1f blocks", p.distanceTo(fp))) + ", bodies " + n);
            if (p == null) return 0;
            CompoundTag stale = new CompoundTag();
            p.saveWithoutId(stale);
            O[7] = stale;
            L[4] = p.getUUID().getMostSignificantBits();
            L[5] = p.getUUID().getLeastSignificantBits();
            p.discard(); // as if her chunk had unloaded with her in it
            return 90;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            HumanoidNpc p = body(s, "petra");
            UUID staleId = new UUID(L[4], L[5]);
            HumanoidNpc copy = ModEntities.HUMANOID_NPC.get().create(l);
            copy.load((CompoundTag) O[7]);
            copy.moveTo(P2.getX() + 1.5, P2.getY(), P2.getZ() + 1.5);
            boolean added = l.addFreshEntity(copy);
            int n = bodies(s, "petra");
            check("crew recall: a companion lost with an unloaded chunk comes back with a fresh body and the stale copy is refused (no duplicates)",
                    p != null && !p.getUUID().equals(staleId) && !added && n == 1 && p.distanceTo(fp) < 6, "recalled=" + (p != null) + " staleAdded=" + added + " bodies " + n);
            putCaptain(fp, 0, -6, 0);
            return 60;
        });

        // ---------- measured contribution: a reference captain alone, with two companions, and the companions alone ----------
        final String[] mode = {"captain alone", "captain + Petra + Kenji", "Petra + Kenji, captain idle"};
        final int[][] ticks = new int[2][3], blows = new int[2][3];
        final float[] foeHp = new float[2];
        for (int sturdy = 0; sturdy < 2; sturdy++)
            for (int md = 0; md < 3; md++) {
                final int fs = sturdy, m = md;
                steps.add(s -> setupRun(s, fs, m, foeHp));
                steps.add(s -> fightStep(s, m != 2, ticks[fs], blows[fs], m));
            }
        steps.add(s -> {
            StringBuilder sb = new StringBuilder();
            String[] foeName = {"standard foe (tier-3 Marine)", "sturdy foe (10 reference blows)"};
            for (int f = 0; f < 2; f++) {
                sb.append(String.format("%s, %.0f health: ", foeName[f], foeHp[f]));
                for (int md = 0; md < 3; md++) sb.append(mode[md]).append(' ').append(ticks[f][md]).append(" ticks (").append(blows[f][md]).append(" captain blows; damage ")
                        .append(BREAKDOWN[f][md]).append(')').append(md < 2 ? "; " : "");
                sb.append(f == 0 ? " | " : "");
            }
            boolean done = ticks[0][0] > 0 && ticks[0][1] > 0 && ticks[1][0] > 0 && ticks[1][1] > 0;
            boolean helps = done && ticks[0][1] < ticks[0][0] && ticks[1][1] < ticks[1][0];
            boolean noCarry = ticks[1][0] > 0 && (ticks[1][2] < 0 || ticks[1][2] > ticks[1][0]); // -1: they did not fell it at all
            check("crew contribution (measured, captain level 16 with the reference blow): two companions make the fight shorter", helps, sb.toString());
            check("crew contribution: the companions alone are slower than the captain alone against a sturdy foe (they help, they do not carry)", noCarry,
                    "companions alone " + ticks[1][2] + " ticks vs captain alone " + ticks[1][0]);
            return 0;
        });

        // ---------- clean up ----------
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer fp = captain(s);
            CrewManager.disband(s, Characters.get(fp).characterId);
            NotableState st = NotableState.get(s);
            boolean released = true;
            StringBuilder states = new StringBuilder();
            for (String id : TRIO) {
                HumanoidNpc b = body(s, id);
                NotableState.Status ss = st.status(id);
                states.append(id).append('=').append(ss).append(' ');
                if (CrewManager.member(fp, id) != null || ss == NotableState.Status.RECRUITED || b != null && CrewManager.isCrew(b)) released = false;
            }
            check("crew: disbanding (the captain's permadeath) sends everyone home: no longer crew, story state alive or unspawned", released, states.toString());
            CrewManager.testCaptain(fp, false);
            for (String id : TRIO) {
                HumanoidNpc b = body(s, id);
                if (b != null) b.discard();
            }
            for (Entity e : SPAWNED) if (!e.isRemoved()) e.discard();
            SPAWNED.clear();
            for (BlockPos p : BUILT) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            BUILT.clear();
            if (P2 != null) l.setChunkForced(P2.getX() >> 4, P2.getZ() >> 4, false);
            for (var e : SAVED.entrySet()) st.set(e.getKey(), e.getValue().status, e.getValue().entity, e.getValue().fate, e.getValue().since);
            var atk = fp.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null && ORIGINAL_ATTACK[0] >= 0) atk.setBaseValue(ORIGINAL_ATTACK[0]);
            return 0;
        });
    }

    private static final double[] ORIGINAL_ATTACK = {-1};
    /** Damage the measured foe took in the current run, by who dealt it ("captain", companion names, "other"). */
    private static final Map<String, Float> LEDGER = new LinkedHashMap<>();
    private static final String[][] BREAKDOWN = new String[2][3];
    private static boolean ledgerOn;

    private static void ledger() {
        if (ledgerOn) return;
        ledgerOn = true;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) -> {
            if (O[0] == null || e.getEntity() != O[0]) return;
            Entity by = e.getSource().getEntity();
            String who = by instanceof FakePlayer ? "captain" : by instanceof GLNpc g && !g.notableId().isEmpty() ? g.notableId() : "other";
            String how = e.getSource().getDirectEntity() != null && e.getSource().getDirectEntity() != by ? " (shot)" : "";
            if (by instanceof GLNpc g && g.level().getGameTime() - CombatState.of(g).lastAbilityAt <= 15) how += " [just after " + CombatState.of(g).lastAbility + "]";
            LEDGER.merge(who + how, e.getNewDamage(), Float::sum);
        });
    }

    /**
     * One measured fight: a foe pinned in place on the platform, going for the captain. {@code md} 0: the companions
     * hold far off; 1 and 2: they follow and are told to focus it. The captain's blow is set to the reference blow of
     * the captain's sea (a fake player carries no weapon or stats of its own).
     */
    private static int setupRun(MinecraftServer s, int sturdy, int md, float[] foeHp) {
        ServerLevel l = s.overworld();
        FakePlayer fp = captain(s);
        Characters.get(fp).level = 16;
        putCaptain(fp, 0, 4, 0);
        HumanoidNpc petra = body(s, "petra"), kenji = body(s, "kenji");
        if (petra == null || kenji == null) {
            if (++N[3] < 200) return -1; // still stepping off
            check("crew contribution: both companions present", false, "petra " + petra + ", kenji " + kenji);
            return 0;
        }
        N[3] = 0;
        var atk = fp.getAttribute(Attributes.ATTACK_DAMAGE);
        if (ORIGINAL_ATTACK[0] < 0) ORIGINAL_ATTACK[0] = atk.getBaseValue();
        float bonus = Hits.power(fp) - (float) atk.getValue();
        atk.setBaseValue(Tuning.referenceBlow(3) - bonus - (atk.getValue() - atk.getBaseValue()));
        CombatState.of(fp).nextAttackAllowed = 0;
        for (String id : List.of("petra", "kenji")) {
            CrewManager.Member m = CrewManager.member(fp, id);
            if (m.bond() > 0) CompanionCredit.addBond(fp, m, -m.bond()); // bond rank 1: the base companion
            m.resetTimersForTest();
            HumanoidNpc b = body(s, id);
            CompanionTuning.apply(b);
            b.setHealth(b.getMaxHealth());
            CombatState.of(b).cooldowns.clear();
            CombatState.of(b).nextAttackAllowed = 0;
            b.removeAllEffects();
        }
        if (md == 0) {
            place(petra, C.offset(-10, 0, -10));
            place(kenji, C.offset(10, 0, -10));
            CompanionOrders.order(fp, "petra", CrewManager.Order.HOLD);
            CompanionOrders.order(fp, "kenji", CrewManager.Order.HOLD);
        } else {
            CompanionOrders.order(fp, "petra", CrewManager.Order.FOLLOW);
            CompanionOrders.order(fp, "kenji", CrewManager.Order.FOLLOW);
            place(petra, C.offset(-3, 0, -2));
            place(kenji, C.offset(2, 0, 3));
        }
        Companions.endFightForTest(fp);
        HumanoidNpc f = foe(l, "marine", 3, C.offset(0, 0, 6), fp, sturdy == 1 ? 10 * Tuning.referenceBlow(3) : 0);
        foeHp[sturdy] = f.getMaxHealth();
        if (md != 0) CompanionOrders.focus(fp, f);
        ledger();
        LEDGER.clear();
        O[0] = f;
        N[0] = 0;
        N[1] = 0;
        return 0;
    }

    private static int sturdyOf(LivingEntity f) {
        return f.getMaxHealth() > 100 ? 1 : 0;
    }

    /** The captain swings whenever a blow is allowed (as a player holding the attack key would) until the foe falls. */
    private static int fightStep(MinecraftServer s, boolean swing, int[] ticks, int[] blows, int md) {
        FakePlayer fp = captain(s);
        LivingEntity f = (LivingEntity) O[0];
        if (f == null) return 0;
        BlockPos spot = C.offset(0, 0, 6);
        if (f.isAlive() && N[0] < 2400) {
            N[0]++;
            f.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5); // pinned in place: every run alike
            f.setDeltaMovement(Vec3.ZERO);
            lookAt(fp, f);
            Characters.get(fp).stamina = 100;
            long now = s.overworld().getGameTime();
            if (swing && now >= CombatState.of(fp).nextAttackAllowed && CombatManager.attack(fp, false, 0)) N[1]++;
            return -1;
        }
        ticks[md] = f.isAlive() ? -1 : N[0];
        blows[md] = N[1];
        StringBuilder b = new StringBuilder();
        LEDGER.forEach((k, v) -> b.append(b.length() == 0 ? "" : ", ").append(k).append(' ').append(Math.round(v)));
        BREAKDOWN[sturdyOf(f)][md] = b.toString();
        if (f.isAlive()) f.discard();
        return 20;
    }
}
