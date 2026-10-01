package com.starforged.moon;

import com.starforged.Starforged;
import com.starforged.moon.block.GravityPlateBlock;
import com.starforged.moon.block.LunarGatewayBlock;
import com.starforged.moon.block.LunarMuralBlock;
import com.starforged.moon.block.MoonAltarBlock;
import com.starforged.moon.block.MoonDecorBlock;
import com.starforged.moon.block.MoonOreBlock;
import com.starforged.moon.block.MoonSealBlock;
import com.starforged.moon.block.MoonpetalBlock;
import com.starforged.moon.block.OrreryConsoleBlock;
import com.starforged.moon.block.OrreryRingBlock;
import com.starforged.moon.block.SeleniteClusterBlock;
import com.starforged.moon.block.TidalClamBlock;
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

/** Blocks of the Moonforged expansion. */
public final class MoonBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Starforged.MODID);

    // --- Pale Reach terrain -------------------------------------------------------------------------------------
    public static final RegistryObject<Block> MOONSTONE = register("moonstone", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(1.8F, 7.0F).requiresCorrectToolForDrops().sound(SoundType.CALCITE));
    public static final RegistryObject<Block> REGOLITH = register("regolith", p -> new ColoredFallingBlock(new ColorRGBA(0xFFB8BCC4), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.6F)
            .sound(SoundType.SUSPICIOUS_GRAVEL));
    public static final RegistryObject<Block> UMBRAL_REGOLITH = register("umbral_regolith", p -> new ColoredFallingBlock(new ColorRGBA(0xFF2E3038), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.SNARE).strength(0.6F)
            .sound(SoundType.SUSPICIOUS_GRAVEL));
    public static final RegistryObject<Block> SILVER_SAND = register("silver_sand", p -> new ColoredFallingBlock(new ColorRGBA(0xFFDDE2EA), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final RegistryObject<Block> MOONSILVER_ORE = register("moonsilver_ore", p -> new MoonOreBlock(UniformInt.of(5, 9), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(6.0F, 16.0F).requiresCorrectToolForDrops()
            .sound(SoundType.DEEPSLATE).lightLevel(s -> 6));
    public static final RegistryObject<Block> SELENITE_CLUSTER = register("selenite_cluster", p -> new SeleniteClusterBlock(7.0F, 9.0F, p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
            .strength(1.5F).lightLevel(s -> 12).pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> MOONPETAL = register("moonpetal", MoonpetalBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).noCollision().instabreak().sound(SoundType.GRASS).lightLevel(s -> 9)
            .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> TIDAL_CLAM = register("tidal_clam", TidalClamBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(2.0F, 6.0F).sound(SoundType.BONE_BLOCK).noOcclusion()
            .lightLevel(s -> s.getValue(TidalClamBlock.OPEN) && s.getValue(TidalClamBlock.PEARL) ? 10 : 0));
    public static final RegistryObject<Block> MOONSILVER_BLOCK = register("moonsilver_block", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(7.5F, 18.0F).requiresCorrectToolForDrops()
            .sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 5));

    // --- Tidal Orrery masonry -----------------------------------------------------------------------------------
    public static final RegistryObject<Block> LUNAR_BRICKS = register("lunar_bricks", Block::new, MoonBlocks::brickProps);
    public static final RegistryObject<Block> CRACKED_LUNAR_BRICKS = register("cracked_lunar_bricks", Block::new, MoonBlocks::brickProps);
    public static final RegistryObject<Block> CHISELED_LUNAR_BRICKS = register("chiseled_lunar_bricks", MoonDecorBlock::new,
        () -> brickProps().lightLevel(s -> 7));
    public static final RegistryObject<Block> LUNAR_BRICK_STAIRS = register("lunar_brick_stairs",
        p -> new StairBlock(LUNAR_BRICKS.get().defaultBlockState(), p), MoonBlocks::brickProps);
    public static final RegistryObject<Block> LUNAR_BRICK_SLAB = register("lunar_brick_slab", SlabBlock::new, MoonBlocks::brickProps);
    public static final RegistryObject<Block> MOON_GLASS = register("moon_glass", TransparentBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6F).sound(SoundType.GLASS).noOcclusion()
            .lightLevel(s -> 3).isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final RegistryObject<Block> MOON_LANTERN = register("moon_lantern", MoonDecorBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.GLASS).lightLevel(s -> 15));

    // --- Functional ---------------------------------------------------------------------------------------------
    public static final RegistryObject<Block> GRAVITY_PLATE = register("gravity_plate", GravityPlateBlock::new,
        () -> brickProps().noOcclusion().lightLevel(s -> 8));
    public static final RegistryObject<Block> ORRERY_RING = register("orrery_ring", OrreryRingBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL)
            .lightLevel(s -> 10).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<Block> LUNAR_MURAL = register("lunar_mural", LunarMuralBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.STONE)
            .lightLevel(s -> 9).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<Block> ORRERY_CONSOLE = register("orrery_console", OrreryConsoleBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL)
            .lightLevel(s -> 12).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<Block> MOON_SEAL = register("moon_seal", MoonSealBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.GLASS)
            .lightLevel(s -> 13).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<Block> MOON_ALTAR = register("moon_altar", MoonAltarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(30.0F, 1200.0F).requiresCorrectToolForDrops()
            .sound(SoundType.LODESTONE).lightLevel(s -> 12).noOcclusion());
    public static final RegistryObject<Block> LUNAR_GATEWAY = register("lunar_gateway", LunarGatewayBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).noCollision().strength(-1.0F, 3600000.0F).noLootTable()
            .lightLevel(s -> 15).pushReaction(PushReaction.BLOCK).sound(SoundType.GLASS));

    private static BlockBehaviour.Properties brickProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(3.0F, 10.0F)
            .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS);
    }

    private static RegistryObject<Block> register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                                  Supplier<BlockBehaviour.Properties> props) {
        return BLOCKS.register(name, () -> factory.apply(props.get().setId(BLOCKS.key(name))));
    }

    private MoonBlocks() {
    }
}
