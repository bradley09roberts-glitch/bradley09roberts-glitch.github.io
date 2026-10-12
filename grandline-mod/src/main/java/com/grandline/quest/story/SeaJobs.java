package com.grandline.quest.story;

import com.grandline.GrandLine;
import com.grandline.navigation.Ferries;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.story.JobBoard.StartGift;
import com.grandline.ship.LivingSea;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Notice-board jobs at sea, and the escort job's travelling merchant.
 * <ul>
 *   <li><b>Escort</b> ({@link EscortJobs}): taking the job brings the merchant to your side; they follow you on foot
 *   (teleporting to you when left far behind on land, never into deep water), take a seat on your ship when you sail
 *   and step ashore when you land. At the destination's dock (where pirates lie in ambush) talk to them to finish.
 *   They can die (the job fails), and a long absence or {@code /jobs abandon} ends the job cleanly. An escort in
 *   progress is saved: a merchant stowed when you log out (or the server stops) steps off beside you again.</li>
 *   <li><b>Sink the &lt;ship&gt;</b> ({@link SeaBattleJobs}): a named pirate ship last seen in a stretch of open sea.
 *   She is put to sea (under the shared NPC ship cap) when someone with the job comes within range, and sinking or
 *   taking her completes it; Marines are paid a bonus for bringing her in as a prize, bounty hunters more.</li>
 *   <li><b>Convoy</b> ({@link SeaBattleJobs}): a merchantman sails from the board's island to another island's dock
 *   while you keep station with your own ship; pirates fall on her partway. She must make port; if she sinks, is
 *   overrun or is lost, the job fails. Her state is saved, so a convoy survives a restart.</li>
 * </ul>
 * Job data (who, where, which ship) travels in the board's {@link StartGift}s, generated from the seed with the rest
 * of the board. Objectives: {@code escort} / {@code sink} / {@code convoy} with the job's (base) id as target.
 * Sea battles in general count for {@code sea_battle} objectives (QuestEvents.onSeaBattleWon), used by the classic
 * board job "Privateer's Commission" registered here.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class SeaJobs {
    public static final String ESCORT = "escort", SINK = "sink", CONVOY = "convoy";
    /** Ticks a player may stay logged out before an escort or convoy is called off (10 minutes of server time). */
    public static long abandonTicks = 12000;
    /** Test hook: messages sent to players by sea jobs. */
    @Nullable public static BiConsumer<ServerPlayer, Component> tap;
    /** Leaders seen this session, by character id (fake players in the self-test included). */
    static final Map<UUID, ServerPlayer> LEADERS = new HashMap<>();

    // ================================================================== generation (pure)

    /** The sea jobs of one board (called at the end of {@link JobBoard.Gen#all}). */
    static void generate(JobBoard.Gen g, List<Island> near) {
        sink(g);
        convoy(g, near);
    }

    private static void sink(JobBoard.Gen g) {
        StoryContext c = g.c;
        Island isl = g.isl;
        if (!LivingSea.openZone(isl.zone)) return;
        int[] area = seaArea(g.plan, isl, c.r);
        if (area == null) return;
        String name = PeopleNames.crew(c.r);
        ShipClass cls = pirateClass(isl.zone, isl.tier);
        int tier = Math.max(2, Math.min(10, isl.tier + 1));
        double dist = Math.hypot(area[0] - isl.x, area[1] - isl.z);
        double w = 1.6 + dist / 1500 + cls.ordinal() * 0.3;
        String id = g.id(SINK, -1);
        Object[] args = {name, c.islandName(), area[0], area[1]};
        List<QuestDef.Objective> obj = List.of(new QuestDef.Objective(SINK, id, 1));
        g.quest(id, SINK, obj, w, Map.of("civilian", 10, "island/" + isl.id, 10), List.of(), List.of(), true, args, null);
        g.quest(id + ".marine", SINK, obj, w * 1.2, Map.of("marine", 40, "civilian", 10), List.of(), List.of(), true, args, "marine");
        g.quest(id + ".bounty_hunter", SINK, obj, w * 1.5, Map.of("bounty_hunter", 40), List.of(), List.of(), true, args, "bounty_hunter");
        for (String v : List.of(id, id + ".marine", id + ".bounty_hunter"))
            g.gifts.put(v, new StartGift(SINK, name, c.islandName(), area[0], area[1], tier, cls.id()));
    }

    private static void convoy(JobBoard.Gen g, List<Island> near) {
        Island isl = g.isl;
        if (!LivingSea.openZone(isl.zone) || !isl.settlement().hasDock) return;
        Island dest = null;
        for (Island y : g.c.neighbours(16, false)) {
            if (y.zone != isl.zone || !port(y)) continue;
            double d = y.distance(isl.x, isl.z);
            if (d < 300 || d > 3600 || !clearWay(Ferries.dockOf(isl), Ferries.dockOf(y), isl.zone)) continue;
            dest = y;
            break;
        }
        if (dest == null) return;
        String name = PeopleNames.ship(g.c.r);
        double dist = dest.distance(isl.x, isl.z);
        double w = 1.2 + dist / 1500;
        String id = g.id(CONVOY, -1);
        Object[] args = {name, StoryContext.islandName(dest), g.c.islandName()};
        List<QuestDef.Objective> obj = List.of(new QuestDef.Objective(CONVOY, id, 1));
        g.quest(id, CONVOY, obj, w, Map.of("civilian", 15, "island/" + dest.id, 10), List.of(), List.of(), true, args, null);
        g.quest(id + ".marine", CONVOY, obj, w * 1.25, Map.of("marine", 30, "civilian", 10), List.of(), List.of(), true, args, "marine");
        Vec3 dock = Ferries.dockOf(dest);
        for (String v : List.of(id, id + ".marine"))
            g.gifts.put(v, new StartGift(CONVOY, name, StoryContext.islandName(dest), (int) dock.x, (int) dock.z, 0, dest.id));
    }

    /** Open sea 320–760 blocks off the island in its own sea, clear of land and harbours (null if none). */
    @Nullable
    static int[] seaArea(WorldPlan plan, Island isl, net.minecraft.util.RandomSource r) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int d = isl.radius + 320; d <= isl.radius + 760; d += 110)
            for (int i = 0; i < 12; i++) {
                double a = a0 + i * Math.PI / 6;
                int x = isl.x + (int) (Math.cos(a) * d), z = isl.z + (int) (Math.sin(a) * d);
                if (WorldLayout.zoneAt(x, z) != isl.zone || (Math.abs(x) < 1500 && Math.abs(z) < 1500)) continue;
                boolean open = true;
                for (int ox = -60; ox <= 60 && open; ox += 20)
                    for (int oz = -60; oz <= 60 && open; oz += 20) {
                        plan.sample(x + ox, z + oz, smp);
                        if (smp.height > WorldPlan.SEA_LEVEL - 4 || smp.redLine || smp.canal) open = false;
                    }
                if (open && !LivingSea.inHarbour(plan, x, z)) return new int[]{x, z};
            }
        return null;
    }

    static ShipClass pirateClass(SeaZone zone, int tier) {
        if (zone == SeaZone.NEW_WORLD) return ShipClass.GALLEON;
        if (zone.isGrandLine()) return tier >= 4 ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
        return tier >= 2 ? ShipClass.CARAVEL : ShipClass.SLOOP;
    }

    static boolean port(Island i) {
        return i.settlementType != SettlementType.NONE && i.settlementType != SettlementType.RUINS && i.settlement().hasDock;
    }

    /** The straight course stays in one sea (never through the Calm Belt or over a Red Line). */
    static boolean clearWay(Vec3 a, Vec3 b, SeaZone zone) {
        int steps = (int) Math.ceil(a.distanceTo(b) / 100);
        for (int k = 0; k <= steps; k++) {
            double t = steps == 0 ? 0 : k / (double) steps;
            if (WorldLayout.zoneAt((int) (a.x + (b.x - a.x) * t), (int) (a.z + (b.z - a.z) * t)) != zone) return false;
        }
        return true;
    }

    /** Classic board job won at sea: beat a pirate ship (Marines and civilians), or for pirates a Marine ship. */
    public static void register(Map<String, QuestDef> all) {
        all.put("board_privateer", new QuestDef("board_privateer", JobBoard.GIVER, "*", List.of(new QuestDef.Objective("sea_battle", "pirate", 1)),
                600, 4000, 0, Map.of("marine", 15, "civilian", 5), List.of(), List.of(), null, null, true, "board"));
        all.put("board_privateer.pirate", new QuestDef("board_privateer.pirate", JobBoard.GIVER, "*", List.of(new QuestDef.Objective("sea_battle", "marine", 1)),
                700, 3000, 4_000_000, Map.of("pirate", 20), List.of(), List.of(), null, null, true, "board"));
    }

    // ================================================================== hooks

    /** Hook from {@link JobBoard#onStarted}: true when this is a sea job (it then sets itself up). */
    static boolean onStarted(ServerPlayer sp, QuestDef q, StartGift g) {
        switch (g.kind()) {
            case ESCORT -> EscortJobs.start(sp, q, g);
            case CONVOY -> SeaBattleJobs.startConvoy(sp, q, g);
            case SINK -> tell(sp, Component.translatable("job.grandline.sink.where", g.name(), g.where(), g.x(), g.z()).withStyle(ChatFormatting.GRAY));
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Hook from QuestEvents.onTalk: an escorted merchant answers. */
    public static void onTalk(ServerPlayer sp, GLNpc npc) {
        if (npc.getTags().contains(EscortJobs.TAG)) EscortJobs.onTalk(sp, npc);
    }

    /** Hook from LivingSea.onSinking: a named pirate ship went down, or a convoy's merchantman. */
    public static void onShipSinking(ServerLevel sl, ShipEntity ship) {
        if (!ship.getTags().isEmpty()) SeaBattleJobs.onSinking(sl, ship);
    }

    /** Hook from NavalCombat.capture: a named pirate ship (or a convoy's merchantman) was taken as a prize. */
    public static void onShipCaptured(ServerPlayer sp, ShipEntity ship) {
        if (!ship.getTags().isEmpty()) SeaBattleJobs.onCaptured(sp, ship);
    }

    // ================================================================== ticking and events

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        ServerLevel ow = s.overworld();
        if (ow == null) return;
        long now = ow.getGameTime();
        Data data = data(s);
        if (!data.convoys.isEmpty()) SeaBattleJobs.drive(s, data, now);
        EscortJobs.tickRetired(s, now);
        if (now % 10 != 3) return;
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) if (!sp.isSpectator()) tickPlayer(sp);
    }

    /** One pass for a player (every half second; the self-test calls it for its fake players). */
    public static void tickPlayer(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (!d.created && !(sp instanceof net.neoforged.neoforge.common.util.FakePlayer)) return;
        LEADERS.put(d.characterId, sp);
        Data data = data(sp.server);
        long now = sp.server.overworld().getGameTime();
        for (Escort e : List.copyOf(data.escorts.values())) if (e.character.equals(d.characterId)) EscortJobs.tick(sp, d, data, e, now);
        for (Convoy c : List.copyOf(data.convoys.values())) if (c.character.equals(d.characterId)) SeaBattleJobs.tickConvoy(sp, d, data, c, now);
        if (d.questProgress.isEmpty()) return;
        for (String q : List.copyOf(d.questProgress.keySet())) {
            StartGift g = JobBoard.gift(q);
            if (g != null && g.kind().equals(SINK)) SeaBattleJobs.tickSink(sp, q, g, now);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) loggedOut(sp);
    }

    /** A player leaves (or the server stops): escorted merchants are stowed, convoys hold position. */
    public static void loggedOut(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        Data data = data(sp.server);
        long now = sp.server.overworld().getGameTime();
        for (Escort e : data.escorts.values())
            if (e.character.equals(d.characterId)) {
                EscortJobs.stow(sp.server, data, e);
                e.logoutAt = now;
                e.away = true;
            }
        for (Convoy c : data.convoys.values())
            if (c.character.equals(d.characterId)) {
                c.logoutAt = now;
                c.away = true;
                c.escorted = false;
                data.setDirty();
            }
        LEADERS.remove(d.characterId);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof GLNpc npc && !npc.level().isClientSide && npc.getTags().contains(EscortJobs.TAG)) EscortJobs.died(npc);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        LEADERS.clear();
        cached = null;
        cachedFor = null;
        SeaBattleJobs.clear();
        EscortJobs.clear();
    }

    // ================================================================== shared helpers

    /** The online player (or test fake player) leading a character's jobs. */
    @Nullable
    static ServerPlayer leader(MinecraftServer server, UUID character, UUID player) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p == null) p = LEADERS.get(character);
        if (p == null || p.isRemoved() || !character.equals(Characters.get(p).characterId)) return null;
        return p;
    }

    static void tell(ServerPlayer sp, Component msg) {
        if (tap != null) tap.accept(sp, msg);
        sp.sendSystemMessage(msg);
    }

    /** A job ends without success: the quest is dropped (with a message saying why). */
    static void fail(@Nullable ServerPlayer sp, String quest, Component why) {
        if (sp == null) return;
        CharacterData d = Characters.get(sp);
        QuestDef q = QuestRegistry.get(quest);
        if (d.questProgress.remove(quest) == null) return;
        GrandLine.LOGGER.info("Sea job {} of {} failed: {}", quest, sp.getName().getString(), why.getString());
        tell(sp, why.copy().withStyle(ChatFormatting.RED));
        tell(sp, Component.translatable("job.grandline.failed", q == null ? Component.literal(quest) : QuestRegistry.title(q)).withStyle(ChatFormatting.RED));
        CharacterManager.markDirty(sp);
    }

    /** Whether a character has a job (any faction version) of this base id in progress; returns that version or null. */
    @Nullable
    static String active(CharacterData d, String base) {
        for (String k : d.questProgress.keySet()) if (QuestRegistry.baseId(k).equals(base)) return k;
        return null;
    }

    /** Give up a job: the quest is dropped and its merchant, convoy or quarry is let go. */
    public static boolean abandon(ServerPlayer sp, String quest) {
        CharacterData d = Characters.get(sp);
        QuestDef q = QuestRegistry.get(quest);
        if (q == null || !d.questProgress.containsKey(quest) || !q.giverRole().equals(JobBoard.GIVER)) return false;
        d.questProgress.remove(quest);
        Data data = data(sp.server);
        Escort e = data.escorts.remove(key(d.characterId, quest));
        if (e != null) EscortJobs.dismiss(sp.server, e);
        Convoy c = data.convoys.remove(key(d.characterId, quest));
        if (c != null) SeaBattleJobs.release(sp.server, c, true); // she sails on alone
        data.setDirty();
        CharacterManager.markDirty(sp);
        tell(sp, Component.translatable("job.grandline.abandoned", QuestRegistry.title(q)).withStyle(ChatFormatting.GRAY));
        return true;
    }

    static String key(UUID character, String quest) {
        return character + "|" + quest;
    }

    static MutableComponent quoted(String who, Component line) {
        return Component.translatable("job.grandline.says", who, line);
    }

    // ================================================================== /jobs

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("jobs")
                .executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    CharacterData d = Characters.get(sp);
                    int n = 0;
                    for (String id : d.questProgress.keySet()) {
                        QuestDef q = QuestRegistry.get(id);
                        if (q == null || !q.giverRole().equals(JobBoard.GIVER)) continue;
                        sp.sendSystemMessage(Component.translatable("job.grandline.list_entry", QuestRegistry.title(q), id).withStyle(ChatFormatting.AQUA));
                        n++;
                    }
                    if (n == 0) sp.sendSystemMessage(Component.translatable("job.grandline.list_empty").withStyle(ChatFormatting.GRAY));
                    return n;
                })
                .then(Commands.literal("abandon").then(Commands.argument("job", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            ServerPlayer sp = c.getSource().getPlayer();
                            List<String> ids = new ArrayList<>();
                            if (sp != null) for (String id : Characters.get(sp).questProgress.keySet()) {
                                QuestDef q = QuestRegistry.get(id);
                                if (q != null && q.giverRole().equals(JobBoard.GIVER)) ids.add(id);
                            }
                            return SharedSuggestionProvider.suggest(ids, b);
                        })
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            if (abandon(sp, StringArgumentType.getString(c, "job"))) return 1;
                            c.getSource().sendFailure(Component.translatable("job.grandline.no_such_job"));
                            return 0;
                        }))));
    }

    // ================================================================== saved state

    /** An escort in progress: whose, which job, the merchant's entity or stowed copy. */
    public static final class Escort {
        public final UUID character;
        public UUID player;
        public final String quest;
        @Nullable public UUID entity;
        @Nullable CompoundTag stowed, snapshot;
        public long logoutAt;
        /** Logged out (stowed) since logoutAt. */
        public boolean away;
        @Nullable UUID seatedOn;
        public boolean arrived;
        boolean failed;
        String failKey = "";
        int missing;
        long snapAt;

        Escort(UUID character, UUID player, String quest) {
            this.character = character;
            this.player = player;
            this.quest = quest;
        }

        public boolean stowed() {
            return stowed != null;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("character", character);
            t.putUUID("player", player);
            t.putString("quest", quest);
            if (entity != null) t.putUUID("entity", entity);
            if (stowed != null) t.put("stowed", stowed);
            if (snapshot != null) t.put("snapshot", snapshot);
            t.putLong("logoutAt", logoutAt);
            t.putBoolean("away", away);
            t.putBoolean("arrived", arrived);
            t.putBoolean("failed", failed);
            t.putString("failKey", failKey);
            return t;
        }

        static Escort load(CompoundTag t) {
            Escort e = new Escort(t.getUUID("character"), t.getUUID("player"), t.getString("quest"));
            if (t.hasUUID("entity")) e.entity = t.getUUID("entity");
            if (t.contains("stowed")) e.stowed = t.getCompound("stowed");
            if (t.contains("snapshot")) e.snapshot = t.getCompound("snapshot");
            e.logoutAt = t.getLong("logoutAt");
            e.away = t.getBoolean("away");
            e.arrived = t.getBoolean("arrived");
            e.failed = t.getBoolean("failed");
            e.failKey = t.getString("failKey");
            return e;
        }
    }

    /** A convoy in progress: the merchantman's last known state (she is rebuilt from it after a restart). */
    public static final class Convoy {
        public final UUID character;
        public UUID player;
        public final String quest;
        @Nullable public UUID ship, raider;
        /** 0 not yet launched, 1 waiting for her escort, 2 under way. */
        public int stage;
        public double x, z, total;
        public float yaw, hull = -1;
        public boolean raided, escorted;
        public long logoutAt, awaySince = -1, raidNext;
        public boolean away;
        int raidTries, near, pinned;
        boolean failed;
        String failKey = "";

        Convoy(UUID character, UUID player, String quest) {
            this.character = character;
            this.player = player;
            this.quest = quest;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("character", character);
            t.putUUID("player", player);
            t.putString("quest", quest);
            if (ship != null) t.putUUID("ship", ship);
            t.putInt("stage", stage);
            t.putDouble("x", x);
            t.putDouble("z", z);
            t.putDouble("total", total);
            t.putFloat("yaw", yaw);
            t.putFloat("hull", hull);
            t.putBoolean("raided", raided);
            t.putLong("logoutAt", logoutAt);
            t.putBoolean("away", away);
            t.putBoolean("failed", failed);
            t.putString("failKey", failKey);
            return t;
        }

        static Convoy load(CompoundTag t) {
            Convoy c = new Convoy(t.getUUID("character"), t.getUUID("player"), t.getString("quest"));
            if (t.hasUUID("ship")) c.ship = t.getUUID("ship");
            c.stage = t.getInt("stage");
            c.x = t.getDouble("x");
            c.z = t.getDouble("z");
            c.total = t.getDouble("total");
            c.yaw = t.getFloat("yaw");
            c.hull = t.getFloat("hull");
            c.raided = t.getBoolean("raided");
            c.logoutAt = t.getLong("logoutAt");
            c.away = t.getBoolean("away");
            c.failed = t.getBoolean("failed");
            c.failKey = t.getString("failKey");
            return c;
        }
    }

    public static final class Data extends SavedData {
        final Map<String, Escort> escorts = new LinkedHashMap<>();
        final Map<String, Convoy> convoys = new LinkedHashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag es = new ListTag(), cs = new ListTag();
            for (Escort e : escorts.values()) es.add(e.save());
            for (Convoy c : convoys.values()) cs.add(c.save());
            tag.put("escorts", es);
            tag.put("convoys", cs);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            for (Tag t : tag.getList("escorts", Tag.TAG_COMPOUND)) {
                Escort e = Escort.load((CompoundTag) t);
                d.escorts.put(key(e.character, e.quest), e);
            }
            for (Tag t : tag.getList("convoys", Tag.TAG_COMPOUND)) {
                Convoy c = Convoy.load((CompoundTag) t);
                d.convoys.put(key(c.character, c.quest), c);
            }
            return d;
        }
    }

    private static Data cached;
    private static MinecraftServer cachedFor;

    public static Data data(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cached = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), GrandLine.MODID + "_seajobs");
        cachedFor = server;
        return cached;
    }

    // ================================================================== read-only views (self-test)

    @Nullable
    public static Escort escortOf(MinecraftServer server, UUID character, String quest) {
        return data(server).escorts.get(key(character, quest));
    }

    @Nullable
    public static Convoy convoyOf(MinecraftServer server, UUID character, String quest) {
        return data(server).convoys.get(key(character, quest));
    }

    /** The escorted merchant of an escort in the world now, or null (stowed, not placed yet). */
    @Nullable
    public static com.grandline.npc.HumanoidNpc merchantOf(MinecraftServer server, Escort e) {
        return EscortJobs.find(server, e.entity);
    }

    /** The convoy's merchantman afloat now, or null. */
    @Nullable
    public static ShipEntity shipOf(MinecraftServer server, Convoy c) {
        return SeaBattleJobs.convoyShip(server.overworld(), c);
    }

    /** A job's named pirate ship afloat now (base job id), or null. */
    @Nullable
    public static ShipEntity namedShip(ServerLevel l, String base) {
        return SeaBattleJobs.named(l, base);
    }

    /** Whether an objective names something these jobs report progress for (self-test validity checks). */
    public static boolean progresses(String type, String target) {
        if (type.equals("sea_battle")) return Set.of("any", LivingSea.PIRATE, LivingSea.MARINE, LivingSea.HUNTER, LivingSea.MERCHANT).contains(target);
        StartGift g = JobBoard.gift(target);
        return g != null && g.kind().equals(type);
    }

    /** Self-test counters: {teleports, boardings, landings, named ships put to sea, spawns refused by the NPC ship cap}. */
    public static int[] counters() {
        return new int[]{EscortJobs.teleports, EscortJobs.boardings, EscortJobs.landings, SeaBattleJobs.namedSpawned, SeaBattleJobs.refusedByCap};
    }

    /** Self-test: ships put to sea by these jobs never despawn on their own (fake players do not count as "near"). */
    public static boolean keepShips;

    /** Self-test: forget when named ships were last tried (so the next pass may put one to sea at once). */
    public static void resetSinkTimers() {
        SeaBattleJobs.clearTimers();
    }

    /**
     * Self-test shortcut for the generic job player (IslandStoryTests.playQuest): bring the escorted merchant to the
     * destination's dock and talk to them there. Returns a problem, or null. (SeaJobTests plays the real journey.)
     */
    @Nullable
    public static String deliverForTest(ServerPlayer sp, String quest) {
        StartGift g = JobBoard.gift(quest);
        if (g == null || !g.kind().equals(ESCORT)) return "no escort job " + quest;
        CharacterData d = Characters.get(sp);
        Escort e = data(sp.server).escorts.get(key(d.characterId, quest));
        if (e == null) return "no escort in progress for " + quest;
        ServerLevel l = sp.server.overworld();
        net.minecraft.core.BlockPos at = com.grandline.npc.notable.NotableSpawner.findStand(l, g.x(), g.z(), 12);
        if (at == null) return "no footing at the dock of " + g.target();
        double x = sp.getX(), y = sp.getY(), z = sp.getZ();
        try {
            sp.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            com.grandline.npc.HumanoidNpc m = EscortJobs.find(sp.server, e.entity);
            if (m == null) m = EscortJobs.bring(sp, e, e.stowed != null ? e.stowed : e.snapshot, false);
            if (m == null) return "the merchant could not be brought to the dock";
            m.stopRiding();
            m.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            com.grandline.quest.QuestEvents.onTalk(sp, m);
        } finally {
            sp.moveTo(x, y, z);
        }
        return null;
    }

    private SeaJobs() {}
}
