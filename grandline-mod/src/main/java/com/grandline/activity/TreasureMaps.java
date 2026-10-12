package com.grandline.activity;

import com.grandline.GrandLine;
import com.grandline.net.GLNetwork;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.Quests;
import com.grandline.world.Island;
import com.grandline.world.IslandShape;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.BlockSink;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Treasure maps. A map points at one real island secret of the ocean world ({@code Settlement.secrets}: buried
 * treasure under an X, a hidden cache under a mossy boulder, a lost shrine, a shipwreck on the coastal shelf). The
 * target is chosen deterministically from the world plan, the place the map turns up and a salt: among the secrets of
 * the nearest islands of the same sea, those nobody has looted come first, then those this character has not looted.
 * A map found in a chest, bought from a shady dealer or given as a quest reward is blank until it reaches a player's
 * inventory, where it is drawn for that spot. Opening (or breaking) the target chest solves every map of it the
 * player carries; looted secrets are remembered per world ({@code grandline_treasure}) and per character (flags).
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class TreasureMaps {
    /** Character flag prefixes: maps read (shown on the sea chart) and secrets looted. */
    public static final String READ_FLAG = "tmap:", LOOTED_FLAG = "treasure:";
    private static final int NEAR_ISLANDS = 6;

    // ================================================================== world data

    public static final class Data extends SavedData {
        public final Set<String> looted = new LinkedHashSet<>();

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag l = new ListTag();
            for (String k : looted) l.add(StringTag.valueOf(k));
            tag.put("looted", l);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            for (Tag t : tag.getList("looted", Tag.TAG_STRING)) d.looted.add(t.getAsString());
            return d;
        }
    }

    public static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), GrandLine.MODID + "_treasure");
    }

    // ================================================================== secrets

    private static final BlockSink NOWHERE = (x, y, z, s) -> {};

    /** Position of the chest a secret hides (the local spots of Buildings.buriedTreasure / hiddenCache / lostShrine / shipwreck). */
    @Nullable
    public static BlockPos chestPos(Plot p) {
        Builder b = new Builder(NOWHERE, p.x(), p.y(), p.z(), p.type().w, p.type().d, p.rotation(), p.seed());
        return switch (p.type()) {
            case BURIED_TREASURE -> b.world(2, -3, 2);
            case HIDDEN_CACHE -> b.world(1, -7, 5);
            case LOST_SHRINE -> b.world(3, 2, 4);
            case SHIPWRECK -> b.world(p.type().w / 2, 1, p.type().d - 4);
            default -> null;
        };
    }

    public static String kind(BuildingType t) {
        return t.name().toLowerCase(Locale.ROOT);
    }

    static String nameOf(Island isl) {
        return isl.name == null ? "" : isl.name;
    }

    @Nullable
    public static TreasureTarget target(Island isl, int index) {
        List<Plot> secrets = isl.settlement().secrets;
        if (index < 0 || index >= secrets.size()) return null;
        Plot p = secrets.get(index);
        BlockPos c = chestPos(p);
        if (c == null) return null;
        return new TreasureTarget(isl.id, index, nameOf(isl), kind(p.type()), c.getX(), c.getY(), c.getZ(), false, false);
    }

    private static boolean sameSea(SeaZone a, SeaZone b) {
        if (a == SeaZone.RED_LINE || a == SeaZone.WORLD_EDGE) return true;
        return a == b;
    }

    private static boolean mayHaveSecrets(Island i) {
        return i.radius >= 60 && i.shape != IslandShape.SEA;
    }

    /**
     * Chooses a secret for a map that turns up at {@code origin}. Deterministic for the same world, place, salt and
     * looted state. {@code grand} maps (pieced from torn halves) skip the two nearest islands and look further out.
     */
    @Nullable
    public static TreasureTarget pick(MinecraftServer server, BlockPos origin, long salt, @Nullable Player player, boolean grand) {
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return null;
        int ox = origin.getX(), oz = origin.getZ();
        SeaZone zone = WorldLayout.zoneAt(ox, oz);
        List<Island> sorted = new ArrayList<>(plan.islands);
        sorted.sort(Comparator.comparingDouble(i -> i.distance(ox, oz)));
        List<Island> near = new ArrayList<>();
        int want = grand ? NEAR_ISLANDS + 2 : NEAR_ISLANDS;
        for (int pass = 0; pass < 2 && near.size() < want; pass++)
            for (Island i : sorted) {
                if (near.size() >= want) break;
                if (!mayHaveSecrets(i) || near.contains(i)) continue;
                if (pass == 0 && !sameSea(zone, i.zone)) continue;
                if (i.settlement().secrets.isEmpty()) continue;
                near.add(i);
            }
        if (grand && near.size() > 3) near = near.subList(2, near.size());
        if (near.isEmpty()) return null;
        Data data = data(server);
        CharacterData cd = player == null ? null : Characters.get(player);
        int bestTier = Integer.MAX_VALUE;
        List<TreasureTarget> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (int rank = 0; rank < near.size(); rank++) {
            Island isl = near.get(rank);
            for (int idx = 0; idx < isl.settlement().secrets.size(); idx++) {
                TreasureTarget t = target(isl, idx);
                if (t == null) continue;
                boolean mine = cd != null && cd.flags.contains(LOOTED_FLAG + t.key());
                int tier = mine ? 2 : data.looted.contains(t.key()) ? 1 : 0;
                if (tier > bestTier) continue;
                if (tier < bestTier) {
                    bestTier = tier;
                    pool.clear();
                    weights.clear();
                }
                pool.add(t);
                weights.add(1.0 / (1 + rank));
            }
        }
        if (pool.isEmpty()) return null;
        RandomSource r = new LegacyRandomSource(plan.seed ^ salt * 0x9E3779B97F4A7C15L ^ origin.asLong());
        double total = 0;
        for (double w : weights) total += w;
        double roll = r.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return grand ? pool.get(i).asGrand() : pool.get(i);
        }
        TreasureTarget last = pool.get(pool.size() - 1);
        return grand ? last.asGrand() : last;
    }

    /** Draws a blank map for the player holding it (salt: the inventory slot, so maps bought together differ); null off the ocean world. */
    @Nullable
    public static TreasureTarget assign(ItemStack stack, ServerPlayer sp, int slot) {
        TreasureTarget t = stack.get(ActivityRegistry.TREASURE.get());
        if (t != null) return t;
        if (sp.level().dimension() != Level.OVERWORLD) return null; // drawn once back on the ocean world
        long salt = sp.getUUID().getLeastSignificantBits() * 31 + slot * 977L + Characters.get(sp).flags.stream().filter(f -> f.startsWith(READ_FLAG)).count();
        t = pick(sp.server, sp.blockPosition(), salt, sp, false);
        if (t != null) stack.set(ActivityRegistry.TREASURE.get(), t);
        return t;
    }

    /** Secret whose chest sits exactly at {@code pos} in the ocean world, as {island, index}; null if none. */
    @Nullable
    public static Object[] secretAt(MinecraftServer server, Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return null;
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return null;
        for (Island isl : plan.around(pos.getX(), pos.getZ())) {
            List<Plot> secrets = isl.settlement().secrets;
            for (int i = 0; i < secrets.size(); i++) {
                Plot p = secrets.get(i);
                if (!p.contains(pos.getX(), pos.getZ(), 2)) continue;
                if (pos.equals(chestPos(p))) return new Object[]{isl, i};
            }
        }
        return null;
    }

    // ================================================================== solving

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!(e.getLevel().getBlockEntity(e.getPos()) instanceof BaseContainerBlockEntity)) return;
        opened(sp, e.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp) || !(e.getLevel() instanceof Level l)) return;
        if (!(l.getBlockEntity(e.getPos()) instanceof BaseContainerBlockEntity)) return;
        opened(sp, e.getPos());
    }

    /** A player opened or broke a container: if it is a secret's chest, mark it looted and solve their maps of it. */
    public static int opened(ServerPlayer sp, BlockPos pos) {
        Object[] s = secretAt(sp.server, sp.level(), pos);
        if (s == null) return 0;
        Island isl = (Island) s[0];
        int idx = (Integer) s[1];
        String key = isl.id + "#" + idx;
        Data data = data(sp.server);
        if (data.looted.add(key)) data.setDirty();
        CharacterData d = Characters.get(sp);
        d.flags.add(LOOTED_FLAG + key);
        int solved = 0;
        long beli = 0;
        int xp = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            TreasureTarget t = st.get(ActivityRegistry.TREASURE.get());
            if (t == null || t.solved() || !t.key().equals(key)) continue;
            st.set(ActivityRegistry.TREASURE.get(), t.solve());
            solved++;
            xp += (120 + isl.tier * 60) * (t.grand() ? 2 : 1);
            if (t.grand()) beli += 2500 + isl.tier * 500L;
        }
        if (solved > 0) {
            int lv = d.addXp(xp);
            d.beli += beli;
            Component name = isl.canon && isl.name == null ? Component.translatable(isl.translationKey()) : Component.literal(isl.name);
            sp.sendSystemMessage(Component.translatable(beli > 0 ? "grandline.treasure.found_grand" : "grandline.treasure.found", name, xp, beli)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            sp.level().playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.0f);
            if (lv > 0) CharacterManager.applyAttributes(sp);
            Quests.progress(sp, "treasure", "any", solved);
        }
        CharacterManager.markDirty(sp);
        return solved;
    }

    // ================================================================== the map and the chart

    /** Opens the painted map of a target for the player (and remembers it for the sea chart). */
    public static void show(ServerPlayer sp, TreasureTarget t) {
        CharacterData d = Characters.get(sp);
        if (d.flags.add(READ_FLAG + t.key())) CharacterManager.markDirty(sp);
        CompoundTag data = screenData(sp, t);
        if (data != null) GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("treasure_map", data));
    }

    /** Land mask of the island around the target (0 deep water .. 4 highland) plus the target and the reader. */
    @Nullable
    public static CompoundTag screenData(ServerPlayer sp, TreasureTarget t) {
        WorldPlan plan = WorldPlan.of(sp.server);
        Island isl = plan == null ? null : plan.byId(t.island());
        if (isl == null) return null;
        int half = (int) Math.max(isl.radius * 1.3, Math.max(Math.abs(t.x() - isl.x), Math.abs(t.z() - isl.z)) + 48);
        int n = 64;
        double cell = 2.0 * half / n;
        byte[] mask = new byte[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int x = (int) (isl.x - half + (i + 0.5) * cell), z = (int) (isl.z - half + (j + 0.5) * cell);
                double h = isl.naturalHeight(x, z);
                int sea = WorldPlan.SEA_LEVEL;
                mask[j * n + i] = (byte) (Double.isNaN(h) || h < sea - 6 ? 0 : h < sea ? 1 : h < sea + 4 ? 2 : h < sea + 24 ? 3 : 4);
            }
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("mask", mask);
        tag.putInt("n", n);
        tag.putInt("x0", isl.x - half);
        tag.putInt("z0", isl.z - half);
        tag.putInt("span", 2 * half);
        tag.putInt("tx", t.x());
        tag.putInt("tz", t.z());
        tag.putString("kind", t.kind());
        tag.putBoolean("solved", t.solved());
        tag.putBoolean("grand", t.grand());
        tag.putString("island", t.island());
        tag.putString("name", t.name());
        tag.putString("zone", isl.zone.id());
        tag.putInt("px", sp.getBlockX());
        tag.putInt("pz", sp.getBlockZ());
        tag.putBoolean("here", sp.level().dimension() == Level.OVERWORLD);
        return tag;
    }

    /** Map targets the player knows (maps carried or read), for the sea chart. */
    public static ListTag chartMarks(ServerPlayer sp) {
        Map<String, TreasureTarget> known = new LinkedHashMap<>();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            TreasureTarget t = inv.getItem(i).get(ActivityRegistry.TREASURE.get());
            if (t != null) known.merge(t.key(), t, (a, b) -> a.solved() ? a : b);
        }
        CharacterData d = Characters.get(sp);
        WorldPlan plan = WorldPlan.of(sp.server);
        if (plan != null) for (String f : d.flags) {
            if (!f.startsWith(READ_FLAG)) continue;
            String key = f.substring(READ_FLAG.length());
            if (known.containsKey(key)) continue;
            int hash = key.lastIndexOf('#');
            if (hash < 0) continue;
            Island isl = plan.byId(key.substring(0, hash));
            TreasureTarget t = isl == null ? null : target(isl, Integer.parseInt(key.substring(hash + 1)));
            if (t != null) known.put(key, t);
        }
        ListTag l = new ListTag();
        for (TreasureTarget t : known.values()) {
            CompoundTag e = new CompoundTag();
            e.putInt("x", t.x());
            e.putInt("z", t.z());
            e.putString("island", t.island());
            e.putString("name", t.name());
            e.putBoolean("solved", t.solved() || d.flags.contains(LOOTED_FLAG + t.key()));
            l.add(e);
        }
        return l;
    }

    private TreasureMaps() {}
}
