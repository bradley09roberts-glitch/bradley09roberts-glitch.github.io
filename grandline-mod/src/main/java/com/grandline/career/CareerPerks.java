package com.grandline.career;

import com.grandline.GrandLine;
import com.grandline.dialogue.Shops;
import com.grandline.npc.GLNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModItems;
import com.grandline.ship.*;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * What a rank is worth.
 * <ul>
 *   <li><b>Marines</b>: rank pay every day (200 + 300 per rank); Marine quartermasters (any Marine NPC) sell supplies, the
 *   coat, the black snail and the seastone jitte by rank; rest at a Marine base; the coat on enlistment, a seastone
 *   jitte at Ensign, a rank sword at Captain, a black snail at Vice Admiral; a squad from Commander
 *   ({@code /career squad}); a warship from Commodore ({@code /career ship}); a Buster Call request from Vice Admiral
 *   ({@code /career bustercall <player>}).</li>
 *   <li><b>Pirates</b>: generic pirates respect a Supernova and join their banner (in conversation); on pirate-held
 *   islands any pirate with a poster is welcome and the fence sells supplies (port of call); a Supernova may claim an
 *   island of the Grand Line as territory ({@code /career claim}: tribute every day, "Emperor of ..."); an Emperor's
 *   ships fly a crimson flag with a golden Jolly Roger, a Marine's the gull.</li>
 *   <li><b>Revolutionaries</b>: safe houses (any revolutionary: rest, and the law looks away for five minutes), contacts
 *   from Officer (supplies and word of the nearest Marine base), missions, funding every day, a cell from Commander.</li>
 *   <li><b>Bounty hunters</b>: 10% more per licence tier on every outlaw brought in alive; tracking hints on wanted
 *   posters from Bronze; a hunter ship from Gold.</li>
 *   <li><b>Civilians</b>: merchants pay up to 15% less everywhere; explorers sell every new chart, scholars every new
 *   rubbing, to the Sea-Gull Gazette; a retainer from Journeyman.</li>
 * </ul>
 */
public final class CareerPerks {
    static final Set<String> MARINE_ROLES = Set.of("marine", "marine_officer", "marine_captain", "vice_admiral", "admiral");
    static final Set<String> PIRATE_ROLES = Set.of("pirate", "pirate_captain", "lookout");
    public static final long TRIBUTE = 4_000;

    // ================================================================== rank pay

    /** Pay for one day in this career (Marine rank pay, revolutionary funding, the Gazette's retainer, territory tribute). */
    public static long pay(CharacterData d, CareerTrack t) {
        int r = Careers.rank(d);
        return switch (t) {
            case MARINE -> 200 + 300L * r;
            case REVOLUTIONARY -> 250L * r;
            case CIVILIAN -> r >= 2 ? 150L * r : 0;
            case PIRATE -> TRIBUTE * d.career.territories.size();
            case BOUNTY_HUNTER -> 0;
        };
    }

    /** Once per in-game day (on login, or while online at the turn of the day). */
    public static void payDay(ServerPlayer sp, CharacterData d) {
        CareerTrack t = Careers.ensure(sp, d);
        if (t == null) return;
        CareerState c = d.career;
        long day = Careers.day(sp.server);
        if (c.lastPaidDay == CareerState.NEVER) c.lastPaidDay = day;
        if (day <= c.lastPaidDay) return;
        c.lastPaidDay = day;
        c.demerits = Math.max(0, c.demerits - 2);
        keepTerritories(sp, d);
        long pay = pay(d, t);
        if (pay > 0) {
            d.beli += pay;
            Careers.tell(sp, Component.translatable("grandline.career.pay." + t.id(), String.format("%,d", pay), Careers.title(d)).withStyle(ChatFormatting.GOLD));
        }
        CharacterManager.markDirty(sp);
    }

    /** Territories another character took over (or the world lost) are no longer this one's. */
    static void keepTerritories(ServerPlayer sp, CharacterData d) {
        CareerWorldData w = CareerWorldData.get(sp.server);
        boolean changed = d.career.territories.removeIf(id -> {
            CareerWorldData.Holder h = w.territory(id);
            return h == null || !h.character().equals(d.characterId);
        });
        if (changed) {
            d.career.territoryName = d.career.territories.isEmpty() ? "" : islandLabel(sp, d.career.territories.get(0));
            Careers.changed(sp);
        }
    }

