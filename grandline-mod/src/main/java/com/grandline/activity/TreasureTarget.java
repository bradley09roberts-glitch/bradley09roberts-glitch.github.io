package com.grandline.activity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a treasure map points at: one secret (buried treasure, hidden cache, lost shrine or shipwreck) of an island,
 * by island id and index into {@code Settlement.secrets}. The target chest position and the island name are copied in
 * so the client can draw the map and the tooltip without asking the server. {@code grand} marks a map pieced together
 * from two torn halves (a richer reward when it is found).
 */
public record TreasureTarget(String island, int index, String name, String kind, int x, int y, int z, boolean solved, boolean grand) {
    public static final Codec<TreasureTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("island").forGetter(TreasureTarget::island),
            Codec.INT.fieldOf("index").forGetter(TreasureTarget::index),
            Codec.STRING.optionalFieldOf("name", "").forGetter(TreasureTarget::name),
            Codec.STRING.fieldOf("kind").forGetter(TreasureTarget::kind),
            Codec.INT.fieldOf("x").forGetter(TreasureTarget::x),
            Codec.INT.optionalFieldOf("y", 64).forGetter(TreasureTarget::y),
            Codec.INT.fieldOf("z").forGetter(TreasureTarget::z),
            Codec.BOOL.optionalFieldOf("solved", false).forGetter(TreasureTarget::solved),
            Codec.BOOL.optionalFieldOf("grand", false).forGetter(TreasureTarget::grand)
    ).apply(i, TreasureTarget::new));
    public static final StreamCodec<ByteBuf, TreasureTarget> STREAM = ByteBufCodecs.fromCodec(CODEC);

    /** "island#index": the key looted secrets are remembered by. */
    public String key() {
        return island + "#" + index;
    }

    public TreasureTarget solve() {
        return new TreasureTarget(island, index, name, kind, x, y, z, true, grand);
    }

    public TreasureTarget asGrand() {
        return new TreasureTarget(island, index, name, kind, x, y, z, solved, true);
    }

    /** Island display name (canon islands use their translation). */
    public Component islandName() {
        return name.isEmpty() ? Component.translatable("island.grandline." + island) : Component.literal(name);
    }
}
