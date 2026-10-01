package com.starforged.tempest;

import com.starforged.Starforged;
import com.starforged.tempest.block.AetheriumOreBlock;
import com.starforged.tempest.block.CitadelCoreBlock;
import com.starforged.tempest.block.ConductorBlock;
import com.starforged.tempest.block.CycloneEmitterBlock;
import com.starforged.tempest.block.GaleSeedBlock;
import com.starforged.tempest.block.GaleVentBlock;
import com.starforged.tempest.block.LightningBeaconBlock;
import com.starforged.tempest.block.OverloadRelayBlock;
import com.starforged.tempest.block.RotatingConductorBlock;
import com.starforged.tempest.block.ShockPlateBlock;
import com.starforged.tempest.block.SkyAnchorBlock;
import com.starforged.tempest.block.SplitterRelayBlock;
import com.starforged.tempest.block.StormCapacitorBlock;
import com.starforged.tempest.block.StormDynamoBlock;
import com.starforged.tempest.block.StormLiftBlock;
import com.starforged.tempest.block.StormRelayBlock;
import com.starforged.tempest.block.StormgateBlock;
import com.starforged.tempest.block.TempestAltarBlock;
import com.starforged.tempest.block.TempestDecorBlock;
import com.starforged.tempest.block.TempestRuneBlock;
import com.starforged.tempest.block.TempestSealBlock;
import com.starforged.tempest.block.ThunderCrystalBlock;
import com.starforged.tempest.block.WeatherEngineBlock;
import com.starforged.tempest.block.WindChimeBlock;
import com.starforged.tempest.block.WindVentBlock;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blocks of the Tempestforged expansion. */
public final class TempestBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Starforged.MODID);

    // --- Stormreach terrain -------------------------------------------------------------------------------------
    public static final RegistryObject<Block> STORMSTONE = register("stormstone", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(2.0F, 8.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final RegistryObject<Block> SKYROCK = register("skyrock", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(1.6F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.TUFF));
    public static final RegistryObject<Block> SKYSOIL = register("skysoil", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6F).sound(SoundType.ROOTED_DIRT));
    public static final RegistryObject<Block> STORMGRASS = register("stormgrass", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.7F).sound(SoundType.GRASS));
    public static final RegistryObject<Block> STORMWOOD_LOG = register("stormwood_log", RotatedPillarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).instrument(NoteBlockInstrument.BASS).strength(2.0F)
            .sound(SoundType.WOOD).ignitedByLava().lightLevel(s -> 3));
    public static final RegistryObject<Block> STORMWOOD_PLANKS = register("stormwood_planks", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F)
            .sound(SoundType.WOOD).ignitedByLava());
    public static final RegistryObject<Block> STORMLEAVES = register("stormleaves",
        p -> new UntintedParticleLeavesBlock(0.03F, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF7AC8FF), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.2F).randomTicks().sound(SoundType.AZALEA_LEAVES)
            .noOcclusion().lightLevel(s -> 6).isValidSpawn((s, l, p, t) -> false).isSuffocating((s, l, p) -> false)
            .isViewBlocking((s, l, p) -> false).ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor((s, l, p) -> false));
    public static final RegistryObject<Block> GALE_SEED = register("gale_seed", GaleSeedBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).noCollision().instabreak().sound(SoundType.GRASS)
            .lightLevel(s -> 7).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));

    // --- Resources ----------------------------------------------------------------------------------------------
    public static final RegistryObject<Block> AETHERIUM_ORE = register("aetherium_ore", p -> new AetheriumOreBlock(UniformInt.of(6, 10), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(7.0F, 18.0F).requiresCorrectToolForDrops()
            .sound(SoundType.DEEPSLATE).lightLevel(s -> 7));
    public static final RegistryObject<Block> AETHERIUM_BLOCK = register("aetherium_block", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(8.0F, 20.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 4));
    public static final RegistryObject<Block> CHARGED_AETHERIUM_BLOCK = register("charged_aetherium_block", TempestDecorBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(8.0F, 20.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 13));
    public static final RegistryObject<Block> THUNDER_CRYSTAL_CLUSTER = register("thunder_crystal_cluster", p -> new ThunderCrystalBlock(7.0F, 9.0F, p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
            .strength(1.5F).lightLevel(s -> 12).pushReaction(PushReaction.DESTROY));

    // --- Building -----------------------------------------------------------------------------------------------
    public static final RegistryObject<Block> TEMPEST_BRICKS = register("tempest_bricks", Block::new, TempestBlocks::brickProps);
    public static final RegistryObject<Block> CHISELED_TEMPEST_BRICKS = register("chiseled_tempest_bricks", TempestDecorBlock::new,
        () -> brickProps().lightLevel(s -> 7));
    public static final RegistryObject<Block> TEMPEST_BRICK_STAIRS = register("tempest_brick_stairs",
        p -> new StairBlock(TEMPEST_BRICKS.get().defaultBlockState(), p), TempestBlocks::brickProps);
    public static final RegistryObject<Block> TEMPEST_BRICK_SLAB = register("tempest_brick_slab", SlabBlock::new, TempestBlocks::brickProps);
    public static final RegistryObject<Block> AETHERGLASS = register("aetherglass", TransparentBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6F).sound(SoundType.GLASS).noOcclusion()
            .lightLevel(s -> 3).isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final RegistryObject<Block> STORM_LANTERN = register("storm_lantern", LanternBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN)
            .lightLevel(s -> 15).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> WIND_CHIME = register("wind_chime", WindChimeBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.CHAIN).noOcclusion().noCollision()
            .pushReaction(PushReaction.DESTROY));

    // --- Storm machines -----------------------------------------------------------------------------------------
    public static final RegistryObject<Block> STORM_DYNAMO = register("storm_dynamo", StormDynamoBlock::new, () -> machineProps().lightLevel(s -> 9));
    public static final RegistryObject<Block> AETHERIUM_CONDUCTOR = register("aetherium_conductor", ConductorBlock::new,
        () -> machineProps().lightLevel(s -> s.getValue(ConductorBlock.POWERED) ? 12 : 4));
    public static final RegistryObject<Block> ROTATING_CONDUCTOR = register("rotating_conductor", RotatingConductorBlock::new,
        () -> machineProps().lightLevel(s -> s.getValue(ConductorBlock.POWERED) ? 12 : 4));
    public static final RegistryObject<Block> SPLITTER_RELAY = register("splitter_relay", SplitterRelayBlock::new, () -> machineProps().lightLevel(s -> 6));
    public static final RegistryObject<Block> STORM_RELAY = register("storm_relay", StormRelayBlock::new,
        () -> machineProps().lightLevel(s -> s.getValue(StormRelayBlock.POWERED) ? 12 : 3));
    public static final RegistryObject<Block> OVERLOAD_RELAY = register("overload_relay", OverloadRelayBlock::new, () -> machineProps().lightLevel(s -> 8));
    public static final RegistryObject<Block> STORM_CAPACITOR = register("storm_capacitor", StormCapacitorBlock::new,
        () -> machineProps().lightLevel(s -> s.getValue(StormCapacitorBlock.CHARGE)));
    public static final RegistryObject<Block> CITADEL_CORE = register("citadel_core", CitadelCoreBlock::new,
        () -> machineProps().lightLevel(s -> s.getValue(CitadelCoreBlock.LIT) ? 15 : 5));
    public static final RegistryObject<Block> LIGHTNING_BEACON = register("lightning_beacon", LightningBeaconBlock::new,
        () -> machineProps().lightLevel(s -> 10));
    public static final RegistryObject<Block> WEATHER_ENGINE = register("weather_engine", WeatherEngineBlock::new, () -> machineProps().lightLevel(s -> 9));
    public static final RegistryObject<Block> WIND_VENT = register("wind_vent", WindVentBlock::new, TempestBlocks::machineProps);
    public static final RegistryObject<Block> GALE_VENT = register("gale_vent", GaleVentBlock::new, TempestBlocks::machineProps);
    public static final RegistryObject<Block> STORM_LIFT = register("storm_lift", StormLiftBlock::new,
        () -> machineProps().lightLevel(s -> s.getValue(StormLiftBlock.POWERED) ? 10 : 2));
    public static final RegistryObject<Block> SHOCK_PLATE = register("shock_plate", ShockPlateBlock::new, () -> machineProps().noOcclusion().lightLevel(s -> 5));
    public static final RegistryObject<Block> SKY_ANCHOR = register("sky_anchor", SkyAnchorBlock::new, () -> machineProps().lightLevel(s -> 9));

    // --- Tempest Citadel ----------------------------------------------------------------------------------------
    public static final RegistryObject<Block> THUNDER_RUNE = register("thunder_rune", p -> new TempestRuneBlock(true, p),
        () -> brickProps().lightLevel(s -> s.getValue(TempestRuneBlock.ARMED) ? 7 : 0));
    public static final RegistryObject<Block> GALE_RUNE = register("gale_rune", p -> new TempestRuneBlock(false, p),
        () -> brickProps().lightLevel(s -> s.getValue(TempestRuneBlock.ARMED) ? 7 : 0));
    public static final RegistryObject<Block> CYCLONE_EMITTER = register("cyclone_emitter", CycloneEmitterBlock::new,
        () -> machineProps().lightLevel(s -> 8));
    public static final RegistryObject<Block> TEMPEST_SEAL = register("tempest_seal", TempestSealBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.GLASS)
            .lightLevel(s -> 13).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<Block> TEMPEST_ALTAR = register("tempest_altar", TempestAltarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(30.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.LODESTONE).lightLevel(s -> 12).noOcclusion());
    public static final RegistryObject<Block> STORMGATE = register("stormgate", StormgateBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).noCollision().strength(-1.0F, 3600000.0F).noLootTable()
            .lightLevel(s -> 15).pushReaction(PushReaction.BLOCK).sound(SoundType.GLASS));

    private static BlockBehaviour.Properties brickProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F, 10.0F)
            .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS);
    }

    private static BlockBehaviour.Properties machineProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.5F, 12.0F)
            .requiresCorrectToolForDrops().sound(SoundType.COPPER);
    }

    private static RegistryObject<Block> register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                                  Supplier<BlockBehaviour.Properties> props) {
        return BLOCKS.register(name, () -> factory.apply(props.get().setId(BLOCKS.key(name))));
    }

    private TempestBlocks() {
    }
}
