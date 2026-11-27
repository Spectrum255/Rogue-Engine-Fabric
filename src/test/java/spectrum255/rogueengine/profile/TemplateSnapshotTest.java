package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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

import spectrum255.rogueengine.worldgen.WorldGenParameters;

class TemplateSnapshotTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void templateGeneratorIsMaterializedForSnapshots() {
		ResolvedBoxProfile resolved = resolve(42L);

		BoxProfile.InlineGenerator inline = assertInstanceOf(BoxProfile.InlineGenerator.class,
				resolved.materializedProfile().generator());
		assertSame(resolved.generator(), inline.value());
		assertSame(resolved.generator(), resolved.levelStem().generator());
		assertTrue(resolved.worldGenParameters().isPresent());
	}

	@Test
	void sameSeedProducesSameGeneratorSettings() {
		NoiseBasedChunkGenerator first = (NoiseBasedChunkGenerator) resolve(42L).generator();
		NoiseBasedChunkGenerator second = (NoiseBasedChunkGenerator) resolve(42L).generator();

		assertEquals(first.generatorSettings(), second.generatorSettings());
	}

	@Test
	void templateHashIsStable() {
		WorldGenParameters first = resolve(42L).worldGenParameters().orElseThrow();
		WorldGenParameters second = resolve(42L).worldGenParameters().orElseThrow();

		assertEquals(first.templateHash(), second.templateHash());
		assertEquals(first.templateId(), Identifier.withDefaultNamespace("test"));
	}

	private ResolvedBoxProfile resolve(long seed) {
		BoxProfile profile = new BoxProfile(
				2,
				Identifier.withDefaultNamespace("overworld"),
				new BoxProfile.TemplateGenerator("template", Identifier.withDefaultNamespace("test"), Map.of(), Optional.empty()),
				new BoxProfile.Seed("fixed", Optional.of(seed)),
				new BoxProfile.SearchSpawn("search", 8),
				new BoxProfile.Border(0.0, 0.0, 256.0, 16.0),
				BoxProfile.InventoryPolicy.DEFAULT);
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
				Identifier.withDefaultNamespace("overworld"), ranges, List.of());
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
		ChunkGenerator generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(null), Holder.direct(settings));
		Holder<DimensionType> dimensionType = Holder.direct(null);
		LevelStem baseStem = new LevelStem(dimensionType, generator);
		return ResolvedBoxProfile.materializeTemplate(profile, dimensionType, template, baseStem, seed);
	}
}
