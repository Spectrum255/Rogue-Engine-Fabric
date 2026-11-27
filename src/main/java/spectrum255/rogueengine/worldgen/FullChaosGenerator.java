package spectrum255.rogueengine.worldgen;

import java.util.Objects;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;

import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseParameters;

public final class FullChaosGenerator {
	private FullChaosGenerator() {
	}

	public static NoiseBasedChunkGenerator create(BiomeSource biomeSource, NoiseGeneratorSettings base,
			NoiseParameters parameters) {
		Objects.requireNonNull(biomeSource, "biomeSource");
		NoiseGeneratorSettings randomized = NoiseChaosSettings.apply(base, parameters).settings();
		NoiseRouter router = randomized.noiseRouter();
		DensityFunction alternate = DensityFunctions.abs(router.ridges())
				.sub(router.erosion())
				.mul((float) parameters.continentScale());
		DensityFunction warped = DensityFunctions.lerp(
				router.finalDensity(), DensityFunctions.constant(0.35F), alternate).clamp(-1.0F, 1.0F);
		NoiseRouter fullChaos = new NoiseRouter(
				router.vegetation().add((float) parameters.temperatureShift()),
				router.temperature().add((float) parameters.vegetationShift()),
				router.continents(),
				router.erosion(),
				router.depth(),
				DensityFunctions.halfNegative(router.ridges()),
				router.chunkSurfaceLevel(),
				warped);
		NoiseGeneratorSettings settings = new NoiseGeneratorSettings(
				randomized.noiseSettings(),
				randomized.defaultBlock(),
				randomized.defaultFluid(),
				fullChaos,
				randomized.materialRule(),
				randomized.spawnTarget(),
				randomized.seaLevel(),
				randomized.disableMobGeneration(),
				randomized.aquifers(),
				randomized.useLegacyRandomSource(),
				randomized.debugFunctions());
		return new NoiseBasedChunkGenerator(biomeSource, Holder.direct(settings));
	}
}
