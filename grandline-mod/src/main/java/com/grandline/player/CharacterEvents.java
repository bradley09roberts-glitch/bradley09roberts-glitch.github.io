package com.grandline.player;

import com.grandline.GrandLine;
import com.grandline.core.GLConfig;
import com.grandline.fruit.FruitWorldData;
import com.grandline.net.GLNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Character lifecycle events: login sync, knockdown instead of death, permadeath on true death,
 * graves, stamina/haki regeneration, revive interactions.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CharacterEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterManager.applyAttributes(sp);
        if (Characters.get(sp).created) com.grandline.combat.Loadouts.refresh(sp, Characters.get(sp)); // fills newer sets (race)
        CharacterManager.syncToOwner(sp);
        CharacterManager.broadcastVisuals(sp);
        if (!Characters.get(sp).created) CharacterManager.openCreationScreen(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterManager.applyAttributes(sp);
        CharacterManager.syncToOwner(sp);
        CharacterManager.broadcastVisuals(sp);
        if (!Characters.get(sp).created) CharacterManager.openCreationScreen(sp);
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterManager.applyAttributes(sp);
        CharacterManager.syncToOwner(sp);
        CharacterManager.broadcastVisuals(sp);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof Player target) {
            CharacterManager.sendVisualsTo(viewer, target);
        }
    }

    /** Clone: on true death the character is archived (permadeath); on End-portal return the data is kept. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CharacterData oldData = Characters.get(e.getOriginal());
        CharacterData newData = Characters.get(e.getEntity());
        newData.deserializeNBT(e.getEntity().registryAccess(), oldData.serializeNBT(e.getEntity().registryAccess()));
        if (e.isWasDeath()) {
            if (GLConfig.PERMADEATH.get()) {
                newData.archiveAndReset();
            } else {
                newData.knockdownTicks = 0;
                newData.knockedBy = null;
                newData.stamina = newData.maxStamina();
            }
        }
    }

    // ------------------------------------------------------------------ knockdown

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterData d = Characters.get(sp);
        if (!d.created) return;
        DamageSource src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill, void
        if (d.isKnockedDown()) {
            // Knocked-down characters only die to finishing blows from lethal enemies or the environment.
            Entity attacker = src.getEntity();
            if (attacker != null && !Knockdown.isLethalAttacker(attacker)) {
                e.setCanceled(true);
            }
            return;
        }
        // foes from the gentle seas (tier 1-3 NPCs) hit softer, so a new character can take on a bandit gang
        if (src.getEntity() instanceof com.grandline.npc.GLNpc npc && npc.tier() <= 3)
            e.setAmount(e.getAmount() * (npc.tier() <= 1 ? 0.45f : npc.tier() == 2 ? 0.65f : 0.85f));
        float health = sp.getHealth() + sp.getAbsorptionAmount();
        if (e.getAmount() >= health && Knockdown.canBeKnockedDown(sp, src)) {
            e.setCanceled(true);
            Knockdown.knockDown(sp, src);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterData d = Characters.get(sp);
        if (!d.created || !GLConfig.PERMADEATH.get()) return;
        ServerLevel level = sp.serverLevel();
        // Return the fruit to the world
        if (d.fruit != null) {
            FruitWorldData.get(level.getServer()).release(level.getServer(), d.fruit, sp.getUUID());
        }
        Component msg = Component.translatable("grandline.death.final", d.name, Component.translatable(d.race.translationKey()), d.level)
                .withStyle(ChatFormatting.DARK_RED);
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        GrandLine.LOGGER.info("Permadeath: {} ({}) died: {}", d.name, sp.getGameProfile().getName(), e.getSource().getMsgId());
    }

    /** Put a dead character's belongings into a grave chest at the death site. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (e.getDrops().isEmpty()) return;
        ServerLevel level = sp.serverLevel();
        BlockPos pos = Graves.findGraveSpot(level, sp.blockPosition());
        if (pos == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity ie : e.getDrops()) stacks.add(ie.getItem().copy());
        int placed = Graves.placeGrave(level, pos, stacks, Characters.get(sp).name);
        if (placed > 0) {
            var it = e.getDrops().iterator();
            for (int i = 0; i < placed && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
            sp.sendSystemMessage(Component.translatable("grandline.death.grave", pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterData d = Characters.get(sp);
        if (!d.created) {
            // uncreated characters are frozen in place until they finish creation
            if (sp.tickCount % 100 == 0) CharacterManager.openCreationScreen(sp);
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6, false, false));
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
            return;
        }
        if (d.isKnockedDown()) {
            Knockdown.tick(sp, d);
            return;
        }
        boolean changed = false;
        // stamina regen (faster when not sprinting / not in combat stance)
        float maxSt = d.maxStamina();
        if (d.stamina < maxSt) {
            float regen = sp.isSprinting() ? 0.15f : 0.6f;
            regen *= 1f + d.stat(Stat.ENDURANCE) * 0.02f;
            d.stamina = Math.min(maxSt, d.stamina + regen);
            changed = sp.tickCount % 10 == 0;
        }
        if (sp.isSprinting() && sp.tickCount % 4 == 0) d.stamina = Math.max(0, d.stamina - 0.3f);
        float maxHaki = d.maxHakiGauge();
        if (d.hakiGauge < maxHaki && !com.grandline.haki.Haki.anyActive(sp)) {
            d.hakiGauge = Math.min(maxHaki, d.hakiGauge + 0.35f + d.stat(Stat.WILLPOWER) * 0.01f);
            changed |= sp.tickCount % 10 == 0;
        }
        RaceTraits.tick(sp, d);
        if (changed) CharacterManager.markDirty(sp);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        CharacterManager.flush();
    }

    /** Sneak + right-click a knocked-down player to revive them. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer helper)) return;
        if (!(e.getTarget() instanceof ServerPlayer downed)) return;
        CharacterData dd = Characters.get(downed);
        if (!dd.isKnockedDown() || !helper.isShiftKeyDown()) return;
        Knockdown.revive(downed, dd, helper);
        e.setCanceled(true);
    }

    private CharacterEvents() {}
}
