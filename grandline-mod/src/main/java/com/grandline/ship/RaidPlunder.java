package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.npc.GLNpc;
import com.grandline.world.Island;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Harbour raids carry off goods, not just Beli. Once raiders have been ashore {@link #takeAfter} ticks, each one still
 * standing (one a second) carries off a stack of the town market's goods: the island's shops stop selling those goods
 * until they are restocked ({@link #restockTicks}, a day by default; kept in a SavedData so a restart does not restock
 * them early; at most {@link #MAX_GOODS} goods per island). A raider who is beaten (killed, or still lying there when
 * the raid is repelled) drops what he carried for the defenders to pick up; raiders who get away keep it. Nothing
 * here opens or empties a container: no player's chest and no crew base is ever touched.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class RaidPlunder {
    /** A raider's loot (item id and count) kept in his persistent data. */
    static final String LOOT = "GLRaidLoot";
    public static final int MAX_GOODS = 6;
    public static long restockTicks = 24000;
    public static long takeAfter = 300;
    /** What a town market holds and a raider can carry off (item id, stack), all goods the island's shops sell. */
    static final String[][] GOODS = {
            {"minecraft:bread", "8"}, {"grandline:meat_on_bone", "4"}, {"minecraft:cooked_cod", "8"}, {"grandline:log_pose", "1"},
            {"grandline:sea_chart", "1"}, {"grandline:bullet", "16"}, {"grandline:cannonball", "8"}, {"minecraft:white_wool", "8"},
            {"minecraft:oak_planks", "32"}, {"grandline:cutlass", "1"}, {"minecraft:torch", "16"}, {"minecraft:iron_chestplate", "1"}};
    private static final Set<UUID> ANNOUNCED = new HashSet<>();

    /** One plundered good of an island's market: gone from its shops until {@code until}. */
    public record Taken(String item, int count, long until) {}

    // ================================================================== during a raid (from HarbourRaids)

    /** Every second ashore: one more raider who has reached the town carries off a stack of the market's goods. */
    static void tick(ShipEntity ship, ServerLevel sl, Island isl, List<GLNpc> standing, long ashore) {
        if (ashore < takeAfter) return;
        Data data = data(sl.getServer());
        long now = sl.getGameTime();
        List<Taken> gone = data.goods(isl.id, now);
        if (gone.size() >= MAX_GOODS) return;
        for (GLNpc m : standing) {
            if (m.getPersistentData().contains(LOOT)) continue;
            List<String[]> left = new ArrayList<>();
            for (String[] g : GOODS) if (gone.stream().noneMatch(t -> t.item().equals(g[0]))) left.add(g);
            if (left.isEmpty()) return;
            String[] g = left.get(sl.random.nextInt(left.size()));
            CompoundTag loot = new CompoundTag();
            loot.putString("item", g[0]);
            loot.putInt("count", Integer.parseInt(g[1]));
            loot.putString("island", isl.id);
            m.getPersistentData().put(LOOT, loot);
            gone.add(new Taken(g[0], Integer.parseInt(g[1]), now + restockTicks));
            data.setDirty();
            if (ANNOUNCED.add(ship.getUUID()))
                LivingSea.announce(sl, m.position(), Component.translatable("grandline.raid_plunder.taking", MerchantShips.islandName(isl)).withStyle(ChatFormatting.RED));
            return; // one a second
        }
    }

    /** The raid is repelled: every raider still carrying goods (knocked out, surrendered, cut down) drops them. */
    static void recover(ShipEntity ship, ServerLevel sl) {
        ANNOUNCED.remove(ship.getUUID());
        for (UUID u : ship.naval.crew) if (sl.getEntity(u) instanceof GLNpc m) drop(m, sl);
    }

    /** What a raider carries (an empty stack when nothing). */
    public static ItemStack carried(GLNpc m) {
        CompoundTag t = m.getPersistentData().getCompound(LOOT);
        if (t.isEmpty()) return ItemStack.EMPTY;
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.getString("item")));
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, Math.max(1, t.getInt("count")));
    }

    /** Drop a raider's loot where he lies (returns the stack dropped, or empty). */
    public static ItemStack drop(GLNpc m, ServerLevel sl) {
        ItemStack st = carried(m);
        m.getPersistentData().remove(LOOT);
        if (st.isEmpty()) return st;
        ItemEntity ie = new ItemEntity(sl, m.getX(), m.getY() + 0.5, m.getZ(), st.copy());
        ie.setDefaultPickUpDelay();
        sl.addFreshEntity(ie);
        return st;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof GLNpc m && m.level() instanceof ServerLevel sl && m.getPersistentData().contains(LOOT)) drop(m, sl);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        ANNOUNCED.clear();
        cached = null;
        cachedFor = null;
    }

    // ================================================================== the market (from Shops.offers)

    /** Does this NPC's island market still have this good (not carried off by raiders, or restocked since)? */
    public static boolean available(GLNpc npc, String item) {
        if (!(npc.level() instanceof ServerLevel sl) || npc.islandId().isEmpty()) return true;
        Data d = data(sl.getServer());
        if (d.islands.isEmpty()) return true;
        for (Taken t : d.goods(npc.islandId(), sl.getGameTime())) if (t.item().equals(item)) return false;
        return true;
    }

    /** The goods missing from an island's market now (self-test, status). */
    public static List<Taken> missing(MinecraftServer server, String island) {
        return List.copyOf(data(server).goods(island, server.overworld().getGameTime()));
    }

    /** Self-test: land a raid ship's party now (as when she reaches the harbour). */
    public static boolean landNow(ShipEntity ship, ServerLevel sl, Island isl) {
        return HarbourRaids.land(ship, sl, isl, sl.getGameTime());
    }

    // ================================================================== saved state

    public static final class Data extends SavedData {
        final Map<String, List<Taken>> islands = new HashMap<>();

        /** The island's plundered goods still missing at {@code now} (restocked ones are dropped). */
        List<Taken> goods(String island, long now) {
            List<Taken> l = islands.get(island);
            if (l == null) {
                l = new ArrayList<>();
                islands.put(island, l);
                return l;
            }
            if (l.removeIf(t -> t.until() <= now)) setDirty();
            return l;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            CompoundTag is = new CompoundTag();
            islands.forEach((id, l) -> {
                if (l.isEmpty()) return;
                ListTag lt = new ListTag();
                for (Taken t : l) {
                    CompoundTag e = new CompoundTag();
                    e.putString("item", t.item());
                    e.putInt("count", t.count());
                    e.putLong("until", t.until());
                    lt.add(e);
                }
                is.put(id, lt);
            });
            tag.put("islands", is);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            CompoundTag is = tag.getCompound("islands");
            for (String id : is.getAllKeys()) {
                List<Taken> l = new ArrayList<>();
                for (Tag x : is.getList(id, Tag.TAG_COMPOUND)) {
                    CompoundTag e = (CompoundTag) x;
                    l.add(new Taken(e.getString("item"), e.getInt("count"), e.getLong("until")));
                }
                d.islands.put(id, l);
            }
            return d;
        }
    }

    @Nullable private static Data cached;
    @Nullable private static MinecraftServer cachedFor;

    static Data data(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cached = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), GrandLine.MODID + "_plunder");
        cachedFor = server;
        return cached;
    }

    /** Self-test and operators: restock an island's market now (as if the restock time had passed). Returns the goods restocked. */
    public static int restockNow(MinecraftServer server, String island) {
        Data d = data(server);
        List<Taken> l = d.islands.get(island);
        if (l == null || l.isEmpty()) return 0;
        int n = l.size();
        l.clear();
        d.setDirty();
        return n;
    }

    /** Self-test: let {@code ticks} pass for one plundered good of an island (its restock time comes that much sooner). */
    public static void age(MinecraftServer server, String island, String item, long ticks) {
        Data d = data(server);
        List<Taken> l = d.islands.get(island);
        if (l == null) return;
        l.replaceAll(t -> t.item().equals(item) ? new Taken(t.item(), t.count(), t.until() - ticks) : t);
        d.setDirty();
    }

    private RaidPlunder() {}
}
