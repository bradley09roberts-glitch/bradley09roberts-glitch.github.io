package com.terracraft.npc;

import com.terracraft.TerraCraft;
import com.terracraft.economy.Coins;
import com.terracraft.item.TerraItemStats;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.OpenNpcChatPacket;
import com.terracraft.progression.WorldProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Town NPC life cycle, Terraria style:
 * <ul>
 *     <li>the Guide appears when the world is first played,</li>
 *     <li>during the day, absent NPCs whose arrival condition is met move into a free valid house
 *     ("X has arrived!"),</li>
 *     <li>dead NPCs ("X was slain...") come back after a while under the same rules,</li>
 *     <li>houses are discovered from placed doors, lights and furniture (and the Housing Query) and are
 *     re-validated before NPCs move in,</li>
 *     <li>chat windows, shops, the Nurse's healing and the Guide's help are served here.</li>
 * </ul>
 * Runs every 5 seconds on the overworld; house checks are capped per run.
 */
public final class NpcManager {
    private static final int CHECK_INTERVAL = 100;
    private static final long RESPAWN_DELAY = 6000;
    private static final int MAX_HOUSE_CHECKS = 12;
    private static final int HELP_TIPS = 12;

    private NpcManager() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), NpcManager.class);
        NpcCommands.register();
    }

    @SubscribeEvent
    static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.server();
        if (server.getTickCount() % CHECK_INTERVAL != 0 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        NpcWorldData data = NpcWorldData.get(server);
        for (TownNpcType type : TownNpcs.all().values()) {
            Optional<NpcWorldData.Record> record = data.record(type.id());
            if (record.isPresent() && record.get().alive()) {
                maintainHouse(level, data, type, record.get());
                continue;
            }
            boolean firstGuide = type == TownNpcs.GUIDE && record.isEmpty();
            boolean respawnReady = record.isEmpty() || level.getGameTime() - record.get().diedAt() >= RESPAWN_DELAY;
            if (!firstGuide && (!level.isBrightOutside() || !respawnReady)) {
                continue;
            }
            if (!type.arrival().test(server)) {
                continue;
            }
            BlockPos house = findFreeHouse(level, data);
            if (house == null && type.needsHouse()) {
                continue;
            }
            spawn(level, data, type, house, record.map(NpcWorldData.Record::name).orElse(""), !firstGuide);
        }
    }

    /** Collects possible houses: doors, light sources and furniture placed by players. */
    @SubscribeEvent
    static boolean onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        BlockState state = event.getPlacedBlock();
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD
            && (state.is(HousingChecker.DOORS) || state.is(HousingChecker.COMFORT) || state.is(HousingChecker.TABLES)
            || state.getLightEmission() > 0)) {
            addCandidate(level, event.getPos());
        }
        return false;
    }

    public static void addCandidate(ServerLevel level, BlockPos pos) {
        NpcWorldData.get(level.getServer()).addCandidate(pos);
    }

    public static boolean isPresent(MinecraftServer server, String npc) {
        return NpcWorldData.get(server).record(npc).map(NpcWorldData.Record::alive).orElse(false);
    }

    /** The living NPC whose house is {@code anchor}, if loaded. */
    public static @Nullable TownNpc occupant(ServerLevel level, @Nullable BlockPos anchor) {
        if (anchor == null) {
            return null;
        }
        for (NpcWorldData.Record record : NpcWorldData.get(level.getServer()).records().values()) {
            if (record.alive() && record.house().filter(anchor::equals).isPresent()
                && level.getEntity(record.entity().get()) instanceof TownNpc npc) {
                return npc;
            }
        }
        return null;
    }

    private static Set<BlockPos> occupiedHouses(NpcWorldData data) {
        Set<BlockPos> occupied = new HashSet<>();
        for (NpcWorldData.Record record : data.records().values()) {
            if (record.alive()) {
                record.house().ifPresent(occupied::add);
            }
        }
        return occupied;
    }

    /** Validates candidate houses (a few per call) and returns the anchor of a free, valid one. */
    public static @Nullable BlockPos findFreeHouse(ServerLevel level, NpcWorldData data) {
        Set<BlockPos> occupied = occupiedHouses(data);
        int checks = 0;
        for (BlockPos candidate : new ArrayList<>(data.candidates())) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (++checks > MAX_HOUSE_CHECKS) {
                break;
            }
            HousingChecker.Result best = null;
            for (BlockPos start : new BlockPos[]{candidate, candidate.north(), candidate.south(), candidate.east(), candidate.west(), candidate.below()}) {
                HousingChecker.Result result = HousingChecker.check(level, start);
                if (result.valid()) {
                    best = result;
                    if (!occupied.contains(result.anchor())) {
                        return result.anchor();
                    }
                }
            }
            if (best == null) {
                data.removeCandidate(candidate);
            }
        }
        return null;
    }

    private static void maintainHouse(ServerLevel level, NpcWorldData data, TownNpcType type, NpcWorldData.Record record) {
        if (!(level.getEntity(record.entity().get()) instanceof TownNpc npc)) {
            return;
        }
        if (level.getGameTime() % 1200 >= CHECK_INTERVAL) {
            return;
        }
        if (record.house().isPresent()) {
            BlockPos house = record.house().get();
            if (level.isLoaded(house) && !HousingChecker.check(level, house).valid()) {
                npc.setHouse(null);
                data.put(type.id(), new NpcWorldData.Record(record.entity(), record.name(), Optional.empty(), -1));
            }
        } else {
            BlockPos house = findFreeHouse(level, data);
            if (house != null) {
                npc.setHouse(house);
                data.put(type.id(), new NpcWorldData.Record(record.entity(), record.name(), Optional.of(house), -1));
            }
        }
    }

    /** Spawns (or respawns) a town NPC in its house, or near a player when it has none. */
    public static @Nullable TownNpc spawn(ServerLevel level, NpcWorldData data, TownNpcType type, @Nullable BlockPos house, String name,
                                          boolean announce) {
        TownNpc npc = type.entity().get().create(level, EntitySpawnReason.EVENT);
        if (npc == null) {
            return null;
        }
        BlockPos at = house;
        if (at == null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
            int x = Mth.floor(player.getX()) + level.getRandom().nextInt(9) - 4;
            int z = Mth.floor(player.getZ()) + level.getRandom().nextInt(9) - 4;
            int y = player.level() == level ? Math.max(player.getBlockY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)) : 100;
            at = new BlockPos(x, y, z);
        }
        npc.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
        if (name.isEmpty()) {
            name = type.names().get(level.getRandom().nextInt(type.names().size()));
        }
        npc.setNpcName(name);
        npc.setHouse(house);
        level.addFreshEntity(npc);
        data.put(type.id(), new NpcWorldData.Record(Optional.of(npc.getUUID()), name, Optional.ofNullable(house), -1));
        TerraCraft.LOGGER.info("Town NPC {} ({}) arrived at {}", name, type.id(), at);
        if (announce) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.terracraft.npc.arrived", npc.getDisplayName()).withStyle(ChatFormatting.DARK_AQUA), false);
        }
        return npc;
    }

    static void onDeath(ServerLevel level, TownNpc npc, DamageSource source) {
        NpcWorldData data = NpcWorldData.get(level.getServer());
        TownNpcType type = npc.npcType();
        data.record(type.id()).ifPresent(record -> {
            if (record.entity().filter(npc.getUUID()::equals).isPresent()) {
                data.put(type.id(), new NpcWorldData.Record(Optional.empty(), record.name(), Optional.empty(), level.getGameTime()));
            }
        });
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.terracraft.npc.slain", npc.getDisplayName()).withStyle(ChatFormatting.RED), false);
    }

    // ------------------------------------------------------------------ chat, shops and services

    public static void openChat(ServerPlayer player, TownNpc npc, @Nullable String dialogueKey) {
        openChat(player, npc, dialogueKey, "");
    }

    public static void openChat(ServerPlayer player, TownNpc npc, @Nullable String dialogueKey, String arg) {
        TownNpcType type = npc.npcType();
        npc.startTalking(player);
        if (dialogueKey == null) {
            dialogueKey = type.roleKey() + ".dialogue." + (1 + player.getRandom().nextInt(type.dialogueLines()));
            arg = player.getName().getString();
        }
        List<String> services = new ArrayList<>();
        for (TownNpcType.Service service : type.services()) {
            services.add(service.name().toLowerCase(java.util.Locale.ROOT));
        }
        List<OpenNpcChatPacket.Offer> offers = new ArrayList<>();
        for (NpcShops.Entry entry : availableOffers(player.level(), type)) {
            offers.add(new OpenNpcChatPacket.Offer(BuiltInRegistries.ITEM.getKey(entry.item()).toString(), entry.count(), entry.price()));
        }
        TerraNetwork.sendToPlayer(player, new OpenNpcChatPacket(npc.getId(), type.id(), dialogueKey, arg, services, offers));
    }

    private static List<NpcShops.Entry> availableOffers(ServerLevel level, TownNpcType type) {
        WorldProgression progression = WorldProgression.get(level.getServer());
        boolean night = level.isDarkOutside();
        List<NpcShops.Entry> list = new ArrayList<>();
        for (NpcShops.Entry entry : NpcShops.shop(type.id())) {
            if (entry.available(progression, night)) {
                list.add(entry);
            }
        }
        return list;
    }

    public static void handleAction(ServerPlayer player, int entityId, String action, int index) {
        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof TownNpc npc) || !npc.isAlive() || npc.distanceToSqr(player) > 10 * 10) {
            return;
        }
        if ("close".equals(action)) {
            if (npc.isTalkingTo(player)) {
                npc.stopTalking();
            }
            return;
        }
        if (!npc.isTalkingTo(player)) {
            return;
        }
        TownNpcType type = npc.npcType();
        switch (action) {
            case "help" -> {
                if (type.services().contains(TownNpcType.Service.HELP)) {
                    openChat(player, npc, type.roleKey() + ".help." + (1 + player.getRandom().nextInt(HELP_TIPS)), "");
                }
            }
            case "heal" -> {
                if (type.services().contains(TownNpcType.Service.HEAL)) {
                    heal(player, npc, type);
                }
            }
            case "buy" -> buy(player, type, index);
            case "sell" -> sell(player);
            default -> {
            }
        }
    }

    private static void buy(ServerPlayer player, TownNpcType type, int index) {
        List<NpcShops.Entry> offers = availableOffers(player.level(), type);
        if (index < 0 || index >= offers.size()) {
            return;
        }
        NpcShops.Entry entry = offers.get(index);
        if (Coins.total(player) < entry.price()) {
            player.sendOverlayMessage(Component.translatable("message.terracraft.shop.too_poor").withStyle(ChatFormatting.RED));
            return;
        }
        Coins.remove(player, entry.price());
        ItemStack bought = new ItemStack(entry.item(), entry.count());
        if (!player.getInventory().add(bought)) {
            player.drop(bought, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /** Terraria: anything sells for a fifth of its value. */
    private static void sell(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        long each = held.isEmpty() || held.getItem() instanceof com.terracraft.item.coin.CoinItem ? 0 : TerraItemStats.of(held).value() / 5;
        if (each <= 0) {
            player.sendOverlayMessage(Component.translatable("message.terracraft.shop.cannot_sell").withStyle(ChatFormatting.GRAY));
            return;
        }
        long total = each * held.getCount();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (ItemStack coins : Coins.toStacks(total)) {
            if (!player.getInventory().add(coins)) {
                player.drop(coins, false);
            }
        }
        player.sendOverlayMessage(Component.translatable("message.terracraft.shop.sold", Coins.format(total)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** Nurse: 1 copper per missing life point plus 1 silver per debuff (Terraria's early-game pricing). */
    private static void heal(ServerPlayer player, TownNpc npc, TownNpcType type) {
        float missing = player.getMaxHealth() - player.getHealth();
        List<MobEffectInstance> debuffs = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                debuffs.add(effect);
            }
        }
        if (missing < 1 && debuffs.isEmpty()) {
            openChat(player, npc, type.roleKey() + ".healthy", "");
            return;
        }
        long cost = (long) Math.ceil(missing) + debuffs.size() * Coins.SILVER;
        if (Coins.total(player) < cost) {
            openChat(player, npc, type.roleKey() + ".too_poor", Coins.format(cost).getString());
            return;
        }
        Coins.remove(player, cost);
        player.setHealth(player.getMaxHealth());
        for (MobEffectInstance effect : debuffs) {
            player.removeEffect(effect.getEffect());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        openChat(player, npc, type.roleKey() + ".healed", Coins.format(cost).getString());
    }

    /** Removes a town NPC record (used by commands). */
    public static void forget(MinecraftServer server, String npc) {
        NpcWorldData.get(server).remove(npc);
    }

    public static Optional<UUID> livingEntity(MinecraftServer server, String npc) {
        return NpcWorldData.get(server).record(npc).flatMap(NpcWorldData.Record::entity);
    }
}
