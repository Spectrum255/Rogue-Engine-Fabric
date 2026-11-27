package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.material.MaterialRules;

import spectrum255.rogueengine.map.MapPool;
import spectrum255.rogueengine.worldgen.BoundedNoiseGenerator;

class TerrainBoundaryResolutionTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void materializesBoundedGeneratorAndRejectsInvalidSpawnOrBorder() {
		TerrainBoundary boundary = boundary(128);
		ResolvedBoxProfile resolved = resolve(profile(0, 129, 0, 256), boundary);
		BoundedNoiseGenerator generator = assertInstanceOf(BoundedNoiseGenerator.class, resolved.generator());
		assertEquals(boundary, generator.boundary());
		assertEquals(256.0, resolved.materializedProfile().border().initialSize());

		assertThrows(IllegalArgumentException.class,
				() -> resolve(profile(120, 129, 120, 256), boundary));
		assertThrows(IllegalArgumentException.class,
				() -> resolve(profile(0, 129, 0, 128), boundary));
	}

	@Test
	void hardBorderSupportsDesignDefaultAndRejectsVanillaOverflow() {
		BoxProfile valid = profile(0, 129, 0, 30_000_000);
		assertTrue(valid.validate().result().isPresent());

		BoxProfile invalid = profile(0, 129, 0, 30_000_000_000.0);
		assertTrue(invalid.validate().result().isEmpty());
	}

	private ResolvedBoxProfile resolve(BoxProfile profile, TerrainBoundary boundary) {
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
		ChunkGenerator base = new NoiseBasedChunkGenerator(new FixedBiomeSource(null), Holder.direct(settings));
		Holder<DimensionType> dimensionType = Holder.direct(null);
		LevelStem baseStem = new LevelStem(dimensionType, base);
		WorldGenTemplate.NoiseRanges ranges = new WorldGenTemplate.NoiseRanges(
				new WorldGenTemplate.DoubleRange(-0.2, 0.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(-0.1, 0.1),
				new WorldGenTemplate.DoubleRange(-0.1, 0.1),
				new WorldGenTemplate.IntRange(-8, 8));
		WorldGenTemplate template = new WorldGenTemplate(1, "noise_chaos",
				Identifier.withDefaultNamespace("overworld"), ranges, List.of(), null,
				new MapPool.SelectionRange(List.of(), List.of()), boundary, null);
		return ResolvedBoxProfile.materializeTemplate(profile, dimensionType, template, baseStem, 42L);
	}

	private BoxProfile profile(double x, double y, double z, double borderSize) {
		return new BoxProfile(
				2,
				Identifier.withDefaultNamespace("overworld"),
				new BoxProfile.TemplateGenerator("template", Identifier.withDefaultNamespace("test"),
						Map.of(), Optional.empty()),
				new BoxProfile.Seed("fixed", Optional.of(42L)),
				new BoxProfile.FixedSpawn("fixed", x, y, z, 0.0F),
				new BoxProfile.Border(0, 0, borderSize, 16),
				BoxProfile.InventoryPolicy.DEFAULT);
	}

	private TerrainBoundary boundary(double radius) {
		return new TerrainBoundary(0, 0, radius, radius,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
	}
}
