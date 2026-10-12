package com.grandline.activity;

import com.grandline.GrandLine;
import com.grandline.combat.CombatState;
import com.grandline.npc.GLNpc;
import com.grandline.npc.crew.CrewBonuses;
import com.grandline.npc.crew.CrewManager;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModDamageTypes;
import com.grandline.registry.ModItems;
import com.grandline.ship.ShipEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cooking: dishes and the meal buffs they give through the combat buff system ({@link CombatState#buffs}).
 * <ul>
 *   <li>meal_stamina: faster stamina regeneration; meal_haki: the Haki gauge refills, even while Haki is in use</li>
 *   <li>meal_strength: +15% damage dealt; meal_swim: Dolphin's Grace and slower air loss in water</li>
 *   <li>meal_warm (cold resistance): ice and freezing hurt half as much, frost thaws; meal_cool (heat resistance):
 *       fire hurts half as much and flames die down quickly</li>
 *   <li>meal_prime: a prime (chef's) dish makes every meal effect 25% stronger while it lasts</li>
 * </ul>
 * Quality (0 plain, 1 fine, 2 prime) lengthens the buffs by half per step. A dish takes the quality of its best
 * ingredient (catch quality comes from the fishing skill), one step for a seasoned cook (cooking mastery 15) and one
 * for a cook in the crew. A crew cook also keeps a galley going at sea: every minute aboard your ship they cook one
 * raw catch or Sea King steak from your hold into a fine dish and serve everyone nearby a hot meal.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Meals {
    public static final String STAMINA = "meal_stamina", STRENGTH = "meal_strength", HAKI = "meal_haki", SWIM = "meal_swim",
            WARM = "meal_warm", COOL = "meal_cool", PRIME = "meal_prime";
    public static final String COOKING = "activity/cooking";
    /** Cooking mastery level at which dishes come out one quality step better. */
    public static final int SEASONED_COOK = 15;
    public static final int GALLEY_TICKS = 20 * 60;

    public record Effect(String buff, int seconds) {}

    private static final Map<String, List<Effect>> DISHES = new HashMap<>();

    private static void def(String id, Effect... e) {
        DISHES.put(id, List.of(e));
    }

    private static Effect e(String buff, int seconds) {
        return new Effect(buff, seconds);
    }

    static {
        def("grilled_fish", e(STAMINA, 60));
        def("sea_king_roast", e(STRENGTH, 90), e(STAMINA, 60));
        def("fisherman_stew", e(STAMINA, 150), e(WARM, 150));
        def("abyss_chowder", e(SWIM, 180), e(HAKI, 60));
        def("saltfire_curry", e(STRENGTH, 150), e(WARM, 240));
        def("tidewater_sashimi", e(COOL, 240), e(SWIM, 120), e(STAMINA, 60));
        def("sea_king_hotpot", e(HAKI, 180), e(STRENGTH, 120));
        def("pirates_feast", e(STRENGTH, 180), e(STAMINA, 180), e(HAKI, 90));
    }

    public static List<Effect> effects(ItemStack s) {
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(GrandLine.MODID) ? DISHES.getOrDefault(key.getPath(), List.of()) : List.of();
    }

    public static int quality(ItemStack s) {
        Integer q = s.get(ActivityRegistry.QUALITY.get());
        return q == null ? 0 : Mth.clamp(q, 0, 2);
    }

    public static int scaledSeconds(int base, int quality) {
        return (int) Math.round(base * (1 + 0.5 * quality));
    }

    // ================================================================== eating

    /** Grants a dish's buffs (called when it has been eaten). */
    public static void eat(LivingEntity e, ItemStack dish) {
        int q = quality(dish);
        CombatState cs = CombatState.of(e);
        int longest = 0;
        for (Effect ef : effects(dish)) {
            int ticks = scaledSeconds(ef.seconds(), q) * 20;
            cs.buffs.merge(ef.buff(), ticks, Math::max);
            longest = Math.max(longest, ticks);
        }
        if (q >= 2 && longest > 0) cs.buffs.merge(PRIME, longest, Math::max);
        if (e instanceof ServerPlayer sp && longest > 0)
            sp.displayClientMessage(Component.translatable("grandline.meal.eaten", dish.getHoverName()).withStyle(ChatFormatting.GOLD), true);
    }

    /** 1.25 while a prime dish is in effect, else 1. */
    public static float potency(LivingEntity e) {
        return CombatState.of(e).hasBuff(PRIME) ? 1.25f : 1f;
    }

    public static float strengthMultiplier(LivingEntity attacker) {
        CombatState cs = CombatState.of(attacker);
        return cs.hasBuff(STRENGTH) ? 1f + 0.15f * potency(attacker) : 1f;
    }

    public static float coldMultiplier(LivingEntity victim) {
        return CombatState.of(victim).hasBuff(WARM) ? Math.max(0.3f, 1f - 0.5f * potency(victim)) : 1f;
    }

    public static float heatMultiplier(LivingEntity victim) {
        return CombatState.of(victim).hasBuff(COOL) ? Math.max(0.3f, 1f - 0.5f * potency(victim)) : 1f;
    }

    public static boolean isCold(DamageSource s) {
        return s.is(DamageTypes.FREEZE) || s.is(ModDamageTypes.ICE);
    }

    public static boolean isHeat(DamageSource s) {
        return s.is(DamageTypeTags.IS_FIRE) || s.is(ModDamageTypes.FIRE);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource src = e.getSource();
        float amount = e.getAmount();
        if (src.getEntity() instanceof LivingEntity att && att != victim) amount *= strengthMultiplier(att);
        if (isCold(src)) amount *= coldMultiplier(victim);
        if (isHeat(src)) amount *= heatMultiplier(victim);
        if (amount != e.getAmount()) e.setAmount(amount);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) tick(sp);
    }

    /** Per-tick meal effects on a player (stamina, Haki, swimming, frost and flames). */
    public static void tick(ServerPlayer sp) {
        CombatState cs = CombatState.of(sp);
        if (cs.buffs.isEmpty()) return;
        CharacterData d = Characters.get(sp);
        float k = potency(sp);
        boolean dirty = false;
        if (d.created && cs.hasBuff(STAMINA) && d.stamina < d.maxStamina()) {
            d.stamina = Math.min(d.maxStamina(), d.stamina + 0.35f * k);
            dirty = true;
        }
        if (d.created && cs.hasBuff(HAKI) && d.hakiGauge < d.maxHakiGauge()) {
            d.hakiGauge = Math.min(d.maxHakiGauge(), d.hakiGauge + 0.3f * k);
            dirty = true;
        }
        if (cs.hasBuff(SWIM) && sp.isInWater()) {
            if (sp.tickCount % 20 == 0) sp.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, true, false, true));
            if (sp.tickCount % 2 == 0 && sp.getAirSupply() < sp.getMaxAirSupply()) sp.setAirSupply(sp.getAirSupply() + 1);
        }
        if (cs.hasBuff(WARM) && sp.getTicksFrozen() > 0) sp.setTicksFrozen(Math.max(0, sp.getTicksFrozen() - 4));
        if (cs.hasBuff(COOL) && sp.getRemainingFireTicks() > 20) sp.setRemainingFireTicks(20);
        if (dirty && sp.tickCount % 10 == 0) CharacterManager.markDirty(sp);
    }

    // ================================================================== cooking

    /** Whether the player's crew has a cook aboard (recruited; waiting members count too). */
    public static boolean hasCrewCook(ServerPlayer sp) {
        return CrewBonuses.activeRoles(sp).contains("cook");
    }

    /** Quality a dish comes out with: best ingredient, +1 seasoned cook, +1 crew cook (max prime). */
    public static int cookedQuality(int bestIngredient, int cookingMastery, boolean crewCook) {
        return Mth.clamp(bestIngredient + (cookingMastery >= SEASONED_COOK ? 1 : 0) + (crewCook ? 1 : 0), 0, 2);
    }

    /** A dish was taken out of a crafting grid, furnace or smoker. */
    public static void onCooked(ItemStack stack, Player player) {
        int best = 0;
        AbstractContainerMenu m = player.containerMenu;
        if (m instanceof CraftingMenu || m instanceof InventoryMenu) {
            int n = m instanceof CraftingMenu ? 9 : 4;
            for (int i = 1; i <= n && i < m.slots.size(); i++) best = Math.max(best, quality(m.getSlot(i).getItem()));
        }
        CharacterData d = Characters.get(player);
        boolean cook = player instanceof ServerPlayer sp && hasCrewCook(sp);
        int q = cookedQuality(best, d.mastery(COOKING), cook);
        if (q > 0) stack.set(ActivityRegistry.QUALITY.get(), q);
        if (d.created) {
            if (d.addMastery(COOKING, 6) && player instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.translatable("grandline.cooking.level", d.mastery(COOKING)).withStyle(ChatFormatting.GOLD));
            if (player instanceof ServerPlayer sp) CharacterManager.markDirty(sp);
            com.grandline.journey.Journey.onCooked(player, stack);
        }
    }

    // ================================================================== the galley (crew cook at sea)

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % GALLEY_TICKS != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (aboardOwnShip(sp) == null || !hasCrewCook(sp)) continue;
            galley(sp);
        }
    }

    @Nullable
    static ShipEntity aboardOwnShip(ServerPlayer sp) {
        ShipEntity ship = sp.getVehicle() instanceof ShipEntity s ? s : null;
        if (ship == null) for (ShipEntity s : sp.level().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(40), s -> s.carries(sp))) ship = s;
        return ship != null && ship.mayCommand(sp) ? ship : null;
    }

    /**
     * The crew cook's round: cooks one raw catch (or Sea King steak) from the captain's hold into a fine dish (prime
     * if the catch was), and serves a hot meal (stamina buff, a bite to eat) to everyone near the captain and their
     * crew. Returns the dish cooked, or EMPTY.
     */
    public static ItemStack galley(ServerPlayer sp) {
        ItemStack cooked = ItemStack.EMPTY;
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack st = inv.items.get(i);
            Item out = st.getItem() instanceof SeaFishItem ? ActivityItems.GRILLED_FISH.get() : st.is(ModItems.SEA_KING_STEAK.get()) ? ActivityItems.SEA_KING_ROAST.get() : null;
            if (out == null) continue;
            ItemStack dish = new ItemStack(out);
            dish.set(ActivityRegistry.QUALITY.get(), Math.min(2, 1 + (quality(st) > 0 ? 1 : 0)));
            Component raw = st.getHoverName();
            st.shrink(1);
            cooked = dish.copy();
            if (!inv.add(dish)) sp.drop(dish, false);
            sp.sendSystemMessage(Component.translatable("grandline.galley.cooked", raw, cooked.getHoverName()).withStyle(ChatFormatting.GOLD));
            break;
        }
        List<ServerPlayer> fed = new java.util.ArrayList<>(sp.serverLevel().getPlayers(p -> p != sp && p.distanceToSqr(sp) < 24 * 24));
        fed.add(sp);
        for (ServerPlayer p : fed) {
            CombatState.of(p).buffs.merge(STAMINA, 45 * 20, Math::max);
            if (p.getFoodData().getFoodLevel() < 20) p.getFoodData().eat(2, 0.4f);
        }
        for (GLNpc npc : sp.level().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(24), n -> CrewManager.isCrewOf(n, sp)))
            if (npc.getHealth() < npc.getMaxHealth()) npc.heal(3f);
        return cooked;
    }

    private Meals() {}
}
