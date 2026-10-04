package com.terracraft.client.renderer;

import com.terracraft.client.model.BatModel3D;
import com.terracraft.client.model.EyeModel;
import com.terracraft.client.model.SlimeBodyModel;
import com.terracraft.client.model.TerraHumanoidModel;
import com.terracraft.client.model.TerraModels;
import com.terracraft.config.TerraConfig;
import com.terracraft.registry.content.MobContent;
import com.terracraft.registry.content.NpcContent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.function.Function;

/**
 * Chooses how each TerraCraft creature is drawn: a 3D model (default) or, with the client option
 * {@code flatSprites}, the flat Terraria-style sprite. The choice is made whenever renderers are (re)built.
 */
public final class CreatureRenderers {
    private CreatureRenderers() {}

    /** Model scale = hitbox width / model width in blocks (slime model 14 px, eye model 10 px). */
    private static float slime(float width) {
        return width / (14.0F / 16.0F);
    }

    private static float eye(float width) {
        return width / (10.0F / 16.0F);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        slime(event, MobContent.GREEN_SLIME.get(), 0.9F);
        slime(event, MobContent.BLUE_SLIME.get(), 0.9F);
        slime(event, MobContent.RED_SLIME.get(), 0.95F);
        slime(event, MobContent.PURPLE_SLIME.get(), 1.1F);
        slime(event, MobContent.YELLOW_SLIME.get(), 1.0F);
        slime(event, MobContent.BLACK_SLIME.get(), 1.0F);
        slime(event, MobContent.BABY_SLIME.get(), 0.55F);
        register(event, MobContent.MOTHER_SLIME.get(), ctx -> new TerraModelRenderer<>(ctx,
            new SlimeBodyModel(ctx.bakeLayer(TerraModels.MOTHER_SLIME)), slime(1.3F), 0.6F, false));
        register(event, MobContent.KING_SLIME.get(), ctx -> new TerraModelRenderer<>(ctx,
            new SlimeBodyModel(ctx.bakeLayer(TerraModels.KING_SLIME)), slime(3.0F), 1.4F, false));
        register(event, MobContent.ZOMBIE.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, true));
        register(event, MobContent.SKELETON.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.SKELETON)), 0.9375F, 0.5F, true));
        eye(event, MobContent.DEMON_EYE.get(), 0.7F);
        eye(event, MobContent.SERVANT_OF_CTHULHU.get(), 0.55F);
        eye(event, MobContent.EYE_OF_CTHULHU.get(), 2.4F);
        register(event, MobContent.CAVE_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        register(event, MobContent.BLOOD_ZOMBIE.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, true));
        eye(event, MobContent.DRIPPLER.get(), 0.9F);
        worm(event, MobContent.DEVOURER.get(), 0.8F);
        worm(event, MobContent.EATER_OF_WORLDS.get(), 1.4F);
        eye(event, MobContent.BRAIN_CREEPER.get(), 0.7F);
        register(event, MobContent.BRAIN_OF_CTHULHU.get(), ctx -> new TerraModelRenderer<>(ctx,
            new com.terracraft.client.model.BrainModel(ctx.bakeLayer(TerraModels.BRAIN)), 2.2F / (15.0F / 16.0F), 1.0F, false));
        worm(event, MobContent.GIANT_WORM.get(), 0.6F);
        json(event, MobContent.EATER_OF_SOULS.get(), "eater_of_souls", 1.0F, 0.35F);
        json(event, MobContent.CRIMERA.get(), "crimera", 1.0F, 0.35F);
        json(event, MobContent.FACE_MONSTER.get(), "face_monster", 1.0F, 0.5F);
        json(event, MobContent.BLOOD_CRAWLER.get(), "blood_crawler", 1.0F, 0.5F);
        // Dungeon
        register(event, MobContent.ANGRY_BONES.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.SKELETON)), 0.9375F, 0.5F, true));
        register(event, MobContent.DARK_CASTER.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, false));
        slime(event, MobContent.DUNGEON_SLIME.get(), 1.0F);
        skull(event, MobContent.CURSED_SKULL.get(), 0.7F);
        skull(event, MobContent.DUNGEON_GUARDIAN.get(), 1.6F);
        skull(event, MobContent.SKELETRON.get(), 2.2F);
        register(event, MobContent.SKELETRON_HAND.get(), ctx -> new TerraModelRenderer<>(ctx,
            new com.terracraft.client.model.BoneHandModel(ctx.bakeLayer(TerraModels.BONE_HAND)), 1.2F / (10.0F / 16.0F), 0.4F, false));
        // Jungle
        slime(event, MobContent.JUNGLE_SLIME.get(), 1.0F);
        register(event, MobContent.JUNGLE_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        json(event, MobContent.MAN_EATER.get(), "man_eater", 1.0F, 0.4F);
        json(event, MobContent.SNATCHER.get(), "snatcher", 1.0F, 0.35F);
        bee(event, MobContent.HORNET.get(), 0.7F);
        bee(event, MobContent.BEE.get(), 0.35F);
        bee(event, MobContent.QUEEN_BEE.get(), 2.0F);
        // Underworld
        json(event, MobContent.IMP.get(), "imp", 1.0F, 0.4F);
        json(event, MobContent.DEMON.get(), "demon", 1.0F, 0.6F);
        json(event, MobContent.VOODOO_DEMON.get(), "voodoo_demon", 1.0F, 0.6F);
        slime(event, MobContent.LAVA_SLIME.get(), 1.0F);
        register(event, MobContent.HELLBAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        worm(event, MobContent.BONE_SERPENT.get(), 0.8F);
        register(event, MobContent.WALL_OF_FLESH.get(), WallOfFleshRenderer::new);
        eye(event, MobContent.WALL_OF_FLESH_EYE.get(), 3.2F);
        maw(event, MobContent.THE_HUNGRY.get(), 1.0F);
        // Goblin Army (goblins are a bit shorter than people)
        for (var goblin : java.util.List.of(MobContent.GOBLIN_PEON, MobContent.GOBLIN_THIEF, MobContent.GOBLIN_SORCERER, MobContent.GOBLIN_ARCHER)) {
            register(event, goblin.get(), ctx -> new TerraModelRenderer<>(ctx,
                new TerraHumanoidModel(ctx.bakeLayer(TerraModels.GOBLIN)), 0.9375F * 1.5F / 1.8F, 0.4F, false));
        }
        register(event, MobContent.GOBLIN_WARRIOR.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.GOBLIN)), 0.9375F * 1.7F / 1.8F, 0.45F, false));
        json(event, MobContent.METEOR_HEAD.get(), "meteor_head", 1.0F, 0.0F);
        // Hardmode
        json(event, MobContent.PIXIE.get(), "pixie", 1.0F, 0.2F);
        json(event, MobContent.UNICORN.get(), "unicorn", 1.0F, 0.7F);
        json(event, MobContent.GASTROPOD.get(), "gastropod", 1.0F, 0.4F);
        register(event, MobContent.ILLUMINANT_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 0.8F, 0.3F, false));
        slime(event, MobContent.ILLUMINANT_SLIME.get(), 1.0F);
        json(event, MobContent.CHAOS_ELEMENTAL.get(), "chaos_elemental", 0.9375F, 0.5F);
        json(event, MobContent.CORRUPTOR.get(), "corruptor", 1.0F, 0.5F);
        json(event, MobContent.SLIMER.get(), "slimer", 1.0F, 0.5F);
        slime(event, MobContent.CRIMSLIME.get(), 1.0F);
        json(event, MobContent.HERPLING.get(), "herpling", 1.0F, 0.5F);
        json(event, MobContent.FLOATY_GROSS.get(), "floaty_gross", 1.0F, 0.0F);
        json(event, MobContent.WRAITH.get(), "wraith", 0.9375F, 0.0F);
        json(event, MobContent.POSSESSED_ARMOR.get(), "possessed_armor", 0.9375F, 0.5F);
        json(event, MobContent.WEREWOLF.get(), "werewolf", 1.0F, 0.5F);
        worm(event, MobContent.WYVERN.get(), 1.0F);
        register(event, MobContent.ARMORED_SKELETON.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.SKELETON)), 0.9375F, 0.5F, true));
        register(event, MobContent.GIANT_BAT.get(), ctx -> new TerraModelRenderer<>(ctx,
            new BatModel3D(ctx.bakeLayer(TerraModels.BAT)), 1.2F, 0.4F, false));
        json(event, MobContent.MIMIC.get(), "mimic", 1.0F, 0.6F);
        // Mechanical bosses
        eye(event, MobContent.RETINAZER.get(), 2.0F);
        eye(event, MobContent.SPAZMATISM.get(), 2.0F);
        eye(event, MobContent.PROBE.get(), 0.6F);
        worm(event, MobContent.DESTROYER.get(), 1.4F);
        skull(event, MobContent.SKELETRON_PRIME.get(), 2.2F);
        json(event, MobContent.PRIME_CANNON.get(), "prime_cannon", 1.0F, 0.4F);
        json(event, MobContent.PRIME_SAW.get(), "prime_saw", 1.0F, 0.4F);
        json(event, MobContent.PRIME_VICE.get(), "prime_vice", 1.0F, 0.4F);
        json(event, MobContent.PRIME_LASER.get(), "prime_laser", 1.0F, 0.4F);
        // Queen Slime and her minions
        slime(event, MobContent.CRYSTAL_SLIME.get(), 0.8F);
        slime(event, MobContent.BOUNCY_SLIME.get(), 0.9F);
        register(event, MobContent.HEAVENLY_SLIME.get(), ctx -> new TerraModelRenderer<>(ctx,
            new SlimeBodyModel(ctx.bakeLayer(TerraModels.HEAVENLY_SLIME)), slime(0.8F), 0.3F, false));
        register(event, MobContent.QUEEN_SLIME.get(), ctx -> new TerraModelRenderer<>(ctx,
            new SlimeBodyModel(ctx.bakeLayer(TerraModels.QUEEN_SLIME)), slime(2.6F), 1.2F, false));
        // Pirate Invasion
        for (var pirate : java.util.List.of(MobContent.PIRATE_DECKHAND, MobContent.PIRATE_CORSAIR, MobContent.PIRATE_CROSSBOWER,
                MobContent.PIRATE_DEADEYE)) {
            register(event, pirate.get(), ctx -> new TerraModelRenderer<>(ctx,
                new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F, 0.5F, false));
        }
        register(event, MobContent.PIRATE_CAPTAIN.get(), ctx -> new TerraModelRenderer<>(ctx,
            new TerraHumanoidModel(ctx.bakeLayer(TerraModels.HUMANOID)), 0.9375F * 2.0F / 1.8F, 0.6F, false));
        json(event, MobContent.PARROT.get(), "parrot", 1.0F, 0.2F);
        json(event, MobContent.FLYING_DUTCHMAN.get(), "flying_dutchman", 1.5F, 0.0F);
        // Frost Legion
        json(event, MobContent.MISTER_STABBY.get(), "mister_stabby", 1.0F, 0.5F);
        json(event, MobContent.SNOWMAN_GANGSTA.get(), "snowman_gangsta", 1.0F, 0.5F);
        json(event, MobContent.SNOW_BALLA.get(), "snow_balla", 1.0F, 0.5F);
        // Hardmode jungle and Plantera
        json(event, MobContent.ANGRY_TRAPPER.get(), "angry_trapper", 1.0F, 0.0F);
        json(event, MobContent.DERPLING.get(), "derpling", 1.0F, 0.5F);
        json(event, MobContent.PLANTERA.get(), "plantera", 1.6F, 0.0F);
        json(event, MobContent.PLANTERA_HOOK.get(), "plantera_hook", 1.0F, 0.0F);
        json(event, MobContent.PLANTERA_TENTACLE.get(), "plantera_tentacle", 1.0F, 0.0F);
        // Stage 7: Duke Fishron, the Dungeon after Plantera, the moons, Empress of Light, Martian Madness
        humanoid(event, MobContent.RAGGED_CASTER.get(), TerraModels.HUMANOID, 1.8F, false);
        humanoid(event, MobContent.NECROMANCER.get(), TerraModels.HUMANOID, 1.8F, false);
        humanoid(event, MobContent.SCARECROW.get(), TerraModels.HUMANOID, 1.9F, true);
        humanoid(event, MobContent.HEADLESS_HORSEMAN.get(), TerraModels.HUMANOID, 2.6F, true);
        humanoid(event, MobContent.ZOMBIE_ELF.get(), TerraModels.HUMANOID, 1.3F, true);
        humanoid(event, MobContent.ELF_ARCHER.get(), TerraModels.HUMANOID, 1.3F, false);
        humanoid(event, MobContent.GINGERBREAD_MAN.get(), TerraModels.HUMANOID, 1.4F, true);
        humanoid(event, MobContent.NUTCRACKER.get(), TerraModels.HUMANOID, 2.0F, true);
        humanoid(event, MobContent.YETI.get(), TerraModels.HUMANOID, 2.6F, true);
        humanoid(event, MobContent.SANTA_NK1.get(), TerraModels.HUMANOID, 2.6F, false);
        humanoid(event, MobContent.GRAY_GRUNT.get(), TerraModels.HUMANOID, 1.8F, true);
        humanoid(event, MobContent.RAY_GUNNER.get(), TerraModels.HUMANOID, 1.8F, false);
        humanoid(event, MobContent.BRAIN_SCRAMBLER.get(), TerraModels.HUMANOID, 1.8F, false);
        humanoid(event, MobContent.GIGAZAPPER.get(), TerraModels.HUMANOID, 1.8F, true);
        humanoid(event, MobContent.MARTIAN_OFFICER.get(), TerraModels.HUMANOID, 1.9F, false);
        humanoid(event, MobContent.BLUE_ARMORED_BONES.get(), TerraModels.SKELETON, 1.8F, true);
        humanoid(event, MobContent.HELL_ARMORED_BONES.get(), TerraModels.SKELETON, 1.8F, true);
        humanoid(event, MobContent.PALADIN.get(), TerraModels.SKELETON, 2.6F, true);
        humanoid(event, MobContent.SKELETON_SNIPER.get(), TerraModels.SKELETON, 1.8F, false);
        humanoid(event, MobContent.TACTICAL_SKELETON.get(), TerraModels.SKELETON, 1.8F, false);
        humanoid(event, MobContent.SKELETON_COMMANDO.get(), TerraModels.SKELETON, 1.8F, false);
        json(event, MobContent.TRUFFLE_WORM.get(), "truffle_worm", 1.0F, 0.2F);
        json(event, MobContent.DUKE_FISHRON.get(), "duke_fishron", 1.0F, 0.0F);
        json(event, MobContent.SHARKRON.get(), "sharkron", 1.0F, 0.0F);
        json(event, MobContent.DUNGEON_SPIRIT.get(), "dungeon_spirit", 1.0F, 0.0F);
        json(event, MobContent.SPLINTERLING.get(), "splinterling", 1.0F, 0.4F);
        json(event, MobContent.HELLHOUND.get(), "hellhound", 1.0F, 0.5F);
        json(event, MobContent.POLTERGEIST.get(), "poltergeist", 1.0F, 0.0F);
        json(event, MobContent.MOURNING_WOOD.get(), "mourning_wood", 1.0F, 1.0F);
        json(event, MobContent.PUMPKING.get(), "pumpking", 1.0F, 0.0F);
        json(event, MobContent.FLOCKO.get(), "flocko", 1.0F, 0.0F);
        json(event, MobContent.EVERSCREAM.get(), "everscream", 1.0F, 1.2F);
        json(event, MobContent.ICE_QUEEN.get(), "ice_queen", 1.0F, 0.0F);
        json(event, MobContent.PRISMATIC_LACEWING.get(), "prismatic_lacewing", 1.0F, 0.0F);
        json(event, MobContent.EMPRESS_OF_LIGHT.get(), "empress_of_light", 1.0F, 0.0F);
        json(event, MobContent.MARTIAN_PROBE.get(), "martian_probe", 1.0F, 0.0F);
        json(event, MobContent.MARTIAN_DRONE.get(), "martian_drone", 1.0F, 0.0F);
        json(event, MobContent.SCUTLIX.get(), "scutlix", 1.0F, 0.6F);
        json(event, MobContent.MARTIAN_SAUCER.get(), "martian_saucer", 1.0F, 0.0F);
        // Lihzahrd Temple and Golem
        json(event, MobContent.LIHZAHRD.get(), "lihzahrd", 1.0F, 0.5F);
        json(event, MobContent.FLYING_SNAKE.get(), "flying_snake", 1.0F, 0.3F);
        json(event, MobContent.GOLEM.get(), "golem", 1.0F, 1.6F);
        json(event, MobContent.GOLEM_HEAD.get(), "golem_head", 1.0F, 0.0F);
        json(event, MobContent.GOLEM_FIST.get(), "golem_fist", 1.0F, 0.0F);
        // Stage 8: Lunatic Cultist, Celestial Pillars, Moon Lord
        humanoid(event, MobContent.LUNATIC_CULTIST.get(), TerraModels.HUMANOID, 2.2F, false);
        humanoid(event, MobContent.CULTIST_CLONE.get(), TerraModels.HUMANOID, 2.2F, false);
        humanoid(event, MobContent.CULTIST_DEVOTEE.get(), TerraModels.HUMANOID, 2.0F, false);
        humanoid(event, MobContent.SELENIAN.get(), TerraModels.HUMANOID, 1.8F, true);
        humanoid(event, MobContent.STORM_DIVER.get(), TerraModels.HUMANOID, 1.8F, false);
        humanoid(event, MobContent.VORTEXIAN.get(), TerraModels.HUMANOID, 1.8F, true);
        humanoid(event, MobContent.PREDICTOR.get(), TerraModels.HUMANOID, 1.9F, false);
        humanoid(event, MobContent.TWINKLE_POPPER.get(), TerraModels.HUMANOID, 1.6F, false);
        json(event, MobContent.SOLAR_PILLAR.get(), "solar_pillar", 1.6F, 2.0F);
        json(event, MobContent.VORTEX_PILLAR.get(), "vortex_pillar", 1.6F, 2.0F);
        json(event, MobContent.NEBULA_PILLAR.get(), "nebula_pillar", 1.6F, 2.0F);
        json(event, MobContent.STARDUST_PILLAR.get(), "stardust_pillar", 1.6F, 2.0F);
        json(event, MobContent.SROLLER.get(), "sroller", 1.0F, 0.5F);
        json(event, MobContent.CORITE.get(), "corite", 1.0F, 0.0F);
        json(event, MobContent.ALIEN_HORNET.get(), "alien_hornet", 1.0F, 0.0F);
        json(event, MobContent.NEBULA_FLOATER.get(), "nebula_floater", 1.0F, 0.0F);
        json(event, MobContent.BRAIN_SUCKLER.get(), "brain_suckler", 1.0F, 0.0F);
        json(event, MobContent.STAR_CELL.get(), "star_cell", 1.0F, 0.0F);
        json(event, MobContent.FLOW_INVADER.get(), "flow_invader", 1.0F, 0.0F);
        json(event, MobContent.MOON_LORD.get(), "moon_lord", 1.5F, 0.0F);
        json(event, MobContent.MOON_LORD_HAND.get(), "moon_lord_hand", 2.0F, 0.0F);
        json(event, MobContent.MOON_LORD_HEAD.get(), "moon_lord_head", 2.0F, 0.0F);
        // Stage 9: minions and sentries, the Solar Eclipse
        for (var minion : com.terracraft.registry.content.SummonContent.minions()) {
            json(event, minion.get(), minion.getId().getPath(), 1.0F, 0.0F);
        }
        for (var mob : java.util.List.of(MobContent.EYEZOR, MobContent.FRANKENSTEIN, MobContent.SWAMP_THING, MobContent.VAMPIRE,
            MobContent.CREATURE_FROM_THE_DEEP, MobContent.BUTCHER)) {
            humanoid(event, mob.get(), TerraModels.HUMANOID, mob == MobContent.FRANKENSTEIN || mob == MobContent.BUTCHER ? 2.1F : 1.8F, true);
        }
        humanoid(event, MobContent.FRITZ.get(), TerraModels.HUMANOID, 1.3F, true);
        humanoid(event, MobContent.NAILHEAD.get(), TerraModels.HUMANOID, 1.9F, false);
        humanoid(event, MobContent.DR_MAN_FLY.get(), TerraModels.HUMANOID, 1.8F, false);
        json(event, MobContent.REAPER.get(), "reaper", 1.0F, 0.0F);
        json(event, MobContent.MOTHRON.get(), "mothron", 1.0F, 0.0F);
        json(event, MobContent.DEADLY_SPHERE.get(), "deadly_sphere", 1.0F, 0.0F);
        for (var npc : NpcContent.all()) {
            var layer = npc.getId().getPath().contains("goblin") ? TerraModels.GOBLIN : TerraModels.HUMANOID;
            register(event, npc.get(), ctx -> new TerraModelRenderer<>(ctx,
                new TerraHumanoidModel(ctx.bakeLayer(layer)), 0.9375F, 0.5F, false));
        }
    }

    /** A humanoid (or skeleton) scaled to a hitbox {@code height} blocks tall (the model is 2 blocks at scale 0.9375). */
    private static void humanoid(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type,
                                 net.minecraft.client.model.geom.ModelLayerLocation layer, float height, boolean armsForward) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new TerraHumanoidModel(ctx.bakeLayer(layer)), 0.9375F * height / 1.8F,
            Math.min(1.0F, height * 0.28F), armsForward));
    }

    /** A creature with its own JSON model (see JsonCreatureModel), drawn at 1 model unit = 1/16 block times {@code scale}. */
    private static void json(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, String name, float scale, float shadow) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx,
            new com.terracraft.client.model.JsonCreatureModel(ctx.bakeLayer(TerraModels.creature(name)), name), scale, shadow, false));
    }

    private static void slime(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new SlimeBodyModel(ctx.bakeLayer(TerraModels.SLIME)), slime(width), width * 0.45F, false));
    }

    /** Worm segment model is 10 px wide. */
    public static void worm(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.WormModel(ctx.bakeLayer(TerraModels.WORM)),
            width / (10.0F / 16.0F), width * 0.4F, false));
    }

    private static void maw(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.MawModel(ctx.bakeLayer(TerraModels.MAW)),
            width / (8.0F / 16.0F), width * 0.4F, false));
    }

    /** Bee model: scaled so 10 px fill the hitbox width (the body is longer than wide). */
    private static void bee(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.BeeModel3D(ctx.bakeLayer(TerraModels.BEE)),
            width / (10.0F / 16.0F), width * 0.4F, false));
    }

    /** Skull model is 10 px wide. */
    private static void skull(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new com.terracraft.client.model.SkullModel(ctx.bakeLayer(TerraModels.SKULL)),
            width / (10.0F / 16.0F), width * 0.4F, false));
    }

    private static void eye(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends LivingEntity> type, float width) {
        register(event, type, ctx -> new TerraModelRenderer<>(ctx, new EyeModel(ctx.bakeLayer(TerraModels.EYE)), eye(width), width * 0.4F, false));
    }

    private static <T extends LivingEntity> void register(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type,
                                                          Function<EntityRendererProvider.Context, EntityRenderer<T, ?>> model) {
        event.registerEntityRenderer(type, ctx -> flat() ? new TerrariaMobRenderer<>(ctx) : model.apply(ctx));
    }

    private static boolean flat() {
        try {
            return TerraConfig.CLIENT.flatSprites.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }
}
