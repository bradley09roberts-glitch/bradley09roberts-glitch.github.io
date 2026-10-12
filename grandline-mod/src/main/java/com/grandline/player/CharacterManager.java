package com.grandline.player;

import com.grandline.GrandLine;
import com.grandline.core.GLConfig;
import com.grandline.net.GLNetwork;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.WeakHashMap;

/** Server-side character lifecycle: creation, attribute application, synchronisation. */
public final class CharacterManager {
    private static final ResourceLocation RACE_MOD = GrandLine.id("race");
    private static final ResourceLocation STAT_MOD = GrandLine.id("stats");
    private static final Set<ServerPlayer> DIRTY = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerPlayer> VISUALS_DIRTY = java.util.Collections.newSetFromMap(new WeakHashMap<>());

    /** Mark the owner's data for sync at end of tick. */
    public static void markDirty(ServerPlayer player) {
        DIRTY.add(player);
    }

    /** Mark data that other players render (race, appearance, forms) for broadcast. */
    public static void markVisualsDirty(ServerPlayer player) {
        DIRTY.add(player);
        VISUALS_DIRTY.add(player);
    }

    public static void flush() {
        if (!DIRTY.isEmpty()) {
            for (ServerPlayer p : DIRTY.toArray(new ServerPlayer[0])) {
                if (!p.isRemoved()) syncToOwner(p);
            }
            DIRTY.clear();
        }
        if (!VISUALS_DIRTY.isEmpty()) {
            for (ServerPlayer p : VISUALS_DIRTY.toArray(new ServerPlayer[0])) {
                if (!p.isRemoved()) broadcastVisuals(p);
            }
            VISUALS_DIRTY.clear();
        }
    }

    public static void syncToOwner(ServerPlayer player) {
        GLNetwork.toPlayer(player, new GLNetwork.SyncCharacter(Characters.get(player).serializeNBT(player.registryAccess())));
    }

    public static CompoundTag visuals(Player player) {
        CharacterData d = Characters.get(player);
        CompoundTag t = new CompoundTag();
        t.putBoolean("created", d.created);
        t.putString("race", d.race.id());
        t.put("appearance", d.appearance.save());
        t.putString("fruit", d.fruit == null ? "" : d.fruit);
        t.putString("zoanForm", d.zoanForm);
        t.putBoolean("knocked", d.isKnockedDown());
        t.putString("faction", d.faction.id());
        t.putLong("bounty", d.bounty);
        t.putString("name", d.name);
        return t;
    }

    public static void broadcastVisuals(ServerPlayer player) {
        GLNetwork.toTrackingAndSelf(player, new GLNetwork.EntityVisuals(player.getId(), visuals(player)));
    }

    public static void sendVisualsTo(ServerPlayer viewer, Player target) {
        GLNetwork.toPlayer(viewer, new GLNetwork.EntityVisuals(target.getId(), visuals(target)));
    }

    // ------------------------------------------------------------------ attributes

