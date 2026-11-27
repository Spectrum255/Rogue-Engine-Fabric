package spectrum255.rogueengine.worldgen;

import java.util.Objects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import spectrum255.rogueengine.profile.TerrainBoundary;

public final class BoundedDensityFunction implements DensityFunction {
	public static final MapCodec<BoundedDensityFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			DensityFunction.CODEC.fieldOf("input").forGetter(BoundedDensityFunction::input),
			TerrainBoundary.CODEC.fieldOf("boundary").forGetter(BoundedDensityFunction::boundary))
			.apply(instance, BoundedDensityFunction::new));

	private final DensityFunction input;
	private final TerrainBoundary boundary;

	public BoundedDensityFunction(DensityFunction input, TerrainBoundary boundary) {
		this.input = Objects.requireNonNull(input, "input");
		this.boundary = Objects.requireNonNull(boundary, "boundary").validate().getOrThrow();
	}

	public DensityFunction input() {
		return input;
	}

	public TerrainBoundary boundary() {
		return boundary;
	}

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		DensitySampler delegate = input.compileSampler(context);
		return new DensitySampler() {
			@Override
			public float sampleValue(SamplerContext samplerContext, int x, int y, int z) {
				return boundary.contains(x, z) ? delegate.sampleValue(samplerContext, x, y, z) : -1.0F;
			}

			@Override
			public void sampleVolume(SamplerContext samplerContext, DensityBuffer buffer, DensityVolume volume) {
				DensitySampler.sampleVolumeNaive(samplerContext, buffer, volume, this);
			}
		};
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return new BoundedDensityFunction(rule.rewrite(input), boundary);
	}

	@Override
	public Interval range() {
		return Interval.encapsulating(input.range(), Interval.ofExact(-1.0F));
	}

	@Override
	public int domainAxes() {
		return DensityFunction.ALL_AXES;
	}

	@Override
	public MapCodec<BoundedDensityFunction> codec() {
		return CODEC;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BoundedDensityFunction function
				&& input.equals(function.input) && boundary.equals(function.boundary);
	}

	@Override
	public int hashCode() {
		return 31 * input.hashCode() + boundary.hashCode();
	}
}
