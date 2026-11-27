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
import net.minecraft.world.level.NoiseColumn;
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

import spectrum255.rogueengine.profile.TerrainBoundary;

class BoundedNoiseGeneratorTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void wrapsDelegateAndRoundTripsThroughOwnCodec() {
		NoiseBasedChunkGenerator delegate = delegate();
		TerrainBoundary boundary = new TerrainBoundary(0, 0, 16, 16,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
		BoundedNoiseGenerator generator = BoundedNoiseGenerator.create(delegate, boundary,
				TerrainBoundary.OutsideMode.VOID);

		assertSame(delegate.getBiomeSource(), generator.getBiomeSource());
		assertEquals(delegate.getGenDepth(), generator.getGenDepth());
		assertEquals(delegate.getSeaLevel(), generator.getSeaLevel());
		assertEquals(generator.getMinY(), generator.getBaseHeight(1000, 1000,
				Heightmap.Types.WORLD_SURFACE, null, null));
		assertTrue(generator.getBaseColumn(1000, 1000, null, null).getBlock(generator.getMinY()).isAir());
	}

	@Test
	void bedrockFloorModeReportsOutsideFloor() {
		TerrainBoundary boundary = new TerrainBoundary(0, 0, 16, 16,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.BEDROCK_FLOOR);
		BoundedNoiseGenerator generator = BoundedNoiseGenerator.create(delegate(), boundary,
				TerrainBoundary.OutsideMode.BEDROCK_FLOOR);

		NoiseColumn column = generator.getBaseColumn(1000, 1000, null, null);
		assertEquals(Blocks.BEDROCK.defaultBlockState(), column.getBlock(generator.getMinY()));
		assertTrue(column.getBlock(generator.getMinY() + 1).isAir());
	}

	private NoiseBasedChunkGenerator delegate() {
		DensityFunction zero = DensityFunctions.zero();
		NoiseRouter router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero);
		NoiseGeneratorSettings settings = new NoiseGeneratorSettings(
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
		return new NoiseBasedChunkGenerator(new FixedBiomeSource(null), Holder.direct(settings));
	}
}
