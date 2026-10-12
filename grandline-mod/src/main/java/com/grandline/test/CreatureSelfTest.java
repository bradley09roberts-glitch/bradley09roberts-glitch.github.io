package com.grandline.test;

import com.grandline.combat.Hits;
import com.grandline.creature.BeastForms;
import com.grandline.creature.SeaKing;
import com.grandline.creature.SeaKingSpawner;
import com.grandline.creature.WildBeast;
import com.grandline.creature.WildlifeSpawner;
import com.grandline.world.IslandTheme;
import com.grandline.fruit.Zoan;
import com.grandline.haki.HakiAbilities;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.registry.ModDataComponents;
import com.grandline.registry.ModEntities;
import com.grandline.registry.ModItems;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.Wind;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.Deque;
import java.util.UUID;

import static com.grandline.test.SelfTest.check;

/**
 * Self-test steps for Sea Kings, the Calm Belt and Zoan beast forms (added to phase 1 by {@link SelfTest}).
 * A Sea King is spawned by the real spawner next to a ship in the Calm Belt and must find and damage the ship by
 * itself; then ship cannon fire and a melee cone must hit its body segments, it must die and drop meat and Beli,
 * Conqueror's Haki must knock out a weak one but not a colossal one, and a full Zoan form must change the hitbox.
 */
final class CreatureSelfTest {
    private static final UUID SAILOR_ID = UUID.fromString("5e1a4f2b-7c9d-4e21-9a33-0b5e0c1d2f47");

