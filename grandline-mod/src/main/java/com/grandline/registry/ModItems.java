package com.grandline.registry;

import com.grandline.GrandLine;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.item.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GrandLine.MODID);
    public static final List<DeferredItem<GLWeaponItem>> WEAPONS = new ArrayList<>();

    public static final DeferredItem<DevilFruitItem> DEVIL_FRUIT = ITEMS.register("devil_fruit", DevilFruitItem::new);
    public static final DeferredItem<Item> LOG_POSE = ITEMS.register("log_pose", () -> new LogPoseItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ETERNAL_POSE = ITEMS.register("eternal_pose", () -> new EternalPoseItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> VIVRE_CARD = ITEMS.register("vivre_card", () -> new VivreCardItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> SEA_CHART = ITEMS.register("sea_chart", () -> new SeaChartItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SEASTONE_CUFFS = ITEMS.register("seastone_cuffs", () -> new SeastoneCuffsItem(new Item.Properties().stacksTo(4)));
    public static final DeferredItem<Item> STYLE_SCROLL = ITEMS.register("style_scroll", () -> new StyleScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> BULLET = ITEMS.register("bullet", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> CANNONBALL = ITEMS.register("cannonball", () -> new Item(new Item.Properties().stacksTo(32)));
    public static final DeferredItem<Item> PELLET = ITEMS.register("pellet", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> POP_GREEN = ITEMS.register("pop_green", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> SEASTONE_SHARD = ITEMS.register("seastone_shard", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> MEAT_ON_BONE = ITEMS.register("meat_on_bone", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(10).saturationModifier(1.0f).build())));
    public static final DeferredItem<Item> SEA_KING_STEAK = ITEMS.register("sea_king_steak", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(14).saturationModifier(1.2f).build())));
    public static final DeferredItem<Item> WANTED_POSTER = ITEMS.register("wanted_poster", () -> new WantedPosterItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> PONEGLYPH_RUBBING = ITEMS.register("poneglyph_rubbing", () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private static DeferredItem<GLWeaponItem> weapon(String id, WeaponClass wc, String grade, float dmg, float speed, int dur, double reach) {
        DeferredItem<GLWeaponItem> it = ITEMS.register(id, () -> new GLWeaponItem(wc, grade, dmg, speed, dur, reach));
        WEAPONS.add(it);
        return it;
    }

    static {
        // ---- common weapons (one per class) ----
        weapon("katana", WeaponClass.KATANA, "common", 6.5f, 1.6f, 700, 0.3);
        weapon("cutlass", WeaponClass.CUTLASS, "common", 6f, 1.7f, 600, 0);
        weapon("rapier", WeaponClass.RAPIER, "common", 5f, 2.2f, 500, 0.5);
        weapon("nodachi", WeaponClass.NODACHI, "common", 8.5f, 1.0f, 900, 1.0);
        weapon("dual_blades", WeaponClass.DUAL_BLADES, "common", 5f, 2.0f, 600, 0);
        weapon("knife", WeaponClass.KNIFE, "common", 4f, 2.6f, 350, -0.5);
        weapon("throwing_knife", WeaponClass.THROWING_KNIVES, "common", 3f, 2.5f, 0, -0.5);
        weapon("spear", WeaponClass.SPEAR, "common", 6.5f, 1.3f, 650, 1.5);
        weapon("naginata", WeaponClass.NAGINATA, "common", 7f, 1.2f, 700, 1.5);
        weapon("war_trident", WeaponClass.TRIDENT_POLE, "common", 7f, 1.2f, 700, 1.2);
        weapon("bo_staff", WeaponClass.BO_STAFF, "common", 5f, 1.8f, 550, 1.0);
        weapon("kanabo", WeaponClass.KANABO, "common", 10f, 0.8f, 1000, 0.5);
        weapon("war_hammer", WeaponClass.HAMMER, "common", 9.5f, 0.85f, 900, 0.3);
        weapon("tonfa", WeaponClass.TONFA, "common", 5f, 2.2f, 500, -0.3);
        weapon("whip", WeaponClass.WHIP, "common", 4.5f, 1.6f, 450, 3.0);
        weapon("brass_knuckles", WeaponClass.GAUNTLETS, "common", 5f, 2.4f, 600, -0.5);
        weapon("seastone_jitte", WeaponClass.JITTE, "graded", 5.5f, 1.8f, 1200, 0);
        weapon("anchor_chain", WeaponClass.ANCHOR_CHAIN, "common", 9f, 0.9f, 1000, 2.0);
        weapon("flintlock", WeaponClass.FLINTLOCK, "common", 3f, 1.6f, 500, 0);
        weapon("musket", WeaponClass.RIFLE, "common", 3f, 1.2f, 500, 0);
        weapon("bazooka", WeaponClass.BAZOOKA, "common", 4f, 0.8f, 400, 0);
        weapon("hand_cannon", WeaponClass.HAND_CANNON, "common", 4f, 0.8f, 400, 0);
        weapon("slingshot", WeaponClass.SLINGSHOT, "common", 1f, 2.0f, 400, 0);
        weapon("war_bow", WeaponClass.BOW, "common", 1f, 2.0f, 450, 0);
        weapon("weather_staff", WeaponClass.WEATHER_STAFF, "graded", 4f, 1.6f, 800, 1.0);
        weapon("impact_dial", WeaponClass.DIAL, "graded", 2f, 1.6f, 250, 0);
        // ---- fine weapons ----
        weapon("fine_katana", WeaponClass.KATANA, "fine", 8f, 1.6f, 1500, 0.3);
        weapon("fine_cutlass", WeaponClass.CUTLASS, "fine", 7.5f, 1.7f, 1300, 0);
        weapon("steel_spear", WeaponClass.SPEAR, "fine", 8f, 1.3f, 1400, 1.5);
        weapon("iron_kanabo", WeaponClass.KANABO, "fine", 12f, 0.8f, 2000, 0.5);
        // ---- named swords (meito) — legendary loot ----
        weapon("enma", WeaponClass.KATANA, "supreme", 12f, 1.6f, 6000, 0.4);
        weapon("ame_no_habakiri", WeaponClass.KATANA, "supreme", 12f, 1.7f, 6000, 0.4);
        weapon("murakumogiri", WeaponClass.NAGINATA, "supreme", 13f, 1.2f, 6000, 1.6);
        weapon("yoru", WeaponClass.NODACHI, "black_blade", 14f, 1.0f, 8000, 1.2);
        weapon("shusui", WeaponClass.KATANA, "black_blade", 12.5f, 1.6f, 8000, 0.4);
        weapon("wado_ichimonji", WeaponClass.KATANA, "great", 10.5f, 1.7f, 4000, 0.3);
        weapon("sandai_kitetsu", WeaponClass.KATANA, "great", 10.5f, 1.7f, 4000, 0.3);
        weapon("yubashiri", WeaponClass.KATANA, "skillful", 9f, 1.9f, 3000, 0.3);
        // original named blades
        weapon("kurenai_shigure", WeaponClass.KATANA, "great", 10.5f, 1.7f, 4000, 0.3);
        weapon("abyssal_fang", WeaponClass.CUTLASS, "skillful", 9f, 1.8f, 3000, 0);
        weapon("tidebreaker", WeaponClass.ANCHOR_CHAIN, "great", 13f, 0.9f, 4000, 2.2);
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ModItems() {}
}
