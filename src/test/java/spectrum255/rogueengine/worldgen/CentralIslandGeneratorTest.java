package spectrum255.rogueengine.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.material.MaterialRules;

import spectrum255.rogueengine.profile.CentralIslandSettings;
import spectrum255.rogueengine.profile.TerrainBoundary;

class CentralIslandGeneratorTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void wrapsDelegateAndReportsVoidOutside() {
		NoiseBasedChunkGenerator delegate = delegate();
		CentralIslandGenerator generator = CentralIslandGenerator.create(delegate, boundary(), settings());

		assertSame(delegate.getBiomeSource(), generator.getBiomeSource());
		assertEquals(generator.getMinY(), generator.getBaseHeight(1000, 1000,
				Heightmap.Types.WORLD_SURFACE, null, null));
		assertTrue(generator.getBaseColumn(1000, 1000, null, null).getBlock(generator.getMinY()).isAir());
		assertEquals(129, generator.spawnY());
	}

	@Test
	void customLayersUseDepthAndDeterministicOres() {
		CentralIslandSettings.CustomLayers layers = new CentralIslandSettings.CustomLayers(
				Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
				Blocks.STONE.defaultBlockState(), 1, 3, Optional.empty(), 0.0, 4, 32);

		assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), CentralIslandGenerator.layerState(layers, 0, 0, 128, 0));
		assertEquals(Blocks.DIRT.defaultBlockState(), CentralIslandGenerator.layerState(layers, 3, 0, 125, 0));
		assertEquals(Blocks.STONE.defaultBlockState(), CentralIslandGenerator.layerState(layers, 4, 0, 124, 0));
	}

	private TerrainBoundary boundary() {
		return new TerrainBoundary(0, 0, 16, 16,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
	}

	private CentralIslandSettings settings() {
		return new CentralIslandSettings(CentralIslandSettings.SurfaceMode.VANILLA_SURFACE,
				128, 64, 1.5, 4.0, 0.35, Optional.empty());
	}

	private NoiseBasedChunkGenerator delegate() {
		DensityFunction zero = DensityFunctions.zero();
		NoiseGeneratorSettings settings = new NoiseGeneratorSettings(
				NoiseSettings.create(-64, 384),
				Blocks.STONE.defaultBlockState(),
				Blocks.WATER.defaultBlockState(),
				new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero),
				Holder.direct(MaterialRules.state(Blocks.STONE.defaultBlockState())),
				List.of(),
				63,
				false,
				Optional.empty(),
				false,
				NoiseGeneratorSettings.DebugFunctions.EMPTY);
		return new NoiseBasedChunkGenerator(new FixedBiomeSource(null), Holder.direct(settings));
	}
}
