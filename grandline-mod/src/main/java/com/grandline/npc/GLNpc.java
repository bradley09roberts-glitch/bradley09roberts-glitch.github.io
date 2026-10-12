package com.grandline.npc;

import com.grandline.combat.CombatState;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.player.Knockdown;
import com.grandline.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Base class for every intelligent NPC (humanoids, beasts, Sea Kings). Server-side state + synced visuals. */
public abstract class GLNpc extends PathfinderMob {
    protected static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(GLNpc.class, EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(GLNpc.class, EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> FRUIT = SynchedEntityData.defineId(GLNpc.class, EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> ZOAN = SynchedEntityData.defineId(GLNpc.class, EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<String> RACE = SynchedEntityData.defineId(GLNpc.class, EntityDataSerializers.STRING);
    protected static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(GLNpc.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(GLNpc.class, EntityDataSerializers.BYTE);
    public static final byte FLAG_ARMAMENT = 1, FLAG_SURRENDER = 2, FLAG_NOTABLE = 4, FLAG_ARRESTED = 8;

    protected String styleId = "brawler";
    protected Faction faction = Faction.CIVILIAN;
    protected float stamina = 100;
    protected BlockPos home = BlockPos.ZERO;
    protected String islandId = "";
    String markerKey = "";
    protected String notableId = "";
    protected int hakiObs, hakiArm, hakiCoq;

    protected GLNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ROLE, "villager");
        b.define(SKIN, "villager_1");
        b.define(FRUIT, "");
        b.define(ZOAN, "");
        b.define(RACE, "human");
        b.define(TIER, 1);
        b.define(FLAGS, (byte) 0);
    }

    // ------------------------------------------------------------------ accessors

    public String role() { return entityData.get(ROLE); }
    public String skin() { return entityData.get(SKIN); }
    public int tier() { return entityData.get(TIER); }
    public Faction faction() { return faction; }
    public String styleId() { return styleId; }
    public String raceId() { return entityData.get(RACE); }
    @Nullable public String fruitId() { String f = entityData.get(FRUIT); return f.isEmpty() ? null : f; }
    public String zoanForm() { return entityData.get(ZOAN); }
    public void setZoanForm(String f) { entityData.set(ZOAN, f); }
    public int hakiObservation() { return hakiObs; }
    public int hakiArmament() { return hakiArm; }
    public int hakiConqueror() { return hakiCoq; }
    public BlockPos home() { return home; }
    public String notableId() { return notableId; }
    public String islandId() { return islandId; }
    public boolean flag(byte f) { return (entityData.get(FLAGS) & f) != 0; }
    public void setFlag(byte f, boolean on) { entityData.set(FLAGS, (byte) (on ? entityData.get(FLAGS) | f : entityData.get(FLAGS) & ~f)); }

    public float powerScale() {
        return 1f + (tier() - 1) * 0.22f;
    }

    public boolean usesArmament() {
        return hakiArm > 0 && getTarget() != null;
    }

    public boolean usesObservation() {
        return hakiObs > 0 && getTarget() != null;
    }

    public boolean spendStamina(float amount) {
        if (stamina < amount) return false;
        stamina -= amount;
        return true;
    }

    public Archetype archetype() {
        return Archetypes.get(role());
    }

    /** Whether the NPC kills downed players (beasts, ruthless bosses) rather than robbing/arresting. */
    public boolean killsDowned() {
        Boolean story = com.grandline.npc.notable.NotableHooks.killsDowned(this);
        return story != null ? story : archetype().killsDowned();
    }

    // ------------------------------------------------------------------ relations

    /** Attitude toward an entity, -100..100. Players: faction relation + personal reputation + bounty. */
    public int attitudeTo(LivingEntity e) {
        if (e instanceof Player p) {
            CharacterData d = Characters.get(p);
            int base = Faction.baseAttitude(faction, d.faction);
            int rep = d.reputation(faction.id()) / 5 + (islandId.isEmpty() ? 0 : d.reputation("island/" + islandId) / 5);
            int bountyHeat = Faction.isLaw(faction) && d.bounty > 0 ? -(int) Math.min(120, 20 + Math.log10(d.bounty + 1) * 15) : 0;
            return base + rep + bountyHeat;
        }
        if (e instanceof GLNpc n) {
            if (n.faction == faction) return 100;
            return Faction.baseAttitude(faction, n.faction);
        }
        if (e instanceof net.minecraft.world.entity.monster.Enemy) return -100;
        return 0;
    }

    public boolean isHostileTo(LivingEntity e) {
        if (e == this) return false;
        if (getLastHurtByMob() == e || getTarget() == e) return true;
        Boolean story = com.grandline.npc.notable.NotableHooks.hostility(this, e); // story cast & crews
        if (story != null) return story;
        Archetype.Hostility h = archetype().hostility();
        int att = attitudeTo(e);
        return switch (h) {
            case PASSIVE -> false;
            case DEFENSIVE -> att < -90;
            case LAWFUL -> att < -40;
            case AGGRESSIVE -> att < 30;
        };
    }

    // ------------------------------------------------------------------ outcomes

    /** A player was knocked down by this NPC: Marines arrest, outlaws rob and leave, beasts finish. */
    public void onKnockedDownPlayer(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (killsDowned()) return;
        setTarget(null);
        if (Faction.isLaw(faction)) {
            NpcOutcomes.arrestPlayer(this, sp, d);
        } else if (faction == Faction.WILD || faction == Faction.PIRATE) {
            long taken = d.beli * 3 / 10;
            d.beli -= taken;
            sp.sendSystemMessage(Component.translatable("grandline.knockdown.robbed", getDisplayName(), taken).withStyle(ChatFormatting.GOLD));
            Knockdown.standUp(sp, d, 0.15f);
            d.flags.add("survived_knockdown");
        }
    }

    public void onConquerorKnockout(LivingEntity by) {
        setTarget(null);
        getNavigation().stop();
    }

    public void arrestedBy(Player p) {
        setFlag(FLAG_ARRESTED, true);
        setTarget(null);
        if (p instanceof ServerPlayer sp) NpcOutcomes.npcArrested(this, sp);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (stamina < 100 + tier() * 10) stamina += 0.5f;
        com.grandline.fruit.FruitRules.tick(this);
        if (hasEffect(ModEffects.KNOCKED_OUT)) {
            getNavigation().stop();
            setTarget(null);
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("Role", role());
        t.putString("Skin", skin());
        t.putString("Fruit", entityData.get(FRUIT));
        t.putString("Race", raceId());
        t.putInt("Tier", tier());
        t.putString("Style", styleId);
        t.putString("Faction", faction.id());
        t.putLong("Home", home.asLong());
        t.putString("Island", islandId);
        t.putString("Marker", markerKey);
        t.putString("Notable", notableId);
        t.putInt("HakiObs", hakiObs);
        t.putInt("HakiArm", hakiArm);
        t.putInt("HakiCoq", hakiCoq);
        t.putByte("Flags", entityData.get(FLAGS));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(ROLE, t.getString("Role"));
        entityData.set(SKIN, t.getString("Skin"));
        entityData.set(FRUIT, t.getString("Fruit"));
        entityData.set(RACE, t.contains("Race") ? t.getString("Race") : "human");
        entityData.set(TIER, Math.max(1, t.getInt("Tier")));
        entityData.set(FLAGS, t.getByte("Flags"));
        styleId = t.getString("Style");
        faction = Faction.byId(t.getString("Faction"));
        home = BlockPos.of(t.getLong("Home"));
        islandId = t.getString("Island");
        markerKey = t.getString("Marker");
        notableId = t.getString("Notable");
        hakiObs = t.getInt("HakiObs");
        hakiArm = t.getInt("HakiArm");
        hakiCoq = t.getInt("HakiCoq");
    }

    @Nullable
    public DevilFruit fruit() {
        String f = fruitId();
        return f == null ? null : DevilFruits.get(f);
    }

    /** Testing/story hook: give this NPC a specific fruit without the world ledger. */
    public void forceFruitForTest(String fruit) {
        entityData.set(FRUIT, fruit);
    }

    public CombatState combat() {
        return CombatState.of(this);
    }

    // ------------------------------------------------------------------ story cast & crews

    /** Turn this NPC into a named story character (see {@link com.grandline.npc.notable.NotableCharacters}). */
    public void makeNotable(String id, @Nullable String fruit, int obs, int arm, int coq, String style) {
        notableId = id;
        entityData.set(FRUIT, fruit == null ? "" : fruit);
        hakiObs = obs;
        hakiArm = arm;
        hakiCoq = coq;
        if (style != null && !style.isEmpty()) styleId = style;
        setFlag(FLAG_NOTABLE, true);
        setPersistenceRequired();
    }

    /** Explicit tier and race for a notable (archetype defaults are only a starting point). */
    public void setNotableBody(int tier, String race) {
        entityData.set(TIER, Math.max(1, tier));
        entityData.set(RACE, race);
    }

    /** Change allegiance and home: joining a crew (home ZERO = no leash), waiting somewhere, going back home. */
    public void setAllegiance(Faction f, BlockPos newHome) {
        faction = f;
        home = newHome;
    }

    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        return super.isAlliedTo(other) || com.grandline.npc.crew.CrewManager.allied(this, other);
    }
}
