package spectrum255.rogueengine.worldgen;

import java.util.Objects;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseParameters;

public final class NoiseChaosGenerator {
	private NoiseChaosGenerator() {
	}

	public static NoiseBasedChunkGenerator create(BiomeSource biomeSource, NoiseGeneratorSettings base,
			NoiseParameters parameters) {
		Objects.requireNonNull(biomeSource, "biomeSource");
		NoiseGeneratorSettings settings = NoiseChaosSettings.apply(base, parameters).settings();
		return new NoiseBasedChunkGenerator(biomeSource, Holder.direct(settings));
	}
}
