package com.grandline.journey;

import com.grandline.GrandLine;
import com.grandline.navigation.Navigation;
import com.grandline.npc.GLNpc;
import com.grandline.npc.crew.CrewManager;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.ship.BusterCall;
import com.grandline.ship.LivingSea;
import com.grandline.ship.ShipEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Den Den Mushi, all on the server. A <b>personal</b> snail calls any online player who carries one too: the callee's
 * snail rings ("puru puru puru", a bell every 1.5 s for 20 s) and they accept or decline (clickable chat buttons,
 * /den accept|decline, or using their snail); while connected, everything either party types in chat goes only to
 * the other, shown with the snail glyph. A <b>crew</b> snail speaks to every crewmate (players sharing a crew, see
 * /crew) who carries any snail. A <b>Baby Den Den</b> carries one message to anyone and is used up. A <b>black</b>
 * snail, taken from Marine officers, now and then picks up Marine and bounty-hunter movements nearby (and Buster Call
 * orders); /den listen (or using it) listens on purpose every 30 s.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class DenDenMushi {
    /** Bitmap font with the four snail glyphs (assets/grandline/font/den_den.json, U+E000..U+E003). */
    public static final ResourceLocation FONT = GrandLine.id("den_den");
    public static final long RING_TICKS = 20 * 20, LISTEN_COOLDOWN = 20 * 30, INVITE_TICKS = 20 * 120;
    public static final double SHIP_RANGE = 480, FOOT_RANGE = 160;

    /** Tests: sees every snail line delivered (players outside the player list included). Null in normal play. */
    @Nullable public static BiConsumer<ServerPlayer, Component> tap;

    public enum Result { RINGING, NO_SNAIL, NO_SNAIL_THERE, BUSY, SELF, NOT_CREATED }

    public static final class Call {
        public final ServerPlayer caller, callee;
        public boolean connected;
        final long ringUntil;

        Call(ServerPlayer caller, ServerPlayer callee, long ringUntil) {
            this.caller = caller;
            this.callee = callee;
            this.ringUntil = ringUntil;
        }

        public ServerPlayer other(ServerPlayer p) {
            return p == caller ? callee : caller;
        }
    }

    private static final Map<UUID, Call> CALLS = new HashMap<>();
    private static final Map<UUID, Long> LISTENED = new HashMap<>();
    private record Invite(UUID crew, String captain, long until) {}
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    // ================================================================== snails carried

    public static boolean carries(Player p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }

    public static boolean carriesAny(Player p) {
        for (var s : JourneyRegistry.SNAILS) if (carries(p, s.get())) return true;
        return false;
    }

    /** Glyph of a snail kind, in the snail font, followed by a space. */
    public static MutableComponent prefix(DenDenMushiItem.Kind k) {
        String glyph = String.valueOf((char) (0xE000 + k.ordinal()));
        return Component.literal(glyph).withStyle(Style.EMPTY.withFont(FONT).withColor(ChatFormatting.WHITE))
                .append(Component.literal(" ").withStyle(Style.EMPTY.withFont(Style.DEFAULT_FONT)));
    }

    private static MutableComponent line(DenDenMushiItem.Kind k, Component body, ChatFormatting colour) {
        return prefix(k).append(body.copy().withStyle(colour));
    }

    static void deliver(ServerPlayer to, Component msg) {
        to.sendSystemMessage(msg);
        if (tap != null) tap.accept(to, msg);
    }

    private static MutableComponent button(String key, String command, ChatFormatting colour) {
        return Component.translatable(key).withStyle(Style.EMPTY.withColor(colour).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static MutableComponent suggest(String key, String command) {
        return Component.translatable(key).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    private static Component name(ServerPlayer p) {
        CharacterData d = Characters.get(p);
        return Component.literal(d.created && !d.name.isEmpty() ? d.name : p.getGameProfile().getName());
    }

    // ================================================================== using the item

    static void use(ServerPlayer sp, DenDenMushiItem.Kind kind) {
        switch (kind) {
            case PERSONAL -> {
                Call c = CALLS.get(sp.getUUID());
                if (c != null && !c.connected && c.callee == sp) {
                    if (sp.isShiftKeyDown()) decline(sp);
                    else accept(sp);
                } else if (c != null) {
                    if (sp.isShiftKeyDown()) hangUp(sp);
                    else deliver(sp, line(kind, Component.translatable("grandline.den.in_call", name(c.other(sp)))
                            .append(" ").append(button("grandline.den.hang_up", "/den hangup", ChatFormatting.RED)), ChatFormatting.GREEN));
                } else menu(sp, sp.server.getPlayerList().getPlayers());
            }
            case CREW -> deliver(sp, line(kind, Component.translatable("grandline.den.crew_prompt", suggest("grandline.den.speak", "/den crew ")), ChatFormatting.GOLD));
            case BABY -> deliver(sp, line(kind, Component.translatable("grandline.den.baby_prompt", suggest("grandline.den.write", "/den baby ")), ChatFormatting.LIGHT_PURPLE));
            case BLACK -> listen(sp);
        }
    }

    /** The personal snail's menu: everyone at sea who can be called, as clickable names. */
    public static int menu(ServerPlayer sp, Collection<? extends ServerPlayer> online) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (ServerPlayer o : online) {
            if (o == sp || !carries(o, JourneyRegistry.DEN_DEN_MUSHI.get())) continue;
            if (n++ > 0) list.append("  ");
            list.append(Component.literal("[").append(name(o)).append("]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/den call " + o.getGameProfile().getName()))));
        }
        deliver(sp, line(DenDenMushiItem.Kind.PERSONAL, n == 0 ? Component.translatable("grandline.den.menu_empty")
                : Component.translatable("grandline.den.menu").append(" ").append(list), ChatFormatting.GREEN));
        return n;
    }

    // ================================================================== calls

    @Nullable
    public static Call callOf(ServerPlayer p) {
        return CALLS.get(p.getUUID());
    }

    /** Ring {@code callee} from {@code caller}'s personal snail. */
    public static Result call(ServerPlayer caller, ServerPlayer callee) {
        DenDenMushiItem.Kind k = DenDenMushiItem.Kind.PERSONAL;
        if (!Characters.get(caller).created) return Result.NOT_CREATED;
        if (caller == callee || caller.getUUID().equals(callee.getUUID())) {
            deliver(caller, line(k, Component.translatable("grandline.den.self"), ChatFormatting.GRAY));
            return Result.SELF;
        }
        if (!carries(caller, JourneyRegistry.DEN_DEN_MUSHI.get())) {
            deliver(caller, line(k, Component.translatable("grandline.den.no_snail"), ChatFormatting.GRAY));
            return Result.NO_SNAIL;
        }
        if (!carries(callee, JourneyRegistry.DEN_DEN_MUSHI.get())) {
            deliver(caller, line(k, Component.translatable("grandline.den.they_no_snail", name(callee)), ChatFormatting.GRAY));
            return Result.NO_SNAIL_THERE;
        }
        if (CALLS.containsKey(caller.getUUID()) || CALLS.containsKey(callee.getUUID())) {
            deliver(caller, line(k, Component.translatable("grandline.den.busy", name(callee)), ChatFormatting.GRAY));
            return Result.BUSY;
        }
        Call c = new Call(caller, callee, caller.server.overworld().getGameTime() + RING_TICKS);
        CALLS.put(caller.getUUID(), c);
        CALLS.put(callee.getUUID(), c);
        deliver(callee, line(k, Component.translatable("grandline.den.ringing", name(caller)).append(" ")
                .append(button("grandline.den.accept", "/den accept", ChatFormatting.GREEN)).append(" ")
                .append(button("grandline.den.decline", "/den decline", ChatFormatting.RED)), ChatFormatting.GREEN));
        deliver(caller, line(k, Component.translatable("grandline.den.calling", name(callee)), ChatFormatting.GREEN));
        ring(callee, 0);
        return Result.RINGING;
    }

    public static boolean accept(ServerPlayer callee) {
        Call c = CALLS.get(callee.getUUID());
        if (c == null || c.connected || c.callee != callee) return false;
        c.connected = true;
        for (ServerPlayer p : List.of(c.caller, c.callee)) {
            deliver(p, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.connected", name(c.other(p))).append(" ")
                    .append(button("grandline.den.hang_up", "/den hangup", ChatFormatting.RED)), ChatFormatting.GREEN));
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
            Journey.milestone(p, Journey.DEN_CALL);
        }
        return true;
    }

    public static boolean decline(ServerPlayer callee) {
        Call c = CALLS.get(callee.getUUID());
        if (c == null || c.connected || c.callee != callee) return false;
        end(c);
        deliver(c.caller, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.declined", name(callee)), ChatFormatting.GRAY));
        deliver(callee, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.declined_self"), ChatFormatting.GRAY));
        return true;
    }

    /** Hang up (or stop ringing) whatever call the player is in. */
    public static boolean hangUp(ServerPlayer p) {
        Call c = CALLS.get(p.getUUID());
        if (c == null) {
            deliver(p, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.not_in_call"), ChatFormatting.GRAY));
            return false;
        }
        end(c);
        for (ServerPlayer q : List.of(c.caller, c.callee))
            deliver(q, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.ended", name(c.other(q))), ChatFormatting.GRAY));
        return true;
    }

    private static void end(Call c) {
        CALLS.remove(c.caller.getUUID(), c);
        CALLS.remove(c.callee.getUUID(), c);
    }

    /** Speak on a connected call: only the two parties hear it. False when the player is on no connected call. */
    public static boolean say(ServerPlayer from, String text) {
        Call c = CALLS.get(from.getUUID());
        if (c == null || !c.connected || text.isBlank()) return false;
        Component msg = line(DenDenMushiItem.Kind.PERSONAL, Component.empty().append(name(from).copy().withStyle(ChatFormatting.DARK_GREEN))
                .append(": ").append(text), ChatFormatting.GREEN);
        deliver(c.other(from), msg);
        deliver(from, msg);
        return true;
    }

    /** While on a call, chat goes down the line instead of to everyone. */
    @SubscribeEvent
    public static void onChat(ServerChatEvent e) {
        if (say(e.getPlayer(), e.getRawText())) e.setCanceled(true);
    }

    private static void ring(ServerPlayer p, long t) {
        float pitch = (t / 30) % 2 == 0 ? 1.4f : 1.2f;
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, pitch);
        p.displayClientMessage(Component.translatable("grandline.den.puru").withStyle(ChatFormatting.GREEN), true);
    }

    // ================================================================== crew and baby snails

    /**
     * Speak on the crew snail: every crewmate among {@code candidates} (same player crew, carrying any snail) hears it.
     * Returns how many players heard it (-1 without a crew snail).
     */
    public static int crewCall(ServerPlayer from, String text, Collection<? extends ServerPlayer> candidates) {
        DenDenMushiItem.Kind k = DenDenMushiItem.Kind.CREW;
        if (!carries(from, JourneyRegistry.CREW_DEN_DEN_MUSHI.get())) {
            deliver(from, line(k, Component.translatable("grandline.den.no_crew_snail"), ChatFormatting.GRAY));
            return -1;
        }
        CharacterData d = Characters.get(from);
        Component msg = line(k, Component.empty().append(name(from).copy().withStyle(ChatFormatting.YELLOW)).append(": ").append(text), ChatFormatting.GOLD);
        int heard = 0;
        if (d.crewId != null) {
            for (ServerPlayer p : candidates) {
                if (p == from || !d.crewId.equals(Characters.get(p).crewId) || !carriesAny(p)) continue;
                deliver(p, msg);
                heard++;
            }
        }
        int hands = CrewManager.members(from.server, d.characterId).size();
        deliver(from, msg);
        deliver(from, line(k, heard == 0 && hands == 0 ? Component.translatable("grandline.den.crew_none")
                : Component.translatable("grandline.den.crew_sent", heard, hands), ChatFormatting.GRAY));
        if (heard > 0) Journey.milestone(from, Journey.DEN_CREW);
        return heard;
    }

    /** A Baby Den Den carries one message to anyone (no snail needed there) and is used up. */
    public static boolean baby(ServerPlayer from, ServerPlayer to, String text) {
        DenDenMushiItem.Kind k = DenDenMushiItem.Kind.BABY;
        var inv = from.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getContainerSize() && slot < 0; i++) if (inv.getItem(i).is(JourneyRegistry.BABY_DEN_DEN_MUSHI.get())) slot = i;
        if (slot < 0) {
            deliver(from, line(k, Component.translatable("grandline.den.no_baby"), ChatFormatting.GRAY));
            return false;
        }
        if (text.isBlank()) return false;
        if (!from.isCreative()) inv.getItem(slot).shrink(1);
        deliver(to, line(k, Component.empty().append(name(from).copy().withStyle(ChatFormatting.DARK_PURPLE)).append(": ").append(text), ChatFormatting.LIGHT_PURPLE));
        deliver(from, line(k, Component.translatable("grandline.den.baby_sent", name(to)), ChatFormatting.GRAY));
        to.level().playSound(null, to.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5f, 1.8f);
        return true;
    }

    // ================================================================== the black snail

    /** Listen in on purpose (using the black snail, /den listen): every 30 s. */
    public static boolean listen(ServerPlayer sp) {
        DenDenMushiItem.Kind k = DenDenMushiItem.Kind.BLACK;
        if (!carries(sp, JourneyRegistry.BLACK_DEN_DEN_MUSHI.get())) {
            deliver(sp, line(k, Component.translatable("grandline.den.no_black"), ChatFormatting.GRAY));
            return false;
        }
        long now = sp.server.overworld().getGameTime();
        long next = LISTENED.getOrDefault(sp.getUUID(), 0L);
        if (next > now) {
            deliver(sp, line(k, Component.translatable("grandline.den.black_wait", (next - now) / 20 + 1), ChatFormatting.DARK_GRAY));
            return false;
        }
        LISTENED.put(sp.getUUID(), now + LISTEN_COOLDOWN);
        eavesdrop(sp);
        return true;
    }

    /**
     * What a black snail picks up around the player: Marine and bounty-hunter ships within {@link #SHIP_RANGE} (name,
     * distance, bearing and heading), Marines and bounty hunters on foot within {@link #FOOT_RANGE}, and Buster Call
     * orders. Sends the lines (or static) and returns them; empty without a black snail.
     */
    public static List<Component> eavesdrop(ServerPlayer sp) {
        DenDenMushiItem.Kind k = DenDenMushiItem.Kind.BLACK;
        List<Component> lines = new ArrayList<>();
        if (!carries(sp, JourneyRegistry.BLACK_DEN_DEN_MUSHI.get())) return lines;
        ServerLevel l = sp.serverLevel();
        List<ShipEntity> ships = l.getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(SHIP_RANGE, 64, SHIP_RANGE),
                s -> s.flag(ShipEntity.F_NPC) && !s.isSinking() && (s.npcFaction.equals(LivingSea.MARINE) || s.npcFaction.equals(LivingSea.HUNTER)));
        ships.sort(Comparator.comparingDouble(s -> s.distanceToSqr(sp)));
        for (ShipEntity s : ships.subList(0, Math.min(4, ships.size()))) {
            double dx = s.getX() - sp.getX(), dz = s.getZ() - sp.getZ();
            var h = s.heading();
            lines.add(Component.translatable("grandline.den.black_ship", s.shipName(), Component.translatable("grandline.den.side." + s.npcFaction),
                    (int) Math.sqrt(dx * dx + dz * dz), Navigation.compass(dx, dz), Navigation.compass(h.x, h.z)));
        }
        Map<Faction, int[]> foot = new EnumMap<>(Faction.class);
        Map<Faction, double[]> nearest = new EnumMap<>(Faction.class);
        for (GLNpc n : l.getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(FOOT_RANGE, 48, FOOT_RANGE),
                n -> n.isAlive() && (n.faction() == Faction.MARINE || n.faction() == Faction.BOUNTY_HUNTER) && !n.getTags().contains(LivingSea.CREW)
                        && !CrewManager.isCrew(n))) {
            foot.computeIfAbsent(n.faction(), f -> new int[1])[0]++;
            double dx = n.getX() - sp.getX(), dz = n.getZ() - sp.getZ(), d2 = dx * dx + dz * dz;
            double[] best = nearest.computeIfAbsent(n.faction(), f -> new double[]{Double.MAX_VALUE, 0, 0});
            if (d2 < best[0]) {
                best[0] = d2;
                best[1] = dx;
                best[2] = dz;
            }
        }
        for (var e : foot.entrySet()) {
            double[] b = nearest.get(e.getKey());
            lines.add(Component.translatable("grandline.den.black_foot", e.getValue()[0], Component.translatable("grandline.den.side." + e.getKey().id()),
                    (int) Math.sqrt(b[0]), Navigation.compass(b[1], b[2])));
        }
        lines.addAll(BusterCall.intercepts());
        if (lines.isEmpty()) deliver(sp, line(k, Component.translatable("grandline.den.black_static"), ChatFormatting.DARK_GRAY));
        else {
            for (Component c : lines) deliver(sp, line(k, c, ChatFormatting.GRAY));
            Journey.milestone(sp, Journey.DEN_EAVESDROP);
        }
        return lines;
    }

    /** The Marines' own traffic, overheard by every black snail at sea (reports, Buster Call orders). */
    public static void overheard(MinecraftServer server, Component what) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (carries(p, JourneyRegistry.BLACK_DEN_DEN_MUSHI.get())) deliver(p, line(DenDenMushiItem.Kind.BLACK, what, ChatFormatting.GRAY));
    }

    /** Chance that a Marine of this rank carries a black snail you can take when you beat them. */
    public static double blackSnailChance(String role) {
        return switch (role) {
            case "marine_officer" -> 0.08;
            case "marine_captain" -> 0.15;
            case "vice_admiral" -> 0.35;
            case "admiral" -> 0.5;
            default -> 0;
        };
    }

    /** Marine officers beaten by a player may drop their black snail. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof GLNpc npc) || npc.level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer)) return;
        double chance = blackSnailChance(npc.role());
        if (chance > 0 && npc.getRandom().nextDouble() < chance) npc.spawnAtLocation(stack(DenDenMushiItem.Kind.BLACK));
    }

    /** A Marine ship taken as a prize: half the time her captain's black snail is still in the chart room. */
    public static void onPrizeTaken(Player p, String fromFaction) {
        if (p instanceof ServerPlayer sp && fromFaction.equals(LivingSea.MARINE) && sp.getRandom().nextBoolean()) {
            if (!sp.getInventory().add(stack(DenDenMushiItem.Kind.BLACK))) sp.drop(stack(DenDenMushiItem.Kind.BLACK), false);
            deliver(sp, line(DenDenMushiItem.Kind.BLACK, Component.translatable("grandline.den.black_found"), ChatFormatting.GRAY));
        }
    }

    // ================================================================== player crews (/crew)

    /** Invite another player to sail in your crew (your crew is founded under your character on the first invite). */
    public static boolean invite(ServerPlayer captain, ServerPlayer who) {
        CharacterData d = Characters.get(captain);
        if (!d.created || captain == who) return false;
        if (d.crewId != null && d.crewId.equals(Characters.get(who).crewId)) {
            deliver(captain, Component.translatable("grandline.crew.same", name(who)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (d.crewId == null) {
            d.crewId = d.characterId;
            CharacterManager.markDirty(captain);
        }
        INVITES.put(who.getUUID(), new Invite(d.crewId, d.name, captain.server.overworld().getGameTime() + INVITE_TICKS));
        deliver(who, Component.translatable("grandline.crew.invited", name(captain)).append(" ")
                .append(button("grandline.crew.join_button", "/crew join", ChatFormatting.GREEN)).withStyle(ChatFormatting.GOLD));
        deliver(captain, Component.translatable("grandline.crew.invite_sent", name(who)).withStyle(ChatFormatting.GRAY));
        return true;
    }

    public static boolean join(ServerPlayer who) {
        Invite inv = INVITES.remove(who.getUUID());
        CharacterData d = Characters.get(who);
        if (inv == null || inv.until < who.server.overworld().getGameTime() || !d.created) {
            deliver(who, Component.translatable("grandline.crew.no_invite").withStyle(ChatFormatting.GRAY));
            return false;
        }
        d.crewId = inv.crew;
        CharacterManager.markDirty(who);
        deliver(who, Component.translatable("grandline.crew.joined_player", inv.captain).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : who.server.getPlayerList().getPlayers())
            if (p != who && inv.crew.equals(Characters.get(p).crewId)) deliver(p, Component.translatable("grandline.crew.new_mate", name(who)).withStyle(ChatFormatting.GOLD));
        return true;
    }

    public static boolean leave(ServerPlayer who) {
        CharacterData d = Characters.get(who);
        if (d.crewId == null) return false;
        d.crewId = null;
        CharacterManager.markDirty(who);
        deliver(who, Component.translatable("grandline.crew.left").withStyle(ChatFormatting.GRAY));
        return true;
    }

    // ================================================================== ticking and events

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        long now = server.overworld().getGameTime();
        if (!CALLS.isEmpty()) {
            for (Call c : new HashSet<>(CALLS.values())) {
                if (gone(c.caller) || gone(c.callee)) {
                    end(c);
                    for (ServerPlayer p : List.of(c.caller, c.callee)) if (!gone(p))
                        deliver(p, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.ended", name(c.other(p))), ChatFormatting.GRAY));
                } else if (!c.connected) {
                    if (now >= c.ringUntil) {
                        end(c);
                        deliver(c.caller, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.missed", name(c.callee)), ChatFormatting.GRAY));
                        deliver(c.callee, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.missed_callee", name(c.caller)), ChatFormatting.GRAY));
                    } else if ((c.ringUntil - now) % 30 == 0) ring(c.callee, c.ringUntil - now);
                } else if (now % 40 == 0 && (!carries(c.caller, JourneyRegistry.DEN_DEN_MUSHI.get()) || !carries(c.callee, JourneyRegistry.DEN_DEN_MUSHI.get()))) {
                    hangUp(c.caller); // a snail was dropped or put away in a chest: the line goes dead
                }
            }
        }
        // the black snail mutters now and then (about once a minute, half the time)
        if (now % 20 == 0) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if ((now / 20 + (sp.getId() & 63)) % 60 != 0 || sp.getRandom().nextFloat() > 0.5f) continue;
                if (carries(sp, JourneyRegistry.BLACK_DEN_DEN_MUSHI.get()) && Characters.get(sp).created) eavesdrop(sp);
            }
        }
    }

    private static boolean gone(ServerPlayer p) {
        return p.isRemoved() || p.hasDisconnected();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            Call c = CALLS.get(sp.getUUID());
            if (c != null) {
                end(c);
                ServerPlayer o = c.other(sp);
                deliver(o, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.ended", name(sp)), ChatFormatting.GRAY));
            }
            INVITES.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        CALLS.clear();
        LISTENED.clear();
        INVITES.clear();
    }

    // ================================================================== commands: /den and /crew (every player)

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var d = e.getDispatcher();
        d.register(Commands.literal("den")
                .executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    deliver(sp, line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.help"), ChatFormatting.GRAY));
                    return menu(sp, sp.server.getPlayerList().getPlayers());
                })
                .then(Commands.literal("call").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> call(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")) == Result.RINGING ? 1 : 0)))
                .then(Commands.literal("accept").executes(c -> accept(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("decline").executes(c -> decline(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("hangup").executes(c -> hangUp(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("say").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> say(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message")) ? 1 : notInCall(c))))
                .then(Commands.literal("crew").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            return Math.max(0, crewCall(sp, StringArgumentType.getString(c, "message"), sp.server.getPlayerList().getPlayers()));
                        })))
                .then(Commands.literal("baby").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> baby(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "message")) ? 1 : 0))))
                .then(Commands.literal("listen").executes(c -> listen(c.getSource().getPlayerOrException()) ? 1 : 0)));
        d.register(Commands.literal("crew")
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
                .then(Commands.literal("join").executes(c -> join(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("leave").executes(c -> leave(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("list").executes(DenDenMushi::crewList)));
    }

    private static int notInCall(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        deliver(c.getSource().getPlayerOrException(), line(DenDenMushiItem.Kind.PERSONAL, Component.translatable("grandline.den.not_in_call"), ChatFormatting.GRAY));
        return 0;
    }

    private static int crewList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        CharacterData d = Characters.get(sp);
        List<String> mates = new ArrayList<>();
        if (d.crewId != null)
            for (ServerPlayer p : sp.server.getPlayerList().getPlayers()) if (p != sp && d.crewId.equals(Characters.get(p).crewId)) mates.add(name(p).getString());
        int hands = CrewManager.members(sp.server, d.characterId).size();
        deliver(sp, Component.translatable("grandline.crew.list", mates.isEmpty() ? "-" : String.join(", ", mates), hands).withStyle(ChatFormatting.GOLD));
        return mates.size();
    }

    /** A snail stack for tests and loot. */
    public static ItemStack stack(DenDenMushiItem.Kind k) {
        return new ItemStack(JourneyRegistry.SNAILS.get(k.ordinal()).get());
    }

    private DenDenMushi() {}
}
