package com.terracraft.item.modifier;

import com.terracraft.combat.DamageClass;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.tool.TerrariaToolItem;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.MeleeWeaponItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Terraria's prefixes and how they are applied. The modifier id is stored on the stack
 * ({@code terracraft:modifier}); applying one recomputes the stack's stats from the item's base stats, and for
 * swung weapons also its attack attributes. Crafted weapons and accessories get a random prefix; the Goblin
 * Tinkerer rerolls it for coins.
 */
public final class Modifiers {
    private static final Map<String, Modifier> ALL = new LinkedHashMap<>();

    static {
        // universal (any weapon)
        weapon("keen", Modifier.Category.UNIVERSAL, 0, 0, 3, 0, 0, 0, 0);
        weapon("superior", Modifier.Category.UNIVERSAL, 0.10F, 0, 3, 0.10F, 0, 0, 0);
        weapon("forceful", Modifier.Category.UNIVERSAL, 0, 0, 0, 0.15F, 0, 0, 0);
        weapon("broken", Modifier.Category.UNIVERSAL, -0.30F, 0, 0, -0.20F, 0, 0, 0);
        weapon("damaged", Modifier.Category.UNIVERSAL, -0.15F, 0, 0, 0, 0, 0, 0);
        weapon("shoddy", Modifier.Category.UNIVERSAL, -0.10F, 0, 0, -0.15F, 0, 0, 0);
        weapon("hurtful", Modifier.Category.UNIVERSAL, 0.10F, 0, 0, 0, 0, 0, 0);
        weapon("strong", Modifier.Category.UNIVERSAL, 0, 0, 0, 0.15F, 0, 0, 0);
        weapon("unpleasant", Modifier.Category.UNIVERSAL, 0.05F, 0, 0, 0.15F, 0, 0, 0);
        weapon("weak", Modifier.Category.UNIVERSAL, 0, 0, 0, -0.20F, 0, 0, 0);
        weapon("ruthless", Modifier.Category.UNIVERSAL, 0.18F, 0, 0, -0.10F, 0, 0, 0);
        weapon("godly", Modifier.Category.UNIVERSAL, 0.15F, 0, 5, 0.15F, 0, 0, 0);
        weapon("demonic", Modifier.Category.UNIVERSAL, 0.15F, 0, 5, 0, 0, 0, 0);
        weapon("zealous", Modifier.Category.UNIVERSAL, 0, 0, 5, 0, 0, 0, 0);
        // melee
        weapon("large", Modifier.Category.MELEE, 0, 0, 0, 0, 0, 0, 0.12F);
        weapon("massive", Modifier.Category.MELEE, 0, 0, 0, 0, 0, 0, 0.18F);
        weapon("dangerous", Modifier.Category.MELEE, 0.05F, 0, 2, 0, 0, 0, 0.05F);
        weapon("savage", Modifier.Category.MELEE, 0.10F, 0, 0, 0.10F, 0, 0, 0.10F);
        weapon("sharp", Modifier.Category.MELEE, 0.15F, 0, 0, 0, 0, 0, 0);
        weapon("pointy", Modifier.Category.MELEE, 0.10F, 0, 0, 0, 0, 0, 0);
        weapon("tiny", Modifier.Category.MELEE, 0, 0, 0, 0, 0, 0, -0.18F);
        weapon("terrible", Modifier.Category.MELEE, -0.15F, 0, 0, -0.15F, 0, 0, -0.13F);
        weapon("small", Modifier.Category.MELEE, 0, 0, 0, 0, 0, 0, -0.10F);
        weapon("dull", Modifier.Category.MELEE, -0.15F, 0, 0, 0, 0, 0, 0);
        weapon("unhappy", Modifier.Category.MELEE, 0, -0.10F, 0, -0.10F, 0, 0, -0.10F);
        weapon("bulky", Modifier.Category.MELEE, 0.05F, -0.15F, 0, 0.10F, 0, 0, 0.10F);
        weapon("shameful", Modifier.Category.MELEE, -0.10F, 0, 0, -0.20F, 0, 0, -0.10F);
        weapon("heavy", Modifier.Category.MELEE, 0, -0.10F, 0, 0.15F, 0, 0, 0);
        weapon("light", Modifier.Category.MELEE, 0, 0.15F, 0, -0.10F, 0, 0, 0);
        weapon("legendary", Modifier.Category.MELEE, 0.15F, 0.10F, 5, 0.15F, 0, 0, 0.10F);
        // ranged
        weapon("sighted", Modifier.Category.RANGED, 0.10F, 0, 3, 0, 0, 0, 0);
        weapon("rapid", Modifier.Category.RANGED, 0, 0.15F, 0, 0, 0.10F, 0, 0);
        weapon("hasty", Modifier.Category.RANGED, 0, 0.10F, 0, 0, 0.15F, 0, 0);
        weapon("intimidating", Modifier.Category.RANGED, 0, 0, 0, 0.15F, 0.05F, 0, 0);
        weapon("deadly", Modifier.Category.RANGED, 0.10F, 0.05F, 5, 0.05F, 0.05F, 0, 0);
        weapon("staunch", Modifier.Category.RANGED, 0.10F, 0, 0, 0.15F, 0, 0, 0);
        weapon("awful", Modifier.Category.RANGED, -0.15F, 0, 0, -0.10F, -0.10F, 0, 0);
        weapon("lethargic", Modifier.Category.RANGED, 0, -0.15F, 0, 0, -0.10F, 0, 0);
        weapon("awkward", Modifier.Category.RANGED, 0, -0.10F, 0, -0.20F, 0, 0, 0);
        weapon("powerful", Modifier.Category.RANGED, 0.15F, -0.12F, 1, 0, 0, 0, 0);
        weapon("frenzying", Modifier.Category.RANGED, -0.15F, 0.15F, 0, 0, 0, 0, 0);
        weapon("unreal", Modifier.Category.RANGED, 0.15F, 0.10F, 5, 0.15F, 0.10F, 0, 0);
        // magic
        weapon("mystic", Modifier.Category.MAGIC, 0.10F, 0, 0, 0, 0, -0.15F, 0);
        weapon("adept", Modifier.Category.MAGIC, 0, 0, 0, 0, 0, -0.15F, 0);
        weapon("masterful", Modifier.Category.MAGIC, 0.15F, 0, 0, 0.05F, 0, -0.20F, 0);
        weapon("inept", Modifier.Category.MAGIC, 0, 0, 0, 0, 0, 0.10F, 0);
        weapon("ignorant", Modifier.Category.MAGIC, -0.10F, 0, 0, 0, 0, 0.20F, 0);
        weapon("deranged", Modifier.Category.MAGIC, -0.10F, 0, 0, -0.10F, 0, 0, 0);
        weapon("intense", Modifier.Category.MAGIC, 0.10F, 0, 0, 0, 0, 0.15F, 0);
        weapon("taboo", Modifier.Category.MAGIC, 0, 0.10F, 0, 0.10F, 0, 0.10F, 0);
        weapon("celestial", Modifier.Category.MAGIC, 0.10F, -0.10F, 0, 0.10F, 0, -0.10F, 0);
        weapon("furious", Modifier.Category.MAGIC, 0.15F, 0, 0, 0.15F, 0, 0.20F, 0);
        weapon("manic", Modifier.Category.MAGIC, -0.10F, 0.10F, 0, 0, 0, -0.10F, 0);
        weapon("mythical", Modifier.Category.MAGIC, 0.15F, 0.10F, 5, 0.15F, 0, -0.10F, 0);
        // accessories
        accessory("hard", 1, 0, 0, 0, 0, 0);
        accessory("guarding", 2, 0, 0, 0, 0, 0);
        accessory("armored", 3, 0, 0, 0, 0, 0);
        accessory("warding", 4, 0, 0, 0, 0, 0);
        accessory("arcane", 0, 20, 0, 0, 0, 0);
        accessory("precise", 0, 0, 2, 0, 0, 0);
        accessory("lucky", 0, 0, 4, 0, 0, 0);
        accessory("jagged", 0, 0, 0, 0.01F, 0, 0);
        accessory("spiked", 0, 0, 0, 0.02F, 0, 0);
        accessory("angry", 0, 0, 0, 0.03F, 0, 0);
        accessory("menacing", 0, 0, 0, 0.04F, 0, 0);
        accessory("brisk", 0, 0, 0, 0, 0.01F, 0);
        accessory("fleeting", 0, 0, 0, 0, 0.02F, 0);
        accessory("hasty_accessory", 0, 0, 0, 0, 0.03F, 0);
        accessory("quick", 0, 0, 0, 0, 0.04F, 0);
        accessory("wild", 0, 0, 0, 0, 0, 0.01F);
        accessory("rash", 0, 0, 0, 0, 0, 0.02F);
        accessory("intrepid", 0, 0, 0, 0, 0, 0.03F);
        accessory("violent", 0, 0, 0, 0, 0, 0.04F);
    }

