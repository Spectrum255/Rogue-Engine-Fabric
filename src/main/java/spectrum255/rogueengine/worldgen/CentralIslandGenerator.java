package spectrum255.rogueengine.worldgen;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.profile.CentralIslandSettings;
import spectrum255.rogueengine.profile.TerrainBoundary;

public final class CentralIslandGenerator extends ChunkGenerator {
	public static final MapCodec<CentralIslandGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			NoiseBasedChunkGenerator.CODEC.fieldOf("delegate").forGetter(CentralIslandGenerator::delegate),
			TerrainBoundary.CODEC.fieldOf("boundary").forGetter(CentralIslandGenerator::boundary),
			CentralIslandSettings.CODEC.fieldOf("settings").forGetter(CentralIslandGenerator::settings))
			.apply(instance, CentralIslandGenerator::new));

	private final NoiseBasedChunkGenerator delegate;
	private final TerrainBoundary boundary;
	private final CentralIslandSettings settings;

	public CentralIslandGenerator(NoiseBasedChunkGenerator delegate, TerrainBoundary boundary,
			CentralIslandSettings settings) {
		super(Objects.requireNonNull(delegate, "delegate").getBiomeSource());
		this.delegate = delegate;
		this.boundary = Objects.requireNonNull(boundary, "boundary").validate().getOrThrow();
		this.settings = Objects.requireNonNull(settings, "settings").validate().getOrThrow();
		if (boundary.outsideMode() != TerrainBoundary.OutsideMode.VOID) {
			throw new IllegalArgumentException("Central islands require a void terrain boundary");
		}
	}

	public static CentralIslandGenerator create(NoiseBasedChunkGenerator delegate, TerrainBoundary boundary,
			CentralIslandSettings settings) {
		Objects.requireNonNull(delegate, "delegate");
		Objects.requireNonNull(boundary, "boundary").validate().getOrThrow();
		Objects.requireNonNull(settings, "settings").validate().getOrThrow();
		NoiseGeneratorSettings base = delegate.generatorSettings().value();
		NoiseRouter router = base.noiseRouter();
		DensityFunction noise = DensityFunctions.add(
				router.ridges().mul(0.65F), router.erosion().mul(0.35F)).clamp(-1.0F, 1.0F);
		DensityFunction island = new CentralIslandDensityFunction(noise, boundary, settings);
		NoiseRouter islandRouter = new NoiseRouter(
				router.temperature(), router.vegetation(), router.continents(), router.erosion(),
				router.depth(), router.ridges(), router.chunkSurfaceLevel(), island);
		CentralIslandSettings.CustomLayers layers = settings.customLayers().orElse(null);
		BlockState defaultBlock = layers == null ? base.defaultBlock() : layers.rockBlock();
		Holder<MaterialRule> materialRule = layers == null
				? base.materialRule() : Holder.direct(MaterialRules.state(layers.rockBlock()));
		NoiseGeneratorSettings islandSettings = new NoiseGeneratorSettings(
				base.noiseSettings(), defaultBlock, base.defaultFluid(), islandRouter,
				materialRule, base.spawnTarget(), base.seaLevel(), base.disableMobGeneration(),
				base.aquifers(), base.useLegacyRandomSource(), base.debugFunctions());
		return new CentralIslandGenerator(
				new NoiseBasedChunkGenerator(delegate.getBiomeSource(), Holder.direct(islandSettings)),
				boundary, settings);
	}

	public NoiseBasedChunkGenerator delegate() {
		return delegate;
	}

	public TerrainBoundary boundary() {
		return boundary;
	}

	public CentralIslandSettings settings() {
		return settings;
	}

	public int spawnY() {
		return settings.spawnY();
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public Optional<Identifier> getTypeNameForDataFixer() {
		return Optional.of(RogueEngineFabric.id("central_island"));
	}

	public boolean stable(ResourceKey<NoiseGeneratorSettings> key) {
		return delegate.stable(key);
	}

	@Override
	public int getGenDepth() {
		return delegate.getGenDepth();
	}

	@Override
	public int getSeaLevel() {
		return delegate.getSeaLevel();
	}

	@Override
	public int getMinY() {
		return delegate.getMinY();
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
			RandomState randomState) {
		return boundary.contains(x, z) ? delegate.getBaseHeight(x, z, type, level, randomState) : getMinY();
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		if (boundary.contains(x, z)) {
			return delegate.getBaseColumn(x, z, level, randomState);
		}
		BlockState[] states = new BlockState[getGenDepth()];
		Arrays.fill(states, Blocks.AIR.defaultBlockState());
		return new NoiseColumn(getMinY(), states);
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender,
			RandomState randomState, StructureManager structureManager, BiomeManager biomeManager,
			WorldGenRegion region, Set<Holder<Biome>> biomes) {
		if (!boundary.intersects(chunk.getPos())) {
			return CompletableFuture.completedFuture(chunk);
		}
		return delegate.buildTerrain(chunk, blender, randomState, structureManager, biomeManager, region, biomes)
				.thenApply(generated -> {
					settings.customLayers().ifPresent(layers -> applyCustomLayers(generated, layers));
					return generated;
				});
	}

	@Override
	public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
			StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager,
			ResourceKey<Level> levelKey) {
		if (boundary.intersects(chunk.getPos())) {
			delegate.createStructures(registries, state, structureManager, chunk, templateManager, levelKey);
		}
	}

	@Override
	public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
		if (boundary.intersects(chunk.getPos())) {
			delegate.createReferences(level, structureManager, chunk);
		}
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		if (boundary.intersects(chunk.getPos())) {
			delegate.applyBiomeDecoration(level, chunk, structureManager);
		}
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		delegate.spawnOriginalMobs(region);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos,
			SamplerContext samplerContext) {
		delegate.addDebugScreenInfo(info, randomState, pos, samplerContext);
		info.add("Rogue Engine central island: center_y=" + settings.centerY()
				+ " thickness=" + settings.maxThickness() + " surface=" + settings.surfaceMode().getSerializedName());
	}

	@Override
	public void validate() {
		delegate.validate();
	}

	static BlockState layerState(CentralIslandSettings.CustomLayers layers, int depth, int x, int y, int z) {
		if (depth < layers.topDepth()) {
			return layers.topBlock();
		}
		if (depth < layers.topDepth() + layers.soilDepth()) {
			return layers.soilBlock();
		}
		if (layers.oreBlock().isPresent() && depth >= layers.oreMinDepth() && depth <= layers.oreMaxDepth()
				&& oreHash(x, y, z) < layers.oreChance()) {
			return layers.oreBlock().get();
		}
		return layers.rockBlock();
	}

	private static double oreHash(int x, int y, int z) {
		long hash = (x * 3129871L) ^ (y * 116129781L) ^ (z * 1554987889L);
		hash = hash * 0x27D4EB2FL + 0x165667B1L;
		return (hash & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
	}

	private void applyCustomLayers(ChunkAccess chunk, CentralIslandSettings.CustomLayers layers) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
		for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
			for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
				if (!boundary.contains(x, z)) {
					continue;
				}
				int surfaceY = -1;
				for (int y = chunk.getMaxY(); y >= chunk.getMinY(); y--) {
					if (!chunk.getBlockState(blockPos.set(x, y, z)).isAir()) {
						surfaceY = y;
						break;
					}
				}
				if (surfaceY < 0) {
					continue;
				}
				int depth = 0;
				for (int y = surfaceY; y >= chunk.getMinY(); y--) {
					BlockState current = chunk.getBlockState(blockPos.set(x, y, z));
					if (current.isAir()) {
						continue;
					}
					BlockState replacement = layerState(layers, depth, x, y, z);
					if (!current.equals(replacement)) {
						chunk.setBlockState(blockPos.set(x, y, z), replacement, 0);
					}
					depth++;
				}
			}
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CentralIslandGenerator generator
				&& delegate.generatorSettings().equals(generator.delegate.generatorSettings())
				&& getBiomeSource().equals(generator.getBiomeSource())
				&& boundary.equals(generator.boundary) && settings.equals(generator.settings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(delegate.generatorSettings(), getBiomeSource(), boundary, settings);
	}
}