    // ================================================================== promotions: issued kit

    static void onPromoted(ServerPlayer sp, CharacterData d, CareerTrack t, int rank) {
        String rid = t.rank(rank).id();
        switch (t) {
            case MARINE -> {
                switch (rid) {
                    case "seaman" -> give(sp, Careers.issued(new ItemStack(CareerRegistry.MARINE_COAT.get()), d, null), "grandline.career.issued.coat");
                    case "ensign" -> give(sp, Careers.issued(new ItemStack(item("seastone_jitte")), d, "career.grandline.issued.jitte"), "grandline.career.issued.jitte");
                    case "captain" -> give(sp, Careers.issued(new ItemStack(item("fine_katana")), d, "career.grandline.issued.sword"), "grandline.career.issued.sword");
                    case "vice_admiral" -> give(sp, new ItemStack(com.grandline.journey.JourneyRegistry.BLACK_DEN_DEN_MUSHI.get()), "grandline.career.issued.snail");
                    default -> {}
                }
            }
            case PIRATE -> {
                if (rank == t.top()) flyColours(sp, new ShipLook("classic", "skull", 0xE8C040, 0x9E2222), true);
            }
            case REVOLUTIONARY -> {
                if (rid.equals("officer")) give(sp, new ItemStack(com.grandline.journey.JourneyRegistry.DEN_DEN_MUSHI.get()), "grandline.career.issued.snail_rev");
                if (rid.equals("commander")) give(sp, new ItemStack(ModItems.ETERNAL_POSE.get()), "grandline.career.issued.pose");
            }
            case BOUNTY_HUNTER -> {
                if (rid.equals("licensed")) give(sp, new ItemStack(ModItems.SEASTONE_CUFFS.get(), 2), "grandline.career.issued.cuffs");
                if (rid.equals("gold")) give(sp, new ItemStack(ModItems.SEASTONE_CUFFS.get(), 4), "grandline.career.issued.cuffs");
            }
            case CIVILIAN -> {
                long purse = 1_000L * rank;
                d.beli += purse;
                Careers.tell(sp, Component.translatable("grandline.career.issued.purse", String.format("%,d", purse)).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(GrandLine.MODID, id));
    }

    static void give(ServerPlayer sp, ItemStack st, String msgKey) {
        Component name = st.getHoverName();
        if (!sp.getInventory().add(st)) sp.drop(st, false);
        Careers.tell(sp, Component.translatable(msgKey, name).withStyle(ChatFormatting.AQUA));
    }

    // ================================================================== ships: colours

    /** A deserter strikes the Marine gull: their ships fly a Jolly Roger. */
    static void strikeMarineColours(ServerPlayer sp) {
        forShips(sp, look -> look.emblem().equals("marine") ? new ShipLook(look.figurehead(), "skull", 0xF0F0F0, 0x1A1A1A) : null);
    }

    /** Fly the given colours on every ship the player owns (the emblem too when {@code emblem}). */
    static void flyColours(ServerPlayer sp, ShipLook colours, boolean keepEmblem) {
        forShips(sp, look -> new ShipLook(look.figurehead(), keepEmblem && !look.emblem().equals("marine") ? look.emblem() : colours.emblem(),
                colours.emblemColor(), colours.flagColor()));
    }

    /** Shipyard: a new ship is dressed for its owner's career (the Marine gull on white for Marines; an Emperor's colours). */
    public static void dressShip(ServerPlayer sp, ShipEntity ship) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        if (t == CareerTrack.MARINE) {
            ship.setLook(ShipLook.MARINE.withFigurehead(ship.look().figurehead()));
            ship.setColors(0xF4F4F4, 0xE8E8E8);
        } else if (t == CareerTrack.PIRATE && Careers.rank(d) == t.top())
            ship.setLook(new ShipLook(ship.look().figurehead(), ship.look().emblem(), 0xE8C040, 0x9E2222));
        else return;
        ShipRegistry reg = ShipRegistry.get(sp.server);
        if (reg.get(ship.shipId) != null) reg.update(ship);
    }

    private static void forShips(ServerPlayer sp, java.util.function.Function<ShipLook, ShipLook> change) {
        ShipRegistry reg = ShipRegistry.get(sp.server);
        boolean dirty = false;
        for (ShipRegistry.Record r : reg.ownedBy(sp.getUUID())) {
            ShipLook now = ShipLook.decode(r.look), next = change.apply(now);
            if (next == null || next.equals(now)) continue;
            r.look = next.valid().encode();
            dirty = true;
            for (ServerLevel l : sp.server.getAllLevels())
                for (ShipEntity e : l.getEntitiesOfClass(ShipEntity.class, new AABB(r.x, r.y, r.z, r.x, r.y, r.z).inflate(96), e -> e.shipId.equals(r.shipId)))
                    e.setLook(next);
        }
        if (dirty) reg.setDirty();
    }

    // ================================================================== civilians

    static void chartSold(ServerPlayer sp, CharacterData d) {
        int r = Careers.rank(d);
        if (r <= 0) return;
        long fee = 150L * r;
        d.beli += fee;
        sp.displayClientMessage(Component.translatable("grandline.career.chart_sold", String.format("%,d", fee)).withStyle(ChatFormatting.GOLD), true);
        CharacterManager.markDirty(sp);
    }

    static void rubbingSold(ServerPlayer sp, CharacterData d) {
        int r = Careers.rank(d);
        if (r <= 0) return;
        long fee = 1_000L * r;
        d.beli += fee;
        d.addXp(100L * r);
        sp.displayClientMessage(Component.translatable("grandline.career.rubbing_sold", String.format("%,d", fee)).withStyle(ChatFormatting.GOLD), true);
        CharacterManager.markDirty(sp);
    }

    /** Shops.price: a merchant's trade discount (3% a rank). */
    public static double priceFactor(CharacterData d) {
        if (d.faction != Faction.CIVILIAN || !d.career.track.equals(CareerTrack.CIVILIAN.id())) return 1.0;
        if (!CareerTrack.specialty(d).equals("merchant")) return 1.0;
        return 1.0 - 0.03 * Careers.rank(d);
    }

    // ================================================================== bounty hunters: tracking

    /** Tests: players outside the player list (fake players) that posters may name. Null in normal play. */
    @Nullable public static java.util.function.Function<UUID, ServerPlayer> lookup;

    /** WantedPosterItem: a licensed hunter (Bronze) or Marine officer (Lieutenant) reads where the outlaw is. */
    public static void track(ServerPlayer sp, CompoundTag poster) {
        Component hint = trackingHint(sp, poster);
        if (hint != null) Careers.tell(sp, hint);
    }

    @Nullable
    public static Component trackingHint(ServerPlayer sp, CompoundTag poster) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        int r = Careers.rank(d);
        boolean hunter = t == CareerTrack.BOUNTY_HUNTER && r >= 2, marine = t == CareerTrack.MARINE && r >= 5;
        if (!hunter && !marine) return null;
        boolean sharp = (hunter && r >= 4) || (marine && r >= 8);
        double x, z;
        Component who = Component.literal(poster.getString("name"));
        if (poster.hasUUID("uuid")) {
            ServerPlayer target = sp.server.getPlayerList().getPlayer(poster.getUUID("uuid"));
            if (target == null && lookup != null) target = lookup.apply(poster.getUUID("uuid"));
            if (target == null || target.level() != sp.level() || target == sp)
                return Component.translatable("grandline.career.track.lost", who).withStyle(ChatFormatting.GRAY);
            x = target.getX();
            z = target.getZ();
        } else if (poster.contains("x")) {
            x = poster.getInt("x");
            z = poster.getInt("z");
        } else return null;
        double dist = Math.hypot(x - sp.getX(), z - sp.getZ());
        long shown = sharp ? Math.round(dist / 10) * 10 : Math.max(500, Math.round(dist / 500) * 500);
        Component dir = Component.translatable("grandline.career.dir." + compass(x - sp.getX(), z - sp.getZ()));
        Component near = Component.literal("?");
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        Island isl = plan == null ? null : plan.nearest((int) x, (int) z, 2000);
        if (isl != null) near = islandName(isl);
        return Component.translatable(sharp ? "grandline.career.track.sharp" : "grandline.career.track.rough", who, String.format("%,d", shown), dir, near)
                .withStyle(ChatFormatting.GOLD);
    }