    private Modifiers() {}

    private static void weapon(String id, Modifier.Category category, float damage, float speed, int crit, float knockback, float velocity,
                               float mana, float size) {
        ALL.put(id, new Modifier(id, category, damage, speed, crit, knockback, velocity, mana, size, 0, 0, 0, 0, 0));
    }

    private static void accessory(String id, int defense, int maxMana, int crit, float damage, float moveSpeed, float meleeSpeed) {
        ALL.put(id, new Modifier(id, Modifier.Category.ACCESSORY, 0, 0, crit, 0, 0, 0, 0, defense, maxMana, damage, moveSpeed, meleeSpeed));
    }

    public static @Nullable Modifier get(String id) {
        return ALL.get(id);
    }

    public static @Nullable Modifier of(ItemStack stack) {
        String id = stack.get(ModDataComponents.MODIFIER);
        return id == null ? null : ALL.get(id);
    }

    /** Which prefix family an item takes (null = cannot be reforged). */
    public static @Nullable Modifier.Category typeOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() > 1) {
            return null;
        }
        if (stack.getItem() instanceof AccessoryItem) {
            return Modifier.Category.ACCESSORY;
        }
        if (stack.getItem() instanceof MagicWeaponItem) {
            return Modifier.Category.MAGIC;
        }
        if (stack.getItem() instanceof RangedWeaponItem) {
            return Modifier.Category.RANGED;
        }
        if (stack.getItem() instanceof MeleeWeaponItem || stack.getItem() instanceof TerrariaToolItem) {
            return Modifier.Category.MELEE;
        }
        return null;
    }

    /** A random prefix for this item, or null if it takes none. */
    public static @Nullable Modifier roll(ItemStack stack, RandomSource random) {
        Modifier.Category type = typeOf(stack);
        if (type == null) {
            return null;
        }
        List<Modifier> options = new ArrayList<>();
        for (Modifier modifier : ALL.values()) {
            if (Modifier.categoriesFor(type).contains(modifier.category())) {
                options.add(modifier);
            }
        }
        return options.get(random.nextInt(options.size()));
    }

    /** Freshly crafted gear: 3 in 4 get a random prefix, like Terraria. */
    public static void rollOnCreate(ItemStack stack, RandomSource random) {
        if (typeOf(stack) != null && random.nextInt(4) != 0) {
            Modifier modifier = roll(stack, random);
            if (modifier != null) {
                apply(stack, modifier);
            }
        }
    }

    /** Applies a prefix: base stats from the item, scaled, written to the stack (melee attributes too). */
    public static void apply(ItemStack stack, Modifier modifier) {
        TerraItemStats base = stack.getItem().components().getOrDefault(ModDataComponents.STATS, TerraItemStats.NONE);
        TerraItemStats.Builder stats = TerraItemStats.builder()
            .damage(base.damageClass(), Math.max(base.damage() > 0 ? 1 : 0, Math.round(base.damage() * (1.0F + modifier.damage()))))
            .useTime(base.useTime() <= 0 ? 0 : Math.max(1, Math.round(base.useTime() / (1.0F + modifier.speed()))))
            .knockback(base.knockback() * (1.0F + modifier.knockback()))
            .crit(base.crit() + (modifier.category() == Modifier.Category.ACCESSORY ? 0 : modifier.crit()))
            .mana(base.mana() <= 0 ? 0 : Math.max(1, Math.round(base.mana() * (1.0F + modifier.mana()))))
            .velocity(base.velocity() * (1.0F + modifier.velocity()))
            .pickaxe(base.pickPower()).axe(base.axePower()).hammer(base.hammerPower())
            .defense(base.defense() + modifier.defense())
            .rarity(base.rarity())
            .value(Math.max(0, Math.round(base.value() * Mth.clamp(1.0F + modifier.quality(), 0.3F, 3.0F))));
        TerraItemStats result = stats.build();
        stack.set(ModDataComponents.STATS, result);
        stack.set(ModDataComponents.MODIFIER, modifier.id());
        if (base.damageClass() == DamageClass.MELEE && stack.getItem().components().has(DataComponents.ATTRIBUTE_MODIFIERS)) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, WeaponProperties.meleeAttributes(result));
        }
    }
}
