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

import spectrum255.rogueengine.profile.CentralIslandSettings;
import spectrum255.rogueengine.profile.TerrainBoundary;

public final class CentralIslandDensityFunction implements DensityFunction {
	public static final MapCodec<CentralIslandDensityFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			DensityFunction.CODEC.fieldOf("noise").forGetter(CentralIslandDensityFunction::noise),
			TerrainBoundary.CODEC.fieldOf("boundary").forGetter(CentralIslandDensityFunction::boundary),
			CentralIslandSettings.CODEC.fieldOf("settings").forGetter(CentralIslandDensityFunction::settings))
			.apply(instance, CentralIslandDensityFunction::new));

	private final DensityFunction noise;
	private final TerrainBoundary boundary;
	private final CentralIslandSettings settings;

	public CentralIslandDensityFunction(DensityFunction noise, TerrainBoundary boundary,
			CentralIslandSettings settings) {
		this.noise = Objects.requireNonNull(noise, "noise");
		this.boundary = Objects.requireNonNull(boundary, "boundary").validate().getOrThrow();
		this.settings = Objects.requireNonNull(settings, "settings").validate().getOrThrow();
		if (boundary.outsideMode() != TerrainBoundary.OutsideMode.VOID) {
			throw new IllegalArgumentException("Central islands require a void terrain boundary");
		}
	}

	public DensityFunction noise() {
		return noise;
	}

	public TerrainBoundary boundary() {
		return boundary;
	}

	public CentralIslandSettings settings() {
		return settings;
	}

	@Override
	public DensitySampler compileSampler(CompileContext context) {
		DensitySampler noiseSampler = noise.compileSampler(context);
		return new DensitySampler() {
			@Override
			public float sampleValue(SamplerContext samplerContext, int x, int y, int z) {
				double nx = (x - boundary.centerX()) / boundary.radiusX();
				double nz = (z - boundary.centerZ()) / boundary.radiusZ();
				double radius = Math.sqrt(nx * nx + nz * nz);
				if (radius >= 1.0) {
					return -1.0F;
				}
				double noiseValue = noiseSampler.sampleValue(samplerContext, x, settings.centerY(), z);
				double effectiveRadius = radius + settings.edgeNoise() * noiseValue * 0.08;
				if (effectiveRadius >= 1.0) {
					return -1.0F;
				}
				double centerBlend = Math.min(1.0, radius / 0.2);
				double surface = settings.centerY()
						+ settings.surfaceRoughness() * noiseValue * centerBlend
						- radius * radius * settings.maxThickness() * 0.15;
				double thickness = settings.maxThickness()
						* Math.pow(Math.max(0.0, 1.0 - radius * radius), settings.bottomTaper());
				double bottom = surface - thickness;
				float topDensity = (float) ((surface - y + 0.5) / 3.0);
				float bottomDensity = (float) ((y - bottom + 0.5) / 3.0);
				return Math.max(-1.0F, Math.min(1.0F, Math.min(topDensity, bottomDensity)));
			}

			@Override
			public void sampleVolume(SamplerContext samplerContext, DensityBuffer buffer, DensityVolume volume) {
				DensitySampler.sampleVolumeNaive(samplerContext, buffer, volume, this);
			}
		};
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return new CentralIslandDensityFunction(rule.rewrite(noise), boundary, settings);
	}

	@Override
	public Interval range() {
		return Interval.of(-1.0F, 1.0F);
	}

	@Override
	public int domainAxes() {
		return DensityFunction.ALL_AXES;
	}

	@Override
	public MapCodec<CentralIslandDensityFunction> codec() {
		return CODEC;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CentralIslandDensityFunction function
				&& noise.equals(function.noise) && boundary.equals(function.boundary)
				&& settings.equals(function.settings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(noise, boundary, settings);
	}
}
