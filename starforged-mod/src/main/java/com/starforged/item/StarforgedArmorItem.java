package com.starforged.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Armor piece with a special ability. The abilities themselves live in {@link com.starforged.event.ArmorAbilities}.
 */
public class StarforgedArmorItem extends Item {
    public enum Ability {
        STARMETAL_SET("ability.starforged.starmetal_set"),
        COMET_BOOTS("ability.starforged.comet_boots"),
        NEBULA_CLOAK("ability.starforged.nebula_cloak"),
        ECLIPSE_CROWN("ability.starforged.eclipse_crown"),
        SUNSTEEL_SET("ability.starforged.sunsteel_set"),
        PHOENIX_MANTLE("ability.starforged.phoenix_mantle"),
        MAGMA_TREADS("ability.starforged.magma_treads"),
        SOLAR_CROWN("ability.starforged.solar_crown"),
        MOONSILVER_SET("ability.starforged.moonsilver_set"),
        TIDE_CROWN("ability.starforged.crown_of_tides");

        private final String key;

        Ability(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }
    }

    private final Ability ability;

    public StarforgedArmorItem(Item.Properties properties, Ability ability) {
        super(properties);
        this.ability = ability;
    }

    public Ability ability() {
        return this.ability;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, this.ability.key());
    }
}
