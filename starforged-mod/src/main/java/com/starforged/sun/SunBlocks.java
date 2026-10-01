package com.starforged.sun;

import com.starforged.Starforged;
import com.starforged.sun.block.AshenSoilBlock;
import com.starforged.sun.block.EmberCrystalClusterBlock;
import com.starforged.sun.block.SolarBrazierBlock;
import com.starforged.sun.block.SolarGatewayBlock;
import com.starforged.sun.block.SunAltarBlock;
import com.starforged.sun.block.SunDecorBlock;
import com.starforged.sun.block.SunOreBlock;
import com.starforged.sun.block.SunSealBlock;
import com.starforged.sun.block.SunbloomBlock;
import com.starforged.sun.block.SunfireVentBlock;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blocks of the Sunforged expansion. */
public final class SunBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Starforged.MODID);

    // --- Sunlands terrain ---------------------------------------------------------------------------------------
    public static final RegistryObject<Block> SCORCHSTONE = register("scorchstone", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(1.6F, 7.0F).requiresCorrectToolForDrops().sound(SoundType.NETHERRACK));
    public static final RegistryObject<Block> SUNSTONE_ORE = register("sunstone_ore", p -> new SunOreBlock(UniformInt.of(4, 8), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(5.5F, 14.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHER_GOLD_ORE).lightLevel(s -> 7));
    public static final RegistryObject<Block> SUNSAND = register("sunsand", p -> new ColoredFallingBlock(new ColorRGBA(0xFFE6A23A), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final RegistryObject<Block> ASHEN_SOIL = register("ashen_soil", AshenSoilBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.6F).sound(SoundType.SOUL_SOIL));
    public static final RegistryObject<Block> EMBER_CRYSTAL_CLUSTER = register("ember_crystal_cluster", p -> new EmberCrystalClusterBlock(7.0F, 10.0F, p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
            .strength(1.5F).lightLevel(s -> 11).pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> SUNBLOOM = register("sunbloom", SunbloomBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).noCollision().instabreak().sound(SoundType.GRASS).lightLevel(s -> 10)
            .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> SUNSTEEL_BLOCK = register("sunsteel_block", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(7.0F, 16.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 4));

    // --- Sun Temple masonry -------------------------------------------------------------------------------------
    public static final RegistryObject<Block> SUNBAKED_BRICKS = register("sunbaked_bricks", Block::new, SunBlocks::brickProps);
    public static final RegistryObject<Block> CRACKED_SUNBAKED_BRICKS = register("cracked_sunbaked_bricks", Block::new, SunBlocks::brickProps);
    public static final RegistryObject<Block> CHISELED_SUNBAKED_BRICKS = register("chiseled_sunbaked_bricks", SunDecorBlock::new,
        () -> brickProps().lightLevel(s -> 7));
    public static final RegistryObject<Block> SUNBAKED_BRICK_STAIRS = register("sunbaked_brick_stairs",
        p -> new StairBlock(SUNBAKED_BRICKS.get().defaultBlockState(), p), SunBlocks::brickProps);
    public static final RegistryObject<Block> SUNBAKED_BRICK_SLAB = register("sunbaked_brick_slab", SlabBlock::new, SunBlocks::brickProps);
    public static final RegistryObject<Block> SOLAR_GLASS = register("solar_glass", TransparentBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.6F).sound(SoundType.GLASS).noOcclusion()
            .lightLevel(s -> 3).isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final RegistryObject<Block> SUN_LANTERN = register("sun_lantern", SunDecorBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.4F).sound(SoundType.GLASS).lightLevel(s -> 15));

    // --- Functional ---------------------------------------------------------------------------------------------
    public static final RegistryObject<Block> SOLAR_BRAZIER = register("solar_brazier", SolarBrazierBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(4.0F, 9.0F).requiresCorrectToolForDrops()
            .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(SolarBrazierBlock.LIT) ? 15 : 0));
    public static final RegistryObject<Block> SUN_SEAL = register("sun_seal", SunSealBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.GLASS)
            .lightLevel(s -> 13).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<Block> SUNFIRE_VENT = register("sunfire_vent", SunfireVentBlock::new,
        () -> brickProps().lightLevel(s -> s.getValue(SunfireVentBlock.PHASE) >= 2 ? 15 : 6));
    public static final RegistryObject<Block> SUN_ALTAR = register("sun_altar", SunAltarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(30.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.LODESTONE).lightLevel(s -> 12).noOcclusion());
    public static final RegistryObject<Block> SOLAR_GATEWAY = register("solar_gateway", SolarGatewayBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).noCollision().strength(-1.0F, 3600000.0F).noLootTable()
            .lightLevel(s -> 15).pushReaction(PushReaction.BLOCK).sound(SoundType.GLASS));

    private static BlockBehaviour.Properties brickProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(3.0F, 10.0F)
            .requiresCorrectToolForDrops().sound(SoundType.MUD_BRICKS);
    }

    private static RegistryObject<Block> register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                                  Supplier<BlockBehaviour.Properties> props) {
        return BLOCKS.register(name, () -> factory.apply(props.get().setId(BLOCKS.key(name))));
    }

    private SunBlocks() {
    }
}
