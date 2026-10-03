package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.effect.TerraBuffEffect;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Terraria buffs and debuffs. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TerraCraft.MODID);

    public static final RegistryObject<MobEffect> IRONSKIN = buff("ironskin", 0xB4B4B4, StatEffects.builder().add(Stat.DEFENSE, 8));
    public static final RegistryObject<MobEffect> SWIFTNESS = buff("swiftness", 0x5AE65A, StatEffects.builder().add(Stat.MOVE_SPEED, 0.25F));
    public static final RegistryObject<MobEffect> REGENERATION = buff("regeneration", 0xFF6496, StatEffects.builder().add(Stat.LIFE_REGEN, 4));
    public static final RegistryObject<MobEffect> MANA_REGENERATION = buff("mana_regeneration", 0x6464FF, StatEffects.builder().add(Stat.MANA_REGEN, 25));
    public static final RegistryObject<MobEffect> MAGIC_POWER = buff("magic_power", 0xC864FF, StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.20F));
    public static final RegistryObject<MobEffect> ARCHERY = buff("archery", 0xC8A064, StatEffects.builder().add(Stat.RANGED_DAMAGE, 0.10F));
    public static final RegistryObject<MobEffect> MINING = buff("mining", 0xC8C864, StatEffects.builder().add(Stat.MINING_SPEED, 0.25F));
    public static final RegistryObject<MobEffect> OBSIDIAN_SKIN = buff("obsidian_skin", 0x50285A,
        StatEffects.builder().ability(Ability.FIRE_BLOCK_IMMUNE).ability(Ability.LAVA_IMMUNE));
    public static final RegistryObject<MobEffect> WATER_WALKING = buff("water_walking", 0x3C8CFF, StatEffects.builder().ability(Ability.WATER_WALKING));
    public static final RegistryObject<MobEffect> ENDURANCE = buff("endurance", 0x8C8CB4, StatEffects.builder().add(Stat.ENDURANCE, 0.10F));
    public static final RegistryObject<MobEffect> WRATH = buff("wrath", 0xDC3C3C, StatEffects.builder().add(Stat.DAMAGE, 0.10F));
    public static final RegistryObject<MobEffect> RAGE = buff("rage", 0xFF7814, StatEffects.builder().add(Stat.CRIT, 10));

    /** Food buffs, from eating (hunger is disabled; see {@link com.terracraft.player.FoodManager}). */
    public static final RegistryObject<MobEffect> WELL_FED = buff("well_fed", 0xE6B450, food(1));
    public static final RegistryObject<MobEffect> PLENTY_SATISFIED = buff("plenty_satisfied", 0xE68C3C, food(2));
    public static final RegistryObject<MobEffect> EXQUISITELY_STUFFED = buff("exquisitely_stuffed", 0xE6643C, food(3));

    public static final RegistryObject<MobEffect> POTION_SICKNESS = debuff("potion_sickness", 0x784646, StatEffects.NONE);
    public static final RegistryObject<MobEffect> MANA_SICKNESS = debuff("mana_sickness", 0x463278, StatEffects.builder().add(Stat.MAGIC_DAMAGE, -0.25F).build());

    private ModEffects() {}

    /** Terraria's food buff tiers: Well Fed, Plenty Satisfied and Exquisitely Stuffed. */
    private static StatEffects.Builder food(int tier) {
        return StatEffects.builder()
            .add(Stat.DEFENSE, 1 + tier)
            .add(Stat.CRIT, 1 + tier)
            .add(Stat.DAMAGE, 0.025F * (1 + tier))
            .add(Stat.MELEE_SPEED, 0.025F * (1 + tier))
            .add(Stat.KNOCKBACK, 0.025F * (1 + tier))
            .add(Stat.MOVE_SPEED, 0.05F * tier)
            .add(Stat.MINING_SPEED, 0.05F * (1 + tier));
    }

    private static RegistryObject<MobEffect> buff(String name, int color, StatEffects.Builder effects) {
        return EFFECTS.register(name, () -> new TerraBuffEffect(MobEffectCategory.BENEFICIAL, color, effects.build()));
    }

    private static RegistryObject<MobEffect> debuff(String name, int color, StatEffects effects) {
        return EFFECTS.register(name, () -> new TerraBuffEffect(MobEffectCategory.HARMFUL, color, effects));
    }

    public static Holder<MobEffect> holder(RegistryObject<MobEffect> effect) {
        return effect.getHolder().orElseThrow();
    }
}
