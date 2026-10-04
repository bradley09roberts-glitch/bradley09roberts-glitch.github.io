package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.summon.MinionEntity;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.summon.SummonStaffItem;
import com.terracraft.item.summon.WhipItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModEntities;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The summoner class: minions (follow you and fight), sentries (stay where placed and shoot), the staffs that call
 * them, whips (strike and tag enemies for your minions) and summoner gear.
 */
public final class SummonContent {
    private static final List<RegistryObject<? extends EntityType<? extends MinionEntity>>> MINIONS = new ArrayList<>();

    // --- minions -------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<MinionEntity>> SLIME_MINION = minion("slime_minion", 0.6F, 0.5F,
        MinionEntity.Spec.rammer(0.4));
    public static final RegistryObject<EntityType<MinionEntity>> HORNET_MINION = minion("hornet_minion", 0.6F, 0.5F,
        MinionEntity.Spec.shooter(() -> ProjectileKinds.MINION_STINGER, 40, 9.0F));
    public static final RegistryObject<EntityType<MinionEntity>> IMP_MINION = minion("imp_minion", 0.6F, 0.8F,
        MinionEntity.Spec.shooter(() -> ProjectileKinds.MINION_FIREBALL, 45, 8.0F));
    public static final RegistryObject<EntityType<MinionEntity>> OPTIC_MINION = minion("optic_minion", 0.7F, 0.7F,
        MinionEntity.Spec.shooter(() -> ProjectileKinds.MINION_LASER, 25, 14.0F));
    public static final RegistryObject<EntityType<MinionEntity>> PYGMY_MINION = minion("pygmy_minion", 0.5F, 0.9F,
        MinionEntity.Spec.shooter(() -> ProjectileKinds.PYGMY_SPEAR, 35, 10.0F));
    public static final RegistryObject<EntityType<MinionEntity>> TEMPEST_MINION = minion("tempest_minion", 0.7F, 1.0F,
        MinionEntity.Spec.shooter(() -> ProjectileKinds.MINI_SHARK, 40, 9.0F));
    public static final RegistryObject<EntityType<MinionEntity>> UFO_MINION = minion("ufo_minion", 0.8F, 0.4F,
        MinionEntity.Spec.shooter(() -> ProjectileKinds.UFO_LASER, 20, 16.0F));
    public static final RegistryObject<EntityType<MinionEntity>> DEADLY_SPHERE_MINION = minion("deadly_sphere_minion", 0.6F, 0.6F,
        MinionEntity.Spec.rammer(0.9));
    public static final RegistryObject<EntityType<MinionEntity>> TERRAPRISMA_MINION = minion("terraprisma_minion", 0.5F, 0.5F,
        MinionEntity.Spec.rammer(1.3));
    public static final RegistryObject<EntityType<MinionEntity>> STARDUST_CELL_MINION = minion("stardust_cell_minion", 0.6F, 0.6F,
        MinionEntity.Spec.shooter(() -> ProjectileKinds.STARDUST_SHOT, 25, 14.0F));
    public static final RegistryObject<EntityType<MinionEntity>> STARDUST_DRAGON_MINION = minion("stardust_dragon_minion", 0.8F, 0.6F,
        MinionEntity.Spec.rammer(1.1));
    // --- sentries ------------------------------------------------------------------------------------------
    public static final RegistryObject<EntityType<MinionEntity>> RAINBOW_CRYSTAL = minion("rainbow_crystal", 0.8F, 1.0F,
        MinionEntity.Spec.sentry(() -> ProjectileKinds.RAINBOW_BOLT, 15, 10.0F));
    public static final RegistryObject<EntityType<MinionEntity>> LUNAR_PORTAL = minion("lunar_portal", 1.2F, 1.6F,
        MinionEntity.Spec.sentry(() -> ProjectileKinds.PORTAL_LASER, 8, 20.0F));

    // --- staffs (damage, mana, use time, rarity, value) ----------------------------------------------------
    public static final RegistryObject<SummonStaffItem> SLIME_STAFF = staff("slime_staff", SLIME_MINION, 6, 10, TerraRarity.GREEN, 10000);
    public static final RegistryObject<SummonStaffItem> HORNET_STAFF = staff("hornet_staff", HORNET_MINION, 12, 10, TerraRarity.GREEN, 20000);
    public static final RegistryObject<SummonStaffItem> IMP_STAFF = staff("imp_staff", IMP_MINION, 21, 10, TerraRarity.ORANGE, 35000);
    public static final RegistryObject<SummonStaffItem> OPTIC_STAFF = staff("optic_staff", OPTIC_MINION, 32, 10, TerraRarity.PINK, 100000);
    public static final RegistryObject<SummonStaffItem> PYGMY_STAFF = staff("pygmy_staff", PYGMY_MINION, 34, 10, TerraRarity.LIME, 100000);
    public static final RegistryObject<SummonStaffItem> TEMPEST_STAFF = staff("tempest_staff", TEMPEST_MINION, 50, 10, TerraRarity.YELLOW, 250000);
    public static final RegistryObject<SummonStaffItem> XENO_STAFF = staff("xeno_staff", UFO_MINION, 50, 10, TerraRarity.YELLOW, 250000);
    public static final RegistryObject<SummonStaffItem> DEADLY_SPHERE_STAFF = staff("deadly_sphere_staff", DEADLY_SPHERE_MINION, 50, 10,
        TerraRarity.YELLOW, 200000);
    public static final RegistryObject<SummonStaffItem> TERRAPRISMA = staff("terraprisma", TERRAPRISMA_MINION, 100, 10, TerraRarity.CYAN, 500000);
    public static final RegistryObject<SummonStaffItem> STARDUST_CELL_STAFF = staff("stardust_cell_staff", STARDUST_CELL_MINION, 60, 10,
        TerraRarity.RED, 500000);
    public static final RegistryObject<SummonStaffItem> STARDUST_DRAGON_STAFF = staff("stardust_dragon_staff", STARDUST_DRAGON_MINION, 80, 10,
        TerraRarity.RED, 500000);
    public static final RegistryObject<SummonStaffItem> RAINBOW_CRYSTAL_STAFF = staff("rainbow_crystal_staff", RAINBOW_CRYSTAL, 150, 10,
        TerraRarity.RED, 500000);
    public static final RegistryObject<SummonStaffItem> LUNAR_PORTAL_STAFF = staff("lunar_portal_staff", LUNAR_PORTAL, 50, 10,
        TerraRarity.RED, 500000);

    // --- whips (lash kind, damage, use time, rarity, value) ------------------------------------------------
    public static final ProjectileKind LEATHER_WHIP_LASH = lash("leather_whip", 3.5F, 4, 10, null);
    public static final ProjectileKind SNAPTHORN_LASH = lash("snapthorn", 4.0F, 6, 11, ProjectileKind.builder("snapthorn")
        .debuff(() -> net.minecraft.world.effect.MobEffects.POISON, 100, 1.0F));
    public static final ProjectileKind FIRECRACKER_LASH = lash("firecracker", 4.2F, 10, 11, ProjectileKind.builder("firecracker")
        .ignites(100, 1.0F));
    public static final ProjectileKind DURENDAL_LASH = lash("durendal", 4.6F, 9, 11, null);
    public static final ProjectileKind DARK_HARVEST_LASH = lash("dark_harvest", 5.0F, 12, 12, null);
    public static final ProjectileKind KALEIDOSCOPE_LASH = lash("kaleidoscope", 5.5F, 30, 12, null);

    public static final RegistryObject<WhipItem> LEATHER_WHIP = whip("leather_whip", () -> LEATHER_WHIP_LASH, 8, 24, TerraRarity.BLUE, 5000);
    public static final RegistryObject<WhipItem> SNAPTHORN = whip("snapthorn", () -> SNAPTHORN_LASH, 13, 24, TerraRarity.GREEN, 20000);
    public static final RegistryObject<WhipItem> FIRECRACKER = whip("firecracker", () -> FIRECRACKER_LASH, 28, 24, TerraRarity.ORANGE, 40000);
    public static final RegistryObject<WhipItem> DURENDAL = whip("durendal", () -> DURENDAL_LASH, 44, 22, TerraRarity.PINK, 100000);
    public static final RegistryObject<WhipItem> DARK_HARVEST = whip("dark_harvest", () -> DARK_HARVEST_LASH, 60, 22, TerraRarity.YELLOW, 200000);
    public static final RegistryObject<WhipItem> KALEIDOSCOPE = whip("kaleidoscope", () -> KALEIDOSCOPE_LASH, 180, 20, TerraRarity.CYAN, 500000);

    // --- summoner gear -------------------------------------------------------------------------------------
    public static final RegistryObject<AccessoryItem> PYGMY_NECKLACE = ModItems.register("pygmy_necklace", TabGroup.ACCESSORIES,
        p -> new AccessoryItem(p, StatEffects.builder().add(Stat.MAX_MINIONS, 1).build()),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.LIGHT_RED).value(100000).build()));
    public static final RegistryObject<AccessoryItem> PAPYRUS_SCARAB = ModItems.register("papyrus_scarab", TabGroup.ACCESSORIES,
        p -> new AccessoryItem(p, StatEffects.builder().add(Stat.MAX_MINIONS, 1).add(Stat.SUMMON_DAMAGE, 0.15F).build()),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.LIME).value(200000).build()));
    /** Bee armor (Bee Wax): the first summoner set. */
    public static final ArmorContent.ArmorPieces BEE = ArmorContent.named("bee", new String[]{"bee_headgear", "bee_breastplate", "bee_greaves"},
        new int[]{4, 5, 4},
        new StatEffects[]{
            StatEffects.builder().add(Stat.MAX_MINIONS, 1).add(Stat.SUMMON_DAMAGE, 0.04F).build(),
            StatEffects.builder().add(Stat.SUMMON_DAMAGE, 0.04F).build(),
            StatEffects.builder().add(Stat.MAX_MINIONS, 1).add(Stat.SUMMON_DAMAGE, 0.05F).build()},
        StatEffects.builder().add(Stat.SUMMON_DAMAGE, 0.10F).build(), TerraRarity.GREEN, 10000);
    /** Spooky armor (Spooky Wood, Pumpkin Moon): the Hardmode summoner set. */
    public static final ArmorContent.ArmorPieces SPOOKY = ArmorContent.named("spooky", new String[]{"spooky_helmet", "spooky_breastplate",
            "spooky_leggings"}, new int[]{7, 8, 7},
        new StatEffects[]{
            StatEffects.builder().add(Stat.MAX_MINIONS, 1).add(Stat.SUMMON_DAMAGE, 0.11F).build(),
            StatEffects.builder().add(Stat.MAX_MINIONS, 1).add(Stat.SUMMON_DAMAGE, 0.11F).build(),
            StatEffects.builder().add(Stat.MAX_MINIONS, 1).add(Stat.SUMMON_DAMAGE, 0.11F).build()},
        StatEffects.builder().add(Stat.SUMMON_DAMAGE, 0.25F).build(), TerraRarity.YELLOW, 100000);

    private SummonContent() {}

    public static void init() {
        com.terracraft.TerraCraft.modBus().addListener(SummonContent::onAttributes);
    }

    public static List<RegistryObject<? extends EntityType<? extends MinionEntity>>> minions() {
        return MINIONS;
    }

    private static RegistryObject<EntityType<MinionEntity>> minion(String name, float width, float height, MinionEntity.Spec spec) {
        RegistryObject<EntityType<MinionEntity>> type = ModEntities.ENTITY_TYPES.register(name, () -> EntityType.Builder
            .<MinionEntity>of((t, level) -> new MinionEntity(t, level, spec), MobCategory.MISC).sized(width, height).clientTrackingRange(8)
            .fireImmune().build(ModEntities.ENTITY_TYPES.key(name)));
        MINIONS.add(type);
        return type;
    }

    private static RegistryObject<SummonStaffItem> staff(String name, RegistryObject<? extends EntityType<? extends MinionEntity>> minion,
                                                         int damage, int mana, TerraRarity rarity, int value) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new SummonStaffItem(p, minion),
            p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().summon(damage).mana(mana).useTime(30).knockback(2.0F)
                .rarity(rarity).value(value).build()));
    }

    /** A whip's lash: the tip that flies out and back; {@code extra} carries debuffs for special whips. */
    private static ProjectileKind lash(String name, float range, int tag, int ticks, ProjectileKind.Builder extra) {
        ProjectileKind.Builder builder = extra != null ? extra : ProjectileKind.builder(name);
        return ProjectileKinds.registerExternal(builder.lifetime(ticks).size(0.3F, 0.5F)
            .orientation(ProjectileKind.Orientation.BILLBOARD).whip(range, tag));
    }

    private static RegistryObject<WhipItem> whip(String name, Supplier<ProjectileKind> lash, int damage, int useTime, TerraRarity rarity, int value) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new WhipItem(p, lash),
            p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().summon(damage).useTime(useTime).knockback(2.0F).crit(0).velocity(4.0F)
                .rarity(rarity).value(value).build()));
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        for (RegistryObject<? extends EntityType<? extends MinionEntity>> type : MINIONS) {
            event.put(type.get(), MinionEntity.attributes().build());
        }
    }
}