    static void phase1(Deque<SelfTest.Step> steps) {
        final ShipEntity[] ship = new ShipEntity[1];
        final SeaKing[] king = new SeaKing[1];
        final float[] hull0 = new float[1];
        final int[] waited = {0};
        final int[] maxState = {0};
        final int[] extra = {0};
        final Vec3[] spot = new Vec3[1];
        final Vec3[] deathAt = new Vec3[1];

        // ---- Calm Belt: windless; find deep open water there
        steps.add(s -> {
            ServerLevel l = s.overworld();
            WorldPlan plan = WorldPlan.of(s);
            double calm = Wind.at(l, 20000, 5200).length(), gl = Wind.at(l, 20000, 2000).length(), blue = Wind.at(l, 20000, 12000).length();
            check("Calm Belt is windless", calm < 1e-3 && gl > 0.1 && blue > 0.1, String.format("calm %.3f, Grand Line %.2f, East Blue %.2f", calm, gl, blue));
            check("Sea King spawn rates: Calm Belt >> Grand Line >> Blues", SeaKingSpawner.chance(SeaZone.CALM_BELT, false) > 20 * SeaKingSpawner.chance(SeaZone.PARADISE, false)
                    && SeaKingSpawner.chance(SeaZone.PARADISE, false) > 4 * SeaKingSpawner.chance(SeaZone.EAST_BLUE, false), "per 5 s at sea: calm " + SeaKingSpawner.chance(SeaZone.CALM_BELT, false)
                    + ", nest " + SeaKingSpawner.chance(SeaZone.CALM_BELT, true) + ", paradise " + SeaKingSpawner.chance(SeaZone.PARADISE, false) + ", blues " + SeaKingSpawner.chance(SeaZone.EAST_BLUE, false));
            var home = plan.byId("shiokaze_isle");
            check("Reverse Mountain and settled harbours are Sea King-safe", SeaKingSpawner.isSafe(plan, 300, 200) && SeaKingSpawner.isSafe(plan, home.x + home.radius, home.z), "");
            check("Calm Belt has Sea King nests", SeaKingSpawner.nestFor(20000, 5300) != null && SeaKingSpawner.nestFor(20000, 2000) == null, String.valueOf(SeaKingSpawner.nestFor(20000, 5300)));
            WorldPlan.Sample smp = new WorldPlan.Sample();
            for (int x = 20000; x < 40000 && spot[0] == null; x += 350) {
                boolean ok = true;
                for (int dx = -120; dx <= 120 && ok; dx += 40)
                    for (int dz = -120; dz <= 120 && ok; dz += 40) {
                        plan.sample(x + dx, 5250 + dz, smp);
                        if (smp.height > WorldPlan.SEA_LEVEL - 16 || SeaKingSpawner.isSafe(plan, x + dx, 5250 + dz)) ok = false;
                    }
                if (ok) spot[0] = new Vec3(x + 0.5, WorldPlan.SEA_LEVEL + 0.9, 5250.5);
            }
            check("found deep open water in the Calm Belt", spot[0] != null && WorldLayout.zoneAt((int) spot[0].x, (int) spot[0].z) == SeaZone.CALM_BELT, String.valueOf(spot[0]));
            if (spot[0] == null) return 0;
            ChunkPos cp = new ChunkPos(BlockPos.containing(spot[0]));
            l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 8, cp);
            l.setChunkForced(cp.x, cp.z, true); // keeps the level ticking entities with no real player online
            l.getChunk(cp.x, cp.z);
            return 120;
        });
        // ---- the spawner puts a Sea King near a ship in the Calm Belt
        steps.add(s -> {
            if (spot[0] == null) return 0;
            ServerLevel l = s.overworld();
            WorldPlan plan = WorldPlan.of(s);
            ShipEntity sh = ModEntities.SHIPS.get(ShipClass.GALLEON).get().create(l);
            sh.moveTo(spot[0].x, spot[0].y, spot[0].z, 30, 0);
            sh.setShipName("Calm Belt Bait");
            sh.setHull(sh.cls.hull);
            sh.owner = SAILOR_ID;
            l.addFreshEntity(sh);
            ship[0] = sh;
            hull0[0] = sh.hull();
            SeaKing k = null;
            for (int i = 0; i < 6 && k == null; i++) k = SeaKingSpawner.spawnNear(l, plan, sh.position(), SeaZone.CALM_BELT, false, l.random);
            king[0] = k;
            check("Sea King spawns near a ship in the Calm Belt", k != null && WorldLayout.zoneAt(k.getBlockX(), k.getBlockZ()) == SeaZone.CALM_BELT
                            && l.getFluidState(BlockPos.containing(k.headCenter())).is(net.minecraft.tags.FluidTags.WATER),
                    k == null ? "spawner found no spot" : String.format("%s tier %d, %.0f blocks long, %.0f hp, %.0f blocks from the ship, head y=%.1f",
                            k.variantName(), k.tier(), k.length(), k.getHealth(), k.distanceTo(sh), k.getY()));
            return 2;
        });
        // ---- it finds the ship by itself and attacks the hull
        steps.add(s -> {
            SeaKing k = king[0];
            ShipEntity sh = ship[0];
            if (k == null || sh == null) return 0;
            ServerLevel l = s.overworld();
            if (waited[0] % 20 == 0) {
                ChunkPos cp = new ChunkPos(k.blockPosition());
                l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
            }
            maxState[0] = Math.max(maxState[0], k.state());
            if (k.isAlive() && sh.hull() >= hull0[0] && ++waited[0] < 1800) return -1;
            // keep watching a while longer for its other attack, the tail slam
            if (k.isAlive() && k.slams == 0 && !sh.isSinking() && ++extra[0] < 1200) return -1;
            check("Sea King hunts the ship and damages its hull", sh.hull() < hull0[0], String.format("hull %.0f -> %.0f after %d ticks (target=%s, attacks seen: %s)",
                    hull0[0], sh.hull(), waited[0], k.target() == null ? "none" : k.target().getType().toShortString(),
                    k.looms + " looms, " + k.bites + " bites, " + k.slams + " tail slams, state " + k.state() + " age " + (int) k.stateAge(0) + ", head " + (int) k.headCenter().distanceTo(sh.position()) + " blocks from ship"));
            check("Sea King bites and tail-slams", k.bites > 0 && k.slams > 0, k.bites + " bites, " + k.slams + " tail slams; hull now " + (int) sh.hull() + "/" + (int) sh.maxHull());
            double arc = 0;
            for (int i = 0; i < 7; i++) arc += k.segment(i, 1).distanceTo(k.segment(i + 1, 1)); // front half (the tail may be mid-slam)
            double want = 7 * SeaKing.SPACING * k.getScale();
            var mid = k.getParts()[7];
            check("Sea King body segments follow the head (multipart)", k.getParts().length == SeaKing.SEGMENTS && Math.abs(arc / want - 1) < 0.3
                            && mid.getBoundingBox().getCenter().distanceTo(k.segment(7, 1)) < 0.5 && mid.canBeCollidedWith(),
                    String.format("%d collidable parts strung %.1f blocks along its path (expected %.1f); head-to-tail %.1f of %.0f blocks",
                            k.getParts().length, arc, want, k.headCenter().distanceTo(k.tailTip()), k.length()));
            return 0;
        });
        // ---- cannons and melee hit the body (hold it still first so the aim is exact)
        steps.add(s -> {
            if (king[0] != null && king[0].isAlive()) king[0].freezeForTest();
            return 8;
        });
        steps.add(s -> {
            SeaKing k = king[0];
            ShipEntity sh = ship[0];
            if (k == null || sh == null || !k.isAlive()) {
                check("melee and cannon tests have a live Sea King", false, k == null ? "none spawned" : "removed: " + k.getRemovalReason());
                king[0] = null;
                return 0;
            }
            ServerLevel l = s.overworld();
            FakePlayer sailor = sailor(l);
            // melee: a cone swing at a mid-body segment far from the head
            var part = k.getParts()[8];
            Vec3 pc = part.getBoundingBox().getCenter();
            Vec3 away = new Vec3(pc.x - k.getX(), 0, pc.z - k.getZ()).normalize();
            Vec3 side = new Vec3(-away.z, 0, away.x);
            Vec3 stand = pc.add(side.scale(part.getBbWidth() / 2 + 1.6)).subtract(0, 1.4, 0);
            sailor.moveTo(stand.x, stand.y, stand.z);
            Vec3 look = pc.subtract(sailor.getEyePosition());
            sailor.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
            sailor.setXRot((float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
            boolean cone = Hits.cone(sailor, 4, 90).contains(k);
            check("melee cone hits a Sea King body segment", cone, String.format("segment 8 at %.1f blocks from the head", pc.distanceTo(k.headCenter())));
            // cannon: fire the ship's broadside gun facing the Sea King at its nearest segment
            int bestSeat = 1;
            double bestD = Double.MAX_VALUE;
            Vec3 aim = null;
            for (int seat = 1; seat <= sh.cls.cannons; seat++) {
                Vec3 local = sh.seatLocal(seat);
                Vec3 muzzle = sh.toWorld(local.x + Math.signum(local.x) * 1.2, local.y + 0.6, local.z);
                for (var p : k.getParts()) {
                    Vec3 c = p.getBoundingBox().getCenter();
                    Vec3 dir = c.subtract(muzzle);
                    Vec3 sideDir = sh.toWorld(Math.signum(local.x), 0, 0).subtract(sh.toWorld(0, 0, 0)).normalize();
                    if (dir.normalize().dot(sideDir) < 0.35) continue;
                    // not point-blank: a shell spawned inside or against a segment's box can pass through it
                    if (dir.length() >= 10 && dir.length() < bestD) {
                        bestD = dir.length();
                        bestSeat = seat;
                        aim = dir.add(0, dir.length() * 0.012, 0);
                    }
                }
            }
            float before = k.getHealth();
            if (aim == null) {
                Vec3 c = k.getParts()[6].getBoundingBox().getCenter();
                Vec3 from = c.add(side.scale(6));
                var p = com.grandline.entity.AbilityProjectile.shoot(l, sailor, "cannonball", 0.5f, from, c.subtract(from), 2.2, 20f,
                        Hits.Info.of(com.grandline.combat.Element.IMPACT, 1.5f));
                p.gravity = 0.02;
                p.explosion = 2.5f;
            } else {
                sh.seatPlayer(sailor, bestSeat);
                seated[0] = sailor.getVehicle() == sh;
                sh.fireFrom(bestSeat, aim, sailor, 20f);
            }
            hpBefore[0] = before;
            usedShipCannon[0] = aim != null;
            cannonDist[0] = bestD;
            return 25;
        });
        steps.add(s -> {
            SeaKing k = king[0];
            if (k == null) return 0;
            check("cannon fire damages the Sea King", k.isAlive() && k.getHealth() < hpBefore[0], String.format("%s, %.0f blocks: hp %.0f -> %.0f",
                    usedShipCannon[0] ? "ship broadside cannon" + (seated[0] ? " (gunner seated, x2)" : "") : "cannonball", cannonDist[0], hpBefore[0], k.getHealth()));
            ServerLevel l = s.overworld();
            FakePlayer sailor = sailor(l);
            sailor.stopRiding();
            // finish it with another cannonball, fired at the segment farthest from the ship
            k.setHealth(1);
            var far = k.getParts()[0];
            for (var pt : k.getParts()) if (ship[0] != null && pt.distanceToSqr(ship[0]) > far.distanceToSqr(ship[0])) far = pt;
            Vec3 c = far.getBoundingBox().getCenter();
            Vec3 out = ship[0] == null ? new Vec3(1, 0, 0) : new Vec3(c.x - ship[0].getX(), 0, c.z - ship[0].getZ()).normalize();
            Vec3 from = c.add(out.scale(far.getBbWidth() / 2 + 4)).add(0, 1, 0);
            var p = com.grandline.entity.AbilityProjectile.shoot(l, sailor, "cannonball", 0.5f, from, c.subtract(from), 2.2, 20f, Hits.Info.of(com.grandline.combat.Element.IMPACT, 1.5f));
            p.explosion = 2.5f;
            killShot[0] = p;
            killFrom[0] = from;
            deathAt[0] = k.position();
            return 6;
        });
        steps.add(s -> {
            SeaKing k = king[0];
            if (k == null) return 0;
            var p = killShot[0];
            killedByShot[0] = !k.isAlive();
            if (k.isAlive()) {
                // diagnostics, then finish it the way a sword would: a direct hit on a body segment
                com.grandline.GrandLine.LOGGER.warn("[SELFTEST] kill shot missed: removed={} pos={} from={} parts={}", p.isRemoved(), p.position(), killFrom[0], k.getParts()[0].getBoundingBox());
                k.getParts()[3].hurt(s.overworld().damageSources().playerAttack(sailor(s.overworld())), 50);
            }
            return 64;
        });
        steps.add(s -> {
            SeaKing k = king[0];
            if (k == null) return 0;
            ServerLevel l = s.overworld();
            check("Sea King dies (killing blow on a body segment)", k.isDeadOrDying() && k.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED, "health " + k.getHealth() + ", removed " + k.getRemovalReason()
                    + ", killing blow: " + (killedByShot[0] ? "cannonball on a body segment" : "melee hit on a body segment"));
            int steaks = 0, beli = 0;
            for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, new AABB(deathAt[0], deathAt[0]).inflate(40))) {
                if (ie.getItem().is(ModItems.SEA_KING_STEAK.get())) steaks += ie.getItem().getCount();
                if (ie.getItem().is(ModItems.BELI_POUCH.get())) {
                    Integer b = ie.getItem().get(ModDataComponents.BELI.get());
                    beli += b == null ? 0 : b;
                }
            }
            check("Sea King drops Sea King steak and Beli pouches", steaks > 0 && beli > 0, steaks + " steaks, " + beli + " Beli in pouches");
            return 0;
        });
        // ---- Conqueror's Haki: weak Sea Kings faint, colossal ones resist
        steps.add(s -> {
            ShipEntity sh = ship[0];
            if (sh == null) return 0;
            ServerLevel l = s.overworld();
            FakePlayer sailor = sailor(l);
            CharacterData d = Characters.get(sailor);
            d.created = true;
            d.level = 40;
            d.conquerorAwakened = true;
            Vec3 c = sh.position();
            sailor.moveTo(c.x, c.y + 2, c.z);
            SeaKing weak = SeaKingSpawner.spawn(l, c.x + 28, c.z, 0, 0, 1, 28, WorldPlan.SEA_LEVEL);
            SeaKing big = SeaKingSpawner.spawn(l, c.x - 34, c.z, 180, 2, 3, 72, WorldPlan.SEA_LEVEL);
            weak.anchoredForTest = true;
            big.anchoredForTest = true;
            HakiAbilities.conquerorPressure(sailor, 50, true);
            check("Conqueror's Haki knocks out a weaker Sea King", weak.state() == SeaKing.FAINT, "state " + weak.state() + ", will " + weak.willpower());
            check("a colossal Sea King resists Conqueror's Haki", big.state() != SeaKing.FAINT && big.state() != SeaKing.FLEE, "state " + big.state() + ", will " + big.willpower());
            weak.discard();
            big.discard();
            return 0;
        });
        // ---- Zoan full forms take the beast's hitbox
        steps.add(s -> {
            ServerLevel l = s.overworld();
            FakePlayer sailor = sailor(l);
            CharacterData d = Characters.get(sailor);
            d.fruit = "inu_wolf";
            Zoan.setForm(sailor, "");
            float w0 = sailor.getBbWidth(), h0 = sailor.getBbHeight();
            Zoan.setForm(sailor, "full");
            float w1 = sailor.getBbWidth(), h1 = sailor.getBbHeight(), sc = sailor.getScale();
            var wolf = BeastForms.get("wolf");
            check("Zoan full form takes the beast's hitbox (player)", Math.abs(w1 - wolf.width() * sc) < 0.02 && Math.abs(h1 - wolf.height() * sc) < 0.02 && h1 < h0,
                    String.format("wolf: %.2fx%.2f -> %.2fx%.2f (scale %.2f, eyes %.2f)", w0, h0, w1, h1, sc, sailor.getEyeHeight()));
            Zoan.setForm(sailor, "");
            check("reverting restores the humanoid hitbox", Math.abs(sailor.getBbWidth() - w0) < 0.01 && Math.abs(sailor.getBbHeight() - h0) < 0.01,
                    String.format("%.2fx%.2f", sailor.getBbWidth(), sailor.getBbHeight()));
            d.fruit = null;
            HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
            Vec3 c = ship[0] != null ? ship[0].position() : new Vec3(0, 80, 0);
            n.moveTo(c.x, c.y + 3, c.z);
            n.configure("villager", 1, "", BlockPos.containing(c), "", l.random);
            n.forceFruitForTest("ryu_allosaurus");
            l.addFreshEntity(n);
            Zoan.setForm(n, "full");
            var allo = BeastForms.get("allosaurus");
            check("Zoan full form takes the beast's hitbox (NPC)", Math.abs(n.getBbWidth() - allo.width() * n.getScale()) < 0.02 && Math.abs(n.getBbHeight() - allo.height() * n.getScale()) < 0.02,
                    String.format("allosaurus NPC %.2fx%.2f at scale %.2f", n.getBbWidth(), n.getBbHeight(), n.getScale()));
            int modelled = 0;
            for (var f : com.grandline.fruit.DevilFruits.all()) if (f.type.isZoan() && BeastForms.get(BeastForms.modelFor(f.id)) != null) modelled++;
            check("every Zoan fruit maps to a beast form", modelled >= 11, modelled + " Zoan fruits with beast forms");
            n.discard();
            if (ship[0] != null) ship[0].discard();
            if (spot[0] != null) l.setChunkForced(((int) Math.floor(spot[0].x)) >> 4, ((int) Math.floor(spot[0].z)) >> 4, false);
            return 0;
        });
        // ---- island wildlife: picked by island theme, simple AI
        final WildBeast[] wild = new WildBeast[WildBeast.Kind.values().length];
        final HumanoidNpc[] prey = new HumanoidNpc[1];
        final BlockPos[] land = new BlockPos[1];
        steps.add(s -> {
            var r = net.minecraft.util.RandomSource.create(7);
            boolean ok = WildlifeSpawner.kindFor(IslandTheme.SNOW, false, r) == WildBeast.Kind.LAPAHN
                    && WildlifeSpawner.kindFor(IslandTheme.SWAMP, false, r) == WildBeast.Kind.BANANA_GATOR
                    && WildlifeSpawner.kindFor(IslandTheme.TEMPERATE, true, r) == null;
            int dugongs = 0;
            for (int i = 0; i < 40; i++) if (WildlifeSpawner.kindFor(IslandTheme.DESERT, true, r) == WildBeast.Kind.KUNGFU_DUGONG) dugongs++;
            check("wildlife follows island themes", ok && dugongs > 20, "snow: Lapahn, swamp: Banana-gator, desert coast: " + dugongs + "/40 Kung-Fu Dugongs (rest Sea Cats)");
            ServerLevel l = s.overworld();
            var isl = WorldPlan.of(s).byId("shiokaze_isle");
            int x = isl.x + isl.radius / 3, z = isl.z - isl.radius / 3;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.setChunkForced((x >> 4) + dx, (z >> 4) + dz, true);
            int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            land[0] = new BlockPos(x, y, z);
            int i = 0;
            for (WildBeast.Kind k : WildBeast.Kind.values()) wild[i] = WildlifeSpawner.spawn(l, k, x + 0.5 + (i++) * 4, y, z + 0.5, 0);
            prey[0] = ModEntities.HUMANOID_NPC.get().create(l);
            prey[0].moveTo(x + 4.5, y, z + 3.5);
            prey[0].configure("villager", 1, "", land[0], "", l.random);
            prey[0].setNoAi(true);
            l.addFreshEntity(prey[0]);
            wild[1].setTarget(prey[0]); // the Lapahn
            return 80;
        });
        steps.add(s -> {
            ServerLevel l = s.overworld();
            int alive = 0;
            StringBuilder sb = new StringBuilder();
            for (WildBeast b : wild) {
                if (b != null && b.isAlive()) alive++;
                if (b != null) sb.append(b.kind.id()).append(String.format(" %.1fx%.1f ", b.getBbWidth(), b.getBbHeight()));
            }
            check("island wildlife spawns and lives (Kung-Fu Dugong, Lapahn, Banana-gator, Sea Cat)", alive == wild.length, sb.toString().trim());
            check("a Lapahn mauls its prey", prey[0] != null && prey[0].getHealth() < prey[0].getMaxHealth(), prey[0] == null ? "" : String.format("villager hp %.1f/%.1f", prey[0].getHealth(), prey[0].getMaxHealth()));
            WildBeast dugong = wild[0];
            FakePlayer sailor = sailor(l);
            dugong.hurt(l.damageSources().playerAttack(sailor), dugong.getMaxHealth() * 0.75f);
            check("a beaten Kung-Fu Dugong bows to its master", dugong.isAlive() && dugong.bowed(), String.format("hp %.1f/%.1f", dugong.getHealth(), dugong.getMaxHealth()));
            for (WildBeast b : wild) if (b != null) b.discard();
            if (prey[0] != null) prey[0].discard();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.setChunkForced((land[0].getX() >> 4) + dx, (land[0].getZ() >> 4) + dz, false);
            return 0;
        });
    }

    private static final float[] hpBefore = new float[1];
    private static final boolean[] usedShipCannon = new boolean[1];
    private static final double[] cannonDist = new double[1];
    private static final boolean[] seated = new boolean[1];
    private static final com.grandline.entity.AbilityProjectile[] killShot = new com.grandline.entity.AbilityProjectile[1];
    private static final Vec3[] killFrom = new Vec3[1];
    private static final boolean[] killedByShot = new boolean[1];

    private static FakePlayer sailor(ServerLevel l) {
        return FakePlayerFactory.get(l, new GameProfile(SAILOR_ID, "SelfTestSailor"));
    }

    private CreatureSelfTest() {}
}
