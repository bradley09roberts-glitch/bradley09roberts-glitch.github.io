package com.grandline.activity;

import com.grandline.GrandLine;
import com.grandline.npc.crew.CrewBonuses;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.Quests;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.weather.SeaWeather;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Fishing at sea. Vanilla fishing stays as it is (rods, bobbers, waiting, junk and treasure); a global loot modifier
 * on {@code minecraft:gameplay/fishing} turns the fish of a roll into the catch of the sea where the bobber floats:
 * small fry in the Blues, exotic fish in Paradise, giants in the Calm Belt and the New World, deep-sea fish in the
 * deep sea, cloud skippers on the White Sea, and now and then a Sea King fry on the Grand Line. Island ponds keep the
 * vanilla catch. Rough weather and a crew navigator bring up giants and Sea King fry more often; a crew cook handles
 * the catch better (quality). The fishing skill (mastery {@value #MASTERY}) raises both the share of sea catches and
 * their quality; a torn map half sometimes comes up on the line.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class SeaFishing {
    public static final String MASTERY = "activity/fishing";

    /** The loot modifier (registered as grandline:sea_fishing; JSON in data/grandline/loot_modifiers). */
    public static final class Modifier extends LootModifier {
        public static final MapCodec<Modifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, Modifier::new));

        public Modifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
            return modify(loot, ctx);
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    public record Catch(DeferredItem<Item> item, int weight, boolean giant) {}

    /** What bites in each sea. */
    public static List<Catch> table(SeaZone zone) {
        return switch (zone) {
            case PARADISE -> List.of(new Catch(ActivityItems.RAINBOW_TUNA, 55, false), new Catch(ActivityItems.NEEDLENOSE_GAR, 45, false));
            case CALM_BELT -> List.of(new Catch(ActivityItems.BEHEMOTH_CARP, 65, true), new Catch(ActivityItems.NEEDLENOSE_GAR, 35, false));
            case NEW_WORLD -> List.of(new Catch(ActivityItems.CRIMSON_MARLIN, 45, true), new Catch(ActivityItems.THUNDER_EEL, 55, false));
            case SKY_SEA -> List.of(new Catch(ActivityItems.CLOUD_SKIPPER, 100, false));
            case DEEP_SEA -> List.of(new Catch(ActivityItems.ABYSS_LANTERNFISH, 55, false), new Catch(ActivityItems.GHOST_RAY, 45, false));
            default -> List.of(new Catch(ActivityItems.BLUE_SARDINE, 60, false), new Catch(ActivityItems.HARBOR_BREAM, 40, false)); // the Blues
        };
    }

    public static boolean isGiant(ItemStack s) {
        return s.is(ActivityItems.BEHEMOTH_CARP.get()) || s.is(ActivityItems.CRIMSON_MARLIN.get());
    }

    /** Odds of one fishing roll at sea. */
    public record Odds(double seaChance, double fryChance, double giantBoost, double fineChance, double primeChance, double mapChance) {}

    public static Odds odds(SeaZone zone, int mastery, boolean navigator, boolean cook, SeaWeather.Type weather, float intensity) {
        double rough = weather == SeaWeather.Type.STORM || weather == SeaWeather.Type.CYCLONE ? intensity : 0;
        double sea = 0.6 + Math.min(0.25, mastery * 0.004) + (weather == SeaWeather.Type.RAIN ? 0.05 : 0) + (rough > 0 ? 0.1 : 0);
        double fry = switch (zone) {
            case CALM_BELT -> 0.05;
            case NEW_WORLD -> 0.025;
            case PARADISE -> 0.015;
            case DEEP_SEA -> 0.01;
            default -> 0;
        };
        if (fry > 0) fry = fry * (1 + rough) * (navigator ? 2 : 1) + mastery * 0.0002;
        double giant = (1 + rough) * (navigator ? 1.5 : 1);
        double fine = Math.min(0.55, 0.04 + mastery * 0.02 + (cook ? 0.1 : 0));
        double prime = Math.min(0.3, mastery * 0.006 + (cook ? 0.03 : 0));
        double map = 0.012 + (navigator ? 0.01 : 0) + rough * 0.01;
        return new Odds(Math.min(0.92, sea), fry, giant, fine, prime, map);
    }

    /** The sea at a bobber position, or null when it is not at sea (island ponds, other dimensions). */
    @Nullable
    public static SeaZone seaAt(ServerLevel level, double x, double z) {
        WorldPlan plan = WorldPlan.forLevel(level);
        if (plan == null) return null;
        Island isl = plan.islandAt((int) Math.floor(x), (int) Math.floor(z));
        if (isl != null && isl.t((int) Math.floor(x), (int) Math.floor(z)) < 0.97) return null;
        return switch (plan.kind) {
            case SKY -> SeaZone.SKY_SEA;
            case DEEP -> SeaZone.DEEP_SEA;
            default -> WorldLayout.zoneAt((int) Math.floor(x), (int) Math.floor(z));
        };
    }

    @Nullable
    static Player angler(LootContext ctx) {
        if (ctx.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof Player p) return p;
        if (ctx.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof FishingHook h) return h.getPlayerOwner();
        return null;
    }

    static ObjectArrayList<ItemStack> modify(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        Vec3 o = ctx.getParamOrNull(LootContextParams.ORIGIN);
        if (o == null) return loot;
        ServerLevel level = ctx.getLevel();
        SeaZone zone = seaAt(level, o.x, o.z);
        if (zone == null) return loot;
        Player player = angler(ctx);
        int mastery = player == null ? 0 : Characters.get(player).mastery(MASTERY);
        Set<String> roles = player instanceof ServerPlayer sp ? CrewBonuses.activeRoles(sp) : Set.of();
        SeaWeather.Here w = SeaWeather.at(level, o.x, o.z);
        Odds odds = odds(zone, mastery, roles.contains("navigator"), roles.contains("cook"), w.type(), w.intensity());
        RandomSource r = ctx.getRandom();
        for (int i = 0; i < loot.size(); i++) {
            if (!loot.get(i).is(ItemTags.FISHES) || r.nextDouble() >= odds.seaChance()) continue;
            loot.set(i, roll(zone, odds, r));
        }
        if (r.nextDouble() < odds.mapChance()) {
            ItemStack half = new ItemStack(ActivityItems.TORN_MAP.get());
            half.set(ActivityRegistry.MAP_HALF.get(), r.nextInt(2));
            loot.add(half);
        }
        return loot;
    }

    /** One sea catch with its quality. */
    public static ItemStack roll(SeaZone zone, Odds odds, RandomSource r) {
        ItemStack out;
        if (odds.fryChance() > 0 && r.nextDouble() < odds.fryChance()) out = new ItemStack(ActivityItems.SEA_KING_FRY.get());
        else {
            List<Catch> t = table(zone);
            double total = 0;
            for (Catch c : t) total += c.weight() * (c.giant() ? odds.giantBoost() : 1);
            double pick = r.nextDouble() * total;
            Catch chosen = t.get(t.size() - 1);
            for (Catch c : t) {
                pick -= c.weight() * (c.giant() ? odds.giantBoost() : 1);
                if (pick < 0) {
                    chosen = c;
                    break;
                }
            }
            out = new ItemStack(chosen.item().get());
        }
        double q = r.nextDouble();
        int quality = q < odds.primeChance() ? 2 : q < odds.primeChance() + odds.fineChance() ? 1 : 0;
        if (quality > 0) out.set(ActivityRegistry.QUALITY.get(), quality);
        return out;
    }

    // ================================================================== the skill

    @SubscribeEvent
    public static void onFished(ItemFishedEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) onCatch(sp, e.getDrops());
    }

    /** Trains the fishing skill and counts quest catches for what came up on the line; returns the mastery XP given. */
    public static int onCatch(ServerPlayer sp, List<ItemStack> drops) {
        CharacterData d = Characters.get(sp);
        if (!d.created) return 0;
        int xp = 2;
        for (ItemStack st : drops) {
            if (!(st.getItem() instanceof SeaFishItem)) continue;
            boolean fry = st.is(ActivityItems.SEA_KING_FRY.get());
            xp += (fry ? 40 : isGiant(st) ? 14 : 8) + Meals.quality(st) * 4;
            Quests.progress(sp, "fish", BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), 1);
            if (fry || isGiant(st) || Meals.quality(st) == 2)
                sp.displayClientMessage(Component.translatable("grandline.fishing.big", st.getHoverName()).withStyle(ChatFormatting.AQUA), true);
        }
        if (d.addMastery(MASTERY, xp)) sp.sendSystemMessage(Component.translatable("grandline.fishing.level", d.mastery(MASTERY)).withStyle(ChatFormatting.AQUA));
        CharacterManager.markDirty(sp);
        return xp;
    }

    private SeaFishing() {}
}
