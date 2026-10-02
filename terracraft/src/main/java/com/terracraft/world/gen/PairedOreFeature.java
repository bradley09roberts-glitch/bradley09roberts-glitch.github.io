package com.terracraft.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.config.TerraConfig;
import com.terracraft.progression.WorldVariants;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

/**
 * An ore vein that belongs to one side of a Terraria ore pair (Copper/Tin, Iron/Lead, Silver/Tungsten,
 * Gold/Platinum...). Each world picks one ore per pair; the chosen ore always generates, the other only with
 * {@code secondaryOreFrequency} (0 = strict Terraria behaviour).
 * <pre>{"type": "terracraft:paired_ore", "config": {"pair": "copper_tin", "secondary": false, "ore": {...vanilla ore config...}}}</pre>
 */
public class PairedOreFeature extends Feature<PairedOreFeature.Config> {
    public record Config(WorldVariants.OrePair pair, boolean secondary, OreConfiguration ore) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            WorldVariants.OrePair.CODEC.fieldOf("pair").forGetter(Config::pair),
            Codec.BOOL.optionalFieldOf("secondary", false).forGetter(Config::secondary),
            OreConfiguration.CODEC.fieldOf("ore").forGetter(Config::ore)
        ).apply(i, Config::new));
    }

    public PairedOreFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config config = context.config();
        boolean chosen = WorldgenVariants.get(context.level()).usesSecondary(config.pair()) == config.secondary();
        if (!chosen && context.random().nextDouble() >= TerraConfig.COMMON.secondaryOreFrequency.get()) {
            return false;
        }
        return Feature.ORE.place(config.ore(), context.level(), context.chunkGenerator(), context.random(), context.origin());
    }
}
