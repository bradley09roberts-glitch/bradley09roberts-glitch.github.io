package com.longnight.entity;

import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.game.Crime;
import com.longnight.game.Ui;
import com.longnight.sim.Person;
import com.longnight.sim.Profession;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

/**
 * The body of a Person while a player is near. It is never written to chunk data: bodies are
 * created from the simulation and discarded when players leave, so a person exists exactly once
 * and the dead can never return through a save/reload.
 */
public class CitizenEntity extends PathfinderMob {
    static final EntityDataAccessor<String> BODY = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<String> CLOTHES = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<String> HAIR = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Integer> CLOTH_COLOR = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> HAIR_COLOR = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> HOSTILE = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<String> ARMOR = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);

    public UUID personId;
    public UUID settlementId;
    /** for raiders: the settlement being attacked */
    public UUID raidTarget;
    public final CitizenBrain brain = new CitizenBrain();
    private boolean fighter;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation g) {
            g.setCanOpenDoors(true);
            g.setCanPassDoors(true);
        }
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(BODY, "human_2");
        b.define(CLOTHES, "tunic");
        b.define(HAIR, "short");
        b.define(CLOTH_COLOR, 0x7a5c3a);
        b.define(HAIR_COLOR, 0x5a3a22);
        b.define(SCALE, 1f);
        b.define(HOSTILE, false);
        b.define(ARMOR, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CitizenGoals.FleeGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.9, true) {
            @Override
            public boolean canUse() {
                return fighter && super.canUse();
            }
        });
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new CitizenGoals.BrainGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return fighter && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> fighter && isEnemy(e)));
    }

    /** Who this body treats as an enemy: guards fight monsters and raiders; raiders fight townsfolk and players. */
    boolean isEnemy(LivingEntity e) {
        if (isHostile()) {
            if (e instanceof Player pl) return !pl.isCreative() && !pl.isSpectator() && distanceTo(pl) < 16;
            return e instanceof CitizenEntity c && !c.isHostile();
        }
        if (e instanceof CitizenEntity c) return c.isHostile();
        if (e instanceof Monster m) return !(m instanceof net.minecraft.world.entity.monster.EnderMan) && !(m instanceof net.minecraft.world.entity.monster.Creeper);
        if (e instanceof Player pl && personId != null) return Crime.isWanted(this, pl);
        return false;
    }

    // ------------------------------------------------------------------ setup from the simulation

    public void bind(Person p, Settlement s, long day, WorldSim sim) {
        personId = p.id;
        settlementId = s == null ? null : s.id;
        Culture c = Content.culture(p.culture);
        Random r = new Random(p.id.getMostSignificantBits());
        entityData.set(BODY, c.bodies.isEmpty() ? "human_2" : c.bodies.get(Math.floorMod(r.nextInt(), c.bodies.size())));
        String hair = c.hairStyles.isEmpty() ? "short" : c.hairStyles.get(Math.floorMod(r.nextInt(), c.hairStyles.size()));
        if (p.sex == Person.Sex.FEMALE && hair.startsWith("beard") && !c.people.equals("dwarf")) hair = "long";
        if (p.sex == Person.Sex.FEMALE && hair.equals("bald")) hair = "bun";
        entityData.set(HAIR, hair);
        int hc = c.hairColors.isEmpty() ? 0x5a3a22 : c.hairColors.get(Math.floorMod(r.nextInt(), c.hairColors.size()));
        double age = sim.ageOf(p, day);
        if (age > c.lifespan * 0.7) hc = 0xc8c8c8; // grey with age
        entityData.set(HAIR_COLOR, hc);
        entityData.set(CLOTHES, clothesFor(p, r));
        int cc = c.clothColors.isEmpty() ? 0x7a5c3a : c.clothColors.get(Math.floorMod(r.nextInt(), c.clothColors.size()));
        if (p.profession == Profession.RULER) cc = 0x6b2a8a;
        if (p.profession == Profession.PRIEST) cc = 0xd8d0b0;
        entityData.set(CLOTH_COLOR, cc);
        float scale = c.scale;
        if (!sim.isAdult(p, day)) scale *= (float) Math.max(0.45, Math.min(1.0, 0.45 + 0.55 * age / sim.rules.adultAge));
        entityData.set(SCALE, scale);
        refreshDimensions();
        boolean hostile = p.profession == Profession.BANDIT;
        entityData.set(HOSTILE, hostile);
        fighter = hostile || p.profession == Profession.GUARD || p.profession == Profession.SOLDIER;
        entityData.set(ARMOR, p.armorTier >= 2 ? "plate" : p.armorTier == 1 && fighter ? "mail" : "");
        // hoods and helmets sit in the hat layer: only some hairstyles fit under them
        if (entityData.get(CLOTHES).equals("traveller") || !entityData.get(ARMOR).isEmpty()) {
            String h = entityData.get(HAIR);
            if (!h.equals("bald") && !h.equals("braided") && !h.startsWith("beard") && !h.equals("hood_short")) {
                entityData.set(HAIR, p.sex == Person.Sex.MALE && c.people.equals("dwarf") ? "beard_short" : "hood_short");
            }
        }
        setCustomName(Component.literal(p.fullName()));
        setCustomNameVisible(false);
        equip(p, sim.isAdult(p, day));
        double health = 14 + p.bravery * 6 + (fighter ? 6 : 0);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        setHealth((float) health * Math.max(0.3f, p.health));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2 + p.weaponTier * 2 + (fighter ? 1 : 0));
        getAttribute(Attributes.ARMOR).setBaseValue(p.armorTier * 4);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(c.people.equals("dwarf") || c.people.equals("smallfolk") ? 0.45 : 0.5);
    }

    static String clothesFor(Person p, Random r) {
        return switch (p.profession) {
            case FARMER, SHEPHERD, MILLER, LUMBERJACK -> "smock";
            case BLACKSMITH, CARPENTER, BAKER, BREWER, BUILDER, QUARRIER, MINER -> "apron";
            case PRIEST, SCHOLAR, TEACHER, HEALER -> "robe";
            case GUARD, SOLDIER -> "gambeson";
            case RULER, STEWARD -> "noble";
            case MERCHANT, INNKEEPER -> p.sex == Person.Sex.FEMALE ? "dress" : "tunic";
            case BANDIT -> r.nextBoolean() ? "rags" : "traveller";
            case HUNTER, FISHER -> "traveller";
            default -> {
                if (p.culture.equals("ashborn") || p.culture.equals("dunmar")) yield "fur";
                yield p.sex == Person.Sex.FEMALE && r.nextBoolean() ? "dress" : "tunic";
            }
        };
    }

    void equip(Person p, boolean adult) {
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
        Item main = adult ? switch (p.profession) {
            case FARMER -> Items.IRON_HOE;
            case LUMBERJACK -> Items.IRON_AXE;
            case MINER -> Items.IRON_PICKAXE;
            case QUARRIER -> Items.STONE_PICKAXE;
            case BLACKSMITH -> Items.IRON_INGOT;
            case FISHER -> Items.FISHING_ROD;
            case HUNTER -> Items.BOW;
            case SHEPHERD -> Items.SHEARS;
            case MILLER -> Items.WHEAT;
            case BAKER -> Items.BREAD;
            case CARPENTER -> Items.WOODEN_AXE;
            case WEAVER -> Items.WHITE_WOOL;
            case BUILDER -> Items.OAK_PLANKS;
            case GRAVEDIGGER -> Items.IRON_SHOVEL;
            case SCHOLAR, TEACHER, PRIEST -> Items.BOOK;
            case HEALER -> Items.GLASS_BOTTLE;
            case GUARD, SOLDIER -> p.weaponTier > 0 ? Items.IRON_SWORD : Items.STONE_SWORD;
            case BANDIT -> p.weaponTier > 1 ? Items.IRON_AXE : Items.STONE_AXE;
            default -> Items.AIR;
        } : Items.AIR;
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(main));
        if (adult && p.profession == Profession.GUARD && p.armorTier > 0) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }

    // ------------------------------------------------------------------ accessors for the renderer

    public String bodyTexture() {
        return entityData.get(BODY);
    }

    public String clothesTexture() {
        return entityData.get(CLOTHES);
    }

    public String hairTexture() {
        return entityData.get(HAIR);
    }

    public int clothColor() {
        return entityData.get(CLOTH_COLOR);
    }

    public int hairColor() {
        return entityData.get(HAIR_COLOR);
    }

    public float bodyScale() {
        return entityData.get(SCALE);
    }

    public boolean isHostile() {
        return entityData.get(HOSTILE);
    }

    public String armorTexture() {
        return entityData.get(ARMOR);
    }

    public boolean isFighter() {
        return fighter;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale(bodyScale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SCALE.equals(key)) refreshDimensions();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public boolean shouldBeSaved() {
        return false; // bodies are rebuilt from the simulation, never from chunk data
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 100 == 0 && level() instanceof ServerLevel sl) {
            WorldSim sim = WorldSim.get(sl.getServer());
            Person p = sim.person(personId);
            if (p == null || !p.alive) {
                discard();
                return;
            }
            // heal slowly and mirror health back to the person
            if (getHealth() < getMaxHealth() && !isHostile()) heal(0.5f);
            p.health = Math.max(p.health, getHealth() / getMaxHealth() * 0.5f + 0.5f * p.health);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level() instanceof ServerLevel sl && player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
            WorldSim sim = WorldSim.get(sl.getServer());
            Person p = sim.person(personId);
            if (p != null && p.alive) {
                getNavigation().stop();
                getLookControl().setLookAt(player);
                brain.pause(60);
                Ui.citizen(sp, sim, p);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && level() instanceof ServerLevel sl && source.getEntity() instanceof ServerPlayer sp && !isHostile()) {
            Crime.assault(sl, sp, this);
        }
        if (r && isSleeping()) stopSleeping();
        return r;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl && personId != null) {
            WorldSim sim = WorldSim.get(sl.getServer());
            Person p = sim.person(personId);
            if (p != null && p.alive) {
                Entity killer = source.getEntity();
                String cause;
                if (killer instanceof ServerPlayer sp) {
                    cause = "slain by " + sp.getGameProfile().getName();
                    if (!isHostile()) Crime.murder(sl, sp, this, p);
                    else Crime.slewOutlaw(sl, sp, this, p);
                } else if (killer instanceof CitizenEntity c && c.isHostile()) {
                    cause = "slain by outlaws";
                } else if (killer instanceof CitizenEntity) {
                    cause = "cut down by the watch";
                } else if (killer instanceof Enemy && killer != null) {
                    cause = "killed by a " + BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).getPath().replace('_', ' ');
                } else {
                    cause = source.getMsgId().replace('.', ' ');
                }
                sim.kill(p, WorldSim.dayOf(sl.getServer().overworld()), cause);
            }
        }
        super.die(source);
    }

    public static ResourceLocation tex(String kind, String id) {
        return ResourceLocation.fromNamespaceAndPath("longnight", "textures/entity/citizen/" + kind + "/" + id + ".png");
    }
}
