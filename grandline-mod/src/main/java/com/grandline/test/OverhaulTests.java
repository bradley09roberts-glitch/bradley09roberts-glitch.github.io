package com.grandline.test;

import com.grandline.balance.Tuning;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Cover;
import com.grandline.combat.Element;
import com.grandline.combat.Hits;
import com.grandline.entity.AbilityProjectile;
import com.grandline.npc.AttackSlots;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Stat;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Gameplay overhaul checks (GL_SELFTEST=overhaul, also part of phase 1):
 * <ul>
 * <li>line of effect with real blocks: open ground, a wall, a closed and an open door, a corner, waist-high cover,
 * a floor between two levels, a glass pane; projectiles (blocked, fast through a thin wall, piercing then a wall);
 * blasts sheltered by a wall; beams stopped by a wall; an NPC that cannot reach its target never hurts it and
 * gives up; sight for lock-on;</li>
 * <li>fair foes: at most two hold a melee slot on one victim, no stun-lock, the per-blow cap;</li>
 * <li>the balance table: measured blows to fell each rank and the share of health their blows take, per sea tier,
 * written to balance-table.md.</li>
 * </ul>
 */
final class OverhaulTests {
    private OverhaulTests() {}

    private static BlockPos coverPos;

    /** A walled-off stone floor in the sky beside the main test arena, for building cover. */
    static BlockPos coverArena(ServerLevel l) {
        if (coverPos == null) {
            Island i = WorldPlan.of(l.getServer()).byId("shiokaze_isle");
            BlockPos c = new BlockPos(i.x - 60, 210, i.z - 60);
            l.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(c), 3, new ChunkPos(c));
            l.setChunkForced(c.getX() >> 4, c.getZ() >> 4, true);
            coverPos = c;
        }
        return coverPos;
    }

    /** Clear the cover arena: stone floor, air above. */
    private static void clear(ServerLevel l, BlockPos c) {
        for (int dx = -10; dx <= 12; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                l.setBlock(c.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 8; dy++) l.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        for (var e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(14, 10, 14), e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
        for (var p : l.getEntitiesOfClass(AbilityProjectile.class, new AABB(c).inflate(14, 10, 14))) p.discard();
    }

    /** A still NPC with lots of health and no Haki (no random dodges), facing +x. */
    private static HumanoidNpc dummy(ServerLevel l, String role, double x, double y, double z) {
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        n.moveTo(x, y, z, -90, 0);
        n.configure(role, 1, "", BlockPos.containing(x, y, z), "", l.random);
        n.clearHakiForTest();
        n.setNoAi(true);
        n.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        n.setHealth(1000);
        n.setYRot(-90);
        n.setYHeadRot(-90);
        n.yBodyRot = -90;
        l.addFreshEntity(n);
        return n;
    }

    private static void face(LivingEntity a, LivingEntity b) {
        Vec3 d = b.getBoundingBox().getCenter().subtract(a.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        a.setYRot(yaw);
        a.setYHeadRot(yaw);
        a.yBodyRot = yaw;
        a.setXRot(pitch);
    }

    private static void wallX(ServerLevel l, BlockPos c, int x, int z0, int z1, int h, Block b) {
        for (int z = z0; z <= z1; z++) for (int y = 0; y < h; y++) l.setBlock(c.offset(x, y, z), b.defaultBlockState(), 3);
    }

    /** Melee from a to b: picked by the cone and landed by Hits.hit (the same path as a real swing). */
    private static Hits.Result swing(HumanoidNpc a, LivingEntity b) {
        Cover.forgetCleared(); // each scenario is built and swung within one tick
        face(a, b);
        a.setTarget(b);
        b.invulnerableTime = 0;
        boolean picked = Hits.cone(a, 3.0, 120).contains(b);
        Hits.Result r = Hits.hit(a, b, 4f, Hits.Info.physical(0f).withPoise(0));
        return picked || r != Hits.Result.HIT ? r : Hits.Result.IGNORED; // landed without being picked would be a bug
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        // force-load the cover arena first: entities added to a chunk that is not loaded yet are invisible to area queries
        steps.add(s -> {
            coverArena(SelfTest.ow(s));
            return 40;
        });
        coverSteps(steps);
        projectileSteps(steps);
        aiSteps(steps);
        claritySteps(steps);
        balanceSteps(steps);
        steps.add(OverhaulTests::registries);
    }

    /**
     * The audit's complete registries (written to overhaul-registries.md): every enemy archetype with its rank and the
     * health/blow the tuning gives it at its base tier, every named character with a boss fight, every ability, and
     * every quest line with its number of steps.
     */
    private static int registries(MinecraftServer s) throws Exception {
        List<String> out = new ArrayList<>();
        ServerLevel l = SelfTest.ow(s);
        BlockPos c = coverArena(l);
        out.add("## Enemy and NPC archetypes");
        out.add("");
        out.add("| Role | Faction | Base tier | Hostility | Rank | Health at base tier | Light blow at base tier |");
        out.add("|---|---|---|---|---|---|---|");
        List<String> roles = new ArrayList<>(com.grandline.npc.Archetypes.roles());
        Collections.sort(roles);
        for (String role : roles) {
            var a = com.grandline.npc.Archetypes.get(role);
            HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
            n.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            n.configure(role, a.baseTier(), "", BlockPos.ZERO, "", l.random);
            out.add(String.format("| %s | %s | %d | %s | %s | %.0f | %.1f |", role, a.faction().id(), n.tier(), a.hostility().name().toLowerCase(Locale.ROOT),
                    Tuning.rank(n).id(), n.getMaxHealth(), Tuning.npcPower(n)));
            n.discard();
        }
        out.add("");
        out.add("Wild beasts and Sea Kings keep their own health; their blows to players go through the same per-blow caps (wild beasts as elites, Sea Kings as bosses).");
        out.add("");
        out.add("## Story bosses");
        out.add("");
        out.add("| Boss | Island | Lore tier | Encounter tier (numbers) | Health |");
        out.add("|---|---|---|---|---|");
        WorldPlan plan = WorldPlan.of(s);
        for (var b : com.grandline.boss.Bosses.all()) {
            var n = com.grandline.npc.notable.NotableCharacters.get(b.id);
            if (n == null) continue;
            Island isl = plan.byId(n.island());
            int enc = isl == null ? n.tier() : Tuning.tierClamp(Math.min(n.tier(), isl.tier + 1));
            out.add(String.format("| %s | %s | %d | %d | %.0f |", b.id, n.island(), n.tier(), enc, Tuning.notableHealth(enc, Tuning.Rank.BOSS)));
        }
        out.add("");
        out.add("## Abilities");
        out.add("");
        Map<String, List<com.grandline.combat.Ability>> bySource = new TreeMap<>();
        for (var a : com.grandline.combat.Abilities.all()) bySource.computeIfAbsent(a.source().name().toLowerCase(Locale.ROOT) + " / " + a.owner(), k -> new ArrayList<>()).add(a);
        out.add("| Source / owner | Abilities (id: cooldown s, stamina, unlock mastery) |");
        out.add("|---|---|");
        int abilities = 0;
        for (var e : bySource.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (var a : e.getValue()) {
                abilities++;
                if (sb.length() > 0) sb.append("; ");
                sb.append(a.id().substring(a.id().lastIndexOf('/') + 1)).append(": ").append(String.format("%.1f", a.cooldown() / 20f)).append(", ")
                        .append((int) a.stamina()).append(", ").append(a.unlockMastery()).append(a.awakened() ? " (awakened)" : "");
            }
            out.add("| " + e.getKey() + " | " + sb + " |");
        }
        out.add("");
        out.add(abilities + " abilities. None is marked as working through walls; every one goes through the line-of-effect check in Hits.hit.");
        out.add("");
        out.add("## Quest lines");
        out.add("");
        Map<String, int[]> arcs = new TreeMap<>();
        int generated = 0;
        for (var q : com.grandline.quest.QuestRegistry.all()) {
            if (com.grandline.quest.QuestRegistry.isGenerated(q.id())) {
                generated++;
                continue;
            }
            arcs.computeIfAbsent(q.arc() == null || q.arc().isEmpty() ? "(none)" : q.arc(), k -> new int[1])[0]++;
        }
        out.add("| Arc / line | Quests |");
        out.add("|---|---|");
        for (var e : arcs.entrySet()) out.add("| " + e.getKey() + " | " + e.getValue()[0] + " |");
        out.add("");
        out.add(generated + " generated optional jobs (island stories, job boards, sea jobs) are not listed one by one.");
        Files.write(Path.of("overhaul-registries.md"), out);
        SelfTest.check("audit: registries of archetypes, bosses, abilities and quest lines written", roles.size() > 20 && abilities > 100, roles.size() + " roles, " + abilities + " abilities, " + arcs.size() + " quest lines");
        return 0;
    }

    // ================================================================== clarity: who is who, practice bouts, lessons

    private static void claritySteps(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = coverArena(l);
            clear(l, c);
            FakePlayer fp = probe(s, 1);
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc bandit = dummy(l, "bandit", c.getX() + 3.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc villager = dummy(l, "villager", c.getX() + 3.5, c.getY(), c.getZ() + 3.5);
            HumanoidNpc admiral = dummy(l, "marine_captain", c.getX() + 3.5, c.getY(), c.getZ() - 3.5);
            admiral.setNotableBody(10, "human");
            admiral.retune();
            bandit.setTarget(fp);
            CompoundTag b = com.grandline.combat.TargetInfo.describe(fp, bandit), v = com.grandline.combat.TargetInfo.describe(fp, villager),
                    a = com.grandline.combat.TargetInfo.describe(fp, admiral);
            boolean ok = b.getString("rel").equals("hostile") && b.getString("rank").equals("minion") && b.getString("threat").equals("matched")
                    && !v.getString("rel").equals("hostile") && a.getString("rank").equals("elite") && a.getString("threat").equals("overwhelming")
                    && b.contains("role") && b.contains("faction");
            SelfTest.check("clarity: the target frame tells role, faction, relationship, rank and threat apart", ok,
                    "bandit " + b.getString("rel") + "/" + b.getString("rank") + "/" + b.getString("threat") + "; villager " + v.getString("rel")
                            + "; captain T10 " + a.getString("rank") + "/" + a.getString("threat"));
            // a guarded blow counts for the combat lesson
            CharacterData d = Characters.get(fp);
            d.flags.remove("lesson_guard");
            d.stamina = d.maxStamina();
            CombatState vs = CombatState.of(fp);
            vs.blocking = true;
            vs.blockStart = l.getGameTime() - 40;
            fp.setYRot(-90); // facing +x, towards the bandit
            fp.setYHeadRot(-90);
            fp.invulnerableTime = 0;
            Hits.Result r = Hits.hit(bandit, fp, 2f, Hits.Info.physical(0f));
            vs.blocking = false;
            SelfTest.check("clarity: guarding a blow counts for the combat lesson", r == Hits.Result.BLOCKED && d.flags.contains("lesson_guard"), "result " + r);
            fp.setHealth(fp.getMaxHealth());
            for (LivingEntity e : List.of(bandit, villager, admiral)) e.discard();
            return 0;
        });
        final HumanoidNpc[] partner = new HumanoidNpc[1];
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            FakePlayer fp = probe(s, 1);
            boolean started = com.grandline.combat.Sparring.start(fp, null);
            var found = l.getEntitiesOfClass(HumanoidNpc.class, fp.getBoundingBox().inflate(8), com.grandline.combat.Sparring::isPartner);
            partner[0] = found.isEmpty() ? null : found.get(0);
            if (partner[0] == null) {
                SelfTest.check("training: a sparring partner fights but never hurts, and bows out instead of dying", false, "started=" + started + ", no partner");
                return 0;
            }
            partner[0].clearHakiForTest();
            fp.setHealth(fp.getMaxHealth());
            fp.invulnerableTime = 0;
            CombatState.of(fp).iframesUntil = 0;
            float before = fp.getHealth();
            Hits.Result r = Hits.hit(partner[0], fp, 15f, Hits.Info.physical(0f));
            float lost = before - fp.getHealth();
            partner[0].invulnerableTime = 0;
            Hits.hit(fp, partner[0], 5000f, Hits.Info.physical(0f));
            boolean survived = partner[0].isAlive() && partner[0].getHealth() >= 1;
            SelfTest.check("training: a sparring partner fights but never hurts, and bows out instead of dying", started && r == Hits.Result.HIT && lost < 0.1f && survived,
                    "hit " + r + ", player lost " + lost + ", partner alive " + survived);
            return 5;
        });
        steps.add(s -> {
            boolean gone = partner[0] == null || partner[0].isRemoved();
            SelfTest.check("training: the bout ends and the partner leaves (the probe is not an online player)", gone, "");
            return 0;
        });
    }

    // ================================================================== cover

    private static void coverSteps(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = coverArena(l);
            clear(l, c);
            double y = c.getY();
            Map<String, Hits.Result> res = new LinkedHashMap<>();
            Map<String, String> want = new LinkedHashMap<>();
            // A at x-1, B at x+2 (2.4 blocks between bodies); obstacles at block x = 0
            HumanoidNpc a = dummy(l, "marine", c.getX() - 0.5, y, c.getZ() + 0.5);
            HumanoidNpc b = dummy(l, "bandit", c.getX() + 2.0, y, c.getZ() + 0.5);
            float hp0 = b.getHealth();
            res.put("open ground", swing(a, b));
            want.put("open ground", "HIT");
            wallX(l, c, 0, -3, 3, 4, Blocks.STONE);
            float hpWall = b.getHealth();
            res.put("stone wall", swing(a, b));
            want.put("stone wall", "COVERED");
            boolean wallNoDamage = b.getHealth() == hpWall;
            // a doorway in the wall with a closed, then an open door
            BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            l.setBlock(c.offset(0, 1, 0), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
            l.setBlock(c.offset(0, 0, 0), lower, 2 | 16);
            res.put("closed door", swing(a, b));
            want.put("closed door", "COVERED");
            l.setBlock(c.offset(0, 1, 0), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN, true), 2 | 16);
            l.setBlock(c.offset(0, 0, 0), lower.setValue(DoorBlock.OPEN, true), 2 | 16);
            res.put("open door", swing(a, b));
            want.put("open door", "HIT");
            // waist-high cover: only the legs are covered
            wallX(l, c, 0, -3, 3, 4, Blocks.AIR);
            wallX(l, c, 0, -3, 3, 1, Blocks.STONE);
            res.put("waist-high cover (head exposed)", swing(a, b));
            want.put("waist-high cover (head exposed)", "HIT");
            // a glass pane row: thin but full height
            wallX(l, c, 0, -3, 3, 1, Blocks.AIR);
            wallX(l, c, 0, -3, 3, 4, Blocks.GLASS_PANE);
            res.put("glass pane", swing(a, b));
            want.put("glass pane", "COVERED");
            wallX(l, c, 0, -3, 3, 4, Blocks.AIR);
            // round a corner: B two blocks to the side behind a short wall
            b.moveTo(c.getX() + 1.5, y, c.getZ() + 3.0);
            for (int z = 1; z <= 4; z++) for (int yy = 0; yy < 4; yy++) l.setBlock(c.offset(0, yy, z), Blocks.STONE.defaultBlockState(), 3);
            res.put("round a corner", swing(a, b));
            want.put("round a corner", "COVERED");
            for (int z = 1; z <= 4; z++) for (int yy = 0; yy < 4; yy++) l.setBlock(c.offset(0, yy, z), Blocks.AIR.defaultBlockState(), 3);
            // a floor between two levels: B stands on a stone ceiling above and in front of A
            for (int x = 0; x <= 4; x++) for (int z = -2; z <= 2; z++) l.setBlock(c.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
            b.moveTo(c.getX() + 1.6, y + 3, c.getZ() + 0.5);
            res.put("floor between levels", swing(a, b));
            want.put("floor between levels", "COVERED");
            boolean all = true;
            StringBuilder sb = new StringBuilder();
            for (var e : res.entrySet()) {
                boolean ok = e.getValue().name().equals(want.get(e.getKey()));
                all &= ok;
                sb.append(e.getKey()).append('=').append(e.getValue()).append(ok ? "" : " (want " + want.get(e.getKey()) + ")").append("; ");
            }
            SelfTest.check("cover: melee through walls, doors, corners, partial cover and floors", all && wallNoDamage && b.getHealth() < hp0, sb.toString());
            // sight (lock-on, ability targets, AI) follows the same rule
            b.moveTo(c.getX() + 2.0, y, c.getZ() + 0.5);
            boolean seenThroughFloor = Cover.canSee(a, b);
            wallX(l, c, 0, -3, 3, 4, Blocks.STONE);
            boolean seenThroughWall = Cover.canSee(a, b);
            wallX(l, c, 0, -3, 3, 4, Blocks.AIR);
            for (int x = 0; x <= 4; x++) for (int z = -2; z <= 2; z++) l.setBlock(c.offset(x, 2, z), Blocks.AIR.defaultBlockState(), 3);
            boolean seenOpen = Cover.canSee(a, b);
            SelfTest.check("cover: sight for lock-on and targets is blocked by walls only", seenOpen && !seenThroughWall, "open=" + seenOpen + " wall=" + seenThroughWall + " underCeiling=" + seenThroughFloor);
            a.discard();
            b.discard();
            return 0;
        });
        // areas and beams
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = coverArena(l);
            clear(l, c);
            double y = c.getY();
            HumanoidNpc a = dummy(l, "marine", c.getX() - 3.5, y, c.getZ() + 0.5);
            HumanoidNpc open = dummy(l, "bandit", c.getX() + 4.0, y, c.getZ() + 0.5);
            HumanoidNpc sheltered = dummy(l, "bandit", c.getX() + 2.5, y, c.getZ() + 3.5);
            for (int x = 0; x <= 5; x++) for (int yy = 0; yy < 4; yy++) l.setBlock(c.offset(x, yy, 2), Blocks.STONE.defaultBlockState(), 3);
            a.setTarget(open);
            Vec3 blast = new Vec3(c.getX() + 2.5, y + 0.5, c.getZ() + 0.5);
            List<LivingEntity> caught = Hits.sphere(a, blast, 4.5);
            SelfTest.check("cover: a blast reaches bodies in the open and not those behind a wall", caught.contains(open) && !caught.contains(sheltered), "caught " + caught.size());
            // a beam along +x stops at a wall at x=2
            HumanoidNpc near = dummy(l, "bandit", c.getX() + 0.5, y, c.getZ() - 1.5);
            HumanoidNpc far = dummy(l, "bandit", c.getX() + 5.5, y, c.getZ() - 1.5);
            for (int z = -3; z <= -1; z++) for (int yy = 0; yy < 4; yy++) l.setBlock(c.offset(2, yy, z), Blocks.STONE.defaultBlockState(), 3);
            Vec3 from = new Vec3(c.getX() - 3.5, y + 1.2, c.getZ() - 1.5);
            List<LivingEntity> beam = Hits.line(a, from, new Vec3(1, 0, 0), 12, 0.6);
            SelfTest.check("cover: a beam hits up to the first wall and stops there", beam.contains(near) && !beam.contains(far), "hit " + beam.size());
            for (LivingEntity e : List.of(a, open, sheltered, near, far)) e.discard();
            return 0;
        });
        // an NPC that cannot reach its target never hurts it, searches, then gives up
        final HumanoidNpc[] chase = new HumanoidNpc[2];
        final float[] hp = new float[1];
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = coverArena(l);
            clear(l, c);
            double y = c.getY();
            // the victim sealed in a stone cell
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++)
                if (dx != 0 || dz != 0 || dy == 2) l.setBlock(c.offset(4 + dx, dy, dz), Blocks.STONE.defaultBlockState(), 3);
            HumanoidNpc victim = dummy(l, "villager", c.getX() + 4.5, y, c.getZ() + 0.5);
            HumanoidNpc foe = ModEntities.HUMANOID_NPC.get().create(l);
            foe.moveTo(c.getX() + 1.5, y, c.getZ() + 0.5, -90, 0);
            foe.configure("bandit", 1, "", BlockPos.ZERO, "", l.random);
            foe.clearHakiForTest();
            l.addFreshEntity(foe);
            foe.setTarget(victim);
            chase[0] = foe;
            chase[1] = victim;
            hp[0] = victim.getHealth();
            return 120;
        });
        steps.add(s -> {
            boolean unhurt = chase[1].getHealth() >= hp[0];
            SelfTest.check("fair foes: an NPC never hurts a target it cannot reach", unhurt, "victim hp " + chase[1].getHealth() + "/" + hp[0]);
            return 120;
        });
        steps.add(s -> {
            boolean gaveUp = chase[0].getTarget() == null;
            SelfTest.check("fair foes: an NPC that lost sight gives up after a while", gaveUp, "target=" + chase[0].getTarget());
            chase[0].discard();
            chase[1].discard();
            return 0;
        });
    }

    // ================================================================== projectiles

    private static void projectileSteps(Deque<SelfTest.Step> steps) {
        final HumanoidNpc[] p = new HumanoidNpc[6];
        final float[] hp = new float[6];
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = coverArena(l);
            clear(l, c);
            double y = c.getY();
            // lane z=-4: open; lane z=0: a 1-block wall at x=2; lane z=4: very fast shot at a thin pane; lane z=8: piercing shot, wall between two victims
            p[0] = dummy(l, "bandit", c.getX() - 4.5, y, c.getZ() + 0.5); // outlaws are hostile to Marines
            p[1] = dummy(l, "marine", c.getX() + 5.5, y, c.getZ() - 3.5);
            p[2] = dummy(l, "marine", c.getX() + 5.5, y, c.getZ() + 0.5);
            p[3] = dummy(l, "marine", c.getX() + 5.5, y, c.getZ() + 4.5);
            p[4] = dummy(l, "marine", c.getX() + 1.5, y, c.getZ() + 8.5);
            p[5] = dummy(l, "marine", c.getX() + 7.5, y, c.getZ() + 8.5);
            for (int yy = 0; yy < 4; yy++) {
                for (int z = -1; z <= 1; z++) l.setBlock(c.offset(2, yy, z), Blocks.STONE.defaultBlockState(), 3);
                for (int z = 3; z <= 5; z++) l.setBlock(c.offset(2, yy, z), Blocks.GLASS_PANE.defaultBlockState(), 3);
                for (int z = 7; z <= 9; z++) l.setBlock(c.offset(4, yy, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int i = 1; i < 6; i++) hp[i] = p[i].getHealth();
            double[][] shots = {{-3.5, 4.0, 0}, {0.5, 4.0, 0}, {4.5, 9.0, 0}, {8.5, 4.6, 1}};
            for (double[] sh : shots) {
                Vec3 from = new Vec3(c.getX() - 3.5, y + 1.2, c.getZ() + sh[0]);
                AbilityProjectile pr = AbilityProjectile.shoot(l, p[0], "bullet", 0.16f, from, new Vec3(1, 0, 0), sh[1], 6f, Hits.Info.of(Element.PIERCE, 0f));
                pr.pierce = sh[2] > 0;
                pr.ignoreShip = null;
                // the owner must be hostile to every lane's victim
            }
            return 30;
        });
        steps.add(s -> {
            boolean open = p[1].getHealth() < hp[1];
            boolean walled = p[2].getHealth() >= hp[2];
            boolean fastPane = p[3].getHealth() >= hp[3];
            boolean pierceFirst = p[4].getHealth() < hp[4], pierceSecond = p[5].getHealth() >= hp[5];
            SelfTest.check("cover: shots hit in the open, stop at walls, never skip through a thin pane, and a piercing shot stops at the wall behind its victim",
                    open && walled && fastPane && pierceFirst && pierceSecond,
                    "open=" + open + " wall=" + walled + " fastThroughPane=" + !fastPane + " pierce1=" + pierceFirst + " pierce2Blocked=" + pierceSecond);
            for (HumanoidNpc n : p) n.discard();
            return 0;
        });
    }

    // ================================================================== fair foes

    private static FakePlayer probe;

    /** A survival fake player with its own character, set to the reference character of a sea tier. */
    static FakePlayer probe(MinecraftServer s, int tier) {
        ServerLevel l = SelfTest.ow(s);
        if (probe == null) {
            // a stock fake player ignores all damage; the probe must take blows like a real player
            probe = new FakePlayer(l, new GameProfile(UUID.nameUUIDFromBytes("gl-overhaul-probe".getBytes(StandardCharsets.UTF_8)), "BalanceProbe")) {
                @Override
                public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                    return false;
                }
            };
            CharacterData d = Characters.get(probe);
            if (!d.created) {
                CompoundTag req = new CompoundTag();
                req.putString("name", "Reference Pirate");
                req.putString("race", "human");
                req.putString("faction", "pirate");
                req.putString("origin", "east");
                req.put("appearance", new com.grandline.player.Appearance().save());
                CharacterManager.create(probe, req);
            }
            probe.setGameMode(GameType.SURVIVAL);
            // a fake player never ticks, so its spawn protection (3 s for a new player) would never run out
            try {
                var f = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                f.setAccessible(true);
                f.setInt(probe, 0);
            } catch (ReflectiveOperationException e) {
                SelfTest.check("probe: clear spawn protection", false, e.toString());
            }
        }
        CharacterData d = Characters.get(probe);
        int lv = Tuning.referenceLevel(tier);
        d.level = lv;
        d.stats.put(Stat.STRENGTH, lv);
        d.stats.put(Stat.ENDURANCE, lv);
        d.observationUnlocked = d.armamentUnlocked = false;
        d.fruit = null;
        String weapon = tier <= 2 ? "cutlass" : tier <= 5 ? "fine_cutlass" : tier <= 8 ? "abyssal_fang" : "enma";
        ItemStack held = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(com.grandline.GrandLine.id(weapon)));
        // a fake player is never ticked, so apply the held weapon's attribute modifiers by hand
        ItemStack old = probe.getMainHandItem();
        old.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            var ai = probe.getAttribute(attr);
            if (ai != null) ai.removeModifier(mod.id());
        });
        probe.setItemSlot(EquipmentSlot.MAINHAND, held);
        held.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            var ai = probe.getAttribute(attr);
            if (ai != null) {
                ai.removeModifier(mod.id());
                ai.addTransientModifier(mod);
            }
        });
        String key = weapon.equals("enma") ? "weapon/katana" : "weapon/cutlass";
        int m = Tuning.referenceMastery(tier);
        d.masteryXp.put(key, 60 * m + 15 * m * (m - 1)); // exactly mastery level m (see CharacterData.masteryLevelFor)
        CharacterManager.applyAttributes(probe);
        probe.setHealth(probe.getMaxHealth());
        CombatState.of(probe).staggerImmuneUntil = 0;
        return probe;
    }

    private static void aiSteps(Deque<SelfTest.Step> steps) {
        final List<HumanoidNpc> gang = new ArrayList<>();
        final HumanoidNpc[] target = new HumanoidNpc[1];
        final int[] maxHolders = new int[1];
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = coverArena(l);
            clear(l, c);
            target[0] = dummy(l, "villager", c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            for (int i = 0; i < 5; i++) {
                HumanoidNpc g = ModEntities.HUMANOID_NPC.get().create(l);
                double ang = i * Math.PI * 2 / 5;
                g.moveTo(c.getX() + 0.5 + Math.cos(ang) * 6, c.getY(), c.getZ() + 0.5 + Math.sin(ang) * 6, 0, 0);
                g.configure("bandit", 1, "", BlockPos.ZERO, "", l.random);
                g.clearHakiForTest();
                l.addFreshEntity(g);
                g.setTarget(target[0]);
                gang.add(g);
            }
            maxHolders[0] = 0;
            return 0;
        });
        for (int k = 0; k < 10; k++) {
            steps.add(s -> {
                maxHolders[0] = Math.max(maxHolders[0], AttackSlots.holders(target[0], SelfTest.ow(s).getGameTime()));
                return 10;
            });
        }
        steps.add(s -> {
            int engaged = 0;
            for (HumanoidNpc g : gang) if (g.getTarget() == target[0]) engaged++;
            SelfTest.check("fair foes: five bandits on one victim, at most two press a melee attack at once", maxHolders[0] >= 1 && maxHolders[0] <= 2,
                    "most slot holders at once: " + maxHolders[0] + ", still engaged " + engaged);
            for (HumanoidNpc g : gang) g.discard();
            target[0].discard();
            gang.clear();
            return 0;
        });
        // no stun-lock and the per-blow cap, against a level-1 player
        steps.add(s -> {
            ServerLevel l = SelfTest.ow(s);
            BlockPos c = coverArena(l);
            clear(l, c);
            FakePlayer fp = probe(s, 1);
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            HumanoidNpc brute = dummy(l, "marine_captain", c.getX() + 2.5, c.getY(), c.getZ() + 0.5);
            brute.setNotableBody(10, "human");
            brute.retune();
            brute.setTarget(fp);
            long now = l.getGameTime();
            CombatState vs = CombatState.of(fp);
            vs.staggeredUntil = 0;
            vs.staggerImmuneUntil = 0;
            fp.invulnerableTime = 0;
            Hits.hit(brute, fp, 1f, Hits.Info.physical(0f).withStun(80));
            long first = vs.staggeredUntil;
            fp.setHealth(fp.getMaxHealth());
            fp.invulnerableTime = 0;
            Hits.hit(brute, fp, 1f, Hits.Info.physical(0f).withStun(80));
            long second = vs.staggeredUntil;
            SelfTest.check("fair foes: a stagger holds a player at most 1.5 s and cannot be chained", first - now <= 30 && second == first,
                    "first stagger " + (first - now) + " ticks, after a second stunning blow " + (second - now));
            fp.setHealth(fp.getMaxHealth());
            fp.invulnerableTime = 0;
            vs.staggerImmuneUntil = 0;
            float before = fp.getHealth();
            Hits.hit(brute, fp, 500f, Hits.Info.physical(0f));
            float lost = before - fp.getHealth();
            boolean down = Characters.get(fp).isKnockedDown();
            SelfTest.check("fair foes: no single unannounced blow takes more than the elite cap (32 %) of a player's health", !down && lost <= fp.getMaxHealth() * 0.32f + 0.01f,
                    String.format("lost %.1f of %.1f (%.0f%%)", lost, fp.getMaxHealth(), 100 * lost / fp.getMaxHealth()));
            fp.setHealth(fp.getMaxHealth());
            brute.discard();
            return 0;
        });
    }

    // ================================================================== balance table

    private record Row(String rank, String role, int tier, float hp, int blowsToFell, float blow, float playerHp, float lightShare, float heavyShare) {}

    private static void balanceSteps(Deque<SelfTest.Step> steps) {
        final List<Row> rows = new ArrayList<>();
        final String[] difficulty = new String[1];
        steps.add(s -> { // the table is for the Standard difficulty, whatever the test world's difficulty is
            difficulty[0] = com.grandline.core.GLConfig.DIFFICULTY.get();
            com.grandline.core.GLConfig.DIFFICULTY.set("standard");
            return 0;
        });
        String[][] roles = {{"MINION", "bandit"}, {"STANDARD", "marine"}, {"ELITE", "pirate_captain"}};
        int[] tiers = {1, 2, 3, 5, 7, 10};
        for (int t : tiers) {
            for (String[] rr : roles) {
                steps.add(s -> {
                    rows.add(measure(s, rr[0], rr[1], t));
                    return 0;
                });
            }
        }
        steps.add(s -> {
            List<String> out = new ArrayList<>();
            out.add("| Tier | Ref. level | Rank | Role | NPC health | Light blows to fell | Ref. light blow | Ref. health | Foe light blow | Foe heavy blow |");
            out.add("|---|---|---|---|---|---|---|---|---|---|");
            boolean ok = true;
            List<String> misses = new ArrayList<>();
            for (Row r : rows) {
                out.add(String.format("| %d | %d | %s | %s | %.0f | %d | %.1f | %.0f | %.0f %% | %.0f %% |", r.tier, Tuning.referenceLevel(r.tier), r.rank, r.role, r.hp, r.blowsToFell, r.blow,
                        r.playerHp, r.lightShare * 100, r.heavyShare * 100));
                boolean rowOk = switch (r.rank) {
                    case "MINION" -> r.blowsToFell >= 1 && r.blowsToFell <= 3 && r.lightShare >= 0.06f && r.lightShare <= 0.15f;
                    case "STANDARD" -> r.blowsToFell >= 3 && r.blowsToFell <= 6 && r.lightShare >= 0.08f && r.lightShare <= 0.16f;
                    default -> r.blowsToFell >= 6 && r.lightShare <= 0.20f;
                };
                if (!rowOk) misses.add(r.rank + "@T" + r.tier + " blows=" + r.blowsToFell + String.format(" light=%.0f%%", r.lightShare * 100));
                ok &= rowOk;
            }
            out.add("");
            out.add("Boss estimate (computed, not measured): a story boss has " + Tuning.Rank.BOSS.hits + " light blows of the reference character of its encounter tier;"
                    + " at one landed blow per 1.5-3 s with its guard and phases that is roughly 1-2.5 minutes for a prepared character.");
            try {
                Files.write(Path.of("balance-table.md"), out);
            } catch (Exception e) {
                ok = false;
            }
            SelfTest.check("balance: measured blows to fell and blow sizes meet the targets at every tier (table in balance-table.md)", ok, misses.isEmpty() ? rows.size() + " rows" : misses.toString());
            com.grandline.core.GLConfig.DIFFICULTY.set(difficulty[0]);
            return 0;
        });
    }

    /** Fell one NPC with the reference character's light blows and take one light and one heavy blow from it. */
    private static Row measure(MinecraftServer s, String rank, String role, int tier) {
        ServerLevel l = SelfTest.ow(s);
        BlockPos c = coverArena(l);
        clear(l, c);
        FakePlayer fp = probe(s, tier);
        fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        n.moveTo(c.getX() + 2.5, c.getY(), c.getZ() + 0.5, 90, 0);
        n.configure(role, tier, "", BlockPos.ZERO, "", l.random);
        n.setNotableBody(tier, "human");
        n.clearHakiForTest();
        n.setNoAi(true);
        n.forceFruitForTest(""); // a random Logia would need Haki: the table is for plain blows
        l.addFreshEntity(n);
        n.retune();
        n.setHealth(n.getMaxHealth());
        float hp = n.getMaxHealth();
        float blow = CombatManager.blowDamage(fp, CombatManager.strikeFor(fp, false, 0), 1f);
        int blows = 0;
        Hits.Info info = Hits.Info.of(Element.SLASH, 0f).withPoise(0);
        while (n.isAlive() && n.getHealth() > 0 && blows < 300) {
            n.invulnerableTime = 0;
            CombatState.of(n).iframesUntil = 0;
            Hits.hit(fp, n, blow, info);
            blows++;
        }
        // its blows on the reference character
        HumanoidNpc foe = ModEntities.HUMANOID_NPC.get().create(l);
        foe.moveTo(c.getX() + 2.5, c.getY(), c.getZ() + 0.5, 90, 0);
        foe.configure(role, tier, "", BlockPos.ZERO, "", l.random);
        foe.setNotableBody(tier, "human");
        foe.clearHakiForTest();
        foe.setNoAi(true);
        foe.forceFruitForTest(""); // a random Logia would need Haki: the table is for plain blows
        l.addFreshEntity(foe);
        foe.retune();
        foe.setTarget(fp);
        float max = fp.getMaxHealth();
        float light = takeBlow(fp, foe, false), heavy = takeBlow(fp, foe, true);
        foe.discard();
        n.discard();
        return new Row(rank, role, tier, hp, blows, blow, max, light / max, heavy / max);
    }

    private static float takeBlow(FakePlayer fp, HumanoidNpc foe, boolean heavy) {
        fp.setHealth(fp.getMaxHealth());
        fp.invulnerableTime = 0;
        CombatState vs = CombatState.of(fp);
        vs.iframesUntil = 0;
        vs.blocking = false;
        float dmg = CombatManager.blowDamage(foe, CombatManager.strikeFor(foe, heavy, 0), heavy ? 1.3f : 1f);
        float before = fp.getHealth();
        Hits.hit(foe, fp, dmg, Hits.Info.physical(0f).withPoise(0));
        float lost = before - fp.getHealth();
        fp.setHealth(fp.getMaxHealth());
        Characters.get(fp).knockdownTicks = 0;
        return lost;
    }
}
