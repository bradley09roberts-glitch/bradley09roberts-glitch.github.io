package net.palemeridian.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code palemeridian:valley} density function. Modes:
 * <ul>
 *   <li>{@code surface}: replaces vanilla {@code sloped_cheese}; valley design inside, vanilla outside, blended at the rim.</li>
 *   <li>{@code height}: replaces {@code preliminary_surface_level} (a height in blocks).</li>
 *   <li>{@code guard}: positive near sites/roads (combine with {@code max} around cave functions).</li>
 * </ul>
 */
public record ValleyDensityFunction(Mode mode, Optional<DensityFunction> vanilla) implements DensityFunction {
	public static final MapCodec<ValleyDensityFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				Mode.CODEC.fieldOf("mode").forGetter(ValleyDensityFunction::mode),
				DensityFunction.CODEC.optionalFieldOf("vanilla").forGetter(ValleyDensityFunction::vanilla)
			)
			.apply(i, ValleyDensityFunction::new)
	);
	public static final KeyDispatchDataCodec<ValleyDensityFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

	private static final double SURFACE_MIN = -40.0, SURFACE_MAX = 60.0;

	public enum Mode implements StringRepresentable {
		SURFACE("surface"),
		HEIGHT("height"),
		GUARD("guard");

		public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
		private final String name;

		Mode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	@Override
	public double compute(FunctionContext ctx) {
		ValleyTerrain terrain = ValleyTerrain.get();
		int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
		switch (this.mode) {
			case GUARD:
				return terrain.valleyWeight(x, z) < 0.5 ? -1.0E6 : terrain.caveGuard(x, y, z);
			case HEIGHT: {
				double w = terrain.valleyWeight(x, z);
				if (w <= 0.0) {
					return this.vanillaValue(ctx, terrain.layout().waterLevel);
				}
				double valley = terrain.height(x, z);
				return w >= 1.0 ? valley : ValleyTerrain.lerp(w, this.vanillaValue(ctx, valley), valley);
			}
			case SURFACE:
			default: {
				double w = terrain.valleyWeight(x, z);
				if (w <= 0.0) {
					return this.vanillaValue(ctx, 0.0);
				}
				double valley = terrain.surfaceDensity(x, y, z);
				return w >= 1.0 ? valley : ValleyTerrain.lerp(w, this.vanillaValue(ctx, valley), valley);
			}
		}
	}

	private double vanillaValue(FunctionContext ctx, double fallback) {
		return this.vanilla.isPresent() ? this.vanilla.get().compute(ctx) : fallback;
	}

	@Override
	public void fillArray(double[] output, ContextProvider contextProvider) {
		contextProvider.fillAllDirectly(output, this);
	}

	@Override
	public DensityFunction mapChildren(Visitor visitor) {
		return new ValleyDensityFunction(this.mode, this.vanilla.map(v -> v.mapAll(visitor)));
	}

	@Override
	public double minValue() {
		return switch (this.mode) {
			case GUARD -> -1.0E6;
			case HEIGHT -> Math.min(-64.0, this.vanilla.map(DensityFunction::minValue).orElse(-64.0));
			case SURFACE -> Math.min(SURFACE_MIN, this.vanilla.map(DensityFunction::minValue).orElse(SURFACE_MIN));
		};
	}

	@Override
	public double maxValue() {
		return switch (this.mode) {
			case GUARD -> 1.0;
			case HEIGHT -> Math.max(320.0, this.vanilla.map(DensityFunction::maxValue).orElse(320.0));
			case SURFACE -> Math.max(SURFACE_MAX, this.vanilla.map(DensityFunction::maxValue).orElse(SURFACE_MAX));
		};
	}

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() {
		return CODEC;
	}
}
