package com.grandline.base;

import com.grandline.GrandLine;
import com.grandline.activity.Meals;
import com.grandline.combat.CombatState;
import com.grandline.core.GLConfig;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.story.IslandStories;
import com.grandline.quest.story.StoryContext;
import com.grandline.quest.story.StoryModel;
import com.grandline.registry.ModEffects;
import com.grandline.ship.BaseRaids;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.IslandShape;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Crew bases on claimed islands. A player (or the player crew they sail in, see {@code /crew}) plants a crew flag at
 * the heart of an uninhabited original island (not canon, no settlement, not a starter island, ocean world only), after
 * clearing any threat there and away from the island's story places; the island is then the crew's (one claim per crew
 * by default), saved per world, announced, and shown on everyone's sea chart under the crew's name.
 * <ul>
 *   <li>Building: inside the claim (a circle round the flag, story places and island secrets left out) only the crew
 *   may break and place blocks, open containers, pour buckets or light fires; town protection is a separate, unchanged
 *   rule that still applies everywhere (a base never lifts it).</li>
 *   <li>The base chest is a warehouse shared by the whole crew; the dock spot keeps the crew's ships repaired for free;
 *   upgrades ({@link BaseUpgrades}) add a watchtower, dojo, galley, medical bay, shipyard slip and quarters for hired
 *   hands ({@link BaseHands}).</li>
 *   <li>Raids ({@link BaseRaids}) may come while the crew is near; if the defenders fall the flag can be captured, and
 *   after a grace period the claim is lost.</li>
 * </ul>
 * Everything is server-side; the per-claim work runs every few seconds and only where crew members are near.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CrewBases {
    /** Entity tags: a hired hand of a base, a raider landed on a base, and which base ({@code OF} + island id). */
    public static final String HAND = "gl_base_hand", RAIDER = "gl_base_raider", OF = "gl_base_of:";
    public static final int WAREHOUSE_SIZE = 54;
    /** Crew members within this distance of the flag keep the base's services and raids going. */
    public static final double NEAR = 160;

    /** Tests: sees every base message sent to a player (players outside the player list included). */
    @Nullable public static BiConsumer<ServerPlayer, Component> tap;
    /** Tests: sees every server-wide base announcement. */
    @Nullable public static Consumer<Component> announced;
    /** Tests: extra players counted as online (fake players are not in the player list). */
    @Nullable public static Supplier<List<? extends ServerPlayer>> testPlayers;

    // ================================================================== saved data

    /** One crew's claim on one island. */
    public static final class Claim {
        public final String island;
        public UUID crew;
        public UUID founder;
        public String founderName = "", crewName = "";
        public BlockPos flag;
        public boolean hasDock;
        public int dockX, dockZ;
        public long claimedAt, raidReadyAt;
        public int raidsRepelled;
        public final Set<String> upgrades = new LinkedHashSet<>();
        /** Roles of the hands hired and alive (each lives at the base). */
        public final List<String> hands = new ArrayList<>();
        /** Player ids seen sailing in this crew (their ships count as crew ships while they are offline). */
        public final Set<UUID> members = new LinkedHashSet<>();
        public final SimpleContainer warehouse;
        @Nullable Data owner;
        // ---- transient ----
        /** Hands on the ground now: entity id → role. */
        final Map<UUID, String> spawnedHands = new LinkedHashMap<>();
        @Nullable List<int[]> exempt;

        Claim(String island) {
            this.island = island;
            this.warehouse = new SimpleContainer(WAREHOUSE_SIZE) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    dirty();
                }
            };
        }

        public boolean has(String upgrade) {
            return upgrades.contains(upgrade);
        }

        /** The crew's name as shown: the name given (anvil-named flag, /base name) or "&lt;founder&gt;'s Crew". */
        public Component crewName() {
            return crewName.isEmpty() ? Component.translatable("grandline.base.default_crew", founderName) : Component.literal(crewName);
        }

        public void dirty() {
            if (owner != null) owner.setDirty();
        }

        CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag t = new CompoundTag();
            t.putString("island", island);
            t.putUUID("crew", crew);
            t.putUUID("founder", founder);
            t.putString("founderName", founderName);
            t.putString("crewName", crewName);
            t.putLong("flag", flag.asLong());
            t.putBoolean("hasDock", hasDock);
            t.putInt("dockX", dockX);
            t.putInt("dockZ", dockZ);
            t.putLong("claimedAt", claimedAt);
            t.putLong("raidReadyAt", raidReadyAt);
            t.putInt("repelled", raidsRepelled);
            ListTag ups = new ListTag();
            for (String u : upgrades) ups.add(StringTag.valueOf(u));
            t.put("upgrades", ups);
            ListTag hs = new ListTag();
            for (String h : hands) hs.add(StringTag.valueOf(h));
            t.put("hands", hs);
            ListTag ms = new ListTag();
            for (UUID u : members) ms.add(StringTag.valueOf(u.toString()));
            t.put("members", ms);
            CompoundTag wh = new CompoundTag();
            ContainerHelper.saveAllItems(wh, warehouse.getItems(), provider);
            t.put("warehouse", wh);
            return t;
        }

        static Claim load(CompoundTag t, HolderLookup.Provider provider) {
            Claim c = new Claim(t.getString("island"));
            c.crew = t.getUUID("crew");
            c.founder = t.getUUID("founder");
            c.founderName = t.getString("founderName");
            c.crewName = t.getString("crewName");
            c.flag = BlockPos.of(t.getLong("flag"));
            c.hasDock = t.getBoolean("hasDock");
            c.dockX = t.getInt("dockX");
            c.dockZ = t.getInt("dockZ");
            c.claimedAt = t.getLong("claimedAt");
            c.raidReadyAt = t.getLong("raidReadyAt");
            c.raidsRepelled = t.getInt("repelled");
            for (Tag u : t.getList("upgrades", Tag.TAG_STRING)) c.upgrades.add(u.getAsString());
            for (Tag h : t.getList("hands", Tag.TAG_STRING)) c.hands.add(h.getAsString());
            for (Tag m : t.getList("members", Tag.TAG_STRING)) {
                try {
                    c.members.add(UUID.fromString(m.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            NonNullList<ItemStack> items = NonNullList.withSize(WAREHOUSE_SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(t.getCompound("warehouse"), items, provider);
            for (int i = 0; i < WAREHOUSE_SIZE; i++) c.warehouse.getItems().set(i, items.get(i));
            return c;
        }
    }

    public static final class Data extends SavedData {
        final Map<String, Claim> byIsland = new LinkedHashMap<>();

        void put(Claim c) {
            c.owner = this;
            byIsland.put(c.island, c);
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag l = new ListTag();
            for (Claim c : byIsland.values()) l.add(c.save(provider));
            tag.put("claims", l);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            for (Tag t : tag.getList("claims", Tag.TAG_COMPOUND)) {
                Claim c = Claim.load((CompoundTag) t, provider);
                c.owner = d;
                d.byIsland.put(c.island, c);
            }
            return d;
        }
    }

    private static Data cached;
    private static MinecraftServer cachedFor;

    public static Data data(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cached = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), GrandLine.MODID + "_bases");
        cachedFor = server;
        return cached;
    }

    public static Collection<Claim> claims(MinecraftServer server) {
        return Collections.unmodifiableCollection(data(server).byIsland.values());
    }

    // ================================================================== crews and lookups

    /** The crew a player's character sails in: their player crew, or their own character as a crew of one. */
    @Nullable
    public static UUID crewKey(Player p) {
        CharacterData d = Characters.get(p);
        if (!d.created) return null;
        return d.crewId != null ? d.crewId : d.characterId;
    }

    public static boolean isCrew(Player p, Claim c) {
        return c.crew.equals(crewKey(p));
    }

    @Nullable
    public static Claim claimOn(MinecraftServer server, String islandId) {
        return data(server).byIsland.get(islandId);
    }

    /** Claims of a crew (normally at most one). */
    public static List<Claim> claimsOf(MinecraftServer server, @Nullable UUID crew) {
        List<Claim> out = new ArrayList<>();
        if (crew == null) return out;
        for (Claim c : data(server).byIsland.values()) if (c.crew.equals(crew)) out.add(c);
        return out;
    }

    /** The player's crew's base (the first, if a server allows several). */
    @Nullable
    public static Claim baseOf(Player p) {
        List<Claim> l = claimsOf(p.getServer(), crewKey(p));
        return l.isEmpty() ? null : l.get(0);
    }

    @Nullable
    public static Claim claimAtFlag(MinecraftServer server, BlockPos pos) {
        for (Claim c : data(server).byIsland.values()) if (c.flag.equals(pos)) return c;
        return null;
    }

    public static int radius() {
        return GLConfig.BASE_RADIUS.get();
    }

    /** Inside the claim's circle round the flag (story places included: use {@link #covers} for protection). */
    public static boolean within(Claim c, double x, double z) {
        double dx = x - (c.flag.getX() + 0.5), dz = z - (c.flag.getZ() + 0.5);
        double r = radius();
        return dx * dx + dz * dz <= r * r;
    }

    /** The claim whose protected area covers (x, z) in this level, or null (ocean world overworld only). */
    @Nullable
    public static Claim claimAt(Level level, int x, int z) {
        if (!(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return null;
        Data d = data(sl.getServer());
        if (d.byIsland.isEmpty()) return null;
        for (Claim c : d.byIsland.values()) if (within(c, x + 0.5, z + 0.5) && !exempt(sl.getServer(), c, x, z)) return c;
        return null;
    }

    /** The player's own base, if the player stands inside it. */
    @Nullable
    public static Claim inOwnBase(Player p) {
        if (p.level().isClientSide || p.level().dimension() != Level.OVERWORLD) return null;
        Claim c = baseOf(p);
        return c != null && within(c, p.getX(), p.getZ()) && Math.abs(p.getY() - c.flag.getY()) < 64 ? c : null;
    }

    // ================================================================== eligibility

    /** Why a flag cannot be planted here: a translation key and its arguments. */
    public record Refusal(String key, Object... args) {}

    /** Null if {@code sp} may plant the crew flag at {@code pos}, else the reason. */
    @Nullable
    public static Refusal check(ServerPlayer sp, BlockPos pos) {
        if (!GLConfig.BASES.get()) return new Refusal("grandline.base.refuse.disabled");
        UUID crew = crewKey(sp);
        if (crew == null) return new Refusal("grandline.base.refuse.no_character");
        ServerLevel l = sp.serverLevel();
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null || plan.kind != WorldPlan.Kind.OCEAN || l.dimension() != Level.OVERWORLD) return new Refusal("grandline.base.refuse.wrong_world");
        Island isl = plan.islandAt(pos.getX(), pos.getZ());
        if (isl == null || isl.t(pos.getX(), pos.getZ()) >= 1.0) return new Refusal("grandline.base.refuse.not_island");
        Component name = StoryContext.islandName(isl);
        if (isl.canon || isl.starterFor != null) return new Refusal("grandline.base.refuse.canon", name);
        if (isl.settlementType != SettlementType.NONE) return new Refusal("grandline.base.refuse.settled", name);
        Claim there = claimOn(sp.server, isl.id);
        if (there != null) return there.crew.equals(crew) ? new Refusal("grandline.base.refuse.yours", name) : new Refusal("grandline.base.refuse.taken", name, there.crewName());
        int have = claimsOf(sp.server, crew).size(), max = GLConfig.BASE_CLAIMS_PER_CREW.get();
        if (have >= max) return new Refusal("grandline.base.refuse.limit", max);
        if (!heart(isl, pos.getX(), pos.getZ())) return new Refusal("grandline.base.refuse.not_heart", name);
        if (pos.getY() <= WorldPlan.SEA_LEVEL) return new Refusal("grandline.base.refuse.not_heart", name);
        int[] place = storyPlaceNear(sp.server, plan, isl, pos.getX(), pos.getZ(), 8);
        if (place != null) return new Refusal("grandline.base.refuse.story", (int) Math.sqrt(dist2(place, pos.getX(), pos.getZ())));
        int threats = threats(sp, pos);
        if (threats > 0) return new Refusal("grandline.base.refuse.threat", threats);
        return null;
    }

    /** The heart of an island: its inner part (rings and atolls: the inner rim of their land). */
    public static boolean heart(Island isl, int x, int z) {
        double limit = isl.shape == IslandShape.RING || isl.shape == IslandShape.ATOLL ? 0.95 : 0.7;
        return isl.t(x, z) <= limit;
    }

    /** Whether an island can be claimed at all (never canon, settled, a starter island, the sky or the deep sea). */
    public static boolean claimable(Island isl) {
        return !isl.canon && isl.starterFor == null && isl.settlementType == SettlementType.NONE && isl.plan().kind == WorldPlan.Kind.OCEAN;
    }

    /** Hostiles near the flag spot (NPCs hostile to the player, monsters). */
    public static int threats(ServerPlayer sp, BlockPos pos) {
        int n = 0;
        for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(24, 12, 24), e -> e.isAlive() && e != sp)) {
            if (e instanceof GLNpc npc) {
                if (npc.flag(GLNpc.FLAG_SURRENDER) || npc.flag(GLNpc.FLAG_ARRESTED) || npc.hasEffect(ModEffects.KNOCKED_OUT)) continue;
                if (npc.isHostileTo(sp)) n++;
            } else if (e instanceof Enemy) n++;
        }
        return n;
    }

    /**
     * The island's story places: set pieces, island secrets (buried treasure, caches, shrines, wrecks), the places of
     * its local story and where its people live, as {x, z, radius}. Claims never cover them.
     */
    public static List<int[]> storyPlaces(MinecraftServer server, Island isl) {
        List<int[]> out = new ArrayList<>();
        Settlement s = isl.settlement();
        for (Plot p : s.plots) out.add(new int[]{p.x() + p.sizeX() / 2, p.z() + p.sizeZ() / 2, Math.max(p.sizeX(), p.sizeZ()) / 2 + 6});
        for (Plot p : s.secrets) out.add(new int[]{p.x() + p.sizeX() / 2, p.z() + p.sizeZ() / 2, Math.max(p.sizeX(), p.sizeZ()) / 2 + 4});
        IslandStories.ensure(server);
        StoryModel.Generated g = IslandStories.current();
        if (g != null) {
            for (StoryModel.Site site : g.sites()) if (site.island().equals(isl.id)) out.add(new int[]{site.x(), site.z(), 12});
            for (StoryModel.Person p : g.people()) if (p.island().equals(isl.id)) out.add(new int[]{p.x(), p.z(), 8});
        }
        return out;
    }

    /** A story place whose ring (plus {@code margin}) reaches (x, z), or null. */
    @Nullable
    public static int[] storyPlaceNear(MinecraftServer server, WorldPlan plan, Island isl, int x, int z, int margin) {
        for (int[] p : storyPlaces(server, isl)) {
            double r = p[2] + margin;
            if (dist2(p, x, z) <= r * r) return p;
        }
        return null;
    }

    private static double dist2(int[] p, int x, int z) {
        double dx = p[0] - x, dz = p[1] - z;
        return dx * dx + dz * dz;
    }

    /** Inside one of the claim's story places (left open to everyone). */
    static boolean exempt(MinecraftServer server, Claim c, int x, int z) {
        List<int[]> ex = c.exempt;
        if (ex == null) {
            WorldPlan plan = WorldPlan.of(server);
            Island isl = plan == null ? null : plan.byId(c.island);
            ex = isl == null ? List.of() : storyPlaces(server, isl);
            c.exempt = ex;
        }
        for (int[] p : ex) if (dist2(p, x, z) <= (double) p[2] * p[2]) return true;
        return false;
    }

    // ================================================================== claiming, abandoning, transferring

    public static void tellRefusal(ServerPlayer sp, Refusal r) {
        tell(sp, Component.translatable(r.key(), r.args()).withStyle(ChatFormatting.RED));
    }

    /**
     * Claim the island at {@code flag} for the player's crew (the flag block is already there, or is set here).
     * Callers have checked {@link #check}. Returns the claim.
     */
    public static Claim claim(ServerPlayer sp, BlockPos flag, String crewName) {
        MinecraftServer server = sp.server;
        ServerLevel l = sp.serverLevel();
        WorldPlan plan = WorldPlan.forLevel(l);
        Island isl = plan.islandAt(flag.getX(), flag.getZ());
        CharacterData d = Characters.get(sp);
        Claim c = new Claim(isl.id);
        c.crew = crewKey(sp);
        c.founder = d.characterId;
        c.founderName = d.name.isEmpty() ? sp.getGameProfile().getName() : d.name;
        c.crewName = clean(crewName);
        c.flag = flag.immutable();
        c.claimedAt = l.getGameTime();
        c.raidReadyAt = c.claimedAt + GLConfig.BASE_NEW_CLAIM_GRACE_MINUTES.get() * 1200L;
        c.members.add(sp.getUUID());
        int[] dock = findDock(isl, flag);
        if (dock != null) {
            c.hasDock = true;
            c.dockX = dock[0];
            c.dockZ = dock[1];
        }
        if (!l.getBlockState(flag).is(BaseRegistry.CREW_FLAG.get())) l.setBlock(flag, BaseRegistry.CREW_FLAG.get().defaultBlockState(), 3);
        data(server).put(c);
        l.playSound(null, flag, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        tell(sp, Component.translatable("grandline.base.claimed", StoryContext.islandName(isl)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!c.hasDock) tell(sp, Component.translatable("grandline.base.no_dock").withStyle(ChatFormatting.GRAY));
        announce(server, Component.translatable("grandline.base.claimed_announce", c.crewName(), StoryContext.islandName(isl), Component.translatable("zone.grandline." + isl.zone.id()))
                .withStyle(ChatFormatting.GOLD));
        GrandLine.LOGGER.info("Base claimed: {} by crew {} ({}) at {}", isl.id, c.crew, c.founderName, flag);
        return c;
    }

    private static String clean(String s) {
        String n = s == null ? "" : s.replaceAll("[^\\p{L}\\p{N} .'\\-]", "").trim();
        return n.length() > 28 ? n.substring(0, 28) : n;
    }

    /** Water off the island nearest the flag where ships can moor: {x, z} or null. */
    @Nullable
    static int[] findDock(Island isl, BlockPos flag) {
        double best = Double.MAX_VALUE;
        int[] out = null;
        for (int i = 0; i < 24; i++) {
            double ang = i * Math.PI * 2 / 24, ux = Math.cos(ang), uz = Math.sin(ang);
            for (int d = 6; d < isl.radius * 2 + 80; d += 3) {
                int x = flag.getX() + (int) (ux * d), z = flag.getZ() + (int) (uz * d);
                double h = isl.plan().islandAt(x, z) == null ? WorldPlan.SEA_LEVEL - 20 : naturalOrSea(isl, x, z);
                if (h >= WorldPlan.SEA_LEVEL - 1) continue;
                boolean open = true;
                for (int k = 3; k <= 21 && open; k += 3) if (naturalOrSea(isl, x + (int) (ux * k), z + (int) (uz * k)) >= WorldPlan.SEA_LEVEL - 2) open = false;
                if (open && d < best) {
                    best = d;
                    out = new int[]{x + (int) (ux * 6), z + (int) (uz * 6)};
                }
                break;
            }
        }
        return out;
    }

    private static double naturalOrSea(Island isl, int x, int z) {
        double h = isl.naturalHeight(x, z);
        return Double.isNaN(h) ? WorldPlan.SEA_LEVEL - 20 : h;
    }

    /** Who may manage the claim's future (abandon, transfer): its founder, the crew's captain, or an operator. */
    public static boolean isLeader(ServerPlayer sp, Claim c) {
        if (sp.hasPermissions(2)) return true;
        CharacterData d = Characters.get(sp);
        return d.created && isCrew(sp, c) && (d.characterId.equals(c.founder) || d.characterId.equals(c.crew));
    }

    /** End a claim: the flag comes down, the warehouse spills at the flag (or goes with the raiders), hands leave. */
    public static void remove(MinecraftServer server, Claim c, String reason, @Nullable List<ItemStack> takenBy) {
        Data d = data(server);
        if (d.byIsland.remove(c.island) == null) return;
        d.setDirty();
        BaseRaids.onClaimRemoved(server, c);
        ServerLevel l = server.overworld();
        BaseHands.despawn(l, c);
        if (l.getBlockState(c.flag).is(BaseRegistry.CREW_FLAG.get())) l.setBlock(c.flag, Blocks.AIR.defaultBlockState(), 3);
        if (takenBy != null) {
            // the raiders carry off every other stack
            for (int i = 0; i < c.warehouse.getContainerSize(); i += 2) {
                ItemStack st = c.warehouse.removeItemNoUpdate(i);
                if (!st.isEmpty()) takenBy.add(st);
            }
        }
        if (!c.warehouse.isEmpty()) Containers.dropContents(l, c.flag, c.warehouse);
        WorldPlan plan = WorldPlan.of(server);
        Island isl = plan == null ? null : plan.byId(c.island);
        Component name = isl == null ? Component.literal(c.island) : StoryContext.islandName(isl);
        announce(server, Component.translatable("grandline.base.removed." + reason, c.crewName(), name).withStyle(reason.equals("captured") ? ChatFormatting.RED : ChatFormatting.GRAY));
        GrandLine.LOGGER.info("Base on {} removed ({})", c.island, reason);
    }

    /** {@code /base abandon}: the founder or captain strikes the flag. */
    public static boolean abandon(ServerPlayer sp, Claim c) {
        if (!isLeader(sp, c)) {
            tell(sp, Component.translatable("grandline.base.not_leader").withStyle(ChatFormatting.RED));
            return false;
        }
        remove(sp.server, c, "abandoned", null);
        return true;
    }

    /** Hand the base over to another player's crew (they must have a character and room for another claim). */
    public static boolean transfer(ServerPlayer sp, Claim c, ServerPlayer to) {
        if (!isLeader(sp, c)) {
            tell(sp, Component.translatable("grandline.base.not_leader").withStyle(ChatFormatting.RED));
            return false;
        }
        UUID crew = crewKey(to);
        if (crew == null || crew.equals(c.crew)) {
            tell(sp, Component.translatable("grandline.base.transfer_same").withStyle(ChatFormatting.RED));
            return false;
        }
        if (claimsOf(sp.server, crew).size() >= GLConfig.BASE_CLAIMS_PER_CREW.get()) {
            tell(sp, Component.translatable("grandline.base.refuse.limit", GLConfig.BASE_CLAIMS_PER_CREW.get()).withStyle(ChatFormatting.RED));
            return false;
        }
        Component before = c.crewName();
        CharacterData td = Characters.get(to);
        BaseHands.despawn(sp.serverLevel(), c);
        c.crew = crew;
        c.founder = td.characterId;
        c.founderName = td.name.isEmpty() ? to.getGameProfile().getName() : td.name;
        c.crewName = "";
        c.members.clear();
        c.members.add(to.getUUID());
        c.dirty();
        WorldPlan plan = WorldPlan.of(sp.server);
        Island isl = plan == null ? null : plan.byId(c.island);
        Component name = isl == null ? Component.literal(c.island) : StoryContext.islandName(isl);
        announce(sp.server, Component.translatable("grandline.base.transferred", before, name, c.crewName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    public static boolean rename(ServerPlayer sp, Claim c, String name) {
        if (!isCrew(sp, c) && !sp.hasPermissions(2)) return false;
        c.crewName = clean(name);
        c.dirty();
        tell(sp, Component.translatable("grandline.base.renamed", c.crewName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Moor the base's ships here: the player stands at (or in) open water near the base. */
    public static boolean setDock(ServerPlayer sp, Claim c) {
        int x = sp.getBlockX(), z = sp.getBlockZ();
        double dx = x - c.flag.getX(), dz = z - c.flag.getZ();
        boolean water = false;
        for (int ox = -2; ox <= 2 && !water; ox++)
            for (int oz = -2; oz <= 2 && !water; oz++)
                water = sp.level().getBlockState(new BlockPos(x + ox, WorldPlan.SEA_LEVEL, z + oz)).is(Blocks.WATER);
        double reach = radius() * 2.0;
        if (!isCrew(sp, c) || !water || dx * dx + dz * dz > reach * reach) {
            tell(sp, Component.translatable("grandline.base.dock_refused", (int) reach).withStyle(ChatFormatting.RED));
            return false;
        }
        c.hasDock = true;
        c.dockX = x;
        c.dockZ = z;
        c.dirty();
        tell(sp, Component.translatable("grandline.base.dock_set", x, z).withStyle(ChatFormatting.GOLD));
        return true;
    }

    // ================================================================== the warehouse

    /** Open the crew's shared warehouse from a base chest at {@code pos}. */
    public static boolean openWarehouse(ServerPlayer sp, BlockPos pos) {
        Container view = warehouse(sp, pos);
        if (view == null) return false;
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.sixRows(id, inv, view),
                Component.translatable("container.grandline.base_chest", ((View) view).c.crewName())));
        return true;
    }

    /** The player's window onto the warehouse of the base a chest at {@code pos} stands in, or null (told why). */
    @Nullable
    public static Container warehouse(ServerPlayer sp, BlockPos pos) {
        Claim c = claimAt(sp.level(), pos.getX(), pos.getZ());
        if (c == null) c = nearestWithin(sp.server, pos);
        if (c == null) {
            tell(sp, Component.translatable("grandline.base.chest_outside").withStyle(ChatFormatting.GRAY));
            return null;
        }
        if (!isCrew(sp, c)) {
            tell(sp, Component.translatable("grandline.base.chest_locked", c.crewName()).withStyle(ChatFormatting.RED));
            return null;
        }
        return new View(c, pos.immutable(), sp);
    }

    @Nullable
    private static Claim nearestWithin(MinecraftServer server, BlockPos pos) {
        for (Claim c : data(server).byIsland.values()) if (within(c, pos.getX() + 0.5, pos.getZ() + 0.5)) return c;
        return null;
    }

    /** One player's window onto the shared warehouse (valid while they stand by the chest and sail in the crew). */
    static final class View implements Container {
        final Claim c;
        final BlockPos at;
        final ServerPlayer who;

        View(Claim c, BlockPos at, ServerPlayer who) {
            this.c = c;
            this.at = at;
            this.who = who;
        }

        @Override public int getContainerSize() { return c.warehouse.getContainerSize(); }
        @Override public boolean isEmpty() { return c.warehouse.isEmpty(); }
        @Override public ItemStack getItem(int i) { return c.warehouse.getItem(i); }
        @Override public ItemStack removeItem(int i, int n) { return c.warehouse.removeItem(i, n); }
        @Override public ItemStack removeItemNoUpdate(int i) { return c.warehouse.removeItemNoUpdate(i); }
        @Override public void setItem(int i, ItemStack s) { c.warehouse.setItem(i, s); }
        @Override public void setChanged() { c.warehouse.setChanged(); }
        @Override public void clearContent() { c.warehouse.clearContent(); }

        @Override
        public boolean stillValid(Player p) {
            return c.owner != null && c.owner.byIsland.get(c.island) == c && isCrew(p, c) && p.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) < 64;
        }
    }

    // ================================================================== protection

    /** Whether a player may change blocks at {@code pos}: outside every claim, crew of the claim there, or exempt. */
    public static boolean mayModify(Player p, Level level, BlockPos pos) {
        if (p.isCreative() || p.hasPermissions(2)) return true;
        Claim c = claimAt(level, pos.getX(), pos.getZ());
        return c == null || isCrew(p, c);
    }

    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private static void deny(Player p, Claim c) {
        if (!(p instanceof ServerPlayer sp)) return;
        long now = sp.level().getGameTime();
        Long last = WARNED.get(sp.getUUID());
        if (last != null && now - last < 40) return;
        WARNED.put(sp.getUUID(), now);
        sp.displayClientMessage(Component.translatable("grandline.base.protected", c.crewName()).withStyle(ChatFormatting.RED), true);
        if (tap != null) tap.accept(sp, Component.translatable("grandline.base.protected", c.crewName()));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Player p = e.getPlayer();
        if (e.getState().is(BaseRegistry.CREW_FLAG.get())) {
            Claim f = claimAtFlag(sl.getServer(), e.getPos());
            if (f == null) return;
            if (p.isCreative() || p.hasPermissions(2)) remove(sl.getServer(), f, "struck", null); // an operator takes the flag down
            else {
                e.setCanceled(true);
                if (p instanceof ServerPlayer sp && isCrew(sp, f)) tell(sp, Component.translatable("grandline.base.flag_hint").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (mayModify(p, sl, e.getPos())) return;
        e.setCanceled(true);
        deny(p, claimAt(sl, e.getPos().getX(), e.getPos().getZ()));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || !(e.getEntity() instanceof Player p)) return;
        if (mayModify(p, sl, e.getPos())) return;
        e.setCanceled(true);
        deny(p, claimAt(sl, e.getPos().getX(), e.getPos().getZ()));
    }

    /** Outsiders cannot open containers, pour buckets or light fires inside a base. */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Player p = e.getEntity();
        BlockPos pos = e.getPos();
        if (data(sl.getServer()).byIsland.isEmpty() || mayModify(p, sl, pos)) return;
        BlockEntity be = sl.getBlockEntity(pos);
        var item = e.getItemStack().getItem();
        boolean risky = be instanceof Container || item instanceof BucketItem || item instanceof FlintAndSteelItem || item instanceof FireChargeItem;
        if (!risky) return;
        e.setCanceled(true);
        deny(p, claimAt(sl, pos.getX(), pos.getZ()));
    }

    /** Blasts by outsiders (and by nobody in particular) leave a base's blocks standing. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || data(sl.getServer()).byIsland.isEmpty()) return;
        Entity src = e.getExplosion().getIndirectSourceEntity();
        e.getAffectedBlocks().removeIf(pos -> {
            Claim c = claimAt(sl, pos.getX(), pos.getZ());
            return c != null && !(src instanceof Player p && isCrew(p, c));
        });
    }

    // ================================================================== hands, raiders and who fights whom

    @Nullable
    public static String baseTag(Entity e) {
        for (String t : e.getTags()) if (t.startsWith(OF)) return t.substring(OF.length());
        return null;
    }

    /**
     * Hostility for base hands and base raiders: hands never turn on their crew and fight the base's raiders; raiders
     * fight the crew and the hands of the base they landed on. Null: not a base matter.
     */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        Set<String> tags = npc.getTags();
        if (tags.isEmpty()) return null;
        boolean hand = tags.contains(HAND), raider = tags.contains(RAIDER);
        if (!hand && !raider) return null;
        String of = baseTag(npc);
        if (of == null || npc.getServer() == null) return null;
        if (e instanceof Player p) {
            Claim c = claimOn(npc.getServer(), of);
            boolean crew = c != null && isCrew(p, c);
            if (hand) return crew ? Boolean.FALSE : null;
            return crew ? Boolean.TRUE : null;
        }
        if (!(e instanceof GLNpc o) || !of.equals(baseTag(o))) return null;
        boolean oh = o.getTags().contains(HAND), or = o.getTags().contains(RAIDER);
        if ((hand && or) || (raider && oh)) return true;
        if ((hand && oh) || (raider && or)) return false;
        return null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof GLNpc npc) || npc.level().isClientSide || !npc.getTags().contains(HAND)) return;
        String of = baseTag(npc);
        Claim c = of == null ? null : claimOn(npc.getServer(), of);
        if (c != null) BaseHands.died(npc.getServer(), c, npc);
    }

    // ================================================================== upgrade effects

    /** The dojo: style and Haki training inside your own base counts half again. */
    public static int trainingBonus(ServerPlayer sp, String masteryKey, int amount) {
        if (amount <= 0 || !(masteryKey.startsWith("style/") || masteryKey.startsWith("haki/"))) return amount;
        Claim c = inOwnBase(sp);
        return c != null && c.has(BaseUpgrades.DOJO) ? (amount * 3 + 1) / 2 : amount;
    }

    /** The galley: dishes cooked inside your own base come out a quality step better. */
    public static boolean galleyAt(ServerPlayer sp) {
        Claim c = inOwnBase(sp);
        return c != null && c.has(BaseUpgrades.GALLEY);
    }

    // ================================================================== ticking (every few seconds, near crew only)

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        long now = server.overworld().getGameTime();
        if (now % 10 == 3) BaseRaids.steer(server, now); // raid ships sailing in or out
        if (now % 20 == 3) BaseRaids.tick(server, now); // raids ashore, and now and then a new one
        if (now % 40 != 9) return;
        Data d = data(server);
        if (d.byIsland.isEmpty()) return;
        List<? extends ServerPlayer> online = online(server);
        boolean round = now % 120 == 9; // every 6 s: dock, hands, the flag itself; the medical bay every 2 s
        for (Claim c : new ArrayList<>(d.byIsland.values())) {
            List<ServerPlayer> near = crewNear(online, c);
            if (!near.isEmpty()) medicalBay(c, near);
            if (!round) continue;
            if (near.isEmpty()) {
                if (!c.spawnedHands.isEmpty() && !anyoneNear(online, c, 128)) BaseHands.despawn(server.overworld(), c);
                continue;
            }
            service(server.overworld(), c, near, now);
        }
    }

    /** Everyone online (plus the test players). */
    public static List<? extends ServerPlayer> online(MinecraftServer server) {
        if (testPlayers == null) return server.getPlayerList().getPlayers();
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        all.addAll(testPlayers.get());
        return all;
    }

    /** Crew members of the claim in the overworld within {@link #NEAR} of the flag. */
    public static List<ServerPlayer> crewNear(List<? extends ServerPlayer> online, Claim c) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : online)
            if (!p.isSpectator() && p.level().dimension() == Level.OVERWORLD && isCrew(p, c) && p.distanceToSqr(c.flag.getX(), p.getY(), c.flag.getZ()) < NEAR * NEAR) out.add(p);
        return out;
    }

    /** Crew members of the claim online anywhere. */
    public static List<ServerPlayer> crewOnline(List<? extends ServerPlayer> online, Claim c) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : online) if (isCrew(p, c)) out.add(p);
        return out;
    }

    private static boolean anyoneNear(List<? extends ServerPlayer> online, Claim c, double r) {
        for (ServerPlayer p : online) if (p.level().dimension() == Level.OVERWORLD && p.distanceToSqr(c.flag.getX(), p.getY(), c.flag.getZ()) < r * r) return true;
        return false;
    }

    /** One service round for a base with crew near: members, the flag, the dock, the galley's meal, the hands. */
    public static void service(ServerLevel l, Claim c, List<ServerPlayer> near, long now) {
        for (ServerPlayer p : near) if (c.members.add(p.getUUID())) c.dirty();
        if (l.isLoaded(c.flag) && !l.getBlockState(c.flag).is(BaseRegistry.CREW_FLAG.get()) && l.getBlockState(c.flag).canBeReplaced())
            l.setBlock(c.flag, BaseRegistry.CREW_FLAG.get().defaultBlockState(), 3); // the flag is set up again where it stood
        dockService(l, c, online(l.getServer()));
        if (c.has(BaseUpgrades.GALLEY) && now % 1200 < 120) galleyMeal(c, near);
        BaseHands.sync(l, c, now);
    }

    /** The medical bay: crew inside the base heal a heart every 2 s (hands too). */
    public static int medicalBay(Claim c, List<ServerPlayer> near) {
        if (!c.has(BaseUpgrades.MEDBAY)) return 0;
        int healed = 0;
        for (ServerPlayer p : near) {
            if (!within(c, p.getX(), p.getZ()) || p.getHealth() >= p.getMaxHealth() || Characters.get(p).isKnockedDown()) continue;
            p.heal(2f);
            healed++;
        }
        return healed;
    }

    /** A hot meal from the base galley for the crew inside the base (stamina buff, a bite to eat). */
    public static int galleyMeal(Claim c, List<ServerPlayer> near) {
        int fed = 0;
        for (ServerPlayer p : near) {
            if (!within(c, p.getX(), p.getZ())) continue;
            CombatState.of(p).buffs.merge(Meals.STAMINA, 45 * 20, Math::max);
            if (p.getFoodData().getFoodLevel() < 20) p.getFoodData().eat(2, 0.4f);
            fed++;
        }
        return fed;
    }

    /** Whether a ship sails for the claim's crew (its owner is a crew member, online or remembered). */
    public static boolean crewShip(Claim c, ShipEntity s, List<? extends ServerPlayer> online) {
        if (s.owner == null || s.flag(ShipEntity.F_NPC) || s.flag(ShipEntity.F_PRIZE)) return false;
        for (ServerPlayer p : online) if (p.getUUID().equals(s.owner)) return isCrew(p, c);
        return c.members.contains(s.owner);
    }

    /**
     * The dock: crew ships moored within 40 blocks of the dock spot are repaired for free (a twentieth of the hull and
     * some rigging every round). Returns how many were worked on.
     */
    public static int dockService(ServerLevel l, Claim c, List<? extends ServerPlayer> online) {
        if (!c.hasDock) return 0;
        int n = 0;
        AABB box = new AABB(c.dockX - 40, WorldPlan.SEA_LEVEL - 10, c.dockZ - 40, c.dockX + 40, WorldPlan.SEA_LEVEL + 20, c.dockZ + 40);
        for (ShipEntity s : l.getEntitiesOfClass(ShipEntity.class, box, s -> !s.isSinking() && crewShip(c, s, online))) {
            boolean worked = false;
            if (s.hull() < s.maxHull()) {
                s.repair(Math.max(4f, s.maxHull() * 0.05f));
                worked = true;
            }
            if (s.rigging() < 1f) {
                s.setRigging(s.rigging() + 0.1f);
                worked = true;
            }
            if (worked) n++;
        }
        return n;
    }

    /** Crew ships moored at the base dock (for the menu and the slip). */
    public static List<ShipEntity> mooredShips(ServerLevel l, Claim c, List<? extends ServerPlayer> online) {
        if (!c.hasDock) return List.of();
        AABB box = new AABB(c.dockX - 40, WorldPlan.SEA_LEVEL - 10, c.dockZ - 40, c.dockX + 40, WorldPlan.SEA_LEVEL + 20, c.dockZ + 40);
        return l.getEntitiesOfClass(ShipEntity.class, box, s -> !s.isSinking() && crewShip(c, s, online));
    }

    // ================================================================== the sea chart

    /** Every claimed island for the sea chart: its id, the crew's name, where the flag stands, and whether it is yours. */
    public static ListTag chartMarks(ServerPlayer viewer) {
        ListTag l = new ListTag();
        WorldPlan plan = WorldPlan.of(viewer.server);
        for (Claim c : data(viewer.server).byIsland.values()) {
            Island isl = plan == null ? null : plan.byId(c.island);
            CompoundTag t = new CompoundTag();
            t.putString("island", c.island);
            t.putString("islandName", isl == null || isl.name == null ? "" : isl.name);
            t.putString("crew", c.crewName);
            t.putString("founder", c.founderName);
            t.putInt("x", c.flag.getX());
            t.putInt("z", c.flag.getZ());
            t.putBoolean("mine", isCrew(viewer, c));
            l.add(t);
        }
        return l;
    }

    // ================================================================== messages

    public static void tell(ServerPlayer sp, Component msg) {
        sp.sendSystemMessage(msg);
        if (tap != null) tap.accept(sp, msg);
    }

    public static void announce(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
        if (announced != null) announced.accept(msg);
    }

    /** Tell every crew member online (the watchtower's warnings, raids, losses). */
    public static void tellCrew(MinecraftServer server, Claim c, Component msg) {
        for (ServerPlayer p : crewOnline(online(server), c)) tell(p, msg);
    }

    public static MutableComponent islandName(MinecraftServer server, Claim c) {
        WorldPlan plan = WorldPlan.of(server);
        Island isl = plan == null ? null : plan.byId(c.island);
        return isl == null ? Component.literal(c.island) : StoryContext.islandName(isl).copy();
    }

    public static void markDirty(ServerPlayer sp) {
        CharacterManager.markDirty(sp);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        cached = null;
        cachedFor = null;
        WARNED.clear();
        BaseRaids.clear();
    }

    private CrewBases() {}
}