    /** One of the eight winds from a world-space offset (north is -z). */
    public static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        if (a < 0) a += 360;
        String[] w = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return w[(int) Math.round(a / 45) % 8];
    }

    public static Component islandName(Island isl) {
        return isl.canon && isl.name == null ? Component.translatable(isl.translationKey()) : Component.literal(isl.name);
    }

    static String islandLabel(ServerPlayer sp, String id) {
        WorldPlan plan = WorldPlan.of(sp.server);
        Island isl = plan == null ? null : plan.byId(id);
        if (isl == null) return id;
        return isl.canon && isl.name == null ? "#" + isl.translationKey() : isl.name;
    }

    // ================================================================== Marines: Buster Call, warships

    /** Vice Admiral and up: ask Headquarters for a Buster Call on a wanted player (granted when the target is eligible). */
    public static boolean requestBusterCall(ServerPlayer sp, ServerPlayer target) {
        CharacterData d = Characters.get(sp);
        long now = Careers.now(sp.server);
        if (Careers.ensure(sp, d) != CareerTrack.MARINE || Careers.rank(d) < 10) {
            Careers.tell(sp, Component.translatable("grandline.career.buster.rank").withStyle(ChatFormatting.RED));
            return false;
        }
        if (d.career.busterReady > now) {
            Careers.tell(sp, Component.translatable("grandline.career.cooldown", (d.career.busterReady - now) / 20).withStyle(ChatFormatting.RED));
            return false;
        }
        if (target == sp || Characters.get(target).faction == Faction.MARINE) {
            Careers.tell(sp, Component.translatable("grandline.career.buster.target").withStyle(ChatFormatting.RED));
            return false;
        }
        BusterCall.Rules r = new BusterCall.Rules();
        r.force = true; // a Vice Admiral's request is granted; the bounty and the other rules still apply
        BusterCall.Call call = BusterCall.report(target, "orders", r);
        if (call == null) {
            Careers.tell(sp, Component.translatable("grandline.career.buster.refused", Characters.get(target).name).withStyle(ChatFormatting.RED));
            return false;
        }
        d.career.busterReady = now + 20 * 60 * 60;
        Careers.tell(sp, Component.translatable("grandline.career.buster.granted", call.name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        CharacterManager.markDirty(sp);
        return true;
    }

    /** Marines from Commodore and bounty hunters from Gold call a ship of their own side to the open sea nearby. */
    @Nullable
    public static ShipEntity callShip(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        int r = Careers.rank(d);
        long now = Careers.now(sp.server);
        String faction;
        ShipClass cls;
        if (t == CareerTrack.MARINE && r >= 8) {
            faction = LivingSea.MARINE;
            cls = r >= 11 ? ShipClass.FLAGSHIP : r >= 9 ? ShipClass.GALLEON : ShipClass.BRIGANTINE;
        } else if (t == CareerTrack.BOUNTY_HUNTER && r >= 4) {
            faction = LivingSea.HUNTER;
            cls = r >= 5 ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
        } else {
            Careers.tell(sp, Component.translatable("grandline.career.ship.rank").withStyle(ChatFormatting.RED));
            return null;
        }
        if (d.career.shipReady > now) {
            Careers.tell(sp, Component.translatable("grandline.career.cooldown", (d.career.shipReady - now) / 20).withStyle(ChatFormatting.RED));
            return null;
        }
        ServerLevel l = sp.serverLevel();
        if (WorldPlan.forLevel(l) == null || SeaEncounters.countNpcShips(l) >= SeaEncounters.cap(Math.max(1, l.players().size()))) {
            Careers.tell(sp, Component.translatable("grandline.career.ship.none").withStyle(ChatFormatting.RED));
            return null;
        }
        Vec3 pos = null;
        for (int dist = 40; dist <= 110 && pos == null; dist += 35)
            for (int k = 0; k < 8 && pos == null; k++) {
                double a = k * Math.PI / 4;
                pos = LivingSea.openWater(l, sp.getX() + Math.cos(a) * dist, sp.getZ() + Math.sin(a) * dist, cls.length / 2 + 3);
            }
        if (pos == null) {
            Careers.tell(sp, Component.translatable("grandline.career.ship.none").withStyle(ChatFormatting.RED));
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(sp.getX() - pos.x), sp.getZ() - pos.z));
        int tier = Math.max(2, Math.min(9, d.level / 8 + 1));
        ShipEntity ship = SeaEncounters.spawnShip(l, pos, yaw, cls, faction, tier,
                Component.translatable(faction.equals(LivingSea.MARINE) ? "grandline.career.ship.marine_name" : "grandline.career.ship.hunter_name", d.name).getString());
        if (ship == null) return null;
        d.career.shipReady = now + 20 * 60 * 20;
        Careers.tell(sp, Component.translatable("grandline.career.ship.called." + t.id(), ship.shipName(), (int) Math.sqrt(sp.distanceToSqr(pos))).withStyle(ChatFormatting.AQUA));
        CharacterManager.markDirty(sp);
        return ship;
    }

    // ================================================================== pirates: territory

    /** Plant the flag over the island you stand on (Supernova and up). Returns null on success, else why not. */
    @Nullable
    public static Component claim(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        int r = Careers.rank(d);
        if (t != CareerTrack.PIRATE || r < 1) return Component.translatable("grandline.career.claim.rank");
        if (d.career.warlord) return Component.translatable("grandline.career.claim.warlord");
        keepTerritories(sp, d);
        int limit = r >= 2 ? 3 : 1;
        if (d.career.territories.size() >= limit) return Component.translatable("grandline.career.claim.limit", limit);
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        Island isl = plan == null ? null : plan.nearest(sp.getBlockX(), sp.getBlockZ(), 2000);
        if (isl == null || isl.distance(sp.getBlockX(), sp.getBlockZ()) > isl.radius) return Component.translatable("grandline.career.claim.no_island");
        if (!isl.zone.isGrandLine() || isl.starterFor != null || isl.settlementType == SettlementType.NONE || isl.settlementType == SettlementType.RUINS
                || isl.faction.equals("marine") || isl.settlementType == SettlementType.MARINE_BASE || isl.id.equals("mary_geoise") || isl.id.equals("laugh_tale"))
            return Component.translatable("grandline.career.claim.unfit", islandName(isl));
        CareerWorldData w = CareerWorldData.get(sp.server);
        CareerWorldData.Holder h = w.territory(isl.id);
        if (h != null && !h.character().equals(d.characterId)) return Component.translatable("grandline.career.claim.taken", islandName(isl), h.name());
        for (GLNpc n : sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(24), n -> n.isAlive() && !Squads.isMember(n)))
            if (n.isHostileTo(sp)) return Component.translatable("grandline.career.claim.defended");
        w.claim(isl.id, d.characterId, sp.getUUID(), d.name, Careers.now(sp.server));
        d.career.territories.add(isl.id);
        if (d.career.territoryName.isEmpty()) d.career.territoryName = islandLabel(sp, isl.id);
        Careers.tell(sp, Component.translatable("grandline.career.claim.done", islandName(isl), String.format("%,d", TRIBUTE)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Careers.announce(sp.server, Component.translatable("grandline.career.announce.territory", d.name, islandName(isl)).withStyle(ChatFormatting.YELLOW));
        Careers.check(sp);
        Careers.changed(sp);
        CharacterManager.markDirty(sp);
        return null;
    }

    /** An NPC of a pirate-held island (its port of call). */
    static boolean pirateIsland(GLNpc npc) {
        if (npc.islandId().isEmpty() || !(npc.level() instanceof ServerLevel sl)) return false;
        WorldPlan plan = WorldPlan.forLevel(sl);
        Island isl = plan == null ? null : plan.byId(npc.islandId());
        return isl != null && isl.faction.equals("pirate");
    }

    static boolean marineBase(GLNpc npc) {
        if (npc.islandId().isEmpty() || !(npc.level() instanceof ServerLevel sl)) return BusterCall.marineBaseNear(npc) != null;
        WorldPlan plan = WorldPlan.forLevel(sl);
        Island isl = plan == null ? null : plan.byId(npc.islandId());
        return isl != null && (isl.faction.equals("marine") || isl.settlementType == SettlementType.MARINE_BASE);
    }

    // ================================================================== dialogue (called from Dialogues)

    /** Whether this NPC can hold the promotion talk for the character's next rank. */
    public static boolean officerFor(CareerTrack t, int next, GLNpc npc) {
        if (npc.flag(GLNpc.FLAG_SURRENDER) || Squads.isMember(npc)) return false;
        if (t == CareerTrack.MARINE) {
            if (npc.faction() != Faction.MARINE || npc.role().equals("marine") && npc.notableId().isEmpty()) return false;
            return npc.tier() >= (next <= 7 ? 4 : next <= 10 ? 5 : 8);
        }
        if (t == CareerTrack.REVOLUTIONARY) {
            if (npc.faction() != Faction.REVOLUTIONARY) return false;
            return next < 3 ? npc.tier() >= 4 : !npc.notableId().isEmpty();
        }
        return false;
    }

    public static void addOptions(ServerPlayer sp, GLNpc npc, ListTag opts) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        if (t == null) return;
        if (npc.role().equals(Warlords.ENVOY_ROLE)) {
            if (Warlords.isEnvoyFor(npc, sp) && Warlords.eligible(sp, d)) {
                opts.add(StringTag.valueOf("career_marque"));
                opts.add(StringTag.valueOf("career_marque_decline"));
            }
            return;
        }
        if (Squads.isMember(npc)) return;
        int r = Careers.rank(d);
        boolean promote = t.talk && r < t.top() && officerFor(t, r + 1, npc) && Careers.missing(sp, d, t, r + 1).isEmpty();
        switch (t) {
            case MARINE -> {
                if (npc.faction() != Faction.MARINE || npc.isHostileTo(sp)) return;
                if (MARINE_ROLES.contains(npc.role()) || npc.flag(GLNpc.FLAG_NOTABLE)) opts.add(StringTag.valueOf("career_supply"));
                if (marineBase(npc)) opts.add(StringTag.valueOf("career_base_rest"));
            }
            case PIRATE -> {
                if (npc.faction() != Faction.PIRATE || !npc.notableId().isEmpty() || npc.getTags().contains(LivingSea.CREW) || npc.isHostileTo(sp)) return;
                if (Squads.bannerSize(d) > 0 && PIRATE_ROLES.contains(npc.role())) opts.add(StringTag.valueOf("career_banner"));
                if (portOfCall(d, npc)) opts.add(StringTag.valueOf("career_port"));
            }
            case REVOLUTIONARY -> {
                if (npc.faction() != Faction.REVOLUTIONARY) return;
                opts.add(StringTag.valueOf("career_safe_house"));
                if (r >= 1) opts.add(StringTag.valueOf("career_contacts"));
                if (d.career.mission.isEmpty()) opts.add(StringTag.valueOf("career_mission"));
            }
            default -> {
                return;
            }
        }
        if (promote) opts.add(StringTag.valueOf("career_promote"));
    }

    static boolean portOfCall(CharacterData d, GLNpc npc) {
        return PIRATE_ROLES.contains(npc.role()) && pirateIsland(npc) && (d.bounty >= 1_000_000L || Careers.rank(d) >= 1);
    }

    /** Handle a career option; false when the option is not a career one. */
    public static boolean choose(ServerPlayer sp, GLNpc npc, String option) {
        if (!option.startsWith("career_")) return false;
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        if (t == null) return true;
        int r = Careers.rank(d);
        switch (option) {
            case "career_promote" -> {
                if (!t.talk || r >= t.top() || !officerFor(t, r + 1, npc)) return true;
                List<Component> miss = Careers.missing(sp, d, t, r + 1);
                if (!miss.isEmpty()) {
                    Careers.tell(sp, Component.translatable("grandline.career.not_yet").withStyle(ChatFormatting.RED));
                    for (Component m : miss) Careers.tell(sp, Component.literal(" - ").append(m).withStyle(ChatFormatting.GRAY));
                    return true;
                }
                Careers.tell(sp, Component.translatable("grandline.career.talk." + t.id(), npc.getDisplayName()).withStyle(ChatFormatting.ITALIC));
                Careers.promote(sp, d, t);
            }
            case "career_supply", "career_port", "career_contacts" -> {
                if (shop(sp, npc) == null) return true;
                if (option.equals("career_contacts")) intel(sp);
                Shops.open(sp, npc);
            }
            case "career_base_rest", "career_safe_house" -> {
                if (option.equals("career_base_rest") ? t != CareerTrack.MARINE || !marineBase(npc) : t != CareerTrack.REVOLUTIONARY) return true;
                sp.setHealth(sp.getMaxHealth());
                d.stamina = d.maxStamina();
                if (option.equals("career_safe_house")) d.career.safeUntil = sp.level().getGameTime() + 20 * 60 * 5;
                Careers.tell(sp, Component.translatable("grandline.career." + (option.equals("career_safe_house") ? "safe_house" : "base_rest")).withStyle(ChatFormatting.GREEN));
            }
            case "career_banner" -> Squads.recruit(sp, npc);
            case "career_mission" -> {
                if (t == CareerTrack.REVOLUTIONARY) Missions.assign(sp, d);
            }
            case "career_marque" -> {
                if (Warlords.isEnvoyFor(npc, sp)) Warlords.grant(sp, npc);
            }
            case "career_marque_decline" -> {
                if (Warlords.isEnvoyFor(npc, sp)) Warlords.decline(sp, npc);
            }
            default -> {}
        }
        CharacterManager.markDirty(sp);
        return true;
    }

    /** Contacts' word: the nearest Marine base. */
    static void intel(ServerPlayer sp) {
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        if (plan == null) return;
        Island best = null;
        double bd = Double.MAX_VALUE;
        for (Island i : plan.islands) {
            if (!i.faction.equals("marine") && i.settlementType != SettlementType.MARINE_BASE) continue;
            double dd = i.distance(sp.getBlockX(), sp.getBlockZ());
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        if (best != null) Careers.tell(sp, Component.translatable("grandline.career.intel", islandName(best), String.format("%,d", Math.round(bd / 100) * 100),
                Component.translatable("grandline.career.dir." + compass(best.x - sp.getX(), best.z - sp.getZ()))).withStyle(ChatFormatting.DARK_GREEN));
    }

    // ================================================================== shops (Shops.offers)

    public record Offer(String item, int count, long price, int rank) {}

    static final List<Offer> MARINE_SUPPLY = List.of(
            new Offer("grandline:marine_coat", 1, 400, 0), new Offer("grandline:bullet", 32, 260, 0), new Offer("grandline:meat_on_bone", 6, 180, 0),
            new Offer("minecraft:bread", 8, 40, 0), new Offer("grandline:seastone_cuffs", 2, 2400, 1), new Offer("grandline:cannonball", 8, 600, 1),
            new Offer("grandline:den_den_mushi", 1, 1800, 2), new Offer("grandline:grappling_hook", 1, 2200, 2), new Offer("grandline:seastone_jitte", 1, 9000, 4),
            new Offer("grandline:black_den_den_mushi", 1, 8000, 5), new Offer("grandline:chain_shot", 4, 900, 7), new Offer("grandline:fine_katana", 1, 12000, 7));
    static final List<Offer> PIRATE_FENCE = List.of(
            new Offer("grandline:cannonball", 8, 600, 0), new Offer("grandline:chain_shot", 4, 800, 0), new Offer("grandline:grape_shot", 4, 700, 0),
            new Offer("grandline:grappling_hook", 1, 1900, 0), new Offer("grandline:meat_on_bone", 6, 160, 0), new Offer("grandline:pop_green", 4, 900, 0),
            new Offer("grandline:flintlock", 1, 1200, 0), new Offer("grandline:bullet", 32, 280, 0), new Offer("grandline:baby_den_den_mushi", 2, 380, 1),
            new Offer("grandline:log_pose", 1, 2600, 1));
    static final List<Offer> REV_CONTACTS = List.of(
            new Offer("grandline:musket", 1, 1800, 1), new Offer("grandline:bullet", 32, 260, 1), new Offer("grandline:pop_green", 4, 900, 1),
            new Offer("grandline:sea_chart", 1, 700, 1), new Offer("grandline:baby_den_den_mushi", 2, 350, 1), new Offer("grandline:den_den_mushi", 1, 2000, 1),
            new Offer("grandline:eternal_pose", 1, 25000, 2));

    /** The career shop this NPC keeps for this player (Marine supply, a pirate port's fence, revolutionary contacts), or null. */
    @Nullable
    public static List<Offer> shop(ServerPlayer sp, GLNpc npc) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = Careers.ensure(sp, d);
        if (t == null || Squads.isMember(npc)) return null;
        int r = Careers.rank(d);
        List<Offer> src = null;
        if (t == CareerTrack.MARINE && npc.faction() == Faction.MARINE && (MARINE_ROLES.contains(npc.role()) || npc.flag(GLNpc.FLAG_NOTABLE))) src = MARINE_SUPPLY;
        else if (t == CareerTrack.PIRATE && npc.faction() == Faction.PIRATE && npc.notableId().isEmpty() && portOfCall(d, npc)) src = PIRATE_FENCE;
        else if (t == CareerTrack.REVOLUTIONARY && npc.faction() == Faction.REVOLUTIONARY && r >= 1) src = REV_CONTACTS;
        if (src == null) return null;
        List<Offer> out = new ArrayList<>();
        for (Offer o : src) if (o.rank() <= r) out.add(o);
        return out;
    }

    // ================================================================== /career perk lines

    static List<Component> perkLines(ServerPlayer sp, CharacterData d, CareerTrack t) {
        List<Component> out = new ArrayList<>();
        int r = Careers.rank(d);
        long pay = pay(d, t);
        if (pay > 0) out.add(Component.translatable("grandline.career.perk.pay", String.format("%,d", pay)).withStyle(ChatFormatting.DARK_AQUA));
        if (Squads.rankSize(t, r) > 0) out.add(Component.translatable("grandline.career.perk.squad", Squads.rankSize(t, r)).withStyle(ChatFormatting.DARK_AQUA));
        if (t == CareerTrack.MARINE && r >= 8 || t == CareerTrack.BOUNTY_HUNTER && r >= 4) out.add(Component.translatable("grandline.career.perk.ship").withStyle(ChatFormatting.DARK_AQUA));
        if (t == CareerTrack.MARINE && r >= 10) out.add(Component.translatable("grandline.career.perk.buster").withStyle(ChatFormatting.DARK_AQUA));
        if (t == CareerTrack.PIRATE && Squads.bannerSize(d) > 0) out.add(Component.translatable("grandline.career.perk.banner", Squads.bannerSize(d)).withStyle(ChatFormatting.DARK_AQUA));
        if (t == CareerTrack.PIRATE && r >= 1) out.add(Component.translatable("grandline.career.perk.claim", d.career.territories.size()).withStyle(ChatFormatting.DARK_AQUA));
        if (d.career.warlord) out.add(Component.translatable("grandline.career.perk.warlord", Math.min(d.career.dutyDone, Warlords.QUOTA), Warlords.QUOTA,
                Math.max(0, (d.career.dutyDue - Careers.now(sp.server)) / 20)).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (t == CareerTrack.REVOLUTIONARY && !d.career.mission.isEmpty())
            out.add(Component.translatable("grandline.career.perk.mission", Component.translatable("grandline.career.mission." + d.career.mission)).withStyle(ChatFormatting.DARK_GREEN));
        if (t == CareerTrack.BOUNTY_HUNTER && r >= 1) out.add(Component.translatable("grandline.career.perk.payout", r * 10).withStyle(ChatFormatting.DARK_AQUA));
        if (t == CareerTrack.CIVILIAN && priceFactor(d) < 1) out.add(Component.translatable("grandline.career.perk.discount", Math.round((1 - priceFactor(d)) * 100)).withStyle(ChatFormatting.DARK_AQUA));
        return out;
    }

    private CareerPerks() {}
}
