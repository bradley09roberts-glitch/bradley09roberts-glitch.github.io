package com.grandline.player;

import com.grandline.core.GLConfig;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Agreed permadeath model: lethal damage first knocks a character down. Allies can revive; Marines arrest;
 * bandits and pirates rob and leave; only lethal threats (beasts, Sea Kings, bosses that kill) or the
 * environment finish a downed character. Bleeding out at the end of the timer is a true death unless the
 * character's willpower lets them stand back up (once per in-game day).
 */
public final class Knockdown {
    public static boolean canBeKnockedDown(ServerPlayer sp, DamageSource src) {
        if (src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL)) return false;
        if (src.is(DamageTypeTags.IS_DROWNING) || src.is(DamageTypes.LAVA)) return false;
        return true;
    }

    public static boolean isLethalAttacker(Entity attacker) {
        if (attacker instanceof GLNpc npc) return npc.killsDowned();
        if (attacker instanceof Player p) {
            // A player can finish a downed player only if PvP permits it.
            return com.grandline.combat.Pvp.allowed(p, null);
        }
        return attacker instanceof Mob; // vanilla monsters / beasts are lethal
    }

    public static void knockDown(ServerPlayer sp, DamageSource src) {
        CharacterData d = Characters.get(sp);
        d.knockdownTicks = GLConfig.KNOCKDOWN_SECONDS.get() * 20;
        sp.stopRiding(); // a downed sailor drops from the helm or cannon
        d.knockedBy = src.getEntity() == null ? null : src.getEntity().getUUID();
        sp.setHealth(1.0f);
        sp.removeAllEffects();
        sp.setSprinting(false);
        sp.stopUsingItem();
        sp.sendSystemMessage(Component.translatable("grandline.knockdown.you", GLConfig.KNOCKDOWN_SECONDS.get()).withStyle(ChatFormatting.RED));
        GLNetwork.anim(sp, "knocked_down");
        CharacterManager.markVisualsDirty(sp);
        // last: an outlaw may rob and release the player at once (standUp plays get_up after knocked_down)
        if (src.getEntity() instanceof GLNpc npc) npc.onKnockedDownPlayer(sp);
    }

    static void tick(ServerPlayer sp, CharacterData d) {
        d.knockdownTicks--;
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 5, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 10, false, false));
        sp.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        var jump = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        if (jump != null) jump.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                com.grandline.GrandLine.id("knockdown"), -1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (sp.getHealth() > 1f) sp.setHealth(1f);
        if (d.knockdownTicks % 20 == 0) {
            sp.displayClientMessage(Component.translatable("grandline.knockdown.timer", d.knockdownTicks / 20).withStyle(ChatFormatting.RED), true);
            CharacterManager.markDirty(sp);
        }
        if (d.knockdownTicks <= 0) {
            long day = sp.level().getDayTime() / 24000L;
            String flag = "will_to_live_day_" + day;
            double chance = 0.25 + d.stat(Stat.WILLPOWER) * 0.02 + (d.conquerorAwakened ? 0.25 : 0);
            if (!d.flags.contains(flag) && sp.getRandom().nextDouble() < chance) {
                d.flags.removeIf(f -> f.startsWith("will_to_live_day_"));
                d.flags.add(flag);
                standUp(sp, d, 0.2f);
                sp.sendSystemMessage(Component.translatable("grandline.knockdown.will").withStyle(ChatFormatting.GOLD));
                return;
            }
            d.knockdownTicks = 0;
            sp.hurt(sp.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    public static void revive(ServerPlayer downed, CharacterData d, Player helper) {
        standUp(downed, d, 0.35f);
        downed.sendSystemMessage(Component.translatable("grandline.knockdown.revived", helper.getDisplayName()).withStyle(ChatFormatting.GREEN));
    }

    /** Released after being robbed or arrested. */
    public static void standUp(ServerPlayer sp, CharacterData d, float healthFraction) {
        d.knockdownTicks = 0;
        d.knockedBy = null;
        sp.setHealth(Math.max(2f, sp.getMaxHealth() * healthFraction));
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.WEAKNESS);
        sp.setForcedPose(null);
        var jump = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(com.grandline.GrandLine.id("knockdown"));
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2));
        GLNetwork.anim(sp, "get_up");
        CharacterManager.markVisualsDirty(sp);
    }

    private Knockdown() {}
}
