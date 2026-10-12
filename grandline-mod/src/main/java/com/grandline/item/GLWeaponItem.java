package com.grandline.item;

import com.grandline.combat.CombatManager;
import com.grandline.combat.weapon.WeaponClass;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

import java.util.List;

/** A weapon of one {@link WeaponClass}. Grade is cosmetic + stat tier (common → black blade). */
public class GLWeaponItem extends Item {
    public final WeaponClass weaponClass;
    public final String grade;
    public final float damage;

    public GLWeaponItem(WeaponClass wc, String grade, float damage, float speed, int durability, double reach) {
        super(new Item.Properties().stacksTo(wc == WeaponClass.THROWING_KNIVES ? 16 : 1).durability(wc == WeaponClass.THROWING_KNIVES ? 0 : durability)
                .attributes(attributes(damage, speed, reach)));
        this.weaponClass = wc;
        this.grade = grade;
        this.damage = damage;
    }

    private static final ResourceLocation REACH_ID = com.grandline.GrandLine.id("weapon_reach");

    static ItemAttributeModifiers attributes(float damage, float speed, double reach) {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (reach != 0) b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        return b.build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!weaponClass.ranged) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (CombatManager.fireRanged(sp, stack, false)) {
                player.getCooldowns().addCooldown(this, cooldown());
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    public int cooldown() {
        return switch (weaponClass) {
            case FLINTLOCK -> 16;
            case RIFLE -> 30;
            case BAZOOKA, HAND_CANNON -> 60;
            case SLINGSHOT, THROWING_KNIVES -> 10;
            case BOW -> 14;
            case WEATHER_STAFF -> 30;
            default -> 20;
        };
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("weapon_class.grandline." + weaponClass.id()).withStyle(ChatFormatting.GRAY));
        if (!grade.equals("common")) tip.add(Component.translatable("grade.grandline." + grade).withStyle(gradeColor()));
        if (weaponClass.ranged) tip.add(Component.translatable("grandline.tooltip.ranged").withStyle(ChatFormatting.DARK_GRAY));
    }

    private ChatFormatting gradeColor() {
        return switch (grade) {
            case "black_blade" -> ChatFormatting.DARK_PURPLE;
            case "supreme" -> ChatFormatting.GOLD;
            case "great" -> ChatFormatting.LIGHT_PURPLE;
            case "skillful" -> ChatFormatting.AQUA;
            case "graded", "fine" -> ChatFormatting.GREEN;
            default -> ChatFormatting.WHITE;
        };
    }
}
