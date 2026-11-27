package spectrum255.rogueengine.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

class ChaosGeneratorTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void fullChaosProducesSerializableWarpedSettings() {
		NoiseGeneratorSettings base = baseSettings();
		NoiseBasedChunkGenerator generator = FullChaosGenerator.create(biomeSource(), base, parameters());
		NoiseGeneratorSettings applied = generator.generatorSettings().value();

		assertNotSame(base.noiseRouter().temperature(), applied.noiseRouter().temperature());
		assertNotSame(base.noiseRouter().finalDensity(), applied.noiseRouter().finalDensity());
		JsonElement encoded = NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, applied).getOrThrow();
		assertEquals(applied, NoiseGeneratorSettings.DIRECT_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
	}

	@Test
	void presetChaosUsesSelectedParameters() {
		NoiseBasedChunkGenerator generator = PresetChaosGenerator.create(biomeSource(), baseSettings(), parameters());
		NoiseGeneratorSettings applied = generator.generatorSettings().value();

		assertEquals(69, applied.seaLevel());
		JsonElement encoded = NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, applied).getOrThrow();
		assertEquals(applied, NoiseGeneratorSettings.DIRECT_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
	}

	@Test
	void fullChaosRejectsInvalidParameters() {
		assertThrows(IllegalArgumentException.class, () -> FullChaosGenerator.create(biomeSource(), baseSettings(),
				new NoiseParameters(0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0)));
	}

	private NoiseGeneratorSettings baseSettings() {
		DensityFunction temperature = DensityFunctions.constant(0.2F);
		DensityFunction vegetation = DensityFunctions.constant(0.7F);
		DensityFunction continents = DensityFunctions.constant(0.1F);
		DensityFunction erosion = DensityFunctions.constant(0.2F);
		DensityFunction ridges = DensityFunctions.constant(0.4F);
		DensityFunction finalDensity = DensityFunctions.constant(0.05F);
		NoiseRouter router = new NoiseRouter(
				temperature, vegetation, continents, erosion, DensityFunctions.zero(), ridges, DensityFunctions.zero(), finalDensity);
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

	private NoiseParameters parameters() {
		return new NoiseParameters(0.1, 1.1, 1.2, 0.9, 1.05, 0.05, -0.05, 6);
	}
}
