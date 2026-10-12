package com.grandline.npc;

import com.grandline.combat.Ability;
import com.grandline.combat.Abilities;
import com.grandline.combat.CombatState;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.fruit.FruitWorldData;
import com.grandline.player.Faction;
import com.grandline.player.Race;
import com.grandline.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Humanoid NPC (civilians, Marines, pirates, fish-men, giants...). Uses the same combat system as players. */
public class HumanoidNpc extends GLNpc {
    public HumanoidNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_SPEED, 4);
    }

    /** Re-race a configured NPC (Sky People on the White Sea, Fish-Men and Merfolk in the deep sea). */
    public void setRace(Race race) {
        entityData.set(RACE, race.id());
        float scale = race == Race.GIANT ? 2.2f : race.baseScale;
        getAttribute(Attributes.SCALE).setBaseValue(scale);
        refreshDimensions();
    }

    /** Initialise from an archetype. Tier is the island/encounter difficulty. */
    public void configure(String role, int tier, String islandId, BlockPos home, String markerKey, RandomSource r) {
        Archetype a = Archetypes.get(role);
        entityData.set(ROLE, a.id());
        int t = Math.max(1, Math.max(a.baseTier(), tier + (a.baseTier() > 3 ? a.baseTier() - 3 : 0)));
        if (a.faction() == Faction.CIVILIAN && a.hostility() == Archetype.Hostility.PASSIVE) t = 1;
        entityData.set(TIER, t);
        entityData.set(SKIN, a.skins().get(r.nextInt(a.skins().size())));
        entityData.set(RACE, a.race().id());
        this.faction = a.faction();
        this.styleId = a.style();
        this.islandId = islandId;
        this.home = home;
        this.markerKey = markerKey;
        if (!markerKey.isEmpty()) setPersistenceRequired();
        if (r.nextDouble() < a.hakiChance()) {
            hakiObs = 5 + t * 8 + r.nextInt(10);
            hakiArm = 5 + t * 8 + r.nextInt(10);
            if (t >= 9 && r.nextDouble() < 0.3) hakiCoq = 20 + r.nextInt(30);
        }
        if (r.nextDouble() < a.fruitChance() && level() instanceof ServerLevel sl) {
            String fruit = FruitWorldData.get(sl.getServer()).takeFromPool(r, 1, Math.min(10, t + 2));
            if (fruit != null) {
                entityData.set(FRUIT, fruit);
                FruitWorldData.get(sl.getServer()).eaten(fruit, getUUID(), "NPC " + a.id());
            }
        }
        float hp = a.health() * (1 + (t - 1) * 0.35f);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2 + t * 1.2);
        getAttribute(Attributes.ARMOR).setBaseValue(a.armor() + t);
        float scale = a.race() == Race.GIANT ? (role.startsWith("giant") ? 4.5f : 2.2f) : a.race() == Race.FISHMAN ? 1.15f : 1f;
        if (a.race() == Race.GIANT || a.race() == Race.FISHMAN) getAttribute(Attributes.SCALE).setBaseValue(scale);
        setHealth(getMaxHealth());
        if (!a.weapons().isEmpty()) {
            String w = a.weapons().get(r.nextInt(a.weapons().size()));
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("grandline", w));
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
            setDropChance(EquipmentSlot.MAINHAND, t >= 8 ? 0.3f : 0.05f);
        }
        setCustomName(NpcNames.nameFor(a, r));
        setCustomNameVisible(false);
        refreshDimensions();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new NpcSurrenderGoal(this));
        goalSelector.addGoal(2, new NpcFleeGoal(this));
        goalSelector.addGoal(3, new NpcCombatGoal(this));
        goalSelector.addGoal(4, new com.grandline.npc.crew.CrewFollowGoal(this));
        goalSelector.addGoal(5, new NpcHomeGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(HumanoidNpc.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, e -> isHostileTo(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, HumanoidNpc.class, 10, true, false, e -> isHostileTo(e) && archetype().hostility() != Archetype.Hostility.PASSIVE));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, e -> archetype().hostility() != Archetype.Hostility.PASSIVE));
    }

    /** Techniques this NPC can use (style + fruit + haki), scaled by tier as "mastery". */
    public List<Ability> techniques() {
        List<Ability> out = new ArrayList<>();
        int mastery = Math.min(100, tier() * 10);
        FightingStyle st = FightingStyles.get(styleId);
        if (st != null) for (Ability a : st.abilities) if (a.unlockMastery() <= mastery) out.add(a);
        DevilFruit f = fruit();
        if (f != null) for (Ability a : f.abilities()) if (a.unlockMastery() <= mastery && (!a.awakened() || tier() >= 9)) out.add(a);
        var wc = com.grandline.combat.CombatManager.weaponOf(getMainHandItem());
        if (wc != null) for (Ability a : Abilities.of(Ability.Source.WEAPON, wc.id())) if (a.unlockMastery() <= mastery) out.add(a);
        if (hakiArm > 40) {
            Ability em = Abilities.get("haki/armament/emission");
            if (em != null) out.add(em);
        }
        if (hakiCoq > 0) {
            Ability b = Abilities.get("haki/conqueror/burst");
            if (b != null) out.add(b);
        }
        return out;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(ModItems.SEASTONE_CUFFS.get())) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (getTarget() == player) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) com.grandline.dialogue.Dialogues.open(sp, this);
        return InteractionResult.CONSUME;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource src) {
        super.die(src);
        if (level() instanceof ServerLevel sl) NpcOutcomes.onNpcDeath(this, src, sl);
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return markerKey.isEmpty() && notableId.isEmpty();
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("role.grandline." + role());
    }
}
