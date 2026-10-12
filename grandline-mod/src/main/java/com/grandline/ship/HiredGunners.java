package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModEntities;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Hired gunners: shipwrights and dockworkers sign on gunners for a player's ship for a one-off fee (by the sea's
 * danger, see {@link #fee}). They board the ship, each takes a cannon station and stays there (saved with the ship as
 * her passengers, stations kept), and while the ship's order is "fire at will" and her captain is within
 * {@link #ORDERS} blocks to hand out shot they fire on hostile NPC ships: pirates (but not a raid you joined), Marines
 * when the captain is wanted or an outlaw, and any NPC ship that fired on her or is coming for her. Never on a player
 * or a player's ship, never on merchants or bounty hunters that leave her alone. They use the same cannon cooldowns,
 * aim and shot (from the captain's pack, the loaded kind first, then cannonballs; free in creative) as the captain's
 * own gun crew ({@link NavalCombat#gunnery}). {@code /gunners} (or the shipwright's "gunners" option) shows the menu;
 * {@code /gunners dismiss} sends them ashore. A gunner knocked off his station climbs back to a free gun; if the ship
 * goes down they swim for it (they are gone).
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class HiredGunners {
    public static final String TAG = "gl_hired_gunner";
    static final String SHIP_KEY = "GLGunnerShip", EMPLOYER_KEY = "GLGunnerEmployer";
    /** Roles who sign gunners on. */
    public static final Set<String> HIRERS = Set.of("shipwright", "dockworker");
    /** Hired gunners pick targets this far out (blocks) and fire from {@link #FIRE_RANGE}. */
    static final double RANGE = 100, FIRE_RANGE = 85;
    /** The captain must be this close to give the order and hand out shot (as for the crew's gunnery). */
    static final double ORDERS = 64;
    /** Tests: players counted as online (fake players are not in the player list). */
    public static final Map<UUID, ServerPlayer> testPlayers = new HashMap<>();
    private static final Map<ShipEntity, int[]> TARGET = new WeakHashMap<>();
    /** Self-test statistics: shots fired by hired gunners. */
    public static int shots;

    // ================================================================== hiring and dismissal

    /** The gunner's skill (tier) a hirer can sign on: better hands in the more dangerous seas. */
    public static int gunnerTier(GLNpc hirer) {
        return Math.max(2, Math.min(8, hirer.tier() + 1));
    }

    /** One gunner's one-off fee (signing-on money and his share; no wages after that). */
    public static long fee(GLNpc hirer) {
        return 2_500L * gunnerTier(hirer);
    }

    /** The ship this player would hire for: the one they are aboard and command, else their nearest own ship within 80 blocks. */
    @Nullable
    public static ShipEntity shipFor(ServerPlayer sp) {
        ShipEntity s = NavalCombat.shipOf(sp);
        if (s != null && !s.flag(ShipEntity.F_NPC) && !s.flag(ShipEntity.F_PRIZE) && sp.getUUID().equals(s.owner)) return s;
        return Shipyard.nearestOwned(sp);
    }

    /** The hired gunners aboard a ship (seated or standing on her deck). */
    public static List<HumanoidNpc> gunnersOf(ShipEntity ship) {
        String id = ship.shipId.toString();
        return ship.level().getEntitiesOfClass(HumanoidNpc.class, ship.getBoundingBox().inflate(ship.cls.length / 2.0 + 4, 8, ship.cls.length / 2.0 + 4),
                m -> m.isAlive() && m.getTags().contains(TAG) && id.equals(m.getPersistentData().getString(SHIP_KEY)));
    }

    public static boolean isHired(Entity e) {
        return e.getTags().contains(TAG);
    }

    /** Free cannon stations a hire could fill. */
    public static int freeGuns(ShipEntity ship) {
        int free = 0;
        for (int s = 1; s <= ship.cls.cannons; s++) if (ship.getFirstPassengerAtSeat(s) == null) free++;
        return free;
    }

    /**
     * Sign on up to {@code count} gunners at {@code hirer} (a shipwright or dockworker within 8 blocks) for the player's
     * ship: as many as there are free guns and Beli for. Returns how many came aboard (0: refused, with a message).
     */
    public static int hire(ServerPlayer sp, GLNpc hirer, int count) {
        if (!HIRERS.contains(hirer.role()) || hirer.distanceTo(sp) > 8 || count <= 0) return 0;
        ShipEntity ship = shipFor(sp);
        if (ship == null) return refuse(sp, "grandline.gunners.no_ship");
        if (ship.cls.cannons == 0) return refuse(sp, "grandline.gunners.no_guns");
        int free = freeGuns(ship);
        if (free == 0) return refuse(sp, "grandline.gunners.full");
        CharacterData d = Characters.get(sp);
        long fee = fee(hirer);
        int n = (int) Math.min(Math.min(count, free), d.beli / fee);
        if (n <= 0) {
            sp.sendSystemMessage(Component.translatable("grandline.gunners.poor", fee).withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerLevel sl = sp.serverLevel();
        int tier = gunnerTier(hirer), hired = 0;
        for (int i = 0; i < n; i++) {
            int seat = NavalCombat.freeGun(ship);
            if (seat <= 0) break;
            HumanoidNpc g = ModEntities.HUMANOID_NPC.get().create(sl);
            if (g == null) break;
            Vec3 deck = ship.toWorld(ship.seatLocal(seat).x * 0.5, ship.cls.freeboard + 0.05, ship.seatLocal(seat).z);
            g.moveTo(deck.x, deck.y, deck.z, ship.getYRot(), 0);
            g.configure("sailor", tier, hirer.islandId(), BlockPos.ZERO, "", sl.random);
            g.setAllegiance(Faction.CIVILIAN, BlockPos.ZERO);
            g.setPersistenceRequired(); // they live aboard: saved with the ship, never despawned by distance
            g.setNoAi(true); // a gunner minds his gun: the ship aims, loads and fires it
            g.addTag(TAG);
            g.getPersistentData().putString(SHIP_KEY, ship.shipId.toString());
            g.getPersistentData().putUUID(EMPLOYER_KEY, sp.getUUID());
            sl.addFreshEntity(g);
            if (!ship.seatEntity(g, seat)) {
                g.discard();
                break;
            }
            hired++;
        }
        if (hired == 0) return refuse(sp, "grandline.gunners.full");
        d.beli -= fee * hired;
        CharacterManager.markDirty(sp);
        if (!ship.flag(ShipEntity.F_FIRE_AT_WILL)) NavalCombat.setFireAtWill(ship, sp, true); // they came to fire: the order stands until the captain holds it
        sl.playSound(null, ship.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.NEUTRAL, 1f, 0.9f);
        sp.sendSystemMessage(Component.translatable("grandline.gunners.hired", hired, ship.shipName(), fee * hired, gunnersOf(ship).size(), ship.cls.cannons).withStyle(ChatFormatting.GOLD));
        return hired;
    }

    private static int refuse(ServerPlayer sp, String key) {
        sp.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
        return 0;
    }

    /** Pay off and send ashore every hired gunner of the player's ship (no refund). Returns how many left. */
    public static int dismiss(ServerPlayer sp) {
        ShipEntity ship = shipFor(sp);
        if (ship == null) return refuse(sp, "grandline.gunners.no_ship");
        List<HumanoidNpc> gs = gunnersOf(ship);
        if (gs.isEmpty()) return refuse(sp, "grandline.gunners.none");
        for (HumanoidNpc g : gs) {
            g.stopRiding();
            g.discard();
        }
        sp.sendSystemMessage(Component.translatable("grandline.gunners.dismissed", gs.size(), ship.shipName()).withStyle(ChatFormatting.AQUA));
        return gs.size();
    }

    // ================================================================== at sea (from NavalCombat.shipTick, player ships)

    static void tick(ShipEntity ship, ServerLevel sl, long now) {
        if (ship.cls.cannons == 0) return;
        List<GLNpc> gunners = null;
        for (Entity e : ship.getPassengers())
            if (e instanceof GLNpc g && g.getTags().contains(TAG)) {
                if (gunners == null) gunners = new ArrayList<>();
                gunners.add(g);
            }
        int phase = Math.floorMod(ship.getId(), 20);
        if (ship.isSinking()) {
            if (now % 20 == phase) abandon(ship, sl);
            return;
        }
        if (now % 40 == phase) muster(ship, sl);
        if (gunners != null) fire(ship, sl, gunners, now, phase);
    }

    /** A hired gunner standing on the deck (thrown off his station) goes back to a free gun. */
    private static void muster(ShipEntity ship, ServerLevel sl) {
        for (HumanoidNpc g : gunnersOf(ship)) {
            if (g.isPassenger() || !ship.carries(g)) continue;
            int seat = NavalCombat.freeGun(ship);
            if (seat > 0) ship.seatEntity(g, seat);
        }
    }

    /** The ship is going down: her hired gunners swim for it. */
    private static void abandon(ShipEntity ship, ServerLevel sl) {
        List<HumanoidNpc> gs = gunnersOf(ship);
        if (gs.isEmpty()) return;
        for (HumanoidNpc g : gs) g.discard();
        ServerPlayer cap = employer(sl, ship.owner);
        if (cap != null) cap.sendSystemMessage(Component.translatable("grandline.gunners.abandoned", gs.size(), ship.shipName()).withStyle(ChatFormatting.RED));
    }

    /** The captain (online or a test player), or null. */
    @Nullable
    static ServerPlayer employer(ServerLevel sl, @Nullable UUID id) {
        if (id == null) return null;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
        return p != null ? p : testPlayers.get(id);
    }

    /**
     * Would a hired gun crew fire on that ship? Only NPC ships: pirates (unless the captain joined their raid), Marines
     * when the captain is an outlaw or wanted (and not under a Warlord's protection), and anyone who fired on this
     * ship or is coming for her. Merchants, bounty hunters and Marines who leave her alone are let be.
     */
    public static boolean hostile(ShipEntity ship, ShipEntity o, @Nullable Player cap) {
        if (o == ship || !o.flag(ShipEntity.F_NPC) || o.isSinking() || o.flag(ShipEntity.F_PRIZE) || o.naval.crewGone || o.ferryTarget != null) return false;
        if (NavalCombat.attackedBy(ship, o) || o.naval.targetId == ship.getId()) return true;
        if (o.npcFaction.equals(LivingSea.PIRATE)) return cap == null || !o.life.raidAlly(cap);
        if (!o.npcFaction.equals(LivingSea.MARINE) || cap == null || com.grandline.career.Careers.protectedAtSea(cap)) return false;
        CharacterData d = Characters.get(cap);
        return d.faction == Faction.PIRATE || d.faction == Faction.REVOLUTIONARY || d.bounty > 0;
    }

    /** Every manned gun that bears fires on the nearest hostile NPC ship (the crew's cooldowns, aim and shot rules). */
    private static void fire(ShipEntity ship, ServerLevel sl, List<GLNpc> gunners, long now, int phase) {
        if (!ship.flag(ShipEntity.F_FIRE_AT_WILL)) return; // the captain ordered "hold fire"
        ServerPlayer cap = employer(sl, ship.owner);
        if (cap == null || cap.level() != sl || cap.distanceToSqr(ship) > ORDERS * ORDERS) return; // nobody to give the order or hand out shot
        int[] tgt = TARGET.computeIfAbsent(ship, k -> new int[]{-1, -1000});
        if (now % 20 == phase || now - tgt[1] > 40) {
            tgt[1] = (int) now;
            tgt[0] = -1;
            double bd = RANGE * RANGE;
            for (ShipEntity o : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(RANGE, 24, RANGE), o -> hostile(ship, o, cap))) {
                double d = o.distanceToSqr(ship);
                if (d < bd) {
                    bd = d;
                    tgt[0] = o.getId();
                }
            }
        }
        if (tgt[0] < 0 || !(sl.getEntity(tgt[0]) instanceof ShipEntity target) || !target.isAlive() || !hostile(ship, target, cap)) return;
        if (target.distanceToSqr(ship) > FIRE_RANGE * FIRE_RANGE) return;
        NavalCombat.State n = ship.naval;
        for (GLNpc g : gunners) {
            int seat = ship.seatIndex(g);
            if (seat < 1 || seat > ship.cls.cannons || !g.isAlive() || g.flag(GLNpc.FLAG_SURRENDER) || g.hasEffect(ModEffects.KNOCKED_OUT)) continue;
            if (n.seatCd.getOrDefault(seat, 0L) > now) continue;
            String ammo = NavalCombat.ammo(ship);
            if (!NavalCombat.bears(ship, seat, NavalCombat.aimPoint(target, ammo))) {
                n.seatCd.put(seat, now + 10);
                continue;
            }
            if (!cap.isCreative()) {
                if (!take(cap, ammo)) {
                    ammo = NavalCombat.CANNONBALL;
                    if (!take(cap, ammo)) {
                        if (now - n.lastNoAmmo > 200) {
                            n.lastNoAmmo = now;
                            cap.displayClientMessage(Component.translatable("grandline.naval.out_of_shot").withStyle(ChatFormatting.RED), true);
                        }
                        return;
                    }
                }
            }
            float skill = g.tier() + (NavalCombat.CrewBonusesView.sniper(cap) ? 3 : 0);
            if (NavalCombat.gunnerFire(ship, seat, g, target, ammo, (10f + g.tier() * 2.5f) * ship.crewCannon, skill)) {
                shots++;
                n.seatCd.put(seat, now + 70 - Math.min(30, g.tier() * 3) + ship.getRandom().nextInt(20));
            } else {
                if (!cap.isCreative()) cap.getInventory().add(new ItemStack(NavalCombat.ammoItem(ammo))); // gun did not bear: the round goes back
                n.seatCd.put(seat, now + 10);
            }
        }
    }

    private static boolean take(Player p, String kind) {
        Item it = NavalCombat.ammoItem(kind);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(it)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ================================================================== dialogue and /gunners

    /** Hook from Dialogues.open: a shipwright or dockworker offers gunners. */
    public static void addOptions(ServerPlayer sp, GLNpc npc, ListTag opts) {
        if (HIRERS.contains(npc.role()) && npc.notableId().isEmpty() && !npc.getTags().contains(LivingSea.CREW)) opts.add(StringTag.valueOf("gunners"));
    }

    /** Hook from Dialogues.choose. */
    public static boolean choose(ServerPlayer sp, GLNpc npc, String option) {
        if (!option.equals("gunners")) return false;
        menu(sp, npc);
        return true;
    }

    private static MutableComponent button(Component label, String command, ChatFormatting colour, Component hover) {
        return Component.literal("[").append(label).append("]").withStyle(Style.EMPTY.withColor(colour)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /** The chat menu: the ship, her guns and gunners, buttons to hire one, fill every gun or dismiss them. */
    static void menu(ServerPlayer sp, @Nullable GLNpc hirer) {
        ShipEntity ship = shipFor(sp);
        if (ship == null) {
            refuse(sp, "grandline.gunners.no_ship");
            return;
        }
        int have = gunnersOf(ship).size(), free = freeGuns(ship);
        sp.sendSystemMessage(Component.translatable("grandline.gunners.menu", ship.shipName(), ship.cls.cannons, have, free).withStyle(ChatFormatting.GOLD));
        MutableComponent row = Component.empty();
        if (hirer != null && ship.cls.cannons > 0 && free > 0) {
            long fee = fee(hirer);
            row.append(button(Component.translatable("grandline.gunners.menu.hire_one", fee), "/gunners hire 1", ChatFormatting.GREEN,
                    Component.translatable("grandline.gunners.menu.hire.desc", gunnerTier(hirer))));
            if (free > 1) row.append(" ").append(button(Component.translatable("grandline.gunners.menu.hire_all", free, fee * free), "/gunners hire " + free, ChatFormatting.GREEN,
                    Component.translatable("grandline.gunners.menu.hire.desc", gunnerTier(hirer))));
        } else if (hirer == null) sp.sendSystemMessage(Component.translatable("grandline.gunners.menu.at_yard").withStyle(ChatFormatting.GRAY));
        if (have > 0) {
            if (!row.getSiblings().isEmpty()) row.append(" ");
            row.append(button(Component.translatable("grandline.gunners.menu.dismiss"), "/gunners dismiss", ChatFormatting.RED, Component.translatable("grandline.gunners.menu.dismiss.desc")));
        }
        if (!row.getSiblings().isEmpty()) sp.sendSystemMessage(row);
    }

    /** A shipwright or dockworker within 8 blocks, or null. */
    @Nullable
    static GLNpc hirerNear(ServerPlayer sp) {
        GLNpc best = null;
        double bd = 8 * 8;
        for (GLNpc m : sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(8), m -> m.isAlive() && HIRERS.contains(m.role()) && !m.getTags().contains(LivingSea.CREW))) {
            double d = m.distanceToSqr(sp);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("gunners")
                .executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    menu(sp, hirerNear(sp));
                    return 1;
                })
                .then(Commands.literal("hire").executes(c -> hireCmd(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 32)).executes(c -> hireCmd(c, IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("dismiss").executes(c -> dismiss(c.getSource().getPlayerOrException()))));
    }

    private static int hireCmd(CommandContext<CommandSourceStack> c, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        GLNpc hirer = hirerNear(sp);
        if (hirer == null) return refuse(sp, "grandline.gunners.no_yard");
        return hire(sp, hirer, count);
    }

    private HiredGunners() {}
}
