package com.starforged.registry;

import com.starforged.Starforged;
import com.starforged.block.AstralCrystalClusterBlock;
import com.starforged.block.CelestialAltarBlock;
import com.starforged.block.GlowingDecorBlock;
import com.starforged.block.MeteoriteRockBlock;
import com.starforged.block.RuneBlock;
import com.starforged.block.StarglassBlock;
import com.starforged.block.StarmetalOreBlock;
import com.starforged.block.VaultSealBlock;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Starforged.MODID);

    // --- Meteorites -------------------------------------------------------------------------------------------
    public static final RegistryObject<Block> METEORITE_ROCK = register("meteorite_rock", MeteoriteRockBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5F, 12.0F)
            .requiresCorrectToolForDrops().sound(SoundType.BASALT).lightLevel(s -> 3));

    public static final RegistryObject<Block> STARMETAL_ORE = register("starmetal_ore", p -> new StarmetalOreBlock(UniformInt.of(3, 7), p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(5.0F, 12.0F)
            .requiresCorrectToolForDrops().sound(SoundType.ANCIENT_DEBRIS).lightLevel(s -> 6));

    public static final RegistryObject<Block> ASTRAL_CRYSTAL_CLUSTER = register("astral_crystal_cluster", p -> new AstralCrystalClusterBlock(7.0F, 10.0F, p),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).forceSolidOn().noOcclusion()
            .sound(SoundType.AMETHYST_CLUSTER).strength(1.5F).lightLevel(s -> 9).pushReaction(PushReaction.DESTROY));

    public static final RegistryObject<Block> STARMETAL_BLOCK = register("starmetal_block", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(6.0F, 14.0F)
            .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK));

    // --- Observatory masonry ----------------------------------------------------------------------------------
    public static final RegistryObject<Block> ASTRAL_BRICKS = register("astral_bricks", Block::new, ModBlocks::brickProps);
    public static final RegistryObject<Block> CRACKED_ASTRAL_BRICKS = register("cracked_astral_bricks", Block::new, ModBlocks::brickProps);
    public static final RegistryObject<Block> CHISELED_ASTRAL_BRICKS = register("chiseled_astral_bricks", Block::new,
        () -> brickProps().lightLevel(s -> 5));
    public static final RegistryObject<Block> ASTRAL_BRICK_STAIRS = register("astral_brick_stairs",
        p -> new StairBlock(ASTRAL_BRICKS.get().defaultBlockState(), p), ModBlocks::brickProps);
    public static final RegistryObject<Block> ASTRAL_BRICK_SLAB = register("astral_brick_slab", SlabBlock::new, ModBlocks::brickProps);

    public static final RegistryObject<Block> STARGLASS = register("starglass", StarglassBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.6F).sound(SoundType.GLASS).noOcclusion()
            .lightLevel(s -> 2).isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));

    public static final RegistryObject<Block> STAR_LANTERN = register("star_lantern", GlowingDecorBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.4F).sound(SoundType.GLASS).lightLevel(s -> 15));

    // --- Functional blocks ------------------------------------------------------------------------------------
    public static final RegistryObject<Block> CELESTIAL_ALTAR = register("celestial_altar", CelestialAltarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(30.0F, 1200.0F)
            .requiresCorrectToolForDrops().sound(SoundType.LODESTONE).lightLevel(s -> 10).noOcclusion());

    public static final RegistryObject<Block> GRAVITY_RUNE = register("gravity_rune", p -> new RuneBlock(RuneBlock.Kind.GRAVITY, p),
        () -> brickProps().lightLevel(s -> s.getValue(RuneBlock.POWERED) ? 12 : 3));
    public static final RegistryObject<Block> STARFIRE_RUNE = register("starfire_rune", p -> new RuneBlock(RuneBlock.Kind.STARFIRE, p),
        () -> brickProps().lightLevel(s -> s.getValue(RuneBlock.POWERED) ? 12 : 3));

    public static final RegistryObject<Block> VAULT_SEAL = register("vault_seal", VaultSealBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(-1.0F, 3600000.0F)
            .noLootTable().sound(SoundType.AMETHYST).lightLevel(s -> 11).pushReaction(PushReaction.BLOCK));

    private static BlockBehaviour.Properties brickProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.5F, 9.0F)
            .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS);
    }

    private static RegistryObject<Block> register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                                  Supplier<BlockBehaviour.Properties> props) {
        return BLOCKS.register(name, () -> factory.apply(props.get().setId(BLOCKS.key(name))));
    }

    private ModBlocks() {
    }
}