    public static void applyAttributes(Player player) {
        CharacterData d = Characters.get(player);
        Race race = d.created ? d.race : Race.HUMAN;
        float scale = d.created ? d.appearance.scale : 1f;

        set(player, Attributes.SCALE, RACE_MOD, scale - 1.0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MAX_HEALTH, RACE_MOD, race.healthBonus, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, RACE_MOD, race.speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        double reach = race.reachBonus + (scale - 1.0) * 1.5;
        set(player, Attributes.ENTITY_INTERACTION_RANGE, RACE_MOD, reach, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_INTERACTION_RANGE, RACE_MOD, reach, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.STEP_HEIGHT, RACE_MOD, race.stepBonus + Math.max(0, scale - 1.0) * 0.6, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.JUMP_STRENGTH, RACE_MOD, race.jumpBonus * 0.42 + (scale - 1.0) * 0.12, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.SAFE_FALL_DISTANCE, RACE_MOD, (scale - 1.0) * 3.0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR, RACE_MOD, race.armorBonus, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.WATER_MOVEMENT_EFFICIENCY, RACE_MOD, race.waterMovement, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.OXYGEN_BONUS, RACE_MOD, race.oxygenBonus, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.KNOCKBACK_RESISTANCE, RACE_MOD, race.knockbackResistance, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, RACE_MOD, race.damageMultiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // stats
        int str = d.stat(Stat.STRENGTH), end = d.stat(Stat.ENDURANCE), agi = d.stat(Stat.AGILITY);
        set(player, Attributes.MAX_HEALTH, STAT_MOD, end + (d.level / 5) * 2, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, STAT_MOD, str * 0.25, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, STAT_MOD, agi * 0.004, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_SPEED, STAT_MOD, agi * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        player.refreshDimensions();
    }

    private static void set(Player player, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        if (Math.abs(amount) < 1e-6) {
            inst.removeModifier(id);
        } else {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    // ------------------------------------------------------------------ creation

    /** Validate and apply a creation request from the client. Returns an error message or null. */
    public static Component create(ServerPlayer player, CompoundTag req) {
        CharacterData d = Characters.get(player);
        if (d.created) return Component.translatable("grandline.create.already");
        Race race = Race.byId(req.getString("race"));
        if (!race.playable) return Component.translatable("grandline.create.bad_race");
        Faction faction = Faction.byId(req.getString("faction"));
        if (!faction.startingPath) faction = Faction.CIVILIAN;
        Blue origin = Blue.byId(req.getString("origin"));
        String name = req.getString("name").trim();
        if (name.isEmpty()) name = player.getGameProfile().getName();
        if (name.length() > 24) name = name.substring(0, 24);
        name = name.replaceAll("[^\\p{L}\\p{N} .'\\-]", "");

        Appearance app = Appearance.load(req.getCompound("appearance"));
        app.sanitize(race);

        d.created = true;
        d.name = name;
        d.race = race;
        d.appearance = app;
        d.faction = faction;
        d.origin = origin;
        d.level = 1;
        d.xp = 0;
        d.statPoints = 3;
        d.beli = faction == Faction.MARINE ? 800 : 500;
        d.conquerorTrait = player.getRandom().nextDouble() < GLConfig.CONQUEROR_CHANCE.get();
        d.stamina = d.maxStamina();
        d.hakiGauge = d.maxHakiGauge();
        d.knownStyles.clear();
        d.knownStyles.add("brawler");
        if (race == Race.MINK) d.knownStyles.add("electro");
        if (race == Race.FISHMAN) d.knownStyles.add("fishman_karate");
        if (race == Race.SKY_PEOPLE || race == Race.THREE_EYE) d.observationUnlocked = false; // affinity handled by training speed
        d.activeStyle = d.knownStyles.contains("fishman_karate") ? "fishman_karate" : (d.knownStyles.contains("electro") ? "electro" : "brawler");
        // starting reputation
        d.addReputation(Faction.MARINE.id(), faction == Faction.MARINE ? 100 : 0);
        d.addReputation(Faction.REVOLUTIONARY.id(), faction == Faction.REVOLUTIONARY ? 100 : 0);
        d.addReputation(Faction.PIRATE.id(), faction == Faction.PIRATE ? 50 : 0);
        d.addReputation(Faction.CIVILIAN.id(), 20);
        com.grandline.combat.Loadouts.resetDefaults(player, d);

        applyAttributes(player);
        player.setHealth(player.getMaxHealth());
        markVisualsDirty(player);
        StartLocations.sendToStart(player, d);
        Handbook.give(player);
        GrandLine.LOGGER.info("Character created for {}: {} the {} ({} from {} Blue), conqueror={}", player.getGameProfile().getName(), name, race.id(), faction.id(), origin.id(), d.conquerorTrait);
        return null;
    }

    public static void openCreationScreen(ServerPlayer player) {
        CompoundTag t = new CompoundTag();
        CharacterData d = Characters.get(player);
        t.putInt("generation", d.generation);
        GLNetwork.toPlayer(player, new GLNetwork.OpenScreen("create_character", t));
    }

    public static float clampScale(Race race, float s) {
        return Mth.clamp(s, race.minScale, race.maxScale);
    }

    private CharacterManager() {}
}
