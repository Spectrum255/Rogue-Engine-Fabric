package spectrum255.rogueengine.worldgen;

import java.util.Objects;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseParameters;

public record NoiseChaosSettings(NoiseGeneratorSettings settings) {
	public NoiseChaosSettings {
		Objects.requireNonNull(settings, "settings");
	}

	public static NoiseChaosSettings apply(NoiseGeneratorSettings base, NoiseParameters parameters) {
		Objects.requireNonNull(base, "base");
		Objects.requireNonNull(parameters, "parameters");
		validate(parameters);
		NoiseRouter router = base.noiseRouter();
		DensityFunction finalDensity = router.finalDensity()
				.add((float) parameters.densityOffset())
				.mul((float) parameters.densityScale())
				.clamp(-1.0F, 1.0F);
		NoiseRouter randomized = new NoiseRouter(
				router.temperature().add((float) parameters.temperatureShift()),
				router.vegetation().add((float) parameters.vegetationShift()),
				router.continents().mul((float) parameters.continentScale()),
				router.erosion().mul((float) parameters.erosionScale()),
				router.depth(),
				router.ridges().mul((float) parameters.ridgeScale()),
				router.chunkSurfaceLevel(),
				finalDensity);
		int minY = base.noiseSettings().minY();
		int maxY = minY + base.noiseSettings().height() - 1;
		int seaLevel = Math.max(minY + 1, Math.min(maxY, base.seaLevel() + parameters.seaLevelOffset()));
		return new NoiseChaosSettings(new NoiseGeneratorSettings(
				base.noiseSettings(),
				base.defaultBlock(),
				base.defaultFluid(),
				randomized,
				base.materialRule(),
				base.spawnTarget(),
				seaLevel,
				base.disableMobGeneration(),
				base.aquifers(),
				base.useLegacyRandomSource(),
				base.debugFunctions()));
	}

	private static void validate(NoiseParameters parameters) {
		if (!Double.isFinite(parameters.densityOffset()) || Math.abs(parameters.densityOffset()) > 1.0
				|| !Double.isFinite(parameters.temperatureShift()) || Math.abs(parameters.temperatureShift()) > 1.0
				|| !Double.isFinite(parameters.vegetationShift()) || Math.abs(parameters.vegetationShift()) > 1.0) {
			throw new IllegalArgumentException("Density offsets and climate shifts must be finite and within [-1, 1]");
		}
		if (!positiveFinite(parameters.densityScale()) || !positiveFinite(parameters.ridgeScale())
				|| !positiveFinite(parameters.continentScale()) || !positiveFinite(parameters.erosionScale())) {
			throw new IllegalArgumentException("Density and climate scales must be finite and positive");
		}
		if (parameters.seaLevelOffset() < -64 || parameters.seaLevelOffset() > 64) {
			throw new IllegalArgumentException("Sea level offset must stay within [-64, 64]");
		}
	}

	private static boolean positiveFinite(double value) {
		return Double.isFinite(value) && value > 0.0;
	}
}
