package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import spectrum255.rogueengine.worldgen.CentralIslandGenerator;
import spectrum255.rogueengine.worldgen.WorldGenParameterResolver;

class CentralIslandResolutionTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void materializesIslandAndRejectsInvalidSpawnOrBorder() {
		WorldGenTemplate template = template();
		ResolvedBoxProfile resolved = resolve(profile(0, 129, 0, 512), template);
		CentralIslandGenerator generator = assertInstanceOf(CentralIslandGenerator.class, resolved.generator());
		assertEquals(template.terrainBoundary(), generator.boundary());
		assertEquals(129, generator.spawnY());
		assertEquals(512.0, resolved.materializedProfile().border().initialSize());

		assertThrows(IllegalArgumentException.class,
				() -> resolve(profile(200, 129, 0, 512), template));
		assertThrows(IllegalArgumentException.class,
				() -> resolve(profile(0, 129, 0, 128), template));
	}

	@Test
	void rejectsNoiseOverridesForIslandTemplates() {
		WorldGenTemplate template = template();
		String hash = ProfileSnapshot.sha256(WorldGenTemplateCodec.CODEC.encodeStart(
				com.mojang.serialization.JsonOps.INSTANCE, template).result().orElseThrow().toString());
		BoxProfile.TemplateGenerator generator = new BoxProfile.TemplateGenerator("template",
				Identifier.withDefaultNamespace("test"), Map.of("density_offset", 0.1), Optional.empty());

		assertThrows(IllegalArgumentException.class,
				() -> WorldGenParameterResolver.resolve(template, hash, generator, 42L));
	}

	private ResolvedBoxProfile resolve(BoxProfile profile, WorldGenTemplate template) {
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

	private WorldGenTemplate template() {
		TerrainBoundary boundary = new TerrainBoundary(0, 0, 128, 128,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
		CentralIslandSettings island = new CentralIslandSettings(
				CentralIslandSettings.SurfaceMode.VANILLA_SURFACE, 128, 64, 1.5, 4.0, 0.35,
				Optional.empty());
		return new WorldGenTemplate(1, "central_island", Identifier.withDefaultNamespace("overworld"),
				WorldGenTemplate.neutralRanges(), List.of(), null,
				new MapPool.SelectionRange(List.of(), List.of()), boundary, island);
	}
}
