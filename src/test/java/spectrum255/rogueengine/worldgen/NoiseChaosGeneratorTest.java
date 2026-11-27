package spectrum255.rogueengine.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.material.MaterialRules;

import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseParameters;

class NoiseChaosGeneratorTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void preservesBaseSettingsOutsideWhitelist() {
		NoiseGeneratorSettings base = baseSettings();
		BiomeSource biomeSource = biomeSource();

		NoiseBasedChunkGenerator generator = NoiseChaosGenerator.create(biomeSource, base, parameters(0.1, 1.1));
		NoiseGeneratorSettings applied = generator.generatorSettings().value();

		assertSame(biomeSource, generator.getBiomeSource());
		assertEquals(base.noiseSettings(), applied.noiseSettings());
		assertEquals(base.defaultBlock(), applied.defaultBlock());
		assertEquals(base.defaultFluid(), applied.defaultFluid());
		assertEquals(base.materialRule(), applied.materialRule());
		assertEquals(base.spawnTarget(), applied.spawnTarget());
		assertEquals(base.disableMobGeneration(), applied.disableMobGeneration());
		assertEquals(base.aquifers(), applied.aquifers());
		assertEquals(base.useLegacyRandomSource(), applied.useLegacyRandomSource());
		assertEquals(base.debugFunctions(), applied.debugFunctions());
	}

	@Test
	void appliesDensityAndClimateParameters() {
		NoiseBasedChunkGenerator generator = NoiseChaosGenerator.create(biomeSource(), baseSettings(), parameters(0.1, 1.1));
		NoiseGeneratorSettings applied = generator.generatorSettings().value();

		assertEquals(69, applied.seaLevel());
		assertEquals(69, generator.getSeaLevel());
		assertNotSame(baseSettings().noiseRouter().finalDensity(), applied.noiseRouter().finalDensity());
		assertNotSame(baseSettings().noiseRouter().temperature(), applied.noiseRouter().temperature());
	}

	@Test
	void rejectsInvalidScale() {
		assertThrows(IllegalArgumentException.class,
				() -> NoiseChaosGenerator.create(biomeSource(), baseSettings(), parameters(0.0, 0.0)));
	}

	@Test
	void roundTripsThroughNoiseGeneratorSettingsCodec() {
		NoiseGeneratorSettings settings = NoiseChaosGenerator.create(biomeSource(), baseSettings(), parameters(0.1, 1.1))
				.generatorSettings().value();

		JsonElement encoded = NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
		NoiseGeneratorSettings decoded = NoiseGeneratorSettings.DIRECT_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		assertEquals(settings, decoded);
	}

	private NoiseGeneratorSettings baseSettings() {
		DensityFunction zero = DensityFunctions.zero();
		NoiseRouter router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero);
		return new NoiseGeneratorSettings(
				NoiseSettings.create(-64, 384),
				Blocks.STONE.defaultBlockState(),
				Blocks.WATER.defaultBlockState(),
				router,
				Holder.direct(MaterialRules.state(Blocks.STONE.defaultBlockState())),
				List.of(),
				63,
				false,
				Optional.empty(),
				false,
				NoiseGeneratorSettings.DebugFunctions.EMPTY);
	}

	private BiomeSource biomeSource() {
		return new FixedBiomeSource(null);
	}

	private NoiseParameters parameters(double densityOffset, double densityScale) {
		return new NoiseParameters(densityOffset, densityScale, 1.0, 1.0, 1.0, 0.0, 0.0, 6);
	}
}
